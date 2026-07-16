package com.secondspine.app.ui.tape

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondspine.app.ui.archive.ProofImage
import com.secondspine.app.ui.theme.ArenaStyle
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.Hairline
import com.secondspine.app.ui.theme.InkSunken
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.LedgerCounter
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.NumberStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.VerdictRed
import com.secondspine.app.ui.theme.vhsTracking
import com.secondspine.coach.ColdOpen
import com.secondspine.coach.CoachDownCard
import com.secondspine.coach.DeskRow
import com.secondspine.coach.LedgerCard
import com.secondspine.coach.Montage
import com.secondspine.coach.RipsDesk
import com.secondspine.coach.Roast
import com.secondspine.coach.RoastLine
import com.secondspine.coach.SignOff
import com.secondspine.coach.Stage
import com.secondspine.coach.TapeSegment
import com.secondspine.coach.TestWeekCard
import com.secondspine.coach.TheOffer
import com.secondspine.coach.TheOneThing
import com.secondspine.coach.Trends
import com.secondspine.coach.VerifiedVsClaimed
import com.secondspine.coach.WhatILearned
import kotlinx.coroutines.delay

/**
 * THE SEGMENTS.
 *
 * One composable per `TapeSegment`, and the dispatch in [TapeSegmentView] is exhaustive over the
 * sealed interface — so a segment added to `:coach` fails this file's compilation rather than
 * silently rendering nothing on a Sunday.
 *
 * The through-line for every one of these: **it is a roast and a real dashboard at once, and neither
 * is a wrapper for the other.** Every joke cites a true stat and taps into the dead-serious chart
 * behind it. When the comedy stops landing in month 7 — and it will — the taps still resolve to
 * charts he would have built himself, which is the only reason the open-rate can survive the comedy.
 */
@Composable
fun TapeSegmentView(
    segment: TapeSegment,
    state: TapeState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (segment) {
            is ColdOpen -> ColdOpenSegment(segment)
            is Montage -> MontageSegment(segment, state)
            is LedgerCard -> LedgerSegment(segment)
            is VerifiedVsClaimed -> VerifiedSegment(segment)
            is Roast -> RoastSegment(segment, state)
            is Trends -> TrendsSegment(segment, state)
            is WhatILearned -> SpokenSegment("WHAT I LEARNED ABOUT YOU", segment.line)
            is RipsDesk -> DeskSegment(segment)
            is TheOneThing -> SpokenSegment("THE ONE THING", segment.line)
            is TheOffer -> OfferSegment(segment)
            is SignOff -> SpokenSegment(null, segment.line)
            is CoachDownCard -> SpokenSegment(null, segment.line)
        }
    }
}

// ---------------------------------------------------------------------------
// 1. COLD OPEN — one number, no context
// ---------------------------------------------------------------------------

/**
 * **ELEVEN.** *That's how many times you opened the camera when I didn't ask you to.*
 *
 * One number, huge, and then the caption underneath it. Three seconds. The composer guarantees it is
 * never the same stat two weeks running (21-day skeleton lockout), which is what stops the opening
 * beat of the show from becoming a format.
 *
 * The number is display type rather than mono, and it is the only number in the app that is. Every
 * other number here is a *measurement* and mono is what makes it read as one; this one is a
 * **pronouncement**, delivered by a man on a tape, and it is the one place the character is allowed
 * to hold a digit.
 */
@Composable
private fun ColdOpenSegment(segment: ColdOpen) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = segment.value.uppercase(),
            style = ArenaStyle,
            color = Gold,
            modifier = Modifier.fillMaxWidth().vhsTracking(TapeWear.Worn, seed = 0.2f),
        )
        Spacer(Modifier.height(20.dp))
        Text(segment.caption, style = BodyStyle, color = PaperDim)
    }
}

// ---------------------------------------------------------------------------
// 2. THE MONTAGE — the reason he opens it at month 8
// ---------------------------------------------------------------------------

/**
 * THE MONTAGE. Every photo of the week, grid, animating in fast, **one thud each. Unnarrated.**
 *
 * This is the emotional core and the single structural reason the Tape survives its own worst week:
 * it is his own life, so it never depletes, and it does not need a good week to be worth looking at.
 * `Montage.cuttable` is false for exactly that reason — the ceremony cap can take the roast and take
 * the offer, and it can never take this.
 *
 * **Rip does not speak over it.** `Montage.narrated` is `false` in `:coach` and `Tape.spoken` does not
 * collect a line from it, because there is none. Un-scored, un-graded, full screen, not a card. The
 * restraint is the segment: a voiceover here would turn a photo journal back into a report, and the
 * whole trick is that it is a photo journal wearing an evidence-locker costume.
 *
 * The frames land one at a time, ~0.4s apart, with a haptic thud each — the pace `montageSeconds()`
 * budgets for. Not a fade-in stagger: each frame *arrives*, scaled from 1.06 on the house spring,
 * like a photograph being dealt onto a table.
 */
@Composable
private fun MontageSegment(segment: Montage, state: TapeState) {
    val frames = state.montage
    val haptics = LocalHapticFeedback.current
    var landed by remember(frames) { mutableStateOf(0) }

    LaunchedEffect(frames) {
        landed = 0
        // 0.4s per photo, clamped by the composer to a 3..20s segment. Fast enough to be a montage,
        // long enough to be his life.
        while (landed < frames.size) {
            delay(THUD_MS)
            landed++
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    if (frames.isEmpty()) {
        // A week with no photographs. Flat, mono, no character — he does not get to comment on an
        // empty week, and the composer has already suppressed his aggression if it was a collapse.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("NO FRAMES THIS WEEK", style = MonoCaptionStyle, color = PaperFaint)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (frames.size <= 4) 2 else 3),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        itemsIndexed(frames, key = { _, f -> f.id }) { index, frame ->
            val visible = index < landed
            val enter = remember(frame.id) { Animatable(1.06f) }
            LaunchedEffect(visible) { if (visible) enter.animateTo(1f, Motion.Slam) }

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = enter.value
                        scaleY = enter.value
                        // Not a fade: the cell is either dealt or it is not there yet. Alpha is
                        // binary, never interpolated. SPEC §4.9.
                        alpha = if (visible) 1f else 0f
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .vhsTracking(TapeWear.Worn, seed = (frame.id % 100) / 100f),
            ) {
                ProofImage(
                    path = frame.imagePath,
                    contentDescription = null,   // unnarrated, and that includes the accessibility tree
                    modifier = Modifier.fillMaxSize(),
                    targetPx = 240,
                )
            }
        }
    }
}

private const val THUD_MS = 400L

// ---------------------------------------------------------------------------
// 3. THE LEDGER — the only slow thing in the app
// ---------------------------------------------------------------------------

/**
 * THE LEDGER CARD. Cold, flat, court-clerk, monospace — and the tonal drop is what gives the rest of
 * the app its teeth. Cold data has no decay curve because it was never trying to entertain.
 *
 * **The counters are the one slow animation in the product.** [LedgerCounter] is a split-flap board
 * that takes up to 1.4 seconds and rolls through every intermediate digit at 90ms a flap. Six boards
 * flapping at once is the segment: you cannot help but watch a number land, and this is the one
 * moment the app asks for attention rather than demanding it.
 *
 * The counters are **paper, never red** — even the row that says CAUGHT FAKE. The docket is a
 * measurement, not an accusation. Red here would make the Ledger a scoreboard of shame, and a
 * scoreboard of shame is rumination infrastructure with a purge bolted on.
 *
 * **The zeroes are the point.** `LEDGER_CARD_ORDER` prints its six kinds even at zero, because a
 * docket that only prints your failures is not a docket, it is a grievance. A week of zeroes is a
 * scoreboard the user won.
 *
 * **The silent beat.** On a great week the composer sets `silentBeatSeconds = 4` and the card prints
 * empty. Four seconds of nothing on screen. Nobody needs it explained, so nothing explains it — there
 * is no copy in this branch at all, which took more restraint than any line in the file.
 *
 * The window label is printed in the corner, derived from `LEDGER_PURGE_DAYS` so it can never drift
 * from the purge. The user should be able to see that the number in front of him has an expiry.
 */
@Composable
private fun LedgerSegment(card: LedgerCard) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SsSectionLabel("THE LEDGER")
            Spacer(Modifier.weight(1f))
            Text(card.window, style = MonoCaptionStyle, color = PaperFaint)
        }
        Spacer(Modifier.height(12.dp))

        // RIP n : ARSEN n. Mono, flat, no colour. The clerk does not have a favourite.
        Text(card.score, style = NumberStyle, color = Paper)
        Spacer(Modifier.height(24.dp))

        // The board. Six split-flaps, landing together.
        card.rows.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { entry ->
                    LedgerCounter(
                        value = entry.count,
                        label = entry.kind.cardLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep the last short row on the same grid as the others.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(14.dp))
        }

        // The clerk's detail: "Thu 19:41  home x4". Pre-formatted upstream — `:coach` has no timezone.
        val details = card.rows.mapNotNull { r -> r.detail?.let { "${r.kind.cardLabel}  $it" } }
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            details.forEach {
                Text(it, style = MonoCaptionStyle, color = PaperFaint)
                Spacer(Modifier.height(3.dp))
            }
        }

        // THE CHEAT LEADERBOARD, DEMOTED TO ONE ROW. The joke is that by month 3 the default *is*
        // the observation: he stopped exploring, there is nothing to report, and reporting that
        // every week is the report. Suppressed entirely under a positive SCOFF screen — the
        // composer nulls it, so there is nothing here to gate.
        card.cheatRow?.let {
            Spacer(Modifier.height(14.dp))
            Row {
                Text("CHEAT", style = MonoCaptionStyle, color = PaperDim)
                Spacer(Modifier.width(12.dp))
                Text(it, style = MonoCaptionStyle, color = PaperDim)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. VERIFIED vs CLAIMED — three states, and a withdrawal
// ---------------------------------------------------------------------------

/**
 * VERIFIED · UNVERIFIED · CONTRADICTED, and the withdrawal that is not a fourth state.
 *
 * **UNVERIFIED has no colour, no sting and no count that means anything.** Phone in the locker, dead
 * battery, gym bans cameras. *Unverified is not false.* Being falsely accused by your own tool is a
 * one-shot trust-death, so the middle column is rendered in the faintest paper in the palette and is
 * given no more visual weight than the label above it.
 *
 * **CONTRADICTED is the only state with an edge**, and it is the only place on this screen
 * `VerdictRed` may be spent — and only when it is non-zero. A red zero would be the app rehearsing an
 * accusation it did not make.
 *
 * **The withdrawal is a warm line with no count and no chart.** FOR THE RECORD never reaches
 * CONTRADICTED, never reaches the Ledger, never demotes. Rendering it as a *number* would price it,
 * and the button has to be cheaper than lying at every hour, forever.
 */
@Composable
private fun VerifiedSegment(segment: VerifiedVsClaimed) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        SsSectionLabel("VERIFIED vs CLAIMED")
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tally("VERIFIED", segment.verified, Paper, Modifier.weight(1f))
            // No colour. No sting. Not a failure. Not a number he should feel anything about.
            Tally("UNVERIFIED", segment.unverified, PaperFaint, Modifier.weight(1f))
            Tally(
                "CONTRADICTED",
                segment.contradicted,
                if (segment.contradicted > 0) VerdictRed else PaperFaint,
                Modifier.weight(1f),
            )
        }

        segment.withdrawal?.let {
            Spacer(Modifier.height(28.dp))
            // Warm, paper, body type. Not a tally, not a chart, not a verdict.
            Text(it, style = BodyStyle, color = Paper)
        }
    }
}

@Composable
private fun Tally(
    label: String,
    value: Int,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(value.toString(), style = NumberStyle, color = tint)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MonoCaptionStyle, color = PaperFaint)
    }
}

// ---------------------------------------------------------------------------
// 5. THE ROAST — the joke IS the chart's headline
// ---------------------------------------------------------------------------

/**
 * THE ROAST. Max three lines. **Each line taps into the dead-serious chart behind it.**
 *
 * A fourth line is a podcast. `MAX_ROAST_LINES` is 3 and the composer has already trimmed; this
 * renders what survived.
 *
 * The tap is the whole architecture of the segment. The joke *is* the chart's headline, so the
 * segment is simultaneously the funniest thing in the week and the only weekly analytics view, and
 * neither is a wrapper for the other. Mono axes, no character, no jokes on axis labels.
 *
 * The expand is a slam, not a fade, and the chart replaces nothing — it opens underneath the line it
 * belongs to, because the line is the headline and a headline does not disappear when you read the
 * article.
 */
@Composable
private fun RoastSegment(segment: Roast, state: TapeState) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        SsSectionLabel("THE ROAST")
        Spacer(Modifier.height(6.dp))
        Text("TAP ANY LINE", style = MonoCaptionStyle, color = PaperFaint)
        Spacer(Modifier.height(20.dp))

        segment.lines.forEach { roast ->
            RoastRow(roast, state.chartSeries[roast.chart].orEmpty())
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun RoastRow(roast: RoastLine, series: List<Float>) {
    var open by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().clickable { open = !open }) {
        RipSpeech(
            text = roast.line.text,
            register = roast.line.register,
            modifier = Modifier.fillMaxWidth(),
        )
        if (open) {
            Spacer(Modifier.height(12.dp))
            ChartPanel(roast.chart, series)
        }
    }
}

/**
 * THE CHART BEHIND THE JOKE. **Zero character.** Mono axes. No gold anywhere.
 *
 * This is the half of the segment that has to still be here in month 7 when the comedy has stopped
 * landing, so it is built like an instrument and not like a punchline: the descriptor is printed as a
 * mono caption, the series is drawn as a flat paper line on a hairline baseline, and nothing is
 * coloured, celebrated or judged.
 *
 * When the series is absent the frame renders with `NO SERIES` and no line. That is deliberate and it
 * is the honest failure: `RoastLine.chart` is a *descriptor* — `:coach` is pure JVM and hands over no
 * numbers — so a chart with no wired series must say so rather than draw a plausible curve. The one
 * segment whose value rests on the taps resolving to real charts is the one segment that must never
 * fake one.
 */
@Composable
private fun ChartPanel(descriptor: String, series: List<Float>) {
    SsPanel(Modifier.fillMaxWidth(), wear = TapeWear.None) {
        Column(Modifier.padding(14.dp)) {
            Text(descriptor.uppercase(), style = MonoCaptionStyle, color = PaperDim)
            Spacer(Modifier.height(12.dp))
            if (series.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(90.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("NO SERIES", style = MonoCaptionStyle, color = PaperFaint)
                }
            } else {
                Sparkline(
                    series = series,
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                )
            }
        }
    }
}

/**
 * A flat line on a hairline baseline. Paper, 2px, round-capped — the same stroke law as the icon set,
 * because a chart in this app is instrumentation and instrumentation is one system.
 *
 * No fill, no gradient, no dots, no labels on the points. A filled area chart is a chart trying to
 * feel like something.
 */
@Composable
private fun Sparkline(
    series: List<Float>,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = Paper,
) {
    Canvas(modifier) {
        if (series.size < 2) return@Canvas
        val min = series.min()
        val max = series.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val dx = size.width / (series.size - 1)

        // The baseline. One hairline, so the line has something to be measured against.
        drawLine(
            color = Hairline,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f,
        )

        val path = Path()
        series.forEachIndexed { i, v ->
            val x = i * dx
            val y = size.height - ((v - min) / span) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = tint, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------
// 6. TRENDS — the real dashboard. Zero character.
// ---------------------------------------------------------------------------

/**
 * THE REAL DASHBOARD. **Zero character.** This card exists to prove this is a real app.
 *
 * `Trends.cuttable` is false: the ceremony cap may take the roast, the offer, the cold open and the
 * desk, and it may never take this. On the week nothing was funny, this is what is still good.
 *
 * It holds the one genuinely compounding number — Test Week — which renders on *every* edition and
 * not only on test weeks, because a number that only exists six times is a number nobody trusts. On
 * the other seven weeks it carries the standing PB, and that is the compounding part.
 *
 * **The weight EWMA:** grey, cold, unvoiced, **no number as headline, no arrow, no colour, no goal,
 * no BMI, no penalty**. `Trends.weightEwma` is a Boolean and the composer has already forced it false
 * under a positive SCOFF screen — where body metrics are permanently unavailable, not hidden and not
 * rendered. Rip has no read access to this table and never learns the number exists.
 */
@Composable
private fun TrendsSegment(segment: Trends, state: TapeState) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        SsSectionLabel("TRENDS")
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tally("CONSISTENCY", segment.consistencyPct, Paper, Modifier.weight(1f))
            segment.restingHr?.let { Tally("RESTING HR", it, Paper, Modifier.weight(1f)) }
        }

        Spacer(Modifier.height(28.dp))
        TestWeek(segment.testWeek)

        if (segment.weightEwma) {
            Spacer(Modifier.height(28.dp))
            WeightTrend(state.weightSeries)
        }
    }
}

/**
 * TEST WEEK — the one genuinely compounding number, and the app's actual outcome variable.
 *
 * Six dated, self-generated *holy shit I did that* moments across ten months. It requires no writer,
 * it improves roughly monotonically, and it is immune to the gaslighting a scale does. It is why
 * weight does not need to be an outcome variable, and it is the fourth reading of the product's own
 * name: the measuring tape is the one body metric we would allow ourselves to like, and we don't ship
 * it. Nobody has to explain why there is no scale in a product called TAPE.
 *
 * The previous PB is a **dashed rule**, not a comparison and not a delta. He beat it or he did not;
 * the app does not editorialise, and there is no red and no green anywhere near it.
 */
@Composable
private fun TestWeek(card: TestWeekCard) {
    SsPanel(Modifier.fillMaxWidth(), wear = TapeWear.None) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.metric.uppercase(), style = MonoCaptionStyle, color = PaperDim)
                Spacer(Modifier.weight(1f))
                if (card.freshThisWeek) {
                    Text("TEST WEEK", style = MonoCaptionStyle, color = PaperFaint)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(card.value, style = NumberStyle, color = Paper)

            card.previousPb?.let { pb ->
                Spacer(Modifier.height(10.dp))
                DashedRule()
                Spacer(Modifier.height(6.dp))
                Text(
                    "PB $pb" + (card.pbWeek?.let { " · WEEK $it" } ?: ""),
                    style = MonoCaptionStyle,
                    color = PaperFaint,
                )
            }

            // THE ONE SINCERE CONGRATULATION — `SINCERE_ONE`. Spent once, ever.
            //
            // GHOST, per RESOLUTIONS §B: it is §2.6's GHOST scene on the first strict pull-up, and
            // GHOST is the register where the pitch is gone. He stops selling to say it. The composer
            // has already refused to let it appear on a week where nothing was tested (`gated()`), so
            // there is no gate to repeat here — only the rendering of a thing that will happen once.
            card.sincereCongratulation?.let {
                Spacer(Modifier.height(20.dp))
                RipSpeech(text = it, register = com.secondspine.coach.Register.GHOST)
            }
        }
    }
}

@Composable
private fun DashedRule() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = Hairline,
                start = Offset(x, 0f),
                end = Offset((x + 4f).coerceAtMost(size.width), 0f),
                strokeWidth = 1f,
            )
            x += 8f
        }
    }
}

/**
 * WEIGHT. A grey line. That is the entire feature.
 *
 * No number as headline. No arrow. No goal. No BMI. No red, no green, no colour of any kind — it is
 * drawn in [PaperFaint], the same tone as a caption, because it is not a result and it is not
 * something the user is being asked to feel about. It is never an input to any habit's compliance and
 * it can never produce a penalty. `Pillar.WEIGHT` exists in `:coach` only so the guardrail can refuse
 * it by name.
 *
 * The label says "TREND" and not "WEIGHT", and the axis carries no units, because the moment a
 * kilogram appears as text on this screen it becomes a number to beat.
 */
@Composable
private fun WeightTrend(series: List<Float>) {
    Column(Modifier.fillMaxWidth()) {
        Text("TREND", style = MonoCaptionStyle, color = PaperFaint)
        Spacer(Modifier.height(8.dp))
        if (series.size < 2) {
            Text("NOT ENOUGH DATA", style = MonoCaptionStyle, color = PaperFaint)
        } else {
            Sparkline(
                series = series,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                tint = PaperFaint,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 8. RIP'S DESK — he narrates his own decline
// ---------------------------------------------------------------------------

/**
 * RIP'S DESK. Each habit he still owns is a row. **Each graduation deletes a row, permanently, and he
 * cannot add one back.**
 *
 * This is the arc, and there are no acts, no eras, no authored beats and no scheduled reinvention
 * behind it — one integer, and he watches it fall. The contract graduates habits on measured evidence
 * and he has *no vote*, which is why the segment works: it is a countdown to firing the coach that
 * only the user can advance.
 *
 * A graduated row is not removed from the render — it is drawn **struck through**, in paper, above
 * the rest. The amputation is the event. Showing only what remains would hide the only thing this
 * segment is about.
 */
@Composable
private fun DeskSegment(segment: RipsDesk) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        SsSectionLabel("RIP'S DESK")
        Spacer(Modifier.height(16.dp))

        segment.rows.forEach { row ->
            DeskRowView(row)
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(24.dp))
        RipSpeech(
            text = segment.line.text,
            register = segment.line.register,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeskRowView(row: DeskRow) {
    val gone = row.stage == Stage.TRUSTED || row.stage == Stage.RETIRED
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(InkSunken)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.habitId.uppercase(),
            style = LabelStyle,
            // Gold = still his. Paper = the user took it back. Never green, and ENFORCED is never
            // red: being under enforcement is the starting state, not a failure.
            color = if (gone) Paper else Gold,
            textDecoration = if (gone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = row.daysToGraduation?.let { "$it DAYS" } ?: row.stage.name,
            style = MonoCaptionStyle,
            color = PaperFaint,
        )
    }
}

// ---------------------------------------------------------------------------
// 10. THE OFFER / THE RECKONING
// ---------------------------------------------------------------------------

/**
 * THE OFFER — next week's bet. **Rip stakes jurisdiction, never pixels.**
 *
 * The only thing he has to lose is the job, so it is the only thing he is allowed to wager. He cannot
 * offer "a week of silence" or points or a badge; there is nothing else in his pockets. That
 * constraint is what makes the bet mean anything: every offer he makes is an offer to be fired
 * faster.
 *
 * On a great week the composer replaces the offer with **THE RECKONING** — he reads the remaining
 * rows and counts them out loud. It is the only sentimental Tape in the product and it is sentimental
 * about his own unemployment.
 */
@Composable
private fun OfferSegment(segment: TheOffer) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        SsSectionLabel(if (segment.reckoning) "THE RECKONING" else "THE OFFER")
        Spacer(Modifier.height(16.dp))
        RipSpeech(
            text = segment.line.text,
            register = segment.line.register,
            modifier = Modifier.fillMaxWidth(),
        )

        if (segment.reckoning && segment.remaining.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            // What is left of the desk, counted out. No commentary — he already said it.
            segment.remaining.forEach { row ->
                DeskRowView(row)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The spoken segments
// ---------------------------------------------------------------------------

/**
 * One line of his, on a screen of its own — WHAT I LEARNED, THE ONE THING, the SIGN-OFF, the COACH
 * DOWN card.
 *
 * The sign-off and the COACH DOWN card get **no label**, and that is the point of the nullable: a
 * section header over the last thing he says would be the app framing him, and on the collapse week
 * a header saying "COACH DOWN" would be the app telling the user it has noticed he fell apart. The
 * card is one sentence and no chrome.
 */
@Composable
private fun SpokenSegment(label: String?, line: com.secondspine.coach.RipLine) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (label != null) {
            SsSectionLabel(label)
            Spacer(Modifier.height(16.dp))
        }
        RipSpeech(text = line.text, register = line.register, modifier = Modifier.fillMaxWidth())
    }
}
