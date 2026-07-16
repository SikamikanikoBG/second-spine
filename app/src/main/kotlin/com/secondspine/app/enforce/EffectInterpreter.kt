package com.secondspine.app.enforce

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.UserManager
import com.secondspine.app.LockActivity
import com.secondspine.app.data.BreakGlassRow
import com.secondspine.app.data.Graph
import com.secondspine.coach.DeviceContext
import com.secondspine.coach.Effect
import com.secondspine.coach.EscalationState
import com.secondspine.coach.EvasionKind
import com.secondspine.coach.Event
import com.secondspine.coach.Interlock
import com.secondspine.coach.LedgerKind
import com.secondspine.coach.LogKind
import com.secondspine.coach.LOCK_HOLD_BACK_MS
import com.secondspine.coach.Rung
import com.secondspine.coach.VoidReason
import com.secondspine.coach.mayEscalate
import com.secondspine.coach.step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * THE INTERPRETER — where `List<Effect>` stops being data and starts being a noise in a kitchen.
 *
 * `Escalation.kt` is the only file allowed to decide anything; this is the only file allowed to *do*
 * anything. The seam between them is the reason the dangerous half of this product is provable in CI
 * on a laptop with no SDK, and keeping the seam clean is worth more than any convenience that would
 * blur it. There is, deliberately, not one `if` in here about whether a rung *should* fire.
 */
internal class EffectInterpreter(private val context: Context) {

    private val scheduler = AlarmScheduler(context)
    private val store get() = ScheduleStore.get(context)

    fun interpret(state: EscalationState, effects: List<Effect>, now: Long) {
        for (effect in effects) {
            when (effect) {
                is Effect.ShowNotification -> once(effect, now) {
                    RungNotifications.show(context, effect.challengeId, Enforcement.line(effect.rung, effect.challengeId))
                }

                is Effect.Vibrate -> once(effect, now) { Buzz.fire(context) }

                is Effect.PlayAlarm -> once(effect, now) { AlarmTone.play(context) }

                is Effect.Speak -> once(effect, now) {
                    FlatVoice.speak(context, Enforcement.line(effect.rung, effect.challengeId))
                }

                // NOT deduped. See ScheduleStore's `effect_done` comment: re-emitting ShowLock for
                // the same (challengeId, rung) is the BOOMERANG, and it is idempotent by
                // construction because LockActivity is singleInstance.
                is Effect.ShowLock -> showLock(state, effect, now)

                is Effect.Cancel -> cancel(effect.challengeId, now)

                is Effect.Void -> {
                    cancel(effect.challengeId, now)
                    ledger(
                        when (effect.reason) {
                            VoidReason.CLOCK_TAMPER -> LedgerKind.CLOCK_JUMP
                            VoidReason.PLATFORM_LATE,
                            VoidReason.OEM_CANARY_FAIL,
                            VoidReason.PROCESS_DEATH,
                            -> LedgerKind.OEM_KILL
                        },
                        state.habitId,
                        now,
                    )
                }

                is Effect.Log -> log(effect, state, now)

                is Effect.ScheduleNext -> {
                    // The state carrying this `next_at` was committed by the coordinator before we
                    // were called. SPEC §6.3's write-ahead is satisfied by that ordering, not here.
                    scheduler.armRung(effect.challengeId, effect.rung, effect.at)
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // R4
    // -----------------------------------------------------------------------

    /**
     * THE LOCK, AND EVERY GATE IT HAS TO PASS BEFORE IT IS ALLOWED TO EXIST.
     *
     * `step` has already checked all of this. It is checked again here, and a third time in
     * `LockActivity.onCreate`, and the redundancy is the point: this is the one effect in the app
     * that can hurt someone, and it is the one place where "the brain already handled it" is not an
     * acceptable answer. Defence in depth on the only rung that takes something away.
     */
    private fun showLock(state: EscalationState, effect: Effect.ShowLock, now: Long) {
        // The structural blockers, the 14-day hold-back and every interlock — one shared predicate,
        // evaluated here and again in LockActivity.onCreate. Same rule, twice; never two rules.
        if (!lockPermittedNow(context, now)) return

        // Exercise only. RESOLUTIONS §B. This is the challenge-level half, which the device-level
        // predicate above cannot see.
        if (!state.lockEligible || !state.lockOptIn) return

        store.putBootLong(BootKeys.LAST_LOCK_AT, now)

        // The unconditional 90 s expiry goes up BEFORE the lock does. A crash between the two would
        // otherwise leave a lock with no deadline, which is the one failure this design refuses to
        // have: "a coarse model's false negative must never trap a man in his own phone."
        scheduler.armLockExpiry(effect.challengeId, (effect.expiresAt - now).coerceAtLeast(0L))

        val interactive = runCatching {
            context.getSystemService(PowerManager::class.java).isInteractive
        }.getOrDefault(true)

        if (!interactive) {
            // SCREEN OFF: the full-screen intent path. FSI only launches an Activity when the device
            // is locked or the screen is off — which is exactly here, and nowhere else.
            LockNotification.post(context, effect.challengeId)
            return
        }

        // IN USE: SPEC §6.4 — "SAW + a *visible* scrim is the only in-use path. Get this backwards
        // and it passes testing (recent-foreground grace) and dies silently in the field." The scrim
        // is not decoration: a VISIBLE overlay window is what grants the background-activity-launch
        // exemption on 14/15. Holding SYSTEM_ALERT_WINDOW is NOT sufficient.
        LockScrim.show(context, effect.challengeId)
    }

    // -----------------------------------------------------------------------
    // Cancel
    // -----------------------------------------------------------------------

    /**
     * Everything down, in the order that matters least to most.
     *
     * `Cancel` is delivered on satisfaction, on confession, on expiry, on stand-down, on void, and —
     * first and alone — on break glass. The break-glass path reaches this method within a frame of
     * the tap, which is why nothing in here is suspending, awaited, or capable of blocking.
     */
    fun cancel(challengeId: String, now: Long) {
        AlarmTone.stop()
        Buzz.stop(context)
        FlatVoice.stop()
        RungNotifications.clear(context)
        LockNotification.clear(context)
        LockScrim.hide(context)
        LockActivity.dismiss(context)
        scheduler.cancelChallenge(challengeId, now)
    }

    // -----------------------------------------------------------------------
    // The Ledger
    // -----------------------------------------------------------------------

    private fun log(effect: Effect.Log, state: EscalationState, now: Long) {
        when (effect.what) {
            LogKind.EVASION -> {
                ledger(
                    when (effect.evasion) {
                        EvasionKind.REBOOT -> LedgerKind.EVASION_REBOOT
                        EvasionKind.FORCE_STOP -> LedgerKind.FORCE_STOP
                        EvasionKind.HOME, EvasionKind.REVOKE, null -> LedgerKind.EVASION
                    },
                    state.habitId,
                    now,
                )
            }

            // SPEC §6.7 row 6: battery/power-save is capped AND logged — "log every minute; the total
            // is read on Sunday — unlogged escapes become the default path within a month". It is the
            // only interlock that writes to the Ledger, and `POWER_SAVE_MINUTES` exists for it alone.
            LogKind.INTERLOCK_SUSPEND ->
                if (effect.interlock == Interlock.BATTERY_LOW || effect.interlock == Interlock.BATTERY_CRITICAL) {
                    ledger(LedgerKind.POWER_SAVE_MINUTES, state.habitId, now)
                }

            // An interlock SUSPENDS. It never penalises, never counts, and never mocks. So the
            // resume, the stand-down and the void-as-log write nothing a character can read: the
            // Ledger is the failure surface, and none of these are his failures.
            LogKind.INTERLOCK_RESUME, LogKind.STOOD_DOWN, LogKind.VOIDED -> Unit
        }
    }

    private fun ledger(kind: LedgerKind, habitId: String?, at: Long) {
        // Room lives on credential-protected storage. Before first unlock it is not merely empty, it
        // is unreachable, and reaching for it throws. The Ledger is bookkeeping; the ladder is the
        // product. Losing a row to a Direct Boot is acceptable, and crashing the ladder to save one
        // is not.
        if (!userUnlocked(context)) return
        BackgroundWrites.launch {
            runCatching {
                Graph.install(context)
                Graph.repository.recordLedger(kind = kind, habitId = habitId, at = at)
            }
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Perform this effect at most once for its `(challengeId, rung)`.
     *
     * SPEC §6.3: *"Effects are idempotent on `(challengeId, rung)`. Alarms do double-fire on some
     * OEMs."* `step` refuses to re-enter a rung it has already entered, which is the real defence.
     * This is the second one, and it covers the seam the state machine cannot see: a crash after the
     * effects were produced but before the coordinator's write landed.
     */
    private inline fun once(effect: Effect, now: Long, body: () -> Unit) {
        val kind = effect::class.simpleName ?: return
        if (!store.claimEffect(effect.challengeId, effect.rung, kind, now)) return
        body()
    }
}

// ---------------------------------------------------------------------------
// The coordinator
// ---------------------------------------------------------------------------

/**
 * LOAD → `step` → **COMMIT** → INTERPRET. In that order, on one thread, forever.
 *
 * The `synchronized` block is doing real work rather than being defensive noise. Alarms arrive on the
 * main thread from a `BroadcastReceiver`, the lock's buttons arrive from an Activity, the proof seam
 * arrives from a coroutine, and every one of them mutates the same row. Two `step` calls interleaving
 * on one challenge would produce two different "next states", one of which silently wins — and if the
 * loser was the one holding `enteredRungs`, the rung fires twice.
 */
internal object Coordinator {

    private val lock = Any()

    /**
     * Deliver an event to a challenge.
     *
     * @return the new state, or null when there is no such challenge — which is not an error. A
     *   break-glass tap on a challenge that already expired is a no-op, and it is a no-op *silently*.
     */
    fun deliver(
        context: Context,
        challengeId: String,
        event: Event,
        now: Long = System.currentTimeMillis(),
    ): EscalationState? = synchronized(lock) {
        val store = ScheduleStore.get(context)
        val state = store.load(challengeId) ?: return null
        val ctx = DeviceContextReader(context).read()
        val (next, effects) = step(state, event, ctx, now)

        // WRITE-AHEAD. The state is committed BEFORE a single effect is interpreted, and before
        // AlarmScheduler is touched. Do not move this line below the interpreter for any reason.
        val schedule = effects.filterIsInstance<Effect.ScheduleNext>().firstOrNull()
        store.save(next, nextAt = schedule?.at, nextRung = schedule?.rung)

        // One ladder per 90 minutes is a LADDER-START gate, so the stamp goes down when the ladder
        // starts and not on every rung. `ceilingFor` neutralises this value on non-first rungs
        // precisely so the ladder cannot suspend itself against its own start at R1.
        if (state.enteredRungs.isEmpty() && next.enteredRungs.isNotEmpty()) {
            store.putBootLong(BootKeys.LAST_ESCALATION_AT, now)
        }

        EffectInterpreter(context).interpret(next, effects, now)
        next
    }

    /** Every live challenge gets a `Tick`. Drives expiry and the post-interlock settle. */
    fun tickAll(context: Context, now: Long = System.currentTimeMillis()) {
        for (live in ScheduleStore.get(context).live()) {
            deliver(context, live.state.challengeId, Event.Tick, now)
        }
        ScheduleStore.get(context).sweepTerminal(now - SWEEP_AFTER_MS)
    }

    /** Terminal ladders are swept after a day. They are alarms, not evidence — nothing reads them. */
    private const val SWEEP_AFTER_MS = 24L * 3_600_000L
}

// ---------------------------------------------------------------------------
// The isolate's writer
// ---------------------------------------------------------------------------

/**
 * THE ONE TAP'S RECORD, AND NOTHING ELSE.
 *
 * One insert. No read, no count, no flow, no "last used at". The DAO this calls has no `SELECT` in
 * it at all, by design, and this object exists so that the enforcement layer's single write to it is
 * one line in one file that a reviewer can look at.
 *
 * **The write is best-effort and the safety action never waits for it.** That is the DAO's own stated
 * contract: *"If the insert throws, the action still happens — the record is our bookkeeping, not his
 * obligation, and the safety path does not depend on the database being writable."* So: fire and
 * forget, off the caller's thread, and a failure here is invisible to him. Nothing follows. Not now,
 * not Sunday, not in a callback, not ever.
 */
internal object BreakGlassRecorder {

    fun record(context: Context, reason: String?) {
        // Before first unlock the database is unreachable. The valve still opens — it opened before
        // this method was called — and the row is simply not written. A man breaking glass at 04:00
        // after a power cut gets out; he does not get an error, and he does not get a note.
        if (!userUnlocked(context)) return
        BackgroundWrites.launch {
            runCatching {
                Graph.install(context)
                Graph.db.breakGlassDao().record(BreakGlassRow(at = System.currentTimeMillis(), reason = reason))
            }
        }
    }
}

// ---------------------------------------------------------------------------

/**
 * Writes that must outlive the component that started them.
 *
 * A `BroadcastReceiver`'s scope dies when `onReceive` returns and an Activity's dies when it
 * finishes. Break glass finishes the Activity in the same frame as the tap — so a coroutine scoped to
 * the Activity would be cancelled before the insert ran, every time.
 */
internal object BackgroundWrites {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

/**
 * Is credential-protected storage reachable yet?
 *
 * The gate on every Room touch in this package. SPEC §8.1: `data.db` is unreachable before first
 * unlock, which is why the schedule is a separate database.
 */
internal fun userUnlocked(context: Context): Boolean = runCatching {
    context.getSystemService(UserManager::class.java).isUserUnlocked
}.getOrDefault(true)

/** Convenience for the receiver: the service intent that carries one event. */
internal fun serviceIntentFor(
    context: Context,
    challengeId: String,
    event: ServiceEvent,
    extra: String? = null,
): Intent = Intent(context, EscalationService::class.java).apply {
    action = EscalationService.ACTION_EVENT
    putExtra(EscalationService.EXTRA_CHALLENGE_ID, challengeId)
    putExtra(EscalationService.EXTRA_EVENT, event.name)
    putExtra(EscalationService.EXTRA_EXTRA, extra)
}
