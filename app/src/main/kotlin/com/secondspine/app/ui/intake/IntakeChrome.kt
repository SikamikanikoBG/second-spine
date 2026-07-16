package com.secondspine.app.ui.intake

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Cyan
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.InkRaised
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.NumberStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.vhsTracking

/**
 * THE WIZARD'S CHROME.
 *
 * Everything here is assembled from `ui/theme` and adds exactly three things the kit does not have,
 * because they exist nowhere else in the product: a step scaffold, a binary answer row, and a time
 * stepper. Nothing here reimplements a component that already exists — the demand card, the shutter,
 * break glass, the ledger and the speech bubble are all consumed as-is, and a fourth variant of any
 * of them invented in a feature package would be exactly the drift the kit exists to prevent.
 *
 * **The selection colour in this package is [Cyan], everywhere, without exception.** `Color.kt`
 * splits ownership: the app owns cyan (chrome, affordance, focus) and the character owns gold. A
 * selected answer is the *app* registering a choice, so it is cyan. It is never gold — gold means Rip
 * is talking, and if his colour appears on a SCOFF item then he is in the room during a clinical
 * screen, which is the one thing that screen forbids. It is obviously never green.
 */

// ---------------------------------------------------------------------------
// The step scaffold
// ---------------------------------------------------------------------------

/**
 * One wizard step: a scrolling body, a mono step counter, and a bar at the bottom.
 *
 * The counter is two mono glyphs in the corner, and that is the whole progress indicator. A segmented
 * progress bar would be honest and would also be the single strongest "you are filling in a form"
 * signal available — the eye reads a progress bar as *work remaining*. Mono digits read as a tape
 * counter on a machine, which is what the app would like this to be, and they cost 11sp.
 *
 * @param wear the tape. Rip's steps run [TapeWear.Stock]; the clinical screens and the character
 *   break run [TapeWear.None], and that difference is the loudest thing on those screens.
 */
@Composable
fun IntakeStep(
    step: Int,
    label: String,
    modifier: Modifier = Modifier,
    wear: TapeWear = TapeWear.Stock,
    bar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            .vhsTracking(wear, seed = 0.19f)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SsSectionLabel(label)
                Text(
                    text = "${step.toString().padStart(2, '0')}/${TOTAL_STEPS.toString().padStart(2, '0')}",
                    style = MonoCaptionStyle,
                    color = PaperFaint,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            ) {
                content()
                Spacer(Modifier.height(28.dp))
            }

            if (bar != null) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) { bar() }
            }
        }
    }
}

/** Seven. The step counter is honest about the length because four minutes is a length worth owning. */
const val TOTAL_STEPS: Int = 7

// ---------------------------------------------------------------------------
// The one affordance shape
// ---------------------------------------------------------------------------

/**
 * The wizard's continue bar. Full width, 2px stroke, slams in.
 *
 * There is no disabled state and there is no `enabled` parameter, which is the same trick
 * `BreakGlassButton` uses in reverse: a control that cannot be greyed out is a control nobody can
 * grey out. When the step is not answered the caller does not render this at all, and when the last
 * answer lands the bar *arrives* — a hard slide up from below, on the house spring. The affordance
 * appearing is the feedback. A greyed-out button that ungreys is the same information delivered as
 * a scold.
 */
@Composable
fun IntakeBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Paper,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, Motion.Slam) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (1f - enter.value) * Motion.SLIDE_PX
                val s = if (pressed) 0.97f else 1f
                scaleX = s; scaleY = s
            }
            .clip(RoundedCornerShape(2.dp))
            .background(if (pressed) InkRaised else Color.Transparent)
            .border(BorderStroke(2.dp, tint), RoundedCornerShape(2.dp))
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = LabelStyle, color = tint)
    }
}

// ---------------------------------------------------------------------------
// The clinical answer row
// ---------------------------------------------------------------------------

/**
 * One instrument item and its two answers.
 *
 * The item text is [Paper] at body weight and it is never truncated, never behind a "read more",
 * never abbreviated to fit. `Instruments.kt` owns why.
 *
 * YES and NO are the same size, the same weight, and in the same order every time. The design
 * temptation here is to make NO the quiet, easy, default-looking one — it is the answer the app
 * "wants" on a PAR-Q+ — and that is how you build an instrument that measures how much the user
 * wanted to get to the next screen.
 */
@Composable
fun ClinicalItem(
    index: Int,
    item: ScreenItem,
    answer: Boolean?,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MonoCaptionStyle,
                color = PaperFaint,
                modifier = Modifier.padding(end = 12.dp, top = 4.dp),
            )
            Column {
                Text(item.text, style = BodyStyle, color = Paper)
                if (item.hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(item.hint, style = BodyStyle.copy(fontSize = BodyStyle.fontSize * 0.86f), color = PaperDim)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(start = 30.dp)) {
            AnswerChip("YES", selected = answer == true) { onAnswer(true) }
            Spacer(Modifier.width(10.dp))
            AnswerChip("NO", selected = answer == false) { onAnswer(false) }
        }
    }
}

/** A binary answer. Cyan when chosen — the app's colour, because the app is the one asking. */
@Composable
private fun AnswerChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val tint = if (selected) Cyan else PaperDim
    Row(
        Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) InkRaised else Color.Transparent)
            .border(BorderStroke(2.dp, if (selected) Cyan else Hairline), RoundedCornerShape(2.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = LabelStyle, color = tint)
    }
}

/**
 * The result panel on a clinical screen. Flat, UI type, no character, no tape.
 *
 * [alarming] is false for every use in this package and the parameter exists to say so out loud:
 * neither a SCOFF-positive nor a PAR-Q+-positive result is a failure, and neither may ever be
 * rendered in [com.secondspine.app.ui.theme.VerdictRed]. `Color.kt` rations that colour to failure
 * alone, and telling a man the app will no longer weigh him — in the same colour it uses for a
 * missed set — would make the protection read as a punishment for answering honestly.
 */
@Composable
fun ClinicalResult(text: String, modifier: Modifier = Modifier, alarming: Boolean = false) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(text) { enter.snapTo(0f); enter.animateTo(1f, Motion.Slam) }
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = (1f - enter.value) * 32f }
            .clip(RoundedCornerShape(2.dp))
            .background(InkRaised)
            .border(
                BorderStroke(2.dp, if (alarming) Cyan else Hairline),
                RoundedCornerShape(2.dp),
            )
            .padding(18.dp),
    ) {
        Text(text, style = BodyStyle, color = Paper)
    }
}

/** The instrument's provenance, in mono. It is a measurement, so it is in the measurement font. */
@Composable
fun Attribution(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MonoCaptionStyle, color = PaperFaint, modifier = modifier)
}

// ---------------------------------------------------------------------------
// The time stepper
// ---------------------------------------------------------------------------

/**
 * A time, as a machine part.
 *
 * Not a Material `TimePicker`: that is a dialog, it is a Material tell, and it costs four taps and a
 * confirm to move something by fifteen minutes. This is two chevrons and a mono readout, it moves in
 * [STEP_MIN]-minute steps, it wraps at midnight, and the number is [NumberStyle] because every number
 * in this app is mono without exception (SPEC §4.9). Numbers in a proportional font are a claim;
 * numbers in mono are a measurement.
 */
@Composable
fun MinuteStepper(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SsSectionLabel(label)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepArrow(SsIcons.ChevronDown, up = true) { onChange(minutes - STEP_MIN) }
            Box(
                Modifier
                    .width(132.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(InkRaised)
                    .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(formatMinutes(minutes), style = NumberStyle, color = Paper)
            }
            StepArrow(SsIcons.ChevronDown, up = false) { onChange(minutes + STEP_MIN) }
        }
    }
}

@Composable
private fun StepArrow(icon: ImageVector, up: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(52.dp)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (up) "Earlier" else "Later",
            tint = if (pressed) Cyan else PaperDim,
            // One icon, two directions. The set is 24x24 and stroked at 2px; a second hand-drawn
            // chevron pointing the other way is a second chance to draw it 1.5px wide.
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = if (up) 180f else 0f },
        )
    }
}

/** 15 minutes. Fine enough to be honest about a bedtime, coarse enough to be two taps. */
const val STEP_MIN: Int = 15

/**
 * Minutes since local midnight -> `HH:MM`, 24-hour, always.
 *
 * No AM/PM and no locale-dependent formatter, on purpose: this string is a *measurement* of the same
 * ring the brain's `wrapMin` arithmetic runs on, and a 12-hour clock introduces the one ambiguity
 * that matters here — a wind-down window is the thing standing between the user and an alarm at 3am,
 * and "11:30" meaning either end of the day is not a risk worth a familiar format.
 */
fun formatMinutes(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}
