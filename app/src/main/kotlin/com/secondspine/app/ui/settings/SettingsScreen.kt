package com.secondspine.app.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Cyan
import com.secondspine.app.ui.theme.CyanDim
import com.secondspine.app.ui.theme.ExportFooter
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.InkRaised
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.tapeGround
import com.secondspine.coach.PausedMode

/**
 * SETTINGS — the route the spec's inventory forgot, holding the four things that make the rest of
 * the product legitimate.
 *
 * This app coerces. It shouts, it schedules alarms, and on exercise it will eventually take the
 * phone. All of that is defensible for exactly one reason: the user signed a contract in advance and
 * the exit was never taken away from him. **This is the screen with the exits on it**, which makes it
 * the load-bearing wall under every aggressive thing the character is allowed to do — not a junk
 * drawer of preferences.
 *
 * Order is argued, not inherited. The stand-down modes are first because they are the thing a man
 * with the flu is looking for at the moment he is most likely to uninstall. RETIRE RIP is last, but
 * it is *present*, on day one, in the same place it will be on day 200 — SPEC §4.10: "RETIRE RIP has
 * been in the menu since January."
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onRetire: () -> Unit,
    onSetMuted: (Boolean) -> Unit,
    onSetPausedMode: (PausedMode?) -> Unit,
    onExportNow: () -> Unit,
    modifier: Modifier = Modifier,
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
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsTopBar(onBack)

            Column(Modifier.padding(horizontal = 16.dp)) {

                // ---- 1. "I HAVE THE FLU" ------------------------------------------------------
                StandDownSection(state.pausedMode, onSetPausedMode)

                Spacer(Modifier.height(28.dp))

                // ---- 2. MUTE THE MAN ----------------------------------------------------------
                MuteSection(state.muted, onSetMuted)

                Spacer(Modifier.height(28.dp))

                // ---- 3. THE EXPORT, AND ITS RECEIPT -------------------------------------------
                ExportSection(state, onExportNow)

                Spacer(Modifier.height(28.dp))

                // ---- 4. THE ONE BREAK ---------------------------------------------------------
                SafetyBreak()

                Spacer(Modifier.height(28.dp))

                // ---- 5. THE DOOR --------------------------------------------------------------
                RetireSection(onRetire)

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. The stand-down modes
// ---------------------------------------------------------------------------

/**
 * SICK · INJURED · TRAVELLING · DELOAD. Mandatory at v1.
 *
 * `Health.kt` says why, and says it as a prediction rather than as a principle: *"An app with no 'I
 * have the flu' state gets deleted the first time he has the flu. Everyone forgets this and everyone
 * dies of it."* The flu is not an edge case. It is a certainty on a ten-month horizon, and so is the
 * back injury, and so is the week in another timezone.
 *
 * **Uncapped, unpriced, one tap** (SPEC §6.7 row 14). There is no duration picker, no severity
 * scale, no return date, no justification field, and no confirmation. Every one of those is a place
 * to make a sick man negotiate, and a sick man does not negotiate — he uninstalls.
 *
 * Turning it *off* is equally silent. No "welcome back!", no fanfare, no summary of what he missed.
 * The Comeback screen owns re-entry and it owns it with one tap and no reckoning; this control's job
 * is to be a switch and then get out of the way.
 *
 * The selected chip is **cyan**, not gold. This is the app's own colour: the modes are an instrument
 * the user operates, and Rip does not get a vote in whether the user has the flu. He is not consulted
 * and he does not comment — there is no line of his anywhere on this section, by design.
 */
@Composable
private fun StandDownSection(
    current: PausedMode?,
    onSet: (PausedMode?) -> Unit,
) {
    Column {
        SsSectionLabel("STAND DOWN")
        Spacer(Modifier.height(10.dp))
        Text(
            "No penalty. No debt. No catch-up. Take as long as you need; nobody is counting, and " +
                "there is nothing to count with.",
            style = BodyStyle,
            color = PaperDim,
        )
        Spacer(Modifier.height(14.dp))

        PausedMode.entries.forEach { mode ->
            val (title, blurb) = MODE_COPY.getValue(mode)
            val selected = current == mode
            ModeRow(
                title = title,
                blurb = blurb,
                selected = selected,
                // Tapping the active mode clears it. One tap in, one tap out — the exit is the
                // same size as the entrance, which is the whole doctrine of this screen.
                onClick = { onSet(if (selected) null else mode) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModeRow(
    title: String,
    blurb: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border by animateFloatAsState(if (selected) 1f else 0f, Motion.Snap, label = "mode")

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(InkRaised)
            .border(
                BorderStroke(2.dp, lerpStroke(border)),
                RoundedCornerShape(2.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LabelStyle, color = if (selected) Cyan else Paper)
            Spacer(Modifier.height(4.dp))
            Text(blurb, style = MonoCaptionStyle, color = PaperFaint)
        }
        if (selected) {
            Text("ON", style = MonoCaptionStyle, color = Cyan)
        }
    }
}

/** Hairline at rest, cyan when armed. The app's colour, because this is the app's instrument. */
private fun lerpStroke(t: Float): Color =
    Color(
        red = Hairline.red + (Cyan.red - Hairline.red) * t,
        green = Hairline.green + (CyanDim.green - Hairline.green) * t,
        blue = Hairline.blue + (Cyan.blue - Hairline.blue) * t,
        alpha = 1f,
    )

// ---------------------------------------------------------------------------
// 2. Mute the man
// ---------------------------------------------------------------------------

/**
 * MUTE THE MAN — one tap, always reachable (SPEC §4.9).
 *
 * The subtitle is the important part and it is stated plainly rather than buried: **it mutes the
 * voice, not the penalties.** That honesty is the only thing that makes the control ethical in both
 * directions. If it silently disabled enforcement it would be a free win button and the contract
 * would be theatre; if it *implied* it disabled enforcement and did not, the app would be lying to a
 * user at the exact moment he is trying to make it be quiet.
 *
 * So the user gets exactly what it says: the man stops performing, the machine keeps its word.
 */
@Composable
private fun MuteSection(muted: Boolean, onSet: (Boolean) -> Unit) {
    Column {
        SsSectionLabel("THE MAN")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(InkRaised)
                .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
                .clickable { onSet(!muted) }
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MUTE THE MAN", style = LabelStyle, color = if (muted) Cyan else Paper)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Silences his voice. Does not silence the penalties — you signed for those.",
                    style = MonoCaptionStyle,
                    color = PaperFaint,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(if (muted) "MUTED" else "LIVE", style = MonoCaptionStyle, color = if (muted) Cyan else PaperDim)
        }
    }
}

// ---------------------------------------------------------------------------
// 3. The export
// ---------------------------------------------------------------------------

/**
 * EXPORT NOW + PROOF OF LAST RUN — a v1 ship blocker (SPEC §8.6).
 *
 * The archive is the one asset this product claims compounds, and a claim like that is worthless
 * unless the user can walk out with the asset at any moment. [ExportFooter] goes **red** — one of the
 * only places `VerdictRed` is ever spent — when the export has not run in 14 days, because the app
 * failing loudly about its *own* broken promise is the correct use of the alarm colour. The user has
 * never seen that colour used to score him, so when it appears he reads it as information rather than
 * as judgement.
 *
 * The file count is printed because "it ran" is not proof. "It wrote 1,412 files" is proof.
 */
@Composable
private fun ExportSection(state: SettingsState, onExportNow: () -> Unit) {
    Column {
        SsSectionLabel("THE ARCHIVE IS YOURS")
        Spacer(Modifier.height(10.dp))
        Text(
            "Your proofs are files on your phone. The app never holds them hostage, and this button " +
                "works whether or not you ever open this app again.",
            style = BodyStyle,
            color = PaperDim,
        )
        Spacer(Modifier.height(14.dp))
        FlatButton("EXPORT NOW", onExportNow)
        Spacer(Modifier.height(12.dp))
        ExportFooter(state.daysSinceExport)
        if (state.lastExportFileCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.lastExportFileCount} FILES WRITTEN",
                style = MonoCaptionStyle,
                color = PaperFaint,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. The door
// ---------------------------------------------------------------------------

/**
 * RETIRE RIP — **the product announcement, not an easter egg.**
 *
 * It is here on day one, in the menu, above the fold of this section, in the same place it will be in
 * October. That is the single most counter-intuitive decision in the product and it is the one that
 * makes the rest of it work: the whole thesis is that the coach is a scaffold which removes itself,
 * and a scaffold that hides its own dismantling instructions is not a scaffold, it is a trap.
 *
 * It is gold, because it is *him* — this button is the character's own ending, and he owns it. It is
 * not red: retiring him is not a failure and not an emergency, and a red door implies the user is
 * doing something wrong by walking through it. He is doing the thing the app was built to make
 * possible.
 *
 * It does not confirm here. The Goodbye screen is the confirmation, and it is the one that offers the
 * export first — because the only thing that must not happen on the way out is the user losing his
 * archive.
 */
@Composable
private fun RetireSection(onRetire: () -> Unit) {
    Column {
        SsSectionLabel("RETIRE RIP")
        Spacer(Modifier.height(10.dp))
        Text(
            "End it. He goes, the app goes, the archive is yours to take with you. This has been " +
                "here since your first day and it will work on your last one.",
            style = BodyStyle,
            color = PaperDim,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .border(BorderStroke(2.dp, Gold), RoundedCornerShape(2.dp))
                .clickable(onClick = onRetire)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("RETIRE RIP", style = LabelStyle, color = Gold)
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

/** A flat, full-width, app-voice button. Paper on a hairline. Never gold — the app is talking. */
@Composable
internal fun FlatButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = LabelStyle, color = Paper)
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            com.secondspine.app.ui.theme.SsIcons.ChevronLeft,
            contentDescription = "Back",
            tint = PaperFaint,
            modifier = Modifier.size(22.dp).clickable(onClick = onBack),
        )
        Spacer(Modifier.width(14.dp))
        Text("SETTINGS", style = LabelStyle, color = Paper)
    }
}
