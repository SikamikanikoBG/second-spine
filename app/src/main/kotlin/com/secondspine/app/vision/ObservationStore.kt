package com.secondspine.app.vision

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * "COMPUTE A NUMBER, WRITE IT TO ROOM, SHUT UP UNTIL SUNDAY." — the second clause, honoured with an
 * asterisk I am not going to hide.
 *
 * **It is not written to Room, because there is no column for it and I may not add one.**
 *
 * `Entities.kt` is the data agent's file and it is locked to me. It has no `observation` table, and
 * `proof` has no label, pHash, sighting or page column — and looking at the schema's own header, that
 * is not obviously an oversight: the absences in that file are *load-bearing*, enumerated, and argued
 * for. `proof` has no `accepted` column on purpose. Adding an `is_healthy`-shaped surface to somebody
 * else's schema, from a camera module, at the end of a build, is exactly the 1am edit the whole
 * design is written to prevent. So I did not.
 *
 * The observation therefore lands as a **sidecar JSON next to the frame it describes**, in the same
 * app-private directory, and the trade is worth stating precisely rather than glossing:
 *
 *  - **What is kept.** The data is durable, app-private, never in MediaStore, covered by
 *    `allowBackup=false`, and it travels with the image — including through the SAF export, which is
 *    the copy the user actually owns. The Tape can read it on Sunday. Deleting a proof deletes its
 *    observation with it, which is the correct coupling and is one thing a separate table would have
 *    got wrong by default.
 *  - **What is lost.** It is not queryable. The Tape cannot ask "every DRINK sighting in 28 days"
 *    without walking the directory. For a montage that walks the archive anyway, that is acceptable;
 *    for anything aggregate it is not.
 *  - **The real home** is an `observation(proofId, ...)` table owned by the data agent, keyed to
 *    `proof.id`, with the same no-food guarantee the rest of that schema enforces structurally. That
 *    is a v1.1 note, and it is in my report rather than buried here.
 *
 * The one thing this file must never become is a *judgement* store. Read the fields: labels, a
 * pHash, a line count, a page guess. No verdict, no score, no `accepted`, no `suspicion`. It is
 * evidence for a montage and material for a joke, and if it ever grows a column that decides
 * something, the zero-assertion law has been broken in the one place nobody was looking.
 */
class ObservationStore(context: Context) {

    private val root = File(context.filesDir, "proofs")

    /**
     * Write the sidecar for a banked proof.
     *
     * Fire-and-forget in spirit: this is called *after* `bankProof` has already returned, and it
     * cannot fail the capture because the capture is already done. It swallows its own errors for
     * that reason — a disk hiccup writing archive metadata must never surface to a man who just
     * photographed his kitchen at 06:41 and did what he said he would.
     */
    suspend fun write(proofId: Long, imagePath: String, sighting: Sighting, pHash: Long) =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("proofId", proofId)
                    put("objects", JSONArray().also { arr -> sighting.objects.forEach { arr.put(it.name) } })
                    put("textLines", sighting.textLines)
                    sighting.pageGuess?.let { put("pageGuess", it) }
                    // An ARCHIVE CLUSTERING KEY. Stored as an unsigned-safe hex string because JSON
                    // numbers are doubles and a 64-bit hash does not survive the round trip.
                    // See PerceptualHash.kt: it never accuses, never demotes, never speaks.
                    put("phash", java.lang.Long.toHexString(pHash))
                }
                sidecarFor(imagePath).writeText(json.toString())
            }
            Unit
        }

    /** Read one back. Null when there is none — an older proof, or a write that lost a race with a purge. */
    suspend fun read(imagePath: String): StoredObservation? = withContext(Dispatchers.IO) {
        runCatching {
            val file = sidecarFor(imagePath)
            if (!file.exists()) return@runCatching null
            val json = JSONObject(file.readText())
            val objects = json.optJSONArray("objects")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { ProofObject.valueOf(arr.getString(i)) }.getOrNull()
                }
            } ?: emptyList()
            StoredObservation(
                proofId = json.optLong("proofId"),
                objects = objects,
                textLines = json.optInt("textLines"),
                pageGuess = if (json.has("pageGuess")) json.optInt("pageGuess") else null,
                pHash = json.optString("phash").toULongOrNull(16)?.toLong() ?: 0L,
            )
        }.getOrNull()
    }

    /** Where the frames live: `filesDir/proofs/yyyy/MM`, matching `ProofRow.imagePath`'s contract. */
    fun directoryFor(year: Int, month: Int): File =
        File(root, "%04d/%02d".format(year, month)).apply { mkdirs() }

    private fun sidecarFor(imagePath: String): File = File("$imagePath.json")
}

/** A read-back observation. Evidence. Never a verdict. */
data class StoredObservation(
    val proofId: Long,
    val objects: List<ProofObject>,
    val textLines: Int,
    val pageGuess: Int?,
    val pHash: Long,
)
