package com.secondspine.app.ui

import android.content.Context
import com.secondspine.app.data.DayRow
import com.secondspine.app.data.Graph
import com.secondspine.app.data.toEpochDay
import com.secondspine.app.enforce.DeviceContextReader
import com.secondspine.coach.Day
import com.secondspine.coach.Demand
import com.secondspine.coach.DemandWindow
import com.secondspine.coach.DeviceContext
import com.secondspine.coach.Habit
import com.secondspine.coach.Quiet
import com.secondspine.coach.Stage
import com.secondspine.coach.inWindDownWindow
import com.secondspine.coach.localMinutes
import com.secondspine.coach.resolveDemandVerbose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * THE DEMAND, ASSEMBLED FROM THE REAL TABLES.
 *
 * `:coach`'s `resolveDemand()` is a pure function of four things — the habits, today's `day` rows, a
 * set of [DemandWindow]s, and a [DeviceContext]. Three of those existed in the database already. The
 * fourth, the windows, does not exist anywhere and **must not** be invented inside `:coach`: a window
 * is a scheduling policy, and the brain is deliberately ignorant of clocks. So it is defined here,
 * in the app layer, which is the only layer allowed to know what time it is.
 *
 * This file is the seam between the two, and it is deliberately the *only* new file: everything else
 * it touches — the DAOs, the settings store, the device reader — already existed and is only being
 * called for the first time.
 */
internal object DemandSource {

    // -----------------------------------------------------------------------
    // 1. THE WINDOWS — app-layer defaults, because intake does not store them
    // -----------------------------------------------------------------------

    /**
     * THE DEFAULT DEMAND WINDOWS, in minutes from local midnight.
     *
     * **These are defaults invented here, and that is a statement about what the intake actually
     * collects.** `IntakeState` stores exactly two times — `wakeAtMinutes` and the wind-down derived
     * from `bedAtMinutes` — and writes them to `SettingsStore`. It never asks "when do you drink
     * water", so there is nothing per-habit to derive from and no per-habit column to derive it into.
     * Pretending otherwise would mean inventing a fake read from a preference that does not exist.
     *
     * So each habit gets an honest default hour-band, and every band is then **clamped to the user's
     * own waking window** by [clamp] — which is the part that is genuinely his. The bands say *when in
     * a day this habit is plausibly performable*; his times say *when his day is*. The second always
     * wins.
     *
     * They live in `:app` and not in `:coach` for the same reason `DemandWindow` is a parameter and
     * not a constant: the brain must stay a pure function of the schedule it is handed, or the 300-day
     * soak test cannot simulate a schedule it disagrees with.
     */
    private val DEFAULT_BANDS: Map<String, IntRange> = mapOf(
        // The lock-eligible one. Widest band: "one set" is performable for essentially the whole day,
        // and narrowing it would be the app inventing a training schedule it was never told.
        "exercise" to (7 * 60)..(21 * 60),
        // RESOLUTIONS §E's thesis habit. Evening — a page before bed is the behaviour being seeded.
        "reading" to (18 * 60)..(23 * 60),
        "water" to (8 * 60)..(20 * 60),
        "coffee_cutoff" to (6 * 60)..(14 * 60),
        "smoking_cue" to (7 * 60)..(21 * 60),
        // "winddown" is absent on purpose: its band would be inside the wind-down window, which
        // `demandWindows` clamps to empty and `resolve` suppresses outright. It is a dormant habit
        // (Seed.kt: `enabled = false`), so nothing is lost today — but a future agent switching it on
        // must design the wind-down habit's demand deliberately rather than inherit a default that
        // silently never fires. Noted rather than faked.
    )

    /**
     * The bands, clamped to his waking day.
     *
     * A window with `openAt > closeAt` is a legally empty one: `resolveDemand` reads
     * `nowMinutes < openAt || nowMinutes > closeAt`, which is unconditionally true, so the habit
     * resolves to [Quiet.OUTSIDE_WINDOW] rather than vanishing. That is the correct reason — the
     * hours are genuinely not available — and it keeps the debug read honest.
     */
    fun demandWindows(habits: List<Habit>, wakeAt: Int, winddownAt: Int): List<DemandWindow> =
        habits.map { h ->
            val band = DEFAULT_BANDS[h.id] ?: (wakeAt..winddownAt)
            val (open, close) = clamp(band, wakeAt, winddownAt)
            DemandWindow(habitId = h.id, openAt = open, closeAt = close)
        }

    /**
     * Intersect a band with `[wakeAt, winddownAt)`.
     *
     * Only when the waking window does not wrap midnight. When it does — a night-shift user whose
     * wind-down is 06:00 and whose wake is 14:00 — the arithmetic here would be a second, worse copy
     * of the wrap-around logic that `:coach`'s `inMinuteWindow` already owns and unit-tests. So the
     * clamp declines, and [resolve]'s hard `inWindDownWindow` check carries the guarantee instead. The
     * band is never the safety mechanism; the check is.
     */
    private fun clamp(band: IntRange, wakeAt: Int, winddownAt: Int): Pair<Int, Int> {
        if (wakeAt >= winddownAt) return band.first to band.last
        return maxOf(band.first, wakeAt) to minOf(band.last, winddownAt - 1)
    }

    // -----------------------------------------------------------------------
    // 2. THE SCHEDULE — somebody has to write the `day` row
    // -----------------------------------------------------------------------

    /**
     * ENSURE TODAY IS ON THE BOOKS.
     *
     * `resolveDemand` reads `day == null` as [Quiet.NOTHING_SCHEDULED] — correctly, because a habit
     * with no row for today was not asked for today. Nothing in the app ever wrote that row: `day` is
     * touched only by `bankProof` (completed = true) and `confess` (confessed = true), both of which
     * are *records of an answer*. There was no writer for the question. That is the whole reason home
     * said "Nothing is owed" forever — not a rendering bug, an empty denominator.
     *
     * **The `find`-then-`upsert` is load-bearing and is not a micro-optimisation.** `DayRow`'s primary
     * key is `(habitId, epochDay)` and `@Upsert` replaces the whole row, so a bare upsert would
     * silently flip today's `completed = true` back to false every time this ran — deleting a banked
     * proof's day and, through `compliance()`, the user's graduation. It writes only when the day is
     * genuinely absent.
     *
     * Only ENFORCED/AUDITED habits get a row. A TRUSTED habit is off Rip's desk; giving it a `day`
     * row would put it back in `compliance()`'s denominator and let a graduated habit collapse-demote
     * itself in the background, which would silently un-graduate exactly the thing the pipeline exists
     * to hand back.
     */
    suspend fun ensureTodayScheduled(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        runCatching {
            val today = now.toEpochDay()
            val dao = Graph.db.dayDao()
            Graph.db.habitDao().enabled()
                .filter { it.stage == Stage.ENFORCED || it.stage == Stage.AUDITED }
                .forEach { habit ->
                    if (dao.find(habit.id, today) == null) {
                        dao.upsert(DayRow(habitId = habit.id, epochDay = today, completed = false))
                    }
                }
        }
        Unit
    }

    // -----------------------------------------------------------------------
    // 3. THE CONTEXT
    // -----------------------------------------------------------------------

    /**
     * The device snapshot, with his times forced back in.
     *
     * `DeviceContextReader` is the app's one Android→data flattener and is reused rather than
     * duplicated. But it sources `winddownAtMinutes`/`wakeAtMinutes` from `ScheduleStore`'s Direct
     * Boot mirror, which is written only by `Enforcement.arm()` — so on a device where the ladder has
     * never armed, the reader falls back to *constants* (22:30 / 07:00). Those constants are exactly
     * the hardcoded window RESOLUTIONS §D deleted. So the three fields the safety window is keyed on
     * are overwritten from `SettingsStore`, which is where the intake actually wrote them.
     */
    suspend fun deviceContext(context: Context): DeviceContext = withContext(Dispatchers.IO) {
        val settings = Graph.settings
        val raw = DeviceContextReader(context).read()
        raw.copy(
            installAt = settings.installAt.first().takeIf { it > 0L } ?: raw.installAt,
            winddownAtMinutes = settings.winddownAtMinutes.first(),
            wakeAtMinutes = settings.wakeAtMinutes.first(),
            zone = ZoneId.systemDefault(),
        )
    }

    // -----------------------------------------------------------------------
    // 4. THE ONE DEMAND
    // -----------------------------------------------------------------------

    /**
     * Resolve THE demand over the real tables. Exactly one, or null. Never a list.
     *
     * The wind-down check is here rather than left to the resolver, and the reason is a gap worth
     * naming: `Interlock.WIND_DOWN`'s ceiling is `R0_NOTIFICATION`, **not** null, so `mayEscalate(R0)`
     * stays true through the whole of the user's night and the resolver's own interlock gate does not
     * fire. That is right for the *ladder* — R0 is the rung wind-down permits — but a demand card is
     * not a rung, and the brief is explicit that no demand may sit inside his (winddownAt, wakeAt).
     * So the app layer, which owns the schedule, refuses to ask. `:coach` is untouched.
     */
    suspend fun resolve(
        context: Context,
        habits: List<Habit>,
        now: Long = System.currentTimeMillis(),
    ): Pair<Demand?, Map<String, Quiet>> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = deviceContext(context)

            // His night. Not 22:00–08:00 — his.
            if (inWindDownWindow(ctx, now)) {
                return@runCatching null to habits.associate { it.id to Quiet.INTERLOCKED }
            }

            val today = now.toEpochDay()
            val days = Graph.db.dayDao().allSince(today)
                .filter { it.epochDay == today }
                .map { Day(it.habitId, it.epochDay, it.completed, it.confessed, it.suspended) }

            resolveDemandVerbose(
                habits = habits,
                today = days,
                windows = demandWindows(habits, ctx.wakeAtMinutes, ctx.winddownAtMinutes),
                nowMinutes = localMinutes(ctx, now),
                now = now,
                ctx = ctx,
            )
        }.getOrElse { null to emptyMap() }
    }
}
