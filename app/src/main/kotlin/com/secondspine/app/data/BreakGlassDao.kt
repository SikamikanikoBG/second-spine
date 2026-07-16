package com.secondspine.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * THE ISOLATE. This file exists to be the only file in the app that says `break_glass`.
 *
 * RESOLUTIONS §B: *"Narrow it: **no query may name `break_glass` at all.**"*
 *
 * That sentence, read to the letter, is not implementable, and the spec proves it two sections
 * later: SPEC §8.5's own purge job is `DELETE FROM break_glass WHERE at < now() - 28d`, which is a
 * query, and it names it. A table that cannot be purged breaks the 28-day rule that the isolate
 * needs more than any other table in the schema — a permanent record of every time he hit the panic
 * button is the exact artefact this design refuses to build.
 *
 * So the rule is implemented as the strongest form that is actually true, and stated here rather
 * than quietly softened:
 *
 *   **No query outside this file names `break_glass`, and no query anywhere reads a row out of it.**
 *
 * Two operations, and there is no third:
 *
 *  - [record] — one tap, instant, always works, never confirmed, never mocked in the moment.
 *  - [purgeBefore] — 28 days, unconditional, hard DELETE.
 *
 * There is deliberately **no SELECT**. Not a count, not a Flow, not a "last used at". SPEC §8.4
 * wires `BreakGlassDao` into a `StandDownDetector`, and that is cut from v1 on purpose: the moment
 * one subsystem can read this table, "never counted, never rendered, never referenced" becomes a
 * claim maintained by review instead of a fact maintained by the compiler. A row goes in; nothing
 * comes out; 28 days later it is gone. If v1.1 genuinely needs drop detection to see it, that is a
 * decision to make in daylight, with this comment in the diff.
 *
 * `LedgerKind` has no BREAK_GLASS value, so the clerk cannot write it down even by accident, and
 * `LEDGER_CARD_ORDER` has nothing to print. The isolation holds on both sides.
 */
@Dao
interface BreakGlassDao {

    /**
     * The user hit the button.
     *
     * `@Insert`, so not even the write is a query string that a grep for `break_glass` in a `@Query`
     * would find. Fire-and-forget: this must never block, never confirm, never fail loudly. If the
     * insert throws, the break-glass action still happens — the record is our bookkeeping, not his
     * obligation, and the safety path does not depend on the database being writable.
     */
    @Insert
    suspend fun record(row: BreakGlassRow)

    /** SPEC §8.5. The only other statement permitted to exist about this table. */
    @Query("DELETE FROM break_glass WHERE at < :cutoff")
    suspend fun purgeBefore(cutoff: Long): Int
}
