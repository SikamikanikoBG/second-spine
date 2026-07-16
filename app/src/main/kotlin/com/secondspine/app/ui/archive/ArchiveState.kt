package com.secondspine.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.AppGraph
import com.secondspine.coach.Register
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * THE ARCHIVE'S STATE.
 *
 * Read the absences first, as everywhere in this product:
 *
 *  - **No verdict field.** A frame carries no `accepted`, no `score`, no `grade`. There is no
 *    `accepted` column on `proof` to read one from (see `Entities.kt`), and zero-assertion banking is
 *    the reason the callback engine works at month 6: you cannot call back to a month you burned an
 *    accusation in.
 *  - **No food field, and no way to add one.** THE DONUT IS ALLOWED. The archive photographs his
 *    kitchen at 6am and has, structurally, no opinion about what is on the counter.
 *  - **No weight, no body metric, no before/after.** The Archive is a photographic record of *work*,
 *    not of a body. There is no headline number on this screen and no slot to put one in.
 *  - **No streak.** A streak is a number whose only move is to zero.
 *
 * What is left is the thing that actually compounds: about 1,400 photographs of his own life, kept
 * forever. `proof` is the one table the 28-day purge never touches — evidence of failure purges,
 * evidence of work is kept. At jurisdiction 0 this screen *is* the product, and the reason it can be
 * the product on day 200 is that it shipped populated from proof #1 rather than gated behind the
 * horizon it was needed at.
 */

/**
 * One frame of the record.
 *
 * @param dayLabel and [monthLabel] arrive **pre-formatted**, exactly as `LedgerEntry.note` does in
 *   `:coach`, and for the same reason: turning epoch millis into "TUE 06:41" needs a timezone and a
 *   locale, and neither the brain nor a render pass should be the thing that owns one. See
 *   [ArchiveViewModel] for the single place in this package that holds a `ZoneId`.
 * @param caption Rip's caption, or null. Quiet, low jurisdiction, never a judgement of the contents
 *   of the photograph — he is 94% wrong about vision and 100% accurate about what he logged, so he
 *   captions the *logging*, never the picture. Null is the common case and is not an empty state.
 */
data class ProofFrame(
    val id: Long,
    val habitId: String,
    /** App-private path under filesDir/proofs/yyyy/MM. Never MediaStore — this app photographs his home. */
    val imagePath: String,
    val epochDay: Long,
    val dayLabel: String,
    val monthLabel: String,
    val caption: String? = null,
)

/** A month's worth of frames, and the scrubber's unit. */
data class ArchiveMonth(val label: String, val frames: List<ProofFrame>)

/**
 * THE ARCHIVE STATE.
 *
 * @param jurisdiction drives one thing only: whether Rip captions. At `j >= 3` he is busy running
 *   your life and the archive is a filmstrip he does not have time for; at `j <= 2` captioning is
 *   most of what is left of the job. SPEC §4.10: at day 200 he "mostly writes captions". This is not
 *   a feature that unlocks — it is the same field, rendered because he finally has nothing else to do.
 * @param daysSinceExport null means the export has never run, which [ExportFooter] renders loudly.
 *   The app never holds the archive hostage, and this line is how it proves it hasn't.
 */
data class ArchiveState(
    val jurisdiction: Int = 2,
    val months: List<ArchiveMonth> = emptyList(),
    val daysSinceExport: Int? = null,
) {
    val total: Int get() = months.sumOf { it.frames.size }

    /**
     * He captions at low jurisdiction. Same threshold as `HomeState.archiveLed`, and deliberately the
     * same integer that drives everything else — no second dial.
     */
    val captioned: Boolean get() = jurisdiction <= 2

    /** The register a caption is delivered in. Quiet, always. */
    val captionRegister: Register get() = if (jurisdiction <= 1) Register.GHOST else Register.BIT
}

/**
 * The archive view model.
 *
 * **On the timezone.** SPEC §4.2 bans the calendar from `ui/`: no `LocalDate.now()`, no
 * `daysSinceInstall`, no month index, because the arc must be a pure function of the odometer or it
 * is not falsifiable in CI. This class reads no clock and asks no question about *today* — it formats
 * timestamps that are already in the data, which is a different act. The `ZoneId` is a constructor
 * parameter so that the formatting is testable and so that the one place in this package that knows
 * about time zones is this line.
 *
 * The frame source is a seam, in the shape `AppGraph` already established: real when wired, honest
 * when not. It defaults to **empty**, and empty renders as "NO TAPE YET" rather than as sample
 * photographs — a fabricated archive is the one lie this screen cannot tell, because the archive
 * being *his* is the entire reason it survives to month 8.
 */
class ArchiveViewModel(
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _frames = MutableStateFlow<List<ProofFrame>>(emptyList())

    /** The data agent's wiring point: map `ProofDao.observeAll()` through [frameOf] and collect here. */
    fun wireFrames(source: StateFlow<List<ProofFrame>>) {
        viewModelScope.launch { source.collect { _frames.value = it } }
    }

    val state: StateFlow<ArchiveState> = combine(
        AppGraph.jurisdiction,
        _frames,
    ) { j: Int, frames: List<ProofFrame> ->
        ArchiveState(
            jurisdiction = j,
            months = frames
                .sortedByDescending { it.epochDay }
                .groupBy { it.monthLabel }
                .map { (label, rows) -> ArchiveMonth(label, rows) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveState())

    /**
     * Format one row into a frame. The only place a proof timestamp becomes words.
     *
     * Note that nothing about the *contents* of the photograph is read here, because nothing about
     * the contents is stored. There is no classifier output to map.
     */
    fun frameOf(
        id: Long,
        habitId: String,
        imagePath: String,
        capturedAtWall: Long,
        caption: String? = null,
    ): ProofFrame {
        val local = Instant.ofEpochMilli(capturedAtWall).atZone(zone)
        return ProofFrame(
            id = id,
            habitId = habitId,
            imagePath = imagePath,
            epochDay = local.toLocalDate().toEpochDay(),
            dayLabel = DAY_FORMAT.format(local).uppercase(Locale.US),
            monthLabel = MONTH_FORMAT.format(local).uppercase(Locale.US),
            caption = caption,
        )
    }

    private companion object {
        /** "TUE 06:41". Mono, and short — the grid is 3 columns wide. */
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)

        /** "MARCH 2026". The scrubber's rung. */
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.US)
    }
}
