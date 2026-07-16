package com.secondspine.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondspine.app.data.ChallengeRow
import com.secondspine.app.data.ChallengeState
import com.secondspine.app.data.DayRow
import com.secondspine.app.data.Graph
import com.secondspine.app.data.ProofDao
import com.secondspine.app.data.ProofKind
import com.secondspine.app.data.ProofRow
import com.secondspine.app.data.toEpochDay
import com.secondspine.app.export.ExportStatus
import com.secondspine.coach.Stage
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * THE SAMPLED AUDIT. Daily, 04:20. ~15% suspicion-weighted, never more than two a day.
 *
 * ## What an audit is now, and what it stopped being
 *
 * RESOLUTIONS §B: *"Once confession is free the audit rate stops carrying the incentive and becomes a
 * comedy-material budget."* That sentence demotes this entire subsystem, and the demotion is the
 * point. In the original design the audit was the deterrent — the thing that made faking expensive.
 * It could not be, because §A1's arithmetic beat it: confessed days leave the compliance ratio
 * entirely, so honesty strictly dominates deception at every hour for every user, whether an audit
 * ever fires or not. The button is cheaper than lying, always, and no sampling rate is load-bearing
 * next to that.
 *
 * So an audit is not an accusation and it is not a trap. It is a second look that occasionally
 * produces something worth putting on the Tape on Sunday. Budgeted as comedy, capped like comedy.
 *
 * ## "Suspicion-weighted" — and the thing it must never become
 *
 * The weight is derived from one signal: **days he marked complete with no photograph behind them**,
 * over the trailing 28 days. That is a real, dull, arithmetic quantity, and it is emphatically *not* a
 * verdict — UNVERIFIED is not false. Phone in the locker. Dead battery. A gym that bans cameras. It
 * raises the chance of being asked again; it can never, on its own, say anything about him.
 *
 * What is deliberately absent from the weight: pHash proximity, capture timing, "confidence", or any
 * probabilistic model output. RESOLUTIONS §A2 bans FRAME_REPLAY by name because pHash is *engineered
 * to be robust*, so honest re-shots of the same enrolled mug on the same static counter collide, and
 * the app's one insinuation would fire on truthful nights — the #1 rage-uninstall, bought for nothing.
 * A suspicion score that reaches the character's mouth is that same bug wearing a different hat, so
 * this number reaches a `Random.nextDouble()` comparison and nothing else. **It is never displayed,
 * never spoken, never stored, and never explained.** Uncertainty is expressed as suspicion — "I cannot
 * SEE it. Closer." — never as a percentage, and this file is where the percentage stops.
 *
 * ## The four gates before a single audit is issued
 *
 * 1. **Non-TRUSTED stages only** (RESOLUTIONS §B). TRUSTED and RETIRED are off Rip's desk; auditing a
 *    habit he no longer has jurisdiction over would be him reaching back for a job he lost.
 * 2. **Never two demands at once.** If the habit already has an open challenge, no audit. More than
 *    one visible demand turns the coach into a todo list, and todo lists die.
 * 3. **Two a day, total, across everything.** The absolute cap binds before the rate does.
 * 4. **Not while the export is failing loudly.** SPEC §8.6 suspends every non-safety demand until the
 *    archive is leaving again. The app does not get to ask him for a second photograph of his kitchen
 *    while it is failing to give him back the first four hundred.
 */
class AuditWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.install(applicationContext)
        return runCatching { audit(applicationContext, System.currentTimeMillis()) }
            .fold({ Result.success() }, {
                Log.w(TAG, "audit sampling failed", it)
                Result.retry()
            })
    }

    private suspend fun audit(context: Context, now: Long) {
        // Gate 4. The app has no standing to demand anything while it owes him his archive.
        if (ExportStatus.now(context).failingLoudly) {
            Log.i(TAG, "export failing: no audits until the archive is leaving again.")
            return
        }

        val db = Graph.db
        val dayStart = startOfDay(now)
        var issuedToday = db.challengeDao().auditCountSince(dayStart)
        if (issuedToday >= MAX_AUDITS_PER_DAY) return

        // Gate 1.
        val eligible = db.habitDao().enabled()
            .filter { it.stage == Stage.ENFORCED || it.stage == Stage.AUDITED }
        if (eligible.isEmpty()) return

        val since = now - SUSPICION_WINDOW_DAYS * 86_400_000L
        val days = db.dayDao().allSince(since.toEpochDay())
        val provenDays = db.proofDao().observeAllOnce()
            .filter { it.capturedAtWall >= since && it.kind == ProofKind.LIVE && !it.voided }
            .map { it.habitId to it.capturedAtWall.toEpochDay() }
            .toSet()

        // Most suspicious first, so that when the daily cap bites it takes the interesting ones.
        val ranked = eligible
            .map { it to suspicion(it.id, days, provenDays) }
            .sortedByDescending { it.second }

        for ((habit, s) in ranked) {
            if (issuedToday >= MAX_AUDITS_PER_DAY) break
            // Gate 2.
            if (db.challengeDao().openForHabit(habit.id) != null) continue
            if (Random.nextDouble() >= rate(s)) continue

            db.challengeDao().insert(
                ChallengeRow(
                    habitId = habit.id,
                    nonce = nonce(),
                    issuedAt = now,
                    expiresAt = now + AUDIT_WINDOW_MS,
                    // ARMED, not ISSUED. This worker writes a row; it does not make a noise. The
                    // escalation path decides when — and whether — this ever becomes an alarm, and it
                    // is the thing that holds the wind-down interlock and the rung ceilings. SPEC
                    // §8.8: WorkManager is planning only, never the nag.
                    state = ChallengeState.ARMED,
                    isAudit = true,
                ),
            )
            issuedToday++
            Log.i(TAG, "audit armed for ${habit.id}")
        }
    }

    /**
     * The trailing-28-day unverified rate, in `0.0..1.0`.
     *
     * Zero when nothing was claimed — an absence of data is not an absence of trust, and a habit he
     * has not touched all month must not float to the top of the ranking for having done nothing.
     */
    private fun suspicion(
        habitId: String,
        days: List<DayRow>,
        provenDays: Set<Pair<String, Long>>,
    ): Double {
        val claimed = days.filter { it.habitId == habitId && it.completed }
        if (claimed.isEmpty()) return 0.0
        val unverified = claimed.count { (habitId to it.epochDay) !in provenDays }
        return (unverified.toDouble() / claimed.size).coerceIn(0.0, 1.0)
    }

    /**
     * ~15% at rest, rising with suspicion, hard-capped at 30%.
     *
     * A clean habit sits exactly on RESOLUTIONS §B's number. The ceiling exists because the weight is
     * built from UNVERIFIED days, and a man whose gym bans cameras would otherwise walk into a rate
     * that climbs forever on evidence of nothing at all — the app grinding a stranger down over a
     * locker policy. The cap is where that stops.
     */
    private fun rate(suspicion: Double): Double =
        (BASE_RATE * (1.0 + suspicion)).coerceAtMost(MAX_RATE)

    /**
     * The nonce: issued before the capture and bound to it, which is the whole of what makes a proof a
     * proof. `SecureRandom`, because a predictable nonce is a nonce a photograph taken yesterday can
     * answer.
     */
    private fun nonce(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Derived from `now` rather than from a second call to the clock: the cap is a property of the
     *  day being sampled, and a worker that read the clock twice could straddle midnight and issue
     *  four audits in one morning. */
    private fun startOfDay(now: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private companion object {
        const val TAG = "SecondSpine/Audit"

        /** RESOLUTIONS §B: "~15% suspicion-weighted, <=2/day, all non-TRUSTED stages." */
        const val BASE_RATE = 0.15
        const val MAX_RATE = 0.30
        const val MAX_AUDITS_PER_DAY = 2
        const val SUSPICION_WINDOW_DAYS = 28L

        /** Same day, ending before his wind-down could plausibly start. An audit is not an ambush. */
        const val AUDIT_WINDOW_MS = 14 * 3_600_000L
    }
}

/** `ProofDao` exposes the archive as a Flow; the sampler wants one snapshot, once. */
private suspend fun ProofDao.observeAllOnce(): List<ProofRow> = observeAll().first()
