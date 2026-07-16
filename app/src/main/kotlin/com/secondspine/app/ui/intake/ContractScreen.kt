package com.secondspine.app.ui.intake

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Cyan
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.InkRaised
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.NumberStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.SlamIn
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear

/**
 * STEP 6 — THE CONTRACT, AND THE ONE BREAK.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * THIS IS THE MORAL SPINE OF THE PRODUCT AND THE REASON IT IS ALLOWED TO EXIST
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * The psychology panel's finding is not a nicety, it is the load-bearing wall: **this app is
 * coercive, and coercion reliably destroys intrinsic motivation — except when it is a Ulysses
 * contract the user chose while sober.** That exception is the entire product. Everything downstream
 * of this screen — the alarms, the audits, the ninety-second lock, ten months of a man shouting about
 * your Tuesday — is either a commitment device or it is abuse, and the only thing that decides which
 * is what happens on this screen.
 *
 * A commitment device needs three properties, and every design decision below serves one of them:
 *
 *  1. **It is the user's own prior decision.** So the clauses are written in the first person, he
 *     initials each one individually, and he does it with his own initials rather than a checkbox.
 *     In month four, at 11pm, when the app is unbearable, the constraint has to land as *himself*,
 *     not as a boss. `Pipeline.kt`'s doc is exactly this: *"it puts attribution on the user's own
 *     prior commitment and his own data, which is the textbook shape of internalisation."*
 *
 *  2. **The coach is not the authority.** Clause 3. The contract graduates habits on measured
 *     evidence; Rip has no vote and can never reclaim jurisdiction. If he could, the user is taking
 *     orders from a character, and taking orders from a character is a boss with a costume on.
 *
 *  3. **The exit is free, and it stays free.** Clause 5, and it is the one that makes the other four
 *     ethical. *Precommitment is only ethical while the exit is free.* BREAK GLASS never has a
 *     confirmation. RETIRE RIP is in the menu on day one, before anything is earned. The 24-hour
 *     Ulysses delay never touches pausing, muting, standing down, exporting or uninstalling — a
 *     delay on the exit would convert the commitment device into a cage, in one line of code, and
 *     nobody would notice for a month.
 *
 * The screen is not legal cover. Nothing here is enforceable against anybody and it is not trying to
 * be. It is the artefact the user will remember, in his own initials, on the worst night of month
 * four — and it is the app binding *itself*, which is why the intro says so out loud.
 *
 * What is deliberately absent: any goal, target, weight, deadline or number he is promising to hit.
 * He is authorising a *process*, not swearing an *outcome*. An outcome he cannot control by Thursday
 * is exactly what this app refuses to score, so it would be obscene to open by making him sign for
 * one.
 */
@Composable
fun ContractScreen(
    state: IntakeState,
    onInitials: (String) -> Unit,
    onInitialClause: (String) -> Unit,
    onSign: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The signature flips the screen to the break. Same step, two phases: SPEC §4.8 puts the break
    // immediately after the contract, and a route boundary between them would let a back gesture land
    // between a signed contract and its own safety explanation.
    if (state.broke) {
        SafetyBreak(onContinue = onContinue, modifier = modifier)
        return
    }

    IntakeStep(
        step = 6,
        label = CONTRACT_TITLE,
        modifier = modifier,
        // The contract is a document, not footage. The tape does not run on it — the same reason it
        // does not run on the clinical screens, and the same reason it will not run on the break.
        wear = TapeWear.None,
        bar = {
            SlamIn(visible = state.contractSignable) {
                IntakeBar("$CONTRACT_SIGN — ${state.initials}", onSign)
            }
        },
    ) {
        Text(
            text = CONTRACT_TITLE,
            style = BodyStyle.copy(fontSize = BodyStyle.fontSize * 1.7f, lineHeight = BodyStyle.lineHeight * 1.5f),
            color = Paper,
        )
        Spacer(Modifier.height(10.dp))
        Text(CONTRACT_INTRO, style = BodyStyle, color = PaperDim)
        Spacer(Modifier.height(20.dp))

        InitialsField(state.initials, onInitials)

        Spacer(Modifier.height(24.dp))

        CONTRACT_CLAUSES.forEachIndexed { i, clause ->
            ClauseCard(
                index = i + 1,
                clause = clause,
                initials = state.initials,
                initialled = clause.id in state.initialled,
                onInitial = { onInitialClause(clause.id) },
            )
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(CONTRACT_FOOTER, style = MonoCaptionStyle, color = PaperFaint)
    }
}

/**
 * THE KEYBOARD'S ONE APPEARANCE IN THE ENTIRE WIZARD (SPEC §4.8).
 *
 * Two or three letters, and then it is gone for ten months. It is a [BasicTextField] rather than a
 * Material `TextField` because a Material text field arrives with a filled container, a floating
 * label and an indicator line, all of which are somebody else's design language showing through on
 * the one screen that must look like a document this app wrote.
 *
 * Initials rather than a checkbox, and this is the whole mechanism rather than a flourish: a checkbox
 * is something you clear; initials are something you *did*. The research on commitment devices is
 * unambiguous that the effortful, self-authored, physically-performed signature is what makes the
 * commitment stick to the self. Two letters is the cheapest version of that which is still real.
 */
@Composable
private fun InitialsField(
    initials: String,
    onInitials: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier) {
        SsSectionLabel(CONTRACT_INITIALS_LABEL)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(140.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(InkRaised)
                .border(
                    BorderStroke(2.dp, if (initials.isBlank()) Hairline else Cyan),
                    RoundedCornerShape(2.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = initials,
                onValueChange = onInitials,
                textStyle = NumberStyle.copy(color = Paper, textAlign = TextAlign.Center),
                singleLine = true,
                cursorBrush = SolidColor(Cyan),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (initials.isEmpty()) {
                        Text("—", style = NumberStyle, color = PaperFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    inner()
                },
            )
        }
    }
}

/**
 * One clause and its initial box.
 *
 * The clause body is [Paper] at body weight and it is never collapsed behind a "read more". The
 * entire value of this screen is that it was read, and a disclosure triangle is a design pattern for
 * text you have decided nobody will read.
 *
 * SPEC §4.8 asks for a serif body here, and this ships in the UI face instead. `Type.kt` defines
 * exactly three families, split by *who is speaking* — Rip's display, the app's UI, mono for every
 * number — and it is explicit that the UI face is also the face of the one character break. Adding a
 * fourth family for one screen would break that split for a texture, and the split is what makes the
 * break legible four seconds after this screen ends. The type system outranks the mood board.
 */
@Composable
private fun ClauseCard(
    index: Int,
    clause: Clause,
    initials: String,
    initialled: Boolean,
    onInitial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(InkRaised)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MonoCaptionStyle,
                color = PaperFaint,
            )
            Spacer(Modifier.width(10.dp))
            Text(clause.title, style = LabelStyle, color = Paper)
        }
        Spacer(Modifier.height(12.dp))
        Text(clause.body, style = BodyStyle, color = Paper)
        Spacer(Modifier.height(16.dp))
        InitialBox(initials = initials, initialled = initialled, onInitial = onInitial)
    }
}

/**
 * The initial box. Empty until tapped, then his letters slam into it and stay.
 *
 * There is no un-initial gesture and no toggle. You can re-type your initials at the top — that is
 * one field and it re-stamps every clause you have already initialled — but a clause you have marked
 * does not un-mark on a second tap. A signature you can take back with a tap is a checkbox that has
 * been dressed up, and the point of the gesture is that it is a thing you did.
 */
@Composable
private fun InitialBox(
    initials: String,
    initialled: Boolean,
    onInitial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val stamp = remember { Animatable(1f) }
    LaunchedEffect(initialled) {
        if (initialled) {
            // It lands. 1.35 -> 1.0 on the house spring: a stamp hitting paper, not a fade-in.
            stamp.snapTo(1.35f)
            stamp.animateTo(1f, Motion.Slam)
        }
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        if (!initialled && initials.isBlank()) {
            Text("INITIALS FIRST", style = MonoCaptionStyle, color = PaperFaint, modifier = Modifier.padding(end = 12.dp))
        }
        Box(
            Modifier
                .size(width = 88.dp, height = 46.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (initialled) Color.Transparent else InkRaised)
                .border(
                    BorderStroke(2.dp, if (initialled) Paper else Hairline),
                    RoundedCornerShape(2.dp),
                )
                .clickable(enabled = !initialled && initials.isNotBlank()) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onInitial()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (initialled) {
                Text(
                    text = initials,
                    style = NumberStyle.copy(fontSize = NumberStyle.fontSize * 0.7f),
                    // PAPER. Not cyan, not gold. These are the user's letters — the one mark on any
                    // screen in this app that belongs to neither the app nor the character.
                    color = Paper,
                    modifier = Modifier.graphicsLayer { scaleX = stamp.value; scaleY = stamp.value },
                )
            } else {
                Icon(
                    imageVector = SsIcons.Stamp,
                    contentDescription = "Initial this clause",
                    tint = if (initials.isBlank()) PaperFaint else PaperDim,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// THE BREAK
// ---------------------------------------------------------------------------

/**
 * **THE CHARACTER BREAKS. EXACTLY ONCE. THIS IS IT.**
 *
 * UI font. Flat. No gold. No display type. No face. No grain, no scanline, no tracking bar —
 * [TapeWear.None], and on this screen the absence of the medium is not a style choice, it is the
 * content. For four seconds the tape stops interfering, which reads as the man stepping out from
 * behind it, and every reader gets that without a word of explanation.
 *
 * Why this is worth a whole screen: an app that is 100% bit is a joke app, and a joke app's safety
 * text is a joke. The reader has spent ten minutes learning that everything here is a performance —
 * so the one paragraph that must not be discounted has to arrive from outside the performance, or it
 * arrives inside it. This single break buys the licence to be aggressive for the following ten
 * months, and it only works because it is never, ever repeated. Not a second sincere moment, not a
 * heartfelt Sunday, not an apology after a bad week.
 *
 * **This is not the DISAPPOINTED register and it must never be rendered through `RipSpeech`.**
 * RESOLUTIONS §A2 gives DISAPPOINTED the trigger enum `{CAUGHT_FAKE}` and 0–3 firings in ten months;
 * routing the break through it would put a register on this screen and make the break a *mode* he can
 * be in — which is precisely how a one-time break becomes a recurring device. It is drawn here with
 * the app's own `Text` and the app's own type, by hand, because the man is not performing and there
 * is no component for that.
 *
 * There is no timed gate on the continue button. SPEC calls this a four-second beat, meaning that is
 * how long it takes to read — not that the user must be held here for four seconds. Locking somebody
 * onto the screen that explains that the exit is never locked would be the funniest possible bug to
 * ship, and it would be the only line on the page that was false.
 */
@Composable
private fun SafetyBreak(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IntakeStep(
        step = 6,
        label = "",
        modifier = modifier,
        wear = TapeWear.None,
        bar = { IntakeBar(SAFETY_BREAK_CTA, onContinue) },
    ) {
        Spacer(Modifier.height(40.dp))
        SAFETY_BREAK.forEach { paragraph ->
            Text(
                text = paragraph,
                style = BodyStyle,
                color = Paper,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
