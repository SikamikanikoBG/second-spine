package com.secondspine.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondspine.app.data.Graph
import com.secondspine.app.enforce.ChallengePlanner

/**
 * ARM THE DAY, even on a day the app is never opened.
 *
 * The planner also runs on every app open (from `HomeViewModel.onResume`) and the moment the wizard
 * finishes, and those cover the engaged user. This worker covers the one who does not open the app —
 * which, for a coach whose entire job is to reach a man who is avoiding the thing, is not the edge
 * case but the main case. If the ladder only armed when the user opened the app, it would only ever
 * nag people who did not need nagging.
 *
 * Scheduled early (SPEC's 05:00 slot, before most people's wake window opens) so that when the day's
 * demand window opens, a challenge is already sitting inside it at an unpredictable time. WorkManager
 * is inexact and that is fine here: this job only *plans*: it hands the exact-alarm scheduling to
 * `AlarmManager` via `Enforcement.arm`, and a plan that lands ten minutes late still fires an alarm
 * on time.
 *
 * Idempotent: `ChallengePlanner` keys each challenge on (habit, local day) and steps over anything
 * already armed, so a double-run — worker plus an app open five minutes later — arms nothing twice.
 */
class PlannerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.install(applicationContext)
        return runCatching {
            ChallengePlanner.planToday(applicationContext)
            Result.success()
        }.getOrElse {
            Log.w(TAG, "planning failed", it)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "SecondSpine/Planner"
    }
}
