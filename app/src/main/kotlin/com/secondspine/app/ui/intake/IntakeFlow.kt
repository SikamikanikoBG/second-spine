package com.secondspine.app.ui.intake

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondspine.app.ui.theme.Motion

/**
 * THE CASTING CALL — `ui/intake/IntakeFlow.kt`, SPEC §4.1's modal intake, and `ScreenSlots.intake`.
 *
 * ```
 * SecondSpineNav(
 *     intakeComplete = ...,
 *     screens = ScreenSlots(
 *         intake = { onDone -> IntakeFlow(onDone = onDone) },   // <- one line, in MainActivity
 *         home = { ... },
 *     ),
 * )
 * ```
 *
 * The signature is `(onDone: () -> Unit) -> Unit` because that is the slot `Nav.kt` reserved, and the
 * graph does the rest: `onDone` navigates to `home` with `popUpTo(INTAKE) { inclusive = true }`, so
 * the intake removes itself from the back stack and **there is no route back into a contract you have
 * already signed.**
 *
 * ── WHY THIS IS ONE COMPOSABLE AND NOT SEVEN ROUTES ────────────────────────
 *
 * Every step could have been a nav destination, and it would have been wrong. Seven routes means
 * seven back-stack entries, which means the system back gesture walks the user backwards through a
 * committed clinical screen — and "go back and answer SCOFF differently" is an in-app override of a
 * gate that must not have one (SPEC §4.8: *"No in-app override"*). It also means the wizard's state
 * has to survive seven navigations, which is how the initials end up in a `SavedStateHandle` and the
 * signature ends up firing twice on a rotation.
 *
 * So: one destination, one view model, one state machine, and the step transition is an
 * [AnimatedContent] that slides — because nothing in this app fades (SPEC §4.9), including the thing
 * that would most conventionally fade.
 */
@Composable
fun IntakeFlow(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: IntakeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            // Forward, always. The stack only goes one way and the motion says so before the user
            // has had a chance to look for a back button.
            slideInHorizontally(Motion.SlamOffset) { it } togetherWith
                slideOutHorizontally(Motion.SlamOffset) { -it / 3 }
        },
        modifier = modifier,
        label = "intake",
    ) { step ->
        when (step) {
            IntakeStep.COLD_OPEN -> ColdOpenScreen(
                gates = state.gates,
                onContinue = { vm.advance() },
            )

            IntakeStep.DESK -> DeskScreen(
                state = state,
                onToggle = vm::togglePick,
                onContinue = { vm.advance() },
            )

            IntakeStep.SCOFF -> ScoffScreen(
                state = state,
                onAnswer = vm::answerScoff,
                onContinue = { vm.advance() },
            )

            IntakeStep.PARQ -> ParqScreen(
                state = state,
                onAnswer = vm::answerParq,
                onContinue = { vm.advance() },
            )

            IntakeStep.TIMES -> TimesScreen(
                state = state,
                onWake = vm::setWake,
                onBed = vm::setBed,
                onContinue = { vm.advance() },
            )

            IntakeStep.CONTRACT -> ContractScreen(
                state = state,
                onInitials = vm::setInitials,
                onInitialClause = vm::initialClause,
                onSign = vm::sign,
                onContinue = { vm.advance() },
            )

            IntakeStep.HOLD_BACK -> HoldBackScreen(
                gates = state.gates,
                onDone = { vm.advance(onDone) },
            )
        }
    }
}
