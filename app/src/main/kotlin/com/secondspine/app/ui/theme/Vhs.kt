package com.secondspine.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.abs
import kotlin.random.Random

/**
 * THE TAPE — the 1994 layer, and the reason this app has to be built with more craft than its
 * content deserves.
 *
 * Every one of these effects is a cliché. Scanlines, grain, tracking error, head-switching noise —
 * they are the default sticker pack of "retro" and they are 90% of why "VHS aesthetic" usually
 * arrives looking like a joke app. What separates this from that is entirely a matter of *restraint
 * and correctness*:
 *
 *  - The grain is **4%** (SPEC §4.9), not 20%. At 4% you cannot see it; you can only see that the
 *    black is expensive. Above ~8% it stops being a film stock and starts being a filter.
 *  - The grain **moves at 12 fps**, not 60. Static grain is a texture — a sticker. Moving grain is a
 *    *medium*. This is the single cheapest line in the file and it does the most work.
 *  - The scanlines are **black at 5%**, never white, and never animated. White scanlines are a CRT;
 *    black scanlines are a tape, and a tape is the thing he is trapped inside.
 *  - The tracking bar fires on a **7-second** period and takes ~80 ms to cross. It is atmosphere. A
 *    tracking bar you can time is a progress indicator, and users will start reading it as one.
 *
 * The whole layer is also *never* applied to text the user has to act on. Distorting an affordance
 * is where retro turns into contempt for the reader. It goes on him, on chrome, and on the ground.
 */

/**
 * The noise plate. Generated once, tiled forever.
 *
 * Seeded at 1994 because a fixed seed makes this deterministic across processes and screenshots — a
 * regenerated grain plate would make every UI test flake, and grain is not worth a flaky suite.
 * 96×96 tiles without a visible repeat at any sane density and costs ~36 KB of heap.
 */
private const val NOISE_TILE = 96
private const val NOISE_SEED = 1994

@Composable
private fun rememberNoisePlate(): ImageBitmap = remember {
    val bmp = ImageBitmap(NOISE_TILE, NOISE_TILE)
    val canvas = Canvas(bmp)
    val paint = Paint()
    val rnd = Random(NOISE_SEED)
    for (y in 0 until NOISE_TILE) {
        for (x in 0 until NOISE_TILE) {
            val v = rnd.nextFloat()
            // Sparse and weak: only ~1 pixel in 7 is lit at all, and never above 22% alpha. This is
            // what 4% average grain actually looks like when you build it from discrete grains
            // rather than from a flat translucent wash (which reads as fog, not film).
            if (v > 0.86f) {
                paint.color = Color.White.copy(alpha = (v - 0.86f) * 1.6f)
                canvas.drawRect(
                    left = x.toFloat(),
                    top = y.toFloat(),
                    right = x + 1f,
                    bottom = y + 1f,
                    paint = paint,
                )
            }
        }
    }
    bmp
}

/**
 * How hard the tape is running.
 *
 * This is not decoration control — it is *narrative* control. GHOST gets [Heavy] because the
 * degradation is the register: he is a man whose recording is failing, and the picture failing with
 * him is the only way that lands without him having to say so. UI chrome gets [None] because
 * distorting a button is a different app.
 */
enum class TapeWear(
    internal val grainAlpha: Float,
    internal val scanAlpha: Float,
    internal val trackingAlpha: Float,
) {
    /** Nothing. Affordances, numbers, the safety break, the contract. */
    None(0f, 0f, 0f),

    /** SPEC §4.9's 4% grain and a whisper of line structure. The default ground for the whole app. */
    Stock(0.04f, 0.05f, 0.10f),

    /** Rip at work. The face, the speech, the demand card's frame. */
    Worn(0.07f, 0.09f, 0.35f),

    /** GHOST. The tape is losing him. */
    Heavy(0.11f, 0.14f, 0.75f),
}

/**
 * Drive the tape from a single shared clock so every surface degrades *together*.
 *
 * If each element ran its own infinite transition the grain would shimmer out of phase across the
 * screen and read as rendering noise rather than as one medium. One clock, one tape.
 */
@Composable
private fun rememberTapeClock(): Pair<State<Float>, State<Float>> {
    val transition = rememberInfiniteTransition(label = "tape")
    val grainPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // One full grain cycle per second, quantised to 12 steps at draw time. 60 fps grain is
            // digital sensor noise; 12 fps grain is film.
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "grain",
    )
    val trackingPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.TRACKING_SWEEP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tracking",
    )
    return grainPhase to trackingPhase
}

/**
 * THE VHS MODIFIER. Put it on anything that is supposed to be *footage* rather than *interface*.
 *
 * Draws over its content, never under: the tape is a thing that happened to the picture after the
 * picture existed, which is also the whole premise of the character.
 *
 * @param wear how far gone the tape is on this surface.
 * @param seed offsets the tracking sweep so two surfaces on screen do not tear in lockstep, which
 *   would read as a screen-wide glitch (a bug) rather than as two bits of tape (an aesthetic).
 */
fun Modifier.vhsTracking(
    wear: TapeWear = TapeWear.Stock,
    seed: Float = 0f,
): Modifier = composed {
    if (wear == TapeWear.None) return@composed Modifier

    val noise = rememberNoisePlate()
    val (grainPhase, trackingPhase) = rememberTapeClock()
    val grain by grainPhase
    val tracking by trackingPhase

    val grainBrush = remember(noise) {
        ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
    }

    drawWithContent {
        drawContent()

        // --- 1. SCANLINES. Black, 5%, every 3px, static. A tape, not a CRT. ---------------------
        if (wear.scanAlpha > 0f) {
            val step = 3f
            var y = 0f
            while (y < size.height) {
                drawRect(
                    color = Color.Black.copy(alpha = wear.scanAlpha),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, 1f),
                )
                y += step
            }
        }

        // --- 2. GRAIN. Tiled plate, jumped to a new position 12 times a second. -----------------
        // The jump is the point. Translating the shader is free; regenerating noise is not, and the
        // eye cannot tell the difference between new grain and the same grain 40px to the left.
        if (wear.grainAlpha > 0f) {
            val frame = quantiseToTape(grain)
            val jx = (frame * 37 % NOISE_TILE).toFloat()
            val jy = (frame * 53 % NOISE_TILE).toFloat()
            translate(left = -jx, top = -jy) {
                drawRect(
                    brush = grainBrush,
                    topLeft = Offset.Zero,
                    size = Size(size.width + NOISE_TILE, size.height + NOISE_TILE),
                    alpha = wear.grainAlpha * 2.5f,
                )
            }
        }

        // --- 3. THE TRACKING BAR. One sweep every 7s, ~80ms of travel, then gone. ---------------
        if (wear.trackingAlpha > 0f) {
            val phase = (tracking + seed) % 1f
            // Live for the first 12% of the period; dead for the other 88%. Restraint is the effect.
            if (phase < 0.12f) {
                val travel = phase / 0.12f
                val bandHeight = size.height * 0.06f
                val y = (size.height + bandHeight) * (1f - travel) - bandHeight
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to TrackingEdge.copy(alpha = TrackingEdge.alpha * wear.trackingAlpha),
                        0.5f to Color.White.copy(alpha = 0.10f * wear.trackingAlpha),
                        0.6f to TrackingEdge.copy(alpha = TrackingEdge.alpha * wear.trackingAlpha),
                        1f to Color.Transparent,
                        startY = y,
                        endY = y + bandHeight,
                    ),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, bandHeight),
                )
                // Head-switching noise: two torn slivers riding the band. Deterministic per frame so
                // the tear is stable while it crosses instead of boiling.
                val tearRnd = Random((phase * 512).toInt())
                repeat(2) {
                    val sy = y + tearRnd.nextFloat() * bandHeight
                    val sx = (tearRnd.nextFloat() - 0.5f) * size.width * 0.25f
                    drawRect(
                        color = Color.White.copy(alpha = 0.06f * wear.trackingAlpha),
                        topLeft = Offset(abs(sx), sy),
                        size = Size(size.width * (0.3f + tearRnd.nextFloat() * 0.5f), 1.5f),
                    )
                }
            }
        }
    }
}

/**
 * The ground. `Ink` plus permanent [TapeWear.Stock] — 4% grain over near-black.
 *
 * This is on the root of every screen, which is why the app reads as *expensive* rather than as
 * *unfinished*. A flat #0B0C0E fills the screen with nothing; the same colour with 4% of moving
 * grain fills it with a surface. It is the cheapest premium signal available and it costs one
 * tiled shader.
 */
fun Modifier.tapeGround(): Modifier = vhsTracking(TapeWear.Stock, seed = 0f)
