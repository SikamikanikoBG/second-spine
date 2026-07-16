package com.secondspine.app.ui.intake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.AppGraph
import com.secondspine.app.data.Graph
import com.secondspine.app.data.HabitRow
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.MAX_ENFORCED
import com.secondspine.coach.Pillar
import com.secondspine.coach.winddownAtMinOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * THE CASTING CALL.
 *
 * Seven steps, in this order, and the order is an argument:
 *
 *  1. [COLD_OPEN] — he pitches. Before any commitment, before any question, the user has met the
 *     product's only real feature. The demo is the onboarding.
 *  2. [DESK] — what he gets jurisdiction over. Two habits, capped by `MAX_ENFORCED`.
 *  3. [SCOFF] — the clinical gate. Rip vanishes.
 *  4. [PARQ] — the other clinical gate. Rip is still gone.
 *  5. [TIMES] — the two numbers the silence window is keyed to.
 *  6. [CONTRACT] — he signs, and then the character breaks, once.
 *  7. [HOLD_BACK] — the grain snaps back and he tells you about the fourteen days.
 *
 * The screening sits between the pitch and the contract on purpose. Screening first would open a
 * comedy app with a questionnaire about vomiting, and the user would never see step 1. Screening
 * *after* the contract would mean the user signed a document authorising a mocking coach before the
 * app knew whether it was allowed to have one. Between the two, the pitch has landed, nothing has
 * been promised yet, and the contract that follows is signed by an app that already knows what it
 * is permitted to be.
 *
 * **There is no back button.** Not an omission — every screen here lets you change your answer as
 * many times as you like *before* you continue, which is the real job a back button does. What a back
 * button would additionally buy is the ability to walk back into a committed clinical screen and
 * answer it differently, and "go back and answer it differently" is precisely what an in-app override
 * of a permanent gate looks like. The system back gesture is untouched: leaving is always allowed,
 * from everywhere, and nothing in this package traps anybody.
 */
enum class IntakeStep { COLD_OPEN, DESK, SCOFF, PARQ, TIMES, CONTRACT, HOLD_BACK }

/** A row of the desk, as the wizard shows it. */
data class HabitChoice(
    val id: String,
    val title: String,
    val pillar: Pillar,
    /**
     * `Pillar.SMOKING`. He mocks the donut; he NEVER mocks the cigarette.
     *
     * Sourced from the brain's own guardrail rather than from an id match here, so this cannot drift
     * from the thing that actually enforces it.
     */
    val penaltyFree: Boolean,
)

/**
 * THE WIZARD'S STATE.
 *
 * Absences, again, are the design. There is no weight here and no goal and no target and no
 * "experience level" and no birthday and no gender and no height. SPEC §4.8: *"Not in the wizard:
 * weight goals, ever. Targets, ever. Targets are derived from 14 observed days. Wizard numbers are
 * joke fuel, not configuration — lying in the wizard is the softest attack surface in the app, so
 * remove the payoff."*
 *
 * The only numbers this class holds are [wakeAtMinutes] and [bedAtMinutes], and they are not
 * configuration either — they are a *muzzle*. They decide the hours in which the coach is forbidden
 * to make noise. Lying about them cannot buy the user an easier week; it can only hand Rip more of
 * the evening. That is the shape every question in this wizard has to have.
 */
data class IntakeState(
    val step: IntakeStep = IntakeStep.COLD_OPEN,
    val habits: List<HabitChoice> = emptyList(),
    val picked: Set<String> = emptySet(),
    val scoff: Map<String, Boolean> = emptyMap(),
    val parq: Map<String, Boolean> = emptyMap(),
    /** Live from `SettingsStore`. Gates the register of every line Rip speaks from SCOFF onward. */
    val gates: ClinicalGates = ClinicalGates(),
    /** Minutes since local midnight. `SettingsStore`'s default. */
    val wakeAtMinutes: Int = 7 * 60,
    /** Target bed. Wind-down is derived from it and never set independently. */
    val bedAtMinutes: Int = 23 * 60,
    val initials: String = "",
    val initialled: Set<String> = emptySet(),
    /** True once the signature has landed and the character has broken. Terminal phase of CONTRACT. */
    val broke: Boolean = false,
) {
    /**
     * `bed - 45`, from the brain. RESOLUTIONS §D's fix, made of the user's own number.
     *
     * Wind-down is deliberately not a third picker. `winddownAtMinOfDay` is the definition in
     * `:coach` and a second, independently-settable field here would be a second definition — which
     * is how the two drift apart and how an alarm ends up firing inside the wind-down window on the
     * pillar ranked #1.
     */
    val winddownAtMinutes: Int get() = winddownAtMinOfDay(bedAtMinutes)

    val scoffAnswered: Boolean get() = SCOFF_ITEMS.all { scoff.containsKey(it.id) }
    val parqAnswered: Boolean get() = PARQ_ITEMS.all { parq.containsKey(it.id) }

    val scoffIsPositive: Boolean get() = scoffPositive(scoff)
    val parqIsPositive: Boolean get() = parqPositive(parq)

    /** Exactly [MAX_ENFORCED]. Not "at least one" — see [IntakeViewModel.togglePick]. */
    val deskFull: Boolean get() = picked.size == MAX_ENFORCED

    val contractSignable: Boolean
        get() = initials.isNotBlank() && CONTRACT_CLAUSES.all { it.id in initialled }
}

/**
 * The wizard's one view model.
 *
 * It writes as it goes rather than batching everything into the signature, and the direction of that
 * choice matters: if the process is killed at step 5, a SCOFF-positive result that has already been
 * persisted stays persisted. The wizard restarts, but the gate protecting the user does not. The
 * failure mode of write-as-you-go is "he answers five questions again"; the failure mode of
 * write-at-the-end is "the app forgot it was not allowed to mock him". Those are not comparable.
 */
class IntakeViewModel(app: Application) : AndroidViewModel(app) {

    init {
        // Idempotent (`Graph.install` returns early if the database exists) and defensive: the intake
        // is the first destination on a fresh install and must not depend on somebody else's
        // Application.onCreate having reached the data layer first.
        Graph.install(app)
        wireGatesOnce()
        observeHabits()
        observeGates()
    }

    private val settings get() = Graph.settings
    private val habitDao get() = Graph.db.habitDao()

    private val _state = MutableStateFlow(IntakeState())
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    // ── The desk ────────────────────────────────────────────────────────────

    /**
     * The habit rows, seeded by `SecondSpineDatabase.build`'s onCreate callback.
     *
     * On a genuinely fresh install this flow emits an empty list first and the seeded rows a moment
     * later, because the seed hops off Room's onCreate transaction. The desk screen therefore has to
     * be able to render zero rows for one frame without saying anything about it — which it does, by
     * showing Rip's line and no tiles. He is talking either way.
     */
    private fun observeHabits() = viewModelScope.launch {
        habitDao.observeAll().collect { rows ->
            _state.value = _state.value.copy(
                habits = rows.map { it.toChoice() },
                // Pre-select what the seed already switched on — exercise and reading. The user is
                // adjusting a desk that already exists rather than filling in a blank form, which is
                // one fewer decision and a much better default than nothing.
                picked = _state.value.picked.ifEmpty {
                    rows.filter { it.enabled }.map { it.id }.take(MAX_ENFORCED).toSet()
                },
            )
        }
    }

    private fun HabitRow.toChoice() = HabitChoice(
        id = id,
        title = title,
        pillar = pillar,
        // The brain's own list, not a string match on "smoking_cue".
        penaltyFree = !com.secondspine.coach.penaltyEligible(pillar),
    )

    private fun observeGates() = viewModelScope.launch {
        Graph.repository.clinicalGates.collect { gates ->
            _state.value = _state.value.copy(gates = gates)
        }
    }

    /**
     * THE CAP, enforced where the tap happens.
     *
     * `MAX_ENFORCED = 2`, and the seed's own assertion (`assertSeedRespectsCaps`) refuses more than
     * two enabled ENFORCED rows: *"If everything is penalised, nothing is."* If the wizard let a user
     * enable four, `jurisdiction()` would return 4 with every habit under full enforcement, the
     * odometer that drives the register mix, the speech budget and the entire arc would be wrong from
     * hour one, and the first symptom would arrive in month eight.
     *
     * So the third tap **replaces the oldest pick** rather than being rejected. A disabled tile that
     * silently does nothing is a bug the user has to solve; a tile that takes the slot and gives one
     * back is a rule he learns in one gesture, without a dialog.
     */
    fun togglePick(id: String) {
        val current = _state.value.picked
        val next = when {
            id in current -> current - id
            current.size < MAX_ENFORCED -> current + id
            else -> current.drop(1).toSet() + id
        }
        _state.value = _state.value.copy(picked = next)
    }

    /**
     * Write the desk. Every row, every time — `enabled = id in picked`.
     *
     * Writing the whole set rather than a diff is what makes this idempotent and what makes it
     * impossible to leave a third row enabled from the seed after the user has picked two others.
     * `habit.stage` is untouched: stage moves are the pipeline's alone and this is not the pipeline.
     */
    private suspend fun commitDesk(picked: Set<String>) {
        check(picked.size <= MAX_ENFORCED) {
            "The wizard tried to enable ${picked.size} habits; MAX_ENFORCED is $MAX_ENFORCED."
        }
        habitDao.all().forEach { row -> habitDao.setEnabled(row.id, row.id in picked) }
    }

    // ── The clinical screens ────────────────────────────────────────────────

    fun answerScoff(id: String, yes: Boolean) {
        _state.value = _state.value.copy(scoff = _state.value.scoff + (id to yes))
    }

    fun answerParq(id: String, yes: Boolean) {
        _state.value = _state.value.copy(parq = _state.value.parq + (id to yes))
    }

    /**
     * ONE-WAY. A positive screen is permanent; a negative one never clears a positive one.
     *
     * SPEC §4.8: *"Positive → all body metrics, penalty-as-exercise, and the mocking register are
     * permanently unavailable. Not warned about. Unavailable. **No in-app override.**"* A gate that a
     * user can clear by re-answering is an in-app override with extra steps, and the one this
     * protects is the user least well served by being able to argue his way back to the mockery.
     *
     * The legitimate path to a cleared gate is the quarterly re-screen (`ScreeningWorker`, not in
     * v1), which is a different instrument run at a different time by a person in a different state.
     * It is not this wizard, four seconds later, on the same afternoon.
     */
    private suspend fun commitScoff(positive: Boolean) {
        if (positive) settings.setScoffPositive(true)
        else if (!settings.scoffPositive.first()) settings.setScoffPositive(false)
    }

    /** Same one-way rule. `prescribe()` puts PAR-Q+ above everything: a clinician, not a ghost. */
    private suspend fun commitParq(positive: Boolean) {
        if (positive) settings.setParqPositive(true)
        else if (!settings.parqPositive.first()) settings.setParqPositive(false)
    }

    // ── The times ───────────────────────────────────────────────────────────

    fun setWake(minutes: Int) {
        _state.value = _state.value.copy(wakeAtMinutes = wrap(minutes))
    }

    fun setBed(minutes: Int) {
        _state.value = _state.value.copy(bedAtMinutes = wrap(minutes))
    }

    private fun wrap(m: Int) = ((m % 1440) + 1440) % 1440

    /** Wake and wind-down. There is no bed-time key: wind-down is `bed - 45`, so bed is recoverable. */
    private suspend fun commitTimes(state: IntakeState) {
        settings.setWakeAtMinutes(state.wakeAtMinutes)
        settings.setWinddownAtMinutes(state.winddownAtMinutes)
    }

    // ── The contract ────────────────────────────────────────────────────────

    /** The keyboard's one appearance in the whole wizard. Two characters is plenty. */
    fun setInitials(raw: String) {
        val cleaned = raw.filter { it.isLetter() }.take(3).uppercase()
        _state.value = _state.value.copy(initials = cleaned)
    }

    fun initialClause(id: String) {
        if (_state.value.initials.isBlank()) return
        _state.value = _state.value.copy(initialled = _state.value.initialled + id)
    }

    /**
     * THE SIGNATURE.
     *
     * Four writes, and each one is a different promise:
     *  - `signContract` is the timestamp the pipeline's entire authority traces back to.
     *  - `setSafetyExplained` records that the break-glass explanation has been delivered — once,
     *    which is the only number of times it may be delivered.
     *  - `markInstalled` starts the 72-hour grace and the 14-day lock hold-back. It is written *here*
     *    rather than at first launch deliberately: the very next screen promises fourteen days out
     *    loud, and a clock that started while the APK was installing would quietly make that promise
     *    shorter than the sentence the user just read. `markInstalled` writes once and never updates,
     *    so a re-run of the intake cannot buy a second fortnight.
     *  - The desk, the screening and the times are already on disk from earlier steps.
     */
    private suspend fun commitContract(now: Long) {
        settings.signContract(now)
        settings.setSafetyExplained(true)
        settings.markInstalled(now)
    }

    fun sign() {
        val s = _state.value
        if (!s.contractSignable) return
        viewModelScope.launch {
            commitContract(System.currentTimeMillis())
            _state.value = _state.value.copy(broke = true)
        }
    }

    // ── The step machine ────────────────────────────────────────────────────

    /**
     * Advance. Every commit that belongs to the step being left happens here, before the step moves.
     *
     * Returns silently if the step is not complete. There are no error toasts in this wizard: the
     * continue affordance is simply not on screen until the step is answered, so an incomplete
     * advance is unreachable rather than rejected.
     */
    fun advance(onDone: () -> Unit = {}) {
        val s = _state.value
        viewModelScope.launch {
            when (s.step) {
                IntakeStep.COLD_OPEN -> go(IntakeStep.DESK)

                IntakeStep.DESK -> {
                    if (!s.deskFull) return@launch
                    commitDesk(s.picked)
                    go(IntakeStep.SCOFF)
                }

                IntakeStep.SCOFF -> {
                    if (!s.scoffAnswered) return@launch
                    commitScoff(s.scoffIsPositive)
                    go(IntakeStep.PARQ)
                }

                IntakeStep.PARQ -> {
                    if (!s.parqAnswered) return@launch
                    commitParq(s.parqIsPositive)
                    go(IntakeStep.TIMES)
                }

                IntakeStep.TIMES -> {
                    commitTimes(s)
                    go(IntakeStep.CONTRACT)
                }

                IntakeStep.CONTRACT -> {
                    if (!s.broke) return@launch
                    go(IntakeStep.HOLD_BACK)
                }

                IntakeStep.HOLD_BACK -> {
                    finish()
                    onDone()
                }
            }
        }
    }

    private fun go(step: IntakeStep) {
        _state.value = _state.value.copy(step = step)
    }

    /**
     * The end. And there is no "You're all set!" screen — SPEC §4.8 forbids it, and it is right to:
     * a congratulation for finishing a form is the app celebrating its own onboarding funnel. The
     * intake ends, the graph pops it off the stack, and the first real demand fires.
     */
    private suspend fun finish() {
        settings.setWizardComplete(true)
        AppGraph.markIntakeComplete()

        val app = getApplication<Application>()

        // Mirror his times into the boot-safe store NOW, so the wind-down window the interlocks are
        // keyed on is his and not the hardcoded 22:30/07:00 fallback — including on the Direct Boot
        // path, and including for the very first challenge armed below. Nothing else wrote this.
        com.secondspine.app.enforce.Enforcement.syncBootState(
            context = app,
            installAt = settings.installAt.first(),
            winddownAtMinutes = settings.winddownAtMinutes.first(),
            wakeAtMinutes = settings.wakeAtMinutes.first(),
        )

        // ARM THE FIRST REMINDER. The doc on the step machine says "the intake ends... and the first
        // real demand fires" — but nothing fired it. This is the line that makes that sentence true:
        // the moment the wizard closes, today's ladder is armed for the habits he just picked.
        com.secondspine.app.enforce.ChallengePlanner.planToday(app)
    }

    private companion object {
        @Volatile private var gatesWired = false

        /**
         * Point the shell's gate seam at the real store, once per process.
         *
         * `AppGraph.gates` is what `HomeViewModel` and the register mix actually read, and its own
         * doc names this as the data layer's wiring point — but nothing calls it, so the gate this
         * screen exists to set would be written to disk and then never read. Wiring it from the one
         * screen that produces the value is not ideal ownership, and it is much better than a
         * SCOFF-positive result that persists correctly and changes nothing.
         *
         * Guarded because `AppGraph.wireGates` launches a collector: two calls would mean two
         * collectors writing identical values into the same `MutableStateFlow` — harmless, and still
         * not worth leaking one per ViewModel.
         */
        fun wireGatesOnce() {
            if (gatesWired) return
            synchronized(this) {
                if (gatesWired) return
                gatesWired = true
                AppGraph.wireGates(
                    Graph.repository.clinicalGates.stateIn(
                        scope = Graph.appScope,
                        started = SharingStarted.Eagerly,
                        initialValue = ClinicalGates(),
                    )
                )
            }
        }
    }
}
