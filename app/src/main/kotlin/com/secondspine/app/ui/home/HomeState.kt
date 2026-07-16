package com.secondspine.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.AppGraph
import com.secondspine.app.data.Graph
import com.secondspine.app.ui.DemandSource
import com.secondspine.app.ui.ProofSource
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Habit
import com.secondspine.coach.LedgerRow
import com.secondspine.coach.Quiet
import com.secondspine.coach.Register
import com.secondspine.coach.Stage
import com.secondspine.coach.jurisdiction
import com.secondspine.coach.jurisdictionShare
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

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
 *
 * @param imagePath the absolute path `CameraCapture.persist` wrote, carried all the way to the
 *   filmstrip. It used to be absent, which meant this type could not render a photograph even in
 *   principle: the strip drew grey rectangles the size of a photo, in the place a photo goes. A
 *   filmstrip of grey rectangles is not a smaller version of the archive, it is an advert for one.
 * @param id the `proof` row id. A `Long` and not a `String`, because the strip seeds its tape wear
 *   off it arithmetically and `String.hashCode() % 1f` — the old seed — is zero for every frame, so
 *   every rectangle tore in lockstep.
 */
data class ProofThumb(val id: Long, val imagePath: String, val dayLabel: String)

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
 * SPEC §4.2's rule stands and is worth restating precisely, because this class now reads a clock and
 * the rule is narrower than "no time in `ui/`": no `LocalDate.now()`, no `daysSinceInstall`, no month
 * index may drive **the arc**. The arc is the odometer's, and `jurisdiction(habits)` below is still
 * its only input — no wall-clock reading touches `ripFraction`, `archiveLed`, the register mix or the
 * speech budget. What the clock is used for here is the demand's *schedule*, which is a different
 * question and one that cannot be answered without knowing what time it is. `:coach` refuses to own a
 * clock precisely so that this layer must, and `DemandSource` is where it lives.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    init {
        // Idempotent. `SecondSpineApp.onCreate` installs only the shell's seam, and `Graph.db` throws
        // rather than returning null — the same defensive call every other ViewModel here makes.
        Graph.install(app)
    }

    /**
     * A manual pulse, bumped on resume. See [onResume].
     *
     * Seeded with `currentTimeMillis` rather than 0 so that [merge] has something to emit
     * immediately — a `combine` that waits for the first tick would render the Floor for a minute on
     * every cold start, which is the exact false "nothing is owed" this whole change exists to kill.
     */
    private val pulse = MutableStateFlow(System.currentTimeMillis())

    /**
     * The demand is a function of the wall clock, so it must be recomputed when the clock moves and
     * not only when a row changes. One minute is the coarsest tick that cannot visibly miss a window
     * boundary, and it only runs while the screen is actually subscribed (`WhileSubscribed`).
     */
    private val tick: Flow<Long> = flow {
        while (true) {
            delay(60_000L)
            emit(System.currentTimeMillis())
        }
    }

    /**
     * DB CHANGE, observed at the table that actually matters.
     *
     * `day` is the row `resolveDemand` reads, and every writer of it — `bankProof`, `confess`, and
     * `DemandSource.ensureTodayScheduled` — goes through Room, so Room's own invalidation is the
     * signal. `DayDao` exposes only a per-habit flow (it is not this agent's file to widen), so the
     * enabled habits' flows are combined; there are at most two of them, `MAX_ENFORCED = 2`.
     *
     * The emitted value is discarded. This flow is an invalidation signal, not a data source — the
     * rows are re-read inside `DemandSource.resolve` so that the query and the `now` it is resolved
     * against are always from the same instant.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayChanges: Flow<Unit> = AppGraph.habits.flatMapLatest { habits ->
        val flows = habits.map { Graph.db.dayDao().observeForHabit(it.id) }
        if (flows.isEmpty()) flowOf(Unit) else combine(flows) { }
    }

    /**
     * Re-arm and recompute. Called from `MainActivity`'s home slot on every `ON_RESUME` of the home
     * back-stack entry — which covers the cold start, the return from the background, *and* the pop
     * back from the camera.
     */
    fun onResume() {
        viewModelScope.launch {
            DemandSource.ensureTodayScheduled()
            pulse.value = System.currentTimeMillis()
        }
    }

    /**
     * THE FILMSTRIP, from the same table the Archive reads.
     *
     * `recentProofs` had no writer — the same unwired-seam bug as `ArchiveViewModel.wireFrames`, and
     * it is not a coincidence that both surfaces over one table were dark at once: two seams over one
     * source is two chances to forget. `ui.ProofSource` is now the single source both read, so this
     * strip cannot be empty while the grid is full, and a break in either is a break in both — which
     * is the property that makes it noticeable.
     *
     * The strip is a *pull* to the grid, not a second archive: it shows the head of the timeline and
     * the tap opens the real thing. [STRIP_FRAMES] is the cap that keeps a 1,400-proof table from
     * being decoded into a horizontal scroll at 7am — the grid is where 1,400 frames belong, and it
     * is lazy.
     */
    private val recentProofs: Flow<List<ProofThumb>> = ProofSource.frames(ZoneId.systemDefault())
        .map { frames ->
            // Newest-first is `ProofDao.observeAll()`'s own `ORDER BY capturedAtWall DESC`, so `take`
            // is the head of the timeline and not an arbitrary slice.
            frames.take(STRIP_FRAMES).map { ProofThumb(it.id, it.imagePath, it.dayLabel) }
        }
        .catch { emit(emptyList()) }

    val state: StateFlow<HomeState> = combine(
        AppGraph.habits,
        AppGraph.gates,
        dayChanges,
        merge(pulse, tick),
        recentProofs,
    ) { habits: List<Habit>, gates: ClinicalGates, _: Unit, _: Long, proofs: List<ProofThumb> ->
        val (demand, quiet) = DemandSource.resolve(getApplication(), habits)
        HomeState(
            jurisdiction = jurisdiction(habits),
            gates = gates,
            // THE ONE DEMAND, and the mapping is the whole point of `ui.home.Demand` being a
            // different type from `coach.Demand`: `tier` and `lockEligible` are decisions the ladder
            // makes and the screen must not be able to render. Two fields cross. Nothing else.
            demand = demand?.let { Demand(habitId = it.habitId, text = it.text) },
            ladder = habits.map { LadderRow(it.id, it.stage) },
            recentProofs = proofs,
        ).also { quietReasons = quiet }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private companion object {
        /** The head of the timeline. Enough to read as a record, few enough to decode at breakfast. */
        const val STRIP_FRAMES = 12
    }

    /**
     * Why the app is quiet, per habit. Nothing renders this yet — it is kept because `Quiet`'s own
     * doc names its consumers ("the debug screen and the Sunday roast") and because *"the app is
     * quiet" and "the app is broken" look identical from the outside*. Losing it at the seam would
     * throw away the only thing that tells them apart.
     */
    @Volatile
    var quietReasons: Map<String, Quiet> = emptyMap()
        private set
}
