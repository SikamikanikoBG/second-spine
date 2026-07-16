package com.secondspine.app.ui.intake

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Cyan
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.NumberStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.coach.WINDDOWN_LEAD_MIN

/**
 * STEP 5 — THE TWO NUMBERS.
 *
 * This screen exists because of a bug that was in the spec on the facing page from the doctrine that
 * forbade it. RESOLUTIONS §D:
 *
 * > *"The wind-down silence window is hardcoded 22:00–08:00 instead of keyed to (winddown_at,
 * > wake_at). If his target bed is 21:30, wind-down starts 20:45 and there are **75 minutes in which
 * > the ladder can fire an alarm, a TTS line and a lock inside the wind-down window** — on the pillar
 * > ranked #1."*
 *
 * A constant somebody typed once, silently converting the app's loudest safety promise into a lie for
 * every early sleeper who ever installs it. So the promise is made of the user's own numbers or it is
 * not made: `sleepSilenceActive(now, targetBed, wake)` in `:coach` takes both, `maxRungNow` clamps
 * the whole ladder to R0 inside the window, and this screen is the only place either number enters
 * the product.
 *
 * **Wind-down is not a third picker.** It is `bed - 45`, computed by [com.secondspine.coach.winddownAtMinOfDay],
 * and it is displayed rather than set. A second independently-editable field would be a second
 * definition of the same instant, and two definitions of a safety boundary drift apart on exactly the
 * night nobody is testing.
 *
 * And note what these numbers are not: they are not a sleep goal, they are not scored, and nothing in
 * the pipeline reads them. They are a *muzzle*. That shape is the answer to SPEC §4.8's warning that
 * *"lying in the wizard is the softest attack surface in the app, so remove the payoff"* — there is no
 * payoff to lie for here. Claim a bedtime of 21:00 and all you have bought is a longer evening in
 * which Rip is legally silent. The only way to cheat this screen is in your own favour.
 */
@Composable
fun TimesScreen(
    state: IntakeState,
    onWake: (Int) -> Unit,
    onBed: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IntakeStep(
        step = 5,
        label = "THE HOURS",
        modifier = modifier,
        bar = { IntakeBar("SET", onContinue, tint = Gold) },
    ) {
        TIMES_INTRO.forEach { line ->
            RipSpeech(line.text, line.register.gatedBy(state.gates))
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MinuteStepper("WAKE", state.wakeAtMinutes, onWake)
            MinuteStepper("BED", state.bedAtMinutes, onBed)
        }

        Spacer(Modifier.height(26.dp))

        // THE DERIVED NUMBER, and the promise it buys. Cyan: this is the app stating a limit on
        // itself, which is chrome, not character. He does not get to say this one — a man promising
        // to be quiet is a man asking to be believed, and the app can simply be believed instead.
        Column(
            Modifier
                .fillMaxWidth()
                .border(BorderStroke(2.dp, Cyan), RoundedCornerShape(2.dp))
                .padding(18.dp),
        ) {
            SsSectionLabel("HE IS SILENT BETWEEN")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMinutes(state.winddownAtMinutes), style = NumberStyle, color = Paper)
                Text(
                    "  —  ",
                    style = NumberStyle,
                    color = PaperFaint,
                )
                Text(formatMinutes(state.wakeAtMinutes), style = NumberStyle, color = Paper)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "WIND-DOWN = BED − ${WINDDOWN_LEAD_MIN} MIN",
                style = MonoCaptionStyle,
                color = PaperFaint,
            )
            Spacer(Modifier.height(14.dp))
            Text(TIMES_SILENCE_PROMISE, style = BodyStyle, color = Paper)
            Spacer(Modifier.height(10.dp))
            Text(TIMES_WINDDOWN_NOTE, style = BodyStyle, color = PaperDim)
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp))
    }
}
