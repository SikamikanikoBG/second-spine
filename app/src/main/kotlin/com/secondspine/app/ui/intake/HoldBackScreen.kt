package com.secondspine.app.ui.intake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.RipFace
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SlamIn
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.coach.ClinicalGates
import kotlinx.coroutines.delay

/**
 * STEP 7 — THE HOLD-BACK. The grain snaps back and he is screaming again.
 *
 * The sequencing is the joke and it is also the entire ethic of the product in ninety seconds: he has
 * just, for the only time in ten months, stopped performing to tell you where the emergency exit is —
 * and the instant that is done he is back at full volume, threatening you with a fortnight's notice.
 * The reader now knows both things are true at once, which is the whole character.
 *
 * **Fourteen days before the lock arms, and he tells you.** RESOLUTIONS §E ships R4 as dormant code
 * for two weeks, and that is a scope cut wearing a character beat — the design's own patience buys
 * the runway to get the most dangerous code in the app right, the fortnight builds the baseline the
 * targets are derived from anyway, and *anticipation outperforms aggression* by a distance nobody
 * ever believes until they measure it. A threat you can hear coming for two weeks does more work than
 * a locked phone on day one and costs nothing to build. Which is why the last thing he says in
 * onboarding is a favour that is a threat:
 *
 * > *"I'm being nice. Two weeks. Enjoy it. I'm building a file."*
 *
 * **There is no "You're all set!" screen** — SPEC §4.8 forbids it and is right to. A congratulation
 * for finishing a form is an app celebrating its own onboarding funnel, and this product's first
 * gesture must not be self-congratulation about the four minutes it just spent. The intake ends, the
 * graph pops it off the stack, and the first real demand fires.
 */
@Composable
fun HoldBackScreen(
    gates: ClinicalGates,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (shown < HOLD_BACK.size) {
            delay(if (shown == 0) 200L else 950L)
            shown += 1
        }
    }

    IntakeStep(
        step = 7,
        label = "FOURTEEN DAYS",
        modifier = modifier,
        // Worn, not Stock. The break is over; he is back inside the medium and the tape is running
        // harder than it was on the cold open, because he is louder than he was on the cold open.
        wear = TapeWear.Worn,
        bar = {
            SlamIn(visible = shown >= HOLD_BACK.size) {
                IntakeBar(HOLD_BACK_CTA, onDone, tint = Gold)
            }
        },
    ) {
        RipFace(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            speaking = shown < HOLD_BACK.size,
            jurisdiction = 4,
        )
        Spacer(Modifier.height(24.dp))

        HOLD_BACK.forEachIndexed { i, line ->
            SlamIn(visible = i < shown) {
                Column {
                    RipSpeech(line.text, line.register.gatedBy(gates))
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}
