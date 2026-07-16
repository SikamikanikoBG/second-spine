package com.secondspine.app.export

import android.content.Context
import com.secondspine.app.data.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * PROOF OF LAST RUN, and the fourteen-day alarm.
 *
 * SPEC §8.6: TODAY carries a permanent, un-dismissable chip — *"EXPORTED 3 DAYS AGO — 1,412 FILES."*
 * At **14 days without a successful run the app fails loudly**: a non-dismissable banner, every
 * non-safety alarm suspended until it is resolved, and Rip says it out loud, once.
 *
 * The chip is the interesting half. An export that works silently is indistinguishable from an export
 * that has been dead since March, and the whole argument for the export — that the archive is his and
 * can leave — is worth exactly nothing if the mechanism has quietly stopped and nobody can tell. So
 * the app is required to state, permanently and without being asked, when his data last actually
 * left. A backup you have not verified is a rumour.
 *
 * Note what the loud failure is aimed at. It is not aimed at him — he did nothing wrong, and being
 * nagged for the app's broken plumbing would be the app charging him for its own bug. It is aimed at
 * *the app*: the app suspends its own non-safety aggression until it has done the one thing it
 * promised. It has no right to demand a set from a man whose archive it is currently failing to hand
 * back.
 */
object ExportStatus {

    /** SPEC §8.6. Fourteen days, and it is not a preference. */
    const val LOUD_FAILURE_DAYS = 14

    /** A run older than this stops reading as "just now" on the chip. */
    private const val DAY_MS = 86_400_000L

    enum class State {
        /** No tree URI. The wizard is meant to make this unreachable; it is handled anyway. */
        NO_FOLDER,

        /** A folder is picked and nothing has left yet. Normal for the first few hours. */
        NEVER_RUN,

        /** Working. */
        FRESH,

        /** Late, but not yet a scandal. Between one missed weekly run and the 14-day line. */
        STALE,

        /** >= 14 days. The app fails loudly and suspends its own non-safety aggression. */
        FAILING,
    }

    data class Health(
        val state: State,
        val lastSuccessAt: Long,
        val daysSince: Int,
        val filesInArchive: Int,
        /** The last attempt's error, if the last attempt failed. Never a stack trace, never a model name. */
        val lastError: String?,
    ) {
        /** The un-dismissable chip. Flat, factual, monospace. No character in it — it is a receipt. */
        val chip: String
            get() = when (state) {
                State.NO_FOLDER -> "NO EXPORT FOLDER"
                State.NEVER_RUN -> "NEVER EXPORTED"
                else -> "EXPORTED ${agoLabel(daysSince)} — ${"%,d".format(filesInArchive)} FILES"
            }

        val failingLoudly: Boolean get() = state == State.FAILING || state == State.NO_FOLDER
    }

    /**
     * The chip, live. Recomputed whenever the export writes and whenever the collector re-subscribes.
     *
     * `daysSince` is derived from a clock rather than stored, so this flow does not tick on its own —
     * a chip that re-renders every second to advance a day counter would be a wakelock with a
     * typeface. It is correct on every recomposition, which is the only time anyone reads it.
     */
    fun observe(context: Context): Flow<Health> =
        combine(
            Graph.settings.exportTreeUri,
            Graph.settings.lastExportAt,
            Graph.settings.lastExportFileCount,
        ) { uri, lastAt, files -> Triple(uri, lastAt, files) }
            .map { (uri, lastAt, files) ->
                val error = ExportRunLog.of(context).last()?.takeIf { !it.ok }?.error
                health(
                    hasFolder = uri != null && ExportFolder.isWritable(context, android.net.Uri.parse(uri)),
                    lastSuccessAt = lastAt,
                    filesInArchive = files,
                    lastError = error,
                    now = System.currentTimeMillis(),
                )
            }
            .flowOn(Dispatchers.IO)

    /** One-shot read, for the workers. */
    suspend fun now(context: Context, now: Long = System.currentTimeMillis()): Health {
        val log = ExportRunLog.of(context)
        val lastSuccess = log.lastSuccess()
        val last = log.last()
        return health(
            hasFolder = ExportFolder.current(context) != null,
            lastSuccessAt = lastSuccess?.at ?: 0L,
            filesInArchive = lastSuccess?.filesInArchive ?: 0,
            lastError = last?.takeIf { !it.ok }?.error,
            now = now,
        )
    }

    /**
     * Pure, so the rule is testable without a device and cannot drift into a UI file.
     *
     * NO_FOLDER counts as failing. It has to: the alternative is an install that never picked a
     * folder sitting at "NEVER EXPORTED" forever while the ladder runs at full volume, which is
     * precisely the lock this feature exists to disprove.
     */
    internal fun health(
        hasFolder: Boolean,
        lastSuccessAt: Long,
        filesInArchive: Int,
        lastError: String?,
        now: Long,
    ): Health {
        val days = if (lastSuccessAt <= 0L) Int.MAX_VALUE else ((now - lastSuccessAt) / DAY_MS).toInt()
        val state = when {
            !hasFolder -> State.NO_FOLDER
            lastSuccessAt <= 0L -> State.NEVER_RUN
            days >= LOUD_FAILURE_DAYS -> State.FAILING
            days >= STALE_DAYS -> State.STALE
            else -> State.FRESH
        }
        return Health(
            state = state,
            lastSuccessAt = lastSuccessAt,
            daysSince = if (days == Int.MAX_VALUE) 0 else days,
            filesInArchive = filesInArchive,
            lastError = lastError,
        )
    }

    /** Weekly job + one missed run. Past this the chip is telling him something, not just reporting. */
    internal const val STALE_DAYS = 8

    private fun agoLabel(days: Int): String = when (days) {
        0 -> "TODAY"
        1 -> "YESTERDAY"
        else -> "$days DAYS AGO"
    }

    /**
     * The one line Rip is allowed to say about this, verbatim from SPEC §8.6, and he says it ONCE.
     *
     * It is here rather than in the fragment bank because it is not a bit: it does not rotate, it
     * does not retire, it has no slot and no variants, and it is the only time he is on the user's
     * side against his own employer. It is also aimed at `the_situation` — never at the man. He is not
     * the reason the export is broken.
     */
    const val EXPORT_ALARM_LINE: String =
        "Fourteen days. No export. Which means every set, every page, the whole file — it's in ONE " +
            "box, in your POCKET, and brother, I have SEEN what you do to phones. Pick the folder. " +
            "I'll wait. I'm not doing another thing until you pick the folder."
}
