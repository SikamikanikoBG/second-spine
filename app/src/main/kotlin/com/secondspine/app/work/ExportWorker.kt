package com.secondspine.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondspine.app.data.Graph
import com.secondspine.app.export.ExportStatus
import com.secondspine.app.export.Exporter

/**
 * THE EXPORT. Sunday 03:00, and on demand.
 *
 * "If the app only retains him because leaving destroys the archive, it was not a product, it was a
 * lock. Ship the export and find out."
 *
 * ## Three o'clock in the morning, and no notification
 *
 * The hour is SPEC §8.6's and it is a good one: the export is the least interesting event in the
 * product and it should happen while he is asleep, on a charger he probably isn't using, and be
 * finished before he wakes. 03:00 is also, for almost every user, *inside the wind-down window*, which
 * is why this worker cannot be trusted to speak for itself. [WorkNotifications] routes every post
 * through [WorkGuards], so the fourteen-day alarm that fires at 03:00 is silently held; the
 * un-dismissable banner in TODAY is the surface that actually carries it, and it is there when he
 * looks. A background job that wakes a man at three in the morning to tell him about a *file copy* has
 * lost the plot completely — and it would be doing it on the pillar ranked #1 for all-cause mortality,
 * to complain about the app's own plumbing.
 *
 * ## The failure ladder, and why NoFolder is not `retry`
 *
 * - **Success** — record it, clear the alarm, say nothing.
 * - **Failed** — `retry`, with exponential backoff from 30s (see [SecondSpineWork]). A
 *   `DocumentsProvider` can be busy, a card can be unmounted, a sync client can hold a lock. These
 *   are transient and worth another go.
 * - **NoFolder** — `failure`, and never `retry`. Backing off exponentially against a condition that
 *   only a human can clear is a battery drain that pretends to be progress. The state is not "the
 *   export failed", it is "the export has nothing to write to", and the correct response is the loud
 *   banner, not a retry curve. `Result.failure()` on a *periodic* worker does not cancel future
 *   periods — next Sunday runs regardless — so this gives up on the attempt without giving up on the
 *   feature. SPEC §8.6: a failure "DOES NOT silently retire".
 *
 * Every outcome, including both failures, is written to the run log before this returns. An export
 * that fails quietly for six weeks looks exactly like one that works.
 */
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.install(applicationContext)

        return when (val result = Exporter.export(applicationContext)) {
            is Exporter.Result.Success -> {
                Log.i(TAG, "exported: ${result.newFiles} new, ${result.filesInArchive} total")
                WorkNotifications.clearExportFailing(applicationContext)
                Result.success()
            }

            is Exporter.Result.NoFolder -> {
                Log.w(TAG, "no export folder. The archive is not leaving.")
                alarmIfOverdue()
                Result.failure()
            }

            is Exporter.Result.Failed -> {
                Log.w(TAG, "export failed: ${result.error}")
                alarmIfOverdue()
                Result.retry()
            }
        }
    }

    /**
     * Fourteen days, once — and only once it is actually fourteen days.
     *
     * The alarm is not fired on the first failed run, and that restraint is the whole reason it works.
     * A weekly job that shouts the first time a provider is busy has trained him, by month two, to
     * dismiss the one notification in this app that is genuinely about *his* interests rather than the
     * app's. The banner and the suspension are load-bearing precisely because they are rare.
     */
    private suspend fun alarmIfOverdue() {
        val health = ExportStatus.now(applicationContext)
        if (!health.failingLoudly) return
        WorkNotifications.postExportFailing(applicationContext, health.daysSince)
    }

    private companion object {
        const val TAG = "SecondSpine/Export"
    }
}
