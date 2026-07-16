package com.secondspine.app.ui.archive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.secondspine.app.AppGraph
import com.secondspine.app.data.Graph
import com.secondspine.app.export.ExportStatus
import com.secondspine.app.ui.ProofSource
import com.secondspine.coach.Register
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import java.time.ZoneId

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
 *   locale, and neither the brain nor a render pass should be the thing that owns one. `ui.ProofSource`
 *   is the single place that holds a `ZoneId` and the single place a proof timestamp becomes words —
 *   home's filmstrip and this grid are two readers of that one formatter, not two copies of it.
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
 * **The frame source was a seam and the seam was the bug.** It used to be a `wireFrames()` method
 * that nothing anywhere called, defaulting to empty — so an archive with real photographs on disk and
 * real rows in `proof` rendered "NO TAPE YET", forever, and no compile and no CI could tell the
 * difference between "unwired" and "he has taken no photographs". That is the same failure as
 * `wireHabits` and `Graph.install`, and it is worse here than anywhere else in the app: the archive is
 * what is left standing at jurisdiction 0, so a permanently empty one deletes month 8 rather than
 * degrading it.
 *
 * It now reads [ProofSource], which reads `proof`. The default is the real table, not empty. Empty
 * still renders "NO TAPE YET" — but only when the table genuinely is, and a fabricated archive
 * remains the one lie this screen cannot tell, because the archive being *his* is the entire reason
 * it survives to month 8.
 */
class ArchiveViewModel @JvmOverloads constructor(
    app: Application,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /**
     * The real table by default. A parameter rather than a hardcoded call so the formatting and the
     * grouping stay testable against a fake, which is what the old seam was reaching for and got
     * wrong by defaulting it to empty instead of to the truth.
     */
    frames: Flow<List<ProofFrame>> = ProofSource.frames(zone),
) : AndroidViewModel(app) {

    init {
        // Idempotent, and the same defensive call `HomeViewModel` and `SettingsViewModel` make:
        // `Graph.db` throws rather than returning null, and this ViewModel is reachable from a route.
        Graph.install(app)
    }

    /**
     * The export receipt, live.
     *
     * Wired for the same reason as the frames: [ExportFooter] read a field nothing ever wrote, so the
     * one line that proves the app does not hold the archive hostage said "NEVER EXPORTED" on a device
     * that had exported that morning. NEVER_RUN and NO_FOLDER both map to null — no folder means
     * nothing has left, which is the honest reading and the one `ExportStatus.health` already takes.
     */
    private val daysSinceExport: Flow<Int?> = ExportStatus.observe(app)
        .map { health ->
            when (health.state) {
                ExportStatus.State.NEVER_RUN, ExportStatus.State.NO_FOLDER -> null
                else -> health.daysSince
            }
        }
        .catch { emit(null) }

    val state: StateFlow<ArchiveState> = combine(
        AppGraph.jurisdiction,
        frames,
        daysSinceExport,
    ) { j: Int, rows: List<ProofFrame>, exported: Int? ->
        ArchiveState(
            jurisdiction = j,
            // `ProofDao.observeAll()` is already `capturedAtWall DESC`, and `sortedByDescending` is
            // stable — so months land newest-first and, within a day, the query's newest-first order
            // survives. `groupBy` preserves first-encounter key order, which is what makes the
            // scrubber's rail read March, February, January downwards without a second sort.
            months = rows
                .sortedByDescending { it.epochDay }
                .groupBy { it.monthLabel }
                .map { (label, frames) -> ArchiveMonth(label, frames) },
            daysSinceExport = exported,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveState())
}
