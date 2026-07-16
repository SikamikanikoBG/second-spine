package com.secondspine.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.BreakGlassButton
import com.secondspine.app.ui.theme.DemandCard
import com.secondspine.app.ui.theme.ExportFooter
import com.secondspine.app.ui.theme.ForTheRecordButton
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.InkRaised
import com.secondspine.app.ui.theme.InkSunken
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.LedgerCounter
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.RipFace
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.tapeGround
import com.secondspine.app.ui.theme.vhsTracking
import com.secondspine.coach.LEDGER_WINDOW_LABEL
import com.secondspine.coach.Stage
import androidx.compose.foundation.clickable

/**
 * HOME — the 7am surface, and the whole product's one glanceable thing.
 *
 * SPEC §4.3: *"At 07:00 he is holding coffee in one hand. The screen answers exactly one question and
 * it is not 'how am I doing.'"* Everything below follows from that sentence.
 *
 * The layout, top to bottom, is the spec's, and each element earns its slot:
 *  1. **RIP** at `jurisdictionShare(j)` of the vertical. The single geometry decision on the screen.
 *  2. **THE DEMAND CARD** — one line, one shutter. Never two. No countdown.
 *  3. **THE LEDGER STRIP** — three mono glyphs, split-flap, the only slow thing in the app.
 *  4. **FOR THE RECORD** — persistent, bottom, always. Never hidden, never counted, never priced.
 *  5. **BREAK GLASS** — bottom-left, first tap, no confirm.
 *  6. Below a deliberate scroll: **the trust ladder** — the odometer made legible.
 *
 * THE ARC, RENDERED: the same six elements re-weight themselves as one integer falls. At j=4 Rip is
 * 59% of the screen and the Archive is a strip at the bottom; at j=0 he is a 40px face in the corner
 * and the Archive is the product. Nothing is added, nothing is unlocked, no assets are authored, no
 * calendar is consulted. The user's own success is the only thing that moves it, which is what makes
 * this an arc the user *caused* rather than an arc the app performed at him — and that distinction is
 * the entire ten-month bet.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onProof: (habitId: String) -> Unit,
    onForTheRecord: () -> Unit,
    onBreakGlass: () -> Unit,
    onOpenTape: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            // 4% grain over near-black, at 12 fps, on the root of every screen. This is the single
            // line that makes a dark app read as expensive rather than as empty.
            .tapeGround(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            TopBar(onOpenTape, onOpenArchive, onOpenSettings)

            // ---- 1. RIP -----------------------------------------------------------------------
            // `fillMaxHeight(ripFraction)` inside a scrolling column would be unbounded, so the
            // fraction is resolved against a fixed 7am viewport budget instead: the man is a share
            // of the *screen you see at a glance*, which is what SPEC §4.3 is actually specifying.
            RipZone(state)

            Spacer(Modifier.height(20.dp))

            // ---- 2. THE ONE THING -------------------------------------------------------------
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (state.demand != null) {
                    DemandCard(
                        demand = state.demand.text,
                        onProof = { onProof(state.demand.habitId) },
                    )
                } else {
                    // Null demand is the Floor, not an empty card. An empty card is the app admitting
                    // it has nothing to say and asking you to look at the nothing.
                    FloorCard()
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 3. THE LEDGER STRIP ----------------------------------------------------------
            LedgerStrip(state, onOpenLedger)

            Spacer(Modifier.height(24.dp))

            // ---- The Archive. A strip at high jurisdiction; the product at low. ---------------
            ArchiveBand(state, onOpenArchive)

            Spacer(Modifier.height(28.dp))

            // ---- 6. BELOW THE FOLD: the odometer, made legible --------------------------------
            TheFold()
            TrustLadder(state)

            Spacer(Modifier.height(24.dp))
            ExportFooter(
                daysSinceExport = state.daysSinceExport,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(120.dp))   // room for the two buttons that are never priced
        }

        // ---- 4 & 5. The floor of the product, pinned over everything. ------------------------
        // Pinned rather than in the scroll, because "always visible" is not a layout preference
        // here: FOR THE RECORD has to beat the fake at 11pm, and BREAK GLASS has to work at 2am.
        // Neither can be one scroll away on the one night it matters.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Ink)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ForTheRecordButton(onForTheRecord)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                BreakGlassButton(onBreakGlass)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. RIP
// ---------------------------------------------------------------------------

/**
 * The man, at `0.15 + 0.11 × j` of the 7am viewport.
 *
 * At j >= 3 he gets a face *and* a voice, and both are large. At j <= 2 the face shrinks to a fixed
 * 40dp and moves next to the line rather than above it — SPEC §4.10's "40px face in the corner who
 * can still lock the phone". He is never removed. `jurisdictionShare` bottoms out at 0.15 and so
 * does he: the ending is not that he disappears, it is that he stops being the point. A character
 * who vanishes at the finish line was a feature. A character who is still there, small, is a ghost
 * who has been fired one habit at a time, which is the story.
 */
@Composable
private fun RipZone(state: HomeState) {
    // The 7am viewport budget: what a glance covers before any scrolling. The fraction is applied to
    // this rather than to the scrollable content height, which would be unbounded and meaningless.
    val viewportDp = 560f
    val faceHeight = (viewportDp * state.ripFraction).dp

    if (state.archiveLed) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RipFace(
                modifier = Modifier.size(40.dp),
                speaking = state.ripLine != null,
                jurisdiction = state.jurisdiction,
            )
            Spacer(Modifier.width(12.dp))
            if (state.ripLine != null) {
                RipSpeech(state.ripLine, state.ripRegister, Modifier.weight(1f))
            } else {
                // Silence is a first-class outcome (`speak()` returns null and that is not a
                // failure). At jurisdiction 0 the budget is spent before the day starts — the Tape
                // only, forever. That is the ending, and it is spelled 0.
                Spacer(Modifier.weight(1f))
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            RipFace(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(faceHeight),
                speaking = state.ripLine != null,
                jurisdiction = state.jurisdiction,
            )
            if (state.ripLine != null) {
                Spacer(Modifier.height(12.dp))
                RipSpeech(state.ripLine, state.ripRegister)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. The Floor
// ---------------------------------------------------------------------------

/**
 * THE FLOOR. What is on screen when nothing is owed.
 *
 * Not a congratulation, and not an empty state. SPEC ships exactly **one** sincere congratulation in
 * the whole product (`SINCERE_ONE`, on the first strict pull-up) and spending it here — on a Tuesday
 * where the user merely has nothing outstanding — would be spending the only currency the character
 * has on the cheapest possible moment.
 */
@Composable
private fun FloorCard() {
    SsPanel(Modifier.fillMaxWidth(), wear = TapeWear.Worn) {
        Column(Modifier.padding(18.dp)) {
            SsSectionLabel("THE FLOOR")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Nothing is owed.",
                style = BodyStyle,
                color = PaperDim,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. The Ledger strip
// ---------------------------------------------------------------------------

/**
 * THREE GLYPHS. Split-flap. The only slow thing in the app.
 *
 * Three, and not the whole docket, because the strip is a *glance* and the card is a *read* — the
 * full docket is Sunday's business on the Tape, or one tap away at `ledger`. Putting nine rows of
 * failure taxonomy on the 7am screen would make the morning surface a grievance list, which is
 * precisely the artefact the 28-day purge exists to make impossible.
 *
 * The window label is `LEDGER_WINDOW_LABEL` from `:coach` — "ROLLING 28 DAYS" — and it is printed
 * rather than implied. The user should know the number in front of him has an expiry, because the
 * fact that Rip *structurally cannot* hold January against him is a promise the app has to make
 * visibly or it is not worth having made.
 */
@Composable
private fun LedgerStrip(state: HomeState, onOpenLedger: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLedger)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SsIcons.Ledger, contentDescription = null, tint = PaperFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            SsSectionLabel("THE LEDGER")
            Spacer(Modifier.weight(1f))
            Text(LEDGER_WINDOW_LABEL, style = MonoCaptionStyle, color = PaperFaint)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val rows = state.ledger.take(3)
            if (rows.isEmpty()) {
                // Zeroes, not an empty state. SPEC: "the 0s are the point: they are a scoreboard the
                // user won, and a docket that only prints your failures is not a docket, it's a
                // grievance."
                LedgerCounter(0, "CAUGHT FAKE", Modifier.weight(1f))
                LedgerCounter(0, "EVASION", Modifier.weight(1f))
                LedgerCounter(0, "DEMOTION", Modifier.weight(1f))
            } else {
                rows.forEach { row ->
                    LedgerCounter(row.count, row.kind.cardLabel, Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The Archive band — a strip, or the product
// ---------------------------------------------------------------------------

/**
 * THE ARCHIVE, weighted by the odometer.
 *
 * At j >= 3 this is a 72dp filmstrip under the demand: present, populated from proof #1, and not the
 * point. At j <= 2 it is 132dp and it is the first thing under his 40px face, because by then the
 * user has ~1,400 photographs of his own kitchen at 6am and *that* is what he opens the app for.
 *
 * The same component, the same data, one integer. No day-200 gate, no unlock, no "new feature
 * available" — the pull mechanic shipped in week one and simply grew into the room the character
 * vacated. Gating it behind the horizon it is needed at is how you arrive at the horizon with an
 * empty archive and a user who left in month 3.
 */
@Composable
private fun ArchiveBand(state: HomeState, onOpenArchive: () -> Unit) {
    val stripHeight = if (state.archiveLed) 132.dp else 72.dp
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenArchive)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SsIcons.Archive, contentDescription = null, tint = PaperFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            SsSectionLabel("ARCHIVE")
        }
        Spacer(Modifier.height(10.dp))
        if (state.recentProofs.isEmpty()) {
            SsPanel(Modifier.fillMaxWidth().height(stripHeight)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("NO TAPE YET", style = MonoCaptionStyle, color = PaperFaint)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(stripHeight)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.recentProofs.forEach { thumb ->
                    Column(
                        Modifier
                            .width(stripHeight * 0.78f)
                            .fillMaxHeight(),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(InkSunken)
                                .vhsTracking(TapeWear.Worn, seed = thumb.id.hashCode().toFloat() % 1f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(thumb.dayLabel, style = MonoCaptionStyle, color = PaperFaint)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Below the fold
// ---------------------------------------------------------------------------

/**
 * The fold, made explicit.
 *
 * SPEC §4.3: *"Below the fold (a deliberate scroll, not a tab)"*. A chevron and a hairline. The
 * distinction between a scroll and a tab is the whole argument: a tab says "this is also always
 * true"; a scroll says "this is here when you want it". The trust ladder is the second thing, and
 * putting it in a tab bar would make the 7am screen answer two questions.
 */
@Composable
private fun TheFold() {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(SsIcons.ChevronDown, contentDescription = null, tint = Hairline, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * THE TRUST LADDER — the odometer made legible, and the only scoreboard that matters.
 *
 * Which habits are ENFORCED / AUDITED / TRUSTED / RETIRED. It is a scoreboard the user is *winning*
 * by definition, because the only direction the contract moves a habit on measured evidence is up,
 * and Rip has no vote in it. That is why this is the one scoreboard the app is allowed to show: it
 * is a record of jurisdiction the user has taken *back*, not a record of how he is being judged.
 *
 * TRUSTED and RETIRED are rendered in gold and paper. Not green. Never green. And ENFORCED is not
 * red — being under enforcement is not a failure state, it is the starting state, and colouring it
 * as an alarm would mean every user's first day is a screen full of alarms.
 */
@Composable
private fun TrustLadder(state: HomeState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SsSectionLabel("JURISDICTION · ${state.jurisdiction} OF 4")
        Spacer(Modifier.height(12.dp))
        state.ladder.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(InkRaised)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.habitId.uppercase(), style = LabelStyle, color = Paper)
                Spacer(Modifier.weight(1f))
                Text(
                    text = row.stage.name,
                    style = MonoCaptionStyle,
                    color = when (row.stage) {
                        // He still owns these. Gold is his colour, and the label is honest about it.
                        Stage.ENFORCED -> Gold
                        Stage.AUDITED -> PaperDim
                        // Off his desk. Paper — the user's own colour.
                        Stage.TRUSTED, Stage.RETIRED -> Paper
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        if (state.ladder.isEmpty()) {
            Text("Nothing is under his jurisdiction.", style = BodyStyle, color = PaperFaint)
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

/**
 * The top bar. Three icons and no title.
 *
 * No title because the app is not going to spend the top of the 7am screen telling a man what app he
 * just opened. No back arrow because this is home. No badge counts on the Tape icon — a badge is a
 * demand, and there is only ever one demand, and it is the card.
 */
@Composable
private fun TopBar(
    onOpenTape: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SsIcons.Tape,
            contentDescription = "The Tape",
            tint = PaperDim,
            modifier = Modifier.size(22.dp).clickable(onClick = onOpenTape),
        )
        Spacer(Modifier.width(18.dp))
        Icon(
            SsIcons.Archive,
            contentDescription = "Archive",
            tint = PaperDim,
            modifier = Modifier.size(22.dp).clickable(onClick = onOpenArchive),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            SsIcons.Settings,
            contentDescription = "Settings",
            tint = PaperDim,
            modifier = Modifier.size(22.dp).clickable(onClick = onOpenSettings),
        )
    }
}
