package com.secondspine.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.AppGraph
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Habit
import com.secondspine.coach.LedgerRow
import com.secondspine.coach.Register
import com.secondspine.coach.Stage
import com.secondspine.coach.jurisdiction
import com.secondspine.coach.jurisdictionShare
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * THE ONE DEMAND.
 *
 * A `String` and a habit id, and nothing else — no deadline, no countdown, no priority number, no
 * "2 of 3". SPEC §4.3 permits the screen to answer exactly one question at 7am and it is not "how am
 * I doing". Every field this class does not have is a field that would answer a second one.
 *
 * `DemandResolver` in `:coach` picks which single obligation this is. If two are open, the second is
 * invisible until the first resolves. That is not a rendering decision that can be revisited in a
 * later sprint — it is why the coach is not a to-do list.
 */
data class Demand(val habitId: String, val text: String)

/**
 * One frame of the proof timeline. The Archive is the product at low jurisdiction, and it ships
 * populated from proof #1 — never gated behind day 200. You ship the pull mechanic long before the
 * horizon you need it at, or you arrive at the horizon with nothing to pull.
 */
data class ProofThumb(val id: String, val dayLabel: String)

/** A rung of the trust ladder — the odometer made legible, and the only scoreboard that matters. */
data class LadderRow(val habitId: String, val stage: Stage)

/**
 * THE 7AM STATE.
 *
 * Note what is absent, because the absences are the specification:
 *  - **no streak.** Not as a primary metric, not as a secondary one, not at all. A streak is a number
 *    whose only move is to zero, and its destruction is the most reliable uninstall event in this
 *    entire product category.
 *  - **no score, no grade, no percentage.** Compliance is real and lives in `Pipeline.kt`, where it
 *    graduates habits. It is not a thing the user is shown at breakfast and asked to feel about.
 *  - **no weight, no food, no calories.** There is no field here to put them in, and there is no
 *    column in the schema to fill one from. THE DONUT IS ALLOWED.
 *  - **no confidence score, no model name.** Uncertainty reaches the user as suspicion in Rip's own
 *    voice — *"I cannot SEE it. Closer."* — and never as a number. A percentage invites an argument
 *    with the model; a suspicion invites a better photograph.
 */
data class HomeState(
    /** 0..4. The only input to the split. */
    val jurisdiction: Int = 2,
    val gates: ClinicalGates = ClinicalGates(),
    /** The single highest-priority open obligation. Null → the Floor, never an empty card. */
    val demand: Demand? = null,
    /** What he is saying right now, if anything. Null is a first-class outcome, not a failure. */
    val ripLine: String? = null,
    val ripRegister: Register = Register.PITCHMAN,
    /** Three glyphs, from `ledgerRows()`. 28 days and nothing older. */
    val ledger: List<LedgerRow> = emptyList(),
    /** Below the fold. */
    val ladder: List<LadderRow> = emptyList(),
    /** The archive strip. At low jurisdiction this *is* the screen. */
    val recentProofs: List<ProofThumb> = emptyList(),
    /** Null means the export has never run, which the footer renders as loudly broken. */
    val daysSinceExport: Int? = null,
) {
    /**
     * SPEC §4.2 / RESOLUTIONS: **`0.15 + 0.11 × j`**, straight out of `Voice.kt`, and it is the only
     * geometry decision on this screen.
     *
     * j=4 → 0.59: he owns the screen. j=0 → 0.15: a 40px face and an archive that is entirely yours.
     * It never reaches zero — he never fully leaves, he just stops being the point. The arc is a pure
     * function of one integer, so it is falsifiable in CI rather than at month 8, and it costs zero
     * authored content: the same assets, re-priced by the user's own success.
     */
    val ripFraction: Float get() = jurisdictionShare(jurisdiction).toFloat()

    /**
     * At `j <= 2` the demand is no longer the centre of gravity and the Archive is. Same route, same
     * screen, same components — the *weight* moves. This is deliberately not a second start
     * destination: a user who has earned his way down to jurisdiction 1 should not be told he has
     * been moved to a different app, he should notice that the man got smaller.
     */
    val archiveLed: Boolean get() = jurisdiction <= 2
}

/**
 * The home view model.
 *
 * It owns no clock and no calendar. SPEC §4.2 is explicit: no `LocalDate.now()`, no
 * `daysSinceInstall`, no month index anywhere in `ui/` — the arc is driven by the odometer or it is
 * not falsifiable. Everything time-shaped on this screen arrives pre-formatted from a writer that
 * has a timezone (see `LedgerEntry.note`'s doc in `:coach`).
 */
class HomeViewModel : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        AppGraph.habits,
        AppGraph.gates,
    ) { habits: List<Habit>, gates: ClinicalGates ->
        HomeState(
            jurisdiction = jurisdiction(habits),
            gates = gates,
            ladder = habits.map { LadderRow(it.id, it.stage) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
