package com.secondspine.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.secondspine.app.data.Graph
import kotlinx.coroutines.flow.first

/**
 * THE FOLDER — one `ACTION_OPEN_DOCUMENT_TREE`, persisted, and then never asked for again.
 *
 * This is the whole privacy argument in one API choice, and it is worth being explicit about why SAF
 * rather than any of the easier options:
 *
 *  - **Not MediaStore.** This app photographs his kitchen, his desk and the pages of his books. A
 *    MediaStore write puts all of it in the gallery, which is the one place it must never be — it
 *    would be on a photo-grid between his family and his holidays, and it would sync to a cloud he
 *    did not choose.
 *  - **Not a cloud SDK.** There isn't one, there never will be, and the app holds no INTERNET use it
 *    could smuggle one through.
 *  - **Not app-private storage alone.** App-private storage dies with the uninstall, and an archive
 *    that dies with the uninstall is a hostage.
 *
 * A SAF tree the user picked himself satisfies both constraints at once: he points it at a folder he
 * already Syncthings to his own server, so the archive survives the uninstall **and** never lands in
 * anyone's gallery or anyone's cloud. It is his folder, on his terms, and the app only ever holds a
 * URI he handed it.
 *
 * The permission is persisted across reboots via [persist]. Without that call the grant dies with the
 * process and the export silently stops working — which is exactly the failure [ExportStatus] exists
 * to make loud rather than silent.
 */
object ExportFolder {

    /**
     * The picker intent. Call from the wizard; hand the result to [persist].
     *
     * `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` is the load-bearing flag: without it the grant is
     * scoped to this process and the weekly job is dead on the first reboot.
     */
    fun pickIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
    }

    /**
     * Take the persistable grant and remember the tree.
     *
     * Returns false if the system refused to persist the grant, and the caller must treat that as the
     * wizard step NOT being complete. RESOLUTIONS §E makes the export a v1 ship blocker and SPEC §8.6
     * says the wizard cannot complete without a tree URI; a wizard that advances on a grant the
     * system quietly declined would ship an app whose archive never leaves and which never says so.
     */
    suspend fun persist(context: Context, treeUri: Uri): Boolean {
        val ok = runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!ok || !isWritable(context, treeUri)) return false
        Graph.settings.setExportTreeUri(treeUri.toString())
        return true
    }

    /** The tree the user picked, or null. */
    suspend fun current(context: Context): Uri? {
        val raw = Graph.settings.exportTreeUri.first() ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        return uri.takeIf { isWritable(context, it) }
    }

    /**
     * Is the grant still alive?
     *
     * Checked on every run rather than trusted once. A user can revoke a tree grant from system
     * settings, and an SD card can be unmounted — both present as a URI that parses perfectly and
     * writes nothing. The export must notice that, not discover it as a silence.
     */
    fun isWritable(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }

    /** The tree's root document id, i.e. where [Exporter] starts building. */
    internal fun rootDocumentId(treeUri: Uri): String? =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()

    /** The folder the app creates inside his tree. One name, stable forever — it is a sync target. */
    const val ARCHIVE_DIR = "SecondSpine"
}
