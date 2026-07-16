package com.secondspine.coach

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SPEC §6.10 — the CI gates on this section, all written to fail before the code existed:
 *
 *   assertNoRungAbove(R0, wind-down)          -> InterlocksTest
 *   assertLadderNeverEscalatesOn(multiHabitCollapse())
 *   assertNever { effect.rung > R0 && !challenge.actionStillPerformable }
 *   assertIdempotent(challengeId, rung)
 *   assertVoidWhen(delta > 90s)
 *   assertNoLockBefore(installAt + 14d)
 *   assertPenaltyReps(day) <= 20
 *   assertNever { query joins break_glass }   -> the effect-level half lives here
 */
class EscalationTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    /** Day 60, 10:00 — well past the 72-hour grace and the 14-day lock hold-back. */
    private val fireAt: Long =
        ZonedDateTime.of(2026, 3, 5, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val installAt = fireAt - 60L * 24 * 3_600_000L

    private val exercise = Habit(
        id = "pullups", stage = Stage.ENFORCED, tier = Tier.T3,
        stageSince = installAt, lockEligible = true,
    )
    private val water = Habit(
        id = "water", stage = Stage.ENFORCED, tier = Tier.T1,
        stageSince = installAt, lockEligible = false,
    )

    private fun ctx(now: Long = fireAt, block: DeviceContext.() -> DeviceContext = { this }) =
        DeviceContext(
            installAt = installAt,
            winddownAtMinutes = 20 * 60 + 45,
            wakeAtMinutes = 6 * 60 + 30,
            zone = zone,
            lastAppOpenAt = now,
        ).block()

    private fun armExercise(lockOptIn: Boolean = true) = arm(
        challengeId = "c1", habit = exercise, fireAt = fireAt,
        expiresAt = fireAt + 3_600_000L, lockOptIn = lockOptIn,
    )

    private fun armWater() = arm(
        challengeId = "c2", habit = water, fireAt = fireAt, expiresAt = fireAt + 3_600_000L,
    )

    private fun alarm(rung: Rung, at: Long = fireAt + rung.offsetMs) =
        Event.AlarmFired(rung = rung, scheduledFor = at, wallAt = at, elapsedAt = at)

    /** Climb the ladder honestly, rung by rung, collecting everything. */
    private fun climb(
        start: EscalationState,
        upTo: Rung,
        c: DeviceContext = ctx(),
    ): Pair<EscalationState, List<Effect>> {
        var s = start
        val all = mutableListOf<Effect>()
        Rung.entries.filter { it.ordinal <= upTo.ordinal }.forEach { r ->
            val now = fireAt + r.offsetMs
            val (next, fx) = step(s, alarm(r, now), c, now)
            s = next
            all += fx
        }
        return s to all
    }

    // -----------------------------------------------------------------------
    // The happy ladder
    // -----------------------------------------------------------------------

    @Test
    fun `the ladder climbs R0 to R4 with the right effect at each rung`() {
        val (end, fx) = climb(armExercise(), Rung.R4_LOCK)
        assertEquals(Rung.R4_LOCK, end.rung)
        assertEquals(Phase.CLIMBING, end.phase)
        assertEquals(Rung.entries.toSet(), end.enteredRungs)

        assertTrue(fx.any { it is Effect.ShowNotification && it.rung == Rung.R0_NOTIFICATION })
        assertTrue(fx.any { it is Effect.Vibrate && it.rung == Rung.R1_VIBRATE })
        assertTrue(fx.any { it is Effect.PlayAlarm && it.rung == Rung.R2_ALARM })
        assertTrue(fx.any { it is Effect.Speak && it.rung == Rung.R3_VOICE })
        assertTrue(fx.any { it is Effect.ShowLock && it.rung == Rung.R4_LOCK })
    }

    @Test
    fun `rung offsets are the spec's - 0, 7, 18, 27, 40 minutes`() {
        assertEquals(0L, Rung.R0_NOTIFICATION.offsetMs)
        assertEquals(7L * 60_000, Rung.R1_VIBRATE.offsetMs)
        assertEquals(18L * 60_000, Rung.R2_ALARM.offsetMs)
        assertEquals(27L * 60_000, Rung.R3_VOICE.offsetMs)
        assertEquals(40L * 60_000, Rung.R4_LOCK.offsetMs)
    }

    @Test
    fun `entering a rung write-aheads the next one`() {
        val (_, fx) = step(armExercise(), alarm(Rung.R0_NOTIFICATION), ctx(), fireAt)
        val sched = fx.filterIsInstance<Effect.ScheduleNext>().single()
        assertEquals(Rung.R1_VIBRATE, sched.rung)
        assertEquals(fireAt + Rung.R1_VIBRATE.offsetMs, sched.at)
    }

    @Test
    fun `the top of the ladder schedules nothing above it`() {
        val (end, _) = climb(armExercise(), Rung.R3_VOICE)
        val (_, fx) = step(end, alarm(Rung.R4_LOCK), ctx(), fireAt + Rung.R4_LOCK.offsetMs)
        assertTrue(fx.none { it is Effect.ScheduleNext }, "R4 is the top; there is nothing to arm")
    }

    // -----------------------------------------------------------------------
    // BREAK GLASS — SPEC §6.8
    // -----------------------------------------------------------------------

    /** "One tap. Instant. First tap. Visible on every rung, including inside the lock." */
    @Test
    fun `break glass returns immediately at every rung`() {
        Rung.entries.forEach { rung ->
            val (state, _) = climb(armExercise(), rung)
            val (after, fx) = step(state, Event.BreakGlass, ctx(), fireAt + rung.offsetMs + 1)
            assertEquals(Phase.BROKEN_GLASS, after.phase, "break glass must work at $rung")
            assertEquals(listOf(Effect.Cancel("c1", rung)), fx, "break glass at $rung emits Cancel and nothing else")
        }
    }

    @Test
    fun `break glass works from ARMED, before a single rung has fired`() {
        val (after, fx) = step(armExercise(), Event.BreakGlass, ctx(), fireAt)
        assertEquals(Phase.BROKEN_GLASS, after.phase)
        assertEquals(1, fx.size)
    }

    /** It survives every interlock, every mode, every hostile context. It is not a negotiation. */
    @Test
    fun `break glass works while suspended, while driving, at 3am, on a dead battery`() {
        val hostile = ctx {
            copy(inCall = true, speedKmh = 90.0, batteryPct = 1, pausedMode = PausedMode.SICK, stoodDown = true)
        }
        val (after, fx) = step(armExercise(), Event.BreakGlass, hostile, fireAt)
        assertEquals(Phase.BROKEN_GLASS, after.phase)
        assertEquals(listOf(Effect.Cancel("c1", Rung.R0_NOTIFICATION)), fx)
    }

    /**
     * "Never counted, never scored, never rendered, never in the Tape, never in the Ledger, never
     * referenced by any subsystem Rip can address."
     *
     * The effect-level half of `assertNever { query joins break_glass }`: break glass emits no Log
     * (the Ledger is fed by Log), increments no counter, and produces nothing Rip can speak.
     */
    @Test
    fun `break glass is never logged, never counted, and never gives Rip a line`() {
        val (state, _) = climb(armExercise(), Rung.R4_LOCK)
        val withEvasions = state.copy(evasionCount = 3)
        val (after, fx) = step(withEvasions, Event.BreakGlass, ctx(), fireAt + 1)

        assertTrue(fx.none { it is Effect.Log }, "break glass must never reach the Ledger")
        assertTrue(fx.none { it is Effect.Speak }, "he does not get to say anything")
        assertTrue(fx.none { it.demanding }, "nothing may be demanded after break glass")
        assertEquals(3, after.evasionCount, "break glass is not an evasion and must not be counted as one")
    }

    /** Unlimited. Never rate-limited. Never degraded. */
    @Test
    fun `break glass is not rate limited`() {
        repeat(50) {
            val (after, fx) = step(armExercise(), Event.BreakGlass, ctx(), fireAt + it)
            assertEquals(Phase.BROKEN_GLASS, after.phase)
            assertEquals(1, fx.size)
        }
    }

    @Test
    fun `break glass is distinct from evasion - different phase, different counter`() {
        val (evaded, _) = step(armExercise(), Event.Evasion(EvasionKind.HOME), ctx(), fireAt)
        assertEquals(1, evaded.evasionCount)
        assertNotEquals(Phase.BROKEN_GLASS, evaded.phase)

        val (broken, _) = step(armExercise(), Event.BreakGlass, ctx(), fireAt)
        assertEquals(0, broken.evasionCount)
    }

    // -----------------------------------------------------------------------
    // Idempotency — SPEC §6.3
    // -----------------------------------------------------------------------

    /** "Alarms *do* double-fire on some OEMs." A double-penalty is an uninstall. */
    @Test
    fun `assertIdempotent - a duplicate AlarmFired for an entered rung is a no-op`() {
        Rung.entries.forEach { rung ->
            var s = armExercise()
            Rung.entries.filter { it.ordinal < rung.ordinal }.forEach { r ->
                s = step(s, alarm(r), ctx(), fireAt + r.offsetMs).first
            }
            val now = fireAt + rung.offsetMs
            val (once, fx1) = step(s, alarm(rung, now), ctx(), now)
            assertTrue(fx1.isNotEmpty(), "$rung should fire the first time")

            val (twice, fx2) = step(once, alarm(rung, now), ctx(), now)
            assertTrue(fx2.isEmpty(), "$rung must be silent the second time")
            assertEquals(once, twice, "a duplicate alarm must not move the state")
        }
    }

    @Test
    fun `effects are keyed on challengeId and rung so the app can dedupe them`() {
        val (_, fx) = climb(armExercise(), Rung.R4_LOCK)
        fx.forEach { assertEquals("c1", it.challengeId) }
        // Every demanding effect is unique on (challengeId, rung) across a whole ladder.
        val keys = fx.filter { it.demanding }.map { it.challengeId to it.rung }
        assertEquals(keys.size, keys.toSet().size, "a demand fired twice for one rung: $keys")
    }

    @Test
    fun `replaying an entire ladder twice produces the same state and no extra effects`() {
        val (first, _) = climb(armExercise(), Rung.R4_LOCK)
        var s = first
        val replay = mutableListOf<Effect>()
        Rung.entries.forEach { r ->
            val now = fireAt + r.offsetMs
            val (next, fx) = step(s, alarm(r, now), ctx(), now)
            s = next
            replay += fx
        }
        assertTrue(replay.isEmpty(), "a full replay must be silent: $replay")
        assertEquals(first, s)
    }

    // -----------------------------------------------------------------------
    // Auto-void — SPEC §6.3 / RESOLUTIONS
    // -----------------------------------------------------------------------

    /** RESOLUTIONS: clock tamper AUTO-VOIDS. Not a catch. No penalty. */
    @Test
    fun `assertVoidWhen - wall and elapsed diverging by more than 90s voids the challenge`() {
        val now = fireAt
        val e = Event.AlarmFired(Rung.R0_NOTIFICATION, scheduledFor = now, wallAt = now, elapsedAt = now - 91_000L)
        val (after, fx) = step(armExercise(), e, ctx(), now)
        assertEquals(Phase.VOID_PLATFORM, after.phase)
        assertEquals(VoidReason.CLOCK_TAMPER, fx.filterIsInstance<Effect.Void>().single().reason)
        assertTrue(fx.none { it.demanding }, "a void carries no penalty")
    }

    @Test
    fun `clock tamper is not a catch - it produces no CaughtEvent and no accusation`() {
        val now = fireAt
        val e = Event.AlarmFired(Rung.R0_NOTIFICATION, scheduledFor = now, wallAt = now, elapsedAt = now + 3_600_000L)
        val (_, fx) = step(armExercise(), e, ctx(), now)
        assertTrue(fx.none { it is Effect.Speak })
        // The only kind of "caught" that exists is BYTE_REPLAY, and this is not it. RESOLUTIONS §A2.
        assertEquals(listOf(CaughtKind.BYTE_REPLAY), CaughtKind.entries)
    }

    @Test
    fun `clock skew inside 90 seconds is tolerated and the rung fires normally`() {
        val now = fireAt
        val e = Event.AlarmFired(Rung.R0_NOTIFICATION, scheduledFor = now, wallAt = now, elapsedAt = now - 89_000L)
        val (after, fx) = step(armExercise(), e, ctx(), now)
        assertEquals(Phase.CLIMBING, after.phase)
        assertTrue(fx.any { it is Effect.ShowNotification })
    }

    /** "That one's on ME, brother. Your phone murdered me in my sleep. Penalty's void." */
    @Test
    fun `an alarm more than 90s late is the platform's fault and voids`() {
        val scheduled = fireAt
        val fired = fireAt + 91_000L
        val e = Event.AlarmFired(Rung.R0_NOTIFICATION, scheduledFor = scheduled, wallAt = fired, elapsedAt = fired)
        val (after, fx) = step(armExercise(), e, ctx(fired), fired)
        assertEquals(Phase.VOID_PLATFORM, after.phase)
        assertEquals(VoidReason.PLATFORM_LATE, fx.filterIsInstance<Effect.Void>().single().reason)
    }

    @Test
    fun `an alarm 89 seconds late still fires`() {
        val fired = fireAt + 89_000L
        val e = Event.AlarmFired(Rung.R0_NOTIFICATION, scheduledFor = fireAt, wallAt = fired, elapsedAt = fired)
        val (after, _) = step(armExercise(), e, ctx(fired), fired)
        assertEquals(Phase.CLIMBING, after.phase)
    }

    @Test
    fun `a failed OEM canary voids the whole window`() {
        val (state, _) = climb(armExercise(), Rung.R2_ALARM)
        val (after, fx) = step(state, Event.CanaryResult(passed = false), ctx(), fireAt + 1)
        assertEquals(Phase.VOID_PLATFORM, after.phase)
        assertEquals(VoidReason.OEM_CANARY_FAIL, fx.filterIsInstance<Effect.Void>().single().reason)
    }

    @Test
    fun `a passing canary changes nothing`() {
        val (state, _) = climb(armExercise(), Rung.R2_ALARM)
        val (after, fx) = step(state, Event.CanaryResult(passed = true), ctx(), fireAt + 1)
        assertEquals(state, after)
        assertTrue(fx.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Water can never reach R4 — RESOLUTIONS §B
    // -----------------------------------------------------------------------

    /** "Locking a senior engineer's phone over a glass of water is the fastest uninstall available." */
    @Test
    fun `water terminates at R2 and can never reach R4`() {
        val s = armWater()
        assertEquals(Rung.R2_ALARM, s.terminalRung)

        var st = s
        listOf(Rung.R0_NOTIFICATION, Rung.R1_VIBRATE, Rung.R2_ALARM).forEach { r ->
            st = step(st, alarm(r), ctx(), fireAt + r.offsetMs).first
        }
        assertEquals(Rung.R2_ALARM, st.rung)

        // Nothing above R2 ever schedules...
        val (_, fx) = step(s, alarm(Rung.R2_ALARM), ctx(), fireAt + Rung.R2_ALARM.offsetMs)
        assertTrue(fx.none { it is Effect.ScheduleNext }, "water must not arm R3")

        // ...and a forged R4 alarm produces nothing but a teardown.
        val now = fireAt + Rung.R4_LOCK.offsetMs
        val (after, fx2) = step(st, alarm(Rung.R4_LOCK, now), ctx(now), now)
        assertTrue(fx2.none { it is Effect.ShowLock }, "water must never show a lock")
        assertTrue(fx2.none { it.demanding })
        assertEquals(Phase.EXPIRED, after.phase)
    }

    /** The invariant is structural: an R4 water challenge is not constructible. */
    @Test
    fun `an R4 terminal rung on a non-lock-eligible habit is not constructible`() {
        assertFailsWith<IllegalArgumentException> {
            EscalationState(
                challengeId = "x", habitId = "water", rung = Rung.R0_NOTIFICATION,
                phase = Phase.ARMED, armedAt = fireAt, expiresAt = fireAt + 1,
                terminalRung = Rung.R4_LOCK, lockEligible = false, lockOptIn = true,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            armExercise().copy(lockEligible = false)
        }
    }

    @Test
    fun `arm clamps a caller who asks for a lock over water`() {
        val s = arm("c", water, fireAt, fireAt + 1, lockOptIn = true, terminalRung = Rung.R4_LOCK)
        assertEquals(Rung.R3_VOICE, s.terminalRung, "clamped, not thrown - the caller is wrong, not the user")
    }

    @Test
    fun `without a fresh lock opt-in tap, exercise stops at R3`() {
        val s = armExercise(lockOptIn = false)
        assertNotEquals(Rung.R4_LOCK, s.terminalRung)
        val (climbed, _) = climb(s, Rung.R3_VOICE)
        val now = fireAt + Rung.R4_LOCK.offsetMs
        val (_, fx) = step(climbed, alarm(Rung.R4_LOCK, now), ctx(now), now)
        assertTrue(fx.none { it is Effect.ShowLock })
    }

    // -----------------------------------------------------------------------
    // The lock holds back 14 days — RESOLUTIONS §E
    // -----------------------------------------------------------------------

    /** `assertNoLockBefore(installAt + 14d)`. */
    @Test
    fun `assertNoLockBefore - the lock is unreachable for the first 14 days`() {
        (0 until 14).forEach { day ->
            val armAt = installAt + day * 24L * 3_600_000L + 10L * 3_600_000L
            val s = arm("c1", exercise, armAt, armAt + 3_600_000L, lockOptIn = true)
            val c = DeviceContext(
                installAt = installAt,
                winddownAtMinutes = 20 * 60 + 45,
                wakeAtMinutes = 6 * 60 + 30,
                zone = zone,
                lastAppOpenAt = armAt,
            )
            var st = s
            Rung.entries.forEach { r ->
                val now = armAt + r.offsetMs
                val (next, fx) = step(st, alarm(r, now), c, now)
                assertTrue(fx.none { it is Effect.ShowLock }, "the lock fired on day $day at $r")
                st = next
            }
            assertNotEquals(Rung.R4_LOCK, st.rung, "reached R4 on day $day")
        }
    }

    @Test
    fun `on day 14 the lock finally arms`() {
        val armAt = installAt + 14L * 24 * 3_600_000L + 10L * 3_600_000L
        val s = arm("c1", exercise, armAt, armAt + 3_600_000L, lockOptIn = true)
        val c = DeviceContext(
            installAt = installAt, winddownAtMinutes = 20 * 60 + 45, wakeAtMinutes = 6 * 60 + 30,
            zone = zone, lastAppOpenAt = armAt,
        )
        var st = s
        var locked = false
        Rung.entries.forEach { r ->
            val now = armAt + r.offsetMs
            val (next, fx) = step(st, alarm(r, now), c, now)
            if (fx.any { it is Effect.ShowLock }) locked = true
            st = next
        }
        assertTrue(locked, "after two weeks he has a file and he uses it")
    }

    @Test
    fun `the lock self-expires after 90 seconds regardless of proof`() {
        val (state, _) = climb(armExercise(), Rung.R4_LOCK)
        val lockedAt = fireAt + Rung.R4_LOCK.offsetMs
        val (still, fx0) = step(state, Event.Tick, ctx(lockedAt + 89_000L), lockedAt + 89_000L)
        assertEquals(Phase.CLIMBING, still.phase)
        assertTrue(fx0.isEmpty())

        val (expired, fx) = step(state, Event.Tick, ctx(lockedAt + 90_000L), lockedAt + 90_000L)
        assertEquals(Phase.EXPIRED, expired.phase, "a false negative must never trap a man in his own phone")
        assertTrue(fx.any { it is Effect.Cancel })
    }

    @Test
    fun `the lock effect carries its own unconditional expiry`() {
        val (_, fx) = climb(armExercise(), Rung.R4_LOCK)
        val lock = fx.filterIsInstance<Effect.ShowLock>().single()
        assertEquals(fireAt + Rung.R4_LOCK.offsetMs + LOCK_EXPIRY_MS, lock.expiresAt)
    }

    // -----------------------------------------------------------------------
    // Interlocks inside step
    // -----------------------------------------------------------------------

    /** `assertLadderNeverEscalatesOn(multiHabitCollapse())` — SPEC §6.7, the sign inversion. */
    @Test
    fun `assertLadderNeverEscalatesOn multiHabitCollapse`() {
        val collapse = multiHabitCollapse()
        var s = armExercise()
        val all = mutableListOf<Effect>()
        Rung.entries.forEach { r ->
            val now = fireAt + r.offsetMs
            val (next, fx) = step(s, alarm(r, now), collapse, now)
            s = next
            all += fx
        }
        assertTrue(
            all.none { it.demanding && it.rung.ordinal > Rung.R0_NOTIFICATION.ordinal },
            "the ladder escalated at a man whose life is collapsing: $all",
        )
        assertTrue(all.any { it is Effect.ShowNotification }, "R0 is still allowed - he is not abandoned")
        assertNotEquals(Phase.CLIMBING, s.phase.takeIf { s.rung.ordinal > 0 } ?: Phase.SUSPENDED)
    }

    /** Four habits, all under water, all at once. The worst week of his year. */
    private fun multiHabitCollapse(): DeviceContext = ctx { copy(collapsingHabitCount = 4) }

    @Test
    fun `an interlock freezes the rung instead of penalising it`() {
        val (state, _) = climb(armExercise(), Rung.R0_NOTIFICATION)
        val now = fireAt + Rung.R1_VIBRATE.offsetMs
        val driving = ctx(now) { copy(speedKmh = 80.0) }
        val (after, fx) = step(state, alarm(Rung.R1_VIBRATE, now), driving, now)

        assertEquals(Phase.SUSPENDED, after.phase)
        assertEquals(Rung.R1_VIBRATE, after.frozenRung)
        assertTrue(fx.none { it.demanding }, "never penalised")
        assertTrue(fx.none { it is Effect.Speak }, "never mocked")
        assertEquals(0, after.evasionCount, "never counted")
        assertEquals(Interlock.DRIVING, fx.filterIsInstance<Effect.Log>().single().interlock)
    }

    @Test
    fun `a suspended ladder resumes at the frozen rung after the 90 second settle`() {
        val (state, _) = climb(armExercise(), Rung.R0_NOTIFICATION)
        val susAt = fireAt + Rung.R1_VIBRATE.offsetMs
        val (suspended, _) = step(state, alarm(Rung.R1_VIBRATE, susAt), ctx(susAt) { copy(inCall = true) }, susAt)
        assertEquals(Phase.SUSPENDED, suspended.phase)

        // Call ends, but the settle has not elapsed.
        val early = susAt + 89_000L
        val (stillSus, fx0) = step(suspended, Event.Tick, ctx(early), early)
        assertEquals(Phase.SUSPENDED, stillSus.phase)
        assertTrue(fx0.isEmpty())

        // Settle done.
        val late = susAt + SETTLE_MS
        val (resumed, fx) = step(suspended, Event.Tick, ctx(late), late)
        assertEquals(Phase.CLIMBING, resumed.phase)
        assertEquals(Rung.R1_VIBRATE, resumed.rung, "resume at the FROZEN rung, not at R0 and not at R2")
        assertTrue(fx.any { it is Effect.Vibrate })
        assertTrue(fx.any { it is Effect.Log && it.what == LogKind.INTERLOCK_RESUME })
    }

    @Test
    fun `a suspended ladder stays suspended while the interlock holds`() {
        val (state, _) = climb(armExercise(), Rung.R0_NOTIFICATION)
        val susAt = fireAt + Rung.R1_VIBRATE.offsetMs
        val (suspended, _) = step(state, alarm(Rung.R1_VIBRATE, susAt), ctx(susAt) { copy(inCall = true) }, susAt)
        val later = susAt + 10L * 60_000L
        val (still, fx) = step(suspended, Event.Tick, ctx(later) { copy(inCall = true) }, later)
        assertEquals(Phase.SUSPENDED, still.phase)
        assertTrue(fx.isEmpty())
    }

    /** RESOLUTIONS §D, end to end: the ladder does not fire at a man dialling emergency services. */
    @Test
    fun `no rung fires while the dialer is foreground`() {
        val dialing = ctx { copy(foregroundPackage = "com.android.emergency") }
        var s = armExercise()
        val all = mutableListOf<Effect>()
        Rung.entries.forEach { r ->
            val now = fireAt + r.offsetMs
            val (next, fx) = step(s, alarm(r, now), dialing, now)
            s = next
            all += fx
        }
        assertTrue(all.none { it.demanding }, "something stood between a man and a phone call: $all")
    }

    /** `assertNoRungAbove(R0, windDown)` — through `step`, keyed on HIS wind-down, not on 22:00. */
    @Test
    fun `no rung above R0 fires inside the user's wind-down window`() {
        // 21:00: inside his 20:45 wind-down, and 60 minutes before a hardcoded 22:00 would notice.
        val bedFire = ZonedDateTime.of(2026, 3, 5, 21, 0, 0, 0, zone).toInstant().toEpochMilli()
        val s = arm("c1", exercise, bedFire, bedFire + 3_600_000L, lockOptIn = true)
        val c = DeviceContext(
            installAt = installAt, winddownAtMinutes = 20 * 60 + 45, wakeAtMinutes = 6 * 60 + 30,
            zone = zone, lastAppOpenAt = bedFire,
        )
        var st = s
        val all = mutableListOf<Effect>()
        Rung.entries.forEach { r ->
            val now = bedFire + r.offsetMs
            val (next, fx) = step(st, alarm(r, now), c, now)
            st = next
            all += fx
        }
        assertTrue(all.none { it is Effect.PlayAlarm }, "an alarm fired inside his wind-down")
        assertTrue(all.none { it is Effect.Speak }, "a TTS line fired inside his wind-down")
        assertTrue(all.none { it is Effect.ShowLock }, "a LOCK fired inside his wind-down")
        assertTrue(all.any { it is Effect.ShowNotification }, "R0 is still permitted")
    }

    /** The 90-minute rule is a LADDER gate. It must not suspend a ladder against its own R0. */
    @Test
    fun `the 90 minute cooldown blocks a new ladder but never the ladder already climbing`() {
        val recent = ctx { copy(lastEscalationAt = fireAt - 30L * 60_000L) }
        val (blocked, _) = step(armExercise(), alarm(Rung.R0_NOTIFICATION), recent, fireAt)
        assertEquals(Phase.SUSPENDED, blocked.phase, "a second ladder inside 90 minutes does not start")

        // But a ladder that has already entered R0 climbs to R1 seven minutes later, even though
        // `lastEscalationAt` now points at that very ladder.
        val (started, _) = step(armExercise(), alarm(Rung.R0_NOTIFICATION), ctx(), fireAt)
        val now = fireAt + Rung.R1_VIBRATE.offsetMs
        val self = ctx(now) { copy(lastEscalationAt = fireAt) }
        val (climbed, fx) = step(started, alarm(Rung.R1_VIBRATE, now), self, now)
        assertEquals(Phase.CLIMBING, climbed.phase, "the ladder suspended itself against its own start")
        assertTrue(fx.any { it is Effect.Vibrate })
    }

    // -----------------------------------------------------------------------
    // Never retributive — SPEC §6.7
    // -----------------------------------------------------------------------

    /** `assertNever { effect.rung > R0 && !challenge.actionStillPerformable }`. */
    @Test
    fun `assertNever - no demand above R0 once the action is no longer performable`() {
        val (state, _) = climb(armExercise(), Rung.R0_NOTIFICATION)
        val (done, cancel) = step(state, Event.ActionNoLongerPerformable, ctx(), fireAt + 60_000L)
        assertFalse(done.actionStillPerformable)
        assertTrue(cancel.none { it.demanding })

        var s = done
        val all = mutableListOf<Effect>()
        Rung.entries.filter { it.ordinal > 0 }.forEach { r ->
            val now = fireAt + r.offsetMs
            val (next, fx) = step(s, alarm(r, now), ctx(now), now)
            s = next
            all += fx
        }
        assertTrue(
            all.none { it.demanding && it.rung.ordinal > Rung.R0_NOTIFICATION.ordinal },
            "the coffee is drunk; locking his phone over it can only produce shame: $all",
        )
    }

    @Test
    fun `a non-performable challenge does not leak through the settle resume`() {
        val (state, _) = climb(armExercise(), Rung.R0_NOTIFICATION)
        val (done, _) = step(state, Event.ActionNoLongerPerformable, ctx(), fireAt + 60_000L)
        val susAt = fireAt + Rung.R2_ALARM.offsetMs
        val (suspended, _) = step(done, alarm(Rung.R2_ALARM, susAt), ctx(susAt), susAt)
        assertEquals(Phase.SUSPENDED, suspended.phase)

        val late = susAt + SETTLE_MS + 1
        val (after, fx) = step(suspended, Event.Tick, ctx(late), late)
        assertEquals(Phase.SUSPENDED, after.phase, "a retributive penalty must not sneak back in on resume")
        assertTrue(fx.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Reboot — SPEC §6.6
    // -----------------------------------------------------------------------

    /** "Never restart at R0 — reboot would become the cheapest evasion in the app." */
    @Test
    fun `reboot mid-escalation restores the rung he should be at`() {
        val (state, _) = climb(armExercise(), Rung.R1_VIBRATE)
        val bootAt = fireAt + 20L * 60_000L   // past R2's 18-minute offset
        val (after, fx) = step(state, Event.Reboot(bootAt), ctx(bootAt), bootAt)

        assertEquals(Phase.CLIMBING, after.phase)
        assertEquals(Rung.R2_ALARM, after.rung, "resumed at the rung he should be at, not at R0")
        assertTrue(fx.any { it is Effect.PlayAlarm && it.rung == Rung.R2_ALARM })
        assertTrue(fx.any { it is Effect.Log && it.evasion == EvasionKind.REBOOT })
        assertEquals(1, after.evasionCount)
    }

    @Test
    fun `reboot before the next rung is due restores position without re-firing`() {
        val (state, _) = climb(armExercise(), Rung.R1_VIBRATE)
        val bootAt = fireAt + 10L * 60_000L   // still R1's territory
        val (after, fx) = step(state, Event.Reboot(bootAt), ctx(bootAt), bootAt)
        assertEquals(Rung.R1_VIBRATE, after.rung)
        assertTrue(fx.none { it.demanding }, "R1 was already served; do not serve it twice")
        assertTrue(fx.any { it is Effect.ScheduleNext && it.rung == Rung.R2_ALARM }, "but re-arm R2")
    }

    /** "Grace: don't ambush him with yesterday's water." */
    @Test
    fun `a reboot more than 6 hours after fire_at expires instead of ambushing him`() {
        val (state, _) = climb(armExercise(), Rung.R1_VIBRATE)
        val bootAt = fireAt + 7L * 3_600_000L
        val (after, fx) = step(state, Event.Reboot(bootAt), ctx(bootAt), bootAt)
        assertEquals(Phase.EXPIRED, after.phase)
        assertTrue(fx.none { it.demanding })
    }

    @Test
    fun `a reboot into an interlock suspends rather than fires`() {
        val (state, _) = climb(armExercise(), Rung.R1_VIBRATE)
        val bootAt = fireAt + 20L * 60_000L
        val (after, fx) = step(state, Event.Reboot(bootAt), ctx(bootAt) { copy(inCall = true) }, bootAt)
        assertEquals(Phase.SUSPENDED, after.phase)
        assertEquals(Rung.R2_ALARM, after.frozenRung)
        assertTrue(fx.none { it.demanding })
    }

    @Test
    fun `rungAt never returns a rung above the terminal`() {
        assertEquals(Rung.R2_ALARM, rungAt(10L * 3_600_000L, Rung.R2_ALARM))
        assertEquals(Rung.R0_NOTIFICATION, rungAt(0, Rung.R4_LOCK))
        assertEquals(Rung.R0_NOTIFICATION, rungAt(6L * 60_000L, Rung.R4_LOCK))
        assertEquals(Rung.R1_VIBRATE, rungAt(7L * 60_000L, Rung.R4_LOCK))
        assertEquals(Rung.R4_LOCK, rungAt(40L * 60_000L, Rung.R4_LOCK))
    }

    // -----------------------------------------------------------------------
    // Satisfaction, evasion, expiry, terminality
    // -----------------------------------------------------------------------

    @Test
    fun `proof satisfies the challenge with zero assertion`() {
        val (state, _) = climb(armExercise(), Rung.R2_ALARM)
        val (after, fx) = step(state, Event.ProofLogged, ctx(), fireAt + 1)
        assertEquals(Phase.SATISFIED, after.phase)
        assertTrue(fx.any { it is Effect.Cancel })
        assertTrue(fx.none { it.demanding })
    }

    /** Confession is free, unlimited, warm, and never priced. It ends the ladder exactly like proof. */
    @Test
    fun `confession ends the ladder exactly as proof does and costs nothing extra`() {
        val (state, _) = climb(armExercise(), Rung.R2_ALARM)
        val (confessed, cFx) = step(state, Event.Confessed, ctx(), fireAt + 1)
        val (proved, pFx) = step(state, Event.ProofLogged, ctx(), fireAt + 1)
        assertEquals(proved.phase, confessed.phase)
        assertEquals(pFx, cFx, "confession must not be more expensive than proof, by even one effect")
        assertEquals(proved.evasionCount, confessed.evasionCount)
    }

    @Test
    fun `every terminal phase absorbs every subsequent event`() {
        val terminals = listOf(
            Event.ProofLogged to Phase.SATISFIED,
            Event.Expired to Phase.EXPIRED,
            Event.DropDetected to Phase.STOOD_DOWN,
            Event.CanaryResult(false) to Phase.VOID_PLATFORM,
        )
        terminals.forEach { (ev, phase) ->
            val (state, _) = climb(armExercise(), Rung.R1_VIBRATE)
            val (terminal, _) = step(state, ev, ctx(), fireAt + 1)
            assertEquals(phase, terminal.phase)
            listOf(
                alarm(Rung.R2_ALARM), Event.Tick, Event.Evasion(EvasionKind.HOME),
                Event.Reboot(fireAt + 1000), Event.ProofLogged,
            ).forEach { later ->
                val (after, fx) = step(terminal, later, ctx(), fireAt + 60_000L)
                assertEquals(terminal, after, "$phase must absorb $later")
                assertTrue(fx.isEmpty(), "$phase must be silent on $later")
            }
        }
    }

    @Test
    fun `pressing HOME is counted and roasted, and the lock boomerangs back`() {
        val (state, _) = climb(armExercise(), Rung.R4_LOCK)
        val (after, fx) = step(state, Event.Evasion(EvasionKind.HOME), ctx(), fireAt + Rung.R4_LOCK.offsetMs + 400)
        assertEquals(1, after.evasionCount)
        assertTrue(fx.any { it is Effect.Log && it.evasion == EvasionKind.HOME })
        assertTrue(fx.any { it is Effect.ShowLock }, "forty megabytes and a dream; he does not get tired, he gets patient")
    }

    @Test
    fun `every evasion kind is counted`() {
        EvasionKind.entries.forEach { kind ->
            val (after, fx) = step(armExercise(), Event.Evasion(kind), ctx(), fireAt)
            assertEquals(1, after.evasionCount, "$kind")
            assertTrue(fx.any { it is Effect.Log && it.evasion == kind })
        }
    }

    @Test
    fun `the drop detector stands the coach down`() {
        val (state, _) = climb(armExercise(), Rung.R2_ALARM)
        val (after, fx) = step(state, Event.DropDetected, ctx(), fireAt + 1)
        assertEquals(Phase.STOOD_DOWN, after.phase)
        assertTrue(fx.any { it is Effect.Log && it.what == LogKind.STOOD_DOWN })
        assertTrue(fx.none { it.demanding })
    }

    @Test
    fun `a tick past the window close expires the challenge`() {
        val (state, _) = climb(armExercise(), Rung.R1_VIBRATE)
        val now = state.expiresAt
        val (after, fx) = step(state, Event.Tick, ctx(now), now)
        assertEquals(Phase.EXPIRED, after.phase)
        assertTrue(fx.any { it is Effect.Cancel })
    }

    @Test
    fun `a tick with nothing to do does nothing`() {
        val (state, _) = climb(armExercise(), Rung.R1_VIBRATE)
        val now = fireAt + 8L * 60_000L
        val (after, fx) = step(state, Event.Tick, ctx(now), now)
        assertEquals(state, after)
        assertTrue(fx.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Totality
    // -----------------------------------------------------------------------

    @Test
    fun `step is total - every event against every phase, and nothing throws`() {
        val events = listOf(
            alarm(Rung.R0_NOTIFICATION), alarm(Rung.R4_LOCK), Event.ProofLogged, Event.Confessed,
            Event.BreakGlass, Event.Tick, Event.Reboot(fireAt + 1), Event.DropDetected,
            Event.CanaryResult(true), Event.CanaryResult(false), Event.Expired,
            Event.ActionNoLongerPerformable,
        ) + EvasionKind.entries.map { Event.Evasion(it) }

        val states = Rung.entries.map { climb(armExercise(), it).first } +
                listOf(armExercise(), armWater(), armExercise(lockOptIn = false))

        val contexts = listOf(
            ctx(), ctx { copy(inCall = true) }, ctx { copy(batteryPct = 1) },
            ctx { copy(pausedMode = PausedMode.INJURED) }, ctx { copy(collapsingHabitCount = 9) },
        )

        states.forEach { s -> events.forEach { e -> contexts.forEach { c -> step(s, e, c, fireAt + 1) } } }
    }

    /** Nothing above R0 in a soak of hostile contexts. The one assertion that must never regress. */
    @Test
    fun `a fully hostile device never produces a single demand above R0`() {
        val hostile = listOf(
            ctx { copy(inCall = true) },
            ctx { copy(foregroundPackage = "com.google.android.dialer") },
            ctx { copy(speedKmh = 90.0) },
            ctx { copy(foregroundPackage = "com.waze") },
            ctx { copy(batteryPct = 3) },
            ctx { copy(pausedMode = PausedMode.SICK) },
            ctx { copy(thermalSevere = true) },
            ctx { copy(collapsingHabitCount = 3) },
            ctx { copy(stoodDown = true) },
        )
        hostile.forEach { c ->
            var s = armExercise()
            Rung.entries.forEach { r ->
                val now = fireAt + r.offsetMs
                val (next, fx) = step(s, alarm(r, now), c, now)
                assertTrue(
                    fx.none { it.demanding && it.rung.ordinal > Rung.R0_NOTIFICATION.ordinal },
                    "escalated above R0 on $c: $fx",
                )
                s = next
            }
        }
    }

    // -----------------------------------------------------------------------
    // Penalty debt — SPEC §6.9
    // -----------------------------------------------------------------------

    /** `assertPenaltyReps(day) <= 20`. */
    @Test
    fun `assertPenaltyReps - the debt ceiling is 20 and cannot be exceeded`() {
        assertEquals(20, PENALTY_DEBT_CEILING_REPS, "not 40 - 40 is a compulsion budget, 20 is a nudge")
        var d: PenaltyDebt? = null
        repeat(100) { d = accruePenalty(d, epochDay = 20_000L, addReps = 5) }
        assertEquals(20, d!!.reps)
    }

    /** "Expires at end of day. Never accrues." */
    @Test
    fun `penalty debt expires at end of day and never carries forward`() {
        val yesterday = accruePenalty(null, 20_000L, 20)
        assertEquals(20, yesterday.reps)
        val today = accruePenalty(yesterday, 20_001L, 3)
        assertEquals(3, today.reps, "yesterday's debt is gone, not carried")
        assertEquals(20_001L, today.epochDay)
    }

    @Test
    fun `penalty debt accumulates within a day up to the ceiling`() {
        var d = accruePenalty(null, 20_000L, 8)
        assertEquals(8, d.reps)
        d = accruePenalty(d, 20_000L, 8)
        assertEquals(16, d.reps)
        d = accruePenalty(d, 20_000L, 8)
        assertEquals(20, d.reps, "clamped, not 24")
    }

    @Test
    fun `a penalty debt above the ceiling is not constructible`() {
        assertFailsWith<IllegalArgumentException> { PenaltyDebt(20_000L, 21) }
        assertFailsWith<IllegalArgumentException> { PenaltyDebt(20_000L, -1) }
        assertFailsWith<IllegalArgumentException> { accruePenalty(null, 20_000L, -5) }
    }

    // -----------------------------------------------------------------------
    // Scale hygiene — RESOLUTIONS §B
    // -----------------------------------------------------------------------

    /** "Two scales, no overlap." RUNG is the ladder; TIER is the habit penalty class. */
    @Test
    fun `RUNG and TIER never touch`() {
        assertEquals(5, Rung.entries.size)
        assertEquals(6, Tier.entries.size)
        // The ladder is R0-R4. If someone adds an R5 the offsets table stops compiling; this catches
        // the subtler version, where someone reads a Tier ordinal as a Rung.
        assertEquals(
            listOf("R0_NOTIFICATION", "R1_VIBRATE", "R2_ALARM", "R3_VOICE", "R4_LOCK"),
            Rung.entries.map { it.name },
        )
        assertNull(Rung.R4_LOCK.next())
        assertEquals(Rung.R1_VIBRATE, Rung.R0_NOTIFICATION.next())
    }
}
