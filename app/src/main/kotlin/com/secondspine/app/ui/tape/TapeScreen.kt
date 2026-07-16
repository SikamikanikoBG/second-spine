package com.secondspine.app.ui.tape

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.LocalTapeWear
import com.secondspine.app.ui.theme.tapeGround
import androidx.compose.runtime.CompositionLocalProvider
import com.secondspine.coach.Tape

/**
 * THE TAPE — Sunday 20:00, and the only surface whose open-rate can kill the project.
 *
 * README, sober: *open-rate below 50% over any 4-week window after week 8 and the project is
 * ARCHIVED, not patched.* So this screen is not built for its best week; it is built for its worst
 * one. `:coach`'s `TapeTest` writes the **mediocre** week first, on purpose, and this renderer takes
 * the same instruction: weeks 20–30 are structurally flat — no new cheats, no new correlations,
 * nothing graduates, nothing collapses — and if the boring week is not worth opening, nothing else in
 * the spec matters.
 *
 * **Vertical, one screen at a time, thumb-advanced.** Not a scroll and not a slideshow with a timer.
 * A scroll would let the eye skim the whole edition in two seconds and reduce a 90-second show to a
 * wall of text; an auto-timer would take the pace out of the user's hands and make it television he
 * cannot pause. A pager gives each segment the whole screen and gives the thumb the transport
 * control, which is exactly the VHS metaphor: he is holding the remote.
 *
 * **The ceremony is already spent.** `fitCeremony()` cut this edition to 90 seconds at *compose*
 * time, lowest salience first, and the four uncuttable segments — the Montage, Trends, the sign-off,
 * and the COACH DOWN card — are the four things that make it worth opening on the worst week. This
 * file adds no segment, cuts no segment, and re-orders nothing. The show was decided in pure JVM,
 * where it is tested.
 *
 * **Nothing here is a fade.** The pager's transitions are the platform's own snap; the only slow
 * thing on the whole surface is the Ledger's split-flap, which is the design.
 */
@Composable
fun TapeScreen(
    state: TapeState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tape = state.tape
    if (tape == null) {
        NoTapeYet(onBack, modifier)
        return
    }

    val pager = rememberPagerState(pageCount = { tape.segments.size })

    // The wear is ambient and it belongs to the surface, not to the component. At rung 3+ the man is
    // nearly gone and the tape is failing with him — the GHOST speech bubble does not know it is on a
    // failing tape; the tape knows.
    CompositionLocalProvider(
        LocalTapeWear provides if (state.heavyWear) TapeWear.Heavy else TapeWear.Worn,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Ink)
                .tapeGround(),
        ) {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 52.dp, bottom = 44.dp),
                ) {
                    TapeSegmentView(tape.segments[page], state)
                }
            }

            // ---- The transport bar. Chrome, the app's own, over the show. --------------------
            TapeTransport(
                tape = tape,
                pager = pager,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // ---- The advance hint ------------------------------------------------------------
            if (pager.currentPage < tape.segments.lastIndex) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 12.dp),
                ) {
                    Icon(
                        SsIcons.ChevronDown,
                        contentDescription = "Next",
                        tint = Hairline,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * THE TRANSPORT BAR — a VHS counter, and it is the app talking, not him.
 *
 * Mono, faint, and it holds the two things a tape player shows: where you are and how long the tape
 * is. It is the app's own chrome (never gold), because the *instrument* is what the user operates
 * while the character performs — and holding that split is why the app still reads as a tool when he
 * is screaming.
 *
 * The position rail is a row of hairlines, one per segment, filled to the current page. Not a
 * progress bar with a percentage: a percentage would invite the user to measure how much of the show
 * is left, and the show is 90 seconds.
 */
@Composable
private fun TapeTransport(
    tape: Tape,
    pager: PagerState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                SsIcons.ChevronLeft,
                contentDescription = "Back",
                tint = PaperFaint,
                modifier = Modifier.size(20.dp).clickable(onClick = onBack),
            )
            Spacer(Modifier.width(12.dp))
            Icon(SsIcons.Tape, contentDescription = null, tint = PaperFaint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("WEEK ${tape.weekId}", style = MonoCaptionStyle, color = PaperFaint)
            Spacer(Modifier.weight(1f))
            // The counter. Mono, and it counts segments rather than minutes — a clock would make the
            // show a duration to be endured.
            Text(
                "${(pager.currentPage + 1).toString().padStart(2, '0')}/" +
                    tape.segments.size.toString().padStart(2, '0'),
                style = MonoCaptionStyle,
                color = PaperFaint,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(tape.segments.size) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (i <= pager.currentPage) Paper else Hairline),
                )
            }
        }
    }
}

/**
 * THE FIRST TAPE IS SUNDAY.
 *
 * Flat, mono, the app's own voice, and **no character**. Rip does not get to comment on the absence
 * of his own show: an un-composed edition is the app having nothing, not the user having failed, and
 * an error state is where a character becomes a mascot. `Nav.kt`'s `PendingSurface` takes the same
 * position for the same reason.
 *
 * This branch is also the reason there is no demo Tape anywhere in this package. A fabricated edition
 * would render a Montage of photographs the user did not take, and the Montage being *his* is the
 * entire structural reason the Tape survives to month 8. Poisoning that on day 3 to make the screen
 * look finished would trade the kill-criterion metric for a screenshot.
 */
@Composable
private fun NoTapeYet(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            .tapeGround(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    SsIcons.ChevronLeft,
                    contentDescription = "Back",
                    tint = PaperFaint,
                    modifier = Modifier.size(22.dp).clickable(onClick = onBack),
                )
                Spacer(Modifier.width(14.dp))
                Text("THE TAPE", style = LabelStyle, color = Paper)
            }

            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                SsPanel(Modifier.fillMaxWidth(), wear = TapeWear.None) {
                    Column(Modifier.padding(20.dp)) {
                        SsSectionLabel("NO EDITION YET")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "The Tape is built on Sunday and plays at 20:00. It is ninety seconds, " +
                                "it is mostly photographs you took, and it is the only place " +
                                "anything you did this week gets mentioned.",
                            style = BodyStyle,
                            color = PaperFaint,
                        )
                    }
                }
            }
        }
    }
}
