package com.secondspine.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * THE SCHEDULE — every background job in v1, and the reasons there are only five.
 *
 * SPEC §8.8 lists seventeen. RESOLUTIONS §E's verdict on that list is on the record: *"fourteen
 * WorkManager jobs... a studio project, not a sprint"*, landing *"past the horizon of its own kill
 * criterion"*. So this is the v1 line, and each cut is a decision rather than an omission:
 *
 * | Job | When | Why it is here |
 * |---|---|---|
 * | [PipelineWorker] | 04:10 daily | Rip loses his job on measured evidence. The arc itself. |
 * | [AuditWorker] | 04:20 daily | ~15%, <=2/day. Comedy budget, not a deterrent. |
 * | [PurgeWorker] | 04:30 daily | The 28-day unconditional purge. Makes "I'll remember" false. |
 * | [ExportWorker] | Sun 03:00 | The ship blocker. His data leaves whether the app likes it or not. |
 * | [TapeWorker] | Sun 20:00 | The weekly report. Its open-rate is a kill-criterion metric. |
 *
 * ## The one that is deliberately not here
 *
 * **`HeartbeatWorker` — every 15 minutes — is not shipped, and this is a correctness decision, not a
 * scope cut.** RESOLUTIONS §C lists it under "platform lies to delete": *"A 15-minute WorkManager
 * heartbeat invents `OEM_KILL` evidence out of ordinary Doze and hands it to the character's mouth,
 * violating the spec's own >90%-accuracy narration bar."*
 *
 * The mechanism is worth spelling out, because the job looks so reasonable. WorkManager's floor is
 * fifteen minutes and it is **inexact by contract**. Doze, App Standby buckets and every vendor's
 * battery manager defer that work routinely and legitimately, on phones that are working perfectly. A
 * gap in the heartbeat table therefore means "Android did its job" far more often than it means "your
 * OEM murdered me" — and the design hands those gaps straight to a character who says so *out loud*,
 * as his one sanctioned grudge. The result is Rip confidently accusing a man's phone of a crime that
 * did not happen, on a schedule, forever. That is the same failure as FRAME_REPLAY: a probabilistic
 * signal wired to the one mouth in the app that must never be wrong.
 *
 * If OEM evidence is wanted, derive it from **an alarm that actually failed to fire** — a scheduled
 * rung with a write-ahead row and no execution is a fact, not an inference. That belongs to the
 * escalation path, which owns the alarms and already stamps them. It does not belong to a poll.
 *
 * ## The 04:xx ordering, which is not arbitrary
 *
 * Pipeline (10) runs before Audit (20) so the sampler reads today's stages, not yesterday's — a habit
 * that graduated to TRUSTED overnight must not be audited this morning. Audit runs before Purge (30)
 * because both read the trailing 28 days and it is better for the sampler to see one extra dying row
 * than to miss the last day of a window. Nothing in the group can wake him: all three are pure
 * database work, and the only job that can speak at all is [TapeWorker].
 */
object SecondSpineWork {

    const val PIPELINE = "second_spine.pipeline"
    const val AUDIT = "second_spine.audit"
    const val PURGE = "second_spine.purge"
    const val EXPORT = "second_spine.export"
    const val EXPORT_NOW = "second_spine.export.now"
    const val TAPE = "second_spine.tape"
    const val PLANNER = "second_spine.planner"
    const val PLANNER_NOW = "second_spine.planner.now"

    /**
     * Wire everything up. Idempotent — call it from `Application.onCreate` on every start.
     *
     * `ExistingPeriodicWorkPolicy.UPDATE` rather than `KEEP`: KEEP means a schedule change shipped in
     * an update never reaches anyone who already has the app, which on a sideloaded single-user
     * experiment means the developer is the one person who never gets the fix.
     */
    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        WorkNotifications.ensureChannels(context)

        // ARM THE LADDER. Without this the app is silent forever: `Enforcement.arm` has no other
        // caller, so nothing schedules the notification/vibrate/alarm/voice rungs. Daily at 05:00 —
        // before most wake windows open — plus one immediate pass on every start so a fresh install
        // (and anyone updating past the silent build) has today's reminder armed without opening the
        // app. Both are idempotent per (habit, day); the immediate pass keeps at most one queued.
        wm.enqueueUniquePeriodicWork(
            PLANNER,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<PlannerWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(untilDaily(5, 0), TimeUnit.MILLISECONDS)
                .build(),
        )
        wm.enqueueUniqueWork(
            PLANNER_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PlannerWorker>().build(),
        )

        wm.enqueueUniquePeriodicWork(
            PIPELINE,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<PipelineWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(untilDaily(4, 10), TimeUnit.MILLISECONDS)
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            AUDIT,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AuditWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(untilDaily(4, 20), TimeUnit.MILLISECONDS)
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            PURGE,
            ExistingPeriodicWorkPolicy.UPDATE,
            // No constraints. Not BatteryNotLow, not DeviceIdle, not charging. The purge is the app
            // keeping a promise about what it has forgotten, and a promise that only holds when the
            // battery is above 15% is not a promise. It is three DELETEs.
            PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(untilDaily(4, 30), TimeUnit.MILLISECONDS)
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            EXPORT,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ExportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(untilWeekly(DayOfWeek.SUNDAY, 3, 0), TimeUnit.MILLISECONDS)
                .setConstraints(exportConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            TAPE,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TapeWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(untilWeekly(DayOfWeek.SUNDAY, 20, 0), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /**
     * ONE-TAP FULL EXPORT — "at top level, always, offline, open format" (SPEC §8.6).
     *
     * `ExistingWorkPolicy.KEEP`, so hammering the button queues one export rather than ten. It is
     * unconstrained, unlike the weekly run: he asked for it, right now, and an app that answers "not
     * while your battery is at 14%" when a man asks for his own data back has revealed which side it
     * is on.
     */
    fun exportNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPORT_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ExportWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    /**
     * SPEC §8.6: `BatteryNotLow`, `requiresCharging = false`.
     *
     * Charging is not required on purpose. It is the obvious constraint for a job that copies a few
     * hundred files at 3am, and it is wrong here: a man who charges on his desk at work and not at his
     * bedside would silently never export, and would find out at fourteen days. The export must not
     * depend on a habit the app did not ask him to have.
     */
    private fun exportConstraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(false)
        .build()

    // ── clocks ──────────────────────────────────────────────────────────────
    //
    // WorkManager takes a delay, never a time of day, so "04:30 daily" has to be computed as "how
    // long until the next 04:30". Both helpers use the local zone and let java.time handle the
    // discontinuities: on the March DST Sunday, 03:00 may not exist, and `ZonedDateTime.with` moves
    // to the next valid instant instead of throwing. An export that skips one Sunday a year because
    // an hour did not exist is a bug that reproduces twice a decade and is never found.

    internal fun untilDaily(hour: Int, minute: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var next = now.with(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }

    internal fun untilWeekly(
        day: DayOfWeek,
        hour: Int,
        minute: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long {
        var next = now.with(LocalTime.of(hour, minute)).with(TemporalAdjusters.nextOrSame(day))
        if (!next.isAfter(now)) {
            next = next.with(TemporalAdjusters.next(day))
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }
}
