package com.secondspine.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.Motion
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.SsSectionLabel
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Text

/**
 * THE NAVIGATION GRAPH.
 *
 * SPEC §4.1: a single `MainActivity` with Compose Navigation, plus one separate `LockActivity` in its
 * own task. **No five-tab bar.** A tab bar would be the most damaging twenty minutes of work anyone
 * could do to this product: it would put the Archive, the Tape and the demand on screen
 * simultaneously and permanently, and the one rule that keeps a coach from decaying into a to-do
 * list is that exactly one thing is ever being asked of you. Tabs are a promise that nothing is
 * urgent.
 *
 * So the hierarchy is a stack. `home` is the surface; everything else is pushed over it and comes
 * back off.
 */
object Routes {
    /** The casting call. Once, then quarterly re-consent. Modal — blocks everything. */
    const val INTAKE = "intake"

    /** The 7am surface. Rip + exactly ONE demand + the Ledger strip + the two free buttons. */
    const val HOME = "home"

    /** SHOT → STAMP. The signature moment. `<400 ms` to open, camera-only, zero assertion. */
    const val PROOF = "proof/{habitId}"

    /** Sunday, 20:00. The show. Its open-rate is a kill-criterion metric. */
    const val TAPE = "tape"

    /** The docket. 28 days and nothing older, ever. */
    const val LEDGER = "ledger"

    /** The proof timeline. **The product at low jurisdiction.** Ships populated from proof #1. */
    const val ARCHIVE = "archive"

    /** Where RETIRE RIP has lived since day one. */
    const val SETTINGS = "settings"

    /** RETIRE RIP. Export, then goodbye, no begging. */
    const val GOODBYE = "goodbye"

    /** Re-entry after >= 4 dark days. **Intercepts `home`.** One tap. No debt. */
    const val COMEBACK = "comeback"

    fun proof(habitId: String): String = "proof/$habitId"
}

/**
 * The screens, as slots.
 *
 * Every route in SPEC §4.1 belongs to a different author, and several of them do not exist in the
 * tree yet. Rather than have this file import them directly — which makes the graph uncompilable
 * until the last screen lands, and makes every agent's build depend on every other agent's — the
 * graph takes its content as parameters and defaults each one to [PendingSurface].
 *
 * The routes are therefore **real now**: `navigate(Routes.TAPE)` works today, the back stack is
 * correct today, and the transitions are correct today. Each screen's author replaces exactly one
 * lambda and touches nothing else. When the last one lands, the defaults are unreachable.
 *
 * Each slot names its owning file from SPEC §4.1's inventory.
 */
class ScreenSlots(
    /** `ui/intake/IntakeFlow.kt` → terminal step `ui/intake/ContractScreen.kt`. */
    val intake: @Composable (onDone: () -> Unit) -> Unit = { PendingSurface("INTAKE") },
    /** `ui/shot/ShotScreen.kt` → `ui/shot/StampScreen.kt` / `ui/shot/AuditScreen.kt`. */
    val proof: @Composable (habitId: String, onDone: () -> Unit) -> Unit = { _, _ -> PendingSurface("PROOF") },
    /** `ui/tape/TapeScreen.kt`. */
    val tape: @Composable (onBack: () -> Unit) -> Unit = { PendingSurface("THE TAPE") },
    /** The Ledger card, lifted out of the Tape so it is reachable on a Tuesday. `Ledger.kt` feeds it. */
    val ledger: @Composable (onBack: () -> Unit) -> Unit = { PendingSurface("THE LEDGER") },
    /** `ui/archive/ArchiveScreen.kt`. */
    val archive: @Composable (onBack: () -> Unit) -> Unit = { PendingSurface("ARCHIVE") },
    /** Settings, incl. STAND DOWN, MUTE THE MAN, export, and RETIRE RIP. */
    val settings: @Composable (onBack: () -> Unit, onRetire: () -> Unit) -> Unit = { _, _ -> PendingSurface("SETTINGS") },
    /** `ui/exit/GoodbyeScreen.kt`. */
    val goodbye: @Composable (onBack: () -> Unit) -> Unit = { PendingSurface("GOODBYE") },
    /** `ui/arena/ComebackScreen.kt`. One card, one button, one tiny action. */
    val comeback: @Composable (onOneSet: () -> Unit) -> Unit = { PendingSurface("COMEBACK") },
    /** `ui/home/HomeScreen.kt` — built here. Never a placeholder. */
    val home: @Composable (nav: NavHostController) -> Unit,
)

/**
 * THE GRAPH.
 *
 * @param intakeComplete the gate. Incomplete → `intake`, and it is modal: the intake pops itself off
 *   the stack when it finishes, so there is no back gesture into a half-signed contract.
 * @param comebackDue `daysSinceLastProof >= 4`, or exiting `STAND_DOWN(SICK|INJURED)`. When true the
 *   start destination is `comeback` and **not** `home` — SPEC §4.6's interception, done as a start
 *   destination rather than as a `LaunchedEffect` redirect, because a redirect composes `home` for
 *   one frame first and day 10 of the flu is the single worst moment in the product to flash a
 *   demand card at somebody.
 */
@Composable
fun SecondSpineNav(
    intakeComplete: Boolean,
    comebackDue: Boolean,
    screens: ScreenSlots,
    navController: NavHostController = rememberNavController(),
) {
    val start = when {
        !intakeComplete -> Routes.INTAKE
        comebackDue -> Routes.COMEBACK
        else -> Routes.HOME
    }

    NavHost(
        navController = navController,
        startDestination = start,
        // NOTHING FADES (SPEC §4.9). Navigation's own default is a 700 ms fade-through, which is the
        // exact motion this app is defined in opposition to, so all four transitions are named. The
        // stack slides: forward pushes left, back pushes right, on the house spring.
        enterTransition = { slideInHorizontally(Motion.SlamOffset) { it } },
        exitTransition = { slideOutHorizontally(Motion.SlamOffset) { -it / 3 } },
        popEnterTransition = { slideInHorizontally(Motion.SlamOffset) { -it / 3 } },
        popExitTransition = { slideOutHorizontally(Motion.SlamOffset) { it } },
    ) {
        composable(Routes.INTAKE) {
            screens.intake {
                navController.navigate(Routes.HOME) {
                    // The casting call does not stay on the stack. There is no route back into a
                    // contract you have already signed.
                    popUpTo(Routes.INTAKE) { inclusive = true }
                }
            }
        }

        composable(Routes.HOME) { screens.home(navController) }

        composable(
            route = Routes.PROOF,
            arguments = listOf(navArgument("habitId") { type = NavType.StringType }),
        ) { entry ->
            val habitId = entry.arguments?.getString("habitId").orEmpty()
            screens.proof(habitId) { navController.popBackStack() }
        }

        composable(Routes.TAPE) { screens.tape { navController.popBackStack() } }
        composable(Routes.LEDGER) { screens.ledger { navController.popBackStack() } }
        composable(Routes.ARCHIVE) { screens.archive { navController.popBackStack() } }

        composable(Routes.SETTINGS) {
            screens.settings(
                { navController.popBackStack() },
                { navController.navigate(Routes.GOODBYE) },
            )
        }

        composable(Routes.GOODBYE) { screens.goodbye { navController.popBackStack() } }

        composable(Routes.COMEBACK) {
            screens.comeback {
                navController.navigate(Routes.HOME) {
                    // One tap and it is gone. It never comes back on the back stack, because the
                    // entire promise of the screen is that there is no reckoning waiting behind it.
                    popUpTo(Routes.COMEBACK) { inclusive = true }
                }
            }
        }
    }
}

/**
 * A route that exists before its screen does.
 *
 * Flat UI type, no character, no gold. If a surface is not installed, Rip does not get to comment on
 * it: he is a 40 MB tape ghost with opinions about your kitchen, not about the build. Putting him in
 * an error state is how a character becomes a mascot.
 */
@Composable
private fun PendingSurface(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SsSectionLabel(name)
            Text(
                text = "This surface is not installed in this build.",
                style = BodyStyle,
                color = PaperFaint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
