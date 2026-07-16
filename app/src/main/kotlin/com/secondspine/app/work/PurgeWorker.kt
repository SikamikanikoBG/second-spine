package com.secondspine.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondspine.app.data.Graph

/**
 * THE PURGE. Daily, 04:30, no constraints, unconditional.
 *
 * This is the job that makes *"I'm going to remember"* false, and it is worth being precise about why
 * it is a job at all rather than a `WHERE` clause. The Ledger's read query already closes its own
 * 28-day window (`LedgerDao.surviving` has no `since` parameter for a caller to widen), so nothing
 * would ever *display* a purged row even if this worker never ran. This exists because a filter is a
 * promise and a `DELETE` is a fact. *"I've tried, it's gone"* has to be true about the disk, not about
 * the query — otherwise the rows are all still there, forever, one forgotten `WHERE` away from being
 * spoken, and the design's central claim is a UI convention.
 *
 * ## No carve-out. That is the entire point.
 *
 * SPEC §3.4 wrote `... AND cluster_repeat_within_28d = 0`: keep the repeat offences, delete the
 * one-offs. RESOLUTIONS §B deleted that clause, and the reasoning is the sharpest in the document —
 * the carve-out *is* the rumination machine. It keeps precisely the rows a person would lie awake
 * replaying and throws away the harmless ones. It would have shipped a system whose memory is
 * optimised for grievance, wearing a 28-day forgetting policy as a costume.
 *
 * So [Graph.repository]'s `purgeOldEvidence` takes no `kind`, no `habitId` and no `repeatCount`,
 * because every one of those is a place to put an exception and the exception is the bug. A
 * thrice-repeated failure is deleted on exactly the same day as a one-off. Rip's entire addressable
 * memory is what survives this job, so he *structurally cannot* hold a pattern against you. He's a
 * VHS ghost. The tape degrades.
 *
 * ## What it does not touch
 *
 * `proof`. Not one row, not ever. **Evidence of failure purges at 28 days; evidence of work is kept
 * forever.** SPEC §8.6 is explicit that there is no 90-day media GC either — deleting the archive on a
 * timer would destroy the only asset this product claims compounds, which was a privacy instinct about
 * to kill the product. The asymmetry is the design: the app forgets what you did wrong and remembers
 * what you did.
 */
class PurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.install(applicationContext)
        return runCatching {
            Graph.repository.purgeOldEvidence()
            Result.success()
        }.getOrElse {
            Log.w(TAG, "purge failed", it)
            // Retry rather than fail: a purge that silently gives up leaves the app holding rows it
            // has promised the user are gone, which is the one lie this feature cannot tell.
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "SecondSpine/Purge"
    }
}
