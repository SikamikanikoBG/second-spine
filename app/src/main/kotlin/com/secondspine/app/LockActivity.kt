package com.secondspine.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.secondspine.app.enforce.Enforcement
import com.secondspine.app.ui.theme.Gold
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.SecondSpineTheme
import com.secondspine.app.ui.theme.SsIcons
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.BreakGlassButton
import com.secondspine.app.ui.theme.ForTheRecordButton
import com.secondspine.app.ui.theme.MonoCaptionStyle
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SsChip
import com.secondspine.app.ui.theme.vhsTracking
import com.secondspine.coach.LOCK_EXPIRY_MS
import com.secondspine.coach.Register

/**
 * R4. THE LOCK — the only rung that takes something away, and the only code in this app that can hurt
 * someone.
 *
 * It is written accordingly. Every gate below is checked *again* here, after `step` has already
 * checked it and after `EffectInterpreter` has already checked it, and the redundancy is deliberate:
 * SPEC §6.7 says *"if a row is inconvenient to implement, the lock does not ship"*, and this Activity
 * is the last place that sentence can still be honoured before a man's phone stops being his.
 *
 * ### What holds it shut, in order
 *
 *  1. **The 14-day hold-back.** RESOLUTIONS §E. Before install + 14 days, R4 does not exist.
 *  2. **The structural blockers.** SPEC §6.7 row 3 has no implementation on this build — see
 *     `drivingSignal` — so the lock refuses. This is not a bug being tolerated; it is the rule being
 *     obeyed.
 *  3. **Every interlock**, through the brain's own `mayEscalate(R4_LOCK, ctx, now)`. Not a
 *     reimplementation. If this Activity ever grows its own copy of an interlock, the copy will drift
 *     and the drift will be discovered by a man who could not call an ambulance.
 *  4. **Exercise only.** RESOLUTIONS §B: *"locking a senior engineer's phone over a glass of water is
 *     the fastest uninstall available."*
 *  5. **The unconditional 90-second expiry**, which is an alarm in the FGS and not a coroutine here,
 *     so it survives this Activity crashing.
 *
 * ### And what opens it, always
 *
 * **BREAK GLASS.** One tap, first tap, instant, no confirm, no hold, no countdown, no cognition,
 * unlimited, never rate-limited, never degraded, and never mocked — not in the moment, not on Sunday,
 * not ever. It is the first control built in this file and the only one with no conditions attached
 * to it anywhere in its call path.
 */
class LockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Over the keyguard, and the screen comes on. Without these, R4 is a notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        // The screen stays on for the 90 seconds this thing is allowed to live, and not one second
        // more — the expiry alarm, not this flag, is what ends it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val challengeId = intent?.getStringExtra(EXTRA_CHALLENGE_ID)
        if (challengeId == null) {
            finish()
            return
        }

        // THE GATE. Asked and answered before a single pixel is drawn — a lock that appears and then
        // dismisses itself has already turned on the screen of a man who was driving.
        if (!LockGate.permitted(this, challengeId)) {
            finish()
            return
        }

        current = this

        // THE SECOND EXPIRY. The FGS alarm is authoritative; this one exists because the release is
        // the only mechanism in the ladder worth implementing twice.
        //
        // SPEC §6.4 puts the real deadline in the foreground service precisely so that it survives
        // this Activity crashing — a coroutine here would die with the thing it is supposed to be
        // policing. That argument is correct and it is why the alarm exists. But it runs the other
        // way too: the alarm can be delayed by a system that is under load or hostile, and the FGS
        // can be killed by an OEM that has decided our foreground service is a battery problem. The
        // failure mode of BOTH being late is a man holding a phone that will not let him do anything.
        //
        // So: a plain Handler on the main looper, cancelled on destroy, with no dependency on the
        // service, the store, or the ladder. Whichever deadline lands first wins. Neither can be
        // stopped by the other failing, and this one cannot be moved by touching the clock.
        window.decorView.postDelayed({ runCatching { finishAndRemoveTask() } }, LOCK_EXPIRY_MS)

        // Immersive: the system bars go away, because a half-lock with a visible notification shade
        // is a lock with a settings screen in it. `BY_SWIPE` and not `BY_TOUCH` — he can still get
        // the bars back with a deliberate swipe, which keeps every system affordance one gesture
        // away. This is theatre with an exit, not a kiosk.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            SecondSpineTheme {
                LockScreen(
                    onProof = { demandProof(challengeId) },
                    onConfess = {
                        Enforcement.confessed(this, challengeId)
                        finishAndRemoveTask()
                    },
                    // ONE TAP. Nothing between the finger and the exit.
                    onBreakGlass = {
                        Enforcement.breakGlass(this, challengeId)
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    /**
     * THE BOOMERANG — SPEC §6.4, and the platform limit that produced the best writing in the app.
     *
     * *"You cannot block HOME. Not with SAW, not with FSI, not with an accessibility service — only
     * Device Owner + `startLockTask()` on a factory-reset accountless phone, which is not v1 and never
     * will be. So the boomerang ships and Rip narrates his own impotence."*
     *
     * This does not re-launch anything itself. It reports the evasion to the ladder and lets `step`
     * decide — which is the same rule as everywhere else in this package, and it matters more here
     * than anywhere: `step` re-emits `ShowLock` only while the challenge is still CLIMBING at R4, so
     * the boomerang is arithmetically incapable of outliving the 90-second expiry. An Activity that
     * re-launched itself from `onStop` would be a lock with no off switch and a bug away from being
     * the worst thing this codebase could ship.
     */
    override fun onStop() {
        super.onStop()
        val challengeId = intent?.getStringExtra(EXTRA_CHALLENGE_ID) ?: return
        if (isFinishing) return
        Enforcement.evasion(this, challengeId, com.secondspine.coach.EvasionKind.HOME)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    /**
     * The shutter. See `Enforcement.EXTRA_DEMAND_PROOF`.
     *
     * The lock demands a photograph; it does not take one. CameraX, the nonce and the pixel hash
     * belong to the capture path, and a second capture implementation living in the enforcement layer
     * would be one nobody tests and one that would drift from the real one's integrity rules.
     */
    private fun demandProof(challengeId: String) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(Enforcement.EXTRA_CHALLENGE_ID, challengeId)
                    putExtra(Enforcement.EXTRA_DEMAND_PROOF, true)
                },
            )
        }
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_CHALLENGE_ID = "com.secondspine.app.LOCK_CHALLENGE_ID"

        /**
         * The live instance, so `Cancel` can take the lock down from the interpreter.
         *
         * A static reference to an Activity is normally a leak. It is not one here: it is cleared in
         * `onDestroy`, and the alternative — an ordered broadcast, or a bound service — is a
         * dismissal path with more moving parts than the thing it is dismissing. The lock must come
         * down the instant proof lands, and that instruction arrives from a service.
         */
        @Volatile
        private var current: LockActivity? = null

        fun dismiss(context: Context) {
            current?.let { activity ->
                current = null
                activity.runOnUiThread { runCatching { activity.finishAndRemoveTask() } }
            }
        }
    }
}

/**
 * THE GATE, as a function, so that it can be read in one screen and audited without a device.
 *
 * Note what it is not: it is not a new set of rules. Every clause is either the brain's own predicate
 * or a restatement of a rule the brain also enforces. Defence in depth means the same rule checked
 * twice — never a second, subtly different rule checked once.
 */
internal object LockGate {

    fun permitted(context: Context, challengeId: String): Boolean {
        val challenge = com.secondspine.app.enforce.ScheduleStore.get(context).load(challengeId)
            ?: return false

        // Terminal already: proof landed, or he broke glass, while the launch was in flight.
        if (challenge.phase.terminal) return false

        // Not at R4 — nothing asked for a lock. A LockActivity started by anything other than the
        // ladder reaching R4 is a bug or an attack, and either way the answer is no.
        if (challenge.rung != com.secondspine.coach.Rung.R4_LOCK) return false

        // Exercise only. RESOLUTIONS §B — three independent mechanisms, and this is the third.
        if (!challenge.lockEligible || !challenge.lockOptIn) return false

        // The 14-day hold-back, every interlock, and the structural blockers. One predicate, shared
        // with the interpreter, evaluated a second time here.
        return com.secondspine.app.enforce.lockPermittedNow(context)
    }
}

// ---------------------------------------------------------------------------
// The surface
// ---------------------------------------------------------------------------

/**
 * ONE DEMAND. NOT A LIST.
 *
 * The design law is that never more than one demand is visible at a time, because multiple demands
 * turn the coach into a todo list and todo lists die. The lock is the most literal expression of that
 * rule in the product: it is one sentence, one shutter, and two ways out.
 */
@Composable
private fun LockScreen(
    onProof: () -> Unit,
    onConfess: () -> Unit,
    onBreakGlass: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .vhsTracking(TapeWear.Worn, seed = 0.47f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                RipSpeech(
                    // SPEC §6.4, verbatim, and it is canon rather than placeholder copy. He announces
                    // the limit of his own power in the same breath as using it, which is the whole
                    // character in four lines.
                    text = "I own the phone now.\n\n" +
                        "That's a lie. You can press home. You will press home.\n\n" +
                        "I'll be back in four hundred milliseconds because I'm forty megabytes " +
                        "and a dream and I don't get tired. I get patient.",
                    register = Register.ARENA,
                )
                Spacer(Modifier.height(28.dp))
                RipSpeech(text = "One set. That's the key. Camera's on.", register = Register.PITCHMAN)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // The demand. One affordance, and it is an action rather than a device.
                SsChip(text = "One set", onClick = onProof, icon = SsIcons.Shutter, tint = Gold)

                Spacer(Modifier.height(24.dp))

                // FOR THE RECORD, from inside the lock. Free, unlimited, warm, never priced — and
                // reachable at the exact moment the temptation to fake is highest, which is the only
                // moment its price actually matters.
                ForTheRecordButton(onClick = onConfess)

                Spacer(Modifier.height(16.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    // Bottom-left, grey, never mentioned. SPEC §6.8.
                    BreakGlassButton(onBreak = onBreakGlass)
                }

                Spacer(Modifier.height(12.dp))

                // The app's own voice, not his: it tells him the thing expires whatever he does. He
                // would never say this — it is the app refusing to let him bluff about how much power
                // he has, and it is the reason the 90 seconds is a promise rather than a threat.
                androidx.compose.material3.Text(
                    text = "THIS ENDS IN 90 SECONDS EITHER WAY",
                    style = MonoCaptionStyle,
                    color = PaperFaint,
                )
            }
        }
    }
}
