package com.secondspine.app.enforce

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.secondspine.coach.Event
import com.secondspine.coach.Rung

/**
 * THE ESCALATION FOREGROUND SERVICE — the process's claim on the next ninety seconds.
 *
 * Everything the ladder does happens inside this service, and the reason is the temp allowlist: when
 * an exact alarm fires, the app is briefly allowed to start a foreground service, and that permission
 * is the only thing standing between "the ladder works" and "the ladder works on your desk". So
 * [EscalationReceiver] spends the allowlist immediately and hands the work here, where there is a
 * legal foreground context and no clock running out.
 *
 * ### Why the work is not in the receiver
 *
 * A `BroadcastReceiver` has ~10 seconds before it is an ANR, and `goAsync()` buys ~10 more with no
 * process-priority protection at all: the moment `onReceive` returns, the process is a background
 * process and eligible to be killed mid-`step`. Killing it between "effects produced" and "state
 * committed" is precisely the double-penalty crash SPEC §6.3's write-ahead exists to prevent. A
 * foreground service is not a formality here; it is the thing that makes the transaction survivable.
 */
internal class EscalationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
        // Row 5's tracker needs to be warm before it is read. Registering here — rather than at read
        // time — is what gives the AvailabilityCallback a moment to deliver current state.
        CameraAvailability.register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIRST LINE. `startForegroundService` gives ~5 seconds to call `startForeground` or the
        // system kills the process with ForegroundServiceDidNotStartInTimeException. Every early
        // return below this point has already satisfied that contract; not one of them may return
        // before it.
        promote(ServiceNotification.placeholder(this))

        val challengeId = intent?.getStringExtra(EXTRA_CHALLENGE_ID)
        val eventName = intent?.getStringExtra(EXTRA_EVENT)
        if (challengeId == null || eventName == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val serviceEvent = runCatching { ServiceEvent.valueOf(eventName) }.getOrNull()
        if (serviceEvent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val state = ScheduleStore.get(this).load(challengeId)
        if (state != null) promote(ServiceNotification.build(this, challengeId, state.rung))

        val now = System.currentTimeMillis()
        val event = when (serviceEvent) {
            ServiceEvent.RUNG -> alarmEvent(intent, challengeId) ?: run {
                stopSelf(startId)
                return START_NOT_STICKY
            }

            ServiceEvent.REBOOT -> Event.Reboot(bootAt = now)

            else -> serviceEvent.toEvent(intent.getStringExtra(EXTRA_EXTRA)) ?: run {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        // The whole transaction — load, step, COMMIT, interpret — happens here, synchronously, on a
        // thread the system has promised not to kill for the next few seconds.
        val next = Coordinator.deliver(this, challengeId, event, now)

        // R4 is the one rung that must outlive this call: the 90-second expiry alarm is armed and the
        // lock is up, and the FGS notification is carrying BREAK GLASS for as long as it is. Every
        // other rung is a noise that has already been made by the time we get here.
        val holding = next != null && !next.phase.terminal && next.rung == Rung.R4_LOCK
        if (!holding) {
            FlatVoice.release()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Build `AlarmFired` with BOTH clocks — the AUTO-VOID's whole input.
     *
     * `elapsedAt` is the fire instant expressed on the *monotonic* timeline and then translated back
     * into epoch millis through the reference taken at arm time. If the wall clock has not moved, the
     * two agree to within milliseconds. If it has, they diverge by exactly the amount it moved, and
     * `step` voids the challenge — no penalty, no accusation, because a timezone flight and a cheat
     * produce the same two numbers and the app does not accuse a man for boarding a plane.
     *
     * The `bootCount` guard is what keeps a reboot from manufacturing that divergence out of nothing:
     * `elapsedRealtime()` restarts at zero, so a reference from a previous boot is arithmetic
     * garbage. When the boot has changed, both clocks are reported as agreeing — the tamper check
     * abstains rather than guessing — and `PLATFORM_LATE` still catches a genuinely late alarm.
     */
    private fun alarmEvent(intent: Intent, challengeId: String): Event.AlarmFired? {
        val rungOrdinal = intent.getIntExtra(EXTRA_RUNG, -1)
        if (rungOrdinal !in Rung.entries.indices) return null
        val rung = Rung.entries[rungOrdinal]
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, 0L)
        val wallAt = intent.getLongExtra(EXTRA_WALL_AT, System.currentTimeMillis())
        val elapsedNow = intent.getLongExtra(EXTRA_ELAPSED_AT, 0L)

        val ref = ScheduleStore.get(this).armRef(challengeId, rung)
        val elapsedAt = if (ref != null && ref.ref.bootCount == bootCount(this)) {
            ref.ref.wall + (elapsedNow - ref.ref.elapsed)
        } else {
            wallAt
        }

        // The real alarm has rung, so the decoys have nothing left to hide. Cancelling the losers is
        // the first thing that happens at true fire time, before the ladder is even consulted —
        // SPEC §6.5: "cancel the losers at true fire time".
        AlarmScheduler(this).cancelDecoys(challengeId, wallAt)

        return Event.AlarmFired(
            rung = rung,
            scheduledFor = scheduledFor,
            wallAt = wallAt,
            elapsedAt = elapsedAt,
        )
    }

    private fun promote(notification: android.app.Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                ServiceNotification.ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }
    }

    override fun onDestroy() {
        FlatVoice.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_EVENT = "com.secondspine.app.enforce.EVENT"
        const val ACTION_LOCK_EXPIRY = "com.secondspine.app.enforce.LOCK_EXPIRY"
        const val ACTION_CANARY = "com.secondspine.app.enforce.CANARY"

        const val EXTRA_CHALLENGE_ID = "challenge_id"
        const val EXTRA_EVENT = "event"
        const val EXTRA_EXTRA = "extra"
        const val EXTRA_RUNG = "rung"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
        const val EXTRA_WALL_AT = "wall_at"
        const val EXTRA_ELAPSED_AT = "elapsed_at"

        /**
         * Hand an event to the ladder from anywhere in the app.
         *
         * Wrapped in `runCatching` because a background start can be refused, and the callers are
         * things like the break-glass button: a safety control must not be able to throw at the man
         * pressing it.
         */
        fun deliver(context: Context, challengeId: String, event: ServiceEvent, extra: String? = null) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    serviceIntentFor(context, challengeId, event, extra),
                )
            }.onFailure {
                // The service could not start — a restricted app bucket, or an OEM in a bad mood.
                // The event still has to land: these are proofs, confessions and break-glass taps,
                // and losing one because a service start was refused would be the app punishing him
                // for a platform decision. Straight to the coordinator, off-thread.
                BackgroundWrites.launch {
                    val brainEvent = event.toEvent(extra) ?: return@launch
                    runCatching { Coordinator.deliver(context, challengeId, brainEvent) }
                }
            }
        }
    }
}
