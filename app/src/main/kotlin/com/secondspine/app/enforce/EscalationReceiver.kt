package com.secondspine.app.enforce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * THE ALARM LANDS HERE, AND THE FIRST THING THAT HAPPENS IS THE ONLY THING THAT MATTERS.
 *
 * SPEC §6.4, and the parenthesis is the whole file:
 *
 * ```
 * → AlarmReceiver.onReceive()
 *     → startForegroundService() SYNCHRONOUSLY, before any goAsync() work
 *       (the exact-alarm temp allowlist is short; goAsync-then-start throws
 *        ForegroundServiceStartNotAllowedException)
 * ```
 *
 * ### Why `goAsync()` first is the bug that looks like the fix
 *
 * When an exact alarm fires, the system drops the app onto a **temporary power allowlist** for a few
 * seconds. That allowlist is what makes `startForegroundService` legal from the background at 06:41.
 * `goAsync()` does not extend it — it extends the *receiver's* life, which is a different clock. So
 * the natural-looking shape — `goAsync()`, hop to a coroutine, read the database, decide, then start
 * the service — reliably throws `ForegroundServiceStartNotAllowedException` on 12+, because by the
 * time the coroutine gets there the allowlist window has closed. The exception arrives on a
 * background thread inside a receiver that has already returned, which is to say: the ladder is dead
 * and there is nothing in logcat pointing at this file.
 *
 * So `onReceive` does no work. It reads two clocks, starts the service, and returns. Every decision
 * is the service's, taken inside a legally-started foreground context with the allowlist already
 * spent on the one thing that needed it.
 *
 * The decoy path is the single exception, and it is the exception precisely because it is a no-op —
 * see [ACTION_DECOY].
 */
internal class EscalationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // BOTH CLOCKS, FIRST, AND HERE. This is the last honest instant.
        //
        // `wallAt` and `elapsedAt` must be read at the moment the alarm actually landed. Reading them
        // in the service — even 40 ms later, even reliably — would mean the AUTO-VOID's `|fired -
        // scheduled|` includes our own service start latency, and a slow cold start would then look
        // exactly like the OEM battery murder the check exists to detect. The app would void
        // penalties it earned and blame Xiaomi for its own dex loading.
        val wallAt = System.currentTimeMillis()
        val elapsedAt = SystemClock.elapsedRealtime()
        val challengeId = intent.getStringExtra(EXTRA_CHALLENGE_ID) ?: return

        when (intent.action) {
            ACTION_RUNG -> {
                val rung = intent.getIntExtra(EXTRA_RUNG, -1)
                val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, 0L)
                if (rung < 0) return
                // SYNCHRONOUSLY. Nothing above this line touches a database, and nothing below it
                // runs before the service is started.
                startService(
                    context,
                    Intent(context, EscalationService::class.java).apply {
                        action = EscalationService.ACTION_EVENT
                        putExtra(EscalationService.EXTRA_CHALLENGE_ID, challengeId)
                        putExtra(EscalationService.EXTRA_EVENT, ServiceEvent.RUNG.name)
                        putExtra(EscalationService.EXTRA_RUNG, rung)
                        putExtra(EscalationService.EXTRA_SCHEDULED_FOR, scheduledFor)
                        putExtra(EscalationService.EXTRA_WALL_AT, wallAt)
                        putExtra(EscalationService.EXTRA_ELAPSED_AT, elapsedAt)
                    },
                )
            }

            /**
             * A DECOY FIRED. Stamp it, and do nothing else. This is the only branch that may
             * `goAsync`, because it starts no service and therefore needs no allowlist.
             *
             * A decoy is a lie told to `getNextAlarmClock()`. Its entire job was done hours ago, on
             * the lock screen, by existing. When it finally goes off there is nothing to perform: no
             * sound, no vibration, no notification, no state change. `setAlarmClock` schedules a
             * PendingIntent and makes no noise of its own, so this genuinely is silent.
             */
            ACTION_DECOY -> {
                val code = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
                val pending = goAsync()
                BackgroundWrites.launch {
                    runCatching { ScheduleStore.get(context).stampDecoyCancelled(code, wallAt) }
                    pending.finish()
                }
            }

            /**
             * BREAK GLASS, from the notification. One tap. No service start, no confirmation, no
             * cognition, no delay — and deliberately not routed through the FGS, because starting a
             * service is a thing that can fail and this is a thing that cannot be allowed to.
             */
            ACTION_BREAK_GLASS -> {
                val pending = goAsync()
                BackgroundWrites.launch {
                    runCatching {
                        BreakGlassRecorder.record(context, null)
                        Coordinator.deliver(context, challengeId, com.secondspine.coach.Event.BreakGlass, wallAt)
                    }
                    pending.finish()
                }
            }

            EscalationService.ACTION_LOCK_EXPIRY -> startService(
                context,
                serviceIntentFor(context, challengeId, ServiceEvent.LOCK_EXPIRY),
            )

            EscalationService.ACTION_CANARY -> {
                // The canary's verdict is arithmetic, and it is computed here for the same reason the
                // clocks are: `|fired - scheduled|` must not include our own service start.
                val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, 0L)
                val late = kotlin.math.abs(wallAt - scheduledFor)
                val verdict = if (late > CANARY_FAIL_MS) "FAIL" else "PASS"
                startService(
                    context,
                    serviceIntentFor(context, challengeId, ServiceEvent.CANARY, verdict),
                )
            }
        }
    }

    /**
     * `startForegroundService`, and it must not be allowed to take the app down with it.
     *
     * On 12+ a background start that the system disagrees with throws
     * `ForegroundServiceStartNotAllowedException` — inside a receiver, on the main thread, which is a
     * crash. The ladder failing to fire is a bug. The ladder failing to fire *and* killing the
     * process is a bug that also loses the app's own crash-free-session number and, on some OEMs,
     * earns it a place in the "restricted" bucket that makes every future alarm worse.
     */
    private fun startService(context: Context, intent: Intent) {
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    companion object {
        const val ACTION_RUNG = "com.secondspine.app.enforce.RUNG"
        const val ACTION_DECOY = "com.secondspine.app.enforce.DECOY"
        const val ACTION_BREAK_GLASS = "com.secondspine.app.enforce.BREAK_GLASS"

        const val EXTRA_CHALLENGE_ID = "challenge_id"
        const val EXTRA_RUNG = "rung"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
        const val EXTRA_REQUEST_CODE = "request_code"

        /** SPEC §6.6: `delta > 60 s` on the +47 min canary is the platform murdering us. */
        const val CANARY_FAIL_MS = 60_000L
    }
}
