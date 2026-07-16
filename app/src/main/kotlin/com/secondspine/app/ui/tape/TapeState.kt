package com.secondspine.app.ui.tape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.AppGraph
import com.secondspine.app.ui.archive.ProofFrame
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Tape
import com.secondspine.coach.WeekData
import com.secondspine.coach.composeTape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * THE TAPE'S STATE.
 *
 * `:coach`'s `composeTape` already decided everything that matters: which segments exist, in which
 * order, at which register, cut to the 90-second ceremony cap, with every clinical gate asserted at
 * **compose** time rather than at render. SPEC §9.9 is explicit about why that split exists — *"a
 * suppression that happens in the UI is a suppression that a UI bug can undo on the worst night of
 * somebody's year"* — and it makes this class's job narrow and honest: carry the composed [Tape], and
 * carry the three things the composer deliberately does not hold.
 *
 * **What the composer does not hold, and why:**
 *
 *  - `Montage(photos: Int)` is a *count*. `:coach` is pure JVM and has never seen a file path. So the
 *    photographs arrive here, from the archive, as [ProofFrame]s.
 *  - `RoastLine.chart` is a *descriptor* ("bar chart, opens vs sessions, 12 weeks"), not a series.
 *  - `Trends.weightEwma` is a *Boolean* — whether the trend renders at all. Rip has no read access to
 *    that table, so the composer is not given the numbers either.
 *
 * Those three absences are all the same decision and it is a good one: the brain composes the show,
 * and the show's evidence is fetched by the layer that is allowed to touch a disk.
 */
data class TapeState(
    /**
     * Null until `TapeBuildWorker` has composed an edition. **Null renders as "the first tape is
     * Sunday", flat, in the app's own voice — never as a fabricated edition.** A demo Tape would be
     * the one lie this surface cannot tell: the entire reason it survives to month 8 is that the
     * Montage is *his* life, and a Montage of stock photographs on day 3 poisons that on day 3.
     */
    val tape: Tape? = null,

    /**
     * The week's photographs, newest last. The Montage is the emotional core and the reason he opens
     * this at month 8 — a photo journal wearing an evidence-locker costume.
     */
    val montage: List<ProofFrame> = emptyList(),

    /**
     * The series behind each roast line, keyed by `RoastLine.chart`.
     *
     * Empty is a legitimate state and renders as the chart's frame with no series — which is honest.
     * The alternative, inventing a plausible-looking curve so the tap feels good, would make the one
     * segment whose entire value is that *the taps resolve to charts he'd have built himself* into
     * the one segment that lies to him.
     */
    val chartSeries: Map<String, List<Float>> = emptyMap(),

    /**
     * The EWMA trend, as a bare curve. **No headline number, no colour, no arrow, no goal, no BMI.**
     *
     * It is a list of floats and nothing else, and there is deliberately nowhere in this state object
     * to put a delta, a target, or a verdict. `Trends` renders it grey and unvoiced or not at all.
     */
    val weightSeries: List<Float> = emptyList(),
) {
    /**
     * How worn the tape is, from the composed edition's own language rung.
     *
     * `ladderRung` is `4 - jurisdiction`: rung 0 is a man who owns your whole life and is LOUD; rung
     * 4 is an empty desk and nothing left but GHOST. The picture fails as he does, which is the arc
     * rendered in the medium rather than narrated — he never says he is losing.
     */
    val heavyWear: Boolean get() = (tape?.rung ?: 0) >= 3
}

/**
 * The Tape's view model.
 *
 * It calls `composeTape` rather than receiving a composed Tape, and that is deliberate: the composer
 * is a pure function of `(WeekData, jurisdiction, gates)`, so calling it here means the clinical
 * gates are re-asserted against the *live* gate state on every composition. If a quarterly SCOFF
 * re-screen flips positive on Sunday afternoon, the Tape that opens at 20:00 has already lost its
 * mocking registers — with no cache to invalidate and no worker to re-run.
 *
 * `composeTape` throws if jurisdiction is out of 0..4 and `check`s its own grammar gates on the way
 * out. Both are correct and neither is caught here: an edition that violates a clinical gate must not
 * render at all, and swallowing that exception to show *something* is exactly the failure the
 * compose-time assertion exists to prevent.
 */
class TapeViewModel : ViewModel() {

    private val _week = MutableStateFlow<WeekData?>(null)
    private val _montage = MutableStateFlow<List<ProofFrame>>(emptyList())
    private val _charts = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    private val _weight = MutableStateFlow<List<Float>>(emptyList())

    /** `TapeBuildWorker`'s wiring point: the composed week for the current edition. */
    fun wireWeek(source: StateFlow<WeekData?>) {
        viewModelScope.launch { source.collect { _week.value = it } }
    }

    /** The archive's wiring point: this week's frames, for the Montage. */
    fun wireMontage(source: StateFlow<List<ProofFrame>>) {
        viewModelScope.launch { source.collect { _montage.value = it } }
    }

    /** The roast's evidence, keyed by `RoastLine.chart`. */
    fun wireCharts(source: StateFlow<Map<String, List<Float>>>) {
        viewModelScope.launch { source.collect { _charts.value = it } }
    }

    /** The EWMA curve. Trend only, forever. */
    fun wireWeightSeries(source: StateFlow<List<Float>>) {
        viewModelScope.launch { source.collect { _weight.value = it } }
    }

    val state: StateFlow<TapeState> = combine(
        AppGraph.jurisdiction,
        AppGraph.gates,
        _week,
        _montage,
        _charts,
    ) { j: Int, gates: ClinicalGates, week: WeekData?, montage: List<ProofFrame>, charts: Map<String, List<Float>> ->
        TapeState(
            tape = week?.let { composeTape(it, j, gates) },
            montage = montage,
            chartSeries = charts,
            weightSeries = _weight.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TapeState())
}
