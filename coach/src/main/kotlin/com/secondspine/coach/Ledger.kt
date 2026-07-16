package com.secondspine.coach

/**
 * THE LEDGER — the court clerk's docket, and the one place the product is kinder than it pretends.
 *
 * Read flat. No jokes. Monospace. This tonal drop is what gives the rest of the app its teeth, and
 * cold data has no decay curve because it was never trying to entertain.
 *
 * Two structural facts live in this file, and both are load-bearing:
 *
 *  1. THE LEDGER FORGETS. RESOLUTIONS §B: an UNCONDITIONAL hard purge at 28 days. SPEC §3.4 wrote
 *     `... AND cluster_repeat_within_28d = 0`, i.e. a carve-out that keeps repeat offences forever.
 *     That carve-out defeats the graft it belongs to, so it is deleted here. The whole point is that
 *     he STRUCTURALLY CANNOT hold a pattern against you: a permanent unforgiving record of your
 *     failures is rumination infrastructure, and the carve-out is precisely the machine that builds
 *     it — it keeps exactly the rows a person would ruminate on and deletes the harmless ones.
 *     Rip's addressable memory is `purge(entries, now)` and nothing else, so "I'm going to remember"
 *     is FALSE. He's a VHS ghost. The tape degrades.
 *
 *  2. BREAK GLASS IS NOT A LEDGER KIND. Not filtered, not hidden, not soft-deleted — absent. There
 *     is no value to name, so no query can name it. `LedgerTest` asserts this over the enum and
 *     over this file's own source text. EVASION collapses in here (RESOLUTIONS §B) rather
 *     than living in its own table, precisely so that the isolation invariant has only one table to
 *     be true about.
 *
 * Pure JVM. `at` is epoch millis and enters as a parameter; nothing here reads a clock, and nothing
 * here formats a date (see `LedgerEntry.note`).
 */

/**
 * What the clerk is allowed to write down.
 *
 * FROZEN in the same spirit as `Target`: break glass is not an absent flag, it is an absent value.
 * A flag he wrote he can unwrite at 1am; a value that does not exist he cannot conjure. Confessions
 * are likewise unrepresentable — FOR THE RECORD never reaches the Ledger, and there is no kind for
 * it to arrive as. Food, weight and smoking have no kinds either, and never will.
 */
enum class LedgerKind(val cardLabel: String) {
    CAUGHT_FAKE("CAUGHT FAKE"),
    EVASION("EVASION"),
    EVASION_REBOOT("EVASION REBOOT"),
    FORCE_STOP("FORCE STOP"),
    OEM_KILL("OEM KILL"),
    CLOCK_JUMP("CLOCK JUMP"),
    POWER_SAVE_MINUTES("POWER SAVE"),
    NEAR_MISS("NEAR MISS"),
    DEMOTION("DEMOTION"),
}

/**
 * The docket the card always prints, in this order, even at zero. The `0`s are the point: they are a
 * scoreboard the user won, and a docket that only prints your failures is not a docket, it's a
 * grievance. Any other kind appears only when it actually has rows (see `ledgerRows`).
 */
val LEDGER_CARD_ORDER: List<LedgerKind> = listOf(
    LedgerKind.CAUGHT_FAKE,
    LedgerKind.EVASION,
    LedgerKind.FORCE_STOP,
    LedgerKind.OEM_KILL,
    LedgerKind.CLOCK_JUMP,
    LedgerKind.DEMOTION,
)

/**
 * One row of the docket.
 *
 * @param at epoch millis. Used for exactly one thing: purge arithmetic.
 * @param note the clerk's pre-formatted detail ("Thu 19:41  home ×4"). It is a string and not a
 *   derived render of [at] because turning millis into "Thu 19:41" needs a timezone, and :coach is
 *   pure JVM with no clock and no locale. The Android writer formats at write time; the composer
 *   only ever copies. Keep it short — the card is monospace and the segment is 10 seconds.
 */
data class LedgerEntry(
    val kind: LedgerKind,
    val habitId: String?,
    val at: Long,
    val note: String? = null,
)

/** RESOLUTIONS §B. Not a preference, not a setting, not overridable. */
const val LEDGER_PURGE_DAYS = 28

/**
 * THE PURGE. Hard delete, unconditional, at 28 days.
 *
 * There is deliberately no `entry`, no `habit`, no `kind` and no `repeatCount` parameter, because
 * every one of them is a place to put an exception, and the exception is the bug. A thrice-repeated
 * failure is deleted on exactly the same day as a one-off. That is not leniency, it is the
 * character: he is the one format that can't hold a grudge.
 *
 * Callers must treat the return as the new table. This is a DELETE, not a view filter — the rows the
 * caller drops must not be recoverable, or "I've tried, it's gone" becomes a lie the app tells.
 */
fun purge(entries: List<LedgerEntry>, now: Long): List<LedgerEntry> =
    entries.filter { now - it.at < LEDGER_PURGE_DAYS.days }

/**
 * The ONLY thing permitted to outlive the purge: dateless integers.
 *
 * He may say "forty-one." He may not say "that Tuesday in March." A count is not a memory and cannot
 * be ruminated on — you cannot replay a scene you only have a tally of. This class holds no
 * timestamp, and `LedgerTest` asserts by reflection that it never grows one.
 */
data class LifetimeCounters(val counts: Map<LedgerKind, Long> = emptyMap()) {

    operator fun get(kind: LedgerKind): Long = counts[kind] ?: 0L

    val total: Long get() = counts.values.sum()

    /**
     * Accrue at write time, before the purge can ever see the rows. Counters are monotonic: the
     * purge does not decrement them, because that would make the tally a function of the window and
     * the whole point is that it isn't.
     */
    fun accrue(entries: List<LedgerEntry>): LifetimeCounters {
        if (entries.isEmpty()) return this
        val next = counts.toMutableMap()
        for (e in entries) next[e.kind] = (next[e.kind] ?: 0L) + 1L
        return LifetimeCounters(next)
    }
}

/** A rendered docket line: label, count, and the clerk's detail. */
data class LedgerRow(val kind: LedgerKind, val count: Int, val detail: String?)

/**
 * Build the docket from the surviving window.
 *
 * Callers pass entries that have ALREADY been through [purge] — the composer never sees a row older
 * than 28 days, so there is no window parameter here to get wrong and no `since` for a query to
 * widen (SPEC §3.4: "no query against a FAILURE_TABLE may pass `since < now - 28d`").
 */
fun ledgerRows(surviving: List<LedgerEntry>): List<LedgerRow> {
    val byKind = surviving.groupBy { it.kind }
    val kinds = LEDGER_CARD_ORDER + byKind.keys.filter { it !in LEDGER_CARD_ORDER }.sorted()
    return kinds.map { kind ->
        val rows = byKind[kind].orEmpty()
        LedgerRow(
            kind = kind,
            count = rows.size,
            detail = rows.mapNotNull { it.note }.joinToString("  ").ifEmpty { null },
        )
    }
}

/** Monospace column width. "CAUGHT FAKE" + padding lands the count at column 20. */
internal const val LEDGER_LABEL_WIDTH = 19

internal fun LedgerRow.render(): String =
    kind.cardLabel.padEnd(LEDGER_LABEL_WIDTH) + count + (detail?.let { "   $it" } ?: "")
