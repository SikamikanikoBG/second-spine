package com.secondspine.app.enforce

import android.content.Context
import com.secondspine.app.data.Graph
import com.secondspine.app.data.toEpochDay
import com.secondspine.app.ui.DemandSource
import com.secondspine.coach.Stage
import com.secondspine.coach.inWindDownWindow
import com.secondspine.coach.localMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.random.Random

/**
 * THE MISSING FRONT-DOOR CALLER.
 *
 * `Enforcement.arm()` is the single entrance to the entire escalating ladder — R0 notification, R1
 * vibrate, R2 alarm, R3 voice — and until this file existed **nothing called it.** Every downstream
 * seam was built and unit-tested: the state machine (`:coach` `step`), the alarm scheduler, the
 * decoy planner, the foreground service, the effect interpreter, the boot re-arm. They were a train
 * with no engine. `BootReceiver.rearm()` re-armed challenges from `schedule.db`, but that table was
 * always empty because the one thing that writes it — `arm()` — was never invoked. So the app that
 * advertises "an escalating ladder that reminds you to drink water" fired nothing, ever. The user
 * finished the wizard, snapped one photo, and then heard from the coach never again.
 *
 * This planner is the engine. Once a day, per enabled ENFORCED/AUDITED habit, it picks an
 * unpredictable fire time inside that habit's demand window and arms a challenge. The *when* is
 * deliberately random within the band (SPEC §6.5 — the whole deterrent is not knowing when); the
 * *whether* is guarded so the app is never noisy, never double-arms, and never nags during his night.
 *
 * ### It shares the demand window with the home card, on purpose
 *
 * The card on home ("drink water") and the alarm that fires for water MUST come from the same window,
 * or the two contradict each other: a card that says "owed" with no alarm behind it, or an alarm with
 * no card. So the window is `DemandSource.demandWindows` — the exact function the home screen resolves
 * its demand against — and not a second, drifting copy.
 *
 * ### Idempotent per (habit, day)
 *
 * The challenge id encodes the local day, so arming twice in one day for one habit is a no-op:
 * `ScheduleStore.load(id)` already holds a row (armed, satisfied, or expired) and the planner steps
 * over it. That makes this safe to call from every entry point that could plausibly fire — app open,
 * intake completion, the daily worker, a cold start — without any of them coordinating.
 */
internal object ChallengePlanner {

    /**
     * A freshly-armed reminder never fires inside the first two minutes.
     *
     * Arming at "now" would ring in the user's hand the instant he closed the wizard or reopened the
     * app, which reads as a bug, not a coach. Two minutes is enough to feel scheduled rather than
     * reflexive, and small enough that a demo still sees it within one sitting.
     */
    private const val LEAD_MS = 2L * 60_000L

    /**
     * How long a challenge's ladder is allowed to climb before it expires.
     *
     * The full ladder spans R0→R4 across forty minutes (`Rung.offsetMs`); this leaves a little room
     * past the top rung and is then clamped to the window close, so nothing ever climbs into his
     * wind-down. A challenge whose fire time lands near window close simply expires with an unclimbed
     * ladder — which is correct: the hours to perform it are genuinely gone.
     */
    private const val LADDER_SPAN_MS = 45L * 60_000L

    /**
     * Arm today's challenges. Never throws — a planner that crashes on a bad clock or a locked DB is
     * worse than one that quietly arms nothing this pass and tries again on the next open.
     */
    suspend fun planToday(
        context: Context,
        now: Long = System.currentTimeMillis(),
        rng: Random = Random.Default,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            Graph.install(context)
            val settings = Graph.settings

            // The ladder does not exist until the contract is signed. Before that the seed already
            // has habits enabled, and arming off them would fire a coach the user never agreed to.
            if (!settings.wizardComplete.first()) return@runCatching

            // His window and his zone — DemandSource forces the real wake/wind-down times back in.
            val ctx = DemandSource.deviceContext(context)

            // Never arm inside his night. The wind-down window is the one place the whole app agrees
            // to be silent, and a challenge armed now would climb an alarm into it.
            if (inWindDownWindow(ctx, now)) return@runCatching

            val habits = Graph.repository.habits.first()
                .filter { it.stage == Stage.ENFORCED || it.stage == Stage.AUDITED }
            if (habits.isEmpty()) return@runCatching

            val today = now.toEpochDay()
            val zone = ctx.zone
            val midnight = Instant.ofEpochMilli(now).atZone(zone)
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

            val windows = DemandSource
                .demandWindows(habits, ctx.wakeAtMinutes, ctx.winddownAtMinutes)
                .associateBy { it.habitId }

            val store = ScheduleStore.get(context)
            val dayDao = Graph.db.dayDao()

            for (habit in habits) {
                val window = windows[habit.id] ?: continue
                // A clamped window with open > close is legally empty (night-shift wrap, or the band
                // fell entirely inside wind-down). Nothing to arm.
                if (window.openAt > window.closeAt) continue

                val challengeId = challengeId(habit.id, today)
                // Already armed for this habit today, in any phase. Idempotent by construction.
                if (store.load(challengeId) != null) continue

                // Already answered today — a banked proof or a confession. Don't ask again.
                val day = dayDao.find(habit.id, today)
                if (day?.completed == true || day?.confessed == true) continue

                val openMs = midnight + window.openAt * 60_000L
                val closeMs = midnight + window.closeAt * 60_000L

                // The earliest a reminder may fire is the later of "the window is open" and "two
                // minutes from now". If that is already past the close, the window is over for today.
                val earliest = maxOf(now + LEAD_MS, openMs)
                if (earliest >= closeMs) continue

                // Unpredictable within the remaining window. This randomness is the deterrent.
                val fireAt = rng.nextLong(earliest, closeMs)
                val expiresAt = minOf(closeMs, fireAt + LADDER_SPAN_MS)

                // lockOptIn = false, always, in v1: R4 (the full-screen lock) ships dormant for the
                // first two weeks (RESOLUTIONS §E), and `arm()` structurally clamps any habit to R3
                // when it is not opted in. So exercise tops out at the spoken rung and water at the
                // alarm — nobody's phone gets locked over a glass of water, by construction.
                Enforcement.arm(
                    context = context,
                    habit = habit,
                    challengeId = challengeId,
                    fireAt = fireAt,
                    expiresAt = expiresAt,
                    windowStart = now,
                    lockOptIn = false,
                    rng = rng,
                )
            }
        }
        Unit
    }

    /** Idempotency key: one challenge per habit per local day. */
    private fun challengeId(habitId: String, epochDay: Long): String = "auto#$habitId#$epochDay"
}
