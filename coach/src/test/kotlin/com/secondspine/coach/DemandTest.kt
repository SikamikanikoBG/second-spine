package com.secondspine.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The core loop. These tests exist because the app shipped every screen, the ladder, the archive and
 * the Tape — and still asked nothing of anyone, because nothing decided WHAT to ask. That bug was
 * invisible to `assembleDebug`, invisible to CI, and visible in about four seconds on an emulator.
 */
class DemandTest {

    private val now = 100L.dayMillis
    private val todayEpoch = 100L
    private val allDay = listOf(
        DemandWindow("water", 0, 1439),
        DemandWindow("exercise", 0, 1439),
        DemandWindow("reading", 0, 1439),
    )

    private fun h(id: String, stage: Stage = Stage.ENFORCED, tier: Tier = Tier.T2, since: Long = 0, lock: Boolean = false) =
        Habit(id, stage, tier, since, lock)

    private fun open(id: String) = Day(id, todayEpoch, completed = false)

    @Test
    fun `it asks for exactly one thing`() {
        val d = resolveDemand(
            habits = listOf(h("water", tier = Tier.T2), h("exercise", tier = Tier.T5), h("reading", tier = Tier.T1)),
            today = listOf(open("water"), open("exercise"), open("reading")),
            windows = allDay, nowMinutes = 600, now = now,
        )
        assertNotNull(d)
        // Highest tier wins. The other two do not exist yet — a todo list is how these apps die.
        assertEquals("exercise", d.habitId)
    }

    @Test
    fun `a graduated habit is gone from his desk and may not ask`() {
        val d = resolveDemand(
            habits = listOf(h("water", stage = Stage.TRUSTED), h("reading", stage = Stage.RETIRED)),
            today = listOf(open("water"), open("reading")),
            windows = allDay, nowMinutes = 600, now = now,
        )
        assertNull(d, "a TRUSTED/RETIRED habit that still nags is a habit that never graduated")
    }

    @Test
    fun `a confessed day ends the obligation exactly as a proof does`() {
        // If confession left the demand standing, it would not be free — it would be a lesser
        // completion that still costs you the nag. The whole incentive rests on this.
        val d = resolveDemand(
            habits = listOf(h("water")),
            today = listOf(Day("water", todayEpoch, completed = false, confessed = true)),
            windows = allDay, nowMinutes = 600, now = now,
        )
        assertNull(d)
    }

    @Test
    fun `a suspended day never asks - the flu is not a failure`() {
        val d = resolveDemand(
            habits = listOf(h("exercise", tier = Tier.T5)),
            today = listOf(Day("exercise", todayEpoch, completed = false, suspended = true)),
            windows = allDay, nowMinutes = 600, now = now,
        )
        assertNull(d)
    }

    @Test
    fun `outside its window it stays quiet`() {
        val d = resolveDemand(
            habits = listOf(h("water")),
            today = listOf(open("water")),
            windows = listOf(DemandWindow("water", openAt = 8 * 60, closeAt = 20 * 60)),
            nowMinutes = 7 * 60, now = now,
        )
        assertNull(d)
    }

    @Test
    fun `only exercise carries the lock`() {
        val ex = resolveDemand(
            habits = listOf(h("exercise", tier = Tier.T5, lock = true)),
            today = listOf(open("exercise")), windows = allDay, nowMinutes = 600, now = now,
        )
        assertEquals(true, ex?.lockEligible)

        val water = resolveDemand(
            habits = listOf(h("water", tier = Tier.T2, lock = false)),
            today = listOf(open("water")), windows = allDay, nowMinutes = 600, now = now,
        )
        assertEquals(false, water?.lockEligible, "locking the phone over a glass of water is the fastest uninstall available")
    }

    @Test
    fun `ties break toward the habit closest to graduating`() {
        val d = resolveDemand(
            habits = listOf(h("water", tier = Tier.T2, since = 50L.dayMillis), h("reading", tier = Tier.T2, since = 10L.dayMillis)),
            today = listOf(open("water"), open("reading")),
            windows = allDay, nowMinutes = 600, now = now,
        )
        // reading has been enforced longer -> it is nearest the graduation that fires Rip.
        assertEquals("reading", d?.habitId)
    }

    @Test
    fun `the reasons are explicit so quiet and broken are distinguishable`() {
        val (d, why) = resolveDemandVerbose(
            habits = listOf(h("water", tier = Tier.T5), h("reading", tier = Tier.T1), h("guitar", stage = Stage.TRUSTED)),
            today = listOf(open("water"), Day("reading", todayEpoch, completed = true), open("guitar")),
            windows = allDay + DemandWindow("guitar", 0, 1439),
            nowMinutes = 600, now = now,
        )
        assertEquals("water", d?.habitId)
        assertEquals(Quiet.ALREADY_DONE_TODAY, why["reading"])
        assertEquals(Quiet.NOT_ENFORCED, why["guitar"])
    }

    @Test
    fun `the demand text is an instruction, never a punchline`() {
        // Rip does his bit AROUND the demand. A joke inside the ask competes with the ask.
        listOf("water", "exercise", "reading", "guitar", "sleep").forEach { id ->
            val t = demandText(id)
            assertEquals(true, t.length <= 40, "$id demand is a speech, not an ask: $t")
        }
    }
}
