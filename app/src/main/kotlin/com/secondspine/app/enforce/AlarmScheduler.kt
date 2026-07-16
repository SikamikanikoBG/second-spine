package com.secondspine.app.enforce

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.secondspine.coach.Rung
import kotlin.random.Random

/**
 * THE ONLY THING IN THIS APP THAT TOUCHES `AlarmManager`.
 *
 * ### `setAlarmClock`, and why the obvious alternative silently destroys the product
 *
 * SPEC §6.5: *"`AlarmManager.setAlarmClock()`. Doze-exempt, unbatched, unrate-limited. **NOT
 * `setExactAndAllowWhileIdle()`** — rate-limited to ~1 fire per app per 9 minutes in Doze, which will
 * silently collapse a 7-minute ladder into one rung and you will never see it in a log."*
 *
 * That is the whole argument and it is worth restating in the imperative: the ladder's rungs are 7,
 * 11, 9 and 13 minutes apart. Every one of those gaps is under Doze's 9-minute floor for
 * `setExactAndAllowWhileIdle`. Ship that primitive and R0 fires, R1..R4 quietly do not, the app looks
 * like it works on your desk (Doze never engages while you are debugging), and it is a different
 * product on his phone in his pocket at 06:41. There is no log line for this. There is no exception.
 * `setAlarmClock` is the only primitive with the right contract, and it costs the leak that
 * [DecoyPlanner] exists to plug.
 *
 * ### The permission, and the one that is a trap
 *
 * `USE_EXACT_ALARM` is normal, install-time, auto-granted, and **has no user-revocable toggle**.
 * `SCHEDULE_EXACT_ALARM` is denied-by-default on 14+ and revocable in Settings — he *will* flip it
 * while debugging something else, and the ladder will be gone with no notice. Both are declared in
 * the manifest; this class checks [canScheduleExact] anyway and **degrades honestly** rather than
 * throwing, because a `SecurityException` at 06:41 inside a `BroadcastReceiver` is a silent crash
 * loop and the man never learns his coach is dead.
 */
internal class AlarmScheduler(private val context: Context) {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val store get() = ScheduleStore.get(context)

    /**
     * Can we schedule the only primitive that works?
     *
     * On 31+ this is a real question. `USE_EXACT_ALARM` makes it true on 33+ without a dialog; on
     * 31–32 the user may have to grant `SCHEDULE_EXACT_ALARM`. Below 31 exact alarms are simply
     * allowed.
     */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) true
        else runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)

    /**
     * The Settings trip for 31–32, and it is deliberately not called from anywhere in this package.
     *
     * SPEC §6.5: permissions are *"staged at first use, never front-loaded"*, and gaps show as a
     * permanent **COACH INTEGRITY** panel — *"a wound Rip does bits about, never a modal"*. So this
     * hands back an Intent and the decision of when to fire it belongs to a screen, not to the
     * scheduler. Returns null when there is nothing to ask for.
     */
    fun exactAlarmSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExact()) return null
        return Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null),
        )
    }

    /**
     * WRITE-AHEAD IS THE CALLER'S JOB, AND IT HAS ALREADY HAPPENED BY THE TIME YOU ARE HERE.
     *
     * SPEC §6.3: state is committed to `schedule.db` **before** the alarm is scheduled, never after.
     * This method records the arm reference (both clocks + the boot identity) and then schedules.
     * The reference write is what makes the AUTO-VOID arithmetic possible at fire time, so it is
     * also ahead of the schedule call.
     */
    fun armRung(challengeId: String, rung: Rung, at: Long) {
        val ref = clockRef()
        store.recordArm(challengeId, rung, at, ref)
        setAlarmClock(at, rungIntent(challengeId, rung, at))
    }

    /**
     * Arm the decoy set for a window. Idempotent per request code.
     *
     * The real alarm is armed by the caller through [armRung]; this only ever arms lies.
     */
    fun armDecoys(challengeId: String, windowStart: Long, windowEnd: Long, realFireAt: Long, rng: Random = Random.Default) {
        val times = DecoyPlanner.planDecoys(windowStart, windowEnd, realFireAt, rng)
        for ((i, at) in times.withIndex()) {
            val code = requestCode(challengeId, Slot.DECOY_BASE + i)
            store.recordDecoy(code, challengeId, at)
            setAlarmClock(at, decoyIntent(challengeId, code, at))
        }
    }

    /**
     * Put one previously-planned decoy back. Used only by [BootReceiver].
     *
     * A reboot must restore the *same* lies, not invent fresh ones: the decoy times are already
     * committed to `schedule.db`, and re-planning would produce a second, overlapping set while the
     * originals stayed in the table as uncancellable orphans.
     */
    fun rearmDecoy(decoy: Decoy) {
        setAlarmClock(decoy.fireAt, decoyIntent(decoy.challengeId, decoy.requestCode, decoy.fireAt))
    }

    /**
     * CANCEL THE LOSERS. Called at true fire time, from the receiver, before anything else.
     *
     * The decoys have done their work the instant the real alarm rings: the lock screen has been
     * lying all night and there is nothing left to hide. Every uncancelled decoy from here on is a
     * pure liability — it is an alarm that fires for no reason and, worse, one he can eventually use
     * to fingerprint the real one by elimination.
     */
    fun cancelDecoys(challengeId: String, now: Long) {
        for (decoy in store.liveDecoys(challengeId)) {
            alarms.cancel(decoyIntent(decoy.challengeId, decoy.requestCode, decoy.fireAt))
            store.stampDecoyCancelled(decoy.requestCode, now)
        }
    }

    /**
     * THE LOCK'S UNCONDITIONAL 90-SECOND EXPIRY.
     *
     * SPEC §6.4: *"Unconditional 90-second self-expiry regardless of proof. A coarse model's false
     * negative must never trap a man in his own phone. The expiry is a `SystemClock` alarm inside the
     * FGS, not a coroutine — it survives the Activity crashing."*
     *
     * ### Two deliberate contradictions of this file's own rules, and why each is right here
     *
     * **1. `ELAPSED_REALTIME_WAKEUP`, not `RTC`.** This is the one deadline in the app that must not
     * be movable by changing the phone's clock. Everywhere else a clock jump is handled by voiding
     * the challenge — harmless, because the worst case is a penalty he escapes. Here the worst case
     * is a man locked out of his phone because the expiry was pushed into next year. A coroutine
     * `delay` dies with its process; a wall-clock alarm obeys a settings screen. Neither is allowed
     * to be the thing standing between him and his phone.
     *
     * **2. `setExactAndAllowWhileIdle`, which the top of this file bans.** The ban is about Doze's
     * ~9-minute rate limit silently eating a 7-minute ladder. It does not apply here, for a reason
     * that is structural rather than lucky: **Doze requires the screen to be off**, and R4 has just
     * called `setTurnScreenOn(true)` with a foreground service running. The device is definitionally
     * not idle for the ninety seconds this alarm has to survive. `setAlarmClock` — the primitive used
     * everywhere else — is unavailable to us here anyway: it is RTC-only, which loses contradiction 1,
     * and it publishes to `getNextAlarmClock()`, which would put a 90-seconds-from-now alarm on the
     * lock screen and poison the decoy protocol [DecoyPlanner] exists to protect.
     *
     * `LockActivity` carries a second, independent expiry of its own. This one is authoritative
     * because it survives the Activity dying; that one covers this one being delayed. The release is
     * the only mechanism in the ladder with two implementations, and it is the only one that earns
     * them.
     */
    fun armLockExpiry(challengeId: String, inMs: Long) {
        val pi = serviceIntent(
            challengeId,
            Slot.LOCK_EXPIRY,
            EscalationService.ACTION_LOCK_EXPIRY,
        )
        alarms.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + inMs,
            pi,
        )
    }

    /**
     * The OEM canary at +47 min. SPEC §6.6.
     *
     * Note the primitive: this one is `setAlarmClock` too, because the canary is only meaningful if
     * it is scheduled exactly the way the real ladder is. A canary that uses a *different*, weaker
     * primitive tests nothing — it would come back clean while the ladder is being murdered.
     */
    fun armCanary(challengeId: String, at: Long) {
        setAlarmClock(at, serviceIntent(challengeId, Slot.CANARY, EscalationService.ACTION_CANARY, at))
    }

    /** Tear down every alarm this challenge owns. Idempotent by nature; safe to call twice. */
    fun cancelChallenge(challengeId: String, now: Long = System.currentTimeMillis()) {
        for (rung in Rung.entries) {
            val at = store.armRef(challengeId, rung)?.scheduledFor ?: continue
            alarms.cancel(rungIntent(challengeId, rung, at))
        }
        cancelDecoys(challengeId, now)
        alarms.cancel(serviceIntent(challengeId, Slot.LOCK_EXPIRY, EscalationService.ACTION_LOCK_EXPIRY))
        store.clearArms(challengeId)
    }

    // -----------------------------------------------------------------------

    /**
     * `setAlarmClock`, with the degradation path spelled out.
     *
     * The fallback is `setWindow` and **not** `setExactAndAllowWhileIdle`: if we have lost exact
     * alarms we have lost the ladder's timing, and pretending otherwise with a primitive that Doze
     * rate-limits to one fire per nine minutes would produce a ladder that fires R0 and silently
     * eats R1–R4. An honestly inexact alarm that the COACH INTEGRITY panel can report beats an alarm
     * that lies about being exact. The denial is recorded for that panel to read.
     */
    private fun setAlarmClock(at: Long, pi: PendingIntent) {
        // The AlarmClockInfo's showIntent is null, and that is a decision, not an omission.
        // getNextAlarmClock() exposes exactly two things to every app on the phone: the trigger time
        // and this intent. A real alarm carrying a showIntent and decoys carrying null would be a
        // free oracle for telling them apart. They must be indistinguishable, so nobody gets one.
        val info = AlarmManager.AlarmClockInfo(at, null)
        val ok = runCatching { alarms.setAlarmClock(info, pi) }.isSuccess
        if (ok) {
            store.putBootBool(BootKeys.EXACT_ALARMS_DENIED, false)
            return
        }
        store.putBootBool(BootKeys.EXACT_ALARMS_DENIED, true)
        runCatching {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, pi)
        }
    }

    private fun clockRef() = ClockRef(
        wall = System.currentTimeMillis(),
        elapsed = SystemClock.elapsedRealtime(),
        bootCount = bootCount(context),
    )

    private fun rungIntent(challengeId: String, rung: Rung, at: Long): PendingIntent {
        val intent = Intent(context, EscalationReceiver::class.java).apply {
            action = EscalationReceiver.ACTION_RUNG
            // The data URI is load-bearing and it is the classic Android trap in this file.
            // PendingIntent identity is `Intent.filterEquals` — action, data, type, component,
            // categories — and it DOES NOT INCLUDE EXTRAS. Two rungs of the same challenge
            // distinguished only by an extra would be the same PendingIntent, so arming R1 would
            // silently overwrite R0's alarm and the ladder would have exactly one rung. The request
            // code alone is not enough either: it disambiguates the PendingIntent slot, not the
            // Intent, and `FLAG_UPDATE_CURRENT` would then rewrite the extras of the wrong one.
            data = Uri.parse("secondspine://rung/$challengeId/${rung.ordinal}")
            putExtra(EscalationReceiver.EXTRA_CHALLENGE_ID, challengeId)
            putExtra(EscalationReceiver.EXTRA_RUNG, rung.ordinal)
            putExtra(EscalationReceiver.EXTRA_SCHEDULED_FOR, at)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(challengeId, rung.ordinal),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun decoyIntent(challengeId: String, code: Int, at: Long): PendingIntent {
        val intent = Intent(context, EscalationReceiver::class.java).apply {
            action = EscalationReceiver.ACTION_DECOY
            data = Uri.parse("secondspine://decoy/$challengeId/$code")
            putExtra(EscalationReceiver.EXTRA_CHALLENGE_ID, challengeId)
            putExtra(EscalationReceiver.EXTRA_REQUEST_CODE, code)
            putExtra(EscalationReceiver.EXTRA_SCHEDULED_FOR, at)
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(
        challengeId: String,
        slot: Int,
        action: String,
        at: Long = 0L,
    ): PendingIntent {
        val intent = Intent(context, EscalationReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("secondspine://svc/$challengeId/$slot")
            putExtra(EscalationReceiver.EXTRA_CHALLENGE_ID, challengeId)
            putExtra(EscalationReceiver.EXTRA_SCHEDULED_FOR, at)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(challengeId, slot), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Request-code slots. Rungs own 0..4 by ordinal; everything else lives above them. */
    private object Slot {
        const val LOCK_EXPIRY = 10
        const val CANARY = 11
        const val DECOY_BASE = 20
    }

    private companion object {
        /**
         * The degraded window. Fifteen minutes, i.e. openly useless for a 7-minute ladder — which is
         * the honest shape of "we no longer have exact alarms" rather than a number pretending to be
         * a fallback.
         */
        const val INEXACT_WINDOW_MS = 15L * 60_000L

        fun requestCode(challengeId: String, slot: Int): Int = challengeId.hashCode() * 64 + slot
    }
}

/**
 * The boot identity. `Settings.Global.BOOT_COUNT`, monotonic since factory reset, free to read.
 *
 * This exists to keep the AUTO-VOID from accusing a man of moving his clock every time he reboots.
 * `elapsedRealtime()` resets to zero at boot, so a reference taken before a reboot and subtracted
 * after one produces an enormous fake divergence between the two clocks — which is precisely the
 * signature of `CLOCK_TAMPER`. RESOLUTIONS is explicit that a clock jump is "auto-void, no penalty,
 * not a catch", but a false one still voids a challenge he completed. So the reference carries the
 * boot it was taken on, and a reference from a previous boot is discarded rather than trusted.
 */
internal fun bootCount(context: Context): Int =
    runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    }.getOrDefault(0)
