package com.secondspine.app.ui.intake

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.Cyan
import com.secondspine.app.ui.theme.DemandStyle
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.InkRaised
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SlamIn
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.MAX_ENFORCED

/**
 * STEP 2 — THE DESK.
 *
 * *"What are we beating?"* Not *"what are you ashamed of."* SPEC §4.8 is explicit and the reason is
 * mechanical rather than gentle: shame recruits concealment, and this is an app whose entire thesis is
 * that concealment must be defeated. The first question you ask sets the register the user answers
 * every later question in, including the five about vomiting on the next screen.
 *
 * **Two. Exactly two.** `MAX_ENFORCED = 2`, straight from the brain, and this screen is where that
 * number becomes visible to the person it protects. Every habit app ever shipped opens by letting you
 * check eleven boxes, because eleven boxes is a great first session and a dead app by Thursday. Rip
 * is not permitted to be on everything; a coach who is on everything is a to-do list, and *"if
 * everything is penalised, nothing is."*
 *
 * The cap is also, quietly, the first thing that happens to him in the story: on the second screen of
 * his own infomercial, the contract takes five of his seven rows away, and he has to stand there and
 * sell it. He does not get a vote here either.
 */
@Composable
fun DeskScreen(
    state: IntakeState,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gates = state.gates
    val lastTapped = state.picked.lastOrNull()

    IntakeStep(
        step = 2,
        label = "THE DESK",
        modifier = modifier,
        bar = {
            SlamIn(visible = state.deskFull) {
                IntakeBar("THAT'S THE DESK", onContinue, tint = Gold)
            }
        },
    ) {
        DESK_INTRO.forEach { line ->
            RipSpeech(line.text, line.register.gatedBy(gates))
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(10.dp))

        // The slot counter. Mono, because it is a count, and because a count of two is the only
        // number on this screen that means anything.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SsSectionLabel("HIS JURISDICTION")
            Text(
                text = "${state.picked.size} / $MAX_ENFORCED",
                style = MonoCaptionStyle,
                color = if (state.deskFull) Cyan else PaperFaint,
            )
        }

        Spacer(Modifier.height(12.dp))

        state.habits.forEach { habit ->
            HabitTile(
                habit = habit,
                picked = habit.id in state.picked,
                onClick = { onToggle(habit.id) },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))

        // He heckles the last thing you touched. One line, replaced on every tap — RipSpeech's
        // AnimatedContent slides the old one out and the new one in, so the screen has exactly one
        // thing to say at a time, which is the same law the demand card is built on.
        val heckle = lastTapped?.let { DESK_HECKLES[it] } ?: if (state.deskFull) DESK_FULL else null
        if (heckle != null) {
            RipSpeech(heckle.text, heckle.register.gatedBy(gates))
        }
    }
}

/**
 * One row of the desk.
 *
 * Cyan when picked — the app registering a choice. Never gold: the tile is not Rip talking, it is the
 * user handing him a slot, and the moment his colour appears on an affordance the user is choosing
 * from, the app has started letting him do the choosing.
 */
@Composable
private fun HabitTile(
    habit: HabitChoice,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, Motion.Slam) }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = (1f - enter.value) * Motion.SLIDE_PX }
            .clip(RoundedCornerShape(2.dp))
            .background(if (picked) InkRaised else Color.Transparent)
            .border(
                BorderStroke(2.dp, if (picked) Cyan else Hairline),
                RoundedCornerShape(2.dp),
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = DemandStyle.copy(fontSize = DemandStyle.fontSize * 0.72f),
                    color = if (picked) Paper else PaperDim,
                )
                Text(
                    text = habit.pillar.name,
                    style = MonoCaptionStyle,
                    color = PaperFaint,
                )
            }
            // The picked mark is the Stamp, not a checkmark in a circle — `Icons.kt` is explicit that
            // the tick-in-a-circle is the wellness-green gesture with the colour taken out, and the
            // shape carries the connotation on its own. This is a bracket slammed onto a page.
            if (picked) {
                Icon(
                    imageVector = SsIcons.Stamp,
                    contentDescription = "On his desk",
                    tint = Cyan,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // THE CUE. The one row that states its own rule on the row itself, forever, in the app's
        // voice rather than his. He mocks the donut; he NEVER mocks the cigarette, and the user is
        // told that before he chooses rather than discovering it later and having to trust it.
        if (habit.penaltyFree) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = SsIcons.ForTheRecord,
                    contentDescription = null,
                    tint = PaperFaint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(SMOKING_NOTE, style = LabelStyle.copy(letterSpacing = LabelStyle.letterSpacing * 0.4f), color = PaperFaint)
            }
        }
    }
}
