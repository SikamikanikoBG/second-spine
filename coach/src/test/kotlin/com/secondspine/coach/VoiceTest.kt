package com.secondspine.coach

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE GRAMMAR INVARIANTS, as failing tests rather than a style guide.
 *
 * SPEC §3.7's thesis: you cannot lint a value that cannot exist, and you cannot trust a rule that
 * only lives in prose. Every guardrail in the character bible that actually matters is in here as an
 * assertion, because the failure mode of a voice engine is not a crash — it is being subtly cruel to
 * one user in month 8, once, in a way nobody ever sees in review.
 *
 * These run against the real [BANK], the real mix and a seeded 300-day soak, in milliseconds, with no
 * Android SDK. `assertNever` is spelled out rather than imported, because a helper that hides the
 * quantifier is how you end up asserting something weaker than you think.
 */
class VoiceTest {

    private val on = ClinicalGates()
    private val scoff = ClinicalGates(scoffPositive = true)
    private val allJurisdictions = 0..4
    private val notCaught = Trigger.entries.filter { it != Trigger.CAUGHT_FAKE }

    private fun at(day: Long, hour: Int = 9): Long = day * 86_400_000L + hour * 3_600_000L

    /** Every slot resolves. The archive is full and the lights are on. */
    private val fullArchive = SlotResolver { slot ->
        when (slot) {
            "days" -> "41"
            "count" -> "12"
            "habit" -> "water"
            "time" -> "9:14"
            "weeks" -> "6"
            "object" -> "a glass"
            "manufacturer" -> "Xiaomi"
            "ms" -> "812"
            else -> null
        }
    }

    /** The 28-day purge already took the row he was reaching for. */
    private val purgedArchive = SlotResolver { null }

    // -----------------------------------------------------------------------
    // The budgets and the split — RESOLUTIONS §B's single sources of truth
    // -----------------------------------------------------------------------

    @Test
    fun `speechBudget is exactly the curve RESOLUTIONS settled - and zero means zero`() {
        assertEquals(6, speechBudget(4))
        assertEquals(5, speechBudget(3))
        assertEquals(4, speechBudget(2))
        assertEquals(2, speechBudget(1))
        assertEquals(
            0, speechBudget(0),
            "at jurisdiction 0 he has nothing left to have an opinion about. The Tape only. " +
                "A budget of 1 here would be the character refusing to notice he has been fired, " +
                "which is a different scene and it is not this one.",
        )
    }

    @Test
    fun `the budget is monotone - it only ever falls as the user gets better`() {
        // The arc is not a mood. Every step of the odometer must cost him volume, and he can never
        // win ground back: `jurisdiction` is a pure function of the pipeline and Rip has no vote.
        for (j in 1..4) {
            assertTrue(
                speechBudget(j) > speechBudget(j - 1),
                "speechBudget($j) must exceed speechBudget(${j - 1})",
            )
        }
    }

    @Test
    fun `jurisdictionShare is 15 percent plus 11 percent per unit of jurisdiction`() {
        for (j in allJurisdictions) {
            assertEquals(0.15 + 0.11 * j, jurisdictionShare(j), 1e-9)
        }
        // He never fully leaves the screen — he just stops being the point.
        assertTrue(jurisdictionShare(0) > 0.0)
        assertTrue(jurisdictionShare(4) < 1.0)
    }

    // -----------------------------------------------------------------------
    // The mix
    // -----------------------------------------------------------------------

    @Test
    fun `register mix sums to one at every jurisdiction, gated or not`() {
        for (j in allJurisdictions) for (g in listOf(on, scoff)) {
            assertEquals(1.0, registerMix(j, g).values.sum(), 1e-9, "mix must sum to 1.0 at j=$j gates=$g")
        }
    }

    @Test
    fun `ARENA share is the ceiling 0_10 x j and GHOST is 0_10 x (4 - j)`() {
        for (j in allJurisdictions) {
            val mix = registerMix(j, on)
            assertEquals(0.10 * j, mix[Register.ARENA] ?: 0.0, 1e-9, "ARENA ceiling at j=$j")
            assertEquals(0.10 * (4 - j), mix[Register.GHOST] ?: 0.0, 1e-9, "GHOST ceiling at j=$j")
        }
    }

    @Test
    fun `DISAPPOINTED has no scheduled share - it is not a row anyone can tune upward`() {
        // RESOLUTIONS §A2: budgeted at 40% of month-8 speech it was structurally dead anyway, because
        // BYTE_REPLAY is near-unreachable on a camera-only path. It is a rare event, not a share.
        for (j in allJurisdictions) for (g in listOf(on, scoff)) {
            assertTrue(
                Register.DISAPPOINTED !in registerMix(j, g),
                "DISAPPOINTED must not appear in the mix at j=$j — 0.0 is still a dial; absence is not",
            )
        }
    }

    @Test
    fun `the loud voice inverts into the quiet one as the user takes his jurisdiction away`() {
        // The whole re-pricing thesis, in one assertion: same assets, inverted meaning, earned by the
        // user's own success rather than granted by a calendar.
        assertTrue(
            registerMix(4, on).getValue(Register.ARENA) > registerMix(1, on).getValue(Register.ARENA),
            "ARENA must decay with jurisdiction",
        )
        assertTrue(
            registerMix(1, on).getValue(Register.GHOST) > (registerMix(4, on)[Register.GHOST] ?: 0.0),
            "GHOST must rise as jurisdiction drains",
        )
    }

    // -----------------------------------------------------------------------
    // THE CLINICAL GATE — it outranks everything else in this file
    // -----------------------------------------------------------------------

    @Test
    fun `assertNever - a mocking register for a SCOFF-positive user, anywhere, ever`() {
        // RESOLUTIONS §B: "the gate outranks the assertion". No in-app override, no roll of the dice,
        // no trigger, no hour. For this user the mocking registers do not exist.
        for (j in allJurisdictions) for (r in MOCKING_REGISTERS) {
            assertTrue(r !in registerMix(j, scoff), "$r must be absent from the SCOFF mix at j=$j")
        }
        val rng = Random(7)
        for (j in allJurisdictions) for (trigger in Trigger.entries) for (hour in 0..23) {
            repeat(20) {
                val r = chooseRegister(trigger, j, scoff, hour, SpeechHistory(), at(1, hour), rng.nextDouble())
                assertTrue(
                    r !in MOCKING_REGISTERS,
                    "chooseRegister returned $r for a SCOFF-positive user (j=$j trigger=$trigger hour=$hour)",
                )
            }
        }
    }

    @Test
    fun `a SCOFF-positive user still gets a whole coach - the gate removes mockery, not warmth`() {
        // The failure mode worth guarding against: silencing him for the user the gate exists to
        // protect. That is the shape of an accessibility feature that reads as a punishment.
        for (j in allJurisdictions) {
            val mix = registerMix(j, scoff)
            assertEquals(1.0, mix.values.sum(), 1e-9)
            assertTrue((mix[Register.PITCHMAN] ?: 0.0) > 0.0, "PITCHMAN must survive the gate at j=$j")
        }
    }

    // -----------------------------------------------------------------------
    // DISAPPOINTED — the rare event
    // -----------------------------------------------------------------------

    @Test
    fun `assertNever - register is DISAPPOINTED and trigger is not CAUGHT_FAKE`() {
        // The most in-character rule in the document. He is BLIND: he cannot know why the gym did not
        // happen — flu, bereavement, a gate meeting that ran long. Being disappointed in a miss is him
        // inventing a reason to be cruel. Faking is a choice made in front of him; he earned that one.
        val rng = Random(1994)
        for (trigger in notCaught) for (j in allJurisdictions) for (g in listOf(on, scoff)) for (hour in 0..23) {
            repeat(10) {
                val r = chooseRegister(trigger, j, g, hour, SpeechHistory(), at(5, hour), rng.nextDouble())
                assertTrue(
                    r != Register.DISAPPOINTED,
                    "DISAPPOINTED fired on trigger=$trigger (j=$j hour=$hour). Its trigger enum is " +
                        "{CAUGHT_FAKE} and that is the entire enum.",
                )
            }
        }
    }

    @Test
    fun `the bank pins DISAPPOINTED to CAUGHT_FAKE too, so the rule holds even if the mix is wrong`() {
        // Belt and braces on the only line in the app that accuses the user of something.
        for (f in BANK.filter { it.register == Register.DISAPPOINTED }) {
            assertEquals(Trigger.CAUGHT_FAKE, f.trigger, "${f.id} is DISAPPOINTED but does not pin CAUGHT_FAKE")
        }
        for (trigger in notCaught) for (slot in SlotRole.entries) {
            assertEquals(
                emptyList(),
                eligible(slot, Register.DISAPPOINTED, trigger, FragmentSituation.CAUGHT, PlayLedger()),
                "a DISAPPOINTED fragment was reachable under $trigger",
            )
        }
    }

    @Test
    fun `CAUGHT_FAKE always draws DISAPPOINTED - no cap, no share, no roll`() {
        for (j in allJurisdictions) for (g in listOf(on, scoff)) for (hour in 0..23) {
            for (roll in listOf(0.0, 0.25, 0.5, 0.75, 0.999)) {
                assertEquals(
                    Register.DISAPPOINTED,
                    chooseRegister(Trigger.CAUGHT_FAKE, j, g, hour, SpeechHistory(), at(5, hour), roll),
                )
            }
        }
    }

    @Test
    fun `confession is answered warm, every time - it is never priced in his tone`() {
        // RESOLUTIONS §A1. If confession can draw DISAPPOINTED, confession has a price again in the
        // only currency that matters, and the incentive gradient re-inverts. The product IS this.
        for (j in allJurisdictions) for (roll in listOf(0.0, 0.5, 0.999)) {
            assertEquals(
                Register.BIT,
                chooseRegister(Trigger.CONFESSED, j, on, 9, SpeechHistory(), at(5), roll),
            )
            assertEquals(
                Register.PITCHMAN,
                chooseRegister(Trigger.CONFESSED, j, scoff, 9, SpeechHistory(), at(5), roll),
                "the gated user still gets warmth, not silence and not the rare event",
            )
        }
    }

    // -----------------------------------------------------------------------
    // ARENA — the absolute cap always binds first
    // -----------------------------------------------------------------------

    @Test
    fun `assertNever - register is ARENA and jurisdiction is 0`() {
        val rng = Random(97)
        for (trigger in Trigger.entries) for (hour in 0..23) {
            repeat(50) {
                val r = chooseRegister(trigger, 0, on, hour, SpeechHistory(), at(3, hour), rng.nextDouble())
                assertTrue(r != Register.ARENA, "ARENA at jurisdiction 0 (trigger=$trigger hour=$hour)")
            }
        }
        assertTrue(Register.ARENA !in registerMix(0, on), "the ceiling at j=0 is 0.0, so the key is gone")
        assertTrue(!arenaAllowed(0, on, 9, SpeechHistory(), at(3)))
    }

    @Test
    fun `ARENA never fires after 20-00 and never more than 3 times a week or twice a day`() {
        // RESOLUTIONS §B: 3/week, never twice a day, never after 20:00 — and "the absolute cap ALWAYS
        // binds first". Simulated over 30 days of a maximally loud user hammering it every hour.
        val rng = Random(1)
        var history = SpeechHistory()
        val fired = mutableListOf<Long>()

        for (day in 0L until 30L) for (hour in 0..23) {
            val now = at(day, hour)
            repeat(4) {
                val r = chooseRegister(Trigger.SCHEDULED, 4, on, hour, history, now, rng.nextDouble())
                if (r == Register.ARENA) {
                    fired += now
                    history = history.record(Utterance(r, Trigger.SCHEDULED, now))
                }
            }
        }

        assertTrue(fired.isNotEmpty(), "the sim must actually reach ARENA or it proves nothing")

        for (t in fired) {
            val hour = ((t % 86_400_000L) / 3_600_000L).toInt()
            assertTrue(hour < ARENA_CURFEW_HOUR, "ARENA fired at $hour:00 — the curfew is 20:00")
        }
        for ((day, times) in fired.groupBy { it / 86_400_000L }) {
            assertEquals(1, times.size, "ARENA fired ${times.size} times on day $day — never twice a day")
        }
        for (t in fired) {
            val inWindow = fired.count { it > t - 7 * 86_400_000L && it <= t }
            assertTrue(inWindow <= ARENA_MAX_PER_WEEK, "$inWindow ARENA lines in the week ending at $t")
        }
    }

    @Test
    fun `the absolute cap outranks the ceiling - a 40 percent share still yields 3 a week`() {
        // The precedence RESOLUTIONS had to state out loud. At j=4 the mix nominates ARENA 40% of the
        // time; the wall does not care what the mix wants.
        assertEquals(0.40, registerMix(4, on).getValue(Register.ARENA), 1e-9)
        var history = SpeechHistory()
        repeat(3) { i ->
            history = history.record(Utterance(Register.ARENA, Trigger.SCHEDULED, at(i.toLong(), 9)))
        }
        assertTrue(
            !arenaAllowed(4, on, 9, history, at(3, 9)),
            "three in the trailing week must close the door regardless of the ceiling",
        )
        // ...and the door reopens once the week rolls past them. The cap is a window, not a quota.
        assertTrue(arenaAllowed(4, on, 9, history, at(10, 9)))
    }

    @Test
    fun `he does not do the arena voice at a man who is down`() {
        val rng = Random(11)
        for (trigger in ARENA_FORBIDDEN_TRIGGERS) {
            repeat(200) {
                val r = chooseRegister(trigger, 4, on, 9, SpeechHistory(), at(1), rng.nextDouble())
                assertTrue(r != Register.ARENA, "ARENA on $trigger — that is a victory lap over a corpse")
            }
        }
    }

    // -----------------------------------------------------------------------
    // THE FROZEN ENUM — the moral spine in the type system
    // -----------------------------------------------------------------------

    @Test
    fun `the Target enum is frozen - body, weight, appearance and worth are not enumerable`() {
        // SPEC §3.7.1 / RESOLUTIONS §B adopts §3.7's list verbatim. Stronger than linting emissions:
        // a flag you wrote you can unwrite at 1am; a value that does not exist you cannot conjure.
        // Adding `the_body` fails the build here.
        assertEquals(
            listOf("the_habit", "the_excuse", "the_situation", "the_phone", "himself", "the_tape"),
            Target.entries.map { it.name },
        )
        for (banned in listOf("body", "weight", "appearance", "worth", "food", "calorie")) {
            assertTrue(
                Target.entries.none { it.name.contains(banned, ignoreCase = true) },
                "Target must never enumerate '$banned'",
            )
        }
    }

    @Test
    fun `the resolver key space is frozen too - there is no body, weight or food slot to fill`() {
        // The engine's half of SPEC §3.3's "banned slots, deleted from the enum". A SlotResolver
        // physically cannot be asked for a body metric, because no key exists to ask with.
        for (banned in listOf("weight", "body", "food", "calorie", "bmi", "waist", "fat")) {
            assertTrue(
                FRAGMENT_DATA_SLOTS.none { it.contains(banned, ignoreCase = true) },
                "FRAGMENT_DATA_SLOTS must never contain '$banned'",
            )
        }
        // And every slot the bank actually reaches for is inside that frozen set, so the resolver
        // contract is total: no fragment can smuggle in a key the resolver was never asked to police.
        for (f in BANK) for (slot in f.slots) {
            assertTrue(slot in FRAGMENT_DATA_SLOTS, "${f.id} references unknown data slot '{$slot}'")
        }
    }

    // -----------------------------------------------------------------------
    // "brother" — the best tell in the app, and it only has to leak once
    // -----------------------------------------------------------------------

    @Test
    fun `assertNever - a line says brother while the register is DISAPPOINTED or GHOST`() {
        // A tic with negative decay: by month two its ABSENCE is the tell, and one leaky template
        // kills it permanently. The bank and the assembler both have to hold, so both are asserted.
        val brother = Regex("""\bbrother\b""", RegexOption.IGNORE_CASE)
        val serious = setOf(Register.DISAPPOINTED, Register.GHOST)

        for (f in BANK.filter { it.register in serious }) {
            assertTrue(!brother.containsMatchIn(f.text), "fragment ${f.id} (${f.register}) says 'brother'")
        }

        val rng = Random(4)
        var produced = 0
        for ((register, situation, trigger) in listOf(
            Triple(Register.DISAPPOINTED, FragmentSituation.CAUGHT, Trigger.CAUGHT_FAKE),
            Triple(Register.GHOST, FragmentSituation.ANY, Trigger.SCHEDULED),
        )) {
            var ledger = PlayLedger()
            repeat(80) { i ->
                val line = assemble(
                    register, trigger, ledger, fullArchive, at(i.toLong()), rng, situation,
                ) ?: return@repeat
                produced++
                assertTrue(!brother.containsMatchIn(line.text), "$register said: ${line.text}")
                ledger = ledger.record(line, at(i.toLong()))
            }
        }
        assertTrue(produced > 0, "the sim must actually render serious lines or it proves nothing")
    }

    @Test
    fun `brother appears at most once in any rendered line`() {
        // A perfectly clean bank composed carelessly still says it twice in one breath.
        val brother = Regex("""\bbrother\b""", RegexOption.IGNORE_CASE)
        val rng = Random(5)
        var ledger = PlayLedger()
        var checked = 0
        repeat(300) { i ->
            val now = at(i.toLong())
            val line = assemble(Register.PITCHMAN, Trigger.SCHEDULED, ledger, fullArchive, now, rng)
                ?: return@repeat
            checked++
            val n = brother.findAll(line.text).count()
            assertTrue(n <= 1, "\"brother\" x$n in one line: ${line.text}")
            ledger = ledger.record(line, now)
        }
        assertTrue(checked > 50, "only checked $checked lines")
    }

    // -----------------------------------------------------------------------
    // PER-SLOT RETIREMENT — RESOLUTIONS §B
    // -----------------------------------------------------------------------

    @Test
    fun `the retirement table is exactly what RESOLUTIONS settled, per slot and not globally`() {
        // "Nobody notices the fourth 'Okay.'; everybody notices the second swerve."
        assertEquals(20, SlotRole.OPENER.retireAt)
        assertEquals(15, SlotRole.OBSERVATION.retireAt)
        assertEquals(8, SlotRole.ESCALATION.retireAt)
        assertEquals(3, SlotRole.SWERVE.retireAt)
        assertEquals(12, SlotRole.BUTTON.retireAt)
    }

    @Test
    fun `assertNoFragmentExceeds retireAt of its slot role`() {
        // Driven to exhaustion on purpose: 2,000 attempts against a bank that cannot serve them.
        val rng = Random(300)
        var ledger = PlayLedger()
        repeat(2_000) { i ->
            val now = at(i.toLong())
            val line = assemble(Register.BIT, Trigger.SCHEDULED, ledger, fullArchive, now, rng)
                ?: return@repeat
            ledger = ledger.record(line, now)
        }
        assertTrue(ledger.fragmentPlays.isNotEmpty())
        for (f in BANK) {
            assertTrue(
                ledger.playCount(f.id) <= f.retireAt,
                "${f.id} (${f.slotRole}) played ${ledger.playCount(f.id)}x, retireAt=${f.retireAt}",
            )
        }
        // And the swerve must starve first: it is the only slot whose staleness is fatal, so it is
        // priced at 3 and it is SUPPOSED to run out before the openers do.
        val swerveExhausted = BANK.filter { it.slotRole == SlotRole.SWERVE && it.register == Register.BIT }
            .any { ledger.playCount(it.id) == it.retireAt }
        assertTrue(swerveExhausted, "the retire-at-3 swerve should be the first slot to hit its ceiling")
    }

    @Test
    fun `a fragment that prices itself is honoured - the uniques burn at one play`() {
        // SPEC §2.6: the sincere congratulation, the climax and the goodbye fire ONCE, ever.
        val once = BANK.filter { it.once }
        assertTrue(once.isNotEmpty(), "the uniques must exist or the best scenes in the app do not")
        for (f in once) {
            assertEquals(1, f.retireAt, "${f.id} is a unique and must burn at 1 play")
            val spent = PlayLedger(fragmentPlays = listOf(PlayRecord(f.id, at(1))))
            assertTrue(spent.isRetired(f), "${f.id} must be gone forever after one play")
        }
    }

    @Test
    fun `running dry returns null - it is content, not a crash`() {
        val thin = BANK.filter { it.slotRole == SlotRole.OPENER }
        assertNull(
            assemble(
                Register.PITCHMAN, Trigger.SCHEDULED, PlayLedger(), fullArchive, at(1), Random(0),
                bank = thin,
            ),
            "a bank with no OBSERVATION cannot make a line, and must say so quietly",
        )
    }

    // -----------------------------------------------------------------------
    // THE LINE-REPEAT LEDGER — 120 days, hashed
    // -----------------------------------------------------------------------

    @Test
    fun `assertNoLineRepeatsWithin 120 days`() {
        val rng = Random(120)
        var ledger = PlayLedger()
        val seen = mutableListOf<Pair<Long, Long>>()   // hash to when

        repeat(400) { i ->
            val now = at(i.toLong())
            val line = assemble(Register.PITCHMAN, Trigger.SCHEDULED, ledger, fullArchive, now, rng)
                ?: return@repeat
            assertNull(
                seen.firstOrNull { (h, t) -> h == line.hash && now - t < LINE_REPEAT_WINDOW_DAYS.days },
                "line repeated inside 120 days: ${line.text}",
            )
            seen += line.hash to now
            ledger = ledger.record(line, now)
        }
        assertTrue(seen.size > 20, "the sim must render real volume or it proves nothing: ${seen.size}")
    }

    @Test
    fun `the ledger is hashed, so the table structurally cannot become a transcript`() {
        // SPEC §3.6. An unhashed table of every line Rip ever said about your failures IS the
        // rumination infrastructure the 28-day purge exists to delete. The dedupe is enforced; the
        // record is not retrievable. He cannot read you your file because he cannot read it either.
        val line = assertNotNull(
            assemble(Register.PITCHMAN, Trigger.SCHEDULED, PlayLedger(), fullArchive, at(1), Random(2)),
        )
        val ledger = PlayLedger().record(line, at(1))
        assertEquals(lineHash(line.text), ledger.linePlays.single().hash)
        assertTrue(
            LinePlay::class.java.declaredFields.none { it.type == String::class.java },
            "LinePlay must hold no text field — a hash is unreadable and that is the entire point",
        )
    }

    @Test
    fun `line hashing normalises punctuation and case but distinguishes different lines`() {
        // Case and punctuation are noise: the same sentence re-punctuated is the same joke, and the
        // user notices the joke, not the comma.
        assertEquals(lineHash("Okay. The whole ask."), lineHash("okay   the WHOLE ask"))
        assertEquals(lineHash("It is dark in here..."), lineHash("it is DARK in here"))

        // Punctuation normalises to a SPACE, not to nothing — so two words never fuse into a third
        // and collide with a line he never said. (An apostrophe therefore splits: "it's" -> "it s".
        // That is the safe direction — it can only ever make two lines look MORE different, and the
        // cost of a false collision is a line he is silently barred from saying for 120 days.)
        assertTrue(lineHash("the ask.Look at that") != lineHash("the askLook at that"))

        // But the words themselves are load-bearing.
        assertTrue(lineHash("Okay.") != lineHash("Right."))
    }

    @Test
    fun `the hash purge drops rows past the window, so the window is enforced and not hoarded`() {
        val old = PlayLedger(linePlays = listOf(LinePlay(42L, at(0))))
        assertTrue(old.lineSeenWithin(42L, at(119)))
        assertTrue(!old.lineSeenWithin(42L, at(121)), "past 120 days he is allowed to reuse the line")
        assertEquals(0, old.purgeLineHashes(at(200)).linePlays.size)
        assertEquals(1, old.purgeLineHashes(at(100)).linePlays.size)
    }

    // -----------------------------------------------------------------------
    // THE DATA SLOT — the anti-decay thesis
    // -----------------------------------------------------------------------

    @Test
    fun `every rendered line at rung 1+ calls back to real history`() {
        // "Line quality = f(specificity)." A callback to a real Tuesday is funnier than any static
        // line, and his life refills weekly, for free, forever. This is the whole anti-decay bet.
        val rng = Random(33)
        var checked = 0
        for (register in listOf(Register.PITCHMAN, Register.BIT, Register.GHOST, Register.ARENA)) {
            var ledger = PlayLedger()
            repeat(120) { i ->
                val now = at(i.toLong())
                val line = assemble(register, Trigger.SCHEDULED, ledger, fullArchive, now, rng)
                    ?: return@repeat
                checked++
                val fragments = line.fragmentIds.mapNotNull { fragment(it) }
                assertTrue(
                    line.purged || fragments.any { it.slots.isNotEmpty() },
                    "no callback in ($register): ${line.text}",
                )
                ledger = ledger.record(line, now)
            }
        }
        assertTrue(checked > 100, "only checked $checked lines")
    }

    @Test
    fun `the data slot interpolates the real value - a specific wrong number would break him`() {
        // "Numbers Rip quotes must be TRUE. A specific wrong number is the lie that breaks a character
        // built on being lovably vague." So: no placeholder may ever survive into a rendered line.
        val rng = Random(44)
        var ledger = PlayLedger()
        var checked = 0
        repeat(200) { i ->
            val now = at(i.toLong())
            val line = assemble(Register.BIT, Trigger.SCHEDULED, ledger, fullArchive, now, rng)
                ?: return@repeat
            checked++
            assertTrue(!line.text.contains("{"), "an unresolved placeholder leaked: ${line.text}")
            ledger = ledger.record(line, now)
        }
        assertTrue(checked > 50)
    }

    @Test
    fun `contempt at the machine - the OEM vendetta renders a true measured number`() {
        val phone = BANK.filter { it.id in setOf("OP_B_14", "OB_B_20", "BT_B_14", "BT_B_16") }
        val line = assertNotNull(
            assemble(
                Register.BIT, Trigger.OEM_KILL, PlayLedger(), fullArchive, at(1), Random(0),
                situation = FragmentSituation.PHONE, bank = phone,
            ),
        )
        assertTrue(line.text.contains("Xiaomi"), line.text)
        assertTrue(line.text.contains("812"), line.text)
        assertEquals(Target.the_phone, line.target, "contempt at the machine, curiosity at the man")
    }

    @Test
    fun `when the source row is purged he reaches for the bit and it is not there`() {
        // SPEC §3.4: the purge writes its own material. Zero authored content, renewable forever, and
        // it is the mechanical seed for the "I don't know" scene. "I'm going to remember this" is now
        // false, and the app knows it's false.
        val line = assertNotNull(
            assemble(Register.BIT, Trigger.SCHEDULED, PlayLedger(), purgedArchive, at(1), Random(0)),
        )
        assertTrue(line.purged, "an unresolvable callback must take the PURGED branch, not throw")
        assertEquals(Target.the_tape, line.target)
        assertEquals(1, line.fragmentIds.size)
        assertTrue(fragment(line.fragmentIds.single())!!.situation == FragmentSituation.PURGED)
    }

    @Test
    fun `the purged line needs no archive to render - it is a joke about amnesia, not a callback`() {
        for (f in BANK.filter { it.situation == FragmentSituation.PURGED }) {
            // If the [PURGED] branch itself needed a memory, it could not fire on the day the memory
            // went missing, which is the only day it exists for.
            if (f.slots.isNotEmpty()) {
                assertTrue(
                    BANK.any { it.situation == FragmentSituation.PURGED && it.slots.isEmpty() },
                    "at least one PURGED fragment must render without the archive",
                )
            }
        }
        assertTrue(BANK.any { it.situation == FragmentSituation.PURGED && it.slots.isEmpty() })
    }

    // -----------------------------------------------------------------------
    // The grammar, assembled
    // -----------------------------------------------------------------------

    @Test
    fun `the five-slot grammar assembles in order and the optional slots are optional`() {
        val rng = Random(8)
        var sawEscalation = false
        var sawSwerve = false
        var sawNeither = false
        var ledger = PlayLedger()
        repeat(200) { i ->
            val now = at(i.toLong())
            val line = assemble(Register.BIT, Trigger.SCHEDULED, ledger, fullArchive, now, rng)
                ?: return@repeat
            if (line.purged) return@repeat
            val slots = line.fragmentIds.mapNotNull { fragment(it)?.slotRole }
            assertEquals(slots.sortedBy { it.ordinal }, slots, "slots must render in grammar order: $slots")
            assertTrue(SlotRole.OPENER in slots && SlotRole.OBSERVATION in slots && SlotRole.BUTTON in slots)
            if (SlotRole.ESCALATION in slots) sawEscalation = true
            if (SlotRole.SWERVE in slots) sawSwerve = true
            if (SlotRole.ESCALATION !in slots && SlotRole.SWERVE !in slots) sawNeither = true
            ledger = ledger.record(line, now)
        }
        assertTrue(sawEscalation, "ESCALATION (~50%) must fire")
        assertTrue(sawSwerve, "SWERVE (~35%) must fire")
        assertTrue(sawNeither, "and both must be genuinely optional")
    }

    @Test
    fun `DISAPPOINTED collapses to four words - the rhythm breaks and the silence does the work`() {
        // "No caps. No 'brother.' Four words. Funnier than loud." His best mode is quiet.
        val line = assertNotNull(
            assemble(
                Register.DISAPPOINTED, Trigger.CAUGHT_FAKE, PlayLedger(), fullArchive, at(1), Random(0),
                situation = FragmentSituation.CAUGHT,
            ),
        )
        assertTrue(!line.text.contains("brother", ignoreCase = true))
        assertEquals(Register.DISAPPOINTED, line.register)
        for (id in line.fragmentIds) {
            assertEquals(Trigger.CAUGHT_FAKE, fragment(id)!!.trigger, "$id is not pinned to CAUGHT_FAKE")
        }
    }

    // -----------------------------------------------------------------------
    // The engine end to end
    // -----------------------------------------------------------------------

    @Test
    fun `the volunteered budget is spent per day and enforcement speech does not touch it`() {
        var history = SpeechHistory()
        val now = at(10)
        repeat(6) { history = history.record(Utterance(Register.PITCHMAN, Trigger.SCHEDULED, now)) }
        assertEquals(0, history.budgetRemaining(4, now))

        assertNull(
            speak(Trigger.SCHEDULED, 4, on, 9, history, PlayLedger(), fullArchive, now, Random(0)),
            "the seventh volunteered conversation must not happen",
        )
        // But a rung firing is not a conversation he chose to start. RESOLUTIONS §B keeps the two
        // budgets split: enforcement is gated by the ladder, and a man who misses has earned the noise.
        assertNotNull(
            speak(
                Trigger.MISSED, 4, on, 9, history, PlayLedger(), fullArchive, now, Random(0),
                volunteered = false,
            ),
            "enforcement speech is a separate, ungoverned budget",
        )
        // And tomorrow is a new day.
        assertEquals(6, history.budgetRemaining(4, at(11)))
    }

    @Test
    fun `at jurisdiction 0 he volunteers nothing - the Tape only, forever`() {
        assertNull(
            speak(Trigger.SCHEDULED, 0, on, 9, SpeechHistory(), PlayLedger(), fullArchive, at(1), Random(0)),
        )
        assertNotNull(
            speak(
                Trigger.TAPE, 0, on, 9, SpeechHistory(), PlayLedger(), fullArchive, at(1), Random(0),
                volunteered = false,
            ),
            "the Tape is the one thing left, and its open rate is a kill-criterion metric",
        )
    }

    @Test
    fun `speak threads both ledgers through - it is pure, history in, choice out`() {
        val d = assertNotNull(
            speak(Trigger.SCHEDULED, 4, on, 9, SpeechHistory(), PlayLedger(), fullArchive, at(1), Random(3)),
        )
        assertEquals(1, d.history.utterances.size)
        assertEquals(1, d.ledger.linePlays.size)
        assertEquals(d.line.fragmentIds.size, d.ledger.fragmentPlays.size)
        assertEquals(d.line.hash, d.ledger.linePlays.single().hash)
        assertEquals(d.line.register, d.history.utterances.single().register)
    }

    // -----------------------------------------------------------------------
    // THE SOAK — 300 days, in milliseconds, because :coach is pure JVM
    // -----------------------------------------------------------------------

    @Test
    fun `300-day soak - every invariant holds on the plausible-success trajectory`() {
        val rng = Random(1997)
        var history = SpeechHistory()
        var ledger = PlayLedger()
        val spoken = mutableListOf<Line>()

        // The odometer drains as the user takes his jurisdiction away: 4 -> 1 over ten months.
        fun jurisdictionOn(day: Long) = when {
            day < 60 -> 4
            day < 120 -> 3
            day < 210 -> 2
            else -> 1
        }

        for (day in 0L until 300L) {
            val j = jurisdictionOn(day)
            for (hour in listOf(7, 12, 17, 21)) {
                repeat(3) {
                    val now = at(day, hour)
                    val d = speak(
                        notCaught.random(rng), j, on, hour, history, ledger, fullArchive, now, rng,
                    ) ?: return@repeat
                    history = d.history
                    ledger = d.ledger
                    spoken += d.line
                }
            }
            ledger = ledger.purgeLineHashes(at(day))   // the weekly purge, run daily. Same invariant.
        }

        assertTrue(spoken.size > 100, "the soak must actually produce speech: got ${spoken.size}")

        // 1. He never says more than the odometer allows, on any day of the ten months.
        for ((day, said) in history.utterances.filter { it.volunteered }.groupBy { it.at / 86_400_000L }) {
            val budget = speechBudget(jurisdictionOn(day))
            assertTrue(said.size <= budget, "day $day: ${said.size} volunteered, budget $budget")
        }

        // 2. DISAPPOINTED never appeared, because CAUGHT_FAKE never fired. Ten months, zero cruelty.
        assertTrue(
            history.utterances.none { it.register == Register.DISAPPOINTED },
            "DISAPPOINTED fired in a 300-day run with no BYTE_REPLAY. It is a rare event, not a share.",
        )

        // 3. The ARENA wall held for ten months.
        val arena = history.utterances.filter { it.register == Register.ARENA }
        for (u in arena) {
            val hour = ((u.at % 86_400_000L) / 3_600_000L).toInt()
            assertTrue(hour < ARENA_CURFEW_HOUR, "ARENA at $hour:00")
            assertTrue(
                arena.count { it.at > u.at - 7 * 86_400_000L && it.at <= u.at } <= ARENA_MAX_PER_WEEK,
                "more than $ARENA_MAX_PER_WEEK ARENA lines in the week ending at ${u.at}",
            )
        }

        // 4. He got quieter. This is the arc, and it is falsifiable HERE rather than at month 8.
        val early = history.utterances.count { it.at < at(60) }
        val late = history.utterances.count { it.at >= at(240) }
        assertTrue(late < early, "he must decay: $early utterances in M1-2 vs $late in M9-10")

        // 5. No fragment outlived its slot's retirement, across 300 days of real demand.
        for (f in BANK) {
            assertTrue(ledger.playCount(f.id) <= f.retireAt, "${f.id} exceeded ${f.retireAt}")
        }

        // 6. Never "brother" while serious, and never a placeholder, over the whole ten months.
        for (line in spoken) {
            if (line.register == Register.DISAPPOINTED || line.register == Register.GHOST) {
                assertTrue(!line.text.contains("brother", ignoreCase = true), line.text)
            }
            assertTrue(!line.text.contains("{"), "unresolved placeholder: ${line.text}")
        }
    }
}
