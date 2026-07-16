package com.secondspine.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.secondspine.app.ui.theme.Motion
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

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
 * **Every slot is required. There are no defaults, and that is the point of this class now.**
 *
 * It used to default each slot to a `PendingSurface` placeholder, so that the graph would compile
 * before the last screen landed. The cost of that convenience was discovered the expensive way: every
 * screen in the app was written, every screen compiled, `:app:assembleDebug` was green, seven agents
 * each verified their own build — and the shipped APK opened to "INTAKE — This surface is not
 * installed in this build", because `MainActivity` never passed any of them in. The defaults made
 * *unwired* and *done* indistinguishable from the outside, and a green CI proved nothing about
 * whether the product was reachable.
 *
 * So: no defaults. A screen that is not passed in is a **compile error** in `MainActivity`, which is
 * the only place that can be wrong about this. The next person to add a route cannot ship a grey
 * placeholder by forgetting something; they have to look at it.
 *
 * Each slot names its owning file from SPEC §4.1's inventory.
 */
class ScreenSlots(
    /** `ui/intake/IntakeFlow.kt` → terminal step `ui/intake/ContractScreen.kt`. */
    val intake: @Composable (onDone: () -> Unit) -> Unit,
    /** `ui/proof/ProofScreen.kt` — SHOT → STAMP. */
    val proof: @Composable (habitId: String, onDone: () -> Unit) -> Unit,
    /** `ui/tape/TapeScreen.kt`. */
    val tape: @Composable (onBack: () -> Unit) -> Unit,
    /** The Ledger, reachable on a Tuesday rather than only on Sunday's Tape. */
    val ledger: @Composable (onBack: () -> Unit) -> Unit,
    /** `ui/archive/ArchiveScreen.kt`. */
    val archive: @Composable (onBack: () -> Unit) -> Unit,
    /** Settings, incl. STAND DOWN, MUTE THE MAN, export, and RETIRE RIP. */
    val settings: @Composable (onBack: () -> Unit, onRetire: () -> Unit) -> Unit,
    /** `ui/settings/GoodbyeScreen.kt`. `onRetired` fires *after* the retirement is written. */
    val goodbye: @Composable (onBack: () -> Unit, onRetired: () -> Unit) -> Unit,
    /** `ui/comeback/ComebackScreen.kt`. One card, one button, one tiny action. */
    val comeback: @Composable (onOneSet: () -> Unit) -> Unit,
    /** `ui/home/HomeScreen.kt`. Never a placeholder. */
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

        composable(Routes.GOODBYE) {
            screens.goodbye(
                { navController.popBackStack() },
                {
                    // He is retired. The odometer is 0, which home already knows how to render — a
                    // 40px face, no voice, and the Archive. There is no farewell route and no "sorry
                    // to see you go": the settings stack is cleared and he lands on the ending.
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }

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
