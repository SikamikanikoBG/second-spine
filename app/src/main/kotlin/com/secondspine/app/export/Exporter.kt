package com.secondspine.app.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.secondspine.app.BuildConfig
import com.secondspine.app.data.Graph
import com.secondspine.app.data.ProofRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * THE EXPORT.
 *
 * "If the app only retains him because leaving destroys the archive, it was not a product, it was a
 * lock. Ship the export and find out."
 *
 * That sentence is the reason this file exists and it is the reason it is a v1 ship blocker rather
 * than a settings-screen nicety. Every retention mechanic in this product is coercive by design — the
 * ladder, the lock, the jurisdiction the coach refuses to give back until the data says he must. A
 * coercive app that *also* holds the only copy of ten months of your own life is not using a
 * commitment device, it is using a hostage, and the difference is not visible from inside the app.
 * The export is the falsification test: it hands him the door, on a schedule, whether the app likes
 * it or not, and then we find out whether anybody stays.
 *
 * It is deliberately not a feature the app can win back by being clever. It is not gated on being
 * up to date, it is not gated on a good week, it never asks him to confirm, and it never gets quieter
 * when it fails. At fourteen days without a successful run, [ExportStatus] makes the app fail loudly.
 *
 * ## What leaves
 *
 * The photos and a readable manifest of his own data ([ArchiveManifest]). Photos are copied
 * incrementally — a proof already in the folder is never rewritten, so a ten-month archive costs one
 * `query` per month directory and then only the new files. The manifest is **overwritten**, never
 * appended and never dated, so the ledger's 28-day forgetting survives the trip out (see
 * [ArchiveSnapshot.ledger]).
 *
 * ## What does not leave
 *
 * The isolate. Its DAO has no SELECT and this file has no way to ask.
 */
object Exporter {

    private const val TAG = "SecondSpine/Export"

    /**
     * One export at a time, process-wide.
     *
     * The weekly job and the one-tap "export now" are different WorkManager requests with different
     * unique names, so nothing else stops them landing on the same tree in the same second — and two
     * writers interleaving `createDocument` calls against the same `DocumentsProvider` is how you get
     * a half-written manifest next to a photo that is in the folder but not in the index.
     */
    private val mutex = Mutex()

    sealed interface Result {
        /** @param filesInArchive TOTAL photos in the folder, not this run's delta. It is the chip's number. */
        data class Success(val filesInArchive: Int, val newFiles: Int, val at: Long) : Result

        /** The user has not picked a folder, or the grant is gone. Not retryable: he must act. */
        data object NoFolder : Result

        /** Something failed mid-flight. Retryable — a provider can be busy, a card can be remounted. */
        data class Failed(val error: String) : Result
    }

    /**
     * Run the export.
     *
     * Records an [ExportRunLog] row on **every** attempt including the failures, because SPEC §8.6's
     * rule is that a failed export "DOES NOT silently retire" — an export that fails quietly for six
     * weeks is indistinguishable, from the outside, from an export that is working.
     */
    suspend fun export(context: Context): Result = mutex.withLock {
        val log = ExportRunLog.of(context)
        val at = System.currentTimeMillis()
        val result = runCatching { withContext(Dispatchers.IO) { run(context, at) } }
            .getOrElse { t ->
                Log.w(TAG, "export threw", t)
                Result.Failed(t.message ?: t::class.java.simpleName)
            }

        when (result) {
            is Result.Success -> {
                Graph.settings.recordExport(at = result.at, filesWritten = result.filesInArchive)
                log.record(ExportRunLog.Run(at, ok = true, filesInArchive = result.filesInArchive, error = null))
            }
            is Result.NoFolder ->
                log.record(ExportRunLog.Run(at, ok = false, filesInArchive = 0, error = "no folder picked"))
            is Result.Failed ->
                log.record(ExportRunLog.Run(at, ok = false, filesInArchive = 0, error = result.error))
        }
        result
    }

    private suspend fun run(context: Context, at: Long): Result {
        val tree = ExportFolder.current(context) ?: return Result.NoFolder
        val rootId = ExportFolder.rootDocumentId(tree) ?: return Result.NoFolder
        val resolver = context.contentResolver

        val archiveId = resolver.findOrCreateDir(tree, rootId, ExportFolder.ARCHIVE_DIR)
            ?: return Result.Failed("could not create ${ExportFolder.ARCHIVE_DIR}/")
        val proofsId = resolver.findOrCreateDir(tree, archiveId, "proofs")
            ?: return Result.Failed("could not create proofs/")

        val db = Graph.db
        val proofs = db.proofDao().observeAll().first()

        // ── the photos ──────────────────────────────────────────────────────
        // Grouped by the month directory they belong in, so each directory is listed exactly once no
        // matter how many photos it holds. Ten months in, this is ~10 queries and then only new files.
        val paths = mutableMapOf<Long, String>()
        var newFiles = 0
        var totalPhotos = 0

        val byMonth = proofs.groupBy { monthPath(it.capturedAtWall) }
        for ((month, rows) in byMonth) {
            val (year, mm) = month
            val yearId = resolver.findOrCreateDir(tree, proofsId, year)
                ?: return Result.Failed("could not create proofs/$year/")
            val monthId = resolver.findOrCreateDir(tree, yearId, mm)
                ?: return Result.Failed("could not create proofs/$year/$mm/")

            val existing = resolver.childNames(tree, monthId)
            for (proof in rows) {
                val source = proof.sourceFile(context) ?: continue
                val name = proof.exportName(source)
                paths[proof.id] = "proofs/$year/$mm/$name"
                totalPhotos++
                if (name in existing) continue
                val ok = resolver.copyInto(tree, monthId, name, mimeOf(name), source)
                if (ok) newFiles++ else Log.w(TAG, "could not copy ${source.name}")
            }
        }

        // ── the manifest ────────────────────────────────────────────────────
        val snapshot = snapshot(at, proofs, paths)

        if (!resolver.writeText(tree, archiveId, "manifest.json", "application/json", snapshot.toJson())) {
            return Result.Failed("could not write manifest.json")
        }
        for ((name, body) in snapshot.csvFiles()) {
            if (!resolver.writeText(tree, archiveId, name, "text/csv", body)) {
                return Result.Failed("could not write $name")
            }
        }
        if (!resolver.writeText(tree, archiveId, "schema.md", "text/markdown", SCHEMA_MD)) {
            return Result.Failed("could not write schema.md")
        }

        return Result.Success(filesInArchive = totalPhotos, newFiles = newFiles, at = at)
    }

    private suspend fun snapshot(
        at: Long,
        proofs: List<ProofRow>,
        paths: Map<Long, String>,
    ): ArchiveSnapshot {
        val db = Graph.db
        return ArchiveSnapshot(
            exportedAt = at,
            appVersion = BuildConfig.VERSION_NAME,
            habits = db.habitDao().all(),
            days = db.dayDao().all(),
            transitions = db.stageTransitionDao().observeAll().first(),
            proofs = proofs,
            caught = db.caughtEventDao().all(),
            confessions = db.confessionDao().observeAll().first(),
            // Purged by the query itself: the DAO has no `since` parameter to widen.
            ledger = db.ledgerDao().surviving(at),
            weights = db.weightDao().all(),
            unpromptedOpensPerDay = Graph.repository.unpromptedOpensPerDay(at),
            photoPaths = paths,
        )
    }

    // ── files ───────────────────────────────────────────────────────────────

    /**
     * `imagePath` is documented as app-private under `filesDir/proofs/yyyy/MM`, but it is written by
     * the capture path and may be absolute or relative depending on how that agent stored it. Both
     * are resolved here rather than assumed, because the failure mode of guessing wrong is an export
     * that reports success and copies nothing.
     */
    private fun ProofRow.sourceFile(context: Context): File? {
        val direct = File(imagePath)
        val f = if (direct.isAbsolute) direct else File(context.filesDir, imagePath)
        return f.takeIf { it.isFile && it.length() > 0 }
    }

    /** Deterministic and collision-free: the proof id is the primary key, so two rows cannot clash. */
    private fun ProofRow.exportName(source: File): String = "p$id-${source.name}"

    private fun monthPath(millis: Long): Pair<String, String> {
        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return d.format(DateTimeFormatter.ofPattern("yyyy")) to d.format(DateTimeFormatter.ofPattern("MM"))
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    // ── SAF ─────────────────────────────────────────────────────────────────
    //
    // DocumentsContract directly rather than androidx.documentfile. DocumentFile.listFiles() is one
    // IPC per child and a findFile() is a full listing per lookup — over a ten-month archive that is
    // thousands of round trips to copy nothing. These helpers list a directory once and then work
    // from the names.

    private fun ContentResolver.childNames(tree: Uri, parentId: String): Set<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = mutableSetOf<String>()
        runCatching {
            query(children, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let(out::add)
            }
        }
        return out
    }

    private fun ContentResolver.findChild(tree: Uri, parentId: String, name: String): Pair<String, String?>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        runCatching {
            query(
                children,
                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == name) return c.getString(0) to c.getString(2)
                }
            }
        }
        return null
    }

    private fun ContentResolver.findOrCreateDir(tree: Uri, parentId: String, name: String): String? {
        findChild(tree, parentId, name)?.let { (id, mime) ->
            if (mime == Document.MIME_TYPE_DIR) return id
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val created = runCatching {
            DocumentsContract.createDocument(this, parentUri, Document.MIME_TYPE_DIR, name)
        }.getOrNull() ?: return null
        return runCatching { DocumentsContract.getDocumentId(created) }.getOrNull()
    }

    /**
     * Create-or-truncate. `"wt"` is the load-bearing character: without the `t` an overwrite of a
     * manifest that shrank (because the Ledger purged) leaves the tail of last week's JSON welded
     * onto the end of this week's, and the file stops parsing.
     */
    private fun ContentResolver.docForWrite(tree: Uri, parentId: String, name: String, mime: String): Uri? {
        findChild(tree, parentId, name)?.let { (id, _) ->
            return DocumentsContract.buildDocumentUriUsingTree(tree, id)
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        return runCatching { DocumentsContract.createDocument(this, parentUri, mime, name) }.getOrNull()
    }

    private fun ContentResolver.writeText(tree: Uri, parentId: String, name: String, mime: String, body: String): Boolean {
        val uri = docForWrite(tree, parentId, name, mime) ?: return false
        return runCatching {
            openOutputStream(uri, "wt")?.use { it.write(body.toByteArray()) } != null
        }.getOrDefault(false)
    }

    private fun ContentResolver.copyInto(tree: Uri, parentId: String, name: String, mime: String, source: File): Boolean {
        val uri = docForWrite(tree, parentId, name, mime) ?: return false
        return runCatching {
            openOutputStream(uri, "wt")?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
    }
}
