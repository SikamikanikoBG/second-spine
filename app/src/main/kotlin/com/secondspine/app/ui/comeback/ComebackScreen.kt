package com.secondspine.app.ui.comeback

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.secondspine.app.ui.theme.BreakGlassButton
import com.secondspine.app.ui.theme.DemandStyle
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.RipFace
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.tapeGround
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Register
import com.secondspine.coach.Target

/**
 * THE COMEBACK SCREEN — the most important surface in the app.
 *
 * Month 8 is reached **through** three bad weeks, not around them. That sentence is the entire
 * justification for this file's existence, and it is arithmetic rather than kindness: an adversarial
 * app with no forgiveness mechanic converts its own aggression into churn at the first shock, and the
 * first shock is a certainty, not a risk. Every other surface in this product is a bet on the good
 * weeks. This one is the only bet on the bad ones, and the bad ones are the only ones that can kill
 * it.
 *
 * He got the flu. Nine days. Zero proofs. He opens the app, at 40% strength, already braced for the
 * bill. What he sees decides whether there is a month 8 at all.
 *
 * **THE LIST OF WHAT IS NOT ON THIS SCREEN IS THE SCREEN:**
 *
 *  - **No debt.** Nothing accrued while he was gone. Penalty debt expires at end of day and never
 *    accrues (SPEC §6.9), so there is nothing to render and no arithmetic to run.
 *  - **No queued grievances.** Rip did not save anything up. He *cannot* — `purge()` already dropped
 *    the window, unconditionally, and his addressable memory is that table.
 *  - **No catch-up.** No make-up sessions, no "you owe 9 days".
 *  - **No reckoning.** No summary of the nine days. No count of them.
 *  - **No re-setup.** No wizard, no re-consent, no "let's rebuild your plan".
 *  - **No review of failures.** Not one. The most tempting screen in wellness software — the "here's
 *    where you slipped" review — is the exact screen that guarantees he never opens it again.
 *  - **No streak, broken, in red.** There is no streak in this product at all. This is where that
 *    decision earns back its cost.
 *
 * What *is* on it: one card, one line, one button, one tiny action. `[ ONE SET ]`. That is the whole
 * ask, and the ask is small on purpose — the Floor counts as a FULL success (SPEC §7.10), never
 * partial credit, never yellow. The instant the Floor reads as failure he skips it, and he's out.
 *
 * **It takes one tap and it never comes back.** `Nav.kt` pops it with `inclusive = true`, because the
 * entire promise of the screen is that there is no reckoning waiting behind it. If it were on the
 * back stack, it would be a thing he had to get *past*, and the point is that there is nothing to get
 * past.
 */
@Composable
fun ComebackScreen(
    onOneSet: () -> Unit,
    modifier: Modifier = Modifier,
    state: ComebackState = ComebackState(),
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            .tapeGround(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            RipFace(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                speaking = true,
                jurisdiction = state.jurisdiction,
            )

            Spacer(Modifier.height(28.dp))

            // ---- THE CARD. One line. -----------------------------------------------------------
            //
            // Delivered as PITCHMAN, and the register is load-bearing rather than incidental. This is
            // the warm register — the default shape of the man, the one that is *selling* — and what
            // he is selling here is the smallest possible re-entry. It is emphatically not GHOST
            // (which would make his absence the subject and turn the screen into a scene about him)
            // and emphatically not any mocking register.
            //
            // "brother" appears in the line, which is itself the tell: the word is unemittable in
            // DISAPPOINTED and GHOST (`Tape.kt`'s `admissible`), so a line carrying it is
            // structurally guaranteed to be one of the three registers where he still has his armour
            // on. On day 10 of the flu, the armour is the kindness.
            RipSpeech(
                text = state.line.text,
                register = state.line.register,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))

            // The coda. He drops out of the pitch for one sentence, and it is the only place on this
            // screen the nine days are acknowledged at all — obliquely, about himself, and then
            // immediately closed. "Don't worry about it" is the whole forgiveness mechanic, and it
            // works precisely because nothing else on screen contradicts it.
            RipSpeech(
                text = state.coda.text,
                register = state.coda.register,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(36.dp))

            // ---- THE WHOLE ASK -----------------------------------------------------------------
            OneSetButton(onOneSet)
        }

        // BREAK GLASS is on every surface, including this one, including the day he comes back.
        // Especially the day he comes back.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            BreakGlassButton(onBreak = state.onBreakGlass)
        }
    }
}

/**
 * `[ ONE SET ]` — the entire screen.
 *
 * Gold, because it is him asking. Full width, because there is nothing else to look at and nothing
 * else to weigh it against. It slams on press like the shutter does; the two most important buttons
 * in the product should feel like the same mechanism, because they are: this one is a promise to take
 * a photograph in about ninety seconds.
 *
 * There is no secondary action. No "not today", no "remind me later", no "I'm still sick". Not
 * because those are forbidden but because they are already elsewhere and free — FOR THE RECORD is one
 * surface away and never priced, and the stand-down modes are in Settings and uncapped. Putting a
 * decline button here would turn a one-tap re-entry into a decision, and a man on day 10 of the flu
 * does not need another decision.
 */
@Composable
private fun OneSetButton(onOneSet: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.Slam, label = "one-set")
    val haptics = LocalHapticFeedback.current

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, Motion.Slam) }

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = (1f - enter.value) * Motion.SLIDE_PX
            }
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(2.dp, Gold), RoundedCornerShape(2.dp))
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOneSet()
            }
            .padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ONE SET", style = DemandStyle, color = Gold)
    }
}
