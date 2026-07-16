package com.secondspine.app.enforce

import android.app.AppOpsManager
import android.app.UiModeManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import com.secondspine.coach.DeviceContext
import com.secondspine.coach.PausedMode
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * THE PLATFORM, FLATTENED INTO A DATA SNAPSHOT.
 *
 * `Interlocks.kt` says it: *"`DeviceContext` is a dumb data snapshot: the `:app` module reads the
 * platform (TelephonyCallback, AudioManager, ActivityRecognition, PowerManager...) and hands the
 * answers in. Nothing in here reads a sensor, a clock, or a file."* This class is the other side of
 * that sentence, and it is the only place in the app where an Android type becomes a Boolean.
 *
 * Read [drivingSignal] before anything else in this file. It is the one that decides whether the lock
 * exists.
 */
internal class DeviceContextReader(private val context: Context) {

    private val store get() = ScheduleStore.get(context)

    /**
     * Build the snapshot the brain reasons over.
     *
     * @param bootSafe true on the Direct Boot path. Everything credential-protected is unreadable
     *   there, so the snapshot is built from `schedule.db`'s mirror. Nothing on that path fires a
     *   rung — [BootReceiver] only re-arms — so a coarse snapshot is safe by construction.
     */
    fun read(bootSafe: Boolean = false): DeviceContext {
        val driving = drivingSignal?.invoke()
        return DeviceContext(
            installAt = store.getBootLong(BootKeys.INSTALL_AT) ?: System.currentTimeMillis(),

            // RESOLUTIONS §D. HIS times, never 22:00–08:00. The mirror is written on every settings
            // change precisely so that this line never has to fall back to a constant — and the
            // fallbacks below are SettingsStore's own defaults, not a policy invented here.
            winddownAtMinutes = store.getBootInt(BootKeys.WINDDOWN_MIN) ?: (22 * 60 + 30),
            wakeAtMinutes = store.getBootInt(BootKeys.WAKE_MIN) ?: (7 * 60),
            zone = store.bootZone(),

            inCall = if (bootSafe) false else inCall(),
            foregroundPackage = if (bootSafe) null else foregroundPackage(),

            speedKmh = driving?.speedKmh ?: 0.0,
            inVehicleConfidence = driving?.inVehicleConfidence ?: 0,
            lastVehicleSignalAt = driving?.lastVehicleSignalAt,

            batteryPct = if (bootSafe) 100 else batteryPct(),
            powerSaveMode = if (bootSafe) false else powerSave(),
            ringerSilent = if (bootSafe) true else ringerSilent(),
            thermalSevere = if (bootSafe) false else thermalSevere(),
            cameraHeldByOtherApp = if (bootSafe) false else CameraAvailability.heldByOtherApp(context),

            meetingModeUntil = store.getBootLong(BootKeys.MEETING_UNTIL),
            pausedMode = store.getBoot(BootKeys.PAUSED_MODE)
                ?.let { name -> runCatching { PausedMode.valueOf(name) }.getOrNull() },
            stoodDown = store.getBootBool(BootKeys.STOOD_DOWN),

            lastAppOpenAt = store.getBootLong(BootKeys.LAST_APP_OPEN_AT),
            lastEscalationAt = store.getBootLong(BootKeys.LAST_ESCALATION_AT),
            lastLockAt = store.getBootLong(BootKeys.LAST_LOCK_AT),
            collapsingHabitCount = store.getBootInt(BootKeys.COLLAPSING_COUNT) ?: 0,
        )
    }

    // -----------------------------------------------------------------------
    // Rows 1 & 2 — the call
    // -----------------------------------------------------------------------

    /**
     * SPEC §6.7 rows 1 and 2, and only row 2 is implementable with the permissions this app holds.
     *
     * Row 1 wants `TelephonyCallback.CallStateListener != IDLE`, which needs `READ_PHONE_STATE`.
     * That permission is **not** in the manifest and asking for it would be a runtime dialog whose
     * honest description is "so the app can refuse to act" — a hard sell that buys, in practice,
     * nothing: row 2's `AudioManager.mode` is free, needs no permission, and the spec's own table
     * says it *"catches what telephony misses"*. `MODE_IN_CALL` is set for cellular calls and
     * `MODE_IN_COMMUNICATION` for VoIP, so this single read covers both rows.
     *
     * The gap it leaves is a ringing-but-unanswered cellular call, where the mode has not moved yet.
     * That gap is closed by [foregroundPackage] and `DIALER_FOREGROUND` — an incoming call puts the
     * dialer in front — which is the same read RESOLUTIONS §D demands for "he is typing 112".
     */
    private fun inCall(): Boolean = runCatching {
        val am = context.getSystemService(AudioManager::class.java)
        am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
    }.getOrDefault(false)

    // -----------------------------------------------------------------------
    // RESOLUTIONS §D — "he is typing 112"
    // -----------------------------------------------------------------------

    /**
     * The foreground package, via `UsageStatsManager`. THE 112 GATE.
     *
     * RESOLUTIONS §D lists "never lock over the dialer" as *"a mandatory guardrail with no
     * implementation"*, and the reason is exact: `inCall` goes true when the call **connects**. The
     * forty seconds in which a man is dialling emergency services are the forty seconds in which
     * `inCall` is false. So the dialer being in front is its own interlock, and it is this read.
     *
     * Returns null when `PACKAGE_USAGE_STATS` has not been granted — it is a Settings trip, not a
     * runtime dialog. **Null is not "no dialer".** [lockSafe] treats an unreadable foreground package
     * as a reason to refuse the lock, because a guardrail that silently degrades to "off" is not a
     * guardrail.
     */
    fun foregroundPackage(): String? = runCatching {
        if (!hasUsageStatsPermission()) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                latest = event.packageName
            }
        }
        latest
    }.getOrNull()

    fun hasUsageStatsPermission(): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    // -----------------------------------------------------------------------
    // The cheap reads
    // -----------------------------------------------------------------------

    private fun batteryPct(): Int = runCatching {
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0..100) return level
        // Some OEMs return Integer.MIN_VALUE from the property. The sticky broadcast always answers.
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val raw = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (raw >= 0 && scale > 0) raw * 100 / scale else 100
    }.getOrDefault(100)

    private fun powerSave(): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java).isPowerSaveMode
    }.getOrDefault(false)

    /**
     * SPEC §6.7 row 11: *"Respect absolutely. Never route around it via the alarm channel."*
     *
     * VIBRATE counts as silent, and the semantics that produces are exactly right rather than merely
     * convenient: `RINGER_SILENT` caps the ladder at R1, R1 *is* the vibrate rung, so a phone in
     * vibrate mode gets buzzed and never gets an alarm tone or a spoken line. The interlock and the
     * user's switch agree without anything having to translate between them.
     *
     * `ACCESS_NOTIFICATION_POLICY` is cut (SPEC §6.5), so this is `ringerMode` and nothing else —
     * free, no permission, no Settings trip.
     */
    private fun ringerSilent(): Boolean = runCatching {
        context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL
    }.getOrDefault(true)

    private fun thermalSevere(): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java).currentThermalStatus >=
            PowerManager.THERMAL_STATUS_SEVERE
    }.getOrDefault(false)

    private companion object {
        /**
         * How far back to look for a foreground transition.
         *
         * `queryEvents` is a *log*, not a state read: if nothing has come to the foreground in the
         * window there are no events and the answer is null. Ten seconds is long enough to have
         * caught the transition that put the dialer up and short enough that a package he closed a
         * minute ago is not still being reported as foreground.
         */
        const val FOREGROUND_LOOKBACK_MS = 10_000L
    }
}

// ---------------------------------------------------------------------------
// THE DRIVING ROW — and the honest answer about it
// ---------------------------------------------------------------------------

/**
 * What a driving detector must hand back. Speed in km/h, `IN_VEHICLE` confidence 0..100, and the
 * instant of the last vehicle signal — the tail matters, because SPEC §6.7 row 3 suspends for
 * **15 minutes after the last vehicle signal**, not after the first.
 */
internal data class DrivingSnapshot(
    val speedKmh: Double = 0.0,
    val inVehicleConfidence: Int = 0,
    val lastVehicleSignalAt: Long? = null,
)

/**
 * THE DRIVING SIGNAL SEAM — null in v1, and the lock is off because of it.
 *
 * This is the most consequential twelve lines in the enforcement layer, so the reasoning is here in
 * full rather than in a commit message.
 *
 * SPEC §6.7 row 3 requires `ActivityRecognition IN_VEHICLE >= 70, OR speed > 15 km/h sustained`.
 * Both need `play-services-location`, which is **not a dependency of `:app`** — and RESOLUTIONS §C
 * lists the speed clause among the "platform lies to delete": *"The driving interlock's speed clause
 * needs background location, which the spec cuts on the facing page."* So the row is, today, not
 * implementable. `app/build.gradle.kts` is not this agent's file and adding a Play Services
 * dependency is not a decision to smuggle in through an enforcement patch.
 *
 * That leaves two options and only one of them is defensible:
 *
 *  - Ship the lock with the driving row silently off. A man doing 110 on a motorway, phone in the
 *    cupholder, gets his screen turned on and a demand for a photograph of a kettlebell.
 *  - Ship the lock refusing to fire until something wires this seam.
 *
 * SPEC §6.7 already decided, in the imperative, and it is quoted here because it is the rule this
 * file obeys: *"If a row is inconvenient to implement, **the lock does not ship**. Do not build a
 * thing that can stand between a man and a phone call."*
 *
 * So: **while [drivingSignal] is null, R4 is unreachable** — see [lockSafe] and `LockActivity`. This
 * costs nothing that v1 was going to have anyway (RESOLUTIONS §E: *"R4 (the lock) ships as code but
 * stays dormant"*, and the 14-day hold-back makes it unreachable for a fortnight regardless). It is
 * a seam and not a permanent amputation: wire a real detector here, and the lock becomes reachable
 * with no other edit anywhere.
 *
 * Rungs R0–R3 are unaffected. They are not capable of standing between a man and a phone call.
 */
@Volatile
internal var drivingSignal: (() -> DrivingSnapshot)? = null

/**
 * IS IT SAFE TO SHOW THE LOCK AT ALL, ignoring everything about *this* challenge?
 *
 * Every one of these is a row whose failure mode is a phone that will not let a man do something
 * urgent, and every one of them fails *open* if it is merely absent — which is why absence is checked
 * here rather than left to produce a comfortable default deeper in the stack.
 *
 * This is not a substitute for `mayEscalate()`. It is the question that comes before it: the brain
 * decides whether the ladder permits R4 given a context, and this decides whether the context can be
 * trusted enough to ask.
 */
internal fun lockSafe(context: Context): LockSafety {
    val reader = DeviceContextReader(context)
    val reasons = buildList {
        // The row that is not implemented. See `drivingSignal`.
        if (drivingSignal == null) add(LockUnsafe.NO_DRIVING_DETECTION)
        // "He is typing 112" is unanswerable without this, and an unanswerable safety question is a
        // no. RESOLUTIONS §D made this row mandatory; a null foreground package cannot satisfy it.
        if (!reader.hasUsageStatsPermission()) add(LockUnsafe.NO_FOREGROUND_READ)
    }
    return LockSafety(reasons)
}

internal enum class LockUnsafe {
    /** SPEC §6.7 row 3 has no implementation. See [drivingSignal]. */
    NO_DRIVING_DETECTION,

    /** RESOLUTIONS §D's dialer gate cannot be evaluated without `PACKAGE_USAGE_STATS`. */
    NO_FOREGROUND_READ,
}

internal data class LockSafety(val blockers: List<LockUnsafe>) {
    val permitted: Boolean get() = blockers.isEmpty()
}

/**
 * MAY R4 EXIST, RIGHT NOW, ON THIS DEVICE? The single gate, in one place, called from both sides.
 *
 * `EffectInterpreter` calls it before launching the lock and `LockActivity` calls it again in
 * `onCreate`. That is defence in depth done the only way it is worth doing: **the same predicate
 * evaluated twice**, never two predicates that agree today. Two hand-written copies of a safety rule
 * are a rule with a version number, and the version that gets forgotten is the one that stands
 * between a man and a phone call.
 *
 * Three clauses, in increasing order of how often they change:
 *
 *  1. **Structural.** Is the driving row implemented on this build; can the dialer gate be read at
 *     all. Constant for a whole install. See [drivingSignal].
 *  2. **The 14-day hold-back.** RESOLUTIONS §E — *"I'm being nice. Two weeks. Enjoy it. I'm building
 *     a file."* Enforced here as well as in `Interlocks.kt`, because the brief asks for it in both
 *     places and this is the one thing that can hurt someone.
 *  3. **Every interlock**, via the brain's own `mayEscalate`. Never reimplemented here.
 */
internal fun lockPermittedNow(context: Context, now: Long = System.currentTimeMillis()): Boolean {
    if (!lockSafe(context).permitted) return false
    val ctx = DeviceContextReader(context).read()
    if (now - ctx.installAt < com.secondspine.coach.LOCK_HOLD_BACK_MS) return false
    return com.secondspine.coach.mayEscalate(com.secondspine.coach.Rung.R4_LOCK, ctx, now)
}

// ---------------------------------------------------------------------------
// Row 5 — the camera
// ---------------------------------------------------------------------------

/**
 * SPEC §6.7 row 5: another app holds the camera → **cap at R1, log it**.
 *
 * The spec is emphatic that this is a cap and never a suspend, and the reason is worth keeping in
 * front of whoever edits this next: *"A full suspend on camera-unavailability alone is a free,
 * unlogged mute switch."* Open any camera app, and the coach goes away. So the ladder gets quieter
 * and keeps going.
 *
 * `AvailabilityCallback` delivers current state on registration, so this warms up within a tick of
 * being registered. It is registered from [EscalationService.onCreate] rather than lazily at read
 * time, because a read taken microseconds after registration would report "available" for every
 * camera and the cap would never apply. The failure mode of a cold read is a *missing cap*, not a
 * false one — the ladder is louder than it should be for one rung, which is a bug and not a hazard.
 */
internal object CameraAvailability {

    private val registered = AtomicBoolean(false)
    private val unavailable = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) { unavailable.remove(cameraId) }
        override fun onCameraUnavailable(cameraId: String) { unavailable.add(cameraId) }
    }

    fun register(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        runCatching {
            context.getSystemService(CameraManager::class.java)
                .registerAvailabilityCallback(callback, null)
        }
    }

    /**
     * True when something else has a camera open.
     *
     * Our own CameraX preview inside `LockActivity` will also mark the camera unavailable — which is
     * harmless here, because by the time our preview is up the rung has already fired and the cap
     * has already been applied.
     */
    fun heldByOtherApp(context: Context): Boolean {
        register(context)
        return unavailable.isNotEmpty()
    }
}

// ---------------------------------------------------------------------------
// The free half of row 3
// ---------------------------------------------------------------------------

/**
 * `UI_MODE_TYPE_CAR` — free, no permission, and the one vehicle signal this app can read today.
 *
 * It is deliberately **not** wired into [drivingSignal] as a stand-in. It answers "is this phone
 * projecting to a car head unit", which is a strict and very small subset of "is this man driving" —
 * it is false for every phone in a cupholder, which is most of them. Wiring it in would flip the lock
 * on while leaving the actual hazard uncovered, and would do it under a name that reads, to the next
 * person, as though row 3 were implemented.
 *
 * It is exposed because it is a genuine *additional* suspend signal once a real detector exists, and
 * because the navigation-foreground interlock in `Interlocks.kt` is its natural partner.
 */
internal fun isCarUiMode(context: Context): Boolean = runCatching {
    context.getSystemService(UiModeManager::class.java).currentModeType ==
        Configuration.UI_MODE_TYPE_CAR
}.getOrDefault(false)
