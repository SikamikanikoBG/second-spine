package com.secondspine.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * THE UPDATE HANDOFF. Downloads the APK to cacheDir/updates and hands it to the system installer.
 *
 * The app never installs anything itself — it stages the bytes and fires an ACTION_VIEW intent at
 * the OS package installer, where the user confirms the update (and, on first run, grants
 * "install unknown apps" to Second Spine). This is the whole reason for REQUEST_INSTALL_PACKAGES
 * and the FileProvider: the installer needs a content:// uri it is granted temporary read on.
 */
class UpdateInstaller(private val context: Context) {

    /**
     * Stream [apkUrl] to a single file in cacheDir/updates, overwriting any previous download.
     *
     * Runs on IO. Throws on failure — the caller ([UpdateViewModel]) wraps it and surfaces an error
     * state; nothing here is swallowed silently, because a half-written APK must not be handed on.
     */
    suspend fun download(apkUrl: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "second-spine-update.apk")
        if (target.exists()) target.delete()

        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "second-spine-updater")
        }
        try {
            if (conn.responseCode !in 200..299) {
                error("download failed: HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
        target
    }

    /**
     * Hand [file] to the system package installer. The user confirms (or declines) there.
     *
     * The APK is exposed as a content:// uri via the app's FileProvider; the intent grants the
     * installer temporary read on it, and NEW_TASK lets it launch from a non-Activity context.
     */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
