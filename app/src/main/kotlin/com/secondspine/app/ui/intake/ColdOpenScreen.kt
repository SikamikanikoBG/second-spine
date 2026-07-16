package com.secondspine.app.ui.intake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.RipFace
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SlamIn
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.coach.ClinicalGates
import kotlinx.coroutines.delay

/**
 * STEP 1 — THE COLD OPEN.
 *
 * The most important thirty seconds in the product, and the thing almost every onboarding gets
 * backwards: it does not ask for anything. No account, no goal, no name, no "what brings you here
 * today", no permission dialog, no notification prompt. A man who has installed a comedy app about a
 * dead infomercial ghost gets, immediately and at full volume, a dead infomercial ghost. The demo is
 * the onboarding. Everything he is about to agree to is easier to agree to once he has met the thing
 * he is agreeing to.
 *
 * **He is in character and he does not know he is dead.** The studio is dark and he has filed that
 * under lighting. The crew is quiet and he has filed that under a quiet crew. The one fact he cannot
 * file — there is no camera on him, there is a camera on you — he waves off in half a second, and
 * that half-second is the entire ten-month arc in miniature. Nothing here explains any of it. The
 * user is not supposed to work out that Rip is dead; the user is supposed to notice, in month three,
 * that Rip hasn't.
 *
 * The consent line comes first, in the app's own type, before he opens his mouth. SPEC §4.8:
 * *"Photographing his kitchen before explaining what the app does with photos is charming and
 * backwards; the bit survives the fix."* It survives it completely — nobody has ever been made less
 * funny by fifteen words of plain English above them.
 */
@Composable
fun ColdOpenScreen(
    gates: ClinicalGates,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A tape plays. It does not wait for you to press next between sentences, so the lines land on a
    // clock — but the clock is a courtesy, not a lock: one tap anywhere drops the whole monologue in
    // at once. Somebody who has read faster than the timer must never be made to watch it finish.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (shown < COLD_OPEN.size) {
            delay(if (shown == 0) 250L else 1_100L)
            shown += 1
        }
    }

    val interaction = remember { MutableInteractionSource() }

    IntakeStep(
        step = 1,
        label = "SECOND SPINE",
        modifier = modifier,
        bar = {
            // Only once he has finished talking. There is no skip button, because there is nothing to
            // skip: tapping the screen already ends the monologue instantly.
            SlamIn(visible = shown >= COLD_OPEN.size) {
                IntakeBar(COLD_OPEN_CTA, onContinue, tint = Gold)
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = null) { shown = COLD_OPEN.size },
        ) {
            Column(Modifier.fillMaxWidth()) {

                // The app, before the character. Paper, UI type, no gold, no tape.
                Text(
                    text = CONSENT_LINE,
                    style = BodyStyle,
                    color = PaperDim,
                    modifier = Modifier.padding(bottom = 20.dp),
                )

                RipFace(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    speaking = shown < COLD_OPEN.size,
                    // He opens at full jurisdiction. He has never had more than he has right now and
                    // he will never have this much again — the arc only runs one way, and the first
                    // frame is the high-water mark.
                    jurisdiction = 4,
                )

                Spacer(Modifier.height(20.dp))
                SsSectionLabel("ON THE TAPE")
                Spacer(Modifier.height(12.dp))

                COLD_OPEN.forEachIndexed { i, line ->
                    SlamIn(visible = i < shown) {
                        Column {
                            RipSpeech(
                                text = line.text,
                                register = line.register.gatedBy(gates),
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
    }
}
