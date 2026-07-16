package com.secondspine.coach

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE LEDGER FORGETS, and this file is the only thing making that true.
 *
 * "I'm going to remember this" must be FALSE. Not softened, not windowed in a view, not
 * soft-deleted — false. Everything below defends one of two properties:
 *
 *   1. The purge is UNCONDITIONAL at 28 days (RESOLUTIONS §B), so he structurally cannot hold a
 *      pattern against you.
 *   2. `break_glass` is not a value in this module, so no query can name it.
 */
class LedgerTest {

    private val now = 300L.dayMillis
    private fun daysAgo(d: Int) = now - d.days

    private fun entry(
        kind: LedgerKind = LedgerKind.EVASION,
        habitId: String? = "water",
        ageDays: Int = 0,
        note: String? = null,
    ) = LedgerEntry(kind = kind, habitId = habitId, at = daysAgo(ageDays), note = note)

    // -----------------------------------------------------------------------
    // The purge
    // -----------------------------------------------------------------------

    @Test
    fun `the purge is unconditional at 28 days`() {
        val entries = listOf(entry(ageDays = 27), entry(ageDays = 28), entry(ageDays = 29))
        val kept = purge(entries, now)
        assertEquals(1, kept.size, "27 days survives; 28 and 29 do not")
        assertEquals(daysAgo(27), kept.single().at)
    }

    @Test
    fun `a thrice-repeated failure is STILL deleted - there is no carve-out for repeat offences`() {
        // THE TEST THIS FILE EXISTS FOR. SPEC §3.4 wrote the purge as
        //   DELETE ... WHERE ts < now - 28d AND cluster_repeat_within_28d = 0
        // which keeps a row precisely when its kind+habit pattern RECURRED. That carve-out defeats
        // the graft it belongs to: it retains exactly the rows a person ruminates on and deletes the
        // harmless ones, i.e. it is a machine for building the case file the design says it deletes.
        // RESOLUTIONS §B: unconditional. This test is what makes the resolution real.
        val repeated = listOf(
            entry(kind = LedgerKind.EVASION, habitId = "training", ageDays = 40, note = "Thu 19:41"),
            entry(kind = LedgerKind.EVASION, habitId = "training", ageDays = 34, note = "Thu 19:52"),
            entry(kind = LedgerKind.EVASION, habitId = "training", ageDays = 29, note = "Thu 19:38"),
        )
        assertEquals(
            emptyList(), purge(repeated, now),
            "the same failure, at the same hour, three times, is deleted on exactly the same day as " +
                "a one-off. If this ever goes red, the app has become a case file.",
        )
    }

    @Test
    fun `a pattern that recurs INSIDE the window is untouched - the purge is about age, not about mercy`() {
        val recent = listOf(
            entry(kind = LedgerKind.EVASION, habitId = "training", ageDays = 20),
            entry(kind = LedgerKind.EVASION, habitId = "training", ageDays = 10),
            entry(kind = LedgerKind.EVASION, habitId = "training", ageDays = 1),
        )
        assertEquals(3, purge(recent, now).size)
    }

    @Test
    fun `the purge takes every kind - being caught faking is forgotten on the same schedule`() {
        val old = LedgerKind.entries.map { entry(kind = it, ageDays = 30) }
        assertEquals(
            emptyList(), purge(old, now),
            "no kind is exempt. Not CAUGHT_FAKE, not DEMOTION. His memory of Xiaomi is permanent " +
                "(canary_result is a different table); his memory of the man is 28 days.",
        )
    }

    @Test
    fun `purging is idempotent and never resurrects a row`() {
        val entries = (0..60).map { entry(ageDays = it) }
        val once = purge(entries, now)
        assertEquals(once, purge(once, now))
        assertEquals(28, once.size, "days 0..27 survive")
    }

    @Test
    fun `he cannot get it back - the purge returns a new table, not a filtered view`() {
        // The character says: "not hidden, *gone*, I can't get it back, I've *tried*." The only way
        // that line is honest is if the survivors are all a caller ever holds.
        val entries = listOf(entry(ageDays = 5), entry(ageDays = 100))
        val kept = purge(entries, now)
        assertTrue(kept.none { it.at == daysAgo(100) })
        assertTrue(purge(kept, now).none { it.at == daysAgo(100) })
    }

    // -----------------------------------------------------------------------
    // Break glass
    // -----------------------------------------------------------------------

    @Test
    fun `no LedgerKind mentions break glass`() {
        // Structural, like TransitionReason having no CONFESSED. RESOLUTIONS §B narrowed the
        // isolation invariant to "no query may name break_glass at all" — and the cheapest way to
        // guarantee that is to leave nothing to name.
        val forbidden = listOf("break", "glass", "breakglass", "break_glass", "emergency", "panic")
        for (kind in LedgerKind.entries) {
            for (word in forbidden) {
                assertFalse(
                    kind.name.contains(word, ignoreCase = true),
                    "LedgerKind.${kind.name} must not name break glass",
                )
                assertFalse(
                    kind.cardLabel.contains(word, ignoreCase = true),
                    "LedgerKind.${kind.name}'s card label must not name break glass",
                )
            }
        }
    }

    @Test
    fun `break glass is named nowhere in the Ledger or the Tape - not even in a comment or a filter`() {
        // A filter that excludes break_glass is still a query that names break_glass, and it is one
        // refactor away from being an include. Scoped to the two files this agent owns.
        val sources = listOf(
            File("src/main/kotlin/com/secondspine/coach/Ledger.kt"),
            File("src/main/kotlin/com/secondspine/coach/Tape.kt"),
        )
        for (f in sources) {
            if (!f.exists()) continue
            val text = f.readText()
            // The doc comments must be able to say WHY the value is absent, so allow the two prose
            // spellings and forbid every identifier-shaped one.
            val identifierShaped = Regex("""break_glass|breakGlass|BREAK_GLASS|BreakGlass""")
            assertNull(
                identifierShaped.find(text)?.value,
                "${f.name} names break glass as an identifier",
            )
        }
    }

    @Test
    fun `a confession is not expressible as a ledger kind`() {
        // FOR THE RECORD never reaches the Ledger. Not filtered out on the way in — unrepresentable.
        assertFalse(
            LedgerKind.entries.any {
                it.name.contains("CONFESS", ignoreCase = true) ||
                    it.name.contains("WITHDRAW", ignoreCase = true)
            },
        )
    }

    @Test
    fun `the body and the plate have no kinds and never will`() {
        val banned = listOf("weight", "food", "meal", "calorie", "body", "smok", "cigarette", "scale")
        for (kind in LedgerKind.entries) {
            for (word in banned) {
                assertFalse(
                    kind.name.contains(word, ignoreCase = true),
                    "LedgerKind.${kind.name}: a column that doesn't exist he cannot conjure",
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Lifetime counters — the only thing that survives
    // -----------------------------------------------------------------------

    @Test
    fun `dateless lifetime counters survive the purge`() {
        val entries = (0..60).map { entry(ageDays = it) }
        val counters = LifetimeCounters().accrue(entries)
        val survivors = purge(entries, now)

        assertEquals(61L, counters[LedgerKind.EVASION], "he may say 'sixty-one'")
        assertEquals(28, survivors.size, "he may not say 'that Tuesday in March'")
        // The purge is not allowed to walk the counters back.
        assertEquals(61L, counters.accrue(emptyList())[LedgerKind.EVASION])
    }

    @Test
    fun `LifetimeCounters holds no date - a count is not a memory`() {
        // Reflection, on purpose: this asserts the SHAPE, so it fails the day someone adds
        // `lastSeenAt` "just for debugging" and quietly reconstitutes the case file.
        val dateish = setOf("at", "ts", "time", "times", "date", "when", "day", "days", "stamp", "timestamp", "instant")
        val fields = LifetimeCounters::class.java.declaredFields.filterNot { it.isSynthetic }

        assertEquals(listOf("counts"), fields.map { it.name }, "one field: a tally, and nothing else")

        for (f in fields) {
            // Match on camelCase WORDS, not on substrings — "counts" ends in "ts" and is not a
            // timestamp, and a test that cannot tell the difference is a test nobody will keep.
            val words = f.name.split(Regex("(?<=[a-z0-9])(?=[A-Z])|_")).map { it.lowercase() }
            for (word in words) {
                assertFalse(
                    word in dateish,
                    "LifetimeCounters.${f.name} looks like a timestamp. He may say 'forty-one'. He " +
                        "may not say 'that Tuesday in March.'",
                )
            }
            // And the real invariant, which no naming convention can dodge: nothing in here is a
            // point in time. A Long field would be a date wearing a tally's name.
            assertFalse(
                f.type.name in setOf("long", "java.lang.Long", "java.util.Date", "java.time.Instant"),
                "LifetimeCounters.${f.name} is a ${f.type.name} — that is a date, whatever it is called",
            )
        }
    }

    @Test
    fun `the camelCase word check actually catches the field it exists to catch`() {
        // Guarding the guard: the first version of the test above used endsWith() and would have
        // passed `lastSeenAt` while failing on `counts`. Exactly backwards.
        fun words(name: String) = name.split(Regex("(?<=[a-z0-9])(?=[A-Z])|_")).map { it.lowercase() }
        assertEquals(listOf("counts"), words("counts"))
        assertEquals(listOf("last", "seen", "at"), words("lastSeenAt"))
        assertEquals(listOf("evasion", "count", "lifetime"), words("evasion_count_lifetime"))
        assertTrue("at" in words("lastSeenAt"))
        assertFalse("ts" in words("counts"))
    }

    @Test
    fun `counters accrue at write time, before the purge can ever see the rows`() {
        var counters = LifetimeCounters()
        var table = emptyList<LedgerEntry>()
        // 300 days of one evasion a day, purged nightly at 04:00.
        for (day in 0..299) {
            val e = LedgerEntry(LedgerKind.EVASION, "water", at = day.days)
            counters = counters.accrue(listOf(e))
            table = purge(table + e, now = day.days)
        }
        assertEquals(300L, counters[LedgerKind.EVASION])
        assertEquals(300L, counters.total)
        assertEquals(28, table.size, "his addressable memory is 28 rows, on day 300, forever")
    }

    // -----------------------------------------------------------------------
    // The card
    // -----------------------------------------------------------------------

    @Test
    fun `the docket always prints the canonical six, even at zero`() {
        val rows = ledgerRows(emptyList())
        assertEquals(LEDGER_CARD_ORDER, rows.map { it.kind })
        assertTrue(rows.all { it.count == 0 })
        // The zeroes are the point: a docket that only prints your failures is a grievance.
        assertEquals("CAUGHT FAKE        0", rows.first().render())
    }

    @Test
    fun `a kind outside the canonical six appears only when it has rows`() {
        val rows = ledgerRows(listOf(entry(kind = LedgerKind.NEAR_MISS, ageDays = 2)))
        assertTrue(LedgerKind.NEAR_MISS in rows.map { it.kind })
        assertFalse(LedgerKind.POWER_SAVE_MINUTES in rows.map { it.kind })
        assertEquals(LEDGER_CARD_ORDER, rows.take(LEDGER_CARD_ORDER.size).map { it.kind })
    }

    @Test
    fun `the clerk copies the note and never formats a date`() {
        // :coach is pure JVM: no timezone, no locale, no clock. `at` is purge arithmetic and nothing
        // else; the human detail arrives pre-formatted.
        val rows = ledgerRows(listOf(entry(kind = LedgerKind.EVASION, ageDays = 2, note = "Thu 19:41  home ×4")))
        val evasion = rows.single { it.kind == LedgerKind.EVASION }
        assertEquals("EVASION            1   Thu 19:41  home ×4", evasion.render())
    }
}
