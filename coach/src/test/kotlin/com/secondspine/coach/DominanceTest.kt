package com.secondspine.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * THE MOST IMPORTANT TEST IN THIS REPO.
 *
 * The app exists because logging can be faked. Everything else — the character, the ladder, the
 * archive — is downstream of one property: THE HONEST PATH MUST BE CHEAPER THAN THE DISHONEST ONE,
 * for a user who compiles the APK himself and knows exactly how the audit works.
 *
 * That property has been broken twice by careful people:
 *   1. The original spec priced confession and being-caught identically. At ~15% audit sampling
 *      P(caught) is small, so the expected cost of faking was LOWER than the certain cost of
 *      honesty. A judge caught it.
 *   2. The fix ("confession never demotes") was applied at the demotion layer only. `shouldGraduate`
 *      independently gates on compliance, and a confessed day — being non-compliant — still failed
 *      it, while an UNCAUGHT FAKE DAY PASSED IT. The consistency check caught that one.
 *
 * If this file goes red, the product is inverted and it does not matter what else is green.
 */
class DominanceTest {

    private val now = 100L.dayMillis
    private fun habit(stage: Stage = Stage.ENFORCED, since: Long = 0L) =
        Habit(id = "water", stage = stage, tier = Tier.T2, stageSince = since)

    private fun days(vararg completed: Boolean) = completed.mapIndexed { i, c ->
        Day(habitId = "water", epochDay = (60L + i), completed = c)
    }

    @Test
    fun `a confessed day never blocks graduation - it leaves the ratio entirely`() {
        // 20 real days, all done. Plus one day he skipped and said so.
        val honest = days(*BooleanArray(20) { true }) +
            Day(habitId = "water", epochDay = 81, completed = false, confessed = true)

        // The confessed day is excluded from the denominator, so compliance is untouched.
        assertEquals(1.0, compliance(honest), "a confessed day must not enter the ratio")
        assertTrue(shouldGraduate(habit(), honest, emptyList(), now))
    }

    @Test
    fun `honesty is never worse than silence - the day he skips and says nothing`() {
        val confessed = days(*BooleanArray(20) { true }) +
            Day(habitId = "water", epochDay = 81, completed = false, confessed = true)
        val silent = days(*BooleanArray(20) { true }) +
            Day(habitId = "water", epochDay = 81, completed = false, confessed = false)

        // Confessing is strictly better than staying quiet about the same missed day.
        assertTrue(compliance(confessed) > compliance(silent))
        assertTrue(shouldGraduate(habit(), confessed, emptyList(), now))
    }

    @Test
    fun `confession never demotes, no matter how many times he confesses`() {
        // He confessed every single day for three weeks. That is 21 admissions of failure.
        val allConfessed = (60..80).map {
            Day(habitId = "water", epochDay = it.toLong(), completed = false, confessed = true)
        }
        assertEquals(
            null,
            demotionCause(habit(), allConfessed, emptyList(), now),
            "confession must never be a demotion cause, at any volume",
        )
    }

    @Test
    fun `a confession is not expressible as a demotion cause`() {
        // Structural, not behavioural: there is no enum value to write even if someone tried.
        assertFalse(
            TransitionReason.entries.any { it.name.contains("CONFESS", ignoreCase = true) },
            "TransitionReason must have no CONFESSED value — a flag can be unwritten at 1am; " +
                "a value that does not exist cannot be conjured",
        )
    }

    @Test
    fun `being caught demotes, and being caught is the only thing a proof can be called out for`() {
        val fine = days(*BooleanArray(20) { true })
        val caught = listOf(CaughtEvent("water", CaughtKind.BYTE_REPLAY, now - 1000))
        assertEquals(TransitionReason.DEMOTED_CAUGHT, demotionCause(habit(), fine, caught, now))

        // And there is exactly one kind. pHash may never accuse — it fires on truthful nights.
        assertEquals(listOf(CaughtKind.BYTE_REPLAY), CaughtKind.entries)
    }

    @Test
    fun `collapse demotes - otherwise the caught branch is near-dead and Rip is unemployable`() {
        // BYTE_REPLAY is near-unreachable on a camera-only path: it fires perhaps twice in ten
        // months. If collapse did not demote, the pipeline would be one-way and Rip would run out
        // of jurisdiction to lose. Collapse is the renewable fuel.
        val collapsed = (60..80).map {
            Day(habitId = "water", epochDay = it.toLong(), completed = false)
        }
        assertEquals(TransitionReason.DEMOTED_COLLAPSE, demotionCause(habit(), collapsed, emptyList(), now))
    }

    @Test
    fun `the repair window means confessing protects him from the collapse he confessed to`() {
        // He fell apart for three weeks — but he said so. Confession pays; it does not merely cost
        // nothing. Silence would have left fake compliant days for a BYTE_REPLAY to convert later.
        val collapsedButHonest = (60..80).map {
            Day(habitId = "water", epochDay = it.toLong(), completed = false, confessed = true)
        }
        assertEquals(null, demotionCause(habit(), collapsedButHonest, emptyList(), now))
    }

    @Test
    fun `an uncaught fake day must never outperform an honest confession`() {
        // THE INVERSION, stated as a test. The faker logs a completed day he did not do and is not
        // audited. The honest man confesses the same day. If the faker graduates and the honest man
        // does not, the product is upside down.
        val faked = days(*BooleanArray(20) { true }) +
            Day(habitId = "water", epochDay = 81, completed = true)   // the lie
        val confessedTruth = days(*BooleanArray(20) { true }) +
            Day(habitId = "water", epochDay = 81, completed = false, confessed = true)

        val fakerGraduates = shouldGraduate(habit(), faked, emptyList(), now)
        val honestGraduates = shouldGraduate(habit(), confessedTruth, emptyList(), now)

        assertTrue(honestGraduates, "the honest man must graduate")
        assertEquals(
            fakerGraduates, honestGraduates,
            "faking must never buy an outcome honesty cannot. If this fails, the app is a liar detector " +
                "that rewards liars.",
        )
    }
}
