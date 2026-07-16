package com.secondspine.coach

/**
 * THE DEMAND RESOLVER — the core loop, and the piece the whole app was missing.
 *
 * Every surface, worker and screen was built and wired, the ladder was implemented, the archive
 * exported, the Tape composed — and the app still asked nothing of anyone, because nothing ever
 * decided WHAT to ask. A coach with no demand is a photo gallery with opinions.
 *
 * The law this exists to enforce (panel hard rule): **NEVER MORE THAN ONE DEMAND AT A TIME.**
 * Multiple simultaneous demands turns the coach into a todo list, and todo lists die. So this
 * returns exactly one [Demand] or null — never a list. If two obligations are open, the second one
 * does not exist yet.
 *
 * Pure JVM: time enters as parameters, so the whole day can be simulated in microseconds.
 */

/** The single thing being asked of the user right now. */
data class Demand(
    val habitId: String,
    val text: String,
    val tier: Tier,
    /** Only exercise may ever climb to the lock. Water terminates at R2. */
    val lockEligible: Boolean,
)

/**
 * Why a habit is not being demanded right now. Kept explicit rather than filtered away, because
 * "the app is quiet" and "the app is broken" look identical from the outside, and this is what the
 * debug screen and the Sunday roast read to tell them apart.
 */
enum class Quiet {
    NOTHING_SCHEDULED,
    ALREADY_DONE_TODAY,
    ALREADY_CONFESSED_TODAY,
    SUSPENDED,           // sick / injured / travelling / deload — never a penalty
    NOT_ENFORCED,        // TRUSTED and RETIRED habits do not get to ask for anything
    OUTSIDE_WINDOW,      // its hours have not arrived, or have passed
    INTERLOCKED,         // wind-down, driving, a call — the safety floor, which outranks the demand
    OUTRANKED,           // another habit is the one demand today
}

/** A habit's daily demand window, in minutes-from-midnight. */
data class DemandWindow(val habitId: String, val openAt: Int, val closeAt: Int)

/**
 * Resolve THE one demand.
 *
 * Precedence, and each rule is a decision the panel made:
 *  1. Only ENFORCED/AUDITED habits may ask. A graduated habit is *gone from his desk* — that is the
 *     entire promise of the pipeline, and letting a TRUSTED habit nag would silently un-graduate it.
 *  2. Never ask for something already done or already confessed today. Confession is not a lesser
 *     completion; it ends the day's obligation exactly as a proof does, or it is not free.
 *  3. Never ask during a suspension. An app with no "I have the flu" state gets deleted the first
 *     time he has the flu.
 *  4. Highest TIER wins; ties break toward the habit that has been ENFORCED longest (the one closest
 *     to graduating — i.e. the one whose demand is worth the most to him and costs Rip the most).
 *
 * @param nowMinutes minutes from local midnight.
 * @return the one demand, or null with the reasons — never two.
 */
fun resolveDemand(
    habits: List<Habit>,
    today: List<Day>,
    windows: List<DemandWindow>,
    nowMinutes: Int,
    now: Long,
    ctx: DeviceContext? = null,
): Demand? = resolveDemandVerbose(habits, today, windows, nowMinutes, now, ctx).first

/** The same resolution, with the per-habit reasons it stayed quiet. For the debug screen and Tape. */
fun resolveDemandVerbose(
    habits: List<Habit>,
    today: List<Day>,
    windows: List<DemandWindow>,
    nowMinutes: Int,
    now: Long,
    ctx: DeviceContext? = null,
): Pair<Demand?, Map<String, Quiet>> {
    val reasons = LinkedHashMap<String, Quiet>()
    val candidates = mutableListOf<Habit>()

    for (h in habits) {
        val day = today.firstOrNull { it.habitId == h.id }
        val window = windows.firstOrNull { it.habitId == h.id }
        val quiet = when {
            h.stage != Stage.ENFORCED && h.stage != Stage.AUDITED -> Quiet.NOT_ENFORCED
            day == null -> Quiet.NOTHING_SCHEDULED
            day.suspended -> Quiet.SUSPENDED
            day.completed -> Quiet.ALREADY_DONE_TODAY
            day.confessed -> Quiet.ALREADY_CONFESSED_TODAY
            window == null -> Quiet.NOTHING_SCHEDULED
            nowMinutes < window.openAt || nowMinutes > window.closeAt -> Quiet.OUTSIDE_WINDOW
            else -> null
        }
        if (quiet != null) reasons[h.id] = quiet else candidates += h
    }

    // The safety floor outranks the demand. If the ladder may not even speak at R0, there is no
    // demand — not a silent one, none. A demand the app cannot follow up on is a bluff, and this
    // character does not bluff.
    if (ctx != null && !mayEscalate(Rung.R0_NOTIFICATION, ctx, now)) {
        candidates.forEach { reasons[it.id] = Quiet.INTERLOCKED }
        return null to reasons
    }

    val winner = candidates
        .sortedWith(compareByDescending<Habit> { it.tier.ordinal }.thenBy { it.stageSince })
        .firstOrNull() ?: return null to reasons

    candidates.filter { it.id != winner.id }.forEach { reasons[it.id] = Quiet.OUTRANKED }

    return Demand(
        habitId = winner.id,
        text = demandText(winner.id),
        tier = winner.tier,
        // EXERCISE IS THE ONLY LOCK-ELIGIBLE HABIT. This is read off the habit rather than inferred,
        // so the rule lives in one place and a new habit cannot quietly acquire the lock.
        lockEligible = winner.lockEligible,
    ) to reasons
}

/**
 * The ask itself. Short, imperative, and in his register — but deliberately NOT a joke: this is the
 * one string the user reads when they are deciding whether to comply, and a punchline here competes
 * with the instruction. Rip does his bit around the demand, never inside it.
 */
internal fun demandText(habitId: String): String = when (habitId) {
    "water" -> "Water. Glass. Camera."
    "exercise" -> "One set. That's the ask."
    "reading" -> "The book. One page. Show me."
    "guitar" -> "The guitar. Six strings. Prove it."
    "sleep" -> "Wind-down. Phone down."
    "coffee" -> "Last one was the last one."
    else -> "Show me."
}
