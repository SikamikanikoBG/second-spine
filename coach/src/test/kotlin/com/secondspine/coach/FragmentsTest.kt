package com.secondspine.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * THE BANK'S GUARDRAILS.
 *
 * SPEC §3.7 ships the voice bible as failing tests rather than as a style guide, for one reason:
 * a rule in a document is unenforceable against a contributor at 1am, and the rules in this file
 * are the ones whose violation cannot be taken back once it has been said to a user.
 *
 * These run in milliseconds. No Android, no clock, no I/O.
 */
class FragmentsTest {

    // -----------------------------------------------------------------------
    // Size and shape
    // -----------------------------------------------------------------------

    @Test
    fun `bank ships at least 220 fragments`() {
        assertTrue(
            BANK.size >= 220,
            "v1 ships 220 (SPEC §2.3). Bank is ${BANK.size}.",
        )
    }

    @Test
    fun `ids are unique`() {
        val dupes = BANK.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(dupes.isEmpty(), "duplicate fragment ids: $dupes")
    }

    @Test
    fun `no fragment is blank or carries stray whitespace`() {
        BANK.forEach {
            assertTrue(it.text.isNotBlank(), "${it.id} is blank")
            assertEquals(it.text.trim(), it.text, "${it.id} has leading/trailing whitespace")
        }
    }

    @Test
    fun `every slot role has fragments in the two default registers`() {
        SlotRole.entries.forEach { slot ->
            listOf(Register.PITCHMAN, Register.BIT).forEach { reg ->
                assertTrue(
                    fragments(slot, reg).isNotEmpty(),
                    "no $reg fragments for $slot - the assembler would throw at runtime",
                )
            }
        }
    }

    @Test
    fun `lookup by slot and register partitions the bank exactly`() {
        val viaLookup = SlotRole.entries.flatMap { s -> Register.entries.flatMap { r -> fragments(s, r) } }
        assertEquals(BANK.size, viaLookup.size)
        assertEquals(BANK.toSet(), viaLookup.toSet())
    }

    @Test
    fun `lookup by id resolves every fragment`() {
        BANK.forEach { assertEquals(it, fragment(it.id)) }
    }

    // -----------------------------------------------------------------------
    // Targets — the moral spine in the type system
    // -----------------------------------------------------------------------

    @Test
    fun `the target enum is frozen`() {
        // RESOLUTIONS §B adopts §3.7's enum verbatim. body/weight/appearance/worth are not values:
        // a flag you wrote you can unwrite at 1am; a value that does not exist you cannot conjure.
        assertEquals(
            setOf("the_habit", "the_excuse", "the_situation", "the_phone", "himself", "the_tape"),
            Target.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `every fragment aims at a legal target`() {
        BANK.forEach { assertTrue(it.target in Target.entries, "${it.id} has an illegal target") }
    }

    @Test
    fun `every register is represented and none has died`() {
        Register.entries.forEach { reg ->
            assertTrue(BANK.any { it.register == reg }, "$reg has no fragments")
        }
    }

    // -----------------------------------------------------------------------
    // "brother" — the tic with negative decay
    // -----------------------------------------------------------------------

    @Test
    fun `brother is unemittable in DISAPPOINTED and GHOST`() {
        // The single most valuable test in this file. Arsen learns the tell by month two without
        // being taught, and from then on its ABSENCE hits harder every time. One leaky line and the
        // best tell in the app is permanently dead. Note SPEC §2.6's GRACEFUL GOODBYE sample breaks
        // this rule in its own prose; the rule wins and SW_G_04 ships without it.
        val serious = setOf(Register.DISAPPOINTED, Register.GHOST)
        val leaks = BANK.filter { it.register in serious && it.text.contains("brother", ignoreCase = true) }
        assertTrue(leaks.isEmpty(), "brother leaked into a serious register: ${leaks.map { it.id }}")
    }

    @Test
    fun `brother fires at most once per fragment`() {
        // SPEC §2.4: max once per line. Two in one breath and it stops being a tic and starts being
        // a catchphrase, which is the same word with none of the value.
        val greedy = BANK.filter {
            Regex("""\bbrother\b""", RegexOption.IGNORE_CASE).findAll(it.text).count() > 1
        }
        assertTrue(greedy.isEmpty(), "brother twice in one fragment: ${greedy.map { it.id }}")
    }

    // -----------------------------------------------------------------------
    // DISAPPOINTED is an event, not a share
    // -----------------------------------------------------------------------

    @Test
    fun `DISAPPOINTED pins CAUGHT_FAKE and nothing else`() {
        // RESOLUTIONS §A2 / SPEC §3.7. If confession draws DISAPPOINTED, confession has a price
        // again in the only currency that matters - his tone - and the honesty gradient re-inverts.
        BANK.filter { it.register == Register.DISAPPOINTED }.forEach {
            assertEquals(Trigger.CAUGHT_FAKE, it.trigger, "${it.id} is DISAPPOINTED under the wrong trigger")
            assertEquals(FragmentSituation.CAUGHT, it.situation, "${it.id} is DISAPPOINTED off a non-caught situation")
        }
    }

    @Test
    fun `he is never disappointed in a miss`() {
        // He is blind. He cannot know WHY the gym was missed - flu, bereavement, a gate meeting that
        // ran long. Being disappointed in a miss is him inventing a reason to be cruel.
        val onMiss = BANK.filter { it.register == Register.DISAPPOINTED && it.trigger == Trigger.MISSED }
        assertTrue(onMiss.isEmpty(), "DISAPPOINTED aimed at a miss: ${onMiss.map { it.id }}")
    }

    @Test
    fun `the register mix is weighted like a character and not like a spreadsheet`() {
        fun share(r: Register) = BANK.count { it.register == r }.toDouble() / BANK.size
        val loudAndFunny = share(Register.PITCHMAN) + share(Register.BIT)
        assertTrue(loudAndFunny >= 0.60, "PITCHMAN+BIT is the body of the bank; got $loudAndFunny")
        assertTrue(share(Register.ARENA) <= 0.15, "ARENA is rare and explosive; got ${share(Register.ARENA)}")
        assertTrue(share(Register.GHOST) <= 0.20, "GHOST is the wound, not the weather; got ${share(Register.GHOST)}")
        assertTrue(
            share(Register.DISAPPOINTED) <= 0.05,
            "DISAPPOINTED fires 0-3 times in TEN MONTHS. It is not a share. Got ${share(Register.DISAPPOINTED)}",
        )
    }

    // -----------------------------------------------------------------------
    // The one sincere congratulation
    // -----------------------------------------------------------------------

    @Test
    fun `there is exactly one sincere congratulation and it burns`() {
        val sincere = BANK.filter { it.situation == FragmentSituation.SINCERE }
        assertEquals(1, sincere.size, "the product spends exactly one of these, ever: ${sincere.map { it.id }}")

        val one = assertNotNull(fragment(SINCERE_ONE_ID))
        assertEquals(Register.GHOST, one.register, "the sincere one is a GHOST scene (RESOLUTIONS §B)")
        assertTrue(one.once, "it must burn on first play")
        assertEquals(1, one.retireAt)
        assertTrue(one.text.contains("proud of you"))
        assertTrue(!one.text.contains("brother", ignoreCase = true))
    }

    @Test
    fun `uniques burn and everything else retires per slot`() {
        BANK.forEach {
            if (it.once) {
                assertEquals(1, it.retireAt, "${it.id} is a unique and must burn at 1")
            } else if (it.retireAtOverride == null) {
                assertEquals(it.slotRole.retireAt, it.retireAt, "${it.id} broke per-slot retirement")
            }
        }
    }

    // -----------------------------------------------------------------------
    // He is from 1994. The language never updated.
    // -----------------------------------------------------------------------

    @Test
    fun `banned lexicon appears nowhere in the bank`() {
        val hits = mutableListOf<String>()
        BANK.forEach { frag ->
            FRAGMENT_BANNED_LEXICON.forEach { banned ->
                val re = Regex("""(?<![\w-])${Regex.escape(banned)}(?![\w-])""", RegexOption.IGNORE_CASE)
                if (re.containsMatchIn(frag.text)) hits += "${frag.id}: '$banned'"
            }
        }
        assertTrue(hits.isEmpty(), "banned lexicon in the bank:\n${hits.joinToString("\n")}")
    }

    @Test
    fun `the you-are-a-trait construction cannot be built`() {
        // SPEC §2.4: any second-person `you are [trait]` construction is banned outright. The target
        // enum stops him aiming at a person; this stops him doing it by grammar.
        val hits = BANK.filter { FRAGMENT_BANNED_TRAIT_RE.containsMatchIn(it.text) }
        assertTrue(hits.isEmpty(), "second-person trait attack: ${hits.map { it.id }}")
    }

    @Test
    fun `no fragment mocks a cigarette`() {
        // SPEC §7.5: count, don't condemn. Zero penalties, forever. Every smoking fragment is GHOST,
        // curious, and carries no verdict - "what was in the room", never "why".
        val smoking = BANK.filter { it.situation == FragmentSituation.SMOKING }
        assertTrue(smoking.isNotEmpty(), "the relapse scene must exist - silence would read as judgement")
        smoking.forEach {
            assertEquals(Register.GHOST, it.register, "${it.id} does a bit about a cigarette")
            assertTrue(
                it.target == Target.the_situation || it.target == Target.himself,
                "${it.id} aims a smoking line at the wrong thing",
            )
        }
        val jokes = setOf("cigarette", "smoke", "smoking", "nicotine")
        BANK.filter { it.register == Register.ARENA || it.register == Register.BIT }.forEach { frag ->
            jokes.forEach { w ->
                assertTrue(
                    !Regex("""\b$w\b""", RegexOption.IGNORE_CASE).containsMatchIn(frag.text),
                    "${frag.id} brings a cigarette into a comedy register",
                )
            }
        }
    }

    @Test
    fun `no emoji`() {
        // He is forty megabytes of int8 weights from 1994. Nothing above U+2000 except the
        // punctuation a typesetter would have had.
        BANK.forEach { frag ->
            frag.text.forEach { ch ->
                if (ch.code > 0x2000) {
                    assertTrue(
                        ch in ALLOWED_HIGH_CODEPOINTS,
                        "${frag.id} contains U+${ch.code.toString(16).uppercase()} ('$ch')",
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // BREAK GLASS — the absence is the feature
    // -----------------------------------------------------------------------

    @Test
    fun `break glass has no line, in any register, including a warm one`() {
        // SPEC §2.6. The app says nothing, on the night and forever. A safety valve with a price is a
        // trap with a decorative handle - and a JOKE on the night someone pressed it is worse than a
        // price. There is deliberately no FragmentSituation for it, so no fragment can be filed under
        // one; this test also stops a future author writing the line and filing it under ANY.
        assertTrue(BREAK_GLASS_FRAGMENTS.isEmpty())
        assertTrue(FragmentSituation.entries.none { it.name.contains("BREAK") })
        val named = setOf("break glass", "break-glass", "break_glass", "panic button", "emergency stop")
        BANK.forEach { frag ->
            named.forEach { phrase ->
                assertTrue(
                    !frag.text.contains(phrase, ignoreCase = true),
                    "${frag.id} names the one night the app is silent about",
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // The data slots — line quality = f(specificity)
    // -----------------------------------------------------------------------

    @Test
    fun `every data slot is one the resolver can fill`() {
        BANK.forEach { frag ->
            frag.slots.forEach { slot ->
                assertTrue(
                    slot in FRAGMENT_DATA_SLOTS,
                    "${frag.id} demands {$slot}, which the SlotResolver cannot fill",
                )
            }
        }
    }

    @Test
    fun `no banned slot can be conjured`() {
        // SPEC §3.3: weight_delta, any body metric and any food field are deleted from the enum so
        // that no contributor can reach for them.
        val banned = setOf("weight", "weight_delta", "bmi", "body", "food", "calories", "macro")
        banned.forEach { assertTrue(it !in FRAGMENT_DATA_SLOTS, "'$it' is reachable as a data slot") }
    }

    @Test
    fun `the loud registers observe real data, not vibes`() {
        // SPEC §3.3: a callback to a real Tuesday is funnier than any static line, and his life
        // refills weekly, for free, forever. The exemptions are counted, not waved through: the
        // DISAPPOINTED crumb IS the data, and a handful of wizard-declared statics are allowed.
        val slotless = BANK.filter {
            it.slotRole == SlotRole.OBSERVATION &&
                it.register in setOf(Register.PITCHMAN, Register.BIT, Register.ARENA) &&
                it.slots.isEmpty()
        }
        assertTrue(slotless.size <= 2, "too many observations run on vibes: ${slotless.map { it.id }}")
    }

    @Test
    fun `numbers he quotes are slots, never literals he could get wrong`() {
        // A specific WRONG number is the lie that breaks a character built on being lovably vague.
        // The 94% tic, 1994/1997, eleven million units and the Rotterdam warehouse are his own
        // history and are allowed to be literal; anything about Arsen must come off the archive.
        val aboutArsen = BANK.filter { it.target == Target.the_habit && it.slotRole == SlotRole.OBSERVATION }
        assertTrue(aboutArsen.count { it.slots.isNotEmpty() } >= aboutArsen.size - 1)
    }

    // -----------------------------------------------------------------------
    // Situation coverage — the scenes that must exist
    // -----------------------------------------------------------------------

    @Test
    fun `every scene the product needs has lines`() {
        val required = listOf(
            FragmentSituation.WATER, FragmentSituation.EXERCISE, FragmentSituation.DONUT,
            FragmentSituation.CAUGHT, FragmentSituation.PRAISE, FragmentSituation.COMEBACK,
            FragmentSituation.DOOMSCROLL, FragmentSituation.GRADUATION, FragmentSituation.PHONE,
            FragmentSituation.TAPE, FragmentSituation.CONFESSION, FragmentSituation.PURGED,
            FragmentSituation.IDLE, FragmentSituation.BANK_HEALTH, FragmentSituation.SMOKING,
            FragmentSituation.AUDIT, FragmentSituation.LOCK, FragmentSituation.CLIMAX,
            FragmentSituation.SINCERE, FragmentSituation.GOODBYE,
        )
        required.forEach { s ->
            assertTrue(BANK.any { it.situation == s }, "$s has no lines")
        }
    }

    @Test
    fun `the climax and the goodbye fire once and are GHOST`() {
        listOf(FragmentSituation.CLIMAX, FragmentSituation.GOODBYE).forEach { s ->
            BANK.filter { it.situation == s }.forEach {
                assertEquals(Register.GHOST, it.register, "${it.id} performs a scene that must not be performed")
                assertTrue(it.once, "${it.id} must fire once, ever")
            }
        }
        // The biggest event in ten months: the 94% tic breaking.
        val climax = BANK.single { it.situation == FragmentSituation.CLIMAX }
        assertTrue(climax.text.startsWith("I don't know."))
    }

    @Test
    fun `confession is answered warm and never priced`() {
        // RESOLUTIONS §A1 / SPEC §3.7: confession routes to BIT, warm, every time. If a confession
        // can draw a cold register the honesty gradient re-inverts one layer below the pipeline fix.
        BANK.filter { it.trigger == Trigger.CONFESSED }.forEach {
            assertTrue(
                it.register == Register.BIT || it.register == Register.PITCHMAN,
                "${it.id} prices a confession in tone",
            )
        }
        assertTrue(BANK.any { it.situation == FragmentSituation.CONFESSION })
    }

    @Test
    fun `the vendetta is aimed at the machine, never at the man`() {
        // SPEC §2.5: contempt at the machine, curiosity at the man. This is what seats Arsen BESIDE
        // him against a common enemy instead of beneath him.
        BANK.filter { it.situation == FragmentSituation.PHONE }.forEach {
            assertEquals(Target.the_phone, it.target, "${it.id} points the vendetta at a person")
        }
    }

    @Test
    fun `the donut is never given a verdict`() {
        // RESOLVED: he has no arms. There is no food module, no schema, no verdict - the whole
        // mechanic is that he asks to be told about it, because it is the only way he gets to be there.
        val donut = BANK.filter { it.situation == FragmentSituation.DONUT }
        assertTrue(donut.isNotEmpty())
        donut.forEach {
            assertTrue(it.register == Register.BIT, "${it.id} takes a declared indulgence seriously")
            assertTrue(
                it.target == Target.himself,
                "${it.id} aims a donut line at something other than himself",
            )
        }
    }

    // -----------------------------------------------------------------------
    // The arithmetic (design/consistency.md: parameterised on the curve, never a literal)
    // -----------------------------------------------------------------------

    @Test
    fun `the bank covers the v1 demand with headroom on every slot`() {
        // SPEC §2.3: v1 ships 220 and covers months 1-3's ~360-line need outright. The bank grows
        // while the budget shrinks - at J=1 he only wants three lines a day.
        val lines = 360
        val fireRate = mapOf(
            SlotRole.OPENER to 1.0,
            SlotRole.OBSERVATION to 1.0,
            SlotRole.ESCALATION to 0.50,
            SlotRole.SWERVE to 0.35,
            SlotRole.BUTTON to 1.0,
        )
        SlotRole.entries.forEach { slot ->
            val capacity = BANK.filter { it.slotRole == slot && !it.once }.sumOf { it.retireAt }
            val demand = lines * fireRate.getValue(slot)
            assertTrue(
                capacity >= demand,
                "$slot exhausts inside v1: capacity $capacity vs demand $demand",
            )
        }
    }

    @Test
    fun `the swerve is the tightest slot, because its staleness is the only fatal one`() {
        fun headroom(slot: SlotRole, rate: Double): Double {
            val capacity = BANK.filter { it.slotRole == slot && !it.once }.sumOf { it.retireAt }
            return capacity / (360 * rate)
        }
        val swerve = headroom(SlotRole.SWERVE, 0.35)
        assertTrue(swerve >= 1.0, "the swerve bank is short: $swerve")
        assertTrue(swerve <= headroom(SlotRole.OPENER, 1.0), "the swerve should never be the roomiest slot")
    }
}
