package com.secondspine.coach

/**
 * THE ESCALATION STATE MACHINE. SPEC §6.1-§6.9, corrected by RESOLUTIONS §B/§D/§E.
 *
 * PURE JVM. `step` is total, deterministic and side-effect-free. It touches no AlarmManager, no
 * Room, no clock — time enters as `now: Long`. `Effect` is DATA; the `:app` module is the only thing
 * that interprets it. This file is the only part of the ladder that GitHub Actions can prove
 * correct, which is why every dangerous decision lives in it.
 *
 * SPEC §6.1: "This is a codebase where 'it compiled' and 'it works' are unrelated statements."
 */

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** SPEC §6.3 / assignment: |wall - elapsed| > 90 s is a clock tamper. AUTO-VOID. Not a catch. */
const val CLOCK_TAMPER_TOLERANCE_MS = 90_000L

/** SPEC §6.3: |fired_at - scheduled_for| > 90 s is the platform killing us. AUTO-VOID. */
const val ALARM_LATE_TOLERANCE_MS = 90_000L

/** SPEC §6.7: an interlock releases, then 90 s of settle before the rung resumes. */
const val SETTLE_MS = 90_000L

/** SPEC §6.4: unconditional 90-second self-expiry regardless of proof. */
const val LOCK_EXPIRY_MS = 90_000L

/** SPEC §6.6: boot more than 6 h after fire_at expires normally. Don't ambush him with yesterday's water. */
const val REBOOT_GRACE_MS = 6L * 3_600_000L

/**
 * SPEC §6.9: 20 reps/day. Not 40 — 40 is a compulsion budget, 20 is a nudge.
 *
 * DECLARED HERE, and the seam with Health.kt is: §6.9 (this file) owns the ceiling and the debt
 * ledger's accrual — how much debt may exist, and how it expires. §7.9 (Health.kt `penaltyDebtReps`)
 * owns the prior question of whether any debt may exist at all: training mode, pain stops, the sleep
 * window, and which HealthActions are penalty-eligible. Health.kt clamps against this constant, which
 * preserves the direction of the arrow that matters — the autoregulator can override the penalty
 * engine, and the penalty engine can never override the autoregulator.
 */
const val PENALTY_DEBT_CEILING_REPS = 20

// ---------------------------------------------------------------------------
// The ladder
// ---------------------------------------------------------------------------

/**
 * Offset from `fireAt` at which each rung fires. SPEC §6.2: R0 +0, R1 +7m, R2 +18m, R3 +27m, R4 +40m.
 *
 * These are INITIAL VALUES ONLY. A per-habit bandit learns them, targeting P(comply) ~ 0.5 — maximum
 * marginal gain, never minimum P(comply). Scheduling where he is guaranteed to fail is harvesting
 * failures for Sunday material, and unfairness uninstalls faster than boredom. The bandit is not in
 * v1 and is not in this file; when it lands it replaces this table and nothing else.
 */
val Rung.offsetMs: Long
    get() = when (this) {
        Rung.R0_NOTIFICATION -> 0L
        Rung.R1_VIBRATE -> 7L * 60_000L
        Rung.R2_ALARM -> 18L * 60_000L
        Rung.R3_VOICE -> 27L * 60_000L
        Rung.R4_LOCK -> 40L * 60_000L
    }

/** The next rung up, or null at the top of the ladder. */
fun Rung.next(): Rung? = Rung.entries.getOrNull(ordinal + 1)

/** The rung he SHOULD be at, `elapsed` ms after fireAt, clamped to this challenge's terminal rung. */
fun rungAt(elapsedMs: Long, terminal: Rung): Rung =
    Rung.entries.last { it.offsetMs <= elapsedMs && it.ordinal <= terminal.ordinal }

// ---------------------------------------------------------------------------
// Effects — DATA. The Android layer interprets. Idempotent on (challengeId, rung).
// ---------------------------------------------------------------------------

enum class VoidReason {
    /** |wall - elapsed| > 90 s. The clock moved. RESOLUTIONS: auto-void, no penalty, not a catch. */
    CLOCK_TAMPER,
    /** |fired - scheduled| > 90 s. His phone murdered us in our sleep. */
    PLATFORM_LATE,
    /** The +47 min canary came back > 60 s late. Void every penalty in the window. */
    OEM_CANARY_FAIL,
    /** The process died across the window. */
    PROCESS_DEATH,
}

/** SPEC §6.8: pressing HOME is a move in the game. Break glass is leaving the building. */
enum class EvasionKind { HOME, REBOOT, FORCE_STOP, REVOKE }

enum class LogKind {
    EVASION, INTERLOCK_SUSPEND, INTERLOCK_RESUME, STOOD_DOWN, VOIDED,
}

/**
 * Everything the Android layer may be asked to do. Nothing here can name `break_glass`.
 *
 * IDEMPOTENCY: every effect carries `(challengeId, rung)`. Re-delivering an `AlarmFired` for a rung
 * already entered produces NO effects at all (SPEC §6.3 — "alarms *do* double-fire on some OEMs",
 * and a double-penalty is an uninstall).
 */
sealed interface Effect {
    val challengeId: String
    val rung: Rung

    /**
     * True when this effect DEMANDS something of the user. False for bookkeeping.
     *
     * SPEC §6.7: "an Effect above R0 requires challenge.action_still_performable == true". This flag
     * is what makes that assertion expressible — a `Cancel` at R2 is not a penalty at R2.
     */
    val demanding: Boolean get() = false

    data class ShowNotification(override val challengeId: String, override val rung: Rung) : Effect {
        override val demanding get() = true
    }

    data class Vibrate(override val challengeId: String, override val rung: Rung) : Effect {
        override val demanding get() = true
    }

    data class PlayAlarm(override val challengeId: String, override val rung: Rung) : Effect {
        override val demanding get() = true
    }

    /** R3. TTS, or a pre-rendered clip. */
    data class Speak(override val challengeId: String, override val rung: Rung) : Effect {
        override val demanding get() = true
    }

    /** R4. `expiresAt` is unconditional and lives in the FGS, not a coroutine. SPEC §6.4. */
    data class ShowLock(
        override val challengeId: String,
        override val rung: Rung,
        val expiresAt: Long,
    ) : Effect {
        override val demanding get() = true
    }

    /** Tear everything down. Idempotent by nature; safe to deliver twice. */
    data class Cancel(override val challengeId: String, override val rung: Rung) : Effect

    /** The app admitting its own bug out loud. An app that does this is a month-8 app. */
    data class Void(
        override val challengeId: String,
        override val rung: Rung,
        val reason: VoidReason,
    ) : Effect

    data class Log(
        override val challengeId: String,
        override val rung: Rung,
        val what: LogKind,
        val interlock: Interlock? = null,
        val evasion: EvasionKind? = null,
    ) : Effect

    /** Write-ahead: `:app` persists `next_at` in a transaction, THEN calls setAlarmClock. SPEC §6.3. */
    data class ScheduleNext(
        override val challengeId: String,
        override val rung: Rung,
        val at: Long,
    ) : Effect
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

enum class Phase(val terminal: Boolean) {
    /** Armed by the planner, R0 not yet fired. */
    ARMED(false),
    CLIMBING(false),
    /** An interlock is up. The rung is FROZEN. Never penalised, never counted, never mocked. */
    SUSPENDED(false),
    /** Proof or confession. Zero-assertion: nothing is rejected here. */
    SATISFIED(true),
    /** One tap. Gone. Go. */
    BROKEN_GLASS(true),
    EXPIRED(true),
    /** Our fault, and we say so. */
    VOID_PLATFORM(true),
    /** §7's drop detector. Character off. */
    STOOD_DOWN(true),
}

/**
 * The whole of a live challenge. Serialisable to `schedule.db` (device-protected — it must be
 * readable in Direct Boot, before first unlock, which is why it is a separate database).
 */
data class EscalationState(
    val challengeId: String,
    val habitId: String,
    /** The rung he is at (or frozen at). While ARMED this is R0 and `enteredRungs` is empty. */
    val rung: Rung,
    val phase: Phase,
    /** == the challenge's `fireAt`. Every rung offset is measured from here, so reboot can recompute. */
    val armedAt: Long,
    /** The window close. Past this, EXPIRED. */
    val expiresAt: Long,

    /**
     * The top of THIS habit's ladder. RESOLUTIONS §B:
     *   "Exercise is the only lock-eligible habit. Water terminates at R2. Locking a senior
     *    engineer's phone over a glass of water is the fastest uninstall available."
     */
    val terminalRung: Rung,
    /** Exercise only. */
    val lockEligible: Boolean = false,
    /** SPEC §6.2: rungs 1-4 are individually opt-in. The bandit may never promote into a louder
     *  public rung without a fresh consent tap. Third parties did not sign the contract. */
    val lockOptIn: Boolean = false,

    /** PENALTIES ARE NEVER RETRIBUTIVE. False once the behaviour is complete and unchangeable. */
    val actionStillPerformable: Boolean = true,

    /** THE IDEMPOTENCY LEDGER. Mirrors the Room unique index on (challenge_id, rung). */
    val enteredRungs: Set<Rung> = emptySet(),

    val frozenRung: Rung? = null,
    val suspendedSince: Long? = null,
    val lastRungAt: Long = armedAt,
    /** Counted and roasted. Structurally distinct from break glass, which is counted by nothing. */
    val evasionCount: Int = 0,
) {
    init {
        // Water can never reach R4. Not by a bug, not by a `copy()`, not at 1am. It is not
        // constructible. RESOLUTIONS §B.
        require(terminalRung != Rung.R4_LOCK || (lockEligible && lockOptIn)) {
            "R4 requires a lock-eligible, lock-opted-in habit. Exercise is the only lock-eligible habit."
        }
    }
}

/**
 * The conservative default top-of-ladder for a habit.
 *
 * Non-lock-eligible habits default to R2 (RESOLUTIONS §B: "water terminates at R2"). A caller may
 * pass R3_VOICE explicitly to `arm` for a habit that warrants a spoken rung; it may not pass R4.
 */
fun defaultTerminalRung(habit: Habit): Rung =
    if (habit.lockEligible) Rung.R4_LOCK else Rung.R2_ALARM

/**
 * Arm a challenge. Clamps `terminalRung` so R4 is STRUCTURALLY unreachable for anything that is not
 * an opted-in, lock-eligible habit — a caller cannot ask for a lock over a glass of water.
 */
fun arm(
    challengeId: String,
    habit: Habit,
    fireAt: Long,
    expiresAt: Long,
    lockOptIn: Boolean = false,
    terminalRung: Rung = defaultTerminalRung(habit),
): EscalationState {
    val clamped = if (habit.lockEligible && lockOptIn) terminalRung
    else minRung(terminalRung, Rung.R3_VOICE)!!
    return EscalationState(
        challengeId = challengeId,
        habitId = habit.id,
        rung = Rung.R0_NOTIFICATION,
        phase = Phase.ARMED,
        armedAt = fireAt,
        expiresAt = expiresAt,
        terminalRung = clamped,
        lockEligible = habit.lockEligible,
        lockOptIn = lockOptIn,
    )
}

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed interface Event {
    /**
     * An alarm rang. Carries BOTH clocks.
     *
     * `wallAt` is wall-clock epoch millis; `elapsedAt` is the same instant derived from monotonic
     * elapsed-realtime. They agree unless the wall clock was moved. RESOLUTIONS: a divergence over
     * 90 s AUTO-VOIDS the challenge. It is not a catch, it carries no penalty, and Rip does not get
     * to insinuate anything about it — a timezone flight and a cheat produce the same two numbers,
     * and the app does not accuse a man for boarding a plane.
     */
    data class AlarmFired(
        val rung: Rung,
        val scheduledFor: Long,
        val wallAt: Long,
        val elapsedAt: Long = wallAt,
    ) : Event

    /** Zero-assertion. Nothing is rejected here. */
    data object ProofLogged : Event

    /** Free, unlimited, warm, never priced. The button is cheaper than lying. Always. */
    data object Confessed : Event

    /**
     * ONE TAP. INSTANT. FIRST TAP. No countdown, no hold, no confirm, no puzzle, no cognition.
     * Unlimited. Never rate-limited. Never degraded. Never mocked in the moment — or ever.
     */
    data object BreakGlass : Event

    data class Evasion(val kind: EvasionKind) : Event

    /** Drives expiry and the post-interlock settle. It never enters a rung — alarms do that. */
    data object Tick : Event

    /** BOOT_COMPLETED. Resume at the rung he SHOULD be at. Never R0. */
    data class Reboot(val bootAt: Long) : Event

    /** §7's drop detector. */
    data object DropDetected : Event

    /** The +47 min OEM canary. `false` voids every penalty in the window. */
    data class CanaryResult(val passed: Boolean) : Event

    /** The window closed. */
    data object Expired : Event

    /** The behaviour is now complete and unchangeable. The coffee is drunk. */
    data object ActionNoLongerPerformable : Event
}

// ---------------------------------------------------------------------------
// step
// ---------------------------------------------------------------------------

/**
 * THE STATE MACHINE. Total, deterministic, side-effect-free.
 *
 * Order matters and the order is the safety argument:
 *   1. BREAK GLASS, before everything, unconditionally, from any phase, at any rung.
 *   2. Terminal phases absorb everything else.
 *   3. Satisfaction and stand-down.
 *   4. Auto-void.
 *   5. Idempotency.
 *   6. Interlocks.
 *   7. Only then does anything fire.
 */
fun step(
    state: EscalationState,
    event: Event,
    ctx: DeviceContext,
    now: Long,
): Pair<EscalationState, List<Effect>> {

    // 1. BREAK GLASS ---------------------------------------------------------
    // First. Before the terminal check, before the interlocks, before any thought whatsoever.
    // It emits Cancel and NOTHING else: no Log, no Speak, no counter. `break_glass` is recorded by
    // `:app` in its own private table that no report query may reach, and the shame — such as it is —
    // is deferred to Sunday and then not rendered there either. RESOLUTIONS §B: "no query may name
    // break_glass at all."
    //
    //   "Gone. Go."
    //
    // Nothing follows. Not now, not Sunday, not in a callback, not ever.
    if (event is Event.BreakGlass) {
        return state.copy(phase = Phase.BROKEN_GLASS, frozenRung = null, suspendedSince = null) to
                listOf(Effect.Cancel(state.challengeId, state.rung))
    }

    // 2. Terminal absorbs ----------------------------------------------------
    if (state.phase.terminal) return state to emptyList()

    return when (event) {
        is Event.BreakGlass -> state to emptyList() // handled above; keeps `when` exhaustive

        // 3. Satisfaction ----------------------------------------------------
        Event.ProofLogged, Event.Confessed ->
            state.copy(phase = Phase.SATISFIED) to listOf(Effect.Cancel(state.challengeId, state.rung))

        Event.DropDetected ->
            state.copy(phase = Phase.STOOD_DOWN) to listOf(
                Effect.Cancel(state.challengeId, state.rung),
                Effect.Log(state.challengeId, state.rung, LogKind.STOOD_DOWN),
            )

        is Event.CanaryResult ->
            if (event.passed) state to emptyList()
            else voidWith(state, VoidReason.OEM_CANARY_FAIL)

        Event.Expired ->
            state.copy(phase = Phase.EXPIRED) to listOf(Effect.Cancel(state.challengeId, state.rung))

        Event.ActionNoLongerPerformable -> {
            // Cap at R0 from here on. Nothing above R0 can fire again for this challenge, because
            // the NOT_PERFORMABLE interlock is folded into the ceiling on every subsequent rung.
            val next = state.copy(actionStillPerformable = false)
            val effects = if (state.rung.ordinal > Rung.R0_NOTIFICATION.ordinal)
                listOf(Effect.Cancel(state.challengeId, state.rung)) else emptyList()
            next to effects
        }

        is Event.Evasion -> {
            val next = state.copy(evasionCount = state.evasionCount + 1)
            val log = Effect.Log(state.challengeId, state.rung, LogKind.EVASION, evasion = event.kind)
            // The boomerang. SPEC §6.4: you cannot block HOME, so Rip narrates his own impotence and
            // comes back in four hundred milliseconds because he is forty megabytes and a dream.
            // Re-emitting ShowLock for the SAME (challengeId, rung) is idempotent by construction.
            val boomerang =
                if (event.kind == EvasionKind.HOME &&
                    state.phase == Phase.CLIMBING &&
                    state.rung == Rung.R4_LOCK &&
                    Rung.R4_LOCK in state.enteredRungs
                ) listOf(Effect.ShowLock(state.challengeId, Rung.R4_LOCK, state.lastRungAt + LOCK_EXPIRY_MS))
                else emptyList()
            next to (listOf(log) + boomerang)
        }

        is Event.AlarmFired -> onAlarmFired(state, event, ctx, now)
        is Event.Reboot -> onReboot(state, event, ctx)
        Event.Tick -> onTick(state, ctx, now)
    }
}

// --- the branches ----------------------------------------------------------

private fun voidWith(state: EscalationState, reason: VoidReason): Pair<EscalationState, List<Effect>> =
    state.copy(phase = Phase.VOID_PLATFORM) to listOf(
        Effect.Void(state.challengeId, state.rung, reason),
        Effect.Cancel(state.challengeId, state.rung),
    )

private fun onAlarmFired(
    state: EscalationState,
    e: Event.AlarmFired,
    ctx: DeviceContext,
    now: Long,
): Pair<EscalationState, List<Effect>> {

    // 4. AUTO-VOID. Both clocks, before anything is decided.
    //
    // Clock tamper first: it is the one that most looks like cheating and most often isn't. It is
    // NOT a catch. There is no penalty and no accusation — `caught_event.kind` is {BYTE_REPLAY} and
    // nothing else (RESOLUTIONS §A2), and this is not it.
    if (kotlin.math.abs(e.wallAt - e.elapsedAt) > CLOCK_TAMPER_TOLERANCE_MS) {
        return voidWith(state, VoidReason.CLOCK_TAMPER)
    }
    // Then the platform. SPEC §6.3: an app that admits its own bugs is a month-8 app, and OEM
    // battery murder is the single most likely uninstall cause in this design.
    if (kotlin.math.abs(e.wallAt - e.scheduledFor) > ALARM_LATE_TOLERANCE_MS) {
        return voidWith(state, VoidReason.PLATFORM_LATE)
    }

    // 5. IDEMPOTENCY. Alarms *do* double-fire on some OEMs. A double-penalty is an uninstall.
    if (e.rung in state.enteredRungs) return state to emptyList()

    // Off the top of this habit's ladder.
    if (e.rung.ordinal > state.terminalRung.ordinal) {
        return state.copy(phase = Phase.EXPIRED) to listOf(Effect.Cancel(state.challengeId, state.rung))
    }

    // 6. INTERLOCKS.
    val ceiling = ceilingFor(state, ctx, now, isFirstRung = state.enteredRungs.isEmpty())
    if (ceiling == null || e.rung.ordinal > ceiling.ordinal) {
        val blockers = blockingInterlocksFor(state, ctx, now, e.rung, state.enteredRungs.isEmpty())
        return state.copy(
            phase = Phase.SUSPENDED,
            frozenRung = e.rung,
            suspendedSince = state.suspendedSince ?: now,
        ) to listOf(
            // Logged, because SPEC §6.7 row 6 is right that unlogged escapes become the default path
            // within a month. Logged is not mocked and is not counted: no penalty is attached here.
            Effect.Log(state.challengeId, e.rung, LogKind.INTERLOCK_SUSPEND, interlock = blockers.firstOrNull())
        )
    }

    // 7. Fire.
    return enter(state, e.rung, ceiling, now)
}

/** Enter a rung: the only place in this file that produces a demanding effect. */
private fun enter(
    state: EscalationState,
    rung: Rung,
    ceiling: Rung,
    now: Long,
): Pair<EscalationState, List<Effect>> {
    val next = state.copy(
        phase = Phase.CLIMBING,
        rung = rung,
        enteredRungs = state.enteredRungs + rung,
        frozenRung = null,
        suspendedSince = null,
        lastRungAt = now,
    )
    val effects = mutableListOf<Effect>()
    effects += when (rung) {
        Rung.R0_NOTIFICATION -> Effect.ShowNotification(state.challengeId, rung)
        Rung.R1_VIBRATE -> Effect.Vibrate(state.challengeId, rung)
        Rung.R2_ALARM -> Effect.PlayAlarm(state.challengeId, rung)
        Rung.R3_VOICE -> Effect.Speak(state.challengeId, rung)
        Rung.R4_LOCK -> Effect.ShowLock(state.challengeId, rung, now + LOCK_EXPIRY_MS)
    }
    // Write-ahead: `:app` persists this in a transaction BEFORE it calls setAlarmClock. A crash
    // between write and schedule is recoverable; a crash between fire and write is a double penalty.
    val up = rung.next()
    if (up != null && up.ordinal <= state.terminalRung.ordinal && up.ordinal <= ceiling.ordinal) {
        effects += Effect.ScheduleNext(state.challengeId, up, state.armedAt + up.offsetMs)
    }
    return next to effects
}

private fun onReboot(
    state: EscalationState,
    e: Event.Reboot,
    ctx: DeviceContext,
): Pair<EscalationState, List<Effect>> {
    val log = Effect.Log(state.challengeId, state.rung, LogKind.EVASION, evasion = EvasionKind.REBOOT)
    val next = state.copy(evasionCount = state.evasionCount + 1)

    // Grace. SPEC §6.6: don't ambush him with yesterday's water.
    if (e.bootAt - state.armedAt > REBOOT_GRACE_MS || e.bootAt >= state.expiresAt) {
        return next.copy(phase = Phase.EXPIRED) to
                listOf(log, Effect.Cancel(state.challengeId, state.rung))
    }

    // Resume at the rung he SHOULD be at, computed from armedAt. NEVER restart at R0 — reboot would
    // become the cheapest evasion in the app. Blocking reboot is impossible and monstrous, so the
    // defence is arithmetic, not force.
    val shouldBe = rungAt(e.bootAt - state.armedAt, state.terminalRung)
    val ceiling = ceilingFor(next, ctx, e.bootAt, isFirstRung = next.enteredRungs.isEmpty())
    if (ceiling == null || shouldBe.ordinal > ceiling.ordinal) {
        return next.copy(
            phase = Phase.SUSPENDED,
            frozenRung = shouldBe,
            suspendedSince = e.bootAt,
        ) to listOf(log)
    }
    if (shouldBe in next.enteredRungs) {
        // Already served that rung before the reboot; just restore position and re-arm the next one.
        val up = shouldBe.next()
        val re = if (up != null && up.ordinal <= state.terminalRung.ordinal && up.ordinal <= ceiling.ordinal)
            listOf(Effect.ScheduleNext(state.challengeId, up, state.armedAt + up.offsetMs)) else emptyList()
        return next.copy(phase = Phase.CLIMBING, rung = shouldBe) to (listOf(log) + re)
    }
    val (resumed, effects) = enter(next, shouldBe, ceiling, e.bootAt)
    return resumed to (listOf(log) + effects)
}

private fun onTick(
    state: EscalationState,
    ctx: DeviceContext,
    now: Long,
): Pair<EscalationState, List<Effect>> {

    // The lock's unconditional 90-second self-expiry. A coarse model's false negative must never trap
    // a man in his own phone. This fires regardless of proof.
    if (state.phase == Phase.CLIMBING && state.rung == Rung.R4_LOCK &&
        now - state.lastRungAt >= LOCK_EXPIRY_MS
    ) {
        return state.copy(phase = Phase.EXPIRED) to listOf(Effect.Cancel(state.challengeId, state.rung))
    }

    if (now >= state.expiresAt) {
        return state.copy(phase = Phase.EXPIRED) to listOf(Effect.Cancel(state.challengeId, state.rung))
    }

    if (state.phase != Phase.SUSPENDED) return state to emptyList()

    val frozen = state.frozenRung ?: return state to emptyList()
    val since = state.suspendedSince ?: return state to emptyList()
    if (now - since < SETTLE_MS) return state to emptyList()

    val ceiling = ceilingFor(state, ctx, now, isFirstRung = state.enteredRungs.isEmpty())
    if (ceiling == null || frozen.ordinal > ceiling.ordinal) return state to emptyList()

    if (frozen in state.enteredRungs) {
        return state.copy(phase = Phase.CLIMBING, frozenRung = null, suspendedSince = null) to
                listOf(Effect.Log(state.challengeId, frozen, LogKind.INTERLOCK_RESUME))
    }
    val (resumed, effects) = enter(state, frozen, ceiling, now)
    return resumed to (listOf(Effect.Log(state.challengeId, frozen, LogKind.INTERLOCK_RESUME)) + effects)
}

// --- the ceiling -----------------------------------------------------------

/**
 * The device ceiling, narrowed by everything this challenge knows about itself.
 *
 * `isFirstRung` exists for one reason: the "one ladder per 90 minutes" interlock is a LADDER-START
 * gate, not a rung gate. R1 fires seven minutes after R0 — if the cooldown were re-evaluated on
 * every rung, `ctx.lastEscalationAt` (which the planner sets to the ladder that just started) would
 * suspend the ladder against itself at R1, forever. The ladder that fired 90 minutes ago is not
 * allowed to be *this* ladder.
 */
internal fun ceilingFor(
    state: EscalationState,
    ctx: DeviceContext,
    now: Long,
    isFirstRung: Boolean,
): Rung? {
    val effective = if (isFirstRung) ctx else ctx.copy(lastEscalationAt = null)
    var ceiling = escalationCeiling(effective, now)
    // NEVER RETRIBUTIVE. If the behaviour is complete and unchangeable, the only permitted response
    // is data. This is what kills the coffee lock at 15:05: the coffee is drunk; locking his phone
    // over it can only produce shame, shame produces hiding, and hiding blinds the sleep pillar the
    // rule existed to protect. Prompt at 14:45, before.
    if (!state.actionStillPerformable) ceiling = minRung(ceiling, Rung.R0_NOTIFICATION)
    // Water can never reach R4, by three independent mechanisms. This is the second.
    if (!state.lockEligible || !state.lockOptIn) ceiling = minRung(ceiling, Rung.R3_VOICE)
    ceiling = minRung(ceiling, state.terminalRung)
    return ceiling
}

internal fun blockingInterlocksFor(
    state: EscalationState,
    ctx: DeviceContext,
    now: Long,
    rung: Rung,
    isFirstRung: Boolean,
): Set<Interlock> {
    val effective = if (isFirstRung) ctx else ctx.copy(lastEscalationAt = null)
    val out = LinkedHashSet(blockingInterlocks(rung, effective, now))
    if (!state.actionStillPerformable && rung.ordinal > Rung.R0_NOTIFICATION.ordinal) {
        out += Interlock.NOT_PERFORMABLE
    }
    if ((!state.lockEligible || !state.lockOptIn) && rung == Rung.R4_LOCK) {
        out += Interlock.HABIT_NOT_LOCK_ELIGIBLE
    }
    return out
}

// ---------------------------------------------------------------------------
// Penalty debt — SPEC §6.9
// ---------------------------------------------------------------------------

/**
 * Ceiling 20 reps/day. Expires at end of day. NEVER accrues.
 *
 * The "never accrues" is the whole rule: a debt that carries forward is a compulsion engine that
 * writes its own escalation. A new day starts at zero no matter what happened yesterday.
 */
data class PenaltyDebt(val epochDay: Long, val reps: Int) {
    init { require(reps in 0..PENALTY_DEBT_CEILING_REPS) { "penalty debt must be 0..$PENALTY_DEBT_CEILING_REPS" } }
}

/** Add reps to today's debt. Crossing midnight resets to zero first — the old debt is simply gone. */
fun accruePenalty(current: PenaltyDebt?, epochDay: Long, addReps: Int): PenaltyDebt {
    require(addReps >= 0) { "penalty reps cannot be negative" }
    val base = if (current == null || current.epochDay != epochDay) 0 else current.reps
    return PenaltyDebt(epochDay, minOf(base + addReps, PENALTY_DEBT_CEILING_REPS))
}
