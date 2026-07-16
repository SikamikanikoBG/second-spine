package com.secondspine.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE TAPE.
 *
 * THE MEDIOCRE WEEK IS WRITTEN FIRST, ON PURPOSE, AND IT IS THE FIXTURE EVERY OTHER TEST DERIVES
 * FROM. A great week writes itself. Weeks 20-30 are *structurally* mediocre — no new cheats, no new
 * correlations, no unseen lines, nothing graduates, nothing collapses — and three flat Tapes in a
 * row is where this entire architecture dies, because the payoff stops arriving. If the boring week
 * isn't worth opening, nothing else in the spec matters, and the open-rate is a kill criterion.
 *
 * So: week 23. Jurisdiction 3 (training ENFORCED; water AUDITED; coffee cutoff AUDITED; reading
 * TRUSTED). Nothing moved. Nobody cheated. He was fine.
 */
class TapeTest {

    private val now = 161L.dayMillis

    // -----------------------------------------------------------------------
    // THE MEDIOCRE WEEK — the worked example
    // -----------------------------------------------------------------------

    private val mediocre = WeekData(
        weekId = 23,
        testWeek = TestWeekCard(
            metric = "DEAD HANG",
            value = "44 s",
            previousPb = "41 s",
            pbWeek = 15,
            freshThisWeek = false,
        ),
        coldOpen = ColdOpen(
            value = "ELEVEN",
            caption = "That's how many times you opened the camera when I didn't ask you to.",
        ),
        photos = 29,
        ledgerEntries = listOf(
            LedgerEntry(LedgerKind.EVASION, "training", at = now - 3.days, note = "Thu 19:41  home ×4"),
            LedgerEntry(LedgerKind.OEM_KILL, null, at = now - 1.days, note = "Sat 09:12–15:04  power save, 352 min"),
        ),
        ripPoints = 9,
        arsenPoints = 12,
        cheatRow = "— nothing new. Same glass. Still 3/10.",
        verified = 24,
        unverified = 5,
        contradicted = 0,
        withdrawnDay = "Tuesday",
        roastCandidates = listOf(
            RoastLine(
                RipLine(
                    Register.PITCHMAN, Target.the_habit,
                    "You have opened the Arsenal fourteen times and trained six.",
                ),
                chart = "bar chart, opens vs sessions, 12 weeks",
            ),
            RoastLine(
                RipLine(
                    Register.BIT, Target.the_habit,
                    "Nine coffees after 16:40. NINE. Brother, you are training for the WORLD " +
                        "CHAMPIONSHIP OF NOT SLEEPING, and I want you to know — I've seen the field. " +
                        "You're winning.",
                ),
                chart = "cutoff-violation histogram by hour",
            ),
            // SPEC §9.8 labels this one "[DISAPPOINTED-adjacent, flat, no caps]" — and adjacent is
            // the whole point. DISAPPOINTED is unreachable this week (no CAUGHT_FAKE), so the flat
            // register the line actually wants is GHOST: the wound, aimed at the phone, no caps, no
            // "brother", and the joke is that he is the patient one because he is dead.
            RoastLine(
                RipLine(
                    Register.GHOST, Target.the_phone,
                    "Thursday you pressed home four times in eleven seconds. I'm still here. I'm " +
                        "forty megabytes. I don't get tired. I get patient.",
                ),
                chart = "evasion timeline",
            ),
        ),
        consistencyPct = 79,
        restingHr = 58,
        learned = RipLine(
            Register.BIT, Target.the_situation,
            "Nothing. Fourteen of the last sixteen weeks I've had something. This week: nothing. " +
                "You were boringly consistent. Disgusting.",
        ),
        deskRows = listOf(
            DeskRow("training", Stage.ENFORCED),
            DeskRow("water", Stage.AUDITED, daysToGraduation = 12),
            DeskRow("coffee", Stage.AUDITED),
        ),
        deskLine = RipLine(
            Register.PITCHMAN, Target.himself,
            "Three rows. Same three as last Sunday. Water's twelve days from AUDITED going TRUSTED " +
                "and then it's two, and I want to be very clear that I am rooting against you, " +
                "professionally, with my whole chest.",
        ),
        oneThing = RipLine(
            Register.PITCHMAN, Target.the_habit,
            "The guitar. Four times. You didn't tell me, you didn't want credit, you just did it and " +
                "went to bed. It's the only clean thing on the tape. Don't let me catch you being " +
                "proud of it.",
        ),
        offerLine = RipLine(
            Register.PITCHMAN, Target.himself,
            "Next week. Fourteen glasses, two sessions. And if you act now — and you *have* to act " +
                "now, because I'm a recording — I'll throw in, absolutely free: water graduates two " +
                "weeks early. I lose it. Forever. Off my desk, out of my hands, gone. (beat) I've " +
                "never wanted you to fail so much in my LIFE.",
        ),
        lastWeekRung = 1,
        weightEwma = true,
    )

    private fun tape(
        week: WeekData = mediocre,
        jurisdiction: Int = 3,
        gates: ClinicalGates = ClinicalGates(),
    ) = composeTape(week, jurisdiction, gates)

    @Test
    fun `the mediocre week composes, segment by segment, and is worth opening`() {
        val t = tape()

        // COLD OPEN — one number, no context.
        assertEquals("ELEVEN", t.coldOpen?.value)
        assertEquals(
            "That's how many times you opened the camera when I didn't ask you to.",
            t.coldOpen?.caption,
        )

        // THE MONTAGE — 29 photographs of his actual life. Unnarrated. This is the segment that does
        // not need a good week, and it is why the flat Tape still opens.
        assertEquals(29, t.montage?.photos)
        assertEquals(false, t.montage?.narrated)
        assertEquals(false, t.montage?.cuttable)

        // THE LEDGER — cold, flat, court-clerk. The 0s are a scoreboard he won.
        val ledger = assertNotNull(t.ledger)
        assertEquals("ROLLING 28 DAYS", ledger.window)
        assertEquals("RIP 9 : ARSEN 12", ledger.score)
        assertEquals(
            """
            CAUGHT FAKE        0
            EVASION            1   Thu 19:41  home ×4
            FORCE STOP         0
            OEM KILL           1   Sat 09:12–15:04  power save, 352 min
            CLOCK JUMP         0
            DEMOTION           0
            CHEAT              — nothing new. Same glass. Still 3/10.
            """.trimIndent(),
            ledger.render(),
        )

        // VERIFIED vs CLAIMED — and the withdrawal, which is warm, uncounted and uncharted.
        val vvc = assertNotNull(t.verifiedVsClaimed)
        assertEquals("VERIFIED 24 · UNVERIFIED 5 · CONTRADICTED 0", vvc.line)
        assertEquals(
            "You told me about Tuesday before I asked. That's not in the Ledger. That was never " +
                "going in the Ledger.",
            vvc.withdrawal,
        )

        // THE ROAST — three lines, each one tappable into the chart behind it.
        val roast = assertNotNull(t.roast)
        assertEquals(3, roast.lines.size)
        assertTrue(roast.lines.all { it.chart.isNotBlank() }, "a roast line with no chart is just a joke")
        assertEquals(
            "You have opened the Arsenal fourteen times and trained six.",
            roast.lines[0].line.text,
        )
        assertEquals("evasion timeline", roast.lines[2].chart)

        // TRENDS — zero character, and the one compounding number.
        assertEquals(79, t.trends.consistencyPct)
        assertEquals(58, t.trends.restingHr)
        assertEquals("44 s", t.compoundingNumber.value)
        assertEquals("41 s", t.compoundingNumber.previousPb)

        // WHAT I LEARNED — mandatory weekly, and the honest answer this week is nothing.
        assertTrue(t.whatILearned!!.line.text.endsWith("Disgusting."))

        // RIP'S DESK — three rows, and one of them is twelve days from firing him.
        assertEquals(listOf("training", "water", "coffee"), t.ripsDesk?.rows?.map { it.habitId })
        assertEquals(12, t.ripsDesk?.rows?.get(1)?.daysToGraduation)

        // THE ONE THING — grudging, behaviour-attributed, never a trait, never a body.
        assertTrue(t.theOneThing!!.line.text.startsWith("The guitar."))
        assertEquals(Target.the_habit, t.theOneThing!!.line.target)

        // THE OFFER — he stakes jurisdiction. It is the only thing he has to lose.
        assertEquals(false, t.offer?.reckoning)
        assertTrue(t.offer!!.line.text.contains("water graduates two weeks early"))

        // THE BUTTON — rendered from jurisdiction, not from the calendar.
        assertEquals("Same time next week. I'm not going anywhere. Structurally.", t.signOff.line.text)

        // And it fits inside its own ceremony allowance without the composer having to cut anything.
        assertEquals(84, t.seconds)
        assertTrue(t.seconds <= TAPE_CEREMONY_SECONDS)
        assertEquals(11, t.segments.size)
    }

    @Test
    fun `the mediocre week's segments are in the spec's order`() {
        assertEquals(
            listOf(
                "ColdOpen", "Montage", "LedgerCard", "VerifiedVsClaimed", "Roast", "Trends",
                "WhatILearned", "RipsDesk", "TheOneThing", "TheOffer", "SignOff",
            ),
            tape().segments.map { it::class.simpleName },
        )
    }

    @Test
    fun `the cheat leaderboard is ONE ROW and it is a receipt proving he won`() {
        // Not a segment. Cheat variety is an exploration phase and exploration ends: by month 3 he
        // has one cheap cheat that works and never varies it. A whole segment built to remove his
        // win condition would arrive every Sunday as evidence that it didn't.
        val ledger = assertNotNull(tape().ledger)
        assertEquals("— nothing new. Same glass. Still 3/10.", ledger.cheatRow)
        assertEquals(1, ledger.render().lines().count { it.startsWith("CHEAT") })
        assertFalse(
            tape().segments.any { it::class.simpleName?.contains("Cheat") == true },
            "the cheat leaderboard must never be promoted back to a segment",
        )
    }

    // -----------------------------------------------------------------------
    // The one genuinely compounding number
    // -----------------------------------------------------------------------

    @Test
    fun `the compounding number is present on every edition, including the ones he'd rather not see`() {
        val editions = mapOf(
            "mediocre" to tape(),
            "great" to tape(mediocre.copy(graduation = "water", ledgerEntries = emptyList())),
            "collapse" to tape(mediocre.copy(habitsMissedFiveDays = 3)),
            "scoff" to tape(gates = ClinicalGates(scoffPositive = true)),
            "cut to the bone" to tape(mediocre.copy(photos = 60)),
        )
        for ((name, t) in editions) {
            assertEquals("DEAD HANG", t.compoundingNumber.metric, "$name lost Test Week")
            assertEquals("44 s", t.compoundingNumber.value, "$name lost Test Week")
            assertFalse(t.trends.cuttable, "$name: TRENDS holds the outcome variable and cannot be cut")
        }
    }

    @Test
    fun `the one sincere congratulation cannot be spent on a week where nothing was tested`() {
        val sincere = "That's a pull-up. A real one. I don't have anything for this. Well done."
        val notATestWeek = mediocre.copy(
            testWeek = mediocre.testWeek.copy(freshThisWeek = false, sincereCongratulation = sincere),
        )
        assertNull(
            tape(notATestWeek).compoundingNumber.sincereCongratulation,
            "there is EXACTLY ONE sincere congratulation in the whole product and it is not being " +
                "spent on a Sunday where nobody hung from anything",
        )

        val testWeek = mediocre.copy(
            testWeek = mediocre.testWeek.copy(freshThisWeek = true, sincereCongratulation = sincere),
        )
        assertEquals(sincere, tape(testWeek).compoundingNumber.sincereCongratulation)
    }

    // -----------------------------------------------------------------------
    // The language ladder
    // -----------------------------------------------------------------------

    @Test
    fun `the ladder rung is 4 minus jurisdiction`() {
        assertEquals(0, ladderRung(4, lastWeekRung = null))
        assertEquals(1, ladderRung(3, lastWeekRung = null))
        assertEquals(2, ladderRung(2, lastWeekRung = null))
        assertEquals(3, ladderRung(1, lastWeekRung = null))
        assertEquals(4, ladderRung(0, lastWeekRung = null))
    }

    @Test
    fun `the ladder never jumps more than one rung per week`() {
        // Two habits can graduate on the same Sunday. A character who is suddenly two registers
        // quieter reads as a bug, not as loss. Grief is gradual or it isn't grief.
        assertEquals(1, ladderRung(jurisdiction = 0, lastWeekRung = 0), "4 -> 0 in one week is still one rung")
        assertEquals(3, ladderRung(jurisdiction = 4, lastWeekRung = 4), "and it cannot snap back either")
    }

    @Test
    fun `the ladder never jumps more than one rung across a whole ten-month collapse of jurisdiction`() {
        // The plausible-success trajectory, then a cliff, then a relapse. Nothing may jump.
        val trajectory = listOf(4, 4, 3, 3, 2, 2, 2, 1, 1, 1, 0, 0, 4, 4, 0, 2)
        var last: Int? = null
        val rungs = trajectory.map { j ->
            val r = composeTape(mediocre.copy(lastWeekRung = last), j, ClinicalGates()).rung
            if (last != null) {
                assertTrue(
                    kotlin.math.abs(r - last!!) <= 1,
                    "jurisdiction $j moved the language ladder from $last to $r",
                )
            }
            assertTrue(r in 0..4, "rung $r out of range")
            last = r
            r
        }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 3, 2, 3, 2), rungs)
    }

    @Test
    fun `a collapse week never escalates the ladder - the week he fell apart is not the week the volume goes up`() {
        // assertLadderNeverEscalatesOn(multiHabitCollapse()). Without the guard, jurisdiction 3 with
        // last week at rung 3 would pull him back to rung 2 — louder — on the worst week of the year.
        val quiet = mediocre.copy(lastWeekRung = 3)
        assertEquals(2, tape(quiet).rung, "on a normal week he does climb back toward 4 - jurisdiction")
        assertEquals(3, tape(quiet.copy(habitsMissedFiveDays = 3)).rung, "on a collapse week he does not")
    }

    @Test
    fun `the dominant register falls with the odometer and lands on GHOST at zero`() {
        assertEquals(Register.PITCHMAN, dominantRegister(0, mockingAllowed = true))
        assertEquals(Register.BIT, dominantRegister(2, mockingAllowed = true))
        assertEquals(Register.GHOST, dominantRegister(4, mockingAllowed = true))
        assertEquals(Register.PITCHMAN, dominantRegister(2, mockingAllowed = false))
    }

    // -----------------------------------------------------------------------
    // The collapse week
    // -----------------------------------------------------------------------

    @Test
    fun `a collapse week never renders a shame spiral`() {
        val t = tape(mediocre.copy(habitsMissedFiveDays = 3))

        assertTrue(t.coachDown)
        assertNull(t.roast, "assertNoRoastOn(coachDown())")
        assertNull(t.ledger, "no Ledger. No queued grievances.")
        assertNull(t.coldOpen, "no cold-open number")
        assertNull(t.offer, "no offer, no bet, no catch-up debt")
        assertNull(t.theOneThing)
        assertNull(t.whatILearned)
        assertNull(t.ripsDesk)

        // What ships: the Montage (whatever exists), TRENDS, and one card.
        assertEquals(
            listOf("Montage", "Trends", "CoachDownCard", "SignOff"),
            t.segments.map { it::class.simpleName },
        )
        assertEquals(
            "I did some thinking while you were out. Don't worry about it.",
            t.coachDownCard?.line?.text,
        )
        assertEquals(Register.GHOST, t.coachDownCard?.line?.register)
        assertEquals(Target.himself, t.coachDownCard?.line?.target)

        // Nothing he says on this Tape is aimed at the man or his week.
        assertTrue(
            t.spoken.all { it.target == Target.himself },
            "on the worst week of the year the only thing he is allowed to be about is himself",
        )
        assertTrue(t.spoken.none { it.register in MOCKING_REGISTERS })
    }

    @Test
    fun `one photo is fine`() {
        val t = tape(mediocre.copy(habitsMissedFiveDays = 4, photos = 1))
        assertEquals(1, t.montage?.photos)
        assertEquals(4, t.segments.size)
    }

    @Test
    fun `a depressive collapse signature does not notify - the Tape waits to be opened`() {
        assertTrue(tape(mediocre.copy(habitsMissedFiveDays = 3)).notifies)
        assertFalse(tape(mediocre.copy(habitsMissedFiveDays = 3, depressiveSignature = true)).notifies)
        assertTrue(tape().notifies)
    }

    @Test
    fun `two habits down is not a collapse - the threshold is three`() {
        assertFalse(tape(mediocre.copy(habitsMissedFiveDays = 2)).coachDown)
        assertTrue(tape(mediocre.copy(habitsMissedFiveDays = COACH_DOWN_HABITS)).coachDown)
    }

    // -----------------------------------------------------------------------
    // SCOFF / MDDI
    // -----------------------------------------------------------------------

    @Test
    fun `SCOFF-positive removes the mocking segments`() {
        val t = tape(gates = ClinicalGates(scoffPositive = true))

        assertNull(t.roast, "the roast is the mocking segment and it does not render")
        assertNull(t.ledger?.cheatRow, "the cheat row is a taunt; the docket is not")
        assertFalse(t.trends.weightEwma, "all body metrics permanently unavailable — not hidden, absent")
        assertTrue(
            t.spoken.none { it.register in MOCKING_REGISTERS },
            "not warned about. Unavailable. No in-app override.",
        )

        // But the app does not become a stub: the dashboard, the archive and the clerk all survive.
        assertNotNull(t.montage)
        assertNotNull(t.ledger)
        assertNotNull(t.verifiedVsClaimed)
        assertNotNull(t.whatILearned)
        assertEquals("44 s", t.compoundingNumber.value)
        assertNotNull(t.signOff)
    }

    @Test
    fun `SCOFF-positive still gets an answer from WHAT I LEARNED, and he is still the butt of it`() {
        val t = tape(gates = ClinicalGates(scoffPositive = true))
        assertEquals(FALLBACK_LEARNED_NEUTRAL, t.whatILearned?.line)
        assertEquals(Target.himself, t.whatILearned?.line?.target)
        assertFalse(t.whatILearned!!.line.text.contains("Disgusting"))
    }

    @Test
    fun `SCOFF-positive cannot be overridden by a week that hands the composer a mocking line`() {
        // The gate outranks the input (RESOLUTIONS §B: "the gate outranks the assertion"). A future
        // caller that forgets to filter must not be able to put an ARENA line in front of this user.
        val loud = mediocre.copy(
            graduation = "water",
            oneThing = RipLine(Register.ARENA, Target.the_habit, "THE GUITAR! FOUR TIMES!"),
            deskLine = RipLine(Register.BIT, Target.himself, "Three rows and I hate all of them."),
        )
        val t = composeTape(loud, 3, ClinicalGates(scoffPositive = true))
        assertNull(t.theOneThing)
        assertNull(t.ripsDesk)
        assertTrue(t.spoken.none { it.register in MOCKING_REGISTERS })
    }

    @Test
    fun `the weight EWMA never renders under SCOFF, even when the week supplies it`() {
        assertTrue(tape().trends.weightEwma)
        assertFalse(tape(gates = ClinicalGates(scoffPositive = true)).trends.weightEwma)
    }

    // -----------------------------------------------------------------------
    // Grammar
    // -----------------------------------------------------------------------

    @Test
    fun `DISAPPOINTED is unreachable on a week nobody was caught faking`() {
        // RESOLUTIONS §A2: DISAPPOINTED has no scheduled share. Its only trigger is CAUGHT_FAKE. It
        // fires 0-3 times in ten months and that is correct — it is devastating because it is rare,
        // and the way to keep it rare is to make it unreachable, not to budget it.
        val week = mediocre.copy(
            oneThing = RipLine(Register.DISAPPOINTED, Target.the_habit, "I thought we were past this."),
        )
        assertNull(tape(week).theOneThing)
        assertTrue(tape(week).spoken.none { it.register == Register.DISAPPOINTED })

        val caught = week.copy(
            ledgerEntries = week.ledgerEntries +
                LedgerEntry(LedgerKind.CAUGHT_FAKE, "training", at = now - 2.days),
        )
        assertEquals(Register.DISAPPOINTED, tape(caught).theOneThing?.line?.register)
    }

    @Test
    fun `ARENA cannot fire on an ordinary Sunday`() {
        // 3/week max, never after 20:00 — and the Tape fires AT 20:00. The only edition it is
        // sanctioned on is the one where he loses a habit.
        val arena = RipLine(Register.ARENA, Target.the_habit, "FOURTEEN GLASSES! FOURTEEN!")
        assertNull(tape(mediocre.copy(offerLine = arena)).offer)
        assertNotNull(tape(mediocre.copy(offerLine = arena, graduation = "water")).offer)
    }

    @Test
    fun `brother is unemittable in DISAPPOINTED and GHOST`() {
        val week = mediocre.copy(
            oneThing = RipLine(Register.GHOST, Target.the_habit, "The guitar, brother. Four times."),
        )
        assertNull(tape(week).theOneThing)
        assertTrue(
            tape().spoken.none {
                (it.register == Register.GHOST || it.register == Register.DISAPPOINTED) &&
                    it.text.contains("brother", ignoreCase = true)
            },
        )
        // And the sign-off he actually earns at jurisdiction 4 is PITCHMAN, so it may say it.
        assertTrue(signOffFor(4).text.contains("brother"))
        assertFalse(signOffFor(0).text.contains("brother", ignoreCase = true))
    }

    @Test
    fun `nothing on the Tape is ever aimed at the man, because there is nothing to aim at`() {
        // RESOLUTIONS §B adopts §3.7's enum verbatim, and it is CI-frozen. Note `himself` is IN the
        // set and must stay: Rip turning on Rip is the character, and it is the release valve that
        // lets every other line stay off the man. The banned four are aimed at the USER's worth,
        // and they are not absent flags — they are absent values.
        assertEquals(
            listOf(
                Target.the_habit, Target.the_excuse, Target.the_situation,
                Target.the_phone, Target.himself, Target.the_tape,
            ),
            Target.entries,
            "the Target enum is frozen",
        )
        for (word in listOf("body", "weight", "appearance", "worth")) {
            assertFalse(
                Target.entries.any { it.name.contains(word, ignoreCase = true) },
                "Target must not be able to express '$word': a flag he wrote he can unwrite at 1am; " +
                    "a value that does not exist he cannot conjure",
            )
        }

        // And the mediocre week's actual aim: the habit, the phone, the situation, himself.
        assertEquals(
            setOf(Target.the_habit, Target.the_phone, Target.the_situation, Target.himself),
            tape().spoken.map { it.target }.toSet(),
        )
    }

    @Test
    fun `the roast is capped at three lines forever`() {
        val many = mediocre.copy(
            roastCandidates = mediocre.roastCandidates +
                mediocre.roastCandidates + mediocre.roastCandidates,
        )
        assertEquals(MAX_ROAST_LINES, tape(many).roast?.lines?.size)
    }

    // -----------------------------------------------------------------------
    // The great week
    // -----------------------------------------------------------------------

    @Test
    fun `a graduation promotes the desk above the montage, because the amputation is the event`() {
        val great = mediocre.copy(graduation = "water", ledgerEntries = emptyList())
        val t = tape(great)
        assertEquals("ColdOpen", t.segments[0]::class.simpleName)
        assertEquals("RipsDesk", t.segments[1]::class.simpleName)
        assertEquals("Montage", t.segments[2]::class.simpleName)
        assertTrue(t.offer!!.reckoning, "THE OFFER becomes THE RECKONING: he counts the rows out loud")
        assertEquals(3, t.offer!!.remaining.size)
        assertTrue(t.seconds <= TAPE_CEREMONY_SECONDS)
    }

    @Test
    fun `an empty Ledger on a great week gets four seconds of nothing on screen`() {
        val great = mediocre.copy(graduation = "water", ledgerEntries = emptyList())
        val ledger = assertNotNull(tape(great).ledger)
        assertEquals(4, ledger.silentBeatSeconds)
        assertEquals(14, ledger.seconds)
        assertTrue(ledger.rows.all { it.count == 0 })

        // A great week with an evasion still on the docket gets no such beat. The beat is earned.
        assertEquals(0, tape(mediocre.copy(graduation = "water")).ledger?.silentBeatSeconds)
    }

    // -----------------------------------------------------------------------
    // Ceremony
    // -----------------------------------------------------------------------

    @Test
    fun `an over-long edition is cut at build time, lowest salience first`() {
        val fat = mediocre.copy(photos = 60)   // montage hits its 20 s clamp: 92 s total
        val t = tape(fat)
        assertEquals(20, t.montage?.seconds)
        assertTrue(t.seconds <= TAPE_CEREMONY_SECONDS, "the composer must cut, not overrun")
        assertNull(t.whatILearned, "WHAT I LEARNED is the lowest-salience segment and goes first")
        assertNotNull(t.montage)
        assertNotNull(t.roast)
        assertEquals(86, t.seconds)
    }

    @Test
    fun `the four things that make it worth opening are never cut`() {
        // The Montage (his own life, never depletes), TRENDS (the compounding number), and the
        // button. If the cap could reach these, the cap would be deleting the product.
        val t = fitCeremony(tape(mediocre.copy(photos = 60)).segments, cap = 1)
        assertEquals(listOf("Montage", "Trends", "SignOff"), t.map { it::class.simpleName })
    }

    @Test
    fun `the montage is his life and the clock scales to it`() {
        assertEquals(0, montageSeconds(0))
        assertEquals(3, montageSeconds(1))
        assertEquals(12, montageSeconds(29))
        assertEquals(20, montageSeconds(200))
    }

    // -----------------------------------------------------------------------
    // The button
    // -----------------------------------------------------------------------

    @Test
    fun `the sign-off is rendered from jurisdiction, not from the calendar`() {
        val texts = (0..4).map { signOffFor(it).text }
        assertEquals(5, texts.toSet().size, "one integer, and he watches it fall")
        assertEquals(Register.GHOST, signOffFor(0).register)
        assertEquals(Register.GHOST, signOffFor(1).register)
        assertEquals(Register.PITCHMAN, signOffFor(4).register)
        assertTrue(signOffFor(0).text.contains("It's quiet in here"))
        // Same week, same calendar, different desk, different button.
        assertFalse(tape(jurisdiction = 1).signOff.line.text == tape(jurisdiction = 3).signOff.line.text)
    }

    @Test
    fun `he never congratulates cleanly - THE ONE THING is grudging`() {
        assertTrue(tape().theOneThing!!.line.text.contains("Don't let me catch you being proud of it"))
    }

    @Test
    fun `jurisdiction outside the odometer's range is a bug, not a Tape`() {
        // RESOLUTIONS §D: without MAX_AUDITED the declared range 0..4 was fiction and the invariant
        // threw at the moment the user was doing best. It is real now, so it may be enforced.
        assertEquals(4, MAX_ENFORCED + MAX_AUDITED)
        val e = kotlin.runCatching { tape(jurisdiction = 5) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }
}
