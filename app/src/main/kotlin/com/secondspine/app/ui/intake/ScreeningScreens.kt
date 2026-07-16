package com.secondspine.app.ui.intake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.SlamIn
import com.secondspine.app.ui.theme.TapeWear
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box

/**
 * STEPS 3 & 4 — THE CLINICAL SCREENS.
 *
 * **Rip is not here.** Not quiet, not restrained, not doing a tasteful version — absent. There is no
 * gold on these two screens, no display type, no face, no tape, no grain, no tracking bar, no
 * scanline. [TapeWear.None]. The medium itself stops.
 *
 * That absence is the single best piece of craft available to this product and it costs nothing to
 * build. An app that is 100% bit teaches the reader to discount everything it says, including the two
 * screens where it needs to be believed. So the app spends ninety seconds being completely, visibly,
 * structurally not-a-joke, and the reader learns — before he is asked anything that matters — that
 * this thing knows the difference. Everything Rip is allowed to do for the next ten months is bought
 * here and on the character break, and nowhere else.
 *
 * The two screens are deliberately identical in construction. Same scaffold, same items, same chips,
 * same flat result panel. They are one instrument twice, and the user should feel the app change
 * gear once, not twice.
 */

// ---------------------------------------------------------------------------
// STEP 3 — SCOFF
// ---------------------------------------------------------------------------

/**
 * Five items, verbatim, and the gate with the longest reach in the app.
 *
 * `>= 2` permanently removes the mocking registers and the entire scale/measurement feature set, with
 * no in-app override — and `registerMix()` in `:coach` implements it as *"ARENA and BIT do not exist
 * for this user. Their mass becomes warmth, not silence."* The SCOFF-positive user does not get a
 * lesser product; he gets a coach who is only ever kind, which for him is the better one.
 *
 * The result is shown, not hidden. "Not warned about, unavailable" (SPEC §4.8) is about the *feature*
 * being gone rather than discouraged; it is not an instruction to make the gate secret. A silent
 * lockout is something the user notices in month two and starts fighting. A stated one is a promise
 * the app made and kept.
 */
@Composable
fun ScoffScreen(
    state: IntakeState,
    onAnswer: (String, Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClinicalScreen(
        step = 3,
        label = "SCREENING · 1 OF 2",
        title = SCOFF_TITLE,
        intro = SCOFF_INTRO,
        attribution = SCOFF_ATTRIBUTION,
        items = SCOFF_ITEMS,
        answers = state.scoff,
        onAnswer = onAnswer,
        complete = state.scoffAnswered,
        result = if (!state.scoffAnswered) null
        else if (state.scoffIsPositive) SCOFF_POSITIVE_RESULT else SCOFF_NEGATIVE_RESULT,
        onContinue = onContinue,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// STEP 4 — PAR-Q+
// ---------------------------------------------------------------------------

/**
 * Seven items, verbatim. Any yes and the ghost stops giving exercise advice.
 *
 * `prescribe()`'s precedence table in `:coach` puts this above the tripwire, above sick, above
 * everything: *"PAR-Q+ positive -> no prescription at all. A clinician, not a ghost."* This screen is
 * the only place that flag is ever set from, which is why the seven strings live in a file that says
 * they may not be edited.
 *
 * The habit stays on the desk. A PAR-Q+ yes is not a punishment and it is not a demotion — THE SET is
 * still his, the app simply stops being the thing that tells him what to do in it. Removing the habit
 * would be the app deciding he cannot exercise, which is a clinical judgement it is exactly as
 * unqualified to make in that direction as in the other.
 */
@Composable
fun ParqScreen(
    state: IntakeState,
    onAnswer: (String, Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClinicalScreen(
        step = 4,
        label = "SCREENING · 2 OF 2",
        title = PARQ_TITLE,
        intro = PARQ_INTRO,
        attribution = PARQ_ATTRIBUTION,
        items = PARQ_ITEMS,
        answers = state.parq,
        onAnswer = onAnswer,
        complete = state.parqAnswered,
        result = if (!state.parqAnswered) null
        else if (state.parqIsPositive) PARQ_POSITIVE_RESULT else PARQ_NEGATIVE_RESULT,
        onContinue = onContinue,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// The shape both of them are
// ---------------------------------------------------------------------------

/**
 * The instrument screen.
 *
 * Note the title: [PitchmanStyle]'s display face is *not* used here, and the title is UI type at
 * size. Display type is Rip's, exclusively — `Type.kt` splits the families by who is speaking, and
 * the app borrowing his font for a header would be him standing behind it.
 *
 * The continue bar does not exist until every item is answered. No "please answer all questions"
 * toast, no red outline on the ones he skipped, no scroll-to-first-error. An instrument that scolds
 * you for the pace you answer it at is collecting a different measurement than the one it thinks.
 */
@Composable
private fun ClinicalScreen(
    step: Int,
    label: String,
    title: String,
    intro: String,
    attribution: String,
    items: List<ScreenItem>,
    answers: Map<String, Boolean>,
    onAnswer: (String, Boolean) -> Unit,
    complete: Boolean,
    result: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IntakeStep(
        step = step,
        label = label,
        modifier = modifier,
        // THE TAPE STOPS. This is the only wear value on this screen and it is the whole point.
        wear = TapeWear.None,
        bar = {
            SlamIn(visible = complete) {
                IntakeBar("CONTINUE", onContinue)
            }
        },
    ) {
        Text(
            text = title,
            // UI type at display size. The app has a loud voice of its own and it is not his.
            style = BodyStyle.copy(fontSize = BodyStyle.fontSize * 1.6f, lineHeight = BodyStyle.lineHeight * 1.5f),
            color = Paper,
        )
        Spacer(Modifier.height(10.dp))
        Text(intro, style = BodyStyle, color = PaperDim)
        Spacer(Modifier.height(10.dp))
        Attribution(attribution)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        Column {
            items.forEachIndexed { i, item ->
                ClinicalItem(
                    index = i + 1,
                    item = item,
                    answer = answers[item.id],
                    onAnswer = { yes -> onAnswer(item.id, yes) },
                )
                if (i < items.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                }
            }
        }

        if (result != null) {
            Spacer(Modifier.height(20.dp))
            ClinicalResult(result)
        }
    }
}
