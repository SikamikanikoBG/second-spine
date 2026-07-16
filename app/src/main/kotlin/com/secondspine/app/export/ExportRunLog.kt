package com.secondspine.app.export

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * THE RUN LOG — `export_run`, as a file.
 *
 * SPEC §8.6 specifies a table: *"Writes `export_run` on every attempt... Failure writes
 * `export_run.error` and DOES NOT silently retire."* There is no `export_run` entity in the schema
 * and the schema is another agent's file, so the table is not available to write to. This is that
 * table, as an append-only flat file, and the substitution is deliberate rather than a shortcut:
 *
 *  1. **It must be readable when the database is not.** The whole point of the export is that the
 *     archive survives this app. An export health record that lives inside the database it exists to
 *     evacuate has a failure mode where the thing that broke is also the thing that would have told
 *     you it broke. `AppOpenLog` is a flat file for the same reason and says so.
 *  2. It is bounded, dateless-append, and holds no user text — only outcomes.
 *
 * The last line wins. [ExportStatus] reads this to decide whether the app is FAILING LOUDLY, so the
 * one number that matters — *when did his data last actually leave* — depends on nothing except the
 * filesystem.
 */
internal class ExportRunLog(private val file: File) {

    /** One attempt. Not one success — an attempt that failed is the more important row. */
    data class Run(
        val at: Long,
        val ok: Boolean,
        val filesInArchive: Int,
        val error: String?,
    )

    suspend fun record(run: Run) = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(encode(run) + "\n")
            trimIfLarge()
        }
        Unit
    }

    /** Every attempt still on file, oldest first. */
    suspend fun runs(): List<Run> = withContext(Dispatchers.IO) { read() }

    /** The last attempt of any kind, successful or not. */
    suspend fun last(): Run? = withContext(Dispatchers.IO) { read().lastOrNull() }

    /** The last attempt that actually moved his data. This is the number the 14-day rule reads. */
    suspend fun lastSuccess(): Run? = withContext(Dispatchers.IO) { read().lastOrNull { it.ok } }

    private fun read(): List<Run> {
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines().mapNotNull(::decode) }.getOrDefault(emptyList())
    }

    /**
     * Fields are tab-separated because the error string is the one field we do not control — it comes
     * out of a `Throwable` from a `DocumentsProvider` we did not write. A comma, a quote or a newline
     * in a vendor's error message must not be able to corrupt the record of whether the export ran.
     */
    private fun encode(run: Run): String = listOf(
        run.at.toString(),
        if (run.ok) "OK" else "ERR",
        run.filesInArchive.toString(),
        (run.error ?: "").replace(Regex("[\\t\\r\\n]"), " ").take(MAX_ERROR_CHARS),
    ).joinToString("\t")

    private fun decode(line: String): Run? {
        val parts = line.split('\t')
        if (parts.size < 3) return null
        val at = parts[0].toLongOrNull() ?: return null
        return Run(
            at = at,
            ok = parts[1] == "OK",
            filesInArchive = parts[2].toIntOrNull() ?: 0,
            error = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
        )
    }

    private fun trimIfLarge() {
        if (file.length() < PRUNE_BYTES) return
        val kept = read().takeLast(KEEP_RUNS)
        file.writeText(kept.joinToString("\n", postfix = "\n") { encode(it) })
    }

    companion object {
        private const val MAX_ERROR_CHARS = 300
        private const val PRUNE_BYTES = 64 * 1024L
        private const val KEEP_RUNS = 200

        fun of(context: Context): ExportRunLog =
            ExportRunLog(File(File(context.filesDir, "export"), "export_runs.log"))
    }
}
