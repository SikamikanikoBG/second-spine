package com.secondspine.coach

/**
 * The shared vocabulary of the coach brain. Everything here is pure JVM: no Android, no I/O, no clock.
 * Time enters as an explicit parameter so the 300-day soak test can run in milliseconds.
 *
 * Where this file and SPEC.md disagree, RESOLUTIONS.md decides. See RESOLUTIONS §B.
 */

// ---------------------------------------------------------------------------
// The pipeline
// ---------------------------------------------------------------------------

/** A habit's jurisdiction stage. Habits climb; Rip loses ground. He gets no vote. */
enum class Stage(val minDays: Int, val windowDays: Int) {
    ENFORCED(minDays = 42, windowDays = 42),
    AUDITED(minDays = 56, windowDays = 56),
    TRUSTED(minDays = Int.MAX_VALUE, windowDays = 28),
    RETIRED(minDays = Int.MAX_VALUE, windowDays = 0),
}

/**
 * Why a habit moved. RESOLUTIONS §A2: "no confession ever demotes; only being caught, or collapsing, does."
 * There is deliberately no CONFESSED value — a confession must not be expressible as a demotion cause.
 */
enum class TransitionReason { GRADUATED, DEMOTED_CAUGHT, DEMOTED_COLLAPSE, SUBDIVIDED, USER_RETIRED }

/** Ladder position. Distinct from Tier (habit penalty class) — RESOLUTIONS §B, two scales, no overlap. */
enum class Rung { R0_NOTIFICATION, R1_VIBRATE, R2_ALARM, R3_VOICE, R4_LOCK }

/** Habit penalty class. Distinct from Rung. */
enum class Tier { T0, T1, T2, T3, T4, T5 }

// ---------------------------------------------------------------------------
// The voice
// ---------------------------------------------------------------------------

/**
 * Rip's registers. DISAPPOINTED has NO scheduled share — RESOLUTIONS §A2.
 * It fires 0-3 times in ten months and that is correct.
 */
enum class Register { PITCHMAN, ARENA, BIT, DISAPPOINTED, GHOST }

/**
 * What a line may be aimed at. FROZEN — graft 16. RESOLUTIONS §B adopts §3.7's enum verbatim.
 *
 * `body`, `weight`, `appearance` and `worth` are not enumerable values. That is the point: a flag he
 * wrote he can unwrite at 1am; a value that does not exist he cannot conjure.
 */
enum class Target { the_habit, the_excuse, the_situation, the_phone, himself, the_tape }

/** Slot roles in the five-slot line grammar. Retirement is PER-SLOT — RESOLUTIONS §B. */
enum class SlotRole(val retireAt: Int) {
    OPENER(20),        // nobody notices the fourth "Okay."
    OBSERVATION(15),
    ESCALATION(8),
    SWERVE(3),         // everybody notices the second swerve
    BUTTON(12),
}

/** What caused Rip to speak. DISAPPOINTED's trigger enum is {CAUGHT_FAKE} and nothing else. */
enum class Trigger {
    SCHEDULED, MISSED, PROOF_LOGGED, CAUGHT_FAKE, CONFESSED, COLLAPSED,
    GRADUATED, COMEBACK, OEM_KILL, EVASION, TAPE,
}

// ---------------------------------------------------------------------------
// Integrity
// ---------------------------------------------------------------------------

/**
 * The ONLY way a proof can be called fake. RESOLUTIONS §A2: BYTE_REPLAY only.
 *
 * A SHA-256 collision on the decoded pixel buffer is arithmetic, not an accusation — a real sensor
 * cannot produce one twice. pHash (FRAME_REPLAY) is deliberately absent: it is engineered to be
 * robust, so honest re-shots of the same mug collide, and the app's one insinuation would fire on
 * truthful nights. That is the #1 rage-uninstall, bought for nothing.
 */
enum class CaughtKind { BYTE_REPLAY }

// ---------------------------------------------------------------------------
// Domain rows (mirrors of the Room entities, but Android-free)
// ---------------------------------------------------------------------------

data class Habit(
    val id: String,
    val stage: Stage,
    val tier: Tier,
    val stageSince: Long,          // epoch millis
    val lockEligible: Boolean = false,
)

/** A scheduled demand. */
data class Day(
    val habitId: String,
    val epochDay: Long,
    val completed: Boolean,
    /** True when the day was confessed. RESOLUTIONS §A1: these leave the compliance ratio entirely. */
    val confessed: Boolean = false,
    /** True when an interlock suspended the demand (sick/injured/travel/deload/pain). Also excluded. */
    val suspended: Boolean = false,
)

data class CaughtEvent(val habitId: String, val kind: CaughtKind, val at: Long)

/** Clinical gates from intake. These OUTRANK the register-mix assertion — RESOLUTIONS §B. */
data class ClinicalGates(
    val scoffPositive: Boolean = false,
    val parqPositive: Boolean = false,
) {
    /** SCOFF-positive permanently removes the mocking registers. No in-app override. */
    val mockingAllowed: Boolean get() = !scoffPositive
}
