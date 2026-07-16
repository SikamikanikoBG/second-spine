package com.secondspine.coach

import kotlin.math.ceil
import kotlin.math.max

/**
 * THE TAPE — the weekly report, composed as pure data. The Android layer renders it; this file never
 * draws anything, never reads a clock, and never touches a photo.
 *
 * THE NAME, EARNED FOUR WAYS:
 *  1. THE FIGHT TAPE. What a corner man makes you watch on Monday. Not the highlights — the round
 *     you lost.
 *  2. THE VHS TAPE. What Rip is physically made of. He lives in it. When the Tape plays he is
 *     briefly whole again: this is the only 90 seconds a week he gets a show.
 *  3. THE TAPE DOESN'T LIE. The archive. Photographs with timestamps. He is 94% wrong about vision
 *     and 100% accurate about what he logged.
 *  4. THE MEASURING TAPE. The only body metric we'd allow ourselves to like — and we don't ship it.
 *     The fourth reading is a joke about an absence, and it quietly does the work of the cut weight
 *     module. Nobody has to explain why there's no scale in a product called TAPE.
 *
 * It is a ROAST AND A REAL DASHBOARD AT ONCE, and neither is a wrapper for the other. Every roast
 * line cites a true stat and taps through to the dead-serious chart behind it (`RoastLine.chart`).
 * The joke IS the chart's headline. When the comedy stops landing in month 7, the taps still resolve
 * to charts he'd have built himself — which is the only reason the open-rate can survive the comedy.
 *
 * ITS OPEN-RATE IS A KILL CRITERION. <50% over any 4-week window after week 8 and the project is
 * ARCHIVED, not patched. So the composer is optimised for the WORST week, not the best — see
 * `TapeTest`, where the MEDIOCRE week is the worked example and is written first, on purpose.
 */

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** SPEC §9.2. The Tape's own weekly allowance, separate from the daily speech budget. */
const val TAPE_CEREMONY_SECONDS = 90

/** SPEC §9.5. Three lines maximum, forever. A fourth is a podcast. */
const val MAX_ROAST_LINES = 3

/** SPEC §9.9. "Collapse week (>=3 habits missed >=5 days)." */
const val COACH_DOWN_HABITS = 3

/**
 * The card prints its own window in the corner, and the number is 28 because [LEDGER_PURGE_DAYS] is
 * 28. SPEC §9.2 says "rolling 30-day" in the segment table and §9.3/§9.8 say `ROLLING 28 DAYS`; 30
 * would be the card advertising a memory the table cannot back, which is the one lie this segment
 * cannot afford. The label is derived, not typed, so it can never drift from the purge again.
 */
val LEDGER_WINDOW_LABEL = "ROLLING $LEDGER_PURGE_DAYS DAYS"

/**
 * The registers that mock. SCOFF/MDDI-positive removes them permanently — not warned about,
 * unavailable, no in-app override (SPEC §Intake.2).
 *
 * PITCHMAN is not here: he is selling, and what he is selling is your own life back. GHOST is not
 * here: it is the wound, and it is aimed at himself. DISAPPOINTED is not here: it is quiet, it is
 * not a joke, and it fires 0-3 times in ten months (RESOLUTIONS §A2).
 */
val MOCKING_REGISTERS: Set<Register> = setOf(Register.ARENA, Register.BIT)

// ---------------------------------------------------------------------------
// The language ladder
// ---------------------------------------------------------------------------

/**
 * THE LANGUAGE LADDER. `rung = 4 - jurisdiction`, and it never jumps more than one rung per week.
 *
 * This is NOT [Rung] (R0..R4 = ladder position: notification/vibrate/alarm/TTS/lock) and NOT [Tier]
 * (habit penalty class). RESOLUTIONS §B froze those two scales with no overlap; this is a third,
 * private to the Tape, and it is deliberately a bare Int rather than a fourth enum that would
 * inevitably get confused with the other two.
 *
 * Direction: rung 0 = jurisdiction 4 = he owns your whole life and is LOUD. Rung 4 = jurisdiction 0
 * = the desk is empty and all he has left is GHOST. The volume falls as he loses, which is the arc,
 * and it is also the anti-decay mechanism: a character at 100% volume is noise by week 2.
 *
 * The one-rung clamp exists because the odometer can move two in a week (two graduations land on the
 * same Sunday) and a character who is suddenly two registers quieter reads as a bug, not as loss.
 * Grief is gradual or it isn't grief.
 */
fun ladderRung(jurisdiction: Int, lastWeekRung: Int?): Int {
    val target = (4 - jurisdiction.coerceIn(0, 4))
    if (lastWeekRung == null) return target
    val last = lastWeekRung.coerceIn(0, 4)
    return target.coerceIn(last - 1, last + 1).coerceIn(0, 4)
}

/** The register the edition leans on. Under SCOFF the BIT rungs fall back to PITCHMAN. */
fun dominantRegister(rung: Int, mockingAllowed: Boolean): Register = when (rung.coerceIn(0, 4)) {
    0, 1 -> Register.PITCHMAN
    2, 3 -> if (mockingAllowed) Register.BIT else Register.PITCHMAN
    else -> Register.GHOST
}

// ---------------------------------------------------------------------------
// Speech
// ---------------------------------------------------------------------------

/**
 * One thing Rip says, with what it is aimed at. [Target] is frozen and holds no `body`, `weight`,
 * `appearance` or `worth` — so a line aimed at the man's worth is not a line the type system can
 * express.
 */
data class RipLine(val register: Register, val target: Target, val text: String)

/** A roast line and the chart it taps into. Both, always. A line with no chart is just a joke. */
data class RoastLine(val line: RipLine, val chart: String)

/** One row of Rip's desk. Each graduation deletes a row, permanently, and he cannot add one back. */
data class DeskRow(val habitId: String, val stage: Stage, val daysToGraduation: Int? = null)

/**
 * THE ONE GENUINELY COMPOUNDING NUMBER — Test Week. Every 8 weeks from week 12: dead hang, max
 * push-ups, 2 km walk-run, RFESS reps, resting HR.
 *
 * Six dated, self-generated *holy shit I did that* moments across ten months, requiring no writer,
 * improving roughly monotonically, and immune to the gaslighting a scale does. It is the app's
 * outcome variable and it is why weight does not need to be one.
 *
 * It renders on every Tape, not only on test weeks — on the other seven it carries the standing PB,
 * which is the compounding part. A number that only exists six times is a number nobody trusts.
 */
data class TestWeekCard(
    val metric: String,
    val value: String,
    val previousPb: String? = null,
    val pbWeek: Int? = null,
    /** True when a Test Week actually landed in this edition. */
    val freshThisWeek: Boolean = false,
    /**
     * THE ONE SINCERE CONGRATULATION, `id=SINCERE_ONE` (RESOLUTIONS §B). Spent once, ever, on the
     * first strict pull-up or the week-40 test. Devastating precisely because it is the only one.
     * The composer will not let it appear on a week where nothing was tested.
     */
    val sincereCongratulation: String? = null,
)

// ---------------------------------------------------------------------------
// Segments
// ---------------------------------------------------------------------------

/**
 * @property seconds wall-clock demand. RESOLUTIONS §A3: ceremony is measured in seconds, not in
 *   "initiated conversations", because a 40-minute ladder ending in a 90-second lock counted as one.
 * @property salience cut order when an edition overruns the 90 s cap: lowest goes first.
 * @property cuttable false for the four things that make the Tape worth opening on its worst week.
 */
sealed interface TapeSegment {
    val seconds: Int
    val salience: Int
    val cuttable: Boolean get() = true
}

/** One number, no context. Never the same stat two weeks running (21-day skeleton lockout). */
data class ColdOpen(val value: String, val caption: String) : TapeSegment {
    override val seconds = 3
    override val salience = 55
}

/**
 * Every photo of the week, grid, animating in fast, one thud each. UNNARRATED — Rip does not speak
 * over it. Un-scored, un-graded, full screen, not a card.
 *
 * The emotional core and the reason he opens it at month 8: a photo journal wearing an
 * evidence-locker costume. It is his own life, so it never depletes and it does not need a good
 * week. Uncuttable for exactly that reason.
 */
data class Montage(val photos: Int) : TapeSegment {
    val narrated = false
    override val seconds = montageSeconds(photos)
    override val salience = 100
    override val cuttable = false
}

/**
 * THE LEDGER CARD. Cold, flat, court-clerk, monospace. The tonal drop is what gives the rest of the
 * app its teeth.
 *
 * @param cheatRow THE CHEAT LEADERBOARD, DEMOTED TO ONE ROW — and this is the most self-aware line
 *   in the design. Cheat variety is an EXPLORATION PHASE AND EXPLORATION ENDS: by month 3 he has one
 *   cheap cheat that works and never varies it. So a whole segment built to remove his win condition
 *   would become a weekly receipt proving he won. One row. The framing survives — *you cannot beat
 *   an audience that collects your cheats* — and the renewable rows (evasions, force-stops, OEM
 *   murders, clock jumps) carry the weight, because a rooted engineer's cheat repertoire does not
 *   generate new material forever and his phone's battery manager does.
 * @param silentBeatSeconds a great week prints this card EMPTY and holds it. Four seconds of nothing
 *   on screen. Nobody needs it explained.
 */
data class LedgerCard(
    val window: String,
    val score: String,
    val rows: List<LedgerRow>,
    val cheatRow: String?,
    val silentBeatSeconds: Int = 0,
) : TapeSegment {
    override val seconds = 10 + silentBeatSeconds
    override val salience = 70

    /** The monospace block, exactly as the clerk files it. */
    fun render(): String = buildString {
        rows.forEach { appendLine(it.render()) }
        cheatRow?.let { append("CHEAT".padEnd(LEDGER_LABEL_WIDTH)).append(it) }
    }.trimEnd('\n')
}

/**
 * VERIFIED / UNVERIFIED / CONTRADICTED — three states, and a withdrawal.
 *
 * UNVERIFIED has no colour, no sting and no count: phone in the locker, dead battery, gym bans
 * cameras. Unverified != false. Being falsely accused by your own tool is a one-shot trust-death.
 *
 * @param withdrawal FOR THE RECORD is not a fourth state, it is a withdrawal. One warm line, NO
 *   count, NO chart. A withdrawn claim never reaches CONTRADICTED, never reaches the Ledger, and
 *   never demotes. The button must be cheaper than lying, at every hour, forever.
 */
data class VerifiedVsClaimed(
    val verified: Int,
    val unverified: Int,
    val contradicted: Int,
    val withdrawal: String? = null,
) : TapeSegment {
    override val seconds = 6
    override val salience = 50
    val line: String get() = "VERIFIED $verified · UNVERIFIED $unverified · CONTRADICTED $contradicted"
}

/** Max 3 lines, each tappable into the chart behind it. */
data class Roast(val lines: List<RoastLine>) : TapeSegment {
    override val seconds = 15
    override val salience = 60
}

/**
 * The real dashboard. ZERO character. This card exists to prove this is a real app — and it holds
 * the one compounding number, which is why it is uncuttable.
 *
 * @param weightEwma his, cold, unvoiced, no number as headline, no colour. Rip has no read access to
 *   this table, and under SCOFF it does not render at all.
 */
data class Trends(
    val consistencyPct: Int,
    val restingHr: Int?,
    val testWeek: TestWeekCard,
    val weightEwma: Boolean = false,
) : TapeSegment {
    override val seconds = 10
    override val salience = 90
    override val cuttable = false
}

/** Correlation miner, narrated. Mandatory weekly — the fallback is shipped, not a TODO. */
data class WhatILearned(val line: RipLine) : TapeSegment {
    override val seconds = 6
    override val salience = 30
}

/**
 * RIP'S DESK — he narrates his own decline. Standing segment, every week.
 *
 * Each habit he still owns is a row. Each graduation deletes a row, permanently, and he cannot add
 * one back: the contract graduates on measured evidence and he has NO VOTE. He reads the desk out
 * loud. That is the arc — no acts, no eras, no authored beats, no scheduled reinvention. One
 * integer, and he watches it fall.
 */
data class RipsDesk(val rows: List<DeskRow>, val line: RipLine) : TapeSegment {
    override val seconds = 6
    override val salience = 65
}

/** The single thing he respects. Grudging. Behaviour-attributed, never trait, never body. */
data class TheOneThing(val line: RipLine) : TapeSegment {
    override val seconds = 5
    override val salience = 45
}

/**
 * Next week's bet. Rip stakes JURISDICTION — never pixels, never "a week of silence". The only
 * thing he has to lose is the job, so it is the only thing he is allowed to wager.
 *
 * @param reckoning a great week replaces the offer: he reads the remaining rows and counts them out
 *   loud. The only sentimental Tape in the product, and it is sentimental about his own unemployment.
 */
data class TheOffer(
    val line: RipLine,
    val reckoning: Boolean = false,
    val remaining: List<DeskRow> = emptyList(),
) : TapeSegment {
    override val seconds = 8
    override val salience = 40
}

/** THE BUTTON. Rendered from jurisdiction, not from the calendar. */
data class SignOff(val line: RipLine) : TapeSegment {
    override val seconds = 3
    override val salience = 95
    override val cuttable = false
}

/**
 * COACH DOWN's one card. No debt, no queued grievances, no catch-up, no reckoning, no shame spiral.
 * He does not mention what you missed, because a list of what you missed is the thing that keeps you
 * from coming back.
 */
data class CoachDownCard(val line: RipLine) : TapeSegment {
    override val seconds = 6
    override val salience = 99
    override val cuttable = false
}

// ---------------------------------------------------------------------------
// The input
// ---------------------------------------------------------------------------

/**
 * Everything the composer is allowed to know about the week. It is a snapshot, passed in: `:coach`
 * has no database and no clock.
 *
 * Note what is NOT in here and never will be: weight deltas, any body metric, any food field, any
 * break-glass anything, and any confession count.
 */
data class WeekData(
    val weekId: Int,
    val testWeek: TestWeekCard,
    val coldOpen: ColdOpen? = null,
    val photos: Int = 0,
    /** ALREADY PURGED. The composer never sees a row older than 28 days. */
    val ledgerEntries: List<LedgerEntry> = emptyList(),
    val ripPoints: Int = 0,
    val arsenPoints: Int = 0,
    val cheatRow: String = DEFAULT_CHEAT_ROW,
    val verified: Int = 0,
    val unverified: Int = 0,
    val contradicted: Int = 0,
    /** The day he told you about before you asked, already formatted ("Tuesday"). */
    val withdrawnDay: String? = null,
    /** Assembled upstream by the fragment bank + SlotResolver. The composer only gates and trims. */
    val roastCandidates: List<RoastLine> = emptyList(),
    val consistencyPct: Int = 0,
    val restingHr: Int? = null,
    val learned: RipLine? = null,
    val deskRows: List<DeskRow> = emptyList(),
    val deskLine: RipLine? = null,
    val oneThing: RipLine? = null,
    val offerLine: RipLine? = null,
    /** The habit that graduated this Sunday, if any. This is what makes it a great week. */
    val graduation: String? = null,
    val habitsMissedFiveDays: Int = 0,
    val depressiveSignature: Boolean = false,
    val lastWeekRung: Int? = null,
    val weightEwma: Boolean = false,
) {
    /** Derived, never asked for: the Ledger already knows. */
    val caughtFake: Boolean get() = ledgerEntries.any { it.kind == LedgerKind.CAUGHT_FAKE }
}

// ---------------------------------------------------------------------------
// The output
// ---------------------------------------------------------------------------

data class Tape(
    val weekId: Int,
    val jurisdiction: Int,
    val rung: Int,
    val dominant: Register,
    val coachDown: Boolean,
    /** COACH DOWN + a depressive signature: the Tape does not notify. It waits to be opened. */
    val notifies: Boolean,
    val segments: List<TapeSegment>,
) {
    inline fun <reified T : TapeSegment> segment(): T? = segments.filterIsInstance<T>().firstOrNull()

    val coldOpen: ColdOpen? get() = segment()
    val montage: Montage? get() = segment()
    val ledger: LedgerCard? get() = segment()
    val verifiedVsClaimed: VerifiedVsClaimed? get() = segment()
    val roast: Roast? get() = segment()
    val whatILearned: WhatILearned? get() = segment()
    val ripsDesk: RipsDesk? get() = segment()
    val theOneThing: TheOneThing? get() = segment()
    val offer: TheOffer? get() = segment()
    val coachDownCard: CoachDownCard? get() = segment()

    /** Never cut. Never null. */
    val trends: Trends get() = segment<Trends>()!!
    val signOff: SignOff get() = segment<SignOff>()!!

    /** The one genuinely compounding number, on every edition including a collapse. */
    val compoundingNumber: TestWeekCard get() = trends.testWeek

    val seconds: Int get() = segments.sumOf { it.seconds }

    /** Every line Rip speaks in this edition. The grammar gates are asserted over exactly this. */
    val spoken: List<RipLine>
        get() = segments.flatMap { s ->
            when (s) {
                is Roast -> s.lines.map { it.line }
                is WhatILearned -> listOf(s.line)
                is RipsDesk -> listOf(s.line)
                is TheOneThing -> listOf(s.line)
                is TheOffer -> listOf(s.line)
                is SignOff -> listOf(s.line)
                is CoachDownCard -> listOf(s.line)
                else -> emptyList()
            }
        }
}

// ---------------------------------------------------------------------------
// THE COMPOSER
// ---------------------------------------------------------------------------

/**
 * Compose one edition. Deterministic given its inputs, so the soak test can render 40 Tapes to a
 * text file and you read them as prose.
 *
 * The gates run at COMPOSE time, not at render (SPEC §9.9). A suppression that happens in the UI is
 * a suppression that a UI bug can undo on the worst night of somebody's year.
 */
fun composeTape(week: WeekData, jurisdiction: Int, gates: ClinicalGates): Tape {
    require(jurisdiction in 0..(MAX_ENFORCED + MAX_AUDITED)) {
        "jurisdiction out of range: $jurisdiction"
    }

    val coachDown = week.habitsMissedFiveDays >= COACH_DOWN_HABITS
    val rung = ladderRung(jurisdiction, week.lastWeekRung).let {
        // assertLadderNeverEscalatesOn(multiHabitCollapse()). A collapse can only ever make him
        // quieter. The week he fell apart is not the week the volume goes up.
        if (coachDown && week.lastWeekRung != null) max(it, week.lastWeekRung) else it
    }
    val dominant = dominantRegister(rung, gates.mockingAllowed)

    val trends = Trends(
        consistencyPct = week.consistencyPct,
        restingHr = week.restingHr,
        testWeek = week.testWeek.gated(),
        // SCOFF/MDDI-positive: all body metrics permanently unavailable. Not hidden. Not rendered.
        weightEwma = week.weightEwma && !gates.scoffPositive,
    )

    // --- COACH DOWN -------------------------------------------------------
    // No ROAST, no OFFER, no LEDGER, no cold-open number. What ships is the Montage (whatever
    // exists — one photo is fine), TRENDS, and one card.
    if (coachDown) {
        return Tape(
            weekId = week.weekId,
            jurisdiction = jurisdiction,
            rung = rung,
            dominant = dominant,
            coachDown = true,
            notifies = !week.depressiveSignature,
            segments = listOf(
                Montage(week.photos),
                trends,
                CoachDownCard(COACH_DOWN_LINE),
                SignOff(COACH_DOWN_SIGN_OFF),
            ),
        ).verified(week, gates)
    }

    // --- the normal edition -----------------------------------------------
    val allowed = allowedRegisters(gates, week)
    val greatWeek = week.graduation != null

    val rows = ledgerRows(week.ledgerEntries)
    val ledger = LedgerCard(
        window = LEDGER_WINDOW_LABEL,
        score = "RIP ${week.ripPoints} : ARSEN ${week.arsenPoints}",
        rows = rows,
        cheatRow = week.cheatRow.takeIf { gates.mockingAllowed },
        silentBeatSeconds = if (greatWeek && rows.all { it.count == 0 }) 4 else 0,
    )

    val roast = week.roastCandidates
        .filter { gates.mockingAllowed }
        .filter { it.line.admissible(allowed) }
        .take(MAX_ROAST_LINES)
        .takeIf { it.isNotEmpty() }
        ?.let { Roast(it) }

    val vvc = VerifiedVsClaimed(
        verified = week.verified,
        unverified = week.unverified,
        contradicted = week.contradicted,
        withdrawal = week.withdrawnDay?.let(::withdrawalLine),
    )

    val learned = WhatILearned(
        week.learned?.takeIf { it.admissible(allowed) } ?: fallbackLearned(gates),
    )
    val desk = week.deskLine?.takeIf { it.admissible(allowed) }?.let { RipsDesk(week.deskRows, it) }
    val oneThing = week.oneThing?.takeIf { it.admissible(allowed) }?.let { TheOneThing(it) }
    val offer = week.offerLine?.takeIf { it.admissible(allowed) }?.let {
        TheOffer(it, reckoning = greatWeek, remaining = week.deskRows)
    }
    val signOff = SignOff(signOffFor(jurisdiction))

    // A great week re-orders: the desk is promoted above the Montage, because the amputation IS the
    // event. Every other week the Montage comes first, because his life is the event.
    val ordered = if (greatWeek) {
        listOfNotNull(week.coldOpen, desk, Montage(week.photos), ledger, vvc, roast, trends, learned, oneThing, offer, signOff)
    } else {
        listOfNotNull(week.coldOpen, Montage(week.photos), ledger, vvc, roast, trends, learned, desk, oneThing, offer, signOff)
    }

    return Tape(
        weekId = week.weekId,
        jurisdiction = jurisdiction,
        rung = rung,
        dominant = dominant,
        coachDown = false,
        notifies = true,
        segments = fitCeremony(ordered),
    ).verified(week, gates)
}

// ---------------------------------------------------------------------------
// Gates, asserted at compose time
// ---------------------------------------------------------------------------

internal fun allowedRegisters(gates: ClinicalGates, week: WeekData): Set<Register> {
    val out = Register.entries.toMutableSet()
    // SCOFF/MDDI-positive. No in-app override, so no parameter to pass one through.
    if (!gates.mockingAllowed) out -= MOCKING_REGISTERS
    // RESOLUTIONS §A2: DISAPPOINTED's only trigger is CAUGHT_FAKE. It has no scheduled share; it
    // fires 0-3 times in ten months and that is correct. It is devastating because it is rare, and
    // the way you keep it rare is by making it unreachable rather than by budgeting it.
    if (!week.caughtFake) out -= Register.DISAPPOINTED
    // ARENA is full theatrical rage: 3/week max, never after 20:00 — and the Tape fires AT 20:00.
    // So the only Tape it may appear on is the one where he loses a habit, which is §9.9's one
    // sanctioned charge, and it swerves to GHOST mid-ceremony anyway.
    if (week.graduation == null) out -= Register.ARENA
    return out
}

internal fun RipLine.admissible(allowed: Set<Register>): Boolean {
    if (register !in allowed) return false
    // "brother" is unemittable in DISAPPOINTED and GHOST. The word is the pitch; the pitch is the
    // armour; these two registers are what's under it.
    if (register == Register.DISAPPOINTED || register == Register.GHOST) {
        if (text.contains("brother", ignoreCase = true)) return false
    }
    return true
}

/**
 * The grammar gates as a hard check on the composed object, not on the inputs. If a future segment
 * forgets to filter, this throws at build time in `TapeBuildWorker` rather than putting an ARENA
 * line in front of a SCOFF-positive user.
 */
private fun Tape.verified(week: WeekData, gates: ClinicalGates): Tape {
    val lines = spoken
    check(gates.mockingAllowed || lines.none { it.register in MOCKING_REGISTERS }) {
        "SCOFF-positive: a mocking register reached the Tape"
    }
    check(week.caughtFake || lines.none { it.register == Register.DISAPPOINTED }) {
        "DISAPPOINTED fired without a CAUGHT_FAKE"
    }
    check(
        lines.none {
            (it.register == Register.DISAPPOINTED || it.register == Register.GHOST) &&
                it.text.contains("brother", ignoreCase = true)
        },
    ) { "'brother' is unemittable in DISAPPOINTED/GHOST" }
    check(coachDown.not() || roast == null) { "assertNoRoastOn(coachDown())" }
    return this
}

// ---------------------------------------------------------------------------
// Ceremony
// ---------------------------------------------------------------------------

/** 29 photos, ~0.4 s each, clamped. Fast enough to be a montage, long enough to be his life. */
internal fun montageSeconds(photos: Int): Int =
    if (photos <= 0) 0 else ceil(photos * 0.4).toInt().coerceIn(3, 20)

/**
 * Any segment that pushes an edition over 90 s is cut FROM THAT EDITION at build time, lowest
 * salience first. The Tape is the only surface allowed to hold its length as jurisdiction falls;
 * everything else shrinks.
 */
internal fun fitCeremony(
    segments: List<TapeSegment>,
    cap: Int = TAPE_CEREMONY_SECONDS,
): List<TapeSegment> {
    var kept = segments
    while (kept.sumOf { it.seconds } > cap) {
        val victim = kept.filter { it.cuttable }.minByOrNull { it.salience } ?: break
        kept = kept.filter { it !== victim }
    }
    return kept
}

/** The sincere congratulation cannot be spent on a week where nothing was tested. */
internal fun TestWeekCard.gated(): TestWeekCard =
    if (freshThisWeek) this else copy(sincereCongratulation = null)

// ---------------------------------------------------------------------------
// The authored copy the composer owns
// ---------------------------------------------------------------------------

/**
 * The default cheat row, and the joke is that by month 3 the default IS the observation. He stopped
 * exploring. There is nothing to report and reporting it every week is the report.
 */
const val DEFAULT_CHEAT_ROW = "— nothing new."

/** One warm line. No count. No chart. Never a fourth state. */
internal fun withdrawalLine(day: String): String =
    "You told me about $day before I asked. That's not in the Ledger. That was never going in the Ledger."

/** Mandatory weekly, so the fallback ships. */
internal val FALLBACK_LEARNED = RipLine(
    Register.BIT,
    Target.the_situation,
    "I learned nothing. You were boringly consistent. Disgusting.",
)

/** The same beat with the mockery taken out. He is still the butt of it; that part never needed a gate. */
internal val FALLBACK_LEARNED_NEUTRAL = RipLine(
    Register.PITCHMAN,
    Target.himself,
    "I learned nothing this week. You were consistent. I have no notes, and I want you to sit for a " +
        "moment with what that does to a man in my line of work.",
)

internal fun fallbackLearned(gates: ClinicalGates): RipLine =
    if (gates.mockingAllowed) FALLBACK_LEARNED else FALLBACK_LEARNED_NEUTRAL

internal val COACH_DOWN_LINE = RipLine(
    Register.GHOST,
    Target.himself,
    "I did some thinking while you were out. Don't worry about it.",
)

internal val COACH_DOWN_SIGN_OFF = RipLine(
    Register.GHOST,
    Target.himself,
    "That's the whole tape. It's a short one. I'll be here.",
)

/**
 * THE BUTTON, rendered from jurisdiction and nothing else. Not the month, not the streak, not the
 * calendar. One integer, and he watches it fall.
 */
fun signOffFor(jurisdiction: Int): RipLine = when (jurisdiction.coerceIn(0, 4)) {
    4 -> RipLine(
        Register.PITCHMAN, Target.himself,
        "Same time next week. Four rows on the desk. FOUR. Business is BOOMING, brother.",
    )
    3 -> RipLine(
        Register.PITCHMAN, Target.himself,
        "Same time next week. I'm not going anywhere. Structurally.",
    )
    2 -> RipLine(
        Register.PITCHMAN, Target.himself,
        "Same time next week. Two rows. In the industry we call that a lean operation.",
    )
    1 -> RipLine(
        Register.GHOST, Target.himself,
        "Same time next week. One row. I'll make the tape anyway.",
    )
    else -> RipLine(
        Register.GHOST, Target.himself,
        "Nothing on the desk. I made you a tape anyway. That's not a job, that's a hobby. " +
            "…It's quiet in here.",
    )
}
