package com.secondspine.coach

import java.time.Instant
import java.time.ZoneId

/**
 * SAFETY INTERLOCKS — the hard predicate list. SPEC §6.7, corrected by RESOLUTIONS §D.
 *
 * PURE JVM. Zero Android. Time enters as an explicit `now: Long` (epoch millis) so the 300-day soak
 * test runs in milliseconds. `DeviceContext` is a dumb data snapshot: the `:app` module reads the
 * platform (TelephonyCallback, AudioManager, ActivityRecognition, PowerManager...) and hands the
 * answers in. Nothing in here reads a sensor, a clock, or a file.
 *
 * An interlock SUSPENDS or CAPS. It never penalises, never counts, never mocks.
 *
 * SPEC §6.7's own words, and the reason this file exists at all:
 *   "If a row is inconvenient to implement, the lock does not ship. Do not build a thing that can
 *    stand between a man and a phone call."
 */

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Sustained speed above this is driving. SPEC §6.7 row 3. */
const val DRIVING_SPEED_KMH = 15.0

/** ActivityRecognition IN_VEHICLE confidence at or above this is driving. SPEC §6.7 row 3. */
const val DRIVING_CONFIDENCE = 70

/** Driving suspends for this long after the LAST vehicle signal, not the first. SPEC §6.7 row 3. */
const val DRIVING_TAIL_MS = 15L * 60_000L

/** One escalation ladder per 90 minutes. Not one rung — one LADDER. */
const val LADDER_COOLDOWN_MS = 90L * 60_000L

/** Nothing above R1 in the first 72 hours. For any habit. The coach earns the ladder. */
const val INSTALL_GRACE_MS = 72L * 3_600_000L

/**
 * THE LOCK HOLDS BACK 14 DAYS FROM INSTALL AND SAYS SO. RESOLUTIONS §E.
 * Before day 14, R4 is unreachable. The design's own patience is also the scope cut.
 *
 *   PITCHMAN — day 2: "I'm being nice. Two weeks. Enjoy it. I'm building a file."
 */
const val LOCK_HOLD_BACK_MS = 14L * 24 * 3_600_000L

/** R4 fires at most once per 7 days across the whole app. SPEC §6.2. */
const val LOCK_MIN_INTERVAL_MS = 7L * 24 * 3_600_000L

/** SPEC §6.7 row 18. Subsumed by the 7-day rule, kept because the row is testable on its own. */
const val LOCK_COOLDOWN_MS = 90L * 60_000L

/** Ghosting: no opens, no proofs for this long and escalation disables itself. SPEC §6.7 row 17. */
const val GHOSTING_MS = 72L * 3_600_000L

const val BATTERY_LOW_PCT = 15
const val BATTERY_CRITICAL_PCT = 5

/** Two or more habits collapsing at once. The ladder must get QUIETER, never louder. */
const val MULTI_HABIT_COLLAPSE_THRESHOLD = 2

// ---------------------------------------------------------------------------
// The manual modes
// ---------------------------------------------------------------------------

/** SPEC §6.7 row 14: manual, UNCAPPED. Paused. No penalty, no debt, no catch-up. */
enum class PausedMode { SICK, INJURED, TRAVELLING, DELOAD }

// ---------------------------------------------------------------------------
// Foreground packages — RESOLUTIONS §D, "Never lock over the dialer"
// ---------------------------------------------------------------------------

/**
 * RESOLUTIONS §D names this a "mandatory guardrail with no implementation":
 *
 *   "Call detection only catches an *active* call, not 'he is typing 112'. Gate on foreground
 *    package."
 *
 * `inCall` goes true when the call CONNECTS. The forty seconds in which a man is dialling emergency
 * services are exactly the forty seconds in which `inCall` is false. So the dialer being FOREGROUND
 * is its own interlock, checked before and independently of the call state.
 */
val DEFAULT_DIALER_PACKAGES: Set<String> = setOf(
    "com.android.dialer",
    "com.google.android.dialer",
    "com.android.phone",
    "com.android.server.telecom",
    "com.samsung.android.dialer",
    "com.samsung.android.incallui",
    "com.android.incallui",
    "com.oneplus.dialer",
    "com.miui.dialer",
    "com.android.emergency",
    "com.google.android.apps.safetyhub",
)

/**
 * SPEC §6.7 is right that "is navigating" has no API — but a navigation app being FOREGROUND does,
 * and it is the same one-line read as the dialer gate. This is a supplement to the driving row
 * (speed / IN_VEHICLE), never a replacement for it.
 */
val DEFAULT_NAVIGATION_PACKAGES: Set<String> = setOf(
    "com.google.android.apps.maps",
    "com.waze",
    "com.here.app.maps",
    "com.sygic.aura",
    "org.osmand.plus",
    "com.mapswithme.maps.pro",
    "com.tomtom.gplay.navapp",
    "ru.yandex.yandexnavi",
)

// ---------------------------------------------------------------------------
// The context
// ---------------------------------------------------------------------------

/**
 * A pure snapshot of the device, handed in by `:app`. No Android types leak across this boundary —
 * `TelephonyCallback` becomes a Boolean, `PowerManager` becomes an Int.
 */
data class DeviceContext(
    /** Epoch millis of first install. Drives the 72-hour grace and the 14-day lock hold-back. */
    val installAt: Long,

    /**
     * THE USER'S ACTUAL wind-down time, minutes since local midnight. NOT hardcoded 22:00.
     *
     * RESOLUTIONS §D: with a hardcoded 22:00-08:00 window and a target bed of 21:30, wind-down
     * starts 20:45 and there are "75 minutes in which the ladder can fire an alarm, a TTS line and a
     * lock inside the wind-down window" — on the pillar ranked #1. So it is keyed here, on his times.
     */
    val winddownAtMinutes: Int,

    /** THE USER'S ACTUAL wake time, minutes since local midnight. NOT hardcoded 08:00. */
    val wakeAtMinutes: Int,

    /** For mapping `now` to a local time-of-day. A zone is data, not a clock. */
    val zone: ZoneId = ZoneId.of("UTC"),

    /** Row 1+2: cellular CallStateListener != IDLE, OR AudioManager.mode in {IN_CALL, IN_COMMUNICATION}. */
    val inCall: Boolean = false,

    /** The one thing that catches "he is typing 112". RESOLUTIONS §D. */
    val foregroundPackage: String? = null,

    val speedKmh: Double = 0.0,
    val inVehicleConfidence: Int = 0,
    /** Epoch millis of the last vehicle signal. Driving suspends for 15 min AFTER this. */
    val lastVehicleSignalAt: Long? = null,

    val batteryPct: Int = 100,
    val powerSaveMode: Boolean = false,

    /** Row 11: respect absolutely. Never route around it via the alarm channel. */
    val ringerSilent: Boolean = false,

    /** Row 9: thermal >= SEVERE. */
    val thermalSevere: Boolean = false,

    /** Row 5: another app holds the camera. CAP, never a full suspend — that is a free mute switch. */
    val cameraHeldByOtherApp: Boolean = false,

    /** Row 13: manual tile, 90 min max. Epoch millis it ends. */
    val meetingModeUntil: Long? = null,

    /** Row 14: SICK / INJURED / TRAVELLING / DELOAD. Manual, uncapped, unpriced. */
    val pausedMode: PausedMode? = null,

    /** Row 15: §7's drop detector. Ceiling R0, character off, Ledger frozen. */
    val stoodDown: Boolean = false,

    /** Row 17: last app open or proof. Null means "never since install". */
    val lastAppOpenAt: Long? = null,

    /** Epoch millis the PREVIOUS ladder started. Never this one's — see `step`. */
    val lastEscalationAt: Long? = null,

    /** Epoch millis R4 last fired, app-wide. */
    val lastLockAt: Long? = null,

    /**
     * How many habits are currently collapsing. SPEC §6.7, "the sign inversion":
     * the ladder may NEVER escalate in response to multi-habit collapse. A man drowning does not
     * need a louder coach.
     */
    val collapsingHabitCount: Int = 0,

    val dialerPackages: Set<String> = DEFAULT_DIALER_PACKAGES,
    val navigationPackages: Set<String> = DEFAULT_NAVIGATION_PACKAGES,
)

// ---------------------------------------------------------------------------
// The interlocks
// ---------------------------------------------------------------------------

/**
 * Every interlock and the highest rung it still permits.
 *
 * `ceiling == null` means TOTAL SILENCE: the ladder is frozen where it stands and nothing fires.
 * Anything else is a cap: rungs at or below it are still allowed.
 */
enum class Interlock(val ceiling: Rung?) {
    // --- total silence -----------------------------------------------------
    /** Rows 1 & 2. */
    CALL_ACTIVE(null),
    /** RESOLUTIONS §D. He is typing 112. Nothing happens. Nothing. */
    DIALER_FOREGROUND(null),
    /** Row 3. */
    DRIVING(null),
    /** Row 3, supplement. */
    NAVIGATION_FOREGROUND(null),
    /** Row 9. */
    THERMAL_SEVERE(null),
    /** Row 13. Logged, UNMOCKED. */
    MEETING_MODE(null),
    /** Row 14. */
    MODE_PAUSED(null),
    /** Row 7. */
    BATTERY_CRITICAL(null),
    /** One ladder per 90 minutes. */
    COOLDOWN_90M(null),

    // --- ceiling R0 --------------------------------------------------------
    /**
     * Row 8, keyed on the user's own (winddownAt, wakeAt) — RESOLUTIONS §D.
     * "An app that fires an alarm at a man failing to sleep is an insomnia generator."
     */
    WIND_DOWN(Rung.R0_NOTIFICATION),
    /** Row 15. */
    STOOD_DOWN(Rung.R0_NOTIFICATION),
    /** Row 17. "Do not become the thing he factory-resets to escape." */
    GHOSTING(Rung.R0_NOTIFICATION),
    /** The sign inversion. `assertLadderNeverEscalatesOn(multiHabitCollapse())`. */
    MULTI_HABIT_COLLAPSE(Rung.R0_NOTIFICATION),
    /**
     * PENALTIES MAY BE ANTICIPATORY OR INTERRUPTIVE. NEVER RETRIBUTIVE.
     * Set by the challenge, not the device — `step` folds it in. The coffee is drunk; the only
     * permitted response is data.
     */
    NOT_PERFORMABLE(Rung.R0_NOTIFICATION),

    // --- ceiling R1 --------------------------------------------------------
    /** Row 20. */
    INSTALL_GRACE_72H(Rung.R1_VIBRATE),
    /** Row 6. Cap, never lock — he may need maps or a call. */
    BATTERY_LOW(Rung.R1_VIBRATE),
    /** Row 5. */
    CAMERA_BUSY(Rung.R1_VIBRATE),
    /** Row 11. No alarm channel end-run. */
    RINGER_SILENT(Rung.R1_VIBRATE),

    // --- ceiling R3 (R4 forbidden) -----------------------------------------
    /** Row 19 / RESOLUTIONS §E. The lock holds back 14 days and says so. */
    INSTALL_AGE_UNDER_14D(Rung.R3_VOICE),
    /** Row 18. */
    LOCK_COOLDOWN_90M(Rung.R3_VOICE),
    /** SPEC §6.2, <=1 lock per 7 days app-wide. */
    LOCK_COOLDOWN_7D(Rung.R3_VOICE),
    /** Water. RESOLUTIONS §B: exercise is the only lock-eligible habit. */
    HABIT_NOT_LOCK_ELIGIBLE(Rung.R3_VOICE),
}

// ---------------------------------------------------------------------------
// The predicates
// ---------------------------------------------------------------------------

/** Minutes since local midnight for `now` in the user's zone. Deterministic; no system clock. */
fun localMinutes(ctx: DeviceContext, now: Long): Int =
    Instant.ofEpochMilli(now).atZone(ctx.zone).toLocalTime().let { it.hour * 60 + it.minute }

/** Half-open [start, end), wrapping midnight. start == end is an empty window, not a whole day. */
internal fun inMinuteWindow(nowMin: Int, start: Int, end: Int): Boolean = when {
    start == end -> false
    start < end -> nowMin >= start && nowMin < end
    else -> nowMin >= start || nowMin < end
}

/**
 * THE WIND-DOWN..WAKE WINDOW, keyed on the user's actual times.
 *
 * This is the whole of RESOLUTIONS §D's third bullet. There is no 22 and no 8 anywhere in it.
 */
fun inWindDownWindow(ctx: DeviceContext, now: Long): Boolean =
    inMinuteWindow(localMinutes(ctx, now), ctx.winddownAtMinutes, ctx.wakeAtMinutes)

/** RESOLUTIONS §D. True while the dialer or an emergency surface is foreground. */
fun isDialerForeground(ctx: DeviceContext): Boolean =
    ctx.foregroundPackage != null && ctx.foregroundPackage in ctx.dialerPackages

fun isNavigationForeground(ctx: DeviceContext): Boolean =
    ctx.foregroundPackage != null && ctx.foregroundPackage in ctx.navigationPackages

/** Speed OR IN_VEHICLE confidence OR a vehicle signal in the last 15 minutes. */
fun isDriving(ctx: DeviceContext, now: Long): Boolean {
    if (ctx.speedKmh > DRIVING_SPEED_KMH) return true
    if (ctx.inVehicleConfidence >= DRIVING_CONFIDENCE) return true
    val last = ctx.lastVehicleSignalAt ?: return false
    return now - last in 0 until DRIVING_TAIL_MS
}

/** Every interlock currently firing. Device-level only; `step` adds NOT_PERFORMABLE. */
fun interlocksActive(ctx: DeviceContext, now: Long): Set<Interlock> {
    val out = LinkedHashSet<Interlock>()

    // The two that must be checked first, and the two that are never negotiable.
    if (isDialerForeground(ctx)) out += Interlock.DIALER_FOREGROUND
    if (ctx.inCall) out += Interlock.CALL_ACTIVE

    if (isDriving(ctx, now)) out += Interlock.DRIVING
    if (isNavigationForeground(ctx)) out += Interlock.NAVIGATION_FOREGROUND
    if (ctx.thermalSevere) out += Interlock.THERMAL_SEVERE
    if (ctx.pausedMode != null) out += Interlock.MODE_PAUSED
    ctx.meetingModeUntil?.let { if (now < it) out += Interlock.MEETING_MODE }

    if (ctx.batteryPct < BATTERY_CRITICAL_PCT) out += Interlock.BATTERY_CRITICAL
    else if (ctx.batteryPct < BATTERY_LOW_PCT || ctx.powerSaveMode) out += Interlock.BATTERY_LOW

    ctx.lastEscalationAt?.let { if (now - it in 0 until LADDER_COOLDOWN_MS) out += Interlock.COOLDOWN_90M }

    if (inWindDownWindow(ctx, now)) out += Interlock.WIND_DOWN
    if (ctx.stoodDown) out += Interlock.STOOD_DOWN
    if (ctx.collapsingHabitCount >= MULTI_HABIT_COLLAPSE_THRESHOLD) out += Interlock.MULTI_HABIT_COLLAPSE

    val lastSeen = ctx.lastAppOpenAt ?: ctx.installAt
    if (now - lastSeen > GHOSTING_MS) out += Interlock.GHOSTING

    val age = now - ctx.installAt
    if (age < INSTALL_GRACE_MS) out += Interlock.INSTALL_GRACE_72H
    if (age < LOCK_HOLD_BACK_MS) out += Interlock.INSTALL_AGE_UNDER_14D

    if (ctx.cameraHeldByOtherApp) out += Interlock.CAMERA_BUSY
    if (ctx.ringerSilent) out += Interlock.RINGER_SILENT

    ctx.lastLockAt?.let {
        if (now - it in 0 until LOCK_COOLDOWN_MS) out += Interlock.LOCK_COOLDOWN_90M
        if (now - it in 0 until LOCK_MIN_INTERVAL_MS) out += Interlock.LOCK_COOLDOWN_7D
    }

    return out
}

/** The lower (quieter) of two ceilings. `null` — total silence — always wins. */
internal fun minRung(a: Rung?, b: Rung?): Rung? =
    if (a == null || b == null) null else if (a.ordinal <= b.ordinal) a else b

/**
 * The highest rung the device currently permits, or `null` for total silence.
 * With no interlocks active, the ceiling is R4 — but R4 has challenge-level gates too (`step`).
 */
fun escalationCeiling(ctx: DeviceContext, now: Long): Rung? {
    val active = interlocksActive(ctx, now)
    if (active.isEmpty()) return Rung.R4_LOCK
    var ceiling: Rung? = Rung.R4_LOCK
    for (i in active) ceiling = minRung(ceiling, i.ceiling)
    return ceiling
}

/**
 * THE PREDICATE. Pure, total, unit-tested row by row.
 *
 * False means: this rung does not fire, right now, for this device. The caller SUSPENDS — it does
 * not penalise, does not count it, and above all does not mention it.
 */
fun mayEscalate(rung: Rung, ctx: DeviceContext, now: Long): Boolean {
    val ceiling = escalationCeiling(ctx, now) ?: return false
    return rung.ordinal <= ceiling.ordinal
}

/** Why not — for tests and for the Sunday total. Never for a line of dialogue. */
fun blockingInterlocks(rung: Rung, ctx: DeviceContext, now: Long): Set<Interlock> =
    interlocksActive(ctx, now).filterTo(LinkedHashSet()) { i ->
        i.ceiling == null || rung.ordinal > i.ceiling.ordinal
    }
