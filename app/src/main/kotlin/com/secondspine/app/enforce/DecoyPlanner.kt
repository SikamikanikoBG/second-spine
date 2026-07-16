package com.secondspine.app.enforce

import kotlin.random.Random

/**
 * THE DECOYS — and they are not flavour.
 *
 * SPEC §6.5, and this is the sentence the whole file exists for:
 *
 *   *"`setAlarmClock` publishes its fire time to the lock screen and to every app via
 *   `getNextAlarmClock()` — which broadcasts the exact jittered time the whole unpredictability
 *   protocol exists to hide. `getNextAlarmClock()` surfaces only the **soonest**, so: arm 4–6 decoy
 *   alarm clocks per window, cancel the losers at true fire time. He sees *an* alarm, never *the*
 *   alarm. And Rip gets to lie about which one is real."*
 *
 * The deterrent in this product is carried entirely by not knowing when. `setAlarmClock` is the only
 * scheduling primitive Doze does not rate-limit, so it is not optional — and it leaks. The leak is
 * `AlarmManager.getNextAlarmClock()`, which is a *public, permissionless read* available to the lock
 * screen, to any launcher widget, and to any app on the phone. Schedule the real 06:41 alarm and the
 * lock screen prints "06:41" on it, and the protocol is dead before it fires once.
 *
 * ### The one property that makes this work, and the way it is easy to get wrong
 *
 * `getNextAlarmClock()` returns **only the soonest** alarm clock, not the set. So decoys placed
 * *after* the real fire time are invisible and buy nothing — the system would still be advertising
 * the real one. **At least one decoy must fall strictly before the real fire time**, or the whole
 * mechanism is theatre. That is [PLAN_INVARIANT], it is asserted in [planDecoys], and it is the
 * single thing to check if this ever regresses.
 *
 * ### Why a decoy firing is silent, and why that is not a bug
 *
 * `setAlarmClock` schedules a `PendingIntent`; it makes no sound of its own. A decoy's broadcast is a
 * no-op that stamps `cancelled_at` and returns. The user's *only* observation of a decoy is the time
 * on his lock screen — which is exactly the observation being poisoned.
 *
 * ### Pure, and deliberately so
 *
 * No `Context`, no `AlarmManager`, no `System.currentTimeMillis()`. `rng` and `now` are parameters,
 * so the distribution is testable on a laptop with no SDK — the same reason `:coach` is pure.
 */
internal object DecoyPlanner {

    /** SPEC §6.5: "arm 4–6 decoy alarm clocks per window". Inclusive. */
    const val MIN_DECOYS = 4
    const val MAX_DECOYS = 6

    /**
     * No decoy may land within this of the real fire time.
     *
     * Two reasons, and the second is the load-bearing one:
     *  1. A decoy at 06:40:58 and a real alarm at 06:41:00 makes the decoy a *tell* — two alarms two
     *     seconds apart is a pattern, and the man reading it has a week of mornings to spot it.
     *  2. The losers are cancelled *at* true fire time. A decoy inside the guard band is racing that
     *     cancellation, and the loser of that race is a decoy that fires after the ladder has already
     *     started — which is the one event that would let him identify the real alarm by elimination.
     */
    const val GUARD_BAND_MS = 90_000L

    /** Two decoys closer together than this read as one alarm. Spread them. */
    const val MIN_SEPARATION_MS = 4L * 60_000L

    /**
     * The invariant, stated once so a test can name it: **the soonest armed alarm clock is never the
     * real one** — whenever the window leaves room for a decoy in front of it.
     */
    const val PLAN_INVARIANT =
        "at least one decoy strictly precedes the real fire time, so getNextAlarmClock() never " +
            "advertises the real alarm"

    /**
     * Decoy fire times for one window.
     *
     * @param windowStart earliest instant a decoy may occupy — normally `now` at planning time,
     *   never earlier, because an alarm clock in the past fires immediately and would advertise
     *   itself by going off in his hand.
     * @param windowEnd the challenge's expiry. Decoys never outlive the challenge they hide.
     * @param realFireAt the true R0 instant. Never returned in the result.
     * @return 4–6 instants, sorted, none within [GUARD_BAND_MS] of [realFireAt], at least one before
     *   it where the window allows. Empty only when the window is degenerate — see below.
     */
    fun planDecoys(
        windowStart: Long,
        windowEnd: Long,
        realFireAt: Long,
        rng: Random = Random.Default,
    ): List<Long> {
        // A window with no room is not an error and must not throw: the planner may legitimately arm
        // a challenge whose fire time is immediate. Returning empty means "no decoys", the real alarm
        // is advertised, and the ladder still fires — a degraded deterrent beats a crashed one.
        val before = LongRange(windowStart, realFireAt - GUARD_BAND_MS)
        val after = LongRange(realFireAt + GUARD_BAND_MS, windowEnd)
        if (before.isEmpty() && after.isEmpty()) return emptyList()

        val target = rng.nextInt(MIN_DECOYS, MAX_DECOYS + 1)
        val picked = sortedSetOf<Long>()

        // THE INVARIANT, FIRST. One decoy in front of the real alarm before anything else is
        // considered, because every decoy after it is worthless on its own — getNextAlarmClock()
        // surfaces only the soonest.
        if (!before.isEmpty()) picked += rng.nextLongIn(before)

        // The rest, spread across everything that is left. Sampled with a separation floor rather
        // than laid out on a grid: an evenly spaced decoy set is a pattern, and a pattern is a tell.
        val pool = listOfNotNull(before.takeIf { !it.isEmpty() }, after.takeIf { !it.isEmpty() })
        var guard = 0
        while (picked.size < target && guard < ATTEMPT_LIMIT) {
            guard++
            val range = pool[rng.nextInt(pool.size)]
            val candidate = rng.nextLongIn(range)
            if (picked.none { kotlin.math.abs(it - candidate) < MIN_SEPARATION_MS }) picked += candidate
        }

        return picked.toList()
    }

    /**
     * Attempts before giving up on hitting [MIN_DECOYS].
     *
     * A short window physically cannot hold six alarms [MIN_SEPARATION_MS] apart, and looping until
     * it does would hang the planner at 04:00 on a Tuesday. Fewer decoys in a narrow window is the
     * correct outcome: the window is narrow, so there is less to hide.
     */
    private const val ATTEMPT_LIMIT = 400

    private fun Random.nextLongIn(range: LongRange): Long =
        if (range.first >= range.last) range.first else nextLong(range.first, range.last + 1)
}
