package com.secondspine.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * THE UPDATE CHECK. One GET against the public GitHub latest-release API, run on IO.
 *
 * This is the only thing in the app that touches the network, and it is deliberately small and
 * defensive: it is called on every launch (see [UpdateViewModel]), it must never throw out to the
 * caller, and it must cost nothing when offline or rate-limited. There is no account, no token, no
 * telemetry — it reads a public JSON document and decides whether a newer APK exists.
 *
 * GitHub 403s a request with no User-Agent, so one is set. The dotted-version comparison lives in
 * [isNewer] and is unit-testable in isolation.
 */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/SikamikanikoBG/second-spine/releases/latest"

    private const val TIMEOUT_MS = 10_000

    /** The outcome of a check. Never an exception. */
    sealed interface Result {
        /** Local build is at or ahead of the latest release, or there is nothing to install. */
        data object UpToDate : Result

        /** A newer release exists and carries a downloadable APK. */
        data class Update(val version: String, val apkUrl: String) : Result

        /** The check could not complete (offline, rate-limited, malformed). Treated as UpToDate. */
        data class Error(val message: String) : Result
    }

    /**
     * Ask GitHub for the latest release and compare it to [currentVersionName].
     *
     * Returns [Result.Update] only when the remote base version is strictly newer AND a `.apk` asset
     * is attached. Any failure — no network, HTTP error, parse failure, missing asset — collapses to
     * [Result.Error] or [Result.UpToDate]; the caller never sees a throw.
     */
    suspend fun latest(currentVersionName: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetch() ?: return@runCatching Result.Error("no response")
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank {
                return@runCatching Result.Error("no tag_name")
            }
            val apkUrl = firstApkAsset(json)
                ?: return@runCatching Result.Error("no apk asset")

            if (isNewer(tag, currentVersionName)) {
                Result.Update(version = tag.removePrefix("v"), apkUrl = apkUrl)
            } else {
                Result.UpToDate
            }
        }.getOrElse { Result.Error(it.message ?: it.javaClass.simpleName) }
    }

    private fun fetch(): String? {
        val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // GitHub 403s without a User-Agent; the Accept header pins the API media type.
            setRequestProperty("User-Agent", "second-spine-updater")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** The first release asset whose name ends in `.apk`, or null. */
    private fun firstApkAsset(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").ifBlank { null }
            }
        }
        return null
    }

    /**
     * Is [remoteTag] a newer release than [localName]?
     *
     * Rules:
     *  - a leading "v" is stripped from either side ("v0.1.1" == "0.1.1").
     *  - the comparison is on the dotted-integer BASE, before any "-" pre-release suffix, component
     *    by component ("0.2.0" > "0.1.1"; "0.10.0" > "0.2.0").
     *  - when the bases are equal, a suffixed/pre-release LOCAL build ("0.1.1-rc1") is OLDER than a
     *    clean remote of the same base ("0.1.1"), so the release upgrades over the rc. A suffixed
     *    remote never counts as newer than a clean local of the same base.
     */
    fun isNewer(remoteTag: String, localName: String): Boolean {
        val remoteBase = base(remoteTag)
        val localBase = base(localName)
        val cmp = compareDotted(remoteBase, localBase)
        if (cmp != 0) return cmp > 0
        // Equal base: only a clean remote beating a pre-release local counts as newer.
        val remotePre = hasSuffix(remoteTag)
        val localPre = hasSuffix(localName)
        return !remotePre && localPre
    }

    private fun base(version: String): List<Int> {
        val cleaned = version.removePrefix("v").removePrefix("V")
        val head = cleaned.substringBefore('-').trim()
        return head.split('.').map { it.toIntOrNull() ?: 0 }
    }

    private fun hasSuffix(version: String): Boolean =
        version.removePrefix("v").removePrefix("V").contains('-')

    /** Lexicographic compare of dotted-integer lists, shorter side zero-padded. */
    private fun compareDotted(a: List<Int>, b: List<Int>): Int {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai.compareTo(bi)
        }
        return 0
    }
}
