package com.secondspine.coach

/**
 * The pipeline: ENFORCED -> AUDITED -> TRUSTED -> RETIRED.
 *
 * Rip has no vote here. The contract the user signed graduates habits on measured evidence, and Rip
 * can never reclaim jurisdiction on his own initiative. That single fact is why this works: it puts
 * attribution on the user's own prior commitment and his own data, which is the textbook shape of
 * internalisation — and it makes the character's arc a pure function of the user's success at zero
 * authored-content cost.
 */

/** Max habits under full enforcement at once. If everything is penalised, nothing is. */
const val MAX_ENFORCED = 2

/** Max concurrent AUDITED. Without this the odometer's declared range 0..4 is fiction and the
 *  invariant throws at the moment the user is doing best. (RESOLUTIONS §D.) */
const val MAX_AUDITED = 2

const val GRADUATION_COMPLIANCE = 0.85
const val COLLAPSE_COMPLIANCE = 0.60
const val COLLAPSE_WINDOW_DAYS = 21
const val REPAIR_WINDOW_DAYS = 14

/**
 * THE ODOMETER. One integer. It drives the register mix, the speech budget, the ARENA/ARCHIVE split,
 * the Tape's language ladder, and the ending. Nothing else may drive those five things.
 *
 * TRUSTED and RETIRED contribute nothing — they are gone from his desk.
 */
fun jurisdiction(habits: List<Habit>): Int =
    habits.count { it.stage == Stage.ENFORCED || it.stage == Stage.AUDITED }

/**
 * COMPLIANCE — and the most important line in this file is the filter.
 *
 * RESOLUTIONS §A1. The design panel caught the original spec making faking the dominant strategy and
 * grafted "confession never demotes". That fix was applied one layer too high: `shouldGraduate`
 * independently gates on compliance, so a confessed day — being non-compliant — still failed the
 * graduation, while an *uncaught fake day passed it*. Faking still dominated.
 *
 * So confessed days leave the ratio ENTIRELY. Not "count as passes" (that would be the app lying to
 * itself about the data it exists to collect) — excluded from the denominator, exactly as an
 * interlock-suspended day is. The day stays recorded as non-compliant in `proof`/`confession`; the
 * promotion gate simply cannot see it.
 *
 * Result: honesty strictly dominates deception at every hour, for every user, forever. Confession is
 * free, unlimited, warm, and never priced. The button is cheaper than lying. Always.
 */
fun compliance(days: List<Day>): Double {
    val scheduled = days.filterNot { it.confessed || it.suspended }
    if (scheduled.isEmpty()) return 1.0   // nothing was asked of him; nothing was failed
    return scheduled.count { it.completed }.toDouble() / scheduled.size
}

/** Days a habit has been in its current stage. */
fun daysInStage(habit: Habit, now: Long): Long =
    (now - habit.stageSince) / 86_400_000L

/**
 * PROMOTE — automatic, on measured evidence only. Rip is not consulted and cannot object.
 * Note the caught-events window: being caught faking freezes graduation for 14 days.
 */
fun shouldGraduate(
    habit: Habit,
    days: List<Day>,
    caught: List<CaughtEvent>,
    now: Long,
): Boolean {
    if (habit.stage != Stage.ENFORCED && habit.stage != Stage.AUDITED) return false
    if (daysInStage(habit, now) < habit.stage.minDays) return false
    if (caught.any { it.habitId == habit.id && now - it.at < REPAIR_WINDOW_DAYS.days }) return false
    return compliance(days.inWindow(habit.stage.windowDays, now)) >= GRADUATION_COMPLIANCE
}

/**
 * DEMOTE — exactly two causes, and they are not the same event.
 *
 * RESOLUTIONS §A2. "Only being caught demotes" was about confession-vs-caught, and taken literally it
 * left the pipeline with one near-dead branch: BYTE_REPLAY is near-unreachable on a camera-only
 * capture path, so it fires perhaps twice in ten months. Collapse demotes too — and collapse is the
 * renewable fuel that keeps Rip employed without inventing infinite new habits. It is also, for him,
 * the best day of his life.
 *
 * A confession can never appear here. That is enforced by TransitionReason having no CONFESSED value.
 */
fun demotionCause(
    habit: Habit,
    days: List<Day>,
    caught: List<CaughtEvent>,
    now: Long,
): TransitionReason? {
    if (habit.stage == Stage.RETIRED) return null
    if (caught.any { it.habitId == habit.id && now - it.at < 1.days }) {
        return TransitionReason.DEMOTED_CAUGHT
    }
    if (inRepairWindow(habit, days, now)) return null
    val window = days.inWindow(COLLAPSE_WINDOW_DAYS, now)
    if (window.isNotEmpty() && compliance(window) < COLLAPSE_COMPLIANCE) {
        return TransitionReason.DEMOTED_COLLAPSE
    }
    return null
}

/**
 * Confession opens a 14-day repair window in which confessed non-compliance cannot trigger a
 * collapse-demotion. So confession does not merely cost nothing — it *pays*. Silence leaves a fake
 * "compliant" day that a later BYTE_REPLAY converts into instant demotion.
 */
fun inRepairWindow(habit: Habit, days: List<Day>, now: Long): Boolean =
    days.any { it.habitId == habit.id && it.confessed && now - it.epochDay.dayMillis < REPAIR_WINDOW_DAYS.days }

/** The next stage up. There is no path back except demotion. */
fun Stage.next(): Stage = when (this) {
    Stage.ENFORCED -> Stage.AUDITED
    Stage.AUDITED -> Stage.TRUSTED
    Stage.TRUSTED -> Stage.RETIRED
    Stage.RETIRED -> Stage.RETIRED
}

/** Demotion drops exactly one rung. It never drops to RETIRED. */
fun Stage.demoted(): Stage = when (this) {
    Stage.RETIRED -> Stage.TRUSTED
    Stage.TRUSTED -> Stage.AUDITED
    Stage.AUDITED -> Stage.ENFORCED
    Stage.ENFORCED -> Stage.ENFORCED
}

/** Can this habit be promoted into ENFORCED/AUDITED without breaking the concurrency caps? */
fun canEnter(stage: Stage, habits: List<Habit>): Boolean = when (stage) {
    Stage.ENFORCED -> habits.count { it.stage == Stage.ENFORCED } < MAX_ENFORCED
    Stage.AUDITED -> habits.count { it.stage == Stage.AUDITED } < MAX_AUDITED
    else -> true
}

// --- small helpers ---------------------------------------------------------

internal val Int.days: Long get() = this * 86_400_000L
internal val Long.dayMillis: Long get() = this * 86_400_000L

internal fun List<Day>.inWindow(windowDays: Int, now: Long): List<Day> {
    val cutoff = (now - windowDays.days) / 86_400_000L
    return filter { it.epochDay >= cutoff }
}
