package com.secondspine.app.ui.comeback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.AppGraph
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Register
import com.secondspine.coach.RipLine
import com.secondspine.coach.Target
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * THE COMEBACK'S COPY — authored here, on purpose, and not drawn from the fragment bank.
 *
 * Every other line Rip speaks is assembled by `Voice.kt` from the bank, retired per slot, and
 * rationed against a speech budget. This one is not, and the exception is deliberate:
 *
 *  1. **It must be identical every time.** The bank exists to prevent decay through repetition, but
 *     this screen is seen perhaps six times in ten months, always at the same moment, always by
 *     someone in the same state. There is no repetition to decay. What there *is* is a promise, and a
 *     promise that is phrased differently each time is not a promise.
 *  2. **It must not be able to draw a bad card.** The bank is a probabilistic system with registers,
 *     swerves and escalations in it. On day 10 of the flu there is exactly one acceptable register
 *     and no acceptable swerve. A screen this load-bearing does not get to roll dice.
 *  3. **It is SPEC §4.6's copy, verbatim.** The spec wrote this card out in full because the words
 *     *are* the design here in a way they are not anywhere else.
 */

/**
 * The line, as PITCHMAN.
 *
 * Target is [Target.the_situation] — the flu. Not `the_habit` (there is no habit to aim at; he did
 * not miss, he was ill), not `the_excuse` (this is not an excuse and calling it one would be the
 * single most damaging word in the product), and obviously not the man. The `Target` enum is frozen
 * and holds no `body`, `weight`, `appearance` or `worth`, so the worst version of this line is not
 * expressible in the type system. This is what that enum was for.
 */
internal val COMEBACK_LINE = RipLine(
    register = Register.PITCHMAN,
    target = Target.the_situation,
    text = "You were sick. Fine. FINE! The Ledger does not count sick, brother, I am not a MONSTER, " +
        "I am a PROFESSIONAL. Floor is one set. That is today. That is the whole ask.",
)

/**
 * The SCOFF-safe variant.
 *
 * [COMEBACK_LINE] is PITCHMAN, which is not a mocking register, so it survives the clinical gate as
 * written — but "FINE! ... I am not a MONSTER" is delivered at volume, and volume aimed at a man who
 * screened positive on an eating-disorder instrument is a different thing than volume aimed at anyone
 * else. RESOLUTIONS §B: **the clinical gate outranks the assertion.** So the gate does not merely
 * filter registers here; it selects a quieter reading of the same promise.
 *
 * The content is identical. Nothing is withheld and nothing is softened about the *offer* — the
 * Ledger still does not count sick, the floor is still one set. Only the shouting is gone.
 */
internal val COMEBACK_LINE_QUIET = RipLine(
    register = Register.PITCHMAN,
    target = Target.the_situation,
    text = "You were sick. The Ledger does not count sick, brother. It never has. " +
        "Floor is one set. That is today. That is the whole ask.",
)

/**
 * The coda, as GHOST, and note what is not in it: the word "brother".
 *
 * That is not a stylistic choice, it is a rule the composer enforces — "brother" is unemittable in
 * DISAPPOINTED and GHOST (`Tape.kt`'s `admissible`), because the word is the pitch, the pitch is the
 * armour, and these two registers are what is under it. He drops the armour for one sentence.
 *
 * This is the same beat the Tape's COACH DOWN card carries, and it says the same thing: he did some
 * thinking while you were out, and he is not going to tell you what it was. The nine days are
 * acknowledged exactly once, obliquely, about *him*, and then closed. That is the entire mechanic.
 * "Don't worry about it" is only credible because nothing else on the screen contradicts it.
 */
internal val COMEBACK_CODA = RipLine(
    register = Register.GHOST,
    target = Target.himself,
    text = "…I did some thinking while you were out. Don't worry about it.",
)

/**
 * THE COMEBACK STATE.
 *
 * Note the fields that do not exist, because on this screen they are the specification:
 * no `daysMissed`, no `debt`, no `missedHabits`, no `lastProofAt`, no `compliance`. The screen
 * cannot render a reckoning because the state object has nowhere to keep one. That is the same trick
 * as the missing food column and the frozen `Target` enum: make the wrong thing unrepresentable
 * rather than discouraged, because a field that exists is a field somebody renders at 1am.
 *
 * The *trigger* knows the man was gone four days or more — `AppGraph.comebackDue` computed that. The
 * screen never learns the number.
 */
data class ComebackState(
    val jurisdiction: Int = 2,
    val gates: ClinicalGates = ClinicalGates(),
    val onBreakGlass: () -> Unit = {},
) {
    /** Quiet under a positive SCOFF screen; the promise is identical either way. */
    val line: RipLine get() = if (gates.mockingAllowed) COMEBACK_LINE else COMEBACK_LINE_QUIET

    val coda: RipLine get() = COMEBACK_CODA

    init {
        // The gates are asserted on the composed object rather than trusted at the call site, in the
        // same spirit as `Tape.kt`'s `verified()`. A future edit that points `line` at the bank must
        // fail here, loudly, in development — not in front of the one user this gate protects.
        check(line.register !in MOCKING) { "SCOFF-positive: a mocking register reached the Comeback" }
        check(!coda.text.contains("brother", ignoreCase = true)) {
            "'brother' is unemittable in GHOST"
        }
    }

    private companion object {
        val MOCKING = setOf(Register.ARENA, Register.BIT)
    }
}

/**
 * The comeback view model.
 *
 * It reads the odometer (for the size of his face) and the clinical gates (for which reading of the
 * line he gets), and it reads nothing else. There is deliberately no repository call here: this
 * screen has no query to run, because there is no history for it to summarise. That absence is the
 * feature, and keeping it in the ViewModel rather than only in the UI is what stops the summary from
 * being added back "just for context" in six weeks.
 */
class ComebackViewModel : ViewModel() {

    val state: StateFlow<ComebackState> = combine(
        AppGraph.jurisdiction,
        AppGraph.gates,
    ) { j: Int, gates: ClinicalGates ->
        ComebackState(jurisdiction = j, gates = gates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComebackState())
}
