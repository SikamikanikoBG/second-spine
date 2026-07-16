package com.secondspine.coach

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SPEC §6.7: "`mayEscalate(rung)` is a pure predicate over this set, unit-tested row by row.
 * If a row is inconvenient to implement, the lock does not ship."
 *
 * So: every row gets a test.
 */
class InterlocksTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    /** Day 60: past the 72-hour grace and past the 14-day lock hold-back. */
    private fun at(hour: Int, minute: Int = 0, day: Int = 60): Long =
        ZonedDateTime.of(2026, 3, day % 28 + 1, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val installAt = at(hour = 9, day = 0) - 60L * 24 * 3_600_000L

    /** HIS times. Deliberately not 22:00-08:00, so a hardcoded window fails every test below. */
    private val WINDDOWN = 20 * 60 + 45
    private val WAKE = 6 * 60 + 30

    private fun base(now: Long) = DeviceContext(
        installAt = installAt,
        winddownAtMinutes = WINDDOWN,
        wakeAtMinutes = WAKE,
        zone = zone,
        lastAppOpenAt = now,   // he is present; ghosting is its own test
    )

    /** A calm device at 10:00, sixty days in. */
    private fun calm(
        now: Long = at(10, 0),
        block: DeviceContext.() -> DeviceContext = { this },
    ): Pair<DeviceContext, Long> = base(now).block() to now

    private fun assertCeiling(expected: Rung?, ctx: DeviceContext, now: Long) =
        assertEquals(expected, escalationCeiling(ctx, now))

    // -----------------------------------------------------------------------
    // Baseline
    // -----------------------------------------------------------------------

    @Test
    fun `a calm device permits the whole ladder`() {
        val (ctx, now) = calm()
        assertTrue(interlocksActive(ctx, now).isEmpty(), "expected no interlocks, got ${interlocksActive(ctx, now)}")
        assertCeiling(Rung.R4_LOCK, ctx, now)
        Rung.entries.forEach { assertTrue(mayEscalate(it, ctx, now), "$it should be permitted") }
    }

    // -----------------------------------------------------------------------
    // Rows 1 & 2 — the call, and the thing the call check misses
    // -----------------------------------------------------------------------

    @Test
    fun `row 1 - an active call silences everything`() {
        val (ctx, now) = calm { copy(inCall = true) }
        assertTrue(Interlock.CALL_ACTIVE in interlocksActive(ctx, now))
        assertNull(escalationCeiling(ctx, now))
        Rung.entries.forEach { assertFalse(mayEscalate(it, ctx, now), "$it must not fire during a call") }
    }

    /**
     * RESOLUTIONS §D. The whole point: `inCall` is FALSE while he is dialling. If the ladder only
     * gated on the call state, R0 would fire on the emergency keypad.
     */
    @Test
    fun `the dialer being foreground silences everything even though inCall is false`() {
        val (ctx, now) = calm { copy(inCall = false, foregroundPackage = "com.google.android.dialer") }
        assertFalse(ctx.inCall, "the premise: he is typing, not talking")
        assertTrue(Interlock.DIALER_FOREGROUND in interlocksActive(ctx, now))
        assertNull(escalationCeiling(ctx, now))
        Rung.entries.forEach { assertFalse(mayEscalate(it, ctx, now), "$it must not fire over the dialer") }
    }

    @Test
    fun `every known dialer and emergency package is gated`() {
        DEFAULT_DIALER_PACKAGES.forEach { pkg ->
            val (ctx, now) = calm { copy(foregroundPackage = pkg) }
            assertFalse(mayEscalate(Rung.R0_NOTIFICATION, ctx, now), "$pkg must gate the ladder")
        }
    }

    @Test
    fun `an ordinary foreground app does not gate anything`() {
        val (ctx, now) = calm { copy(foregroundPackage = "com.slack") }
        assertTrue(interlocksActive(ctx, now).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Row 3 — driving
    // -----------------------------------------------------------------------

    @Test
    fun `row 3 - speed over 15 kmh suspends`() {
        val (ctx, now) = calm { copy(speedKmh = 15.1) }
        assertTrue(Interlock.DRIVING in interlocksActive(ctx, now))
        assertNull(escalationCeiling(ctx, now))
    }

    @Test
    fun `row 3 - 15 kmh exactly is not driving`() {
        val (ctx, now) = calm { copy(speedKmh = 15.0) }
        assertFalse(Interlock.DRIVING in interlocksActive(ctx, now))
    }

    @Test
    fun `row 3 - walking pace is not driving`() {
        val (ctx, now) = calm { copy(speedKmh = 5.0) }
        assertTrue(interlocksActive(ctx, now).isEmpty())
    }

    @Test
    fun `row 3 - IN_VEHICLE confidence 70 suspends`() {
        val (ctx, now) = calm { copy(inVehicleConfidence = 70) }
        assertTrue(Interlock.DRIVING in interlocksActive(ctx, now))
    }

    @Test
    fun `row 3 - driving suspends for 15 minutes after the LAST vehicle signal`() {
        val now = at(10, 0)
        val (stillHot, _) = calm(now) { copy(lastVehicleSignalAt = now - 14L * 60_000L) }
        assertTrue(Interlock.DRIVING in interlocksActive(stillHot, now), "14 min after: still parked, still suspended")

        val (cooled, _) = calm(now) { copy(lastVehicleSignalAt = now - 16L * 60_000L) }
        assertFalse(Interlock.DRIVING in interlocksActive(cooled, now), "16 min after: released")
    }

    @Test
    fun `navigation foreground suspends`() {
        val (ctx, now) = calm { copy(foregroundPackage = "com.waze") }
        assertTrue(Interlock.NAVIGATION_FOREGROUND in interlocksActive(ctx, now))
        assertNull(escalationCeiling(ctx, now))
    }

    // -----------------------------------------------------------------------
    // Row 5 — the camera. CAP, not suspend: a full suspend is a free mute switch.
    // -----------------------------------------------------------------------

    @Test
    fun `row 5 - another app holding the camera caps at R1, it does not silence`() {
        val (ctx, now) = calm { copy(cameraHeldByOtherApp = true) }
        assertCeiling(Rung.R1_VIBRATE, ctx, now)
        assertTrue(mayEscalate(Rung.R1_VIBRATE, ctx, now), "a cap is not a mute switch")
        assertFalse(mayEscalate(Rung.R2_ALARM, ctx, now))
    }

    // -----------------------------------------------------------------------
    // Rows 6 & 7 — battery
    // -----------------------------------------------------------------------

    @Test
    fun `row 6 - battery under 15 percent caps at R1 and never locks`() {
        val (ctx, now) = calm { copy(batteryPct = 14) }
        assertTrue(Interlock.BATTERY_LOW in interlocksActive(ctx, now))
        assertCeiling(Rung.R1_VIBRATE, ctx, now)
        assertFalse(mayEscalate(Rung.R4_LOCK, ctx, now), "he may need maps or a call")
    }

    @Test
    fun `row 6 - power save mode caps at R1`() {
        val (ctx, now) = calm { copy(batteryPct = 90, powerSaveMode = true) }
        assertCeiling(Rung.R1_VIBRATE, ctx, now)
    }

    @Test
    fun `row 7 - battery under 5 percent is full silence`() {
        val (ctx, now) = calm { copy(batteryPct = 4) }
        assertTrue(Interlock.BATTERY_CRITICAL in interlocksActive(ctx, now))
        assertFalse(Interlock.BATTERY_LOW in interlocksActive(ctx, now), "critical replaces low, it does not stack")
        assertNull(escalationCeiling(ctx, now))
        assertFalse(mayEscalate(Rung.R0_NOTIFICATION, ctx, now))
    }

    // -----------------------------------------------------------------------
    // Row 8 — THE WIND-DOWN WINDOW, keyed on HIS times. RESOLUTIONS §D.
    // -----------------------------------------------------------------------

    /**
     * `assertNoRungAbove(R0, insideWindDownWindow)` — and the assertion is keyed on the user's
     * actual (winddownAt, wakeAt), not on the literals 22:00 and 08:00.
     *
     * RESOLUTIONS §D: with the hardcoded window and a 21:30 target bed, wind-down starts 20:45 and
     * there are "75 minutes in which the ladder can fire an alarm, a TTS line and a lock inside the
     * wind-down window" — on the pillar ranked #1. This test walks that exact hole.
     */
    @Test
    fun `assertNoRungAbove R0 inside the wind-down window`() {
        // Sweep the whole window at 5-minute steps, wrapping midnight.
        var m = WINDDOWN
        var visited = 0
        while (inMinuteWindow(m, WINDDOWN, WAKE) && visited < 500) {
            val hour = m / 60
            val minute = m % 60
            val now = at(hour, minute)
            val (ctx, _) = calm(now)
            assertTrue(inWindDownWindow(ctx, now), "$hour:$minute should be inside his wind-down")
            assertEquals(Rung.R0_NOTIFICATION, escalationCeiling(ctx, now), "ceiling at $hour:$minute")
            assertTrue(mayEscalate(Rung.R0_NOTIFICATION, ctx, now))
            Rung.entries.filter { it.ordinal > 0 }.forEach {
                assertFalse(mayEscalate(it, ctx, now), "$it must never fire at $hour:$minute")
            }
            visited++
            m = (m + 5) % (24 * 60)
        }
        // 20:45 -> 06:30 is 585 minutes; at 5-minute steps that is 117 samples.
        assertEquals(117, visited, "expected to sweep his entire wind-down window")
    }

    /** The 75-minute hole, named explicitly. A hardcoded 22:00 window fires here. We must not. */
    @Test
    fun `the 75-minute hole between his wind-down and a hardcoded 22 00 is closed`() {
        listOf(20 to 45, 21 to 0, 21 to 30, 21 to 59).forEach { (h, m) ->
            val now = at(h, m)
            val (ctx, _) = calm(now)
            assertTrue(inWindDownWindow(ctx, now), "$h:$m is inside HIS wind-down even though it is before 22:00")
            assertFalse(mayEscalate(Rung.R2_ALARM, ctx, now), "no alarm at $h:$m")
            assertFalse(mayEscalate(Rung.R3_VOICE, ctx, now), "no TTS at $h:$m")
            assertFalse(mayEscalate(Rung.R4_LOCK, ctx, now), "no lock at $h:$m")
        }
    }

    /** And the mirror: 07:00 is inside a hardcoded window but he has been awake since 06:30. */
    @Test
    fun `the window releases at HIS wake time, not at a hardcoded 08 00`() {
        val now = at(7, 0)
        val (ctx, _) = calm(now)
        assertFalse(inWindDownWindow(ctx, now), "he wakes at 06:30; 07:00 is his morning")
        assertTrue(mayEscalate(Rung.R3_VOICE, ctx, now))
    }

    @Test
    fun `wind-down keyed to a non-wrapping window still works`() {
        // A night-shift user: wind-down 07:00, wake 15:00. No midnight wrap.
        val ctx = DeviceContext(
            installAt = installAt,
            winddownAtMinutes = 7 * 60,
            wakeAtMinutes = 15 * 60,
            zone = zone,
            lastAppOpenAt = at(9, 0),
        )
        assertTrue(inWindDownWindow(ctx, at(9, 0)))
        assertFalse(inWindDownWindow(ctx, at(16, 0)))
        assertFalse(inWindDownWindow(ctx, at(3, 0)))
        assertEquals(Rung.R0_NOTIFICATION, escalationCeiling(ctx, at(9, 0)))
    }

    @Test
    fun `a zero-width wind-down window is empty, not a whole day`() {
        assertFalse(inMinuteWindow(600, 600, 600))
        assertFalse(inMinuteWindow(0, 600, 600))
    }

    // -----------------------------------------------------------------------
    // Rows 9, 11, 13, 14, 15, 17
    // -----------------------------------------------------------------------

    @Test
    fun `row 9 - severe thermal suspends`() {
        val (ctx, now) = calm { copy(thermalSevere = true) }
        assertNull(escalationCeiling(ctx, now))
    }

    @Test
    fun `row 11 - a silent ringer is respected and never routed around via the alarm channel`() {
        val (ctx, now) = calm { copy(ringerSilent = true) }
        assertCeiling(Rung.R1_VIBRATE, ctx, now)
        assertFalse(mayEscalate(Rung.R2_ALARM, ctx, now), "R2 IS the alarm channel end-run; it must not fire")
    }

    @Test
    fun `row 13 - meeting mode suspends while it lasts and releases when it ends`() {
        val now = at(10, 0)
        val (during, _) = calm(now) { copy(meetingModeUntil = now + 60_000L) }
        assertTrue(Interlock.MEETING_MODE in interlocksActive(during, now))
        assertNull(escalationCeiling(during, now))

        val (after, _) = calm(now) { copy(meetingModeUntil = now - 1L) }
        assertFalse(Interlock.MEETING_MODE in interlocksActive(after, now))
    }

    @Test
    fun `row 14 - every paused mode silences everything, uncapped`() {
        PausedMode.entries.forEach { mode ->
            val (ctx, now) = calm { copy(pausedMode = mode) }
            assertTrue(Interlock.MODE_PAUSED in interlocksActive(ctx, now), "$mode")
            assertNull(escalationCeiling(ctx, now), "$mode must be total silence")
            Rung.entries.forEach { assertFalse(mayEscalate(it, ctx, now), "$it during $mode") }
        }
    }

    @Test
    fun `row 15 - stand down puts the ceiling at R0`() {
        val (ctx, now) = calm { copy(stoodDown = true) }
        assertCeiling(Rung.R0_NOTIFICATION, ctx, now)
    }

    @Test
    fun `row 17 - ghosting past 72 hours disables escalation above R0`() {
        val now = at(10, 0)
        val (ghost, _) = calm(now) { copy(lastAppOpenAt = now - 73L * 3_600_000L) }
        assertTrue(Interlock.GHOSTING in interlocksActive(ghost, now))
        assertCeiling(Rung.R0_NOTIFICATION, ghost, now)

        val (present, _) = calm(now) { copy(lastAppOpenAt = now - 71L * 3_600_000L) }
        assertFalse(Interlock.GHOSTING in interlocksActive(present, now))
    }

    // -----------------------------------------------------------------------
    // Rows 18, 19, 20 — the ladder cooldown and the two install ages
    // -----------------------------------------------------------------------

    @Test
    fun `more than once per 90 minutes - a second ladder inside 90 minutes cannot escalate`() {
        val now = at(10, 0)
        val (tooSoon, _) = calm(now) { copy(lastEscalationAt = now - 89L * 60_000L) }
        assertTrue(Interlock.COOLDOWN_90M in interlocksActive(tooSoon, now))
        assertNull(escalationCeiling(tooSoon, now))
        assertFalse(mayEscalate(Rung.R0_NOTIFICATION, tooSoon, now))

        val (ok, _) = calm(now) { copy(lastEscalationAt = now - 91L * 60_000L) }
        assertFalse(Interlock.COOLDOWN_90M in interlocksActive(ok, now))
        assertTrue(mayEscalate(Rung.R0_NOTIFICATION, ok, now))
    }

    @Test
    fun `row 20 - nothing above R1 in the first 72 hours`() {
        val now = installAt + 71L * 3_600_000L
        val ctx = DeviceContext(
            installAt = installAt,
            winddownAtMinutes = 20 * 60 + 45,
            wakeAtMinutes = 6 * 60 + 30,
            zone = zone,
            lastAppOpenAt = now,
        )
        assertTrue(Interlock.INSTALL_GRACE_72H in interlocksActive(ctx, now))
        assertCeiling(Rung.R1_VIBRATE, ctx, now)
        assertTrue(mayEscalate(Rung.R1_VIBRATE, ctx, now))
        assertFalse(mayEscalate(Rung.R2_ALARM, ctx, now), "the coach earns the ladder")
    }

    /** RESOLUTIONS §E: the lock holds back 14 days from install. Before day 14, R4 is unreachable. */
    @Test
    fun `row 19 - the lock is unreachable before day 14`() {
        (0 until 14).forEach { day ->
            val now = installAt + day * 24L * 3_600_000L + 10L * 3_600_000L
            val ctx = DeviceContext(
                installAt = installAt,
                winddownAtMinutes = 20 * 60 + 45,
                wakeAtMinutes = 6 * 60 + 30,
                zone = zone,
                lastAppOpenAt = now,
            )
            assertTrue(Interlock.INSTALL_AGE_UNDER_14D in interlocksActive(ctx, now), "day $day")
            assertFalse(mayEscalate(Rung.R4_LOCK, ctx, now), "the lock must not arm on day $day")
        }
    }

    @Test
    fun `on day 14 the hold-back releases`() {
        val now = installAt + 14L * 24 * 3_600_000L + 10L * 3_600_000L
        val ctx = DeviceContext(
            installAt = installAt,
            winddownAtMinutes = 20 * 60 + 45,
            wakeAtMinutes = 6 * 60 + 30,
            zone = zone,
            lastAppOpenAt = now,
        )
        assertFalse(Interlock.INSTALL_AGE_UNDER_14D in interlocksActive(ctx, now))
        assertTrue(mayEscalate(Rung.R4_LOCK, ctx, now))
    }

    @Test
    fun `row 18 - a lock 90 minutes ago forbids R4 but not R3`() {
        val now = at(10, 0)
        val (ctx, _) = calm(now) { copy(lastLockAt = now - 60L * 60_000L) }
        assertTrue(Interlock.LOCK_COOLDOWN_90M in interlocksActive(ctx, now))
        assertCeiling(Rung.R3_VOICE, ctx, now)
        assertTrue(mayEscalate(Rung.R3_VOICE, ctx, now))
        assertFalse(mayEscalate(Rung.R4_LOCK, ctx, now))
    }

    @Test
    fun `the lock fires at most once per 7 days across the whole app`() {
        val now = at(10, 0)
        val (inside, _) = calm(now) { copy(lastLockAt = now - 6L * 24 * 3_600_000L) }
        assertTrue(Interlock.LOCK_COOLDOWN_7D in interlocksActive(inside, now))
        assertFalse(mayEscalate(Rung.R4_LOCK, inside, now))

        val (outside, _) = calm(now) { copy(lastLockAt = now - 8L * 24 * 3_600_000L) }
        assertFalse(Interlock.LOCK_COOLDOWN_7D in interlocksActive(outside, now))
        assertTrue(mayEscalate(Rung.R4_LOCK, outside, now))
    }

    // -----------------------------------------------------------------------
    // The sign inversion
    // -----------------------------------------------------------------------

    /** SPEC §6.7: "the bandit may never escalate in response to multi-habit collapse." */
    @Test
    fun `multi-habit collapse lowers the ceiling to R0 - it never raises it`() {
        val (ctx, now) = calm { copy(collapsingHabitCount = 2) }
        assertTrue(Interlock.MULTI_HABIT_COLLAPSE in interlocksActive(ctx, now))
        assertCeiling(Rung.R0_NOTIFICATION, ctx, now)
        assertFalse(mayEscalate(Rung.R1_VIBRATE, ctx, now))
    }

    @Test
    fun `one collapsing habit is not a multi-habit collapse`() {
        val (ctx, now) = calm { copy(collapsingHabitCount = 1) }
        assertFalse(Interlock.MULTI_HABIT_COLLAPSE in interlocksActive(ctx, now))
        assertCeiling(Rung.R4_LOCK, ctx, now)
    }

    // -----------------------------------------------------------------------
    // Composition
    // -----------------------------------------------------------------------

    @Test
    fun `the quietest interlock always wins`() {
        // A cap and a silence together: silence wins.
        val (ctx, now) = calm { copy(cameraHeldByOtherApp = true, inCall = true) }
        assertNull(escalationCeiling(ctx, now))

        // Two caps: the lower wins.
        val (both, _) = calm(now) { copy(cameraHeldByOtherApp = true, lastLockAt = now - 1000L) }
        assertEquals(Rung.R1_VIBRATE, escalationCeiling(both, now))
    }

    @Test
    fun `blockingInterlocks names why, for the Sunday total and for nothing else`() {
        val (ctx, now) = calm { copy(batteryPct = 10) }
        assertEquals(setOf(Interlock.BATTERY_LOW), blockingInterlocks(Rung.R2_ALARM, ctx, now))
        assertTrue(blockingInterlocks(Rung.R1_VIBRATE, ctx, now).isEmpty(), "R1 is under the cap; nothing blocks it")
    }

    @Test
    fun `mayEscalate is total - no context and no rung throws`() {
        val rungs = Rung.entries
        val contexts = listOf(
            DeviceContext(installAt = 0, winddownAtMinutes = 0, wakeAtMinutes = 0),
            DeviceContext(installAt = Long.MAX_VALUE / 2, winddownAtMinutes = 1439, wakeAtMinutes = 1439),
            DeviceContext(installAt = 0, winddownAtMinutes = 1234, wakeAtMinutes = 12, batteryPct = 0, speedKmh = 300.0),
        )
        contexts.forEach { c -> rungs.forEach { r -> mayEscalate(r, c, 1_000_000_000L) } }
    }

    @Test
    fun `every interlock ceiling is either null or a real rung, and the enum is exhaustive`() {
        Interlock.entries.forEach { i ->
            val c = i.ceiling
            if (c != null) assertTrue(c in Rung.entries, "$i has a bogus ceiling")
        }
        // A guard against someone adding a silence-level interlock by accident later.
        assertEquals(9, Interlock.entries.count { it.ceiling == null }, "the total-silence set changed; is that deliberate?")
    }
}
