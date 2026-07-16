package com.secondspine.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondspine.app.ui.Routes
import com.secondspine.app.ui.ScreenSlots
import com.secondspine.app.ui.SecondSpineNav
import com.secondspine.app.ui.home.HomeScreen
import com.secondspine.app.ui.home.HomeViewModel
import com.secondspine.app.ui.theme.SecondSpineTheme

/**
 * THE ONE ACTIVITY. (SPEC §4.1: a single `MainActivity` + `LockActivity`. Nothing else.)
 *
 * `LockActivity` is separate and lives in its own task because it must be able to appear over the
 * keyguard, survive this Activity being force-stopped, and carry its own process-independent BREAK
 * GLASS. Everything the user *chooses* to do lives here, behind one Compose graph.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.loadIntakeState()

        setContent {
            SecondSpineTheme {
                val intakeComplete by AppGraph.intakeComplete.collectAsStateWithLifecycle()
                val comebackDue by AppGraph.comebackDue.collectAsStateWithLifecycle()

                SecondSpineNav(
                    intakeComplete = intakeComplete,
                    comebackDue = comebackDue,
                    screens = ScreenSlots(
                        home = { nav ->
                            val vm: HomeViewModel = viewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            HomeScreen(
                                state = state,
                                onProof = { habitId -> nav.navigate(Routes.proof(habitId)) },
                                onForTheRecord = { /* wired by the confession agent */ },
                                onBreakGlass = { /* wired by the safety agent */ },
                                onOpenTape = { nav.navigate(Routes.TAPE) },
                                onOpenLedger = { nav.navigate(Routes.LEDGER) },
                                onOpenArchive = { nav.navigate(Routes.ARCHIVE) },
                                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                            )
                        },
                    ),
                )
            }
        }
    }

    /**
     * THE KILL CRITERION'S ONE LINE OF INSTRUMENTATION.
     *
     * RESOLUTIONS §D: the project pre-commits to die on *"unprompted opens < 1.0/day"* and, until
     * this call existed, nothing anywhere recorded an app open. A kill criterion you cannot measure
     * is not a pre-commitment, it is a paragraph — and SPEC §1.8 spends a page predicting a repo
     * whose last commit is April, about somebody else.
     *
     * Recorded on **resume**, not on create: `onCreate` misses every return from the background,
     * which is most of the opens that matter and nearly all of the unprompted ones.
     *
     * The source defaults to [AppOpenSource.LAUNCHER] and the notification/alarm paths must say so
     * explicitly via [EXTRA_OPEN_SOURCE]. That default is the conservative direction *against the
     * project's own interest*: an unlabelled open counts as unprompted, which inflates the metric
     * the project has promised to die on. A silent miscount that flatters the app is how a kill
     * criterion quietly stops being one.
     *
     * The extra is consumed after reading, so a rotation or a return from the camera does not
     * re-attribute a launcher open to the notification that started the task twenty minutes ago.
     */
    override fun onResume() {
        super.onResume()
        AppGraph.appOpens.record(consumeOpenSource())
    }

    private fun consumeOpenSource(): AppOpenSource {
        val raw = intent?.getStringExtra(EXTRA_OPEN_SOURCE)
        intent?.removeExtra(EXTRA_OPEN_SOURCE)
        return raw?.let { name -> runCatching { AppOpenSource.valueOf(name) }.getOrNull() }
            ?: AppOpenSource.LAUNCHER
    }

    companion object {
        /**
         * Set by anything that *pulled* the user in — the notification builder and the alarm
         * receiver. If you are launching this Activity and the user did not choose to open the app,
         * you must set this, or the kill criterion counts your nag as evidence the app is loved.
         */
        const val EXTRA_OPEN_SOURCE = "com.secondspine.app.OPEN_SOURCE"

        /** Convenience for the ladder: `MainActivity.intent(ctx, AppOpenSource.ALARM)`. */
        fun intent(context: android.content.Context, source: AppOpenSource): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_SOURCE, source.name)
    }
}
