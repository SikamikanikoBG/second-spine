package com.secondspine.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.secondspine.coach.LedgerKind

/**
 * The DAOs.
 *
 * ONE RULE GOVERNS THIS FILE: **the isolate's table name does not appear in it.** Not in a query,
 * not in a join, not in a comment, not even to filter it out. That is RESOLUTIONS §B's narrowed
 * isolation invariant, and it is expressed this way so that a dumb grep for the table name across
 * `app/src/main/kotlin/` can check it: exactly two files may contain it — the entity that declares
 * the table and the DAO that owns it — and this is neither.
 *
 * Hence the circumlocution. This comment cannot spell the name it is talking about without breaking
 * the rule it is describing, which is the point: the invariant is checkable precisely because prose
 * is not exempt from it. See BreakGlassDao.kt for why exactly one query must exist.
 *
 * `confession` and `ledger_entry` also never appear in the same query as each other. A confession is
 * not a failure, and a query that can see both is a query that could one day count them together.
 */

// ---------------------------------------------------------------------------
// PIPELINE
// ---------------------------------------------------------------------------

@Dao
interface HabitDao {

    /** Every habit, dormant ones included. The repository filters; the DAO does not editorialise. */
    @Query("SELECT * FROM habit ORDER BY pillar")
    fun observeAll(): Flow<List<HabitRow>>

    @Query("SELECT * FROM habit WHERE enabled = 1 ORDER BY pillar")
    fun observeEnabled(): Flow<List<HabitRow>>

    @Query("SELECT * FROM habit WHERE enabled = 1 ORDER BY pillar")
    suspend fun enabled(): List<HabitRow>

    @Query("SELECT * FROM habit")
    suspend fun all(): List<HabitRow>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun byId(id: String): HabitRow?

    @Query("SELECT COUNT(*) FROM habit")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<HabitRow>)

    @Update
    suspend fun update(row: HabitRow)

    /** Stage moves are written by the pipeline only, together with a `stage_transition` row. */
    @Query("UPDATE habit SET stage = :stage, stageSince = :at WHERE id = :id")
    suspend fun setStage(id: String, stage: String, at: Long)

    @Query("UPDATE habit SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}

@Dao
interface DayDao {

    @Query("SELECT * FROM day WHERE habitId = :habitId ORDER BY epochDay DESC")
    fun observeForHabit(habitId: String): Flow<List<DayRow>>

    @Query("SELECT * FROM day WHERE habitId = :habitId AND epochDay >= :sinceEpochDay ORDER BY epochDay")
    suspend fun forHabitSince(habitId: String, sinceEpochDay: Long): List<DayRow>

    @Query("SELECT * FROM day WHERE epochDay >= :sinceEpochDay ORDER BY epochDay")
    suspend fun allSince(sinceEpochDay: Long): List<DayRow>

    @Query("SELECT * FROM day WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun find(habitId: String, epochDay: Long): DayRow?

    @Query("SELECT * FROM day")
    suspend fun all(): List<DayRow>

    @Upsert
    suspend fun upsert(row: DayRow)

    /** Marking completion never overwrites a confession: honesty outranks a later tap. */
    @Query("UPDATE day SET completed = 1 WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun markCompleted(habitId: String, epochDay: Long)

    @Query("UPDATE day SET confessed = 1 WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun markConfessed(habitId: String, epochDay: Long)

    @Query("UPDATE day SET suspended = 1 WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun markSuspended(habitId: String, epochDay: Long)
}

@Dao
interface StageTransitionDao {

    @Query("SELECT * FROM stage_transition ORDER BY at DESC")
    fun observeAll(): Flow<List<StageTransitionRow>>

    @Query("SELECT * FROM stage_transition WHERE habitId = :habitId ORDER BY at DESC")
    suspend fun forHabit(habitId: String): List<StageTransitionRow>

    @Insert
    suspend fun insert(row: StageTransitionRow): Long
}

// ---------------------------------------------------------------------------
// CHALLENGES
// ---------------------------------------------------------------------------

@Dao
interface ChallengeDao {

    @Query("SELECT * FROM challenge WHERE id = :id")
    suspend fun byId(id: Long): ChallengeRow?

    @Query("SELECT * FROM challenge WHERE state IN ('ARMED', 'ISSUED') ORDER BY issuedAt")
    fun observeOpen(): Flow<List<ChallengeRow>>

    /**
     * The one open challenge for a habit. NEVER MORE THAN ONE DEMAND VISIBLE AT A TIME — the design
     * law is upheld by the query as well as by the composable, so the UI cannot be handed a list it
     * would be tempted to render as a todo list. Todo lists die.
     */
    @Query("SELECT * FROM challenge WHERE habitId = :habitId AND state IN ('ARMED', 'ISSUED') ORDER BY issuedAt LIMIT 1")
    suspend fun openForHabit(habitId: String): ChallengeRow?

    @Query("SELECT * FROM challenge WHERE state = 'ISSUED' AND expiresAt < :now")
    suspend fun expired(now: Long): List<ChallengeRow>

    /** RESOLUTIONS §B: audits are ~15% suspicion-weighted and capped at 2/day. This counts the cap. */
    @Query("SELECT COUNT(*) FROM challenge WHERE isAudit = 1 AND issuedAt >= :sinceMillis")
    suspend fun auditCountSince(sinceMillis: Long): Int

    @Insert
    suspend fun insert(row: ChallengeRow): Long

    @Query("UPDATE challenge SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: String)
}

// ---------------------------------------------------------------------------
// PROOF — the archive
// ---------------------------------------------------------------------------

@Dao
interface ProofDao {

    @Query("SELECT * FROM proof ORDER BY capturedAtWall DESC")
    fun observeAll(): Flow<List<ProofRow>>

    @Query("SELECT * FROM proof WHERE habitId = :habitId ORDER BY capturedAtWall DESC")
    fun observeForHabit(habitId: String): Flow<List<ProofRow>>

    @Query("SELECT * FROM proof ORDER BY capturedAtWall DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ProofRow>

    @Query("SELECT COUNT(*) FROM proof")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM proof WHERE id = :id")
    suspend fun byId(id: Long): ProofRow?

    /**
     * THE INTEGRITY CHECK, and the whole of it.
     *
     * A prior proof with the same SHA-256 over the decoded pixel buffer is the only thing in this
     * app that can call a capture fake. It is arithmetic: a real sensor cannot emit an identical
     * buffer twice. Everything softer than this — pHash, timing, "suspicion" — was cut, because a
     * probabilistic accusation fires on truthful nights and that is the #1 rage-uninstall.
     */
    @Query("SELECT * FROM proof WHERE pixelSha256 = :sha AND id != :excludingId LIMIT 1")
    suspend fun findByPixelHash(sha: String, excludingId: Long): ProofRow?

    @Insert
    suspend fun insert(row: ProofRow): Long

    @Query("UPDATE proof SET appealed = 1 WHERE id = :id")
    suspend fun markAppealed(id: Long)

    @Query("UPDATE proof SET voided = 1 WHERE id = :id")
    suspend fun markVoided(id: Long)

    /** Any single proof is deletable, always, and Rip never comments on a deletion (SPEC §8.6). */
    @Query("DELETE FROM proof WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CaughtEventDao {

    @Query("SELECT * FROM caught_event ORDER BY at DESC")
    suspend fun all(): List<CaughtEventRow>

    @Query("SELECT * FROM caught_event WHERE habitId = :habitId ORDER BY at DESC")
    suspend fun forHabit(habitId: String): List<CaughtEventRow>

    @Query("SELECT * FROM caught_event WHERE at >= :since ORDER BY at DESC")
    suspend fun since(since: Long): List<CaughtEventRow>

    @Insert
    suspend fun insert(row: CaughtEventRow): Long
}

// ---------------------------------------------------------------------------
// CONFESSION — free, unlimited, warm. Never counted against him.
// ---------------------------------------------------------------------------

@Dao
interface ConfessionDao {

    @Query("SELECT * FROM confession ORDER BY at DESC")
    fun observeAll(): Flow<List<ConfessionRow>>

    @Query("SELECT * FROM confession WHERE habitId = :habitId ORDER BY at DESC")
    suspend fun forHabit(habitId: String): List<ConfessionRow>

    @Insert
    suspend fun insert(row: ConfessionRow): Long

    /**
     * SPEC §8.5. Confessions purge at 28 days like everything else about a hard week.
     *
     * Note what this is NOT: it is not a count, and there is no `confession_count_this_week` column
     * for it to feed. The cap was deleted, so the counter that would enforce it does not exist.
     */
    @Query("DELETE FROM confession WHERE at < :cutoff")
    suspend fun purgeBefore(cutoff: Long): Int
}

// ---------------------------------------------------------------------------
// THE LEDGER — the only failure surface, and it forgets
// ---------------------------------------------------------------------------

@Dao
interface LedgerDao {

    /**
     * The window is closed at the DAO, not at the caller.
     *
     * SPEC §3.4: "no query against a FAILURE_TABLE may pass `since < now - 28d`". There is therefore
     * no `since` parameter here for a caller to widen — the cutoff is computed from `now` inside the
     * query, and 28 is not a parameter either. Purge is unconditional (RESOLUTIONS §B); this read is
     * the belt to its braces, so a row that outlives a missed purge still cannot be spoken.
     */
    @Query("SELECT * FROM ledger_entry WHERE at >= :now - 2419200000 ORDER BY at DESC")
    suspend fun surviving(now: Long): List<LedgerEntryRow>

    @Query("SELECT * FROM ledger_entry WHERE at >= :now - 2419200000 ORDER BY at DESC")
    fun observeSurviving(now: Long): Flow<List<LedgerEntryRow>>

    @Insert
    suspend fun insert(row: LedgerEntryRow): Long

    /**
     * THE PURGE. Hard DELETE, unconditional, 28 days.
     *
     * No `kind` parameter, no `habitId` parameter, no repeat-offence carve-out — every one of them
     * is a place to put an exception, and the exception is the bug. He structurally cannot hold a
     * pattern against you.
     */
    @Query("DELETE FROM ledger_entry WHERE at < :cutoff")
    suspend fun purgeBefore(cutoff: Long): Int

    /**
     * Dateless integers: the only thing permitted to outlive the purge.
     *
     * He may say "forty-one." He may not say "that Tuesday in March." Accrued at write time into
     * DataStore, because a count is not a memory and cannot be ruminated on.
     */
    @Query("SELECT COUNT(*) FROM ledger_entry WHERE kind = :kind")
    suspend fun countOfKind(kind: LedgerKind): Long
}

// ---------------------------------------------------------------------------
// INSTRUMENTS
// ---------------------------------------------------------------------------

@Dao
interface AppOpenDao {

    @Insert
    suspend fun insert(row: AppOpenRow): Long

    /**
     * THE KILL CRITERION'S READ (RESOLUTIONS §D).
     *
     * "Unprompted" means LAUNCHER: he picked the phone up and chose this app, with nothing buzzing
     * at him. An open that a notification or an alarm produced measures the ladder, not the product,
     * and counting those would let the app pass its own test by nagging harder — which is precisely
     * the failure the pre-commitment exists to catch.
     */
    @Query("SELECT COUNT(*) FROM app_open WHERE source = 'LAUNCHER' AND at >= :since")
    suspend fun unpromptedCountSince(since: Long): Int

    @Query("SELECT MAX(at) FROM app_open")
    suspend fun lastOpenAt(): Long?

    @Query("SELECT * FROM app_open WHERE at >= :since ORDER BY at DESC")
    suspend fun since(since: Long): List<AppOpenRow>

    /** App opens are not evidence of failure, so they are not purged with the Ledger. */
    @Query("DELETE FROM app_open WHERE at < :cutoff")
    suspend fun trimBefore(cutoff: Long): Int
}

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_entry ORDER BY at")
    fun observeAll(): Flow<List<WeightEntryRow>>

    @Query("SELECT * FROM weight_entry ORDER BY at")
    suspend fun all(): List<WeightEntryRow>

    @Query("SELECT * FROM weight_entry ORDER BY at DESC LIMIT 1")
    suspend fun latest(): WeightEntryRow?

    @Insert
    suspend fun insert(row: WeightEntryRow): Long

    @Query("DELETE FROM weight_entry WHERE id = :id")
    suspend fun delete(id: Long)
}
