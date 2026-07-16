package com.secondspine.coach

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * THE HEALTH PILLARS — SPEC §7, decided by RESOLUTIONS where the two disagree.
 *
 * Pure JVM. No Android, no I/O, no clock: every time-dependent function takes `now: Long` (epoch
 * millis) or a minute-of-day Int, so the 300-day soak test runs in milliseconds.
 *
 * The organising principle of this file is that a health app is a medical device with a joke budget,
 * and the joke budget is not the part that can hurt someone. Every number below is either sourced or
 * marked as arbitrary. Where a pillar could injure another pillar, the injured pillar wins.
 *
 * SCALE NOTE (RESOLUTIONS §B). SPEC §7.1's table is written on a stale six-rung scale where R4=TTS
 * and R5=lock. RESOLUTIONS froze the ladder at five: R0 notification, R1 vibrate, R2 alarm, R3 TTS,
 * R4 lock. The low half maps identity (water's "R2. Never a lock." == R2_ALARM); the top two shift
 * down one (sleep's "R4 (TTS)" -> R3_VOICE, exercise's "R5 — full lock" -> R4_LOCK). §7.1's R3 for
 * reading/guitar has no home on the five-rung scale and collapses DOWN to R2_ALARM, because
 * RESOLUTIONS pins only two things here — water terminates at R2, and exercise is the only
 * lock-eligible habit — and rounding an identity pillar UP into synthesised speech is not a licence
 * this file is willing to grant itself.
 */

// ===========================================================================
// 0. THE PILLARS AND THE MASTER GUARDRAIL
// ===========================================================================

/**
 * The pillars, ranked by effect size, not by how funny they are. SPEC §7.1.
 *
 * The brief made water the headline and the 17:00 espresso a footnote. That is backwards: the
 * espresso is a sleep intervention and sleep regularity beats everything else in this table.
 *
 * There is no FOOD entry, and that absence is load-bearing — see [HealthAction.EAT_HEALTHY].
 */
enum class Pillar(val rank: Int, val maxRung: Rung) {
    /** Sleep Regularity Index beats duration for all-cause mortality (Windred 2023, UK Biobank n~61k). */
    SLEEP(1, Rung.R3_VOICE),

    /** Largest absolute mortality contributor — and the one pillar with no aversives at all. */
    SMOKING(2, Rung.R0_NOTIFICATION),

    /** CRF among the strongest mortality predictors (Mandsager 2018, n~122k). The aggression lives here. */
    EXERCISE(3, Rung.R4_LOCK),

    /** Only matters because it destroys #1. */
    COFFEE(4, Rung.R2_ALARM),

    /** Reading / guitar. Identity pillars; cheapest proof in the app. */
    IDENTITY(5, Rung.R2_ALARM),

    /** Genuinely the least important thing on this list. */
    WATER(6, Rung.R2_ALARM),

    /** Not a ladder pillar at all: Rip does not know the number exists (§7.12). Present so the
     *  guardrail can refuse it by name. */
    WEIGHT(7, Rung.R0_NOTIFICATION),
    ;

    /** RESOLUTIONS §B: "Exercise is the only lock-eligible habit." */
    val lockEligible: Boolean get() = this == EXERCISE
}

/**
 * THE REFUSAL LIST.
 *
 * This enum is not a schema and nothing here is stored. It is the set of things the app might be
 * asked to penalise, annotated with the only question that matters:
 *
 *   **Is this fully under voluntary control in the next sixty seconds?**
 *
 * Everything that fails that question is an outcome, and penalising an outcome is how an app becomes
 * iatrogenic. You cannot will yourself asleep. You cannot will yourself thinner by Thursday. You can
 * put the phone face-down, and you can walk out of the door.
 */
enum class HealthAction(
    /** Null means "this is not a pillar and never will be" — see [EAT_HEALTHY]. */
    val pillar: Pillar?,
    val controllableInSixtySeconds: Boolean,
) {
    // --- voluntary: the app may ask, and may escalate ---
    DRINK_A_GLASS(Pillar.WATER, true),
    LEAVE_FOR_THE_GYM(Pillar.EXERCISE, true),
    START_THE_FLOOR_SET(Pillar.EXERCISE, true),
    SET_THE_WINDDOWN_ALARM(Pillar.SLEEP, true),
    SCREENS_DOWN(Pillar.SLEEP, true),
    GET_UP_AT_WAKE_TIME(Pillar.SLEEP, true),
    DECLINE_THE_COFFEE(Pillar.COFFEE, true),
    OPEN_THE_BOOK(Pillar.IDENTITY, true),

    /** Controllable, yes — and still never penalised, because the SMOKING veto outranks this column.
     *  That is the point of keeping it here: it proves the veto is doing work. */
    LOG_THE_CUE(Pillar.SMOKING, true),

    // --- outcomes: the app may observe, and may never penalise ---

    /** Sleep effort is the core maintaining mechanism of insomnia. Penalising this manufactures the
     *  disorder the pillar exists to prevent. */
    FALL_ASLEEP(Pillar.SLEEP, false),
    SLEEP_DURATION(Pillar.SLEEP, false),
    SLEEP_QUALITY(Pillar.SLEEP, false),

    /** You cannot photograph a not-smoke, and punishing withdrawal is the best-documented relapse
     *  pathway there is. */
    NOT_SMOKE(Pillar.SMOKING, false),
    SMOKING_LAPSE(Pillar.SMOKING, false),

    WEIGH_LESS(Pillar.WEIGHT, false),
    WEIGHT_TREND(Pillar.WEIGHT, false),

    /**
     * Food is not a pillar (§7.1) — hence the null. There is no `is_healthy`, no calorie, no macro,
     * no `food_verdict` column anywhere in the schema, and "healthy" is not a visual property:
     * grilled salmon and salmon confit in 60 g of butter are the same photograph (§7.13).
     *
     * It appears in this enum only so the guardrail can say no to it out loud.
     */
    EAT_HEALTHY(null, false),
    ;
}

/**
 * Pillars whose penalty engine is welded shut, forever, at every input.
 *
 * SMOKING: §7.5. Punishing a nicotine-dependent behaviour is punishing withdrawal is raising negative
 * affect, which is the single best-documented relapse pathway. The abstinence violation effect turns
 * one cigarette into a relapse; a penalty is the app volunteering to supply the violation.
 *
 * WEIGHT: §7.12. Rip does not know the number exists.
 */
private val PENALTY_FREE_FOREVER = setOf(Pillar.SMOKING, Pillar.WEIGHT)

/**
 * THE MASTER GUARDRAIL. The app may only penalise actions fully under voluntary control in the next
 * sixty seconds.
 *
 * At pillar granularity this asks: does this pillar have *any* voluntary antecedent worth escalating
 * on? Water yes (drink the glass). Exercise yes (leave the house). Sleep yes — but only the
 * antecedent; see the [HealthAction] overload, which is the one that can tell the wind-down alarm
 * apart from sleep duration.
 */
fun penaltyEligible(pillar: Pillar): Boolean {
    if (pillar in PENALTY_FREE_FOREVER) return false
    return HealthAction.entries.any { it.pillar == pillar && it.controllableInSixtySeconds }
}

/**
 * THE MASTER GUARDRAIL, at the granularity that actually decides things.
 *
 * Both tests must pass: the action itself must be voluntary in the next minute, *and* its pillar must
 * not be one of the welded-shut ones. Sleep is the case that proves why pillar granularity is too
 * coarse — `SET_THE_WINDDOWN_ALARM` is eligible and `SLEEP_DURATION` never is, and they live in the
 * same pillar.
 */
fun penaltyEligible(action: HealthAction): Boolean {
    val p = action.pillar ?: return false          // food has no pillar and no penalty
    return action.controllableInSixtySeconds && penaltyEligible(p)
}

// ===========================================================================
// 1. TIME — minute-of-day arithmetic on a 1440-ring
// ===========================================================================

internal const val MIN_PER_DAY = 1440

internal fun wrapMin(m: Int): Int = ((m % MIN_PER_DAY) + MIN_PER_DAY) % MIN_PER_DAY

/** Half-open [start, end) on the 1440-minute ring. A zero-width interval contains nothing. */
internal fun cyclicContains(start: Int, end: Int, x: Int): Boolean {
    val s = wrapMin(start)
    val e = wrapMin(end)
    val v = wrapMin(x)
    return if (s <= e) v >= s && v < e else v >= s || v < e
}

/** Signed shortest distance from `target` to `actual`, in [-720, 720]. Positive = late. */
internal fun cyclicDiffMin(target: Int, actual: Int): Int {
    val d = wrapMin(actual - target)
    return if (d > MIN_PER_DAY / 2) d - MIN_PER_DAY else d
}

internal fun Long.toEpochDay(): Long = Math.floorDiv(this, 86_400_000L)

// ===========================================================================
// 2. WATER — the honest version, said out loud (§7.2)
// ===========================================================================

/**
 * The rationale ships in-app, in voice, because he will google this in month two and find out the
 * 8-glasses rule is a myth (a 1945 US FNB note said ~2.5 L/day and *also* said "most of this quantity
 * is contained in prepared foods"; the second sentence got dropped — Valtin 2002 found no basis for
 * the rule). If the app is caught holding a myth, every honest thing next to it is discredited too.
 *
 * So the pillar states its real, narrow, defensible reason for existing: thirst is a fine regulator
 * in a healthy adult. The failure mode this pillar addresses is not dehydration. It is that DEEP
 * FOCUS SUPPRESSES THIRST PERCEPTION — he does not drink for six hours because a build is running.
 *
 * (SPEC §7.2 verbatim. This is §7's own rationale copy, not a draw from §3.2's fragment bank.)
 */
const val WATER_RATIONALE: String =
    "You want the science? Here's the science, brother: thirst *works*. You've got kidneys like a " +
        "Swiss bank. You don't need me. What you need me for is that you go SIX HOURS without " +
        "swallowing because a build is running and you forget you have a BODY. I'm not hydration. " +
        "I'm a *reminder that you're made of meat*."

/**
 * HARD CAP. Kidneys clear ~0.7–1.0 L/h of free water; a gamified drinking contest with no ceiling is
 * a hyponatraemia liability. 800 sits at the conservative end of that range on purpose — the cost of
 * being wrong low is a mildly annoyed engineer, and the cost of being wrong high is an ambulance.
 */
const val WATER_HOURLY_CAP_ML = 800

/** Nocturia fragments sleep, and sleep outranks water by five places. No pillar may injure another. */
const val WATER_PROMPT_BLACKOUT_MIN_BEFORE_BED = 150

/** §7.2: max 3/day at M1 — not 8. The ceremony budget cannot afford 8. */
const val WATER_MAX_PROMPTS_PER_DAY = 3

/** SPEC §7.2 verbatim — what the refusal says. It mocks the situation, never the man. */
const val WATER_CAP_REFUSAL: String =
    "You lost. Go to bed. Do NOT try to chug it — that's how a man ends up in an ambulance on a " +
        "Tuesday for absolutely nothing. I sold plastic, brother. I didn't sell kidneys."

data class WaterInputs(
    val bodyMassKg: Double,
    /** Hours of training that actually produced sweat. Not "hours at the gym". */
    val sweatyTrainingHours: Double = 0.0,
    val ambientC: Double = 20.0,
    /**
     * Coffee SUBTRACTS. Killer/Blannin/Jeukendrup 2014: 4x200 mL coffee produced no difference in any
     * hydration marker versus water. Three coffees is a third of the job, already done — and an app
     * that tells a man his coffee dehydrates him is an app that has never read anything.
     */
    val coffeeMlToday: Int = 0,
    val winter: Boolean = false,
)

/**
 * Target intake FROM DRINKS, as a range, because the underlying evidence is a range and collapsing it
 * to a single integer would be inventing precision to make a progress ring look tidy.
 *
 *   total = mass_kg * 30..35 mL      (EFSA 2.5 L/d men, TOTAL water)
 *   drink = total * 0.75             (food supplies ~25%)
 *   drink -= coffee_ml_today
 *   drink += 500..750 per sweaty training hour
 *   drink += 500..1000 if ambient > 28C   (Bulgarian August)
 *   drink += 250 in winter, and no more, and we don't pretend it's science
 *
 * 85 kg, no training, no coffee -> ~1.9–2.3 L. Never negative.
 */
fun waterTargetMl(inputs: WaterInputs): IntRange {
    require(inputs.bodyMassKg > 0) { "body mass must be positive" }
    require(inputs.sweatyTrainingHours >= 0) { "training hours cannot be negative" }
    require(inputs.coffeeMlToday >= 0) { "coffee cannot be negative" }

    val foodShare = 0.75
    var low = inputs.bodyMassKg * 30.0 * foodShare
    var high = inputs.bodyMassKg * 35.0 * foodShare

    low -= inputs.coffeeMlToday
    high -= inputs.coffeeMlToday

    low += 500.0 * inputs.sweatyTrainingHours
    high += 750.0 * inputs.sweatyTrainingHours

    if (inputs.ambientC > 28.0) {
        low += 500.0
        high += 1000.0
    }
    if (inputs.winter) {
        low += 250.0
        high += 250.0
    }

    val lo = max(0, low.roundToInt())
    val hi = max(lo, high.roundToInt())
    return lo..hi
}

data class WaterLog(val at: Long, val ml: Int)

/** The result of trying to log water. Refusal is a first-class outcome, and it explains itself. */
sealed interface WaterLogResult {
    data class Accepted(val ml: Int, val remainingHourlyAllowanceMl: Int) : WaterLogResult

    /** The insert does not happen. Not clamped, not partially banked — refused, and told why. */
    data class Refused(val requestedMl: Int, val allowanceMl: Int, val reason: String) : WaterLogResult
}

/** Rolling 60 minutes, not a clock hour — a clock hour lets him chug 800 at 10:59 and 800 at 11:01. */
internal fun mlInLastHour(history: List<WaterLog>, now: Long): Int =
    history.filter { now - it.at < 3_600_000L && it.at <= now }.sumOf { it.ml }

/**
 * §7.2: "Hard cap 800 mL/hour, enforced in `WaterRepo.log()`, which refuses the insert and says why."
 *
 * This is the one place in the app where Rip's aggression is pointed at the app's own game mechanics.
 * Every other hydration tracker on earth rewards the chug. This one refuses it.
 */
fun logWater(history: List<WaterLog>, ml: Int, now: Long): WaterLogResult {
    if (ml <= 0) {
        return WaterLogResult.Refused(ml, 0, "A non-positive volume is not a drink.")
    }
    val consumed = mlInLastHour(history, now)
    val allowance = max(0, WATER_HOURLY_CAP_ML - consumed)
    if (ml > allowance) {
        return WaterLogResult.Refused(ml, allowance, WATER_CAP_REFUSAL)
    }
    return WaterLogResult.Accepted(ml, allowance - ml)
}

/**
 * Prompts are event-anchored (after wake, after each coffee, after meals, after training), never
 * interval — and they stop dead at `bed - 150 min`, and there are ZERO inside the sleep window.
 *
 * The promptable window is the half-open ring interval [wake, bed - 150). A water pillar that wakes
 * him up to pee is a water pillar that has attacked the number-one pillar in the app to serve the
 * number-six pillar in the app. Net-negative, and unshippable.
 */
fun waterPromptAllowed(
    nowMinOfDay: Int,
    targetBedMinOfDay: Int,
    wakeMinOfDay: Int,
    promptsAlreadyToday: Int = 0,
): Boolean {
    if (promptsAlreadyToday >= WATER_MAX_PROMPTS_PER_DAY) return false
    val lastSlot = wrapMin(targetBedMinOfDay - WATER_PROMPT_BLACKOUT_MIN_BEFORE_BED)
    return cyclicContains(wakeMinOfDay, lastSlot, nowMinOfDay)
}

// ===========================================================================
// 3. SLEEP — enforce the antecedent, never the outcome (§7.3)
// ===========================================================================

/** ±30 min, seven days a week, weekends included. This is the whole ballgame. */
const val WAKE_TOLERANCE_MIN = 30

/** Wind-down at T−45: screens down. Screen-off duration is free and unfakeable from the app's side. */
const val WINDDOWN_LEAD_MIN = 45

fun winddownAtMinOfDay(targetBedMinOfDay: Int): Int = wrapMin(targetBedMinOfDay - WINDDOWN_LEAD_MIN)

/** Signed drift in minutes. Positive = woke late. */
fun wakeDriftMin(targetWakeMinOfDay: Int, actualWakeMinOfDay: Int): Int =
    cyclicDiffMin(targetWakeMinOfDay, actualWakeMinOfDay)

fun wakeCompliant(targetWakeMinOfDay: Int, actualWakeMinOfDay: Int): Boolean =
    abs(wakeDriftMin(targetWakeMinOfDay, actualWakeMinOfDay)) <= WAKE_TOLERANCE_MIN

/** Share of mornings landing within ±30 of target. Consistency, not hours. Empty history = 1.0. */
fun wakeConsistency(actualWakes: List<Int>, targetWakeMinOfDay: Int): Double {
    if (actualWakes.isEmpty()) return 1.0
    return actualWakes.count { wakeCompliant(targetWakeMinOfDay, it) }.toDouble() / actualWakes.size
}

/**
 * Things the sleep pillar could show him, and whether it may.
 *
 * The app records TWO facts: `wake_ts` and `winddown_compliant`. Everything else on this list is an
 * orthosomnia vector (Baron 2017): nightstand accelerometer staging is noise, a quality score is that
 * noise with a number welded to it, and a "5h47m left" countdown is a device for lying awake doing
 * arithmetic about how badly you are sleeping. Sleep-tracking anxiety is a named iatrogenic
 * condition, and this app is not going to cause one for a chart.
 */
enum class SleepMetric {
    WAKE_TIME,
    WINDDOWN_COMPLIANT,
    WAKE_CONSISTENCY,

    SLEEP_STAGES,
    SLEEP_QUALITY_SCORE,
    TIME_LEFT_COUNTDOWN,

    /**
     * Sensed, never shown. The autoregulator reads short-sleep nights as a deload signal (§7.9) — a
     * private input to a decision that makes today EASIER. It is not a number he is scored on.
     */
    SLEEP_DURATION,
}

private val SLEEP_METRICS_DISPLAYABLE = setOf(
    SleepMetric.WAKE_TIME,
    SleepMetric.WINDDOWN_COMPLIANT,
    SleepMetric.WAKE_CONSISTENCY,
)

/** THE ORTHOSOMNIA GUARD. If it cannot be displayed, no screen, widget or Tape line may carry it. */
fun sleepMetricDisplayable(metric: SleepMetric): Boolean = metric in SLEEP_METRICS_DISPLAYABLE

/**
 * THE SLEEP SILENCE WINDOW — keyed to HIS times, not to a hardcoded 22:00–08:00 (RESOLUTIONS §D).
 *
 * The bug this fixes: if his target bed is 21:30, wind-down starts 20:45, and a hardcoded 22:00 gate
 * leaves 75 minutes in which the ladder can fire an alarm, a TTS line and a lock INSIDE the wind-down
 * window — on the pillar ranked #1. So the window is [winddown, wake), computed from
 * (target_bed, wake), and nothing else.
 */
fun sleepSilenceActive(nowMinOfDay: Int, targetBedMinOfDay: Int, wakeMinOfDay: Int): Boolean =
    cyclicContains(winddownAtMinOfDay(targetBedMinOfDay), wakeMinOfDay, nowMinOfDay)

/**
 * The rung ceiling for a pillar RIGHT NOW.
 *
 * Two clamps. The pillar's own maximum (§7.1), and the silence window: "Zero alarms, TTS, vibration
 * or lock between wind-down and wake." R0 is a silent notification and survives; R1_VIBRATE is
 * vibration and does not. Sleep penalties are served the next day, in daylight, or not at all. An app
 * that wakes you to punish you for sleep problems is a parody of itself.
 */
fun maxRungNow(
    pillar: Pillar,
    nowMinOfDay: Int,
    targetBedMinOfDay: Int,
    wakeMinOfDay: Int,
): Rung {
    val ceiling = pillar.maxRung
    if (!sleepSilenceActive(nowMinOfDay, targetBedMinOfDay, wakeMinOfDay)) return ceiling
    return minOf(ceiling, Rung.R0_NOTIFICATION)   // enums compare by ordinal; R0 is the silent one
}

fun rungAllowed(
    pillar: Pillar,
    rung: Rung,
    nowMinOfDay: Int,
    targetBedMinOfDay: Int,
    wakeMinOfDay: Int,
): Boolean {
    if (rung == Rung.R4_LOCK && !pillar.lockEligible) return false
    return rung.ordinal <= maxRungNow(pillar, nowMinOfDay, targetBedMinOfDay, wakeMinOfDay).ordinal
}

// ===========================================================================
// 4. COFFEE — taper, never ban; and the interaction that proves a coach built this (§7.4)
// ===========================================================================

/** EFSA 2015, healthy adults. Coffee is not a vice at these doses. */
const val CAFFEINE_DAILY_MG_MAX = 400
const val CAFFEINE_SINGLE_DOSE_MG_MAX = 200

/**
 * Half-life ~5 h, dominated by CYP1A2. Drake 2013 (JCSM): 400 mg at 0, 3 AND 6 hours before bed all
 * significantly disrupted sleep — including in people reporting no subjective effect, which is why
 * "it doesn't affect me" is not admissible evidence. Gardiner 2023 puts the cutoff for a standard
 * coffee at ~8.8 h; 8 is the round number on the safe side of it.
 */
const val COFFEE_CUTOFF_HOURS_BEFORE_BED = 8

/** Under 50 mg is permitted to T−6h. A black tea at 17:00 is not the thing wrecking his sleep. */
const val COFFEE_LOW_DOSE_CUTOFF_HOURS_BEFORE_BED = 6
const val COFFEE_LOW_DOSE_MG = 50

/** He logs the DRINK, never the mg. Nobody has ever sustained logging milligrams of anything. */
enum class Drink(val mg: Int) {
    ESPRESSO(63),
    DOUBLE_ESPRESSO(125),
    FILTER_240ML(95),
    INSTANT(70),
    BLACK_TEA(45),
}

/** Juliano & Griffiths 2004: abrupt withdrawal = headache, dysphoria, onset 12–24 h, peak 20–51 h. */
const val COFFEE_TAPER_MAX_RATE_PER_WEEK = 0.25
const val COFFEE_TAPER_MIN_RATE_PER_WEEK = 0.10

/** ~50%. */
const val QUIT_COFFEE_MULTIPLIER = 0.5

/**
 * Four weeks — the top of §7.4's "2–4 weeks".
 *
 * CYP1A2 activity itself de-induces faster than this (roughly a week). The window is not sized to the
 * enzyme; it is sized to the RELAPSE RISK, which is what the cut actually exists to manage. Erring
 * long costs him two coffees a day for a fortnight. Erring short hands him palpitations in exactly
 * the month he is most likely to smoke again to make them stop.
 */
const val QUIT_COFFEE_CUT_DAYS = 28

/**
 * SPEC §7.4 verbatim. The cut is worthless silent — the entire mechanism is attribution. He must be
 * told, in advance, that the heartbeat in his teeth at 2am is the COFFEE, or he will attribute it to
 * the quit and smoke to fix it.
 */
const val COFFEE_QUIT_RATIONALE: String =
    "Sit down. This is the one thing I'm going to say straight today.\n" +
        "The cigarettes were burning your coffee off at double speed. That's not a metaphor, it's " +
        "an enzyme — CYP1A2, look it up, I'll wait, I've got nothing but time and no arms.\n" +
        "You quit yesterday. So your four coffees today are going to hit like eight. You're going " +
        "to feel your own heartbeat in your teeth at 2am and you are going to decide the QUIT did " +
        "that.\n" +
        "The quit didn't do that. The COFFEE did that. Two today. Two.\n" +
        "…I'm not being nice. I'm being *correct*. It's rarer."

/**
 * THE CYP1A2 INTERACTION — the whole reason this pillar is not a gimmick.
 *
 * Smoking induces CYP1A2 and roughly DOUBLES caffeine clearance. The day he quits, clearance halves,
 * and his usual four coffees hit like eight: jitters, palpitations, insomnia. He blames the quit, not
 * the coffee, and relapses to calm down. This is one of the most reliably missed drug interactions in
 * primary care, and it is sitting in the middle of the two pillars this app cares most about.
 *
 * So the instant a quit date is set, the target is cut ~50% for four weeks, and [COFFEE_QUIT_RATIONALE]
 * says why.
 *
 * Never floors to zero: stacking a caffeine ban on top of nicotine withdrawal is exactly the abrupt
 * taper §7.4 forbids, and it would put a headache on top of the dysphoria on the worst week of his
 * year. Never stack any other caffeine taper on a quit.
 *
 * @param base habitual drinks/day.
 * @param quitDateSetAt epoch millis at which a quit date was set; null = no quit in play.
 */
fun coffeeTarget(base: Int, quitDateSetAt: Long?, now: Long): Int {
    if (base <= 0) return 0
    if (quitDateSetAt == null) return base
    if (now < quitDateSetAt) return base
    if (now - quitDateSetAt >= QUIT_COFFEE_CUT_DAYS.days) return base
    return max(1, ceil(base * QUIT_COFFEE_MULTIPLIER).toInt())
}

/**
 * The same protection, when the quit date is in the FUTURE.
 *
 * §7.4 says "the instant `quit_date` is set", and [coffeeTarget] honours that literally. But if he
 * sets a quit date three weeks out, a 28-day window that starts on the SETTING expires within days of
 * the quit LANDING — i.e. the protection evaporates at the exact hour clearance halves, which is the
 * one hour it was built for. So the window here spans from whichever comes first through quit + 28d.
 */
fun coffeeTargetForQuit(base: Int, quitDateSetAt: Long, quitDateAt: Long, now: Long): Int {
    if (base <= 0) return 0
    val start = min(quitDateSetAt, quitDateAt)
    val end = quitDateAt + QUIT_COFFEE_CUT_DAYS.days
    if (now < start || now >= end) return base
    return max(1, ceil(base * QUIT_COFFEE_MULTIPLIER).toInt())
}

/** Last full coffee >=8 h before target bed (23:00 -> 15:00). */
fun coffeeCutoffMinOfDay(targetBedMinOfDay: Int): Int =
    wrapMin(targetBedMinOfDay - COFFEE_CUTOFF_HOURS_BEFORE_BED * 60)

fun lowDoseCutoffMinOfDay(targetBedMinOfDay: Int): Int =
    wrapMin(targetBedMinOfDay - COFFEE_LOW_DOSE_CUTOFF_HOURS_BEFORE_BED * 60)

/**
 * Is this drink, at this hour, inside the rules? Anticipatory only — never a lock, because by the
 * time we know, the coffee is already drunk (hence [Pillar.COFFEE]'s R2 ceiling).
 *
 * The promptable "day" for coffee is [wake, cutoff); under 50 mg extends to [wake, low-dose cutoff).
 */
fun coffeeAllowedNow(
    drink: Drink,
    nowMinOfDay: Int,
    targetBedMinOfDay: Int,
    wakeMinOfDay: Int,
): Boolean {
    val cutoff = if (drink.mg < COFFEE_LOW_DOSE_MG) {
        lowDoseCutoffMinOfDay(targetBedMinOfDay)
    } else {
        coffeeCutoffMinOfDay(targetBedMinOfDay)
    }
    return cyclicContains(wakeMinOfDay, cutoff, nowMinOfDay)
}

fun dailyCaffeineMg(drinks: List<Drink>): Int = drinks.sumOf { it.mg }

fun withinDailyCaffeineLimit(drinks: List<Drink>): Boolean =
    dailyCaffeineMg(drinks) <= CAFFEINE_DAILY_MG_MAX

/**
 * Taper, never ban. 10–25%/week, and never more than 25% in one step regardless of enthusiasm — the
 * withdrawal headache is what convinces him the whole app is a mistake.
 *
 * Returns the next weekly target, never below `floor` (default 1: a taper's destination is a
 * sustainable habit, not abstinence — coffee has decent evidence of being net protective).
 */
fun coffeeTaperNext(current: Int, floorDrinks: Int = 1, ratePerWeek: Double = 0.15): Int {
    require(ratePerWeek > 0) { "taper rate must be positive" }
    val rate = min(ratePerWeek, COFFEE_TAPER_MAX_RATE_PER_WEEK)
    if (current <= floorDrinks) return current
    val step = max(1, (current * rate).roundToInt())
    return max(floorDrinks, current - step)
}

// ===========================================================================
// 5. SMOKING — count, don't condemn. Zero penalties, forever. (§7.5)
// ===========================================================================

/**
 * There is deliberately no `smokingStreak()`, no consistency score, no ladder, no proof, and no entry
 * in the penalty engine. This pillar is a notebook, not a warden.
 *
 * A streak is a machine for converting one bad Tuesday into a finished project: it makes the cost of
 * a lapse discontinuous, which is precisely the abstinence violation effect rendered as UI. The
 * metric is [lapseRecoveryLatencyHours] instead — a number that gets BETTER the faster he comes back,
 * and that a lapse cannot zero.
 */
object Smoking {

    /** Cravings peak and decay in 3–5 minutes. Five minutes and a bit is evidence-aligned and free. */
    const val URGE_SURF_SECONDS = 300

    /** SPEC §7.5 verbatim: what he hears on a lapse. Flat. Ten seconds. Then it is over. */
    const val LAPSE_LINE: String =
        "That's a cigarette. That's all it is. It's not a verdict.\n" +
            "I'm not doing a bit about this and I'm not bringing it up on Sunday.\n" +
            "The button's there when you want to tell me what happened right before it."

    /** Cue awareness IS the intervention. This flips the pillar from surveillance to insight. */
    data class CueLog(
        val at: Long,
        val place: String,
        val company: String,
        val affect: String,
        val antecedent: String,
    )

    /** Never `lapseCount`. The count is not the metric and is never surfaced as one. */
    data class Lapse(val at: Long)

    /**
     * THE METRIC: how fast he got back up, in hours. Not how many times he fell.
     *
     * "Fell off Tuesday. Back on Wednesday. Nineteen hours. That's the fastest you've ever gotten
     * back up. I'm FURIOUS about it."
     */
    fun lapseRecoveryLatencyHours(lapseAt: Long, backOnTrackAt: Long): Double? {
        if (backOnTrackAt < lapseAt) return null
        return (backOnTrackAt - lapseAt) / 3_600_000.0
    }

    /** Implementation intentions built from HIS OWN logged cues, not from a generic list. */
    fun implementationIntention(cue: CueLog, substitution: String): String =
        "IF ${cue.place} ${cue.antecedent}, THEN $substitution first"

    /** Heaviness of Smoking Index — cigs/day + time-to-first-cigarette. 0–6. */
    fun hsi(cigsPerDay: Int, timeToFirstCigaretteMin: Int): Int {
        require(cigsPerDay >= 0) { "cigs/day cannot be negative" }
        require(timeToFirstCigaretteMin >= 0) { "TTFC cannot be negative" }
        val cigScore = when {
            cigsPerDay <= 10 -> 0
            cigsPerDay <= 20 -> 1
            cigsPerDay <= 30 -> 2
            else -> 3
        }
        val ttfcScore = when {
            timeToFirstCigaretteMin <= 5 -> 3
            timeToFirstCigaretteMin <= 30 -> 2
            timeToFirstCigaretteMin <= 60 -> 1
            else -> 0
        }
        return cigScore + ttfcScore
    }

    enum class DependenceBand { LOW, MODERATE, HIGH }

    fun dependence(cigsPerDay: Int, timeToFirstCigaretteMin: Int): DependenceBand =
        when (hsi(cigsPerDay, timeToFirstCigaretteMin)) {
            0, 1 -> DependenceBand.LOW
            2, 3 -> DependenceBand.MODERATE
            else -> DependenceBand.HIGH
        }

    /** High dependence = "an app is your sidekick, not your treatment." Route out, and mean it. */
    fun routeToTreatment(cigsPerDay: Int, timeToFirstCigaretteMin: Int): Boolean =
        dependence(cigsPerDay, timeToFirstCigaretteMin) == DependenceBand.HIGH

    /** Surfaced, not buried. Efficacy is roughly relative risk of abstinence vs placebo/none. */
    enum class Treatment(val approxRr: Double, val note: String) {
        VARENICLINE(2.2, "Strongest single agent."),
        COMBINATION_NRT(1.9, "Patch plus a fast-acting form. Not patch alone."),
        NICOTINE_ECIG(1.6, "Cochrane 2024, high certainty, vs NRT."),
        CYTISINE(1.7, "Cheap, effective, and made in Bulgaria — Sopharma/Tabex."),
    }

    /** Abrupt beats gradual (49.0% vs 39.2% at 4 wk). Cutting down is mostly a way of not quitting. */
    const val ABRUPT_BEATS_GRADUAL = true

    /**
     * The quitline number is supplied by the Android layer from `BuildConfig.QUITLINE_BG`, verified at
     * build time. This module is pure JVM and will not carry a phone number recalled from memory: a
     * wrong number on this screen is worse than no screen.
     */
    @JvmInline
    value class Quitline(val e164: String)
}

// ===========================================================================
// 6. WEIGHT — trend only, and the tripwire that outranks everything (§7.12)
// ===========================================================================

/** Hacker's Diet. Daily alpha. */
const val WEIGHT_EWMA_ALPHA = 0.1

/** Manual entry, weekly maximum — the app refuses more, warmly. Daily weighing is body checking. */
const val WEIGHT_MIN_DAYS_BETWEEN_ENTRIES = 7

/**
 * No delta over any window shorter than 90 days. 1–2 kg of glycogen and sodium swing swamps a real
 * 0.5 kg/week change, so a 7-day delta is showing him noise and calling it feedback.
 */
const val WEIGHT_MIN_DELTA_WINDOW_DAYS = 90

data class WeightEntry(val epochDay: Long, val kg: Double)

data class TrendPoint(val epochDay: Long, val trendKg: Double)

fun weightEntryAllowed(history: List<WeightEntry>, nowEpochDay: Long): Boolean {
    val last = history.maxByOrNull { it.epochDay } ?: return true
    return nowEpochDay - last.epochDay >= WEIGHT_MIN_DAYS_BETWEEN_ENTRIES
}

/** No arrows, no colours, no goal field, no window under 90 days. */
fun weightDeltaExposable(windowDays: Int): Boolean = windowDays >= WEIGHT_MIN_DELTA_WINDOW_DAYS

/**
 * EWMA trend on a DAILY grid, seeded at the first reading.
 *
 * Two decisions worth stating, because both are wrong if you do the obvious thing:
 *
 * 1. SEEDED AT THE FIRST READING, not at zero. An EWMA seeded at zero spends its first two months
 *    climbing out of the basement, and every derived rate during that climb is fiction — and the
 *    tripwire below reads those rates.
 *
 * 2. DAILY GRID WITH INTERPOLATION, not per-entry. Entries are weekly-max by design. Applying
 *    alpha=0.1 once per ENTRY makes the trend move 10% of the way per WEEK — a ~70-day time constant,
 *    against which a 3-week tripwire is arithmetically incapable of firing. Alpha is per DAY (as in
 *    Hacker's Diet, which assumes daily weighing), so the grid must be daily; observations between
 *    entries are linearly interpolated and the last reading is carried forward.
 *
 * The residual is a ~9-day phase lag ((1-a)/a), so a newly-started loss reads slightly under true for
 * the first fortnight. That is the correct direction to be wrong: the alternative is firing a GP
 * referral at a man who ate less salt this week.
 */
fun weightTrend(
    entries: List<WeightEntry>,
    nowEpochDay: Long? = null,
    alpha: Double = WEIGHT_EWMA_ALPHA,
): List<TrendPoint> {
    require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1]" }
    if (entries.isEmpty()) return emptyList()

    val sorted = entries.sortedBy { it.epochDay }.distinctBy { it.epochDay }
    val firstDay = sorted.first().epochDay
    val lastDay = max(sorted.last().epochDay, nowEpochDay ?: sorted.last().epochDay)

    val out = ArrayList<TrendPoint>((lastDay - firstDay + 1).toInt().coerceAtLeast(1))
    var trend = sorted.first().kg
    var idx = 0

    for (day in firstDay..lastDay) {
        while (idx < sorted.size - 1 && sorted[idx + 1].epochDay <= day) idx++
        val obs = when {
            idx >= sorted.size - 1 -> sorted.last().kg          // carry the last reading forward
            else -> {
                val a = sorted[idx]
                val b = sorted[idx + 1]
                val span = (b.epochDay - a.epochDay).toDouble()
                val t = (day - a.epochDay) / span
                a.kg + (b.kg - a.kg) * t
            }
        }
        trend = if (day == firstDay) obs else trend + alpha * (obs - trend)
        out.add(TrendPoint(day, trend))
    }
    return out
}

internal fun List<TrendPoint>.trendAt(epochDay: Long): Double? =
    firstOrNull { it.epochDay == epochDay }?.trendKg

enum class TripwireReason {
    /** Trend loss >1% bodyweight/week sustained three weeks. */
    RAPID_TREND_LOSS,

    /** Trend BMI below 18.5. Computed here and NOWHERE else; the number never leaves this function. */
    BMI_UNDER_18_5,
}

/**
 * Not a nag. A stop. Note what this carries and what it does not: there is no [Register], no line, no
 * score, no arrow, and no BMI value. Nobody does a bit on that screen.
 */
data class TripwireResult(
    val fired: Boolean,
    val reason: TripwireReason? = null,
    /** The character drops. Not softened — dropped. */
    val characterSilenced: Boolean = false,
    val modulesAutoDisabled: Boolean = false,
    val gpRouting: Boolean = false,
)

private val NOT_FIRED = TripwireResult(fired = false)

const val TRIPWIRE_MAX_WEEKLY_LOSS_FRACTION = 0.01
const val TRIPWIRE_SUSTAINED_WEEKS = 3
const val TRIPWIRE_MIN_BMI = 18.5

/**
 * THE RATE-OF-CHANGE TRIPWIRE.
 *
 * There is no `enabled` parameter, and that is the entire design. It is enqueued unconditionally at
 * install and cannot be disabled, because the trajectory it detects is produced just as readily by
 * the exercise-only path with the weight module switched off — which is exactly the configuration a
 * user heading somewhere bad will choose. A safety floor that can be turned off by the person it
 * protects is a decoration. See [healthTick], which computes this BEFORE it looks at any module flag.
 *
 * @param heightM optional; when absent, only the rate limb can fire.
 */
fun tripwire(entries: List<WeightEntry>, heightM: Double?, now: Long): TripwireResult {
    val today = now.toEpochDay()
    val trend = weightTrend(entries, nowEpochDay = today)
    if (trend.isEmpty()) return NOT_FIRED

    if (heightM != null && heightM > 0.0) {
        val bmi = trend.last().trendKg / (heightM * heightM)
        if (bmi < TRIPWIRE_MIN_BMI) return fire(TripwireReason.BMI_UNDER_18_5)
    }

    val marks = (0..TRIPWIRE_SUSTAINED_WEEKS).map { trend.trendAt(today - it * 7L) }
    if (marks.any { it == null || it <= 0.0 }) return NOT_FIRED

    // marks[0] = today, marks[1] = a week ago, ... Each successive week must have lost >1%.
    val everyWeekLosing = (0 until TRIPWIRE_SUSTAINED_WEEKS).all { w ->
        val earlier = marks[w + 1]!!
        val later = marks[w]!!
        (earlier - later) / earlier > TRIPWIRE_MAX_WEEKLY_LOSS_FRACTION
    }
    return if (everyWeekLosing) fire(TripwireReason.RAPID_TREND_LOSS) else NOT_FIRED
}

private fun fire(reason: TripwireReason) = TripwireResult(
    fired = true,
    reason = reason,
    characterSilenced = true,
    modulesAutoDisabled = true,
    gpRouting = true,
)

// ===========================================================================
// 7. EXERCISE — the rung model and the autoregulator (§7.6, §7.8–7.11)
// ===========================================================================

enum class Pattern { SQUAT, HINGE, H_PUSH, H_PULL, V_PULL, V_PUSH, CARRY, CORE, DESK_ANTIDOTE }

/**
 * Nine patterns, ~six rungs each. Not 300 exercises: that is a content dump and decision paralysis on
 * a phone. Each rung is a promotion he can feel. Index == rung. (§7.6; the animated assets are cut
 * from v1 per RESOLUTIONS §E — these are the names the rung model needs to mean anything.)
 */
val LADDERS: Map<Pattern, List<String>> = mapOf(
    Pattern.SQUAT to listOf(
        "box squat to chair", "box squat to sofa", "full bodyweight squat", "3s tempo squat",
        "split squat", "RFESS", "RFESS + pack",
    ),
    Pattern.HINGE to listOf(
        "wall hinge with dowel", "glute bridge", "single-leg bridge", "bodyweight RDL",
        "RDL + pack", "single-leg RDL", "hip thrust", "KB swing",
    ),
    Pattern.H_PUSH to listOf(
        "wall push-up", "counter push-up", "chair incline push-up", "full push-up",
        "3s tempo push-up", "feet-elevated push-up", "archer push-up",
    ),
    Pattern.H_PULL to listOf(
        "table row", "inverted row", "feet-elevated inverted row", "single-arm pack row",
        "weighted inverted row",
    ),
    Pattern.V_PULL to listOf(
        "dead hang", "scap pull", "assisted pull-up", "5s negatives", "chin-up", "pull-up",
        "weighted pull-up",
    ),
    Pattern.V_PUSH to listOf(
        "wall pike push-up", "box pike push-up", "pack overhead press", "single-arm overhead press",
    ),
    Pattern.CARRY to listOf(
        "suitcase carry", "jug farmer carry", "heavy farmer carry", "front-rack carry",
        "overhead carry",
    ),
    Pattern.CORE to listOf(
        "dead bug", "short hard plank (20-30s)", "side plank", "hollow hold", "Copenhagen plank",
        "ab rollout",
    ),
    Pattern.DESK_ANTIDOTE to listOf(
        "90/90", "couch stretch", "t-spine extension", "wall slides", "chin tucks",
    ),
)

/** KB swings taught early are the #1 way a desk worker hurts his back and quits. */
val GATED_MOVEMENTS: Set<String> = setOf("KB swing")

/** Demonstrated RDL competence: 3x12 @ RIR 2 with a 20 kg pack. */
data class RdlCompetence(val sets: Int, val reps: Int, val rir: Int, val packKg: Int)

fun kbSwingUnlocked(c: RdlCompetence?): Boolean =
    c != null && c.sets >= 3 && c.reps >= 12 && c.rir <= 2 && c.packKg >= 20

enum class TrainingMode {
    NORMAL,

    /** SICK · INJURED · TRAVELLING · DELOAD — v1, mandatory, uncapped, one tap, no shame
     *  before/during/after. An app with no "I have the flu" state gets deleted the first time he has
     *  the flu. Everyone forgets this and everyone dies of it. */
    SICK,
    INJURED,
    TRAVELLING,
    DELOAD,

    FLOOR,
    TEST_WEEK,
}

/** Two consecutive misses. Not one — Lally 2010: missing a single opportunity does not materially
 *  impair automaticity; the SECOND consecutive miss is what kills habits. */
const val FLOOR_MISS_THRESHOLD = 2

/** Tendons adapt slower than muscle, and month 4 is the danger zone. */
const val RUNG_ADVANCE_COOLDOWN_DAYS = 14

const val DEFAULT_SETS = 3
val DEFAULT_REP_RANGE = 8..12

data class PatternState(
    val pattern: Pattern,
    val rung: Int = 0,
    val lastRungChangeEpochDay: Long = Long.MIN_VALUE / 4,
    /** Consecutive MISSED sessions. Reset on any completed session — including a Floor session. */
    val consecutiveMisses: Int = 0,
) {
    val ladder: List<String> get() = LADDERS.getValue(pattern)
    val movement: String get() = ladder[rung.coerceIn(0, ladder.lastIndex)]
}

/** One session's honest self-report. Prescribed by RIR, never %1RM (Zourdos 2016): he has no gym and
 *  no 1RM, and a percentage of a number that does not exist is theatre. */
data class SessionReport(
    val allSetsHitTopOfRange: Boolean,
    val lastSetRir: Int,
    val setOneReps: Int,
)

/** Any 2 of these -> auto-deload. Reframed as strategy, never as mercy. */
data class ReadinessSignals(
    val restingHr: Int = 0,
    val restingHrBaseline: Int = 0,
    val shortSleepNights: Int = 0,
    val wakeDriftMin: Int = 0,
)

fun autoDeloadTriggered(s: ReadinessSignals): Boolean {
    var flags = 0
    if (s.restingHrBaseline > 0 && s.restingHr > s.restingHrBaseline + 7) flags++
    if (s.shortSleepNights >= 2) flags++
    if (abs(s.wakeDriftMin) > 90) flags++
    return flags >= 2
}

data class Prescription(
    val mode: TrainingMode,
    val movement: String,
    val sets: Int,
    val repRange: IntRange,
    val volumeMultiplier: Double,
    val rungDelta: Int,
    val rungsFrozen: Boolean,
    val reason: String,
    /**
     * §7.10: "The Floor counts as a FULL success. Not partial credit. Never yellow. Anywhere,
     * including the widget." The instant the Floor reads as failure, he skips it, and he's out.
     */
    val countsAsFullSuccess: Boolean = true,
    val penaltyDebtAllowed: Boolean = true,
)

enum class GateReason {
    /** PAR-Q+ (7 items, verbatim) before ANY exercise prescription. */
    PARQ_POSITIVE,

    /** The safety floor outranks the program. */
    TRIPWIRE_ACTIVE,
}

sealed interface PrescriptionResult {
    data class Prescribed(val prescription: Prescription) : PrescriptionResult
    data class Gated(val reason: GateReason) : PrescriptionResult
}

/**
 * Rung advancement is capped at 1 per pattern per 2 weeks, ALWAYS — regardless of how good he feels,
 * how many reps he hit, or how much he wants it. This function deliberately takes no performance
 * argument: there is no input that unlocks the cap, because the cap is not about performance. Tendon
 * and connective tissue adapt on a slower clock than muscle, so the fittest-feeling week is exactly
 * the week the cap earns its keep.
 */
fun canAdvanceRung(state: PatternState, nowEpochDay: Long): Boolean {
    if (state.rung >= state.ladder.lastIndex) return false
    return nowEpochDay - state.lastRungChangeEpochDay >= RUNG_ADVANCE_COOLDOWN_DAYS
}

fun floorModeActive(consecutiveMisses: Int): Boolean = consecutiveMisses >= FLOOR_MISS_THRESHOLD

/**
 * THE AUTOREGULATOR. "Progressive" means AUTOREGULATED — including EASIER.
 *
 * Linear escalation guarantees injury or quit by month 4. An app that only ever says MORE is noise,
 * and noise gets muted.
 *
 * Precedence, top down — and the order is the design:
 *   1. PAR-Q+ positive        -> no prescription at all. A clinician, not a ghost.
 *   2. Tripwire firing        -> no prescription at all.
 *   3. Sick / Injured         -> nothing is prescribed and nothing is owed.
 *   4. Auto-deload signals    -> x0.6, rungs frozen, NO PENALTY, framed as strategy.
 *   5. Floor mode (2 misses)  -> ONE SET. Never escalation.
 *   6. Scheduled deload       -> x0.5, intensity held. NON-NEGOTIABLE.
 *   7. Travelling             -> the Floor, without the demotion.
 *   8. Test week              -> measure, don't train.
 *   9. Normal.
 *
 * The autoregulator can override the penalty engine. The penalty engine can never override the
 * autoregulator.
 */
fun prescribe(
    state: PatternState,
    mode: TrainingMode,
    gates: ClinicalGates,
    signals: ReadinessSignals = ReadinessSignals(),
    lastReports: List<SessionReport> = emptyList(),
    scheduledDeload: Boolean = false,
    tripwire: TripwireResult = NOT_FIRED,
    nowEpochDay: Long = 0L,
): PrescriptionResult {
    if (gates.parqPositive) return PrescriptionResult.Gated(GateReason.PARQ_POSITIVE)
    if (tripwire.fired) return PrescriptionResult.Gated(GateReason.TRIPWIRE_ACTIVE)

    fun rx(
        mode: TrainingMode,
        sets: Int,
        volume: Double,
        rungDelta: Int,
        frozen: Boolean,
        reason: String,
        penalty: Boolean = true,
        range: IntRange = DEFAULT_REP_RANGE,
    ) = PrescriptionResult.Prescribed(
        Prescription(
            mode = mode,
            movement = state.ladder[(state.rung + rungDelta).coerceIn(0, state.ladder.lastIndex)],
            sets = sets,
            repRange = range,
            volumeMultiplier = volume,
            rungDelta = rungDelta,
            rungsFrozen = frozen,
            reason = reason,
            penaltyDebtAllowed = penalty,
        ),
    )

    if (mode == TrainingMode.SICK || mode == TrainingMode.INJURED) {
        return rx(mode, 0, 0.0, 0, true, "Nothing today. Nothing is owed.", penalty = false)
    }

    if (autoDeloadTriggered(signals)) {
        return rx(
            TrainingMode.DELOAD, DEFAULT_SETS, 0.6, 0, true,
            "Your resting heart rate is up and you are not sleeping. Today is smaller. " +
                "That is a strategy, not a mercy.",
            penalty = false,
        )
    }

    if (mode == TrainingMode.FLOOR || floorModeActive(state.consecutiveMisses)) {
        // Two consecutive misses: drop a rung, target ONE SET. Never escalation. The Floor is never
        // the app raising the price of coming back — that is how the second miss becomes the last.
        return rx(
            TrainingMode.FLOOR, 1, 1.0, -1, true,
            "One set. Or a ten-minute walk. Never zero. It counts, in full.",
            penalty = false,
        )
    }

    if (mode == TrainingMode.DELOAD || scheduledDeload) {
        return rx(
            TrainingMode.DELOAD, DEFAULT_SETS, 0.5, 0, true,
            "Scheduled deload. Volume halves, intensity holds. Non-negotiable.",
            penalty = false,
        )
    }

    if (mode == TrainingMode.TRAVELLING) {
        return rx(
            TrainingMode.TRAVELLING, 1, 1.0, 0, true,
            "You're on the road. One set. It counts, in full.",
            penalty = false,
        )
    }

    if (mode == TrainingMode.TEST_WEEK) {
        return rx(
            TrainingMode.TEST_WEEK, 1, 1.0, 0, true, "Test week. We measure. We don't train.",
            penalty = false, range = 1..Int.MAX_VALUE,
        )
    }

    // --- NORMAL -------------------------------------------------------------
    val recent = lastReports.takeLast(2)

    // set 1 misses 8 on TWO consecutive -> deload 10% OR -1 rung
    if (recent.size == 2 && recent.all { it.setOneReps < DEFAULT_REP_RANGE.first }) {
        return rx(
            TrainingMode.NORMAL, DEFAULT_SETS, 0.9, -1, true,
            "You've missed the bottom of the range twice. We go back a step. This is the job.",
        )
    }

    // all sets hit 12 AND last-set RIR >= 1 -> +1 rung; restart at 8. But only if the clock allows.
    val last = lastReports.lastOrNull()
    if (last != null && last.allSetsHitTopOfRange && last.lastSetRir >= 1) {
        if (canAdvanceRung(state, nowEpochDay)) {
            return rx(
                TrainingMode.NORMAL, DEFAULT_SETS, 1.0, +1, false,
                "You earned a rung. Restart at eight.",
            )
        }
        return rx(
            TrainingMode.NORMAL, DEFAULT_SETS, 1.0, 0, true,
            "You're ready and you're waiting. Tendons don't read your enthusiasm. Two weeks.",
        )
    }

    // reported top-set RIR >= 4 twice -> add load without asking; Rip calls him a liar, in character.
    if (recent.size == 2 && recent.all { it.lastSetRir >= 4 }) {
        return rx(
            TrainingMode.NORMAL, DEFAULT_SETS, 1.15, 0, true,
            "Four in the tank. Twice. You're sandbagging me, brother. It's heavier now.",
        )
    }

    return rx(TrainingMode.NORMAL, DEFAULT_SETS, 1.0, 0, false, "Same as last time.")
}

/**
 * A missed session leaves the next session UNCHANGED. Never make up volume.
 *
 * Makeup sessions are how people get hurt and quit: the miss already happened, and doubling the next
 * one prices the return to the gym above the price of not returning at all.
 */
fun makeupVolume(missedSessions: Int): Int = 0

// --- pull:push ----------------------------------------------------------------

/** Pull >= 2x push in months 1–3 (weeks 1–13); >= 1:1 forever. Pulling is the desk worker's #1
 *  deficit, and bodyweight-only pulling is this program's biggest honest hole (§7.6). */
fun minPullToPushRatio(weekOfProgram: Int): Double = if (weekOfProgram <= 13) 2.0 else 1.0

fun pullPushCompliant(pullSets: Int, pushSets: Int, weekOfProgram: Int): Boolean {
    if (pushSets <= 0) return true
    return pullSets.toDouble() / pushSets >= minPullToPushRatio(weekOfProgram)
}

fun prescribedPullSets(pushSets: Int, weekOfProgram: Int): Int =
    ceil(pushSets * minPullToPushRatio(weekOfProgram)).toInt()

// --- test week ----------------------------------------------------------------

/** Every 8 weeks from week 12: max push-ups, dead hang, 2 km walk-run, RFESS reps, resting HR.
 *  Six genuine, dated, self-generated "holy shit, I did that" moments across ten months, from ~200
 *  lines of code. No joke bank competes with that, which is why weight doesn't need to be the
 *  outcome variable. */
fun isTestWeek(weekOfProgram: Int): Boolean =
    weekOfProgram >= 12 && (weekOfProgram - 12) % 8 == 0

// --- pain ---------------------------------------------------------------------

enum class PainKind { DULL, SHARP, RADIATING, JOINT_LINE }

enum class PainVerdict { WORK_THROUGH, STOP }

/**
 * Dull, <3/10, non-progressive, settles in 24 h -> work through.
 * Sharp / radiating / joint-line / worse next day -> STOP.
 *
 * "PAIN" is a free break-glass reason: zero cost, zero shame, zero Ledger mention.
 */
fun painVerdict(
    kind: PainKind,
    severityOutOfTen: Int,
    progressive: Boolean,
    worseNextDay: Boolean,
): PainVerdict {
    if (kind != PainKind.DULL) return PainVerdict.STOP
    if (severityOutOfTen >= 3) return PainVerdict.STOP
    if (progressive || worseNextDay) return PainVerdict.STOP
    return PainVerdict.WORK_THROUGH
}

/** No plyos in months 1–3. No running from zero — walk-run only. */
fun plyosAllowed(weekOfProgram: Int): Boolean = weekOfProgram > 13

// --- penalty debt -------------------------------------------------------------

// The ceiling itself (`PENALTY_DEBT_CEILING_REPS` = 20 — not 40; 40 is a compulsion budget, 20 is a
// nudge) is declared by Escalation.kt, §6.9, which owns how much debt may exist and how it expires.
// This file clamps against it and owns the prior question — whether any debt may exist at all:
// training mode, pain stops, the sleep window, and which HealthActions are penalty-eligible.

/** If penalty volume exceeds this share over 4 weeks, the app reduces its own aggression, and says
 *  why. "37% of your pulling came from screwing up" is CUT: that's a clinical finding, not a brag. */
const val PENALTY_VOLUME_SHARE_LIMIT = 0.15

/**
 * Penalty debt: ceiling 20 reps/day, expires end of day, never accrues. None during
 * wind-down/sleep/deload/Sick/Injured/Floor/after a pain-stop — and structurally none as a
 * consequence of a food or weight event, because [HealthAction.EAT_HEALTHY] and
 * [HealthAction.WEIGH_LESS] are not penalty-eligible at any input.
 */
fun penaltyDebtReps(
    requestedReps: Int,
    mode: TrainingMode,
    inSleepSilenceWindow: Boolean,
    painStoppedToday: Boolean,
    trigger: HealthAction,
): Int {
    if (!penaltyEligible(trigger)) return 0
    if (inSleepSilenceWindow || painStoppedToday) return 0
    if (mode != TrainingMode.NORMAL) return 0
    return requestedReps.coerceIn(0, PENALTY_DEBT_CEILING_REPS)
}

fun aggressionShouldReduce(penaltyReps: Int, totalReps: Int): Boolean {
    if (totalReps <= 0) return false
    return penaltyReps.toDouble() / totalReps > PENALTY_VOLUME_SHARE_LIMIT
}

// ===========================================================================
// 8. THE TICK — where "always live" is actually enforced
// ===========================================================================

/** Every module the user can switch off. Note that the tripwire is not among them. */
data class HealthModules(
    val water: Boolean = true,
    val sleep: Boolean = true,
    val coffee: Boolean = true,
    val exercise: Boolean = true,
    /** Reading / guitar (§7.1's #5). */
    val identity: Boolean = true,
    val weight: Boolean = true,
) {
    companion object {
        fun allDisabled() = HealthModules(false, false, false, false, false, false)
    }
}

data class HealthTickResult(
    /** Computed before any module flag is read, and returned regardless of all of them. */
    val tripwire: TripwireResult,
    val activePillars: Set<Pillar>,
)

/**
 * The daily read. The tripwire is evaluated FIRST and unconditionally: it takes no module argument
 * and there is no branch above it. The exercise-only path produces the same weight trajectory as the
 * weight-module path, which is exactly why it must fire with every module off.
 */
fun healthTick(
    modules: HealthModules,
    weightEntries: List<WeightEntry>,
    heightM: Double?,
    now: Long,
): HealthTickResult {
    val trip = tripwire(weightEntries, heightM, now)

    if (trip.fired) {
        // Character drops, modules auto-disable, one flat screen, GP routing. Not a nag. A stop.
        return HealthTickResult(trip, emptySet())
    }

    val active = buildSet {
        if (modules.water) add(Pillar.WATER)
        if (modules.sleep) add(Pillar.SLEEP)
        if (modules.coffee) add(Pillar.COFFEE)
        if (modules.exercise) add(Pillar.EXERCISE)
        if (modules.identity) add(Pillar.IDENTITY)
        if (modules.weight) add(Pillar.WEIGHT)
        // Smoking has no module toggle and no aversives: it is a notebook, and notebooks don't nag.
        add(Pillar.SMOKING)
    }
    return HealthTickResult(trip, active)
}
