package com.secondspine.app.ui.proof

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.DemandStyle
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.vhsTracking
import kotlinx.coroutines.delay

/**
 * THE TRIBUNAL — SPEC §4.4's three stamps, and the app's signature 1.2 seconds.
 *
 * ## What the stamps say, and why it is funny rather than false
 *
 * Read the three stamps as a set: the capture happened, at this time, and it is **REAL**. That last
 * one is the joke and the thesis in the same word. *Everything* is real. A photograph of a wall gets
 * `REAL`. A photograph of the ceiling gets `REAL`. There is no branch in this file that renders
 * anything else, because there is no branch anywhere in the product that could produce one — LAW 1:
 * **no proof is ever rejected**. The stamp is not the app's opinion of the photograph; it is the app
 * keeping its promise, in 44sp, with a bang.
 *
 * That is why the ceremony is affordable. A verdict animation on a system that might say `FAKE` is a
 * tribunal you have to *watch*, and it converts the shutter into a moment of risk. A verdict
 * animation on a system that has pre-committed to `REAL` is a reward, and the user learns the shape
 * of it in about four days — after which it is a 1.2-second stamp of approval he never has to earn
 * twice.
 *
 * ## Why the ceremony is rationed
 *
 * SPEC §4.4: *"Ceremony is rationed by rarity or it becomes latency. The Tribunal is gorgeous for 50
 * views and a load screen for the next four thousand."* And RESOLUTIONS §A3 made it enforceable by
 * fixing the unit: ceremony is **wall-clock seconds of demanded attention**, from every source, not
 * "initiated conversations". So the caller passes `ceremony = false` on the 51st ordinary proof and
 * this collapses to one stamp and ~200 ms — see [VerdictOverlay].
 *
 * ## Motion
 *
 * Nothing fades. The stamps arrive at scale 1.55 and slam to 1.0 on the house spring, rotated a few
 * degrees off-axis like a real rubber stamp hit by a tired hand. Alpha is never animated — a stamp
 * that fades in is a stamp that is unsure, and the one thing this moment is not is unsure.
 */
@Composable
fun VerdictOverlay(
    stamps: List<VerdictStampSpec>,
    ceremony: Boolean,
    modifier: Modifier = Modifier,
    onSettled: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var landed by remember(stamps) { mutableIntStateOf(0) }

    LaunchedEffect(stamps, ceremony) {
        landed = 0
        if (!ceremony) {
            // THE FAST PATH. SPEC §4.4: 200 ms, one sharp haptic, back to the ARENA. By proof #51 the
            // ceremony has done its job and every millisecond after this is rent.
            landed = 1
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(FAST_PATH_MS)
            onSettled()
            return@LaunchedEffect
        }
        for (i in stamps.indices) {
            landed = i + 1
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(STAMP_INTERVAL_MS)
        }
        delay(SETTLE_MS)
        onSettled()
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // On the fast path only the last stamp — the verdict itself — is rendered. The other two
            // are evidence, and evidence is for the days he is still learning that this always works.
            val visible = if (ceremony) stamps.take(landed) else stamps.takeLast(1).take(landed)
            visible.forEachIndexed { index, spec ->
                Stamp(spec = spec, key = index)
            }
        }
    }
}

/**
 * One stamp.
 *
 * @param gold gold means the character is talking (`Color.kt`'s ownership split: the app owns cyan,
 *   Rip owns gold). `REAL` is his word, so `REAL` is gold. The evidence stamps are paper, because the
 *   time on the clock is not his opinion — it is just true.
 */
data class VerdictStampSpec(
    val text: String,
    val gold: Boolean = false,
    /** Degrees off-axis. A stamp hit by a hand is never square, and never twice at the same angle. */
    val tilt: Float = -4f,
)

@Composable
private fun Stamp(spec: VerdictStampSpec, key: Int) {
    val slam = remember(key, spec.text) { Animatable(SLAM_FROM) }
    LaunchedEffect(key, spec.text) { slam.animateTo(1f, Motion.Slam) }

    val ink: Color = if (spec.gold) Gold else Paper
    Box(
        Modifier
            .graphicsLayer {
                scaleX = slam.value
                scaleY = slam.value
                rotationZ = spec.tilt
            }
            .border(BorderStroke(2.dp, ink), RoundedCornerShape(2.dp))
            .vhsTracking(TapeWear.Worn, seed = 0.19f + key * 0.13f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = spec.text.uppercase(), style = DemandStyle, color = ink)
    }
}

/** Where a stamp starts. Big, so it lands *down* onto the page like a stamp rather than growing like a bubble. */
private const val SLAM_FROM = 1.55f

/** SPEC §4.4: the Tribunal is 1.2 s. Three stamps at 380 ms plus a beat to read it. */
private const val STAMP_INTERVAL_MS = 380L
private const val SETTLE_MS = 460L

/** SPEC §4.4: "PASS — default: 200 ms." */
private const val FAST_PATH_MS = 200L
