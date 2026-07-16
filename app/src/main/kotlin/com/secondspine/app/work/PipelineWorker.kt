package com.secondspine.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondspine.app.data.Graph
import com.secondspine.coach.TransitionReason

/**
 * THE PIPELINE. Daily, 04:10.
 *
 * **This is where Rip loses his job, on measured evidence, with no vote.**
 *
 * It runs at ten past four in the morning, unattended, and that is not an implementation detail — it
 * is the whole ethical architecture of the product expressed as a cron time. The character who spends
 * all week demanding, mocking and locking has *no input whatsoever* into the one decision that
 * matters. He cannot object to a graduation. He cannot reclaim a habit he lost. He cannot notice that
 * the odometer is about to tick down and find a reason it shouldn't. He is not consulted, he is not
 * awake, and he is not asked. A job scheduler is.
 *
 * That is what makes the coercion autonomy-preserving rather than manipulative. The authority to
 * demand traces back to a contract the user signed in the intake, and the authority to *stop*
 * demanding traces to his own data, evaluated by a pure function he could read. Rip is an employee of
 * a process that is firing him one habit at a time, and this worker is the process. It is also,
 * incidentally, why the character's ten-month arc costs zero authored content: the arc is
 * `jurisdiction()`, and this job is the only thing that moves it.
 *
 * ## The two things it writes, and the asymmetry between them
 *
 * `CoachRepository.runPipeline` checks **demotion before graduation** and writes a `stage_transition`
 * row either way. The reasons available are frozen by the brain: GRADUATED, DEMOTED_CAUGHT,
 * DEMOTED_COLLAPSE, SUBDIVIDED, USER_RETIRED. **There is no CONFESSED value**, and its absence is
 * load-bearing (RESOLUTIONS §A2): "no confession ever demotes; only being caught, or collapsing,
 * does." A confession cannot demote you because a demotion-by-confession is not a sentence this
 * codebase can express.
 *
 * ## Why it retries instead of failing
 *
 * A skipped day here is a day a habit that had earned its graduation stays on Rip's desk. Nobody would
 * see a bug; they would see a coach who kept a job he had lost, which is exactly the outcome the "no
 * vote" rule exists to make impossible. Silence is the failure mode to avoid.
 */
class PipelineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.install(applicationContext)
        return runCatching {
            val written = Graph.repository.runPipeline()
            for (t in written) {
                Log.i(TAG, "${t.habitId}: ${t.from} -> ${t.to} (${t.reason})")
            }
            // Not notified. A graduation is the Tape's to announce on Sunday — it is the reckoning
            // segment, and it is the only sentimental thing in the product. Firing a push at 04:10 to
            // say "you have graduated" would spend the best beat in the arc on a lock screen at dawn.
            if (written.any { it.reason == TransitionReason.GRADUATED }) {
                Log.i(TAG, "jurisdiction fell. He finds out on Sunday.")
            }
            Result.success()
        }.getOrElse {
            Log.w(TAG, "pipeline failed", it)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "SecondSpine/Pipeline"
    }
}
