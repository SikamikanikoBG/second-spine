package com.secondspine.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondspine.app.data.Graph

/**
 * THE TAPE. Sunday, 20:00.
 *
 * Build it, then say it's ready. That is the entire job, and both halves are restrained on purpose.
 *
 * ## Why it is composed here and not when he opens it
 *
 * `composeTape` runs every grammar gate as a hard `check()` on the finished object: a mocking register
 * in front of a SCOFF-positive user throws, a DISAPPOINTED line with no CAUGHT_FAKE behind it throws,
 * a roast on a COACH DOWN week throws. SPEC §9.9 is explicit that the gates run at compose time rather
 * than at render — *"a suppression that happens in the UI is a suppression that a UI bug can undo on
 * the worst night of somebody's year."* Composing here means a violation is a failed background job on
 * a Sunday evening. Composing in the composable means it is a joke at a man who has just told the app
 * he has an eating disorder.
 *
 * ## COACH DOWN, and the notification that does not fire
 *
 * `Tape.notifies` is false when the week was a collapse *and* the depressive signature is present. The
 * edition still gets built — it is still there, it still opens, the Montage of his own photographs is
 * still the first thing in it — but the phone stays silent and **waits to be opened**. An app that
 * pushes a weekly report at somebody in the middle of a depressive episode has mistaken a
 * notification for a hand on a shoulder.
 *
 * The wind-down gate applies on top of that, keyed on his own times (RESOLUTIONS §D). If his
 * wind-down starts at 19:30, the Tape composes at 20:00 and says nothing at all; it is on the home
 * screen when he wakes up. SPEC §8.8: **zero jobs** emit a notification between wind-down and wake.
 * The Tape is a report, not an exception.
 *
 * ## What it does not do
 *
 * It does not render. The Tape screen recomposes from [TapeWeek.compose] when he opens it — same
 * function, same snapshot, same edition, because the composer is deterministic given its inputs. There
 * is no serialized `Tape` blob to migrate, to go stale, or to disagree with the database it came from.
 */
class TapeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.install(applicationContext)
        return runCatching {
            val tape = TapeWeek.compose(applicationContext)
            Log.i(
                TAG,
                "week ${tape.weekId}: jurisdiction=${tape.jurisdiction} rung=${tape.rung} " +
                    "dominant=${tape.dominant} coachDown=${tape.coachDown} " +
                    "segments=${tape.segments.size} seconds=${tape.seconds}",
            )

            val notified = if (tape.notifies) {
                WorkNotifications.postTapeReady(applicationContext, tape.weekId)
                // Reported as attempted, not as delivered: mayNotify() may have declined for his
                // night or for a channel he muted, and neither of those is this worker's business to
                // route around.
                true
            } else {
                Log.i(TAG, "week ${tape.weekId} does not notify. It waits to be opened.")
                false
            }

            TapeMarker.write(
                applicationContext,
                TapeMarker.Marker(
                    weekId = tape.weekId,
                    // Next Sunday's one-rung clamp reads this. It is the only reason the marker exists.
                    rung = tape.rung,
                    composedAt = System.currentTimeMillis(),
                    notified = notified,
                ),
            )
            Result.success()
        }.getOrElse {
            // A failed compose is a real defect — the gates throw here by design — and it must be
            // loud in logcat and invisible to him. There is no "the Tape could not be built" push.
            Log.e(TAG, "tape build failed", it)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "SecondSpine/Tape"
    }
}
