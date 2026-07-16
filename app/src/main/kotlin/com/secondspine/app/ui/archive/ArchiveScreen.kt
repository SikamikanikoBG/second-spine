package com.secondspine.app.ui.archive

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.ExportFooter
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.tapeGround
import com.secondspine.app.ui.theme.vhsTracking
import kotlinx.coroutines.launch

/**
 * THE ARCHIVE — the photographic record, and the thing that actually compounds.
 *
 * At jurisdiction 0 this **is** the product. That is not a graceful degradation or a consolation
 * prize for a finished user; it is the plan. The character is a scaffold that removes itself one
 * habit at a time, and what has to be standing underneath when he is gone is ~1,400 photographs of
 * the user's own life, kept forever, that he took himself and nobody scored.
 *
 * The design law here is almost entirely a list of things this screen refuses to do:
 *
 *  - **No food judgement.** Ever, anywhere, in any form. There is no column, no classifier, no
 *    verdict, and no caption slot that could hold one. THE DONUT IS ALLOWED.
 *  - **No weight headline.** No number, no arrow, no colour, no before/after. The Archive is a record
 *    of *work*, not of a body.
 *  - **No score, no grade, no accepted/rejected.** `proof` has no `accepted` column to read. Every
 *    frame here is equal, including the ones from the week he fell apart.
 *  - **No streak.** No calendar heatmap with holes in it, which is a streak wearing a lab coat and is
 *    the single most reliable way to make a man delete a photo journal of his own life.
 *
 * What is left is a grid of his own photographs and a date. Rip captions it, quietly, at low
 * jurisdiction — and he captions *the logging*, never the picture, because he is 94% wrong about
 * vision and 100% accurate about what he logged.
 *
 * The purge never reaches this screen. Evidence of failure is hard-deleted at 28 days; evidence of
 * work is kept forever. That asymmetry is the whole moral architecture of the product, and this is
 * the surface where the user can see it.
 */
@Composable
fun ArchiveScreen(
    state: ArchiveState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()

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
            ArchiveTopBar(state, onBack)

            if (state.total == 0) {
                EmptyArchive()
            } else {
                Row(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = grid,
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 8.dp, bottom = 32.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.months.forEach { month ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "hdr-${month.label}") {
                                MonthHeader(month)
                            }
                            items(month.frames, key = { it.id }) { frame ->
                                FrameCell(frame, state)
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            Column(Modifier.padding(top = 28.dp)) {
                                ExportFooter(state.daysSinceExport)
                                Spacer(Modifier.height(8.dp))
                                // The one number this screen is allowed to have, and it is a count of
                                // his own work rather than a judgement of it.
                                Text(
                                    "${state.total} FRAMES · KEPT FOREVER",
                                    style = MonoCaptionStyle,
                                    color = PaperFaint,
                                )
                            }
                        }
                    }

                    // THE SCRUBBER. What makes 1,400 proofs a timeline rather than an infinite scroll.
                    MonthScrubber(
                        months = state.months,
                        onScrub = { index ->
                            scope.launch { grid.scrollToItem(headerIndexOf(state, index)) }
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The frame
// ---------------------------------------------------------------------------

/**
 * One proof, as a physical frame.
 *
 * It slams in — scale from 0.92 on the house spring, no alpha anywhere in the transition. SPEC §4.9:
 * nothing fades. A grid of photographs that fades in is a gallery app; a grid that lands is an
 * evidence locker, and the entire aesthetic thesis of the Archive is that it is a photo journal
 * wearing an evidence-locker costume.
 *
 * The tape wear is [TapeWear.Worn] and seeded off the frame's own id, so no two frames tear in
 * lockstep. That is the difference between "two bits of tape" (an aesthetic) and "the screen is
 * glitching" (a bug).
 */
@Composable
private fun FrameCell(frame: ProofFrame, state: ArchiveState) {
    val enter = remember(frame.id) { Animatable(0.92f) }
    LaunchedEffect(frame.id) { enter.animateTo(1f, Motion.Slam) }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { scaleX = enter.value; scaleY = enter.value }
                .clip(RoundedCornerShape(2.dp))
                .vhsTracking(TapeWear.Worn, seed = (frame.id % 100) / 100f),
        ) {
            ProofImage(
                path = frame.imagePath,
                // Never describes the contents. The app does not know what is in the picture.
                contentDescription = "Proof, ${frame.dayLabel}",
                modifier = Modifier.fillMaxSize(),
                targetPx = 320,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(frame.dayLabel, style = MonoCaptionStyle, color = PaperFaint)

        // Rip's caption. Low jurisdiction only, quiet, and about the logging — never the picture.
        if (state.captioned && frame.caption != null) {
            Spacer(Modifier.height(4.dp))
            RipSpeech(
                text = frame.caption,
                register = state.captionRegister,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

@Composable
private fun MonthHeader(month: ArchiveMonth) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SsSectionLabel(month.label)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Hairline))
            Spacer(Modifier.width(10.dp))
            Text("${month.frames.size}", style = MonoCaptionStyle, color = PaperFaint)
        }
    }
}

/**
 * THE SCRUBBER — the rail on the right, and the reason the fourth reading of the name is earned.
 *
 * A timeline of 1,400 frames that can only be scrolled is a wall. The rail turns ten months into ten
 * touch targets. It is mono, it is thin, and it is the app's own colour: this is chrome, an
 * instrument, a thing the user *operates*. Rip does not get to touch it.
 */
@Composable
private fun MonthScrubber(
    months: List<ArchiveMonth>,
    onScrub: (Int) -> Unit,
) {
    Column(
        Modifier
            .width(56.dp)
            .fillMaxSize()
            .padding(end = 8.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        months.forEachIndexed { index, month ->
            Text(
                // "MARCH 2026" -> "MAR". The rail is 56dp and the year is implied by its neighbours.
                text = month.label.take(3),
                style = MonoCaptionStyle,
                color = PaperFaint,
                modifier = Modifier
                    .clickable { onScrub(index) }
                    .padding(vertical = 6.dp, horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun ArchiveTopBar(state: ArchiveState, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SsIcons.ChevronLeft,
            contentDescription = "Back",
            tint = PaperFaint,
            modifier = Modifier.size(22.dp).clickable(onClick = onBack),
        )
        Spacer(Modifier.width(14.dp))
        Icon(SsIcons.Archive, contentDescription = null, tint = PaperDimOrPaper(state), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("ARCHIVE", style = LabelStyle, color = Paper)
    }
}

/**
 * The Archive's own icon brightens as the odometer falls. It is the one piece of chrome in the app
 * that gets *more* prominent over ten months, and it costs a ternary: at low jurisdiction this screen
 * is the product, and the icon is allowed to know that.
 */
@Composable
private fun PaperDimOrPaper(state: ArchiveState) = if (state.captioned) Paper else PaperFaint

/**
 * NO TAPE YET.
 *
 * Flat, mono, UI type, no character. Rip does not get to comment on an empty archive: an empty
 * archive is the app having nothing, not the user having failed, and putting the character in an
 * empty state is exactly how a character becomes a mascot. It also says what fills it, once, without
 * nagging.
 */
@Composable
private fun EmptyArchive() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        SsPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                SsSectionLabel("NO TAPE YET")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Every proof you take is kept here, forever. Nothing in this archive is ever " +
                        "scored, and nothing in it is ever deleted.",
                    style = MonoCaptionStyle,
                    color = PaperFaint,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scrub arithmetic
// ---------------------------------------------------------------------------

/**
 * The flat grid index of a month's header.
 *
 * The grid is a flat list of items, so a month's header sits after every preceding header and every
 * preceding frame. Computed rather than remembered because the source list is already in memory and
 * ten months of arithmetic is cheaper than a cache that can go stale mid-scroll.
 */
private fun headerIndexOf(state: ArchiveState, monthIndex: Int): Int {
    var index = 0
    for (i in 0 until monthIndex) {
        index += 1 + state.months[i].frames.size
    }
    return index
}
