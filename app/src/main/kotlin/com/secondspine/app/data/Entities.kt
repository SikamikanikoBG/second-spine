package com.secondspine.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.secondspine.coach.CaughtKind
import com.secondspine.coach.LedgerKind
import com.secondspine.coach.Pillar
import com.secondspine.coach.Stage
import com.secondspine.coach.Tier
import com.secondspine.coach.TransitionReason

/**
 * THE SCHEMA.
 *
 * Read the absences first — they are the design, and they are enforced here rather than in a policy
 * document because a policy can be edited at 01:00 by the man the policy protects.
 *
 * **There is no food table.** No `is_healthy`, no `calories`, no `macros`, no `food_verdict`, no
 * `meal_photo`, no `junk`, no `nutrition_*`. THE DONUT IS ALLOWED. The donut-grief bit is dialogue;
 * dialogue needs no column. The harm was never the classifier's accuracy — it was the review loop —
 * so the loop's storage does not exist. A flag he wrote he can unwrite; a column that does not exist
 * he cannot conjure.
 *
 * **There is no `accepted` column on `proof`.** Zero-assertion banking: nothing is ever rejected,
 * because rejection is the thing this app does not do.
 *
 * **There is no `streak_count`.** Streaks are not a primary metric. Consistency is computed by
 * `:coach` from `day` rows, never stored.
 *
 * **There is no `confession_count`.** The cap is deleted, so the counter that would enforce it does
 * not exist.
 *
 * **There is no `goal_weight` and no `bmi`.** Weight leaves the game: `weight_entry` holds a trend
 * and nothing derived from it may be scored.
 *
 * Two Room facts, from RESOLUTIONS §C ("Platform lies to delete"): Room can express neither a `CHECK`
 * constraint nor a cross-database foreign key. Both are declared in SPEC §8 and neither is declared
 * here. Where SPEC wants a CHECK, the invariant lives in `:coach` where it is unit-tested.
 */

// ---------------------------------------------------------------------------
// PIPELINE — the odometer's source of truth
// ---------------------------------------------------------------------------

/**
 * A habit. Mirrors [com.secondspine.coach.Habit] plus the columns the brain must never see.
 *
 * `enabled` exists for an arithmetic reason. `MAX_ENFORCED = 2` and `MAX_AUDITED = 2`, so the
 * odometer's declared range is 0..4 — but there are seven pillars. Seeding all of them as ENFORCED
 * would make `jurisdiction()` return 7 on first run and blow the caps on install. So the pillar set
 * ships present-but-dormant, and [CoachRepository] passes only enabled rows into the brain. The
 * brain therefore never sees a habit it is not currently responsible for, which is exactly what
 * `jurisdiction()`'s doc means by "gone from his desk".
 */
@Entity(tableName = "habit")
data class HabitRow(
    @PrimaryKey val id: String,
    /** The pillar this habit serves. `:coach` reads [Pillar] for rung ceilings; it is not a Habit field. */
    val pillar: Pillar,
    /** Display title. Chunky type, no emoji — the UI never renders one. */
    val title: String,
    val stage: Stage,
    val tier: Tier,
    /** Epoch millis the habit entered [stage]. `daysInStage()` is a pure function of this. */
    val stageSince: Long,
    /** RESOLUTIONS §B: "Exercise is the only lock-eligible habit." Water terminates at R2. */
    val lockEligible: Boolean,
    /** False = present in the schema, absent from the odometer. See the class doc. */
    val enabled: Boolean,
)

/**
 * One scheduled demand on one day. Mirrors [com.secondspine.coach.Day].
 *
 * `confessed` and `suspended` are the two columns that make honesty dominate deception. RESOLUTIONS
 * §A1: both leave the compliance ratio entirely — the day stays recorded here as non-compliant, and
 * the promotion gate simply cannot see it.
 */
@Entity(
    tableName = "day",
    primaryKeys = ["habitId", "epochDay"],
    indices = [Index("habitId"), Index("epochDay")],
)
data class DayRow(
    val habitId: String,
    val epochDay: Long,
    val completed: Boolean,
    val confessed: Boolean = false,
    val suspended: Boolean = false,
)

/** Why a habit moved. RESOLUTIONS §A2 — `TransitionReason` has no CONFESSED value to store. */
@Entity(tableName = "stage_transition", indices = [Index("habitId"), Index("at")])
data class StageTransitionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    val from: Stage,
    val to: Stage,
    val reason: TransitionReason,
    val at: Long,
)

// ---------------------------------------------------------------------------
// CHALLENGES
// ---------------------------------------------------------------------------

/** Challenge lifecycle. A challenge is never "failed" — it expires, and the day carries the record. */
enum class ChallengeState { ARMED, ISSUED, ANSWERED, EXPIRED, VOIDED }

/**
 * A demand with a nonce.
 *
 * The nonce is what makes a proof a proof: it is issued before the capture and bound to it, so a
 * photograph taken yesterday cannot answer today's challenge.
 */
@Entity(tableName = "challenge", indices = [Index("habitId"), Index("issuedAt"), Index("state")])
data class ChallengeRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    val nonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val state: ChallengeState,
    /** True when this challenge is one of the ~15% suspicion-weighted audits (RESOLUTIONS §B, <=2/day). */
    val isAudit: Boolean = false,
)

// ---------------------------------------------------------------------------
// PROOF — the archive. Kept forever. The purge never touches it.
// ---------------------------------------------------------------------------

/** How a proof arrived. There is no `REJECTED`; there is no `accepted` column. */
enum class ProofKind { LIVE, CONFESSED }

/**
 * A banked proof.
 *
 * `pixelSha256` over the DECODED pixel buffer is the app's only integrity claim, and it is
 * arithmetic rather than an accusation: a real sensor cannot produce the same buffer twice.
 *
 * There is deliberately no `phash` column speaking here. RESOLUTIONS §A2 bans FRAME_REPLAY: pHash is
 * *engineered to be robust*, so honest re-shots of the same enrolled mug on the same static counter
 * collide, and the app's one insinuation would fire on truthful nights. pHash may return in v1.1 as
 * an archive clustering key for the Tape montage; it will never speak and never demote.
 *
 * `capturedAtElapsed` is `SystemClock.elapsedRealtime()`. Two clocks, because one of them is a
 * setting: a wall clock that disagrees with a monotonic clock is a clock jump, not a liar.
 */
@Entity(
    tableName = "proof",
    indices = [Index("habitId"), Index("challengeId"), Index("pixelSha256"), Index("capturedAtWall")],
)
data class ProofRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    /** Null for an unprompted proof — banking one uninvited is allowed and never punished. */
    val challengeId: Long?,
    /** Hex of SHA-256 over the decoded pixel buffer. The only thing that can produce a CaughtEvent. */
    val pixelSha256: String,
    val capturedAtWall: Long,
    val capturedAtElapsed: Long,
    val kind: ProofKind,
    /** The user disputed it. Read only by model-quality accounting; `:coach` never sees this. */
    val appealed: Boolean = false,
    /** An OEM kill or clock jump invalidated the window. A voided proof is never held against him. */
    val voided: Boolean = false,
    /** App-private path under filesDir/proofs/yyyy/MM. Never MediaStore — this app photographs his home. */
    val imagePath: String,
)

/**
 * THE ONLY WAY A PROOF CAN BE CALLED FAKE.
 *
 * Declared here because RESOLUTIONS §D lists it as a missing instrument: "`caught_event` is used by
 * the pipeline and declared in no schema section". `demotionCause()` and `shouldGraduate()` both
 * read it, so its absence was a hole under the pipeline.
 *
 * `kind` is [CaughtKind], whose only value is BYTE_REPLAY. There is no FRAME_REPLAY to store.
 */
@Entity(tableName = "caught_event", indices = [Index("habitId"), Index("at")])
data class CaughtEventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    /** The proof whose pixel hash collided. Kept as a plain id — see the no-FK note below. */
    val proofId: Long,
    val kind: CaughtKind,
    val at: Long,
)

// ---------------------------------------------------------------------------
// THE THREE DISTINCT OBJECTS — different tables, no joins, no foreign keys
//
// SPEC §8.4 wanted four. RESOLUTIONS §B collapsed `evasion` into `ledger_entry` "precisely so that
// the isolation invariant has only one table to be true about". So: three.
//
// No @ForeignKey anywhere between them, by design: a foreign key is a join the schema performs on
// your behalf, and the invariant is that these tables never meet.
// ---------------------------------------------------------------------------

/** What a confession was about. FREE. UNLIMITED. WARM. NEVER PRICED. NEVER DEMOTES. */
enum class ConfessionKind { MISSED_IT, DID_NOT_TRY, CANNOT_TODAY, FOR_THE_RECORD }

/**
 * FOR THE RECORD.
 *
 * Writes no `stage_transition`. Opens a 14-day repair window. Removes the day from the compliance
 * denominator. The button is cheaper than lying — always, at every hour, for every user. That is the
 * whole product.
 */
@Entity(tableName = "confession", indices = [Index("habitId"), Index("at")])
data class ConfessionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    val kind: ConfessionKind,
    val at: Long,
    val note: String? = null,
)

/**
 * THE ISOLATE.
 *
 * RESOLUTIONS §B narrowed the invariant to something a machine can check: **no query may name
 * `break_glass` at all.** Not "no query may join it" — name it. It has its own DAO, in its own file,
 * reachable from nothing that renders, counts, scores or speaks.
 *
 * Break glass is one tap, instant, always works, never confirmed, never mocked in the moment, and
 * never referenced by any subsystem afterwards. `LedgerKind` has no value for it, so the clerk
 * cannot write it down even by accident.
 */
@Entity(tableName = "break_glass", indices = [Index("at")])
data class BreakGlassRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    /** The user's own words, if he typed any. Never rendered back to him, never read by the coach. */
    val reason: String? = null,
)

/**
 * The clerk's docket. The only failure surface, and it forgets at 28 days — unconditionally.
 *
 * RESOLUTIONS §B deletes SPEC §3.4's `AND cluster_repeat_within_28d = 0` carve-out: it keeps exactly
 * the rows a person would ruminate on and deletes the harmless ones, which is rumination
 * infrastructure wearing a memory's clothes.
 */
@Entity(tableName = "ledger_entry", indices = [Index("habitId"), Index("at"), Index("kind")])
data class LedgerEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LedgerKind,
    /** Nullable: OEM_KILL and CLOCK_JUMP are app-wide, not a habit's fault. */
    val habitId: String?,
    val at: Long,
    /** Pre-formatted detail ("Thu 19:41  home x4"). `:coach` is pure JVM: it has no timezone. */
    val note: String? = null,
)

// ---------------------------------------------------------------------------
// INSTRUMENTS
// ---------------------------------------------------------------------------

/** How the app was opened. LAUNCHER is the one that counts. */
enum class AppOpenSource { LAUNCHER, NOTIFICATION, ALARM, LOCK, WIDGET }

/**
 * THE KILL CRITERION'S INSTRUMENT.
 *
 * RESOLUTIONS §D: "The kill criterion has no instrument for its own primary metric. The project
 * pre-commits to die on *unprompted opens < 1.0/day* and nothing anywhere records an app open."
 *
 * This table is that instrument, and [AppOpenDao.unpromptedOpensPerDay] is the 4-week rolling read
 * the pre-commitment is scored against. An app that can be killed by its own number needs to be able
 * to compute the number.
 */
@Entity(tableName = "app_open", indices = [Index("at"), Index("source")])
data class AppOpenRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val source: AppOpenSource,
)

/**
 * Weight. EWMA trend only.
 *
 * There is no `goal_weight`, no `bmi`, no `body_photo`, no `before_after`. The trend is never a
 * headline, never red, never green, never a penalty, and never an input to any habit's compliance.
 * `Pillar.WEIGHT` exists only so the guardrail can refuse it by name; Rip does not know the number
 * exists.
 */
@Entity(tableName = "weight_entry", indices = [Index("at")])
data class WeightEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kg: Double,
)
