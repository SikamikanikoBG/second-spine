package com.secondspine.app.data

import com.secondspine.coach.CaughtEvent
import com.secondspine.coach.CaughtKind
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Day
import com.secondspine.coach.Habit
import com.secondspine.coach.LedgerEntry
import com.secondspine.coach.LedgerKind
import com.secondspine.coach.LEDGER_PURGE_DAYS
import com.secondspine.coach.Stage
import com.secondspine.coach.TransitionReason
import com.secondspine.coach.TrendPoint
import com.secondspine.coach.WeightEntry
import com.secondspine.coach.canEnter
import com.secondspine.coach.demotionCause
import com.secondspine.coach.demoted
import com.secondspine.coach.jurisdiction
import com.secondspine.coach.next
import com.secondspine.coach.purge
import com.secondspine.coach.shouldGraduate
import com.secondspine.coach.weightTrend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * THE BOUNDARY.
 *
 * `:coach` is pure JVM with zero Android imports, and that is not architectural purity — with no
 * emulator and a slow sideload loop it is the only thing standing between the ladder and untested
 * production code. This class is the membrane: Room rows in, brain types out. No `Flow`, no
 * `Context`, no `Cursor`, no `Instant` from a live clock ever crosses into the brain, and the brain
 * never learns that a database exists.
 *
 * Time enters every brain call as an explicit `now` parameter, which is why the 300-day soak test
 * runs in milliseconds. This class is the only place allowed to ask the system what time it is.
 */
class CoachRepository(
    private val db: SecondSpineDatabase,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // ── Mappers: the whole point of this file ───────────────────────────────

    private fun HabitRow.toBrain() = Habit(
        id = id,
        stage = stage,
        tier = tier,
        stageSince = stageSince,
        lockEligible = lockEligible,
    )

    private fun DayRow.toBrain() = Day(
        habitId = habitId,
        epochDay = epochDay,
        completed = completed,
        confessed = confessed,
        suspended = suspended,
    )

    private fun CaughtEventRow.toBrain() = CaughtEvent(habitId = habitId, kind = kind, at = at)

    private fun LedgerEntryRow.toBrain() = LedgerEntry(kind = kind, habitId = habitId, at = at, note = note)

    private fun WeightEntryRow.toBrain() = WeightEntry(epochDay = at.toEpochDay(), kg = kg)

    // ── The odometer ────────────────────────────────────────────────────────

    /**
     * Only ENABLED habits reach the brain.
     *
     * Dormant pillars are rows in the schema, not habits on Rip's desk. `jurisdiction()` counts what
     * he is responsible for, and seeding seven pillars as ENFORCED would hand him an odometer of 7
     * against a declared range of 0..4 before the user has done anything. See Seed.kt.
     */
    val habits: Flow<List<Habit>> = db.habitDao().observeEnabled().map { rows -> rows.map { it.toBrain() } }

    /** Rows, not brain types — the UI needs titles and pillars, which `Habit` deliberately lacks. */
    val habitRows: Flow<List<HabitRow>> = db.habitDao().observeAll()

    /** THE ODOMETER. One integer. Nothing else may drive the five things it drives. */
    val jurisdiction: Flow<Int> = habits.map { jurisdiction(it) }

    val clinicalGates: Flow<ClinicalGates> =
        combine(settings.scoffPositive, settings.parqPositive) { scoff, parq ->
            ClinicalGates(scoffPositive = scoff, parqPositive = parq)
        }

    suspend fun habitsNow(): List<Habit> = db.habitDao().enabled().map { it.toBrain() }

    suspend fun daysFor(habitId: String, windowDays: Int): List<Day> {
        val since = clock().toEpochDay() - windowDays
        return db.dayDao().forHabitSince(habitId, since).map { it.toBrain() }
    }

    suspend fun caughtFor(habitId: String): List<CaughtEvent> =
        db.caughtEventDao().forHabit(habitId).map { it.toBrain() }

    // ── Proof: the signature moment ─────────────────────────────────────────

    /**
     * Bank a proof. ZERO-ASSERTION: this never rejects, never scores, never classifies.
     *
     * There is no `accepted` column to write and no verdict to return — the capture is banked, the
     * day is marked complete, and that is the entire happy path. What comes back is
     * [BankedProof.caught], which is true in one circumstance only: a prior proof carried the same
     * SHA-256 over the decoded pixel buffer. That is arithmetic, not an accusation. A real sensor
     * cannot emit an identical buffer twice.
     *
     * Note what is NOT computed here: no pHash comparison, no "suspicion", no confidence. pHash is
     * engineered to be robust, so honest re-shots of the same mug on the same counter collide, and
     * the app's one insinuation would fire on truthful nights (RESOLUTIONS §A2).
     *
     * **THE PROOF IS BANKED BEFORE THE CHECK RUNS, AND IS NEVER UNDONE BY IT.** Even a caught
     * capture keeps its row and its file. The archive records what happened; the pipeline decides
     * what it means. Those are different jobs and this is the line between them.
     */
    suspend fun bankProof(
        habitId: String,
        challengeId: Long?,
        pixelSha256: String,
        imagePath: String,
        capturedAtWall: Long = clock(),
        capturedAtElapsed: Long,
        kind: ProofKind = ProofKind.LIVE,
    ): BankedProof = withContext(Dispatchers.IO) {
        val proofId = db.proofDao().insert(
            ProofRow(
                habitId = habitId,
                challengeId = challengeId,
                pixelSha256 = pixelSha256,
                capturedAtWall = capturedAtWall,
                capturedAtElapsed = capturedAtElapsed,
                kind = kind,
                imagePath = imagePath,
            )
        )

        db.dayDao().upsert(
            DayRow(habitId = habitId, epochDay = capturedAtWall.toEpochDay(), completed = true)
        )
        challengeId?.let { db.challengeDao().setState(it, ChallengeState.ANSWERED.name) }

        val collision = db.proofDao().findByPixelHash(pixelSha256, excludingId = proofId)
        if (collision == null) return@withContext BankedProof(proofId, caught = false)

        // BYTE_REPLAY. The only value CaughtKind has, and near-unreachable on a camera-only path:
        // it fires perhaps twice in ten months, and that is correct.
        db.caughtEventDao().insert(
            CaughtEventRow(habitId = habitId, proofId = proofId, kind = CaughtKind.BYTE_REPLAY, at = capturedAtWall)
        )
        writeLedger(LedgerKind.CAUGHT_FAKE, habitId, capturedAtWall)
        BankedProof(proofId, caught = true)
    }

    data class BankedProof(val proofId: Long, val caught: Boolean)

    // ── Confession: free, unlimited, warm, never priced ─────────────────────

    /**
     * FOR THE RECORD.
     *
     * Writes `confession` + flips `day.confessed`, and writes **no** `stage_transition` and **no**
     * `ledger_entry` — there is no `LedgerKind` for a confession to arrive as, so the clerk cannot
     * write it down even by accident.
     *
     * The flipped column is what makes honesty pay: RESOLUTIONS §A1 takes confessed days out of the
     * compliance denominator entirely, so a confessed day cannot fail the graduation gate the way an
     * uncaught fake day would pass it. It also opens the 14-day repair window against collapse. The
     * button is cheaper than lying. Always.
     */
    suspend fun confess(
        habitId: String,
        kind: ConfessionKind,
        note: String? = null,
        at: Long = clock(),
    ) = withContext(Dispatchers.IO) {
        db.confessionDao().insert(ConfessionRow(habitId = habitId, kind = kind, at = at, note = note))
        val day = at.toEpochDay()
        db.dayDao().upsert(DayRow(habitId = habitId, epochDay = day, completed = false, confessed = true))
        db.dayDao().markConfessed(habitId, day)
    }

    // ── Break glass ─────────────────────────────────────────────────────────

    /**
     * BREAK GLASS. One tap, instant, always works, never confirmed, never mocked in the moment.
     *
     * Fire-and-forget on the app scope and NOT a `suspend` function, deliberately: the caller must
     * never be able to await it, and the UI must never be able to gate the safety action on a disk
     * write completing. If the insert throws, the break-glass action still happens — the record is
     * our bookkeeping, not his obligation.
     *
     * Nothing reads what this writes. See BreakGlassDao.kt.
     */
    fun breakGlass(reason: String? = null) {
        scope.launch {
            runCatching { db.breakGlassDao().record(BreakGlassRow(at = clock(), reason = reason)) }
        }
    }

    // ── The Ledger ──────────────────────────────────────────────────────────

    /**
     * The docket, already purged to 28 days by the query itself.
     *
     * Runs the brain's `purge()` over the result as well. That is not redundant: the DAO's window is
     * the belt and `purge()` is the braces, and the one thing this app must never do is speak a row
     * it promised to have forgotten.
     */
    suspend fun ledger(now: Long = clock()): List<LedgerEntry> =
        purge(db.ledgerDao().surviving(now).map { it.toBrain() }, now)

    fun observeLedger(now: Long = clock()): Flow<List<LedgerEntry>> =
        db.ledgerDao().observeSurviving(now).map { rows -> purge(rows.map { it.toBrain() }, now) }

    /**
     * THE COMEBACK TRIGGER — `daysSinceLastProof >= 4`. SPEC §4.6, and it is the whole input to the
     * "most important surface in the app".
     *
     * Nothing computed this, so `AppGraph.comebackDue` was frozen `false` and the forgiveness screen
     * — the one that keeps three bad weeks from becoming an uninstall — was permanently unreachable.
     * A returning user landed on a demand card instead, which is the exact churn event that screen
     * exists to prevent.
     *
     * The guard that matters is `maxOfOrNull ?: return false`: a fresh install has **zero** proofs,
     * and without this it would read "infinitely many dark days" and route a brand-new user straight
     * into a screen that says "welcome back". No proofs means the clock has not started, not that it
     * ran out. The number `4` never reaches the screen — only this boolean does (the screen is
     * forbidden a `daysMissed` field on purpose).
     *
     * The time is read at collection through `now()`, so the four dark days elapse on the wall clock
     * while the app is closed and are true the instant it is reopened, even though no row changed.
     */
    fun observeComebackDue(now: () -> Long = clock): Flow<Boolean> =
        db.proofDao().observeAll().map { proofs ->
            val lastProofAt = proofs.maxOfOrNull { it.capturedAtWall } ?: return@map false
            now() - lastProofAt >= COMEBACK_DARK_MS
        }

    /**
     * Accrual happens HERE, at write time, before the purge can ever see the row.
     *
     * A count is not a memory: he may say "forty-one", he may not say "that Tuesday in March". If
     * the counter were derived from the table it would decrement at 28 days, which would make the
     * tally a function of the window — and the whole point is that it isn't.
     */
    private suspend fun writeLedger(kind: LedgerKind, habitId: String?, at: Long, note: String? = null) {
        db.ledgerDao().insert(LedgerEntryRow(kind = kind, habitId = habitId, at = at, note = note))
        settings.accrueLifetime(kind)
    }

    suspend fun recordLedger(kind: LedgerKind, habitId: String? = null, at: Long = clock(), note: String? = null) =
        withContext(Dispatchers.IO) { writeLedger(kind, habitId, at, note) }

    /**
     * THE PURGE. Unconditional hard DELETE at 28 days, across every table that holds evidence of
     * failure or of a hard week. `proof` is never touched: evidence of failure purges; evidence of
     * work is kept forever.
     *
     * No carve-out for repeat offences (RESOLUTIONS §B). A thrice-repeated failure is deleted on
     * exactly the same day as a one-off. He structurally cannot hold a pattern against you, which is
     * what makes "I'm going to remember" false.
     */
    suspend fun purgeOldEvidence(now: Long = clock()) = withContext(Dispatchers.IO) {
        val cutoff = now - LEDGER_PURGE_DAYS * 86_400_000L
        db.ledgerDao().purgeBefore(cutoff)
        db.confessionDao().purgeBefore(cutoff)
        db.breakGlassDao().purgeBefore(cutoff)
    }

    // ── The pipeline ────────────────────────────────────────────────────────

    /**
     * Evaluate every enabled habit and write the transitions. Rip is not consulted and cannot object.
     *
     * DEMOTION IS CHECKED BEFORE GRADUATION. Both gates read the same window, and a habit that has
     * just been caught faking must not graduate on the same pass — `shouldGraduate` already freezes
     * on a caught event inside the repair window, but the ordering makes it structural rather than
     * dependent on that check staying correct.
     *
     * Promotion respects `canEnter`: a habit that has earned AUDITED but would breach `MAX_AUDITED`
     * stays where it is and is re-evaluated tomorrow. It does not lose its progress — `stageSince`
     * is untouched, so the day it earned still counts.
     */
    suspend fun runPipeline(now: Long = clock()): List<StageTransitionRow> = withContext(Dispatchers.IO) {
        val rows = db.habitDao().enabled()
        val written = mutableListOf<StageTransitionRow>()

        for (row in rows) {
            val habit = row.toBrain()
            val days = db.dayDao().forHabitSince(row.id, now.toEpochDay() - MAX_WINDOW_DAYS).map { it.toBrain() }
            val caught = db.caughtEventDao().forHabit(row.id).map { it.toBrain() }

            val cause = demotionCause(habit, days, caught, now)
            if (cause != null) {
                val to = habit.stage.demoted()
                if (to != habit.stage) {
                    written += applyTransition(row, to, cause, now)
                }
                continue
            }

            if (shouldGraduate(habit, days, caught, now)) {
                val to = habit.stage.next()
                // Re-read: an earlier habit in this same loop may have taken the last AUDITED slot.
                val current = db.habitDao().enabled().map { it.toBrain() }
                if (canEnter(to, current)) {
                    written += applyTransition(row, to, TransitionReason.GRADUATED, now)
                }
            }
        }
        written
    }

    private suspend fun applyTransition(
        row: HabitRow,
        to: Stage,
        reason: TransitionReason,
        now: Long,
    ): StageTransitionRow {
        val entry = StageTransitionRow(habitId = row.id, from = row.stage, to = to, reason = reason, at = now)
        db.stageTransitionDao().insert(entry)
        db.habitDao().setStage(row.id, to.name, now)
        // A demotion is a docket line; a graduation is not. The Ledger is the failure surface only.
        if (reason == TransitionReason.DEMOTED_CAUGHT || reason == TransitionReason.DEMOTED_COLLAPSE) {
            writeLedger(LedgerKind.DEMOTION, row.id, now)
        }
        return entry
    }

    val transitions: Flow<List<StageTransitionRow>> = db.stageTransitionDao().observeAll()

    // ── The kill criterion's instrument ─────────────────────────────────────

    /**
     * Record an app open. RESOLUTIONS §D: the project pre-commits to die on "unprompted opens <
     * 1.0/day" and nothing recorded one.
     *
     * Fire-and-forget on the app scope: an open that is only recorded if the user stays on screen
     * long enough to let a suspend function finish would bias the metric upward for engaged users,
     * which is precisely the direction that would let the app pass its own kill test dishonestly.
     */
    fun recordAppOpen(source: AppOpenSource, at: Long = clock()) {
        scope.launch { db.appOpenDao().insert(AppOpenRow(at = at, source = source)) }
    }

    /**
     * THE 4-WEEK ROLLING READ. The number the project agreed to die on.
     *
     * Denominator is a fixed 28 days, not "days since install" and not "days with any activity".
     * Both alternatives flatter the app: the first inflates the rate in week one, the second deletes
     * exactly the days that prove the thesis failed — a man who did not open the app for nine days
     * has produced nine data points, not a gap.
     */
    suspend fun unpromptedOpensPerDay(now: Long = clock()): Double = withContext(Dispatchers.IO) {
        val since = now - ROLLING_WINDOW_DAYS * 86_400_000L
        db.appOpenDao().unpromptedCountSince(since).toDouble() / ROLLING_WINDOW_DAYS
    }

    suspend fun lastAppOpenAt(): Long? = db.appOpenDao().lastOpenAt()

    // ── Weight: trend only ──────────────────────────────────────────────────

    /**
     * The EWMA trend, computed by the brain's `weightTrend` on its daily interpolated grid.
     *
     * This returns a curve and never a headline number, and there is nothing here that colours it,
     * arrows it, compares it to a goal, or hands it to the pipeline. `Pillar.WEIGHT` has no habit
     * row (see Seed.kt), so no `day`, no compliance, no stage, no penalty. Rip does not know the
     * number exists.
     */
    val weightTrend: Flow<List<TrendPoint>> = db.weightDao().observeAll().map { rows ->
        weightTrend(rows.map { it.toBrain() })
    }

    suspend fun addWeight(kg: Double, at: Long = clock()) = withContext(Dispatchers.IO) {
        db.weightDao().insert(WeightEntryRow(at = at, kg = kg))
    }

    suspend fun weightHistory(): List<WeightEntry> = db.weightDao().all().map { it.toBrain() }

    private companion object {
        /** Widest window any brain gate reads: AUDITED's 56 days, plus room for the repair window. */
        const val MAX_WINDOW_DAYS = 90L
        const val ROLLING_WINDOW_DAYS = 28L

        /** SPEC §4.6: four dark days is a comeback, not a lapse. */
        const val COMEBACK_DARK_MS = 4L * 86_400_000L
    }
}

/**
 * Epoch millis -> epoch day, **in the user's local zone**. The brain counts in days and never in
 * milliseconds.
 *
 * It must be *local*, and the bug it replaces is subtle and expensive. `Math.floorDiv(this,
 * 86_400_000L)` counts days from the UTC epoch, so "today" flipped at UTC midnight — which for a user
 * in the Americas is late afternoon and for Asia-Pacific is mid-morning. Everything the user
 * experiences as "a day" is already local: wind-down/wake (`localMinutes`, `inWindDownWindow`) and the
 * Archive's own frame labels (`ProofSource` uses `toLocalDate()`). The demand/compliance day must
 * agree with them, or a habit photographed at 10am re-appears as an unmet demand that same evening the
 * instant UTC rolls over, and graduation is scored against a day that is not the user's.
 *
 * `ZoneId.systemDefault()` is read at call time so a flight across zones takes effect immediately,
 * the same instant the wind-down window it must line up with does.
 */
internal fun Long.toEpochDay(): Long =
    java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
