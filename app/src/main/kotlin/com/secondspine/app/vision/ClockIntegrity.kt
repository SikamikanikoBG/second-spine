package com.secondspine.app.vision

import android.os.SystemClock

/**
 * CLOCK INTEGRITY — an interlock that auto-voids, and the difference matters more than anything else
 * in this file.
 *
 * A clock jump **is not a catch.** It produces no `caught_event`, no `ledger_entry`, no demotion, and
 * no line. It voids the challenge and the app moves on. That is not leniency, it is accuracy: the
 * overwhelmingly most common cause of a wall-clock jump is a phone that crossed a timezone, resynced
 * NTP after a flat battery, or a user who fixed a clock that was wrong. Treating "your clock moved"
 * as "you lied" is the app inventing an accuser out of a timezone, and per SPEC §5.8's narration bar
 * the signal would have to be ~90% accurate before it were even allowed to reach the character's
 * mouth. Clock-jump-implies-fraud is nowhere near that. It is not close.
 *
 * So the character never gets this. `Ledger.kt` has a `CLOCK_JUMP` kind and Sunday may note that a
 * challenge voided — that is the Ledger's business and it is 28 days from forgetting it anyway. In
 * the moment: nothing.
 */

/**
 * The two clocks, sampled together.
 *
 * One of these is a setting and one of them is not, and every honest thing this app knows about time
 * comes from the gap between them:
 *  - `wall` — `System.currentTimeMillis()`. Settable. Meaningful to a human. Useless as evidence.
 *  - `elapsed` — `SystemClock.elapsedRealtime()`. Monotonic since boot, counts through sleep, not
 *    settable from userspace. Meaningless to a human. The only one that can carry a duration.
 *
 * They are sampled as a pair so their *difference* is a stable quantity. Sampling them apart and
 * comparing them later is how you build an interlock that fires on scheduler jitter.
 */
data class ClockBaseline(
    val wall: Long,
    val elapsed: Long,
) {
    companion object {
        /** Now, on both clocks. Called at nonce mint and again at capture. */
        fun now(): ClockBaseline = ClockBaseline(
            wall = System.currentTimeMillis(),
            elapsed = SystemClock.elapsedRealtime(),
        )
    }
}

/**
 * SPEC §5.8 / the brief: `|wall - elapsed| > 90s` → AUTO-VOID.
 *
 * Generous on purpose. NTP corrections are seconds, not minutes; DST is an hour but is applied to the
 * *displayed* zone rather than to `currentTimeMillis`, which is UTC-based and does not jump for it.
 * The tolerance is a full nonce window wide, so ordinary phone behaviour cannot reach it and only a
 * deliberate re-set of the device clock (or an NTP resync after a genuinely dead battery — which we
 * treat identically, because we cannot tell them apart and must not guess) does.
 */
const val CLOCK_SKEW_TOLERANCE_MS: Long = 90_000L

/**
 * Did the wall clock move independently of real time between [baseline] and now?
 *
 * The arithmetic, stated once so it is not re-derived wrongly somewhere else: over any interval, the
 * wall clock and the monotonic clock must advance by the same amount. `elapsedRealtime` counts real
 * seconds through Doze and through screen-off and cannot be set; `currentTimeMillis` can be dragged
 * anywhere. So `(wallNow - baseline.wall)` minus `(elapsedNow - baseline.elapsed)` is precisely the
 * amount by which somebody moved the clock, and nothing else contributes to it.
 *
 * Note this is a *within-session* comparison, and that is an honest limit rather than a complete
 * implementation of SPEC §5.8. The full version binds the baseline to a `bootId` minted at
 * `BOOT_COMPLETED` and persisted, so a jump across a reboot is also visible; that receiver and its
 * device-protected store belong to the escalation agent's `schedule.db`, not to the camera. What is
 * here covers the interval that actually carries the nonce — mint to shutter — which is the interval
 * the audit's unpredictability is spent over, and it needs no persistence to be correct.
 */
fun clockJumped(
    baseline: ClockBaseline,
    wallNow: Long,
    elapsedNow: Long,
    toleranceMs: Long = CLOCK_SKEW_TOLERANCE_MS,
): Boolean {
    val wallDelta = wallNow - baseline.wall
    val elapsedDelta = elapsedNow - baseline.elapsed
    // A negative elapsed delta means the device rebooted mid-challenge: elapsedRealtime restarted at
    // zero. That is a void as well, and it is emphatically not a lie — his phone died.
    if (elapsedDelta < 0) return true
    val skew = wallDelta - elapsedDelta
    return kotlin.math.abs(skew) > toleranceMs
}
