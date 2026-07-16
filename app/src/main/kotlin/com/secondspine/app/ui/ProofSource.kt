package com.secondspine.app.ui

import com.secondspine.app.data.Graph
import com.secondspine.app.ui.archive.ProofFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * THE ARCHIVE, ASSEMBLED FROM THE REAL TABLE.
 *
 * The sibling of [DemandSource], and it exists for the same reason: `:coach` owns no clock, `proof`
 * rows carry epoch millis, and turning "1774081260000" into "TUE 06:41" needs a timezone and a
 * locale that neither the brain nor a render pass may own. So the formatting lives in the app layer,
 * once, here.
 *
 * **Why this is one file and not two.** Both the Archive grid and home's filmstrip are views of the
 * same table, newest-first, and before this file existed each had its own seam — `wireFrames()` and
 * `HomeState.recentProofs` — that nothing called. Two unwired seams over one table is how you get an
 * app that photographs a man's kitchen for ten months and shows him "NO TAPE YET". One source, two
 * readers: if this file is broken, both screens are visibly broken together, which is the property
 * the old seams destroyed.
 *
 * `ProofDao.observeAll()` is already `ORDER BY capturedAtWall DESC`, so newest-first is the query's
 * guarantee and not an invariant this file re-establishes by sorting.
 *
 * **Nothing is filtered.** Not `voided`, not `appealed`, not rows whose file has gone missing. The
 * archive is a record of what happened, and every filter here would be a verdict — `voided` means
 * the *window* was invalid, never that the photograph was; `appealed` means the user disagreed with
 * the app, which is not grounds for the app to delete his picture. A frame whose file has vanished
 * renders as the gutter colour (see `ProofImage`), which is the honest rendering of a missing file
 * rather than a hidden row that makes the count silently wrong.
 */
internal object ProofSource {

    /** "TUE 06:41". Mono, and short — the grid is 3 columns wide. */
    private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)

    /** "MARCH 2026". The scrubber's rung. */
    private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.US)

    /**
     * Every frame, newest first, live.
     *
     * The `runCatching` is not defensive padding: `Graph.db` throws by design when `install()` has
     * not run, and a ViewModel constructed by a preview or a test harness that never installed the
     * graph should render an empty archive rather than crash the process. An empty archive on a
     * device that has never banked a proof is also the truth.
     */
    fun frames(zone: ZoneId): Flow<List<ProofFrame>> =
        runCatching { Graph.db.proofDao().observeAll() }
            .getOrElse { flowOf(emptyList()) }
            .map { rows ->
                rows.map { row ->
                    frameOf(
                        id = row.id,
                        habitId = row.habitId,
                        imagePath = row.imagePath,
                        capturedAtWall = row.capturedAtWall,
                        zone = zone,
                    )
                }
            }
            .catch { emit(emptyList()) }

    /**
     * Format one row into a frame. The only place a proof timestamp becomes words.
     *
     * Nothing about the *contents* of the photograph is read here, because nothing about the contents
     * is stored. There is no classifier output to map, and no column one could write to.
     */
    fun frameOf(
        id: Long,
        habitId: String,
        imagePath: String,
        capturedAtWall: Long,
        zone: ZoneId,
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
}
