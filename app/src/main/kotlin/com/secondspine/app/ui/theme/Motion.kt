package com.secondspine.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * THE MOTION — SPEC §4.9: **nothing fades; everything slams or slides.**
 *
 * A 300 ms ease-in-out fade is the single most common tell of a template app, and it is worse than
 * generic here: a fade is a *hedge*. It is the interface being tentative about whether it should
 * appear. Rip is never tentative — he is a man with no arms who cannot leave, and his entire
 * physical vocabulary is arrival. So things arrive.
 *
 * The one exception is [LedgerSpec], and the exception is the design. See below.
 */
object Motion {

    /**
     * The house spring — SPEC §4.9's `spring(stiffness = 400, damping = 30)`.
     *
     * Compose parameterises springs as (dampingRatio, stiffness) rather than the raw damping
     * coefficient, so the spec's `damping = 30` is expressed as the ratio it produces: just under
     * critical, which lands with a single hard stop and a hint of overshoot. Not bouncy. Bouncy is
     * playful and this man is not playful, he is *desperate*, which reads as fast and slightly too
     * hard.
     */
    val Slam: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 400f,
    )

    /** The same spring, typed for offsets — entrances, the demand card, the lock. */
    val SlamOffset: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.72f,
        stiffness = 400f,
        visibilityThreshold = IntOffset(1, 1),
    )

    /** Chrome that must move without being noticed: chips, ladder rows, the fold. Stiffer, no overshoot. */
    val Snap: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 900f,
    )

    /**
     * THE ONLY SLOW THING IN THE APP (SPEC §4.3).
     *
     * The Ledger counter is slow because it is the one number that is allowed to feel like a
     * verdict, and because a split-flap board that resolves instantly is just a label. The delay is
     * the mechanism: you watch it land. Everything else in the product is 400 ms of stiffness
     * precisely so that this one 1.4-second flap is legible as deliberate rather than as jank.
     *
     * Linear, not eased, because real flap boards are driven by a constant-speed motor.
     */
    val LedgerSpec: AnimationSpec<Float> = tween(durationMillis = 1400, easing = LinearEasing)

    /** How long one flap takes. Twelve of these make a full digit roll. */
    const val FLAP_MS: Int = 90

    /**
     * CHARACTER FRAME RATE (SPEC §4.3): 12 fps against a 60 fps UI.
     *
     * "That frame-rate contrast *is* the aesthetic thesis, rendered." He is tape; the app is glass.
     * Quantising his animation to 12 fps costs nothing and does the work that a whole art budget
     * would otherwise be asked to do — the eye reads 12 fps as *archival footage* instantly, without
     * a single line of copy explaining that he is dead.
     */
    const val CHARACTER_FPS: Int = 12

    /** One character frame, in millis. */
    const val CHARACTER_FRAME_MS: Int = 1000 / CHARACTER_FPS

    /** Tracking-bar sweep period. Slow enough to be atmosphere, not a progress bar. */
    const val TRACKING_SWEEP_MS: Int = 7000

    /** The distance things slide in from. Far enough to read as arrival, near enough to stay fast. */
    const val SLIDE_PX: Int = 140
}

/**
 * Quantise a continuous 0..1 animation to [Motion.CHARACTER_FPS] steps.
 *
 * Everything the character does goes through this. It is one line and it is the difference between
 * "animated mascot" and "a man on a tape".
 */
fun quantiseToTape(progress: Float, framesPerLoop: Int = Motion.CHARACTER_FPS): Int =
    (progress.coerceIn(0f, 1f) * framesPerLoop).toInt().coerceIn(0, framesPerLoop - 1)
