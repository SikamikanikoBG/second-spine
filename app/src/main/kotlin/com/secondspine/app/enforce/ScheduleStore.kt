package com.secondspine.app.enforce

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.secondspine.coach.EscalationState
import com.secondspine.coach.Phase
import com.secondspine.coach.Rung
import java.time.ZoneId

/**
 * `schedule.db` — THE WRITE-AHEAD LOG, AND THE ONLY DATABASE THAT EXISTS BEFORE FIRST UNLOCK.
 *
 * SPEC §8.1, verbatim: *"`schedule.db` — device-protected storage. Holds only what a
 * `directBootAware="true"` receiver needs to re-arm alarms before first unlock. **Neither WorkManager
 * nor Hilt is direct-boot aware; either will throw on that path.** The Direct Boot receiver touches
 * `schedule.db` and `AlarmManager` and nothing else, and constructs its DAO by hand."*
 *
 * So this is a hand-rolled [SQLiteOpenHelper] and not Room, and the reason is not taste:
 *
 *  - **Room's generated code is initialised through KSP-produced classes and an `InvalidationTracker`
 *    that opens a WAL on credential-protected storage.** Before first unlock that storage does not
 *    exist yet, and `data.db` is unreachable by definition. Pointing Room at a device-protected
 *    context to dodge that is worse, not better: it silently creates a *second* schema whose
 *    migrations nobody owns.
 *  - The Direct Boot path is the one path in this app where a wiring failure is unrecoverable — the
 *    ladder is simply gone for the rest of the day and nothing reports it. It gets the dumbest
 *    possible dependency: `android.database.sqlite`, which is in the boot classpath.
 *
 * **NOT ENCRYPTED, and that is the whole point:** there is no key before first unlock. Which is why
 * this database holds no content — ids, timestamps and rung integers, and nothing a human wrote.
 *
 * THE WRITE-AHEAD RULE (SPEC §6.3): `save()` commits *before* [AlarmScheduler] is called, always.
 * A crash between write and schedule is recoverable — the planner re-arms from `next_at` on next
 * open. A crash between fire and write is a double-penalty, and a double-penalty is an uninstall.
 */
internal class ScheduleStore private constructor(context: Context) :
    SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // The live ladder. One row per challenge, and it is the serialised EscalationState plus the
        // one derived column the Direct Boot receiver reads: next_at.
        db.execSQL(
            """
            CREATE TABLE escalation (
              challenge_id      TEXT PRIMARY KEY NOT NULL,
              habit_id          TEXT NOT NULL,
              rung              INTEGER NOT NULL,
              phase             TEXT NOT NULL,
              armed_at          INTEGER NOT NULL,
              expires_at        INTEGER NOT NULL,
              terminal_rung     INTEGER NOT NULL,
              lock_eligible     INTEGER NOT NULL,
              lock_opt_in       INTEGER NOT NULL,
              action_performable INTEGER NOT NULL,
              entered_rungs     TEXT NOT NULL,
              frozen_rung       INTEGER,
              suspended_since   INTEGER,
              last_rung_at      INTEGER NOT NULL,
              evasion_count     INTEGER NOT NULL,
              next_at           INTEGER,
              next_rung         INTEGER
            )
            """.trimIndent(),
        )

        /**
         * Every armed alarm, with BOTH clock references taken at arm time.
         *
         * `wall_ref`/`elapsed_ref`/`boot_count` are what make the AUTO-VOID arithmetic possible at
         * fire time. `Event.AlarmFired` wants two readings of the same instant — one from the wall
         * clock and one derived from the monotonic clock — and a monotonic clock is only meaningful
         * against a reference taken earlier on the same boot. Hence `boot_count`: `elapsedRealtime()`
         * resets to zero on reboot, so a reference from a previous boot must be discarded rather than
         * subtracted, or every reboot would look like a man moving his clock.
         */
        db.execSQL(
            """
            CREATE TABLE armed_alarm (
              challenge_id  TEXT NOT NULL,
              rung          INTEGER NOT NULL,
              scheduled_for INTEGER NOT NULL,
              wall_ref      INTEGER NOT NULL,
              elapsed_ref   INTEGER NOT NULL,
              boot_count    INTEGER NOT NULL,
              PRIMARY KEY (challenge_id, rung)
            )
            """.trimIndent(),
        )

        // SPEC §8.2's `decoy_alarm(id, challengeId, fireAt, cancelledAt)`, with the request code as
        // the key because the request code is the only handle AlarmManager will give back.
        db.execSQL(
            """
            CREATE TABLE decoy_alarm (
              request_code INTEGER PRIMARY KEY NOT NULL,
              challenge_id TEXT NOT NULL,
              fire_at      INTEGER NOT NULL,
              cancelled_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_decoy_challenge ON decoy_alarm(challenge_id)")

        /**
         * THE IDEMPOTENCY LEDGER, on the Android side of the seam.
         *
         * `step` already refuses to re-enter a rung in `enteredRungs`, which is the real defence and
         * is unit-tested in CI. This table defends the *other* seam: a crash between "interpret the
         * effect list" and "commit the new state" would replay the same effects on the next delivery.
         * Write-ahead ordering makes that lose an effect rather than duplicate one — but only for
         * effects the state machine gates. This is the belt to that brace, and it is keyed exactly as
         * the spec words it: `(challenge_id, rung)`, plus the effect kind so that a `Cancel` at R2 is
         * not confused with a `PlayAlarm` at R2.
         *
         * `ShowLock` is deliberately NOT recorded here. Re-emitting `ShowLock` for the same
         * `(challengeId, rung)` is the BOOMERANG — SPEC §6.4, "I'll be back in four hundred
         * milliseconds" — and it is idempotent by construction because the Activity is
         * `singleInstance`. Deduping it would delete the best writing in the app.
         */
        db.execSQL(
            """
            CREATE TABLE effect_done (
              challenge_id TEXT NOT NULL,
              rung         INTEGER NOT NULL,
              kind         TEXT NOT NULL,
              at           INTEGER NOT NULL,
              PRIMARY KEY (challenge_id, rung, kind)
            )
            """.trimIndent(),
        )

        /**
         * The boot-safe mirror of everything [DeviceContextReader] cannot read before first unlock.
         *
         * DataStore lives on credential-protected storage, so `installAt`, `winddownAtMinutes` and
         * `wakeAtMinutes` — the three inputs the *safety* interlocks are keyed on (RESOLUTIONS §D) —
         * are unreadable on the Direct Boot path. They are mirrored here on every write so that the
         * wind-down window is his and not a hardcoded 22:00 even at 03:00 after a power cut.
         */
        db.execSQL("CREATE TABLE boot_state (k TEXT PRIMARY KEY NOT NULL, v TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1. There is no migration to write yet, and this database holds no archive — it is
        // the day's alarms. If a future version cannot migrate it, dropping it costs one ladder and
        // no evidence, which is the one place in this codebase where that trade is acceptable.
        db.execSQL("DROP TABLE IF EXISTS escalation")
        db.execSQL("DROP TABLE IF EXISTS armed_alarm")
        db.execSQL("DROP TABLE IF EXISTS decoy_alarm")
        db.execSQL("DROP TABLE IF EXISTS effect_done")
        db.execSQL("DROP TABLE IF EXISTS boot_state")
        onCreate(db)
    }

    // -----------------------------------------------------------------------
    // The ladder
    // -----------------------------------------------------------------------

    /**
     * WRITE-AHEAD. Commit the state, then — and only then — may the caller touch [AlarmScheduler].
     *
     * @param nextAt the instant the next rung is due, persisted so that a `BOOT_COMPLETED` before
     *   first unlock can re-arm without reading a single credential-protected byte.
     */
    fun save(state: EscalationState, nextAt: Long? = null, nextRung: Rung? = null) {
        val v = ContentValues().apply {
            put("challenge_id", state.challengeId)
            put("habit_id", state.habitId)
            put("rung", state.rung.ordinal)
            put("phase", state.phase.name)
            put("armed_at", state.armedAt)
            put("expires_at", state.expiresAt)
            put("terminal_rung", state.terminalRung.ordinal)
            put("lock_eligible", if (state.lockEligible) 1 else 0)
            put("lock_opt_in", if (state.lockOptIn) 1 else 0)
            put("action_performable", if (state.actionStillPerformable) 1 else 0)
            put("entered_rungs", state.enteredRungs.joinToString(",") { it.ordinal.toString() })
            put("frozen_rung", state.frozenRung?.ordinal)
            put("suspended_since", state.suspendedSince)
            put("last_rung_at", state.lastRungAt)
            put("evasion_count", state.evasionCount)
            put("next_at", nextAt)
            put("next_rung", nextRung?.ordinal)
        }
        writableDatabase.insertWithOnConflict("escalation", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun load(challengeId: String): EscalationState? =
        readableDatabase.query(
            "escalation", null, "challenge_id = ?", arrayOf(challengeId), null, null, null,
        ).use { c -> if (c.moveToFirst()) c.toState() else null }

    /** Every challenge that has not reached a terminal phase. The re-arm set, and the tick set. */
    fun live(): List<LiveChallenge> =
        readableDatabase.query("escalation", null, null, null, null, null, "armed_at").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val state = c.toState()
                    if (state.phase.terminal) continue
                    add(
                        LiveChallenge(
                            state = state,
                            nextAt = c.getLongOrNull("next_at"),
                            nextRung = c.getIntOrNull("next_rung")?.let { Rung.entries[it] },
                        ),
                    )
                }
            }
        }

    /** Terminal rows older than the window are simply gone. The ladder is not evidence. */
    fun sweepTerminal(olderThan: Long) {
        writableDatabase.delete("escalation", "expires_at < ?", arrayOf(olderThan.toString()))
        writableDatabase.delete("effect_done", "at < ?", arrayOf(olderThan.toString()))
        writableDatabase.delete("decoy_alarm", "fire_at < ?", arrayOf(olderThan.toString()))
    }

    // -----------------------------------------------------------------------
    // Alarm references — the AUTO-VOID's arithmetic
    // -----------------------------------------------------------------------

    fun recordArm(challengeId: String, rung: Rung, scheduledFor: Long, ref: ClockRef) {
        val v = ContentValues().apply {
            put("challenge_id", challengeId)
            put("rung", rung.ordinal)
            put("scheduled_for", scheduledFor)
            put("wall_ref", ref.wall)
            put("elapsed_ref", ref.elapsed)
            put("boot_count", ref.bootCount)
        }
        writableDatabase.insertWithOnConflict("armed_alarm", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun armRef(challengeId: String, rung: Rung): ArmedAlarm? =
        readableDatabase.query(
            "armed_alarm", null, "challenge_id = ? AND rung = ?",
            arrayOf(challengeId, rung.ordinal.toString()), null, null, null,
        ).use { c ->
            if (!c.moveToFirst()) null else ArmedAlarm(
                scheduledFor = c.getLong(c.getColumnIndexOrThrow("scheduled_for")),
                ref = ClockRef(
                    wall = c.getLong(c.getColumnIndexOrThrow("wall_ref")),
                    elapsed = c.getLong(c.getColumnIndexOrThrow("elapsed_ref")),
                    bootCount = c.getInt(c.getColumnIndexOrThrow("boot_count")),
                ),
            )
        }

    fun clearArms(challengeId: String) {
        writableDatabase.delete("armed_alarm", "challenge_id = ?", arrayOf(challengeId))
    }

    // -----------------------------------------------------------------------
    // Decoys
    // -----------------------------------------------------------------------

    fun recordDecoy(requestCode: Int, challengeId: String, fireAt: Long) {
        val v = ContentValues().apply {
            put("request_code", requestCode)
            put("challenge_id", challengeId)
            put("fire_at", fireAt)
            putNull("cancelled_at")
        }
        writableDatabase.insertWithOnConflict("decoy_alarm", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Every decoy still standing for this challenge — i.e. armed, uncancelled. The losers. */
    fun liveDecoys(challengeId: String): List<Decoy> =
        readableDatabase.query(
            "decoy_alarm", null, "challenge_id = ? AND cancelled_at IS NULL",
            arrayOf(challengeId), null, null, "fire_at",
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Decoy(
                            requestCode = c.getInt(c.getColumnIndexOrThrow("request_code")),
                            challengeId = challengeId,
                            fireAt = c.getLong(c.getColumnIndexOrThrow("fire_at")),
                        ),
                    )
                }
            }
        }

    /** Every uncancelled decoy across every challenge. The Direct Boot re-arm set. */
    fun allLiveDecoys(): List<Decoy> =
        readableDatabase.query(
            "decoy_alarm", null, "cancelled_at IS NULL", null, null, null, "fire_at",
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Decoy(
                            requestCode = c.getInt(c.getColumnIndexOrThrow("request_code")),
                            challengeId = c.getString(c.getColumnIndexOrThrow("challenge_id")),
                            fireAt = c.getLong(c.getColumnIndexOrThrow("fire_at")),
                        ),
                    )
                }
            }
        }

    fun stampDecoyCancelled(requestCode: Int, at: Long) {
        writableDatabase.update(
            "decoy_alarm",
            ContentValues().apply { put("cancelled_at", at) },
            "request_code = ?",
            arrayOf(requestCode.toString()),
        )
    }

    // -----------------------------------------------------------------------
    // Idempotency
    // -----------------------------------------------------------------------

    /**
     * True if this effect has never been performed for this `(challengeId, rung)`, and marks it
     * performed in the same breath. Racy-by-design callers get exactly one `true`.
     */
    fun claimEffect(challengeId: String, rung: Rung, kind: String, at: Long): Boolean {
        val v = ContentValues().apply {
            put("challenge_id", challengeId)
            put("rung", rung.ordinal)
            put("kind", kind)
            put("at", at)
        }
        val row = writableDatabase.insertWithOnConflict(
            "effect_done", null, v, SQLiteDatabase.CONFLICT_IGNORE,
        )
        return row != -1L
    }

    // -----------------------------------------------------------------------
    // The boot-safe mirror
    // -----------------------------------------------------------------------

    fun putBoot(key: String, value: String?) {
        if (value == null) {
            writableDatabase.delete("boot_state", "k = ?", arrayOf(key))
            return
        }
        writableDatabase.insertWithOnConflict(
            "boot_state",
            null,
            ContentValues().apply { put("k", key); put("v", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getBoot(key: String): String? =
        readableDatabase.query("boot_state", arrayOf("v"), "k = ?", arrayOf(key), null, null, null)
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun putBootLong(key: String, value: Long?) = putBoot(key, value?.toString())
    fun getBootLong(key: String): Long? = getBoot(key)?.toLongOrNull()
    fun putBootInt(key: String, value: Int?) = putBoot(key, value?.toString())
    fun getBootInt(key: String): Int? = getBoot(key)?.toIntOrNull()
    fun putBootBool(key: String, value: Boolean) = putBoot(key, if (value) "1" else "0")
    fun getBootBool(key: String): Boolean = getBoot(key) == "1"

    /** The user's zone, mirrored so the wind-down window survives a Direct Boot. */
    fun bootZone(): ZoneId =
        runCatching { ZoneId.of(getBoot(BootKeys.ZONE) ?: ZoneId.systemDefault().id) }
            .getOrElse { ZoneId.systemDefault() }

    // -----------------------------------------------------------------------

    private fun android.database.Cursor.toState(): EscalationState {
        val terminal = Rung.entries[getInt(getColumnIndexOrThrow("terminal_rung"))]
        val lockEligible = getInt(getColumnIndexOrThrow("lock_eligible")) == 1
        val lockOptIn = getInt(getColumnIndexOrThrow("lock_opt_in")) == 1
        // `EscalationState.init` refuses to construct an R4-terminal challenge that is not a
        // lock-eligible, opted-in habit. A corrupted or hand-edited row must therefore be clamped
        // rather than thrown at the user at 06:41 — water can never reach R4, and that includes
        // water arriving through a deserialiser. This is the third of the three independent
        // mechanisms RESOLUTIONS §B asks for.
        val safeTerminal =
            if (terminal == Rung.R4_LOCK && !(lockEligible && lockOptIn)) Rung.R3_VOICE else terminal
        return EscalationState(
            challengeId = getString(getColumnIndexOrThrow("challenge_id")),
            habitId = getString(getColumnIndexOrThrow("habit_id")),
            rung = Rung.entries[getInt(getColumnIndexOrThrow("rung"))],
            phase = Phase.valueOf(getString(getColumnIndexOrThrow("phase"))),
            armedAt = getLong(getColumnIndexOrThrow("armed_at")),
            expiresAt = getLong(getColumnIndexOrThrow("expires_at")),
            terminalRung = safeTerminal,
            lockEligible = lockEligible,
            lockOptIn = lockOptIn,
            actionStillPerformable = getInt(getColumnIndexOrThrow("action_performable")) == 1,
            enteredRungs = getString(getColumnIndexOrThrow("entered_rungs"))
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .mapTo(LinkedHashSet()) { Rung.entries[it] },
            frozenRung = getIntOrNull("frozen_rung")?.let { Rung.entries[it] },
            suspendedSince = getLongOrNull("suspended_since"),
            lastRungAt = getLong(getColumnIndexOrThrow("last_rung_at")),
            evasionCount = getInt(getColumnIndexOrThrow("evasion_count")),
        )
    }

    private fun android.database.Cursor.getLongOrNull(name: String): Long? {
        val i = getColumnIndexOrThrow(name)
        return if (isNull(i)) null else getLong(i)
    }

    private fun android.database.Cursor.getIntOrNull(name: String): Int? {
        val i = getColumnIndexOrThrow(name)
        return if (isNull(i)) null else getInt(i)
    }

    companion object {
        const val NAME = "schedule.db"
        const val VERSION = 1

        @Volatile private var instance: ScheduleStore? = null

        /**
         * Always on the device-protected context, from every caller, including the ones that will
         * only ever run after unlock.
         *
         * There is exactly one copy of this database and it lives where the Direct Boot receiver can
         * reach it. A second, credential-protected copy created by a careless `Context` would be a
         * ladder that silently forks in two on the first reboot — the FGS writing to one and the boot
         * receiver re-arming from the other, forever, with no error anywhere.
         */
        fun get(context: Context): ScheduleStore =
            instance ?: synchronized(this) {
                instance ?: ScheduleStore(context.applicationContext.deviceProtected()).also {
                    instance = it
                }
            }
    }
}

/** A challenge, its serialised state, and where the ladder is due to go next. */
internal data class LiveChallenge(
    val state: EscalationState,
    val nextAt: Long?,
    val nextRung: Rung?,
)

/** Both clocks plus the boot identity, taken at the instant an alarm was armed. */
internal data class ClockRef(val wall: Long, val elapsed: Long, val bootCount: Int)

internal data class ArmedAlarm(val scheduledFor: Long, val ref: ClockRef)

internal data class Decoy(val requestCode: Int, val challengeId: String, val fireAt: Long)

/** The keys of the boot-safe mirror. Strings, because this table is a `TEXT` map on purpose. */
internal object BootKeys {
    const val INSTALL_AT = "install_at"
    const val WINDDOWN_MIN = "winddown_at_minutes"
    const val WAKE_MIN = "wake_at_minutes"
    const val ZONE = "zone"
    const val LAST_ESCALATION_AT = "last_escalation_at"
    const val LAST_LOCK_AT = "last_lock_at"
    const val PAUSED_MODE = "paused_mode"
    const val MEETING_UNTIL = "meeting_mode_until"
    const val STOOD_DOWN = "stood_down"
    const val COLLAPSING_COUNT = "collapsing_habit_count"
    const val LAST_APP_OPEN_AT = "last_app_open_at"
    const val EXACT_ALARMS_DENIED = "exact_alarms_denied"
}

/**
 * The device-protected context, and the one-line reason it is not optional.
 *
 * Credential-protected storage — the default — does not exist before the user's first unlock after a
 * reboot. A `directBootAware` receiver that touches it does not get an empty database; it gets an
 * exception, and the ladder for that morning is gone with no trace.
 */
internal fun Context.deviceProtected(): Context =
    if (isDeviceProtectedStorage) this else createDeviceProtectedStorageContext()
