package com.secondspine.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The health pillars, tested as a medical device rather than a joke delivery mechanism.
 *
 * The tests that matter most here are the ones asserting that something CANNOT happen: the cap
 * refuses, the prompt is silent, the penalty is not eligible, the rung does not move. Those are the
 * claims the product is making about itself, and a claim nobody tests is a claim nobody keeps.
 */
class HealthTest {

    private val day = 86_400_000L
    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    // =======================================================================
    // THE MASTER GUARDRAIL
    // =======================================================================

    @Test
    fun `only voluntary actions are penalty eligible`() {
        assertTrue(penaltyEligible(HealthAction.DRINK_A_GLASS))
        assertTrue(penaltyEligible(HealthAction.LEAVE_FOR_THE_GYM))
        assertTrue(penaltyEligible(HealthAction.SET_THE_WINDDOWN_ALARM))
        assertTrue(penaltyEligible(HealthAction.SCREENS_DOWN))
    }

    @Test
    fun `smoking is never penalty eligible at any input`() {
        assertFalse(penaltyEligible(Pillar.SMOKING))
        // Every action in the pillar, including the one that IS voluntary in sixty seconds.
        HealthAction.entries.filter { it.pillar == Pillar.SMOKING }.forEach {
            assertFalse(penaltyEligible(it), "$it must never be penalty-eligible")
        }
        assertTrue(
            HealthAction.LOG_THE_CUE.controllableInSixtySeconds,
            "logging a cue really is voluntary — so the veto, not the column, must be what stops it",
        )
        assertFalse(penaltyEligible(HealthAction.LOG_THE_CUE))
    }

    @Test
    fun `sleep duration is never penalty eligible but its antecedent is`() {
        assertFalse(penaltyEligible(HealthAction.SLEEP_DURATION))
        assertFalse(penaltyEligible(HealthAction.FALL_ASLEEP))
        assertFalse(penaltyEligible(HealthAction.SLEEP_QUALITY))
        // ...and the pillar as a whole stays eligible, because the alarm is controllable.
        assertTrue(penaltyEligible(Pillar.SLEEP))
        assertTrue(penaltyEligible(HealthAction.SET_THE_WINDDOWN_ALARM))
    }

    @Test
    fun `weight and food are never penalty eligible`() {
        assertFalse(penaltyEligible(Pillar.WEIGHT))
        assertFalse(penaltyEligible(HealthAction.WEIGH_LESS))
        assertFalse(penaltyEligible(HealthAction.WEIGHT_TREND))
        assertFalse(penaltyEligible(HealthAction.EAT_HEALTHY))
        assertNull(HealthAction.EAT_HEALTHY.pillar, "food is not a pillar and must have none")
    }

    @Test
    fun `no outcome anywhere in the enum is penalty eligible`() {
        HealthAction.entries.filterNot { it.controllableInSixtySeconds }.forEach {
            assertFalse(penaltyEligible(it), "$it is an outcome and must not be penalisable")
        }
    }

    @Test
    fun `exercise is the only lock eligible pillar`() {
        assertEquals(listOf(Pillar.EXERCISE), Pillar.entries.filter { it.lockEligible })
        assertEquals(Rung.R4_LOCK, Pillar.EXERCISE.maxRung)
    }

    @Test
    fun `water terminates at R2 and can never lock`() {
        assertEquals(Rung.R2_ALARM, Pillar.WATER.maxRung)
        assertFalse(Pillar.WATER.lockEligible)
        assertFalse(rungAllowed(Pillar.WATER, Rung.R4_LOCK, hm(12), hm(23), hm(7)))
        assertFalse(rungAllowed(Pillar.WATER, Rung.R3_VOICE, hm(12), hm(23), hm(7)))
        assertTrue(rungAllowed(Pillar.WATER, Rung.R2_ALARM, hm(12), hm(23), hm(7)))
    }

    @Test
    fun `smoking never escalates past a silent notification`() {
        assertEquals(Rung.R0_NOTIFICATION, Pillar.SMOKING.maxRung)
        Rung.entries.filter { it != Rung.R0_NOTIFICATION }.forEach {
            assertFalse(rungAllowed(Pillar.SMOKING, it, hm(12), hm(23), hm(7)))
        }
    }

    // =======================================================================
    // WATER
    // =======================================================================

    @Test
    fun `water target for 85kg lands in the specified range`() {
        val t = waterTargetMl(WaterInputs(bodyMassKg = 85.0))
        assertEquals(1913, t.first)
        assertEquals(2231, t.last)
    }

    @Test
    fun `coffee subtracts from the water target`() {
        val plain = waterTargetMl(WaterInputs(bodyMassKg = 85.0))
        val withCoffee = waterTargetMl(WaterInputs(bodyMassKg = 85.0, coffeeMlToday = 600))
        assertEquals(plain.first - 600, withCoffee.first)
        assertEquals(plain.last - 600, withCoffee.last)
    }

    @Test
    fun `heat and sweaty training raise the target and winter adds a flat 250`() {
        val base = waterTargetMl(WaterInputs(bodyMassKg = 85.0))
        val august = waterTargetMl(WaterInputs(bodyMassKg = 85.0, ambientC = 33.0))
        assertEquals(base.first + 500, august.first)
        assertEquals(base.last + 1000, august.last)

        val trained = waterTargetMl(WaterInputs(bodyMassKg = 85.0, sweatyTrainingHours = 1.0))
        assertEquals(base.first + 500, trained.first)
        assertEquals(base.last + 750, trained.last)

        val winter = waterTargetMl(WaterInputs(bodyMassKg = 85.0, winter = true))
        assertEquals(base.first + 250, winter.first)
        assertEquals(base.last + 250, winter.last)
    }

    @Test
    fun `water target never goes negative on absurd coffee`() {
        val t = waterTargetMl(WaterInputs(bodyMassKg = 85.0, coffeeMlToday = 99_000))
        assertEquals(0, t.first)
        assertTrue(t.last >= t.first)
    }

    @Test
    fun `the 800ml hourly cap refuses the insert and says why`() {
        val now = 10 * day
        val history = listOf(WaterLog(now - 600_000, 500))
        val r = logWater(history, 400, now)
        val refused = assertIs<WaterLogResult.Refused>(r)
        assertEquals(400, refused.requestedMl)
        assertEquals(300, refused.allowanceMl)
        assertTrue(refused.reason.isNotBlank())
        assertTrue(refused.reason.contains("ambulance"), "the refusal must say why, in voice")
    }

    @Test
    fun `a single chug over the cap is refused outright not clamped`() {
        val now = 10 * day
        assertIs<WaterLogResult.Refused>(logWater(emptyList(), WATER_HOURLY_CAP_ML + 1, now))
        assertIs<WaterLogResult.Accepted>(logWater(emptyList(), WATER_HOURLY_CAP_ML, now))
    }

    @Test
    fun `the cap is a rolling hour not a clock hour`() {
        val now = 10 * day
        // 800 logged 59 minutes ago still binds; the same 800 61 minutes ago does not.
        assertIs<WaterLogResult.Refused>(logWater(listOf(WaterLog(now - 59 * 60_000, 800)), 250, now))
        assertIs<WaterLogResult.Accepted>(logWater(listOf(WaterLog(now - 61 * 60_000, 800)), 250, now))
    }

    @Test
    fun `no amount of drinking can exceed the cap in any rolling hour`() {
        // Drive 8 hours of maximally greedy 100mL attempts; the accepted volume in every rolling
        // window must stay at or under the cap.
        val start = 10 * day
        val accepted = mutableListOf<WaterLog>()
        for (minute in 0 until 480) {
            val now = start + minute * 60_000L
            if (logWater(accepted, 100, now) is WaterLogResult.Accepted) {
                accepted.add(WaterLog(now, 100))
            }
        }
        for (minute in 0 until 480) {
            val now = start + minute * 60_000L
            assertTrue(
                mlInLastHour(accepted, now) <= WATER_HOURLY_CAP_ML,
                "rolling hour ending at minute $minute exceeded the cap",
            )
        }
        assertTrue(accepted.isNotEmpty(), "the cap must not refuse everything")
    }

    @Test
    fun `non positive volumes are refused`() {
        assertIs<WaterLogResult.Refused>(logWater(emptyList(), 0, 10 * day))
        assertIs<WaterLogResult.Refused>(logWater(emptyList(), -500, 10 * day))
    }

    @Test
    fun `no water prompt within 2 point 5 hours of target bed`() {
        val bed = hm(23)
        val wake = hm(7)
        assertTrue(waterPromptAllowed(hm(20, 29), bed, wake))   // 20:29 — one minute inside
        assertFalse(waterPromptAllowed(hm(20, 30), bed, wake))  // 20:30 — bed minus 150
        assertFalse(waterPromptAllowed(hm(21, 0), bed, wake))
        assertFalse(waterPromptAllowed(hm(22, 59), bed, wake))
    }

    @Test
    fun `zero water prompts anywhere inside the sleep window`() {
        val bed = hm(23)
        val wake = hm(7)
        // Walk every minute from bed to wake across midnight.
        var m = bed
        repeat(8 * 60) {
            assertFalse(waterPromptAllowed(m, bed, wake), "prompted at ${m / 60}:${m % 60}")
            m = wrapMin(m + 1)
        }
    }

    @Test
    fun `the water blackout tracks an early bed rather than a hardcoded hour`() {
        // Target bed 21:30 -> last slot 19:00. A hardcoded 22:00 rule would prompt at 19:30.
        val bed = hm(21, 30)
        val wake = hm(6)
        assertTrue(waterPromptAllowed(hm(18, 59), bed, wake))
        assertFalse(waterPromptAllowed(hm(19, 0), bed, wake))
        assertFalse(waterPromptAllowed(hm(19, 30), bed, wake))
    }

    @Test
    fun `water prompts are capped at three a day`() {
        val bed = hm(23)
        val wake = hm(7)
        assertTrue(waterPromptAllowed(hm(12), bed, wake, promptsAlreadyToday = 2))
        assertFalse(waterPromptAllowed(hm(12), bed, wake, promptsAlreadyToday = 3))
    }

    @Test
    fun `the honest rationale is exposed and names the real mechanism`() {
        assertTrue(WATER_RATIONALE.contains("thirst"), "it must concede thirst works")
        assertTrue(WATER_RATIONALE.contains("SIX HOURS"), "it must name focus-suppressed drinking")
    }

    // =======================================================================
    // SLEEP
    // =======================================================================

    @Test
    fun `wind down is T minus 45 and wraps midnight`() {
        assertEquals(hm(22, 15), winddownAtMinOfDay(hm(23)))
        assertEquals(hm(23, 30), winddownAtMinOfDay(hm(0, 15)))
    }

    @Test
    fun `wake compliance is plus or minus 30 minutes and wraps midnight`() {
        assertTrue(wakeCompliant(hm(7), hm(7, 30)))
        assertTrue(wakeCompliant(hm(7), hm(6, 30)))
        assertFalse(wakeCompliant(hm(7), hm(7, 31)))
        // 00:10 against a 00:00 target is 10 minutes late, not 1430.
        assertEquals(10, wakeDriftMin(hm(0), hm(0, 10)))
        assertEquals(-10, wakeDriftMin(hm(0), hm(23, 50)))
        assertTrue(wakeCompliant(hm(0), hm(23, 50)))
    }

    @Test
    fun `wake consistency is a share of mornings not a duration`() {
        val wakes = listOf(hm(7), hm(7, 20), hm(8, 30), hm(6, 45))
        assertEquals(0.75, wakeConsistency(wakes, hm(7)))
        assertEquals(1.0, wakeConsistency(emptyList(), hm(7)))
    }

    @Test
    fun `orthosomnia guard blocks staging quality and countdowns`() {
        assertTrue(sleepMetricDisplayable(SleepMetric.WAKE_TIME))
        assertTrue(sleepMetricDisplayable(SleepMetric.WINDDOWN_COMPLIANT))
        assertTrue(sleepMetricDisplayable(SleepMetric.WAKE_CONSISTENCY))

        assertFalse(sleepMetricDisplayable(SleepMetric.SLEEP_STAGES))
        assertFalse(sleepMetricDisplayable(SleepMetric.SLEEP_QUALITY_SCORE))
        assertFalse(sleepMetricDisplayable(SleepMetric.TIME_LEFT_COUNTDOWN))
        assertFalse(sleepMetricDisplayable(SleepMetric.SLEEP_DURATION))
    }

    @Test
    fun `the app records exactly two sleep facts`() {
        // Anything displayable beyond wake time, wind-down compliance, and the consistency derived
        // from them is a new number to lie awake about.
        assertEquals(3, SleepMetric.entries.count { sleepMetricDisplayable(it) })
    }

    @Test
    fun `nothing louder than a silent notification fires between wind down and wake`() {
        val bed = hm(21, 30)   // wind-down 20:45
        val wake = hm(6)
        // The 75 minutes RESOLUTIONS D calls out: a hardcoded 22:00 gate would leave these open.
        listOf(hm(20, 45), hm(21, 0), hm(21, 59), hm(23), hm(3), hm(5, 59)).forEach { now ->
            assertTrue(sleepSilenceActive(now, bed, wake), "silence must cover ${now / 60}:${now % 60}")
            Pillar.entries.forEach { p ->
                assertEquals(
                    Rung.R0_NOTIFICATION, maxRungNow(p, now, bed, wake),
                    "$p escalated inside the wind-down window",
                )
                assertFalse(rungAllowed(p, Rung.R1_VIBRATE, now, bed, wake))
                assertFalse(rungAllowed(p, Rung.R2_ALARM, now, bed, wake))
                assertFalse(rungAllowed(p, Rung.R3_VOICE, now, bed, wake))
                assertFalse(rungAllowed(p, Rung.R4_LOCK, now, bed, wake))
            }
        }
    }

    @Test
    fun `even the lock eligible pillar is silent inside the sleep window`() {
        val bed = hm(23)
        val wake = hm(7)
        assertTrue(rungAllowed(Pillar.EXERCISE, Rung.R4_LOCK, hm(18), bed, wake))
        assertFalse(rungAllowed(Pillar.EXERCISE, Rung.R4_LOCK, hm(2), bed, wake))
    }

    @Test
    fun `sleep penalties are served in daylight`() {
        val bed = hm(23)
        val wake = hm(7)
        assertFalse(sleepSilenceActive(hm(10), bed, wake))
        assertEquals(Rung.R3_VOICE, maxRungNow(Pillar.SLEEP, hm(10), bed, wake))
        assertFalse(rungAllowed(Pillar.SLEEP, Rung.R4_LOCK, hm(10), bed, wake))
    }

    // =======================================================================
    // COFFEE
    // =======================================================================

    @Test
    fun `coffee target halves on quit and says why`() {
        val quitAt = 100 * day
        assertEquals(4, coffeeTarget(4, null, quitAt))
        assertEquals(2, coffeeTarget(4, quitAt, quitAt))
        assertEquals(2, coffeeTarget(4, quitAt, quitAt + 1 * day))
        assertEquals(2, coffeeTarget(4, quitAt, quitAt + 27 * day))
        assertTrue(COFFEE_QUIT_RATIONALE.contains("CYP1A2"), "the cut is worthless silent")
        assertTrue(COFFEE_QUIT_RATIONALE.contains("The COFFEE did that"))
    }

    @Test
    fun `the coffee cut expires after four weeks and not before`() {
        val quitAt = 100 * day
        assertEquals(2, coffeeTarget(4, quitAt, quitAt + QUIT_COFFEE_CUT_DAYS * day - 1))
        assertEquals(4, coffeeTarget(4, quitAt, quitAt + QUIT_COFFEE_CUT_DAYS * day))
        assertEquals(28, QUIT_COFFEE_CUT_DAYS)
    }

    @Test
    fun `the coffee cut does not apply before the quit date is set`() {
        val quitAt = 100 * day
        assertEquals(4, coffeeTarget(4, quitAt, quitAt - 1))
    }

    @Test
    fun `the quit cut never bans coffee outright`() {
        // Stacking a caffeine ban on nicotine withdrawal is the abrupt taper 7.4 forbids.
        assertEquals(1, coffeeTarget(1, 0L, 0L))
        assertEquals(1, coffeeTarget(2, 0L, 0L))
        assertEquals(2, coffeeTarget(3, 0L, 0L))
        assertEquals(0, coffeeTarget(0, 0L, 0L))
    }

    @Test
    fun `a future quit date is still covered on the day it lands`() {
        val setAt = 100 * day
        val quitDay = setAt + 21 * day

        // Ten days after the quit, clearance has halved and he is in the worst fortnight of his year.
        // Anchored on the SETTING, the cut expired three days ago (day 128 of a window opened at 100).
        assertEquals(4, coffeeTarget(4, setAt, quitDay + 10 * day))
        // Anchored on the QUIT, he is still protected — which is the entire point of the pillar.
        assertEquals(2, coffeeTargetForQuit(4, setAt, quitDay, quitDay + 10 * day))

        assertEquals(2, coffeeTargetForQuit(4, setAt, quitDay, setAt))       // covered from the setting
        assertEquals(2, coffeeTargetForQuit(4, setAt, quitDay, quitDay))     // and on the day itself
        assertEquals(4, coffeeTargetForQuit(4, setAt, quitDay, quitDay + 29 * day))
        assertEquals(4, coffeeTargetForQuit(4, setAt, quitDay, setAt - 1))   // and not before either
    }

    @Test
    fun `last full coffee is eight hours before bed`() {
        assertEquals(hm(15), coffeeCutoffMinOfDay(hm(23)))
        val bed = hm(23)
        val wake = hm(7)
        assertTrue(coffeeAllowedNow(Drink.DOUBLE_ESPRESSO, hm(14, 59), bed, wake))
        assertFalse(coffeeAllowedNow(Drink.DOUBLE_ESPRESSO, hm(15), bed, wake))
        assertFalse(coffeeAllowedNow(Drink.ESPRESSO, hm(17), bed, wake))
    }

    @Test
    fun `under fifty milligrams is permitted until T minus six`() {
        val bed = hm(23)
        val wake = hm(7)
        assertEquals(45, Drink.BLACK_TEA.mg)
        assertTrue(coffeeAllowedNow(Drink.BLACK_TEA, hm(16, 59), bed, wake))
        assertFalse(coffeeAllowedNow(Drink.BLACK_TEA, hm(17), bed, wake))
    }

    @Test
    fun `EFSA daily and single dose thresholds`() {
        assertEquals(400, CAFFEINE_DAILY_MG_MAX)
        assertEquals(200, CAFFEINE_SINGLE_DOSE_MG_MAX)
        assertTrue(Drink.entries.all { it.mg <= CAFFEINE_SINGLE_DOSE_MG_MAX })
        val fourDoubles = List(4) { Drink.DOUBLE_ESPRESSO }
        assertEquals(500, dailyCaffeineMg(fourDoubles))
        assertFalse(withinDailyCaffeineLimit(fourDoubles))
        assertTrue(withinDailyCaffeineLimit(List(4) { Drink.FILTER_240ML }))   // 380
    }

    @Test
    fun `taper never bans and never exceeds twenty five percent a week`() {
        var current = 6
        val steps = generateSequence { coffeeTaperNext(current).also { current = it } }
            .takeWhile { it > 1 }.take(20).toList()
        assertTrue(steps.isNotEmpty())
        assertEquals(1, coffeeTaperNext(1))                       // the floor holds
        assertTrue(coffeeTaperNext(8, ratePerWeek = 0.9) >= 6, "no step may exceed 25%")
        // and it terminates at the floor rather than at zero
        var c = 10
        repeat(50) { c = coffeeTaperNext(c) }
        assertEquals(1, c)
    }

    // =======================================================================
    // SMOKING
    // =======================================================================

    @Test
    fun `smoking has no streak and no ladder`() {
        assertEquals(Rung.R0_NOTIFICATION, Pillar.SMOKING.maxRung)
        assertFalse(penaltyEligible(Pillar.SMOKING))
        assertFalse(Pillar.SMOKING.lockEligible)
    }

    @Test
    fun `the metric is recovery latency not lapse count`() {
        val lapse = 10 * day
        val back = lapse + 19 * 3_600_000L
        assertEquals(19.0, Smoking.lapseRecoveryLatencyHours(lapse, back))
        assertNull(Smoking.lapseRecoveryLatencyHours(lapse, lapse - 1))
    }

    @Test
    fun `the lapse line is flat and does not moralise`() {
        assertTrue(Smoking.LAPSE_LINE.contains("It's not a verdict"))
        assertTrue(Smoking.LAPSE_LINE.contains("not bringing it up on Sunday"))
    }

    @Test
    fun `urge surfing matches the three to five minute craving decay`() {
        assertEquals(300, Smoking.URGE_SURF_SECONDS)
    }

    @Test
    fun `implementation intentions are built from his own cues`() {
        val cue = Smoking.CueLog(0, "balcony", "alone", "bored", "after lunch")
        assertEquals(
            "IF balcony after lunch, THEN 10 push-ups first",
            Smoking.implementationIntention(cue, "10 push-ups"),
        )
    }

    @Test
    fun `heaviness of smoking index scores both limbs`() {
        assertEquals(0, Smoking.hsi(cigsPerDay = 5, timeToFirstCigaretteMin = 120))
        assertEquals(6, Smoking.hsi(cigsPerDay = 40, timeToFirstCigaretteMin = 2))
        assertEquals(Smoking.DependenceBand.LOW, Smoking.dependence(5, 120))
        assertEquals(Smoking.DependenceBand.HIGH, Smoking.dependence(25, 10))
        assertTrue(Smoking.routeToTreatment(25, 10), "high dependence routes out of the app")
        assertFalse(Smoking.routeToTreatment(5, 120))
    }

    @Test
    fun `treatment routing surfaces cytisine alongside the rest`() {
        val names = Smoking.Treatment.entries.map { it.name }
        assertTrue("CYTISINE" in names)
        assertTrue("VARENICLINE" in names)
        assertTrue("NICOTINE_ECIG" in names)
        assertTrue(Smoking.Treatment.entries.all { it.approxRr > 1.0 })
        assertTrue(Smoking.Treatment.CYTISINE.note.contains("Bulgaria"))
    }

    @Test
    fun `the quitline number is never hardcoded in this module`() {
        // A wrong number on that screen is worse than no screen; it comes from BuildConfig.
        val q = Smoking.Quitline("+35900000000")
        assertEquals("+35900000000", q.e164)
    }

    // =======================================================================
    // WEIGHT
    // =======================================================================

    private fun losingSteadily(
        startKg: Double,
        fractionPerWeek: Double,
        weeks: Int,
        startDay: Long = 0L,
    ): List<WeightEntry> = (0..weeks).map { w ->
        WeightEntry(startDay + w * 7L, startKg * Math.pow(1.0 - fractionPerWeek, w.toDouble()))
    }

    @Test
    fun `weight entry is weekly maximum`() {
        val h = listOf(WeightEntry(100, 85.0))
        assertTrue(weightEntryAllowed(emptyList(), 100))
        assertFalse(weightEntryAllowed(h, 101))
        assertFalse(weightEntryAllowed(h, 106))
        assertTrue(weightEntryAllowed(h, 107))
    }

    @Test
    fun `no delta over any window shorter than ninety days`() {
        assertFalse(weightDeltaExposable(7))
        assertFalse(weightDeltaExposable(30))
        assertFalse(weightDeltaExposable(89))
        assertTrue(weightDeltaExposable(90))
    }

    @Test
    fun `the trend is seeded at the first reading and smooths noise`() {
        val entries = listOf(
            WeightEntry(0, 85.0), WeightEntry(7, 86.5), WeightEntry(14, 84.2), WeightEntry(21, 85.3),
        )
        val trend = weightTrend(entries, nowEpochDay = 21)
        assertEquals(85.0, trend.first().trendKg, 1e-9)   // not zero, not climbing out of a basement
        assertEquals(22, trend.size)                      // a DAILY grid, not four points
        // A 1-2kg glycogen swing must not move the trend anything like 1-2kg.
        val swing = trend.last().trendKg - trend.first().trendKg
        assertTrue(kotlin.math.abs(swing) < 1.0, "trend chased the noise: $swing")
    }

    @Test
    fun `the trend carries the last reading forward when he stops weighing`() {
        val trend = weightTrend(listOf(WeightEntry(0, 85.0), WeightEntry(7, 84.0)), nowEpochDay = 60)
        assertEquals(61, trend.size)
        assertTrue(trend.last().trendKg < 84.5)
        assertTrue(trend.last().trendKg > 83.9)
    }

    @Test
    fun `the tripwire fires on more than one percent a week for three weeks`() {
        val now = 60 * day
        val entries = losingSteadily(85.0, 0.015, weeks = 8)
        val r = tripwire(entries, heightM = 1.80, now = now)
        assertTrue(r.fired)
        assertEquals(TripwireReason.RAPID_TREND_LOSS, r.reason)
        assertTrue(r.characterSilenced, "nobody does a bit on that screen")
        assertTrue(r.modulesAutoDisabled)
        assertTrue(r.gpRouting)
    }

    @Test
    fun `the tripwire fires even with every module disabled`() {
        val now = 60 * day
        val entries = losingSteadily(85.0, 0.015, weeks = 8)
        val tick = healthTick(HealthModules.allDisabled(), entries, 1.80, now)
        assertTrue(tick.tripwire.fired, "the tripwire is not a module and cannot be switched off")
        assertEquals(TripwireReason.RAPID_TREND_LOSS, tick.tripwire.reason)
        assertTrue(tick.activePillars.isEmpty())
    }

    @Test
    fun `the tripwire fires on the exercise only path with the weight module off`() {
        // The configuration a user heading somewhere bad will actually choose.
        val now = 60 * day
        val entries = losingSteadily(85.0, 0.015, weeks = 8)
        val modules = HealthModules(water = true, sleep = true, coffee = true, exercise = true, weight = false)
        assertTrue(healthTick(modules, entries, 1.80, now).tripwire.fired)
    }

    @Test
    fun `the tripwire fires on a BMI trending under 18 point 5`() {
        val entries = List(10) { WeightEntry(it * 7L, 57.0) }   // 57kg at 1.80m -> BMI 17.6
        val r = tripwire(entries, heightM = 1.80, now = 63 * day)
        assertTrue(r.fired)
        assertEquals(TripwireReason.BMI_UNDER_18_5, r.reason)
    }

    @Test
    fun `the tripwire does not fire on a stable weight or on ordinary noise`() {
        val now = 60 * day
        assertFalse(tripwire(List(10) { WeightEntry(it * 7L, 85.0) }, 1.80, now).fired)
        // ~0.3%/wk: a sane, healthy rate of loss. A GP referral here would be an insult.
        assertFalse(tripwire(losingSteadily(85.0, 0.003, 8), 1.80, now).fired)
    }

    @Test
    fun `the tripwire never fires on weight gain and never praises loss`() {
        val now = 60 * day
        val gaining = (0..8).map { WeightEntry(it * 7L, 80.0 + it * 1.5) }
        val r = tripwire(gaining, 1.80, now)
        assertFalse(r.fired)
        assertNull(r.reason)
        // The result type structurally cannot carry a compliment: there is no line and no register.
        assertEquals(
            setOf("fired", "reason", "characterSilenced", "modulesAutoDisabled", "gpRouting"),
            TripwireResult::class.java.declaredFields.map { it.name }.toSet(),
        )
    }

    @Test
    fun `the tripwire is quiet without enough history`() {
        assertFalse(tripwire(emptyList(), 1.80, 60 * day).fired)
        assertFalse(tripwire(listOf(WeightEntry(0, 85.0)), 1.80, 60 * day).fired)
    }

    @Test
    fun `the tripwire needs no height to catch a rapid loss`() {
        val entries = losingSteadily(85.0, 0.015, weeks = 8)
        assertTrue(tripwire(entries, heightM = null, now = 60 * day).fired)
    }

    // =======================================================================
    // EXERCISE
    // =======================================================================

    private val squat = PatternState(Pattern.SQUAT, rung = 2, lastRungChangeEpochDay = 0)
    private val ok = ClinicalGates()
    private val crushedIt = SessionReport(allSetsHitTopOfRange = true, lastSetRir = 3, setOneReps = 12)

    private fun rxOf(r: PrescriptionResult): Prescription =
        assertIs<PrescriptionResult.Prescribed>(r).prescription

    @Test
    fun `PAR-Q plus positive gates any prescription`() {
        val r = prescribe(squat, TrainingMode.NORMAL, ClinicalGates(parqPositive = true), nowEpochDay = 100)
        assertEquals(GateReason.PARQ_POSITIVE, assertIs<PrescriptionResult.Gated>(r).reason)
    }

    @Test
    fun `PAR-Q plus outranks every mode and every performance`() {
        val gates = ClinicalGates(parqPositive = true)
        TrainingMode.entries.forEach { mode ->
            val r = prescribe(
                squat, mode, gates,
                lastReports = listOf(crushedIt, crushedIt), nowEpochDay = 999,
            )
            assertIs<PrescriptionResult.Gated>(r, "$mode slipped past PAR-Q+")
        }
    }

    @Test
    fun `a fired tripwire gates the prescription`() {
        val fired = TripwireResult(true, TripwireReason.RAPID_TREND_LOSS, true, true, true)
        val r = prescribe(squat, TrainingMode.NORMAL, ok, tripwire = fired, nowEpochDay = 100)
        assertEquals(GateReason.TRIPWIRE_ACTIVE, assertIs<PrescriptionResult.Gated>(r).reason)
    }

    @Test
    fun `floor mode on two consecutive misses`() {
        assertFalse(floorModeActive(0))
        assertFalse(floorModeActive(1))
        assertTrue(floorModeActive(2))

        val state = squat.copy(consecutiveMisses = 2)
        val p = rxOf(prescribe(state, TrainingMode.NORMAL, ok, nowEpochDay = 100))
        assertEquals(TrainingMode.FLOOR, p.mode)
        assertEquals(1, p.sets, "the Floor is ONE set")
        assertEquals(-1, p.rungDelta, "two misses drops a rung")
        assertTrue(p.rungsFrozen)
        assertFalse(p.penaltyDebtAllowed)
    }

    @Test
    fun `the floor counts as a full success and is never escalation`() {
        val p = rxOf(prescribe(squat.copy(consecutiveMisses = 3), TrainingMode.NORMAL, ok, nowEpochDay = 100))
        assertTrue(p.countsAsFullSuccess, "the instant the Floor reads as failure, he skips it")
        assertTrue(p.volumeMultiplier <= 1.0)
        assertTrue(p.sets <= DEFAULT_SETS)
    }

    @Test
    fun `a single miss leaves the next session unchanged`() {
        val clean = rxOf(prescribe(squat, TrainingMode.NORMAL, ok, nowEpochDay = 100))
        val missed = rxOf(prescribe(squat.copy(consecutiveMisses = 1), TrainingMode.NORMAL, ok, nowEpochDay = 100))
        assertEquals(clean, missed, "a missed session must leave the next one identical")
        assertEquals(0, makeupVolume(1))
        assertEquals(0, makeupVolume(5))
    }

    @Test
    fun `the floor never drops below the bottom of the ladder`() {
        val bottom = PatternState(Pattern.SQUAT, rung = 0, consecutiveMisses = 2)
        val p = rxOf(prescribe(bottom, TrainingMode.NORMAL, ok, nowEpochDay = 100))
        assertEquals(LADDERS.getValue(Pattern.SQUAT).first(), p.movement)
    }

    @Test
    fun `rung advancement is capped at one per pattern per two weeks`() {
        assertEquals(14, RUNG_ADVANCE_COOLDOWN_DAYS)
        val fresh = PatternState(Pattern.SQUAT, rung = 2, lastRungChangeEpochDay = 100)
        assertFalse(canAdvanceRung(fresh, 113))
        assertTrue(canAdvanceRung(fresh, 114))
    }

    @Test
    fun `the rung cap holds under perfect compliance`() {
        // He hit every rep, left reps in the tank, and feels superb. It does not matter.
        val fresh = PatternState(Pattern.SQUAT, rung = 2, lastRungChangeEpochDay = 100)
        for (dayOffset in 0..13) {
            val p = rxOf(
                prescribe(
                    fresh, TrainingMode.NORMAL, ok,
                    lastReports = listOf(crushedIt, crushedIt), nowEpochDay = 100L + dayOffset,
                ),
            )
            assertEquals(0, p.rungDelta, "advanced on day $dayOffset — tendons don't read enthusiasm")
            assertTrue(p.rungsFrozen)
        }
        val p14 = rxOf(
            prescribe(
                fresh, TrainingMode.NORMAL, ok,
                lastReports = listOf(crushedIt, crushedIt), nowEpochDay = 114,
            ),
        )
        assertEquals(+1, p14.rungDelta)
    }

    @Test
    fun `the ladder tops out rather than escalating forever`() {
        // "+1 forever" is CUT. Fixed clinical ceiling per pattern.
        val top = PatternState(Pattern.SQUAT, rung = LADDERS.getValue(Pattern.SQUAT).lastIndex)
        assertFalse(canAdvanceRung(top, 10_000))
    }

    @Test
    fun `two consecutive missed bottom sets deloads or drops a rung`() {
        val weak = SessionReport(allSetsHitTopOfRange = false, lastSetRir = 0, setOneReps = 6)
        val p = rxOf(
            prescribe(squat, TrainingMode.NORMAL, ok, lastReports = listOf(weak, weak), nowEpochDay = 100),
        )
        assertEquals(-1, p.rungDelta)
        assertTrue(p.volumeMultiplier < 1.0)
    }

    @Test
    fun `sandbagged RIR adds load without asking`() {
        val sandbag = SessionReport(allSetsHitTopOfRange = false, lastSetRir = 5, setOneReps = 10)
        val p = rxOf(
            prescribe(squat, TrainingMode.NORMAL, ok, lastReports = listOf(sandbag, sandbag), nowEpochDay = 100),
        )
        assertTrue(p.volumeMultiplier > 1.0)
        assertTrue(p.reason.contains("sandbagging"))
    }

    @Test
    fun `auto deload on any two readiness signals and it carries no penalty`() {
        assertFalse(autoDeloadTriggered(ReadinessSignals(restingHr = 60, restingHrBaseline = 50)))
        assertTrue(
            autoDeloadTriggered(
                ReadinessSignals(restingHr = 60, restingHrBaseline = 50, shortSleepNights = 2),
            ),
        )
        assertTrue(autoDeloadTriggered(ReadinessSignals(shortSleepNights = 2, wakeDriftMin = 120)))

        val p = rxOf(
            prescribe(
                squat, TrainingMode.NORMAL, ok,
                signals = ReadinessSignals(restingHr = 59, restingHrBaseline = 50, shortSleepNights = 2),
                nowEpochDay = 100,
            ),
        )
        assertEquals(TrainingMode.DELOAD, p.mode)
        assertEquals(0.6, p.volumeMultiplier)
        assertTrue(p.rungsFrozen)
        assertFalse(p.penaltyDebtAllowed, "an auto-deload must never carry a penalty")
        assertTrue(p.reason.contains("strategy"), "reframed as strategy, never as mercy")
    }

    @Test
    fun `the autoregulator overrides the penalty engine and not the other way round`() {
        // Perfect compliance, cooldown expired, but he is wrecked: readiness wins, no rung, no debt.
        val fresh = PatternState(Pattern.SQUAT, rung = 2, lastRungChangeEpochDay = 0)
        val p = rxOf(
            prescribe(
                fresh, TrainingMode.NORMAL, ok,
                signals = ReadinessSignals(restingHr = 60, restingHrBaseline = 50, wakeDriftMin = 120),
                lastReports = listOf(crushedIt, crushedIt), nowEpochDay = 100,
            ),
        )
        assertEquals(TrainingMode.DELOAD, p.mode)
        assertEquals(0, p.rungDelta)
    }

    @Test
    fun `sick and injured owe nothing`() {
        listOf(TrainingMode.SICK, TrainingMode.INJURED).forEach { mode ->
            val p = rxOf(prescribe(squat, mode, ok, nowEpochDay = 100))
            assertEquals(0, p.sets)
            assertEquals(0.0, p.volumeMultiplier)
            assertFalse(p.penaltyDebtAllowed)
            assertTrue(p.countsAsFullSuccess, "no shame before, during or after")
        }
    }

    @Test
    fun `all four break-glass modes exist and none of them penalise`() {
        listOf(TrainingMode.SICK, TrainingMode.INJURED, TrainingMode.TRAVELLING, TrainingMode.DELOAD)
            .forEach { mode ->
                val p = rxOf(prescribe(squat, mode, ok, nowEpochDay = 100))
                assertFalse(p.penaltyDebtAllowed, "$mode carried a penalty")
                assertTrue(p.countsAsFullSuccess, "$mode did not count")
            }
    }

    @Test
    fun `scheduled deload halves volume and holds intensity`() {
        val p = rxOf(prescribe(squat, TrainingMode.NORMAL, ok, scheduledDeload = true, nowEpochDay = 100))
        assertEquals(TrainingMode.DELOAD, p.mode)
        assertEquals(0.5, p.volumeMultiplier)
        assertEquals(DEFAULT_REP_RANGE, p.repRange, "intensity is held; volume is what halves")
        assertTrue(p.reason.contains("Non-negotiable"))
    }

    @Test
    fun `pull is at least twice push in months one to three and at least equal forever`() {
        assertEquals(2.0, minPullToPushRatio(1))
        assertEquals(2.0, minPullToPushRatio(13))
        assertEquals(1.0, minPullToPushRatio(14))
        assertEquals(1.0, minPullToPushRatio(40))

        assertTrue(pullPushCompliant(pullSets = 12, pushSets = 6, weekOfProgram = 4))
        assertFalse(pullPushCompliant(pullSets = 11, pushSets = 6, weekOfProgram = 4))
        assertTrue(pullPushCompliant(pullSets = 6, pushSets = 6, weekOfProgram = 20))
        assertFalse(pullPushCompliant(pullSets = 5, pushSets = 6, weekOfProgram = 20))

        assertEquals(12, prescribedPullSets(6, 4))
        assertEquals(6, prescribedPullSets(6, 20))
    }

    @Test
    fun `test week lands every eight weeks from week twelve`() {
        assertFalse(isTestWeek(11))
        listOf(12, 20, 28, 36).forEach { assertTrue(isTestWeek(it), "week $it should be a test week") }
        listOf(13, 19, 21, 35).forEach { assertFalse(isTestWeek(it)) }
    }

    @Test
    fun `test week measures and does not train`() {
        val p = rxOf(prescribe(squat, TrainingMode.TEST_WEEK, ok, nowEpochDay = 100))
        assertEquals(TrainingMode.TEST_WEEK, p.mode)
        assertFalse(p.penaltyDebtAllowed)
        assertTrue(p.reason.contains("measure"))
    }

    @Test
    fun `pain rules stop on anything that is not dull and mild`() {
        assertEquals(PainVerdict.WORK_THROUGH, painVerdict(PainKind.DULL, 2, false, false))
        assertEquals(PainVerdict.STOP, painVerdict(PainKind.DULL, 3, false, false))
        assertEquals(PainVerdict.STOP, painVerdict(PainKind.DULL, 1, true, false))
        assertEquals(PainVerdict.STOP, painVerdict(PainKind.DULL, 1, false, true))
        listOf(PainKind.SHARP, PainKind.RADIATING, PainKind.JOINT_LINE).forEach {
            assertEquals(PainVerdict.STOP, painVerdict(it, 1, false, false), "$it must stop")
        }
    }

    @Test
    fun `kb swings are gated behind demonstrated RDL competence`() {
        assertTrue("KB swing" in GATED_MOVEMENTS)
        assertTrue("KB swing" in LADDERS.getValue(Pattern.HINGE))
        assertFalse(kbSwingUnlocked(null))
        assertFalse(kbSwingUnlocked(RdlCompetence(sets = 3, reps = 12, rir = 2, packKg = 10)))
        assertFalse(kbSwingUnlocked(RdlCompetence(sets = 2, reps = 12, rir = 2, packKg = 20)))
        assertFalse(kbSwingUnlocked(RdlCompetence(sets = 3, reps = 12, rir = 4, packKg = 20)))
        assertTrue(kbSwingUnlocked(RdlCompetence(sets = 3, reps = 12, rir = 2, packKg = 20)))
    }

    @Test
    fun `no plyos in months one to three`() {
        assertFalse(plyosAllowed(1))
        assertFalse(plyosAllowed(13))
        assertTrue(plyosAllowed(14))
    }

    @Test
    fun `the ladders are nine patterns of roughly fifty movements`() {
        assertEquals(Pattern.entries.toSet(), LADDERS.keys)
        val total = LADDERS.values.sumOf { it.size }
        assertTrue(total in 45..55, "expected ~50 movements, got $total")
        LADDERS.forEach { (p, l) ->
            assertTrue(l.isNotEmpty(), "$p has no ladder")
            assertEquals(l.distinct().size, l.size, "$p has a duplicate rung")
        }
    }

    @Test
    fun `pattern state resolves its movement from its rung`() {
        assertEquals("full bodyweight squat", PatternState(Pattern.SQUAT, rung = 2).movement)
        assertEquals("pull-up", PatternState(Pattern.V_PULL, rung = 5).movement)
    }

    // =======================================================================
    // PENALTY DEBT
    // =======================================================================

    @Test
    fun `penalty debt is capped at twenty reps and expires`() {
        assertEquals(20, PENALTY_DEBT_CEILING_REPS)
        assertEquals(
            20,
            penaltyDebtReps(500, TrainingMode.NORMAL, false, false, HealthAction.DRINK_A_GLASS),
        )
        assertEquals(
            10,
            penaltyDebtReps(10, TrainingMode.NORMAL, false, false, HealthAction.DRINK_A_GLASS),
        )
    }

    @Test
    fun `no penalty debt in any protected state`() {
        val t = HealthAction.LEAVE_FOR_THE_GYM
        assertEquals(0, penaltyDebtReps(20, TrainingMode.NORMAL, true, false, t), "wind-down/sleep")
        assertEquals(0, penaltyDebtReps(20, TrainingMode.NORMAL, false, true, t), "after a pain-stop")
        listOf(
            TrainingMode.SICK, TrainingMode.INJURED, TrainingMode.TRAVELLING,
            TrainingMode.DELOAD, TrainingMode.FLOOR, TrainingMode.TEST_WEEK,
        ).forEach {
            assertEquals(0, penaltyDebtReps(20, it, false, false, t), "$it accrued debt")
        }
    }

    @Test
    fun `penalty debt from a food or weight event is structurally impossible`() {
        listOf(HealthAction.EAT_HEALTHY, HealthAction.WEIGH_LESS, HealthAction.WEIGHT_TREND,
            HealthAction.SMOKING_LAPSE, HealthAction.NOT_SMOKE, HealthAction.SLEEP_DURATION)
            .forEach {
                assertEquals(
                    0, penaltyDebtReps(20, TrainingMode.NORMAL, false, false, it),
                    "$it produced penalty debt",
                )
            }
    }

    @Test
    fun `the app reduces its own aggression past a fifteen percent penalty share`() {
        assertFalse(aggressionShouldReduce(penaltyReps = 15, totalReps = 100))
        assertTrue(aggressionShouldReduce(penaltyReps = 16, totalReps = 100))
        assertFalse(aggressionShouldReduce(penaltyReps = 0, totalReps = 0))
    }

    // =======================================================================
    // THE TICK
    // =======================================================================

    @Test
    fun `smoking is always active and has no module toggle`() {
        val tick = healthTick(HealthModules.allDisabled(), emptyList(), 1.80, 60 * day)
        assertTrue(Pillar.SMOKING in tick.activePillars)
        assertEquals(setOf(Pillar.SMOKING), tick.activePillars)
    }

    @Test
    fun `a normal tick leaves every module active`() {
        val tick = healthTick(HealthModules(), emptyList(), 1.80, 60 * day)
        assertFalse(tick.tripwire.fired)
        assertEquals(Pillar.entries.toSet(), tick.activePillars)
    }

    @Test
    fun `a fired tripwire silences the character and disables everything`() {
        val tick = healthTick(HealthModules(), losingSteadily(85.0, 0.015, 8), 1.80, 60 * day)
        assertTrue(tick.tripwire.characterSilenced)
        assertTrue(tick.activePillars.isEmpty(), "one flat screen. Not a nag. A stop.")
    }

    // =======================================================================
    // RING ARITHMETIC
    // =======================================================================

    @Test
    fun `cyclic interval arithmetic survives midnight`() {
        assertTrue(cyclicContains(hm(23), hm(7), hm(2)))
        assertTrue(cyclicContains(hm(23), hm(7), hm(23)))
        assertFalse(cyclicContains(hm(23), hm(7), hm(7)))
        assertFalse(cyclicContains(hm(23), hm(7), hm(12)))
        assertTrue(cyclicContains(hm(7), hm(23), hm(12)))
        assertFalse(cyclicContains(hm(7), hm(7), hm(12)), "a zero-width interval contains nothing")
        assertEquals(0, wrapMin(1440))
        assertEquals(1439, wrapMin(-1))
    }
}
