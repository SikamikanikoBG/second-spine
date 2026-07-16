package com.secondspine.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondspine.app.data.Graph
import com.secondspine.app.ui.Routes
import com.secondspine.app.ui.ScreenSlots
import com.secondspine.app.ui.SecondSpineNav
import com.secondspine.app.ui.ShellViewModel
import com.secondspine.app.ui.archive.ArchiveScreen
import com.secondspine.app.ui.archive.ArchiveViewModel
import com.secondspine.app.ui.comeback.ComebackScreen
import com.secondspine.app.ui.comeback.ComebackViewModel
import com.secondspine.app.ui.home.HomeScreen
import com.secondspine.app.ui.home.HomeViewModel
import com.secondspine.app.ui.intake.IntakeFlow
import com.secondspine.app.ui.proof.ProofScreen
import com.secondspine.app.ui.settings.GoodbyeScreen
import com.secondspine.app.ui.settings.SettingsScreen
import com.secondspine.app.ui.settings.SettingsViewModel
import com.secondspine.app.ui.tape.TapeScreen
import com.secondspine.app.ui.tape.TapeViewModel
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

        // THE DATA LAYER, INSTALLED. `SecondSpineApp.onCreate` calls `AppGraph.install` — the shell's
        // in-memory seam — and never `Graph.install`, so the real Room database and DataStore were
        // never constructed. `Graph.db` throws rather than returning null, which is why three separate
        // ViewModels had grown their own defensive `Graph.install(app)` call with a comment saying the
        // line belongs in a file they do not own. This is that file. The call is idempotent.
        Graph.install(this)
        AppGraph.loadIntakeState()

        setContent {
            SecondSpineTheme {
                val intakeComplete by AppGraph.intakeComplete.collectAsStateWithLifecycle()
                val comebackDue by AppGraph.comebackDue.collectAsStateWithLifecycle()

                // THE NOTIFICATION PERMISSION, asked once the contract is signed. On Android 13+ the
                // R0 rung — the whole quiet end of the ladder — is a `notify()` call that silently
                // does nothing until this is granted, and nothing anywhere requested it (the only
                // runtime request in the app was CAMERA). So the coach's first and gentlest way to
                // reach the user was dead on every modern phone. Asked after intake, not during, so it
                // does not interrupt the pitch.
                RequestNotificationsWhen(intakeComplete)

                // Activity-scoped: FOR THE RECORD and BREAK GLASS are the two controls that must
                // outlive the back stack, and home is not the only surface that offers them.
                val shell: ShellViewModel = viewModel()

                SecondSpineNav(
                    intakeComplete = intakeComplete,
                    comebackDue = comebackDue,
                    screens = ScreenSlots(
                        intake = { onDone -> IntakeFlow(onDone = onDone) },

                        proof = { habitId, onDone ->
                            // `ProofScreen` owns its own `ProofViewModel` and calls `start(habitId)`
                            // itself, including the FOR THE RECORD button on the camera.
                            ProofScreen(habitId = habitId, onDone = onDone)
                        },

                        tape = { onBack ->
                            val vm: TapeViewModel = viewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            TapeScreen(state = state, onBack = onBack)
                        },

                        // THE LEDGER has no screen of its own — SPEC §4.1 lists the route but the
                        // docket only ever got built as the Tape's card and home's three-glyph strip.
                        // Routed to the Archive, which is a real surface with real rows in it, rather
                        // than left as a placeholder that ships. Flagged in the report.
                        ledger = { onBack -> ArchiveSurface(onBack) },

                        archive = { onBack -> ArchiveSurface(onBack) },

                        settings = { onBack, onRetire ->
                            val vm: SettingsViewModel = viewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                state = state,
                                onBack = onBack,
                                onRetire = onRetire,
                                onSetMuted = vm::setMuted,
                                onSetPausedMode = vm::setPausedMode,
                                onExportNow = vm::exportNow,
                            )
                        },

                        goodbye = { onBack, onRetired ->
                            val vm: SettingsViewModel = viewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            GoodbyeScreen(
                                state = state,
                                onBack = onBack,
                                onExportNow = vm::exportNow,
                                onConfirmRetire = { vm.retire(onRetired) },
                            )
                        },

                        comeback = { onOneSet ->
                            val vm: ComebackViewModel = viewModel()
                            val state by vm.state.collectAsStateWithLifecycle()
                            ComebackScreen(
                                onOneSet = onOneSet,
                                // The comeback carries BREAK GLASS on its own card, in its own state
                                // object. Day 10 of the flu is not a day to make him go looking.
                                state = state.copy(onBreakGlass = shell::breakGlass),
                            )
                        },

                        home = { nav ->
                            val vm: HomeViewModel = viewModel()
                            val state by vm.state.collectAsStateWithLifecycle()

                            // RECOMPUTE THE DEMAND ON RESUME.
                            //
                            // `LocalLifecycleOwner` inside a `NavHost` destination is the
                            // `NavBackStackEntry`, not the Activity — which is the scope that is
                            // actually wanted here. It fires on the cold start, on every return from
                            // the background, AND on the pop back from the camera, so the demand a
                            // user has just answered is gone by the time he sees home again.
                            //
                            // A day rolls over at midnight and windows open on the hour, so the
                            // demand must also be re-armed on a clock the back stack knows nothing
                            // about; the ViewModel's own minute tick covers that. This is the edge
                            // that the tick cannot see: the app was not running.
                            val owner = LocalLifecycleOwner.current
                            DisposableEffect(owner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
                                }
                                owner.lifecycle.addObserver(observer)
                                onDispose { owner.lifecycle.removeObserver(observer) }
                            }

                            HomeScreen(
                                state = state,
                                onProof = { habitId -> nav.navigate(Routes.proof(habitId)) },
                                onForTheRecord = { shell.forTheRecord(state.demand?.habitId) },
                                onBreakGlass = shell::breakGlass,
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

    /**
     * The Archive, as both `archive` and `ledger` reach it.
     *
     * Each route composes its own `ArchiveViewModel` because `viewModel()` here resolves against the
     * calling destination's `NavBackStackEntry`, which is the correct scope: the grid's scroll
     * position belongs to the entry the user pushed, not to the Activity.
     */
    @Composable
    private fun ArchiveSurface(onBack: () -> Unit) {
        val vm: ArchiveViewModel = viewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        ArchiveScreen(state = state, onBack = onBack)
    }

    /**
     * Ask for POST_NOTIFICATIONS exactly once, and only after [enabled] (intake complete).
     *
     * Below API 33 the permission does not exist and notifications work without it, so this is a
     * no-op there. The result is ignored on purpose: a refusal is an answer, the app degrades
     * honestly (WorkNotifications swallows the SecurityException), and re-prompting on every launch is
     * the behaviour that gets an app muted. `LaunchedEffect(enabled)` fires the request a single time
     * per process once the gate opens.
     */
    @Composable
    private fun RequestNotificationsWhen(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* granted or not, the ladder degrades honestly either way */ }
        LaunchedEffect(enabled) {
            if (!enabled) return@LaunchedEffect
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
