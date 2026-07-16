package com.secondspine.app.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondspine.coach.Register
import kotlinx.coroutines.delay

/**
 * THE COMPONENT KIT.
 *
 * These are the pieces every screen in the product is assembled from, and the reason they live in
 * `theme/` rather than in a feature package is that the *rules* are the components. "Never more than
 * one demand visible at a time" is not enforceable in a style guide; it is enforceable by shipping a
 * [DemandCard] that takes a single nullable `Demand` and no list. "Break glass is never confirmed"
 * survives exactly as long as [BreakGlassButton] has no `onConfirm` parameter to add.
 *
 * So the API surfaces here are narrow on purpose. Every parameter that does not exist is a defect
 * that cannot be introduced at 1am.
 */

// ---------------------------------------------------------------------------
// RIP SPEAKS
// ---------------------------------------------------------------------------

/**
 * RIP SPEECH — one component, five registers, and the register does *all* the work.
 *
 * This is the single highest-leverage composable in the app. `Voice.kt` already decided *what* he
 * says and *which register* it is in, as a pure function of the odometer; this decides what that
 * costs the reader. The mapping is not decorative:
 *
 *  - **ARENA** (44sp gold, worn tape): rationed to `0.10 × j` and hard-capped at 3/week. It is
 *    allowed to be this big precisely because it is nearly never allowed to happen.
 *  - **PITCHMAN** (28sp gold): the warm register, and the default shape of the man.
 *  - **BIT** (22sp paper): where most of the comedy lives. Notice it is *not* the loudest — comedy
 *    that shouts is a catchphrase, and a catchphrase has a half-life of about nine days.
 *  - **GHOST** (20sp, washed out, heavy tracking): rises as `0.10 × (4 - j)`. The tape failing is the
 *    performance failing. He never says he is losing; the picture says it.
 *  - **DISAPPOINTED** (13sp grey, **no tape at all**): fires 0–3 times in ten months (RESOLUTIONS
 *    §A2). The absence of distortion is the point — it is the only time the medium stops interfering,
 *    which reads as the man stepping out from behind it.
 *
 * By month six the same fragment bank is delivering an inverted meaning at zero authored cost,
 * because loud has become rare and rare reads as retreat. That inversion is bought entirely here.
 */
@Composable
fun RipSpeech(
    text: String,
    register: Register,
    modifier: Modifier = Modifier,
) {
    val style = when (register) {
        Register.ARENA -> ArenaStyle
        Register.PITCHMAN -> PitchmanStyle
        Register.BIT -> BitStyle
        Register.GHOST -> GhostStyle
        Register.DISAPPOINTED -> DisappointedStyle
    }
    val color = when (register) {
        Register.ARENA, Register.PITCHMAN -> Gold
        Register.BIT -> Paper
        Register.GHOST -> PaperDim
        Register.DISAPPOINTED -> PaperDim
    }
    val wear = when (register) {
        Register.GHOST -> TapeWear.Heavy
        Register.ARENA, Register.PITCHMAN, Register.BIT -> TapeWear.Worn
        // He has stopped performing. The tape stops with him.
        Register.DISAPPOINTED -> TapeWear.None
    }
    val display = if (register == Register.ARENA) text.uppercase() else text

    AnimatedContent(
        targetState = display,
        transitionSpec = {
            // No fade. He arrives. SPEC §4.9: nothing fades; everything slams or slides.
            slideInHorizontally(Motion.SlamOffset) { -Motion.SLIDE_PX } togetherWith
                slideOutHorizontally(Motion.SlamOffset) { Motion.SLIDE_PX / 2 }
        },
        modifier = modifier,
        label = "rip-speech",
    ) { line ->
        Text(
            text = line,
            style = style,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .vhsTracking(wear, seed = 0.33f)
                .alpha(if (register == Register.GHOST) 0.62f else 1f),
        )
    }
}

/**
 * RIP'S FACE — no arms, no eyes, and one camera.
 *
 * Drawn rather than animated from assets, for a reason that outlives the art budget: at
 * `jurisdiction = 0` he is a 40px face, and at `jurisdiction = 4` he owns 59% of the screen. A raster
 * asset would need two art passes and would still be soft at one of those sizes. A 2px stroke is
 * exactly 2px at both, which is the whole reason the icon set is stroked too.
 *
 * The features are deliberately impoverished. He is an outline, an aperture where the eyes should
 * be, and a mouth. That is all the character model there is, and it is enough, because everything
 * anyone will ever believe about this man arrives through `Voice.kt` and not through a jawline. The
 * mouth is quantised to 12 fps against the 60 fps UI — SPEC §4.3's "frame-rate contrast *is* the
 * aesthetic thesis, rendered".
 *
 * @param speaking drives the mouth. When false he is simply *there*, which at low jurisdiction is
 *   most of what he does.
 */
@Composable
fun RipFace(
    modifier: Modifier = Modifier,
    speaking: Boolean = false,
    jurisdiction: Int = 2,
) {
    val transition = rememberInfiniteTransition(label = "rip-face")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mouth",
    )

    // He fades with his jurisdiction. Not to nothing — jurisdictionShare never reaches 0, and neither
    // does he. He just stops being the point.
    val ink = when {
        jurisdiction >= 3 -> Gold
        jurisdiction >= 1 -> GoldDim
        else -> GoldGhost
    }

    Box(modifier.vhsTracking(TapeWear.Worn, seed = 0.11f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val unit = minOf(w, h)
            val stroke = Stroke(width = (unit / 24f).coerceIn(1.5f, 4f))
            val cx = w / 2f
            val cy = h / 2f
            val r = unit * 0.36f

            // The head. An oval, because a circle is a smiley and this man is not smiling.
            drawOval(
                color = ink,
                topLeft = Offset(cx - r * 0.82f, cy - r),
                size = Size(r * 1.64f, r * 2f),
                style = stroke,
            )

            // THE EYE. Singular, central, mechanical. He does not have eyes; he has your camera, and
            // the only thing he can do with it is wait for you to point it at something true.
            val lensR = r * 0.30f
            drawCircle(color = ink, radius = lensR, center = Offset(cx, cy - r * 0.18f), style = stroke)
            drawCircle(color = ink, radius = lensR * 0.42f, center = Offset(cx, cy - r * 0.18f), style = stroke)

            // The mouth, at 12 fps. Four phonemes, no lip-sync, no phoneme model — a tape from 1994
            // did not have one either.
            val frame = if (speaking) quantiseToTape(phase, 4) else 0
            val mouthY = cy + r * 0.46f
            val mouthW = r * 0.62f
            val open = when (frame) { 0 -> 0.06f; 1 -> 0.34f; 2 -> 0.14f; else -> 0.44f } * r
            drawOval(
                color = ink,
                topLeft = Offset(cx - mouthW / 2f, mouthY - open / 2f),
                size = Size(mouthW, open.coerceAtLeast(unit / 22f)),
                style = stroke,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// THE DEMAND — and there is only ever one
// ---------------------------------------------------------------------------

/**
 * THE DEMAND CARD. **One. Never two.** (SPEC §4.3.)
 *
 * The type signature is the guardrail: this takes a `String?`, not a `List<String>`. There is no
 * arrangement of this API that renders two demands, which means "multiple simultaneous demands turns
 * the coach into a to-do list, and to-do lists die" is enforced by the compiler rather than by
 * whoever is reviewing the PR that adds the second one.
 *
 * There is also no countdown, and that absence is clinical rather than aesthetic. Audit windows are
 * hours, not minutes; a ticking clock on a demand manufactures unearned failures on a habit the user
 * actually performed, and an app that calls you a failure on a day you succeeded gets uninstalled
 * that evening and deserves to.
 *
 * @param demand the single highest-priority open obligation, or null — in which case the caller
 *   shows the Floor, never an empty card.
 */
@Composable
fun DemandCard(
    demand: String,
    onProof: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enter = remember { Animatable(1f) }
    LaunchedEffect(demand) {
        enter.snapTo(0f)
        enter.animateTo(1f, Motion.Slam)
    }

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Slams in from the right and settles. No fade — alpha stays at 1 the whole way.
                translationX = (1f - enter.value) * Motion.SLIDE_PX
            }
            .clip(RoundedCornerShape(2.dp))
            .background(InkRaised)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
            .vhsTracking(TapeWear.Worn, seed = 0.57f)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = demand.uppercase(),
            style = DemandStyle,
            // Gold: this is him asking, not the app asking. The app never demands anything.
            color = Gold,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        ProofButton(onProof)
    }
}

/**
 * THE SHUTTER. SPEC §4.4's signature moment, and the only button in the app that matters.
 *
 * It slams on press — scale to 0.90 on the house spring — because the shot has to feel like a
 * *mechanism* closing, not a link being followed. One haptic, fired on press rather than on release,
 * for the same reason a real shutter is: the confirmation belongs to the moment of the act.
 *
 * No loading state, no spinner, and that is doctrine rather than an omission. SPEC §4.4 spends the
 * inference *before* the shutter, so the verdict already exists when the finger lands. A spinner
 * here would be the app admitting it is thinking about whether to believe you, which is the exact
 * feeling the zero-assertion law exists to prevent.
 */
@Composable
fun ProofButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, Motion.Slam, label = "shutter")
    val haptics = LocalHapticFeedback.current

    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(percent = 50))
            .border(BorderStroke(2.dp, Gold), RoundedCornerShape(percent = 50))
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SsIcons.Shutter,
            contentDescription = "Take the shot",
            tint = Gold,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

// ---------------------------------------------------------------------------
// THE LEDGER — the only slow thing in the app
// ---------------------------------------------------------------------------

/**
 * THE LEDGER COUNTER. A split-flap board, and it is slow on purpose.
 *
 * Everything else in this product resolves in 400 ms of stiff spring. This takes up to 1.4 seconds
 * and rolls through every intermediate digit at 90 ms a flap, and the delay is the entire mechanism:
 * you cannot help but watch a number land. It is the one moment the app asks for your attention
 * rather than demanding it, which is why it is also the one moment it is allowed to take its time.
 *
 * It flaps *down* from the old value to the new even when the new value is smaller, because a real
 * board only turns one way. That detail costs nothing and is most of why it reads as a mechanism
 * rather than as a text field with an animation on it.
 *
 * The number it shows is never older than 28 days. `Ledger.kt` hard-deletes at the boundary, without
 * a carve-out for repeat offences, so this counter is structurally incapable of holding January
 * against you. The tape degrades. That is not leniency — it is the character.
 */
@Composable
fun LedgerCounter(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SplitFlapNumber(value)
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MonoCaptionStyle,
            color = PaperFaint,
            textAlign = TextAlign.Center,
        )
    }
}

/** The flap stack itself. Two digits: the docket has never needed three in 28 days, and if it does, the user has bigger problems than kerning. */
@Composable
private fun SplitFlapNumber(target: Int) {
    var shown by remember { mutableIntStateOf(target) }
    val flip = remember { Animatable(0f) }

    LaunchedEffect(target) {
        // Roll, one flap at a time, the way the board would. Not a tween to the answer.
        while (shown != target) {
            flip.snapTo(0f)
            flip.animateTo(1f, tween(Motion.FLAP_MS, easing = LinearEasing))
            shown = if (shown < target) shown + 1 else shown - 1
            delay(20)
        }
        flip.snapTo(0f)
    }

    Box(
        Modifier
            .widthIn(min = 54.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(InkSunken)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shown.toString().padStart(2, '0'),
            style = NumberStyle,
            // Paper, never red. The docket is a measurement, not an accusation — even the row that
            // says CAUGHT FAKE. Red here would make the Ledger a scoreboard of shame, and a
            // scoreboard of shame is rumination infrastructure with a purge on it.
            color = Paper,
            modifier = Modifier.graphicsLayer {
                // The flap: the glyph compresses vertically as the leaf turns through horizontal.
                scaleY = 1f - (flip.value * 0.85f)
            },
        )
        // The seam. One hairline across the middle of the box is what makes this read as a board.
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = Ink,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5f,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// THE TWO BUTTONS THAT ARE NEVER PRICED
// ---------------------------------------------------------------------------

/**
 * BREAK GLASS. One tap. Instant. Always works. Never confirmed. Never mocked. Never counted.
 *
 * Read the signature: `onBreak` and a modifier. There is no `onConfirm`, no `enabled`, no
 * `confirmationText`, no `analytics` hook. That is the design — every one of those parameters is a
 * place where somebody, reasonably, at some point, adds an "are you sure?" to a control whose entire
 * value is that it never asks. Precommitment is only ethical while the exit is free, and an exit
 * with a dialog on it is not free.
 *
 * It is grey. Not red. A red emergency control is a control the app is proud of, and a user glancing
 * at a red button forty times a day starts reading it as an accusation. Grey, bottom-left, present
 * on every surface including from inside the lock, and never once mentioned by the character — not
 * in the moment, not on Sunday, not ever.
 */
@Composable
fun BreakGlassButton(
    onBreak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onBreak()
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SsIcons.BreakGlass, contentDescription = null, tint = PaperDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("BREAK GLASS", style = LabelStyle, color = PaperDim)
    }
}

/**
 * FOR THE RECORD. Free, unlimited, always visible, warm, and it never demotes.
 *
 * This button is the load-bearing wall of the entire product. RESOLUTIONS §A1: confessed days leave
 * the compliance ratio *entirely*, so honesty strictly dominates deception at every hour, for every
 * user, forever — but only if the button is actually reachable in the second the temptation lands.
 * A confession control behind a menu is a confession control that loses to the fake at 11pm.
 *
 * So it is persistent, bottom, on the home surface, at full width, and never counted down, never
 * priced, never rationed, and never rendered as an admission. Paper, not red. The tap is warm.
 */
@Composable
fun ForTheRecordButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SsIcons.ForTheRecord, contentDescription = null, tint = Paper, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("FOR THE RECORD", style = LabelStyle, color = Paper)
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

/** A panel. Raised ink, one hairline, 2dp of radius. Chunky, square-ish — 1994 had no border-radius. */
@Composable
fun SsPanel(
    modifier: Modifier = Modifier,
    wear: TapeWear = TapeWear.None,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(InkRaised)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
            .vhsTracking(wear, seed = 0.71f),
    ) { content() }
}

/** A section header. UI type, tracked wide, always the app's voice — never gold, never his. */
@Composable
fun SsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = LabelStyle, color = PaperFaint, modifier = modifier)
}

/**
 * A flat, 2px-stroke chip. Used for `IT'S REAL`, `THAT'S MY NEW MUG`, `ONE SET`, and the ladder rows.
 *
 * Slides up on first composition rather than fading in — see the file header. `enabled` exists here
 * and nowhere near [BreakGlassButton], which is the distinction the whole safety floor rests on.
 */
@Composable
fun SsChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Paper,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, Motion.Slam) }
    Row(
        modifier
            .graphicsLayer { translationY = (1f - enter.value) * 24f }
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(2.dp, tint.copy(alpha = 0.35f)), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = LabelStyle, color = tint)
    }
}

/**
 * The mono footer. `LAST EXPORT: 4 DAYS AGO · Documents/SecondSpine` (SPEC §4.10).
 *
 * Goes red — one of the only places [VerdictRed] is ever spent — when the export has not run in 14
 * days. The app failing loudly about *its own* broken promise is the correct use of the alarm
 * colour, and it is a good reason to have kept it scarce everywhere else: the user has never seen
 * this colour used to score them, so when it appears they read it as information rather than as
 * judgement. The app must never hold the archive hostage, and this line is how it proves it hasn't.
 */
@Composable
fun ExportFooter(daysSinceExport: Int?, modifier: Modifier = Modifier) {
    val stale = daysSinceExport == null || daysSinceExport >= 14
    val text = when (daysSinceExport) {
        null -> "LAST EXPORT: NEVER · EXPORT IS BROKEN"
        0 -> "LAST EXPORT: TODAY · Documents/SecondSpine"
        1 -> "LAST EXPORT: 1 DAY AGO · Documents/SecondSpine"
        else -> "LAST EXPORT: $daysSinceExport DAYS AGO · Documents/SecondSpine"
    }
    Text(
        text = text,
        style = MonoCaptionStyle,
        color = if (stale) VerdictRed else PaperFaint,
        modifier = modifier,
    )
}

/**
 * The slide the whole app enters on. No fade, ever — see `Motion.kt`.
 *
 * Exposed as a component rather than as a raw call so that "everything slams or slides" is something
 * screens *inherit* instead of something they each have to remember.
 */
@Composable
fun SlamIn(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.SlamOffset) { Motion.SLIDE_PX },
        exit = slideOutVertically(Motion.SlamOffset) { Motion.SLIDE_PX },
        modifier = modifier,
    ) { content() }
}
