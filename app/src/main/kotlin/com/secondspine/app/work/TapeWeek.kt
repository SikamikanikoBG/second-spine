package com.secondspine.app.work

import android.content.Context
import com.secondspine.app.data.Graph
import com.secondspine.app.data.ProofKind
import com.secondspine.app.data.toEpochDay
import com.secondspine.coach.DeskRow
import com.secondspine.coach.Stage
import com.secondspine.coach.Tape
import com.secondspine.coach.TestWeekCard
import com.secondspine.coach.TransitionReason
import com.secondspine.coach.WeekData
import com.secondspine.coach.composeTape
import com.secondspine.coach.compliance
import com.secondspine.coach.daysInStage
import com.secondspine.coach.jurisdiction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.secondspine.coach.Day as BrainDay
import com.secondspine.coach.Habit as BrainHabit

/**
 * THE WEEK, ASSEMBLED — the Room-to-brain membrane for the Tape.
 *
 * `composeTape(week, jurisdiction, gates)` is pure and deterministic; it has no database and no clock.
 * Something has to read seven days out of Room and hand it over as a snapshot, and this is that
 * something. It is in `work/` rather than in the Tape's UI because the composer runs at *build* time,
 * on Sunday at 20:00, not at render time — SPEC §9.9's point is that a gate which fires in a
 * composable is a gate a UI bug can undo on the worst night of somebody's year.
 *
 * It is public so the Tape screen recomposes from the same inputs when he opens it. Same function,
 * same snapshot, same edition.
 */
object TapeWeek {

    private const val WEEK_MS = 7 * 86_400_000L

    /**
     * Compose this week's edition.
     *
     * Throws if the brain's grammar gates reject it — SCOFF-positive with a mocking register, a
     * DISAPPOINTED line with no CAUGHT_FAKE behind it, a roast on a COACH DOWN week. That is
     * deliberate and it is where the throw belongs: on Sunday at 20:00 in a background job, where it
     * is a crash in a worker, rather than in front of a man in a bad week.
     */
    suspend fun compose(context: Context, now: Long = System.currentTimeMillis()): Tape {
        Graph.install(context)
        val week = build(context, now)
        val habits = Graph.repository.habitsNow()
        val gates = Graph.repository.clinicalGates.first()
        return composeTape(week, jurisdiction(habits), gates)
    }

    /** Everything the composer is allowed to know about the week. Nothing derived, nothing scored. */
    suspend fun build(context: Context, now: Long = System.currentTimeMillis()): WeekData =
        withContext(Dispatchers.IO) {
            Graph.install(context)
            val db = Graph.db
            val settings = Graph.settings
            val weekStart = now - WEEK_MS
            val weekStartDay = weekStart.toEpochDay()

            val habitRows = db.habitDao().all()
            val enabled = habitRows.filter { it.enabled }
            val onDesk = enabled.filter { it.stage == Stage.ENFORCED || it.stage == Stage.AUDITED }

            val days = db.dayDao().allSince(weekStartDay)
            val proofs = db.proofDao().observeAll().first().filter { it.capturedAtWall >= weekStart }
            val caught = db.caughtEventDao().since(weekStart)

            // ── VERIFIED / UNVERIFIED / CONTRADICTED ────────────────────────
            //
            // UNVERIFIED has no colour and no sting: phone in the locker, dead battery, a gym that
            // bans cameras. It is emphatically NOT "we think you lied" — being falsely accused by
            // your own tool is a one-shot trust-death, so a day he completed without a photo lands
            // here and nowhere else. CONTRADICTED is only ever a BYTE_REPLAY, which is arithmetic.
            val provenDays = proofs
                .filter { it.kind == ProofKind.LIVE && !it.voided }
                .map { it.habitId to it.capturedAtWall.toEpochDay() }
                .toSet()
            val completed = days.filter { it.completed }
            val verified = completed.count { (it.habitId to it.epochDay) in provenDays }
            val unverified = completed.size - verified

            // ── the Ledger, already purged by its own query ─────────────────
            val ledger = Graph.repository.ledger(now)

            // ── the withdrawal: he told me before I asked ───────────────────
            //
            // The day name, not a count. VerifiedVsClaimed renders one warm line and no number,
            // because FOR THE RECORD is a withdrawal and not a fourth state. There is no
            // confession_count column to read even if this file wanted one.
            val confessions = db.confessionDao().observeAll().first().filter { it.at >= weekStart }
            val withdrawnDay = confessions.maxByOrNull { it.at }?.let { dayName(it.at) }

            // ── the graduation: what makes it a great week ──────────────────
            var graduation: String? = null
            for (h in habitRows) {
                val moved = db.stageTransitionDao().forHabit(h.id)
                    .firstOrNull { it.at >= weekStart && it.reason == TransitionReason.GRADUATED }
                if (moved != null) graduation = h.title
            }

            // ── COACH DOWN ──────────────────────────────────────────────────
            //
            // Counted from day rows that exist. A habit with no rows at all is not five days missed,
            // it is a habit nothing was asked of — and inferring a collapse from an absence of data
            // would fire the kindest surface in the app at somebody who simply has no schedule yet.
            val missedFive = onDesk.count { h ->
                days.count { it.habitId == h.id && !it.completed && !it.confessed && !it.suspended } >= 5
            }

            val marker = TapeMarker.read(context)

            WeekData(
                weekId = weekId(settings.installAt.first(), now),
                testWeek = testWeek(now),
                coldOpen = null,
                photos = proofs.size,
                ledgerEntries = ledger,
                // The Ledger card's scoreline. His points are the days he did it; Rip's are the rows
                // the clerk actually filed. Rip scores only when the docket does, which means the one
                // way for him to win is for the app to catch something real.
                ripPoints = ledger.count { it.at >= weekStart },
                arsenPoints = completed.size,
                verified = verified,
                unverified = unverified,
                contradicted = caught.size,
                withdrawnDay = withdrawnDay,
                // The roast is authored: it needs the fragment bank and a SlotResolver, which live in
                // the voice layer, not here. An empty candidate list composes a Tape with no Roast
                // segment rather than a Tape with an invented one.
                roastCandidates = emptyList(),
                consistencyPct = consistencyPct(onDesk.map { it.id }, days),
                restingHr = null,
                deskRows = onDesk.map { h ->
                    val brain = BrainHabit(h.id, h.stage, h.tier, h.stageSince, h.lockEligible)
                    DeskRow(
                        habitId = h.title,
                        stage = h.stage,
                        daysToGraduation = (h.stage.minDays - daysInStage(brain, now)).toInt().coerceAtLeast(0),
                    )
                },
                graduation = graduation,
                habitsMissedFiveDays = missedFive,
                depressiveSignature = false,
                lastWeekRung = marker?.rung,
                weightEwma = db.weightDao().latest() != null,
            )
        }

    /** Weeks since install, 1-based. The Tape is numbered from his first one, not from the epoch. */
    internal fun weekId(installAt: Long, now: Long): Int {
        if (installAt <= 0L) return 1
        return ((now - installAt) / WEEK_MS).toInt() + 1
    }

    /**
     * CONSISTENCY — the brain's `compliance()` over the desk, as a percent.
     *
     * Confessed and suspended days leave the ratio entirely, which is RESOLUTIONS §A1 and the reason
     * honesty dominates deception. It matters here and not only in the promotion gate: if a confessed
     * day dragged this number down, the Tape would be quietly pricing the free button once a week, in
     * public, on a chart. The button has to be cheaper than lying on every surface, not just the one
     * that graduates him.
     */
    internal fun consistencyPct(deskIds: List<String>, days: List<com.secondspine.app.data.DayRow>): Int {
        val mine = days.filter { it.habitId in deskIds }
            .map { BrainDay(it.habitId, it.epochDay, it.completed, it.confessed, it.suspended) }
        if (mine.isEmpty()) return 0
        return (compliance(mine) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * THE ONE GENUINELY COMPOUNDING NUMBER — as much of it as v1 honestly has.
     *
     * SPEC §9.6's Test Week (dead hang, max push-ups, 2 km walk-run, RFESS, resting HR) needs the
     * 50-movement arsenal and pose rep-counting, and RESOLUTIONS §E cuts both from v1 by name. There
     * is no `test_week` table and there is no protocol to run. So `freshThisWeek` is false and
     * `sincereCongratulation` is null on every v1 edition: SINCERE_ONE is spent once, ever, on the
     * first strict pull-up, and this file has no business spending the best beat in the product on a
     * number it made up to fill a card.
     *
     * What ships instead is the real compounding number v1 actually has, and it is the one the whole
     * experiment is scored on: **proofs banked**, with the best week standing as the PB. RESOLUTIONS
     * §E: *"if he is still photographing pages in week 7, the thesis is proven."* That is the outcome
     * variable. It improves roughly monotonically, it needs no writer, it is immune to the gaslighting
     * a scale does, and it is his own life rather than a metric about it.
     */
    private suspend fun testWeek(now: Long): TestWeekCard {
        val all = Graph.db.proofDao().observeAll().first()
        val thisWeek = all.count { it.capturedAtWall >= now - WEEK_MS }
        val byWeek = all
            .filter { it.capturedAtWall < now - WEEK_MS }
            .groupingBy { ((now - it.capturedAtWall) / WEEK_MS).toInt() }
            .eachCount()
        val pb = byWeek.maxByOrNull { it.value }
        val installAt = Graph.settings.installAt.first()
        return TestWeekCard(
            metric = "PROOFS BANKED",
            value = thisWeek.toString(),
            previousPb = pb?.value?.toString(),
            pbWeek = pb?.let { weekId(installAt, now - it.key * WEEK_MS) },
            freshThisWeek = false,
            sincereCongratulation = null,
        )
    }

    /** "Tuesday". Formatted here because `:coach` is pure JVM and has no timezone and no locale. */
    private fun dayName(at: Long): String =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
}

/**
 * The one fact about last week the Tape needs and nothing else stores.
 *
 * `ladderRung()` clamps to one rung of movement per week, so it needs last week's rung — and without
 * it, two graduations landing on the same Sunday drop him two registers at once, which reads as a bug
 * rather than as loss. Grief is gradual or it isn't grief.
 *
 * A file, not a column: `SettingsStore` and the schema belong to another agent, and a four-integer
 * marker does not justify reaching into either. It holds a week number and a volume level — no dates
 * of failures, no counts of anything he did wrong, nothing the purge would have opinions about.
 */
internal object TapeMarker {

    data class Marker(val weekId: Int, val rung: Int, val composedAt: Long, val notified: Boolean)

    fun read(context: Context): Marker? = runCatching {
        val parts = file(context).readText().trim().split('\t')
        Marker(parts[0].toInt(), parts[1].toInt(), parts[2].toLong(), parts[3].toBoolean())
    }.getOrNull()

    fun write(context: Context, marker: Marker) {
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            f.writeText("${marker.weekId}\t${marker.rung}\t${marker.composedAt}\t${marker.notified}")
        }
    }

    private fun file(context: Context) = File(File(context.filesDir, "tape"), "latest")
}
