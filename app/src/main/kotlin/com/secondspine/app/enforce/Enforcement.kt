package com.secondspine.app.enforce

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.secondspine.app.MainActivity
import com.secondspine.coach.EvasionKind
import com.secondspine.coach.Event
import com.secondspine.coach.Habit
import com.secondspine.coach.PausedMode
import com.secondspine.coach.Rung
import com.secondspine.coach.arm
import java.time.ZoneId
import kotlin.random.Random

/**
 * THE ENFORCEMENT LAYER'S FRONT DOOR — the only surface the rest of the app is meant to touch.
 *
 * Everything else in this package is `internal` to it. The rule this expresses is the one from
 * `Escalation.kt`: *"`Effect` is DATA; the `:app` module is the only thing that interprets it."* This
 * object is where that interpretation is entered, and the ladder has exactly one entrance so that
 * "did anything else schedule an alarm?" is a question with a one-word answer.
 *
 * ### What this is not
 *
 * It is not a state machine. Every decision in here belongs to `:coach` — `step()` decides what
 * happens, `mayEscalate()` decides whether it is allowed, and this object turns the resulting data
 * into `AlarmManager` calls and vibrations. If you find yourself adding an `if` about *whether* to
 * escalate, it belongs in `Escalation.kt` or `Interlocks.kt`, where CI can prove it.
 */
object Enforcement {

    // -----------------------------------------------------------------------
    // Arming — the planner's entry point
    // -----------------------------------------------------------------------

    /**
     * Arm a challenge: write-ahead, then the real alarm, then the decoys.
     *
     * The order is SPEC §6.3's and it is not cosmetic. State is committed to `schedule.db` **before**
     * `setAlarmClock` is called, so a crash between the two loses an *alarm* (which the planner
     * re-arms from `next_at` on next open) rather than losing the *record* (which would let the same
     * rung fire twice, and a double penalty is an uninstall).
     *
     * @param fireAt the true R0 instant, already jittered by the planner. Never a round number.
     * @param windowStart earliest instant a decoy may occupy — normally "now". See [DecoyPlanner].
     * @param terminalRung the top of this habit's ladder. `arm()` in `:coach` clamps it: R4 is not
     *   constructible for anything that is not a lock-eligible, opted-in habit, so passing R4 for
     *   water does not produce a lock, it produces R3.
     */
    fun arm(
        context: Context,
        habit: Habit,
        challengeId: String,
        fireAt: Long,
        expiresAt: Long,
        windowStart: Long = System.currentTimeMillis(),
        lockOptIn: Boolean = false,
        terminalRung: Rung? = null,
        rng: Random = Random.Default,
    ) {
        val state = terminalRung
            ?.let { arm(challengeId, habit, fireAt, expiresAt, lockOptIn, it) }
            ?: arm(challengeId, habit, fireAt, expiresAt, lockOptIn)

        val store = ScheduleStore.get(context)
        // WRITE-AHEAD. This line is ahead of the scheduler on purpose. Do not reorder it.
        store.save(state, nextAt = fireAt, nextRung = Rung.R0_NOTIFICATION)

        val scheduler = AlarmScheduler(context)
        scheduler.armRung(challengeId, Rung.R0_NOTIFICATION, fireAt)
        // The decoys go up with the real alarm and not a moment later: between the two calls, the
        // lock screen is advertising the true fire time to anyone who looks.
        scheduler.armDecoys(challengeId, windowStart, expiresAt, fireAt, rng)
    }

    // -----------------------------------------------------------------------
    // Events — the seams other agents call
    // -----------------------------------------------------------------------

    /**
     * Proof was banked. Zero-assertion: nothing is rejected here, and this method cannot fail.
     *
     * Called by the capture path after `CoachRepository.bankProof`. Note it takes no verdict, no
     * confidence and no boolean — there is nothing for it to be told. The photograph exists; the
     * challenge is satisfied; the ladder comes down.
     */
    fun proofLogged(context: Context, challengeId: String) =
        EscalationService.deliver(context, challengeId, ServiceEvent.PROOF)

    /**
     * FOR THE RECORD. Free, unlimited, warm, never priced.
     *
     * Identical in every observable way to [proofLogged] from this layer's point of view: the ladder
     * comes down, nothing is counted, nothing is said. RESOLUTIONS §A1 — the button is cheaper than
     * lying, always, and a confession that made the enforcement layer behave even slightly differently
     * would be a price.
     */
    fun confessed(context: Context, challengeId: String) =
        EscalationService.deliver(context, challengeId, ServiceEvent.CONFESS)

    /**
     * BREAK GLASS. One tap. Instant. Gone. Go.
     *
     * This is a fire-and-forget `Intent` on purpose: it must not be `suspend`, must not return a
     * result, must not be awaited, and must not be able to fail in a way the caller has to handle.
     * Every one of those would be a place for a future edit to put a spinner in front of it.
     *
     * The record is written by [BreakGlassRecorder] and nothing reads it back. RESOLUTIONS §B's
     * isolation invariant is checkable by a dumb grep for the isolate's table name across
     * `app/src/main/kotlin/`, and it must hit exactly two files — the entity that declares it and the
     * DAO that owns it. So this comment cannot spell the name it is describing, which is the point:
     * prose is not exempt from the rule. See `BreakGlassDao.kt`, which is allowed to explain itself.
     */
    fun breakGlass(context: Context, challengeId: String, reason: String? = null) {
        BreakGlassRecorder.record(context, reason)
        EscalationService.deliver(context, challengeId, ServiceEvent.BREAK_GLASS)
    }

    /** Pressing HOME is a move in the game. Counted and roasted — unlike break glass, which is not. */
    fun evasion(context: Context, challengeId: String, kind: EvasionKind) =
        EscalationService.deliver(
            context, challengeId, ServiceEvent.EVASION, extra = kind.name,
        )

    /**
     * The behaviour is now complete and unchangeable. The coffee is drunk.
     *
     * From here the ladder is capped at R0 for this challenge, forever. SPEC §6.7: *"PENALTIES MAY BE
     * ANTICIPATORY OR INTERRUPTIVE. NEVER RETRIBUTIVE."*
     */
    fun actionNoLongerPerformable(context: Context, challengeId: String) =
        EscalationService.deliver(context, challengeId, ServiceEvent.NOT_PERFORMABLE)

    /** §7's drop detector. Character off, ceiling R0, Ledger frozen. */
    fun standDown(context: Context, challengeId: String) =
        EscalationService.deliver(context, challengeId, ServiceEvent.DROP_DETECTED)

    // -----------------------------------------------------------------------
    // The boot-safe mirror — the safety inputs, kept readable before first unlock
    // -----------------------------------------------------------------------

    /**
     * Mirror the interlocks' inputs into `schedule.db`.
     *
     * Call this from the settings screen and from the wizard on every change. RESOLUTIONS §D is why
     * it exists: the wind-down window is keyed on **his** `(winddownAt, wakeAt)` and not on a
     * hardcoded 22:00–08:00, and those two numbers live in DataStore — which is on
     * credential-protected storage and therefore unreadable on the Direct Boot path. A mirror that is
     * not kept current degrades silently into exactly the hardcoded window the resolution deletes.
     */
    fun syncBootState(
        context: Context,
        installAt: Long,
        winddownAtMinutes: Int,
        wakeAtMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val store = ScheduleStore.get(context)
        store.putBootLong(BootKeys.INSTALL_AT, installAt)
        store.putBootInt(BootKeys.WINDDOWN_MIN, winddownAtMinutes)
        store.putBootInt(BootKeys.WAKE_MIN, wakeAtMinutes)
        store.putBoot(BootKeys.ZONE, zone.id)
    }

    /** SPEC §6.7 row 14. Manual, uncapped, unpriced. No penalty, no debt, no catch-up. */
    fun setPausedMode(context: Context, mode: PausedMode?) =
        ScheduleStore.get(context).putBoot(BootKeys.PAUSED_MODE, mode?.name)

    /** SPEC §6.7 row 13. Manual tile, 90 min max. Logged, unmocked. */
    fun setMeetingModeUntil(context: Context, until: Long?) =
        ScheduleStore.get(context).putBootLong(BootKeys.MEETING_UNTIL, until)

    /** SPEC §6.7 row 15. */
    fun setStoodDown(context: Context, stoodDown: Boolean) =
        ScheduleStore.get(context).putBootBool(BootKeys.STOOD_DOWN, stoodDown)

    /**
     * THE SIGN INVERSION, and it must be fed or it does nothing.
     *
     * SPEC §6.7: *"the ladder may NEVER escalate in response to multi-habit collapse. A man drowning
     * does not need a louder coach."* `Interlocks.kt` implements the rule and reads
     * `collapsingHabitCount` to do it — but nothing in the platform can compute that number. The
     * pipeline can. If this is never called, the interlock is present, tested, and permanently
     * inactive, which is the worst of the three possible states.
     */
    fun setCollapsingHabitCount(context: Context, count: Int) =
        ScheduleStore.get(context).putBootInt(BootKeys.COLLAPSING_COUNT, count)

    /** SPEC §6.7 row 17. Ghosting >72 h and escalation disables itself. */
    fun setLastAppOpenAt(context: Context, at: Long) =
        ScheduleStore.get(context).putBootLong(BootKeys.LAST_APP_OPEN_AT, at)

    /**
     * Is the ladder actually able to fire on time?
     *
     * SPEC §6.5: gaps show as a permanent **COACH INTEGRITY** panel — *"a wound Rip does bits about,
     * never a modal"*. This is the read for that panel. Never nag on it.
     */
    fun exactAlarmsWorking(context: Context): Boolean =
        AlarmScheduler(context).canScheduleExact() &&
            !ScheduleStore.get(context).getBootBool(BootKeys.EXACT_ALARMS_DENIED)

    /** The Settings trip for 31–32, or null when there is nothing to ask for. Staged at first use. */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        AlarmScheduler(context).exactAlarmSettingsIntent()

    /**
     * Why the lock cannot fire, if it cannot. Empty means nothing structural is in the way.
     *
     * This is deliberately readable from outside the package: an app that refuses to lock should be
     * able to say so out loud rather than simply never doing the thing it advertised.
     */
    fun lockBlockers(context: Context): List<String> =
        lockSafe(context).blockers.map { it.name }

    // -----------------------------------------------------------------------
    // The line seam
    // -----------------------------------------------------------------------

    /**
     * Where the words come from. Wire `Voice.speak()` here; until then, the canon fallbacks below.
     *
     * The enforcement layer must not own the character. `Voice.kt` owns register selection, the
     * five-slot grammar, per-slot retirement and the play ledger, and all four of those need state
     * this package has no business holding. So the ladder asks for a line and does not care how it
     * was chosen.
     *
     * RESOLUTIONS §B: enforcement speech is ungoverned by the volunteered budget and gated by the
     * ladder instead — i.e. `speak(..., volunteered = false)`.
     */
    @Volatile
    var lineProvider: ((Rung, String) -> String)? = null

    /**
     * The fallbacks, and they are canon rather than invented.
     *
     * R3's is SPEC §6.4's, verbatim, because it is the line that is already written for the moment a
     * dead man starts talking in your kitchen. Nothing here is a placeholder to be "improved" later —
     * if these read as thin, the fix is to wire [lineProvider], not to write more dialogue in the
     * enforcement layer where the retirement rules cannot see it.
     */
    internal fun line(rung: Rung, challengeId: String): String =
        lineProvider?.invoke(rung, challengeId) ?: when (rung) {
            Rung.R0_NOTIFICATION -> "Okay. Camera's on."
            Rung.R1_VIBRATE -> "Still here."
            Rung.R2_ALARM -> "I don't get tired. I get patient."
            Rung.R3_VOICE -> "One set. That's the key. Camera's on."
            Rung.R4_LOCK -> "I own the phone now."
        }

    // -----------------------------------------------------------------------
    // Intents
    // -----------------------------------------------------------------------

    /**
     * BREAK GLASS AS A `PendingIntent`, and this is the one that has to work when nothing else does.
     *
     * SPEC §6.8: *"Must survive the overlay process crashing (it is a `PendingIntent` on the FGS
     * notification, not only a Compose button)."* A `PendingIntent` is dispatched by the system on our
     * behalf. Our process can be ANRing, mid-crash, or already dead; this still fires. The Compose
     * button is the pleasant path. This is the guarantee.
     *
     * `FLAG_IMMUTABLE`, and the request code is per-challenge so two live challenges cannot collide
     * into one another's valve.
     */
    fun breakGlassIntent(context: Context, challengeId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            challengeId.hashCode() * 64 + BREAK_GLASS_SLOT,
            Intent(context, EscalationReceiver::class.java).apply {
                action = EscalationReceiver.ACTION_BREAK_GLASS
                data = Uri.parse("secondspine://break/$challengeId")
                putExtra(EscalationReceiver.EXTRA_CHALLENGE_ID, challengeId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Tapping the coach opens the app at the demand. */
    fun openIntent(context: Context, challengeId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            challengeId.hashCode() * 64 + OPEN_SLOT,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CHALLENGE_ID, challengeId)
                putExtra(EXTRA_OPEN_SOURCE, "ALARM")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * THE PROOF SEAM.
     *
     * The lock demands a photograph; it does not take one. CameraX, the nonce, the pixel hash and the
     * zero-assertion bank belong to the capture path, and duplicating any of that here would give the
     * app two capture implementations — one of which nobody is testing.
     *
     * So `LockActivity`'s shutter launches `MainActivity` carrying [EXTRA_CHALLENGE_ID] and
     * [EXTRA_DEMAND_PROOF], and the capture surface calls [proofLogged] when the photograph is
     * banked. Until the capture path reads these extras, the shutter opens the app at the demand,
     * which is a degraded but honest path rather than a dead button.
     */
    const val EXTRA_CHALLENGE_ID = "com.secondspine.app.CHALLENGE_ID"
    const val EXTRA_DEMAND_PROOF = "com.secondspine.app.DEMAND_PROOF"
    const val EXTRA_OPEN_SOURCE = "com.secondspine.app.OPEN_SOURCE"

    private const val BREAK_GLASS_SLOT = 30
    private const val OPEN_SLOT = 31
}

/** Which event an `Intent` is carrying to the service. `Event` is not `Parcelable` and must not be. */
internal enum class ServiceEvent {
    RUNG, PROOF, CONFESS, BREAK_GLASS, EVASION, NOT_PERFORMABLE, DROP_DETECTED,
    TICK, REBOOT, LOCK_EXPIRY, CANARY,
}

/**
 * Turn a [ServiceEvent] into the brain's `Event`. The `AlarmFired` case is built in the service,
 * because it needs both clocks and the arm reference to construct.
 */
internal fun ServiceEvent.toEvent(extra: String?): Event? = when (this) {
    ServiceEvent.PROOF -> Event.ProofLogged
    ServiceEvent.CONFESS -> Event.Confessed
    ServiceEvent.BREAK_GLASS -> Event.BreakGlass
    ServiceEvent.NOT_PERFORMABLE -> Event.ActionNoLongerPerformable
    ServiceEvent.DROP_DETECTED -> Event.DropDetected
    ServiceEvent.TICK, ServiceEvent.LOCK_EXPIRY -> Event.Tick
    ServiceEvent.EVASION ->
        Event.Evasion(
            runCatching { EvasionKind.valueOf(extra ?: "") }.getOrDefault(EvasionKind.HOME),
        )
    ServiceEvent.CANARY -> Event.CanaryResult(passed = extra == "PASS")
    ServiceEvent.RUNG, ServiceEvent.REBOOT -> null // built in the service; both clocks required
}
