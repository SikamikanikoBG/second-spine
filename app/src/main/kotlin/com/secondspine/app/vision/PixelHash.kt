package com.secondspine.app.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

/**
 * THE DECODED PIXEL BUFFER, AND THE ONLY ACCUSATION THIS APP IS ALLOWED TO MAKE.
 *
 * `pixelSha256` is the single integrity claim in the product (RESOLUTIONS §A2), and the reason it is
 * the *only* one is that it is the only one that cannot be wrong. A real sensor cannot emit two
 * byte-identical decoded frames: the noise floor alone guarantees it. So a collision is arithmetic,
 * not a hunch — and this app is permitted exactly one live reaction, on exactly the signal that has
 * no false-positive branch to fire on.
 *
 * Everything softer was cut on purpose and must not come back here: pHash (engineered to be robust,
 * so honest re-shots of the same mug collide — see `PerceptualHash.kt`), screen-photo/moiré
 * detection (no stock model, 60–70% against a modern OLED, false-positives on blinds and wicker),
 * accelerometer jitter, ambient light. SPEC §5.8's narration accuracy bar is the rule: **any signal
 * below ~90% verified accuracy may live in a log the user can open and may NEVER be given to the
 * character's mouth.** SHA-256 clears that bar at 100% and nothing else in the app comes close.
 *
 * Note what this file does not do. It does not compare. It does not decide. It computes a number and
 * hands it to `CoachRepository.bankProof`, which banks the proof *first* and checks for a collision
 * *after* — and never un-banks it. Compute a number, write it, shut up until Sunday.
 */

/** The longest edge we decode to. See [decodeProofFrame] for why this is a design decision, not a perf knob. */
const val PROOF_MAX_EDGE: Int = 1600

/**
 * A captured frame, decoded, upright, and bound to two clocks.
 *
 * The two clocks are not redundancy — they are the clock-tamper interlock's whole input. One of them
 * is a setting the user can change and the other is not, and `ClockIntegrity.kt` is the arithmetic
 * that reads the difference between them. A wall clock that disagrees with a monotonic clock is a
 * clock jump, not a liar.
 *
 * @param jpeg the ORIGINAL sensor JPEG, untouched. This is what goes to disk. We persist what the
 *   camera produced rather than a re-encode of our own decode, because the archive is the one asset
 *   this product claims compounds and it should hold the original, not our lossy opinion of it.
 * @param bitmap the decoded buffer — bounded, upright, ARGB_8888. This is what is hashed and what
 *   the vision models see.
 */
data class ProofFrame(
    val jpeg: ByteArray,
    val bitmap: Bitmap,
    val capturedAtWall: Long,
    val capturedAtElapsed: Long,
) {
    /**
     * THE HASH. Computed once, at capture, over [bitmap].
     *
     * It is deliberately **not** a checksum of the file on disk and is never recomputed from it: a
     * JPEG re-decode is lossy and would not reproduce this, and more importantly nothing in this app
     * ever re-verifies a stored proof. The hash exists to be compared against *other captures'*
     * hashes, once, at bank time. It is a capture-time fingerprint, and treating it as a file
     * integrity check would be inventing a second claim out of a primitive that only supports one.
     */
    val pixelSha256: String by lazy { bitmap.pixelSha256() }

    // Bitmap and ByteArray are identity-compared by data class equals(), which is meaningless here
    // and worse than meaningless in a set. Nothing needs frame equality; make that explicit rather
    // than shipping a wrong implementation that compiles.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * SHA-256 over the decoded pixels.
 *
 * Streamed in bands rather than materialised as one buffer: a 12 MP frame is 48 MB of ARGB and
 * `largeHeap` is false, so the obvious `IntArray(width * height)` is an OOM on the app's most
 * latency-visible path. The dimensions are folded in first so that two different framings which
 * somehow produced the same pixel *sequence* still cannot collide — free, and it closes a hole that
 * would otherwise exist only in theory.
 */
fun Bitmap.pixelSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")

    // Dimensions first. A hash over a bare pixel stream is ambiguous across reshapes.
    digest.update(
        byteArrayOf(
            (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
        )
    )

    val w = width
    val h = height
    if (w <= 0 || h <= 0) return digest.digest().toHex()

    val rowsPerBand = max(1, PIXELS_PER_BAND / w)
    val pixels = IntArray(w * rowsPerBand)
    val bytes = ByteArray(w * rowsPerBand * 4)

    var y = 0
    while (y < h) {
        val bandHeight = min(rowsPerBand, h - y)
        getPixels(pixels, 0, w, 0, y, w, bandHeight)
        val count = w * bandHeight
        var b = 0
        for (i in 0 until count) {
            val p = pixels[i]
            bytes[b++] = (p ushr 24).toByte()
            bytes[b++] = (p ushr 16).toByte()
            bytes[b++] = (p ushr 8).toByte()
            bytes[b++] = p.toByte()
        }
        digest.update(bytes, 0, count * 4)
        y += bandHeight
    }
    return digest.digest().toHex()
}

/**
 * Decode the sensor JPEG to an upright, bounded ARGB buffer.
 *
 * The bound is [PROOF_MAX_EDGE] and it is chosen rather than inherited. Two arguments:
 *
 *  1. **It must fit.** `largeHeap=false`, and this decode happens while a camera pipeline, a preview
 *     surface and two ML Kit models are already resident. A full-resolution ARGB decode of a modern
 *     sensor is 40–90 MB and the failure mode is an OOM at the exact moment the user did the thing
 *     the whole app exists to witness.
 *  2. **It costs nothing that matters.** At 1600 px the buffer still carries ~2 M pixels of sensor
 *     noise. The property the hash needs is "a real sensor cannot produce this buffer twice", and
 *     that property is not marginal at 2 M pixels — it is absurd at 2 M pixels. Downscaling does not
 *     move a collision from impossible to unlikely; it moves it from one impossible number to
 *     another.
 *
 * Rotation is applied here, so the hash is over the frame as it is *seen*. That is the honest choice
 * — the buffer we hash is the buffer we showed the models and the buffer the archive represents.
 */
fun decodeProofFrame(
    jpeg: ByteArray,
    rotationDegrees: Int,
    capturedAtWall: Long,
    capturedAtElapsed: Long,
): ProofFrame? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, PROOF_MAX_EDGE)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: return null
    val upright = decoded.rotated(rotationDegrees)

    return ProofFrame(
        jpeg = jpeg,
        bitmap = upright,
        capturedAtWall = capturedAtWall,
        capturedAtElapsed = capturedAtElapsed,
    )
}

/** Largest power-of-two subsample that keeps the long edge at or under [maxEdge]. */
internal fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    var longEdge = max(width, height)
    while (longEdge / 2 >= maxEdge) {
        longEdge /= 2
        sample *= 2
    }
    return sample
}

/** Upright, or the same instance when there is nothing to do. */
internal fun Bitmap.rotated(degrees: Int): Bitmap {
    val normalised = ((degrees % 360) + 360) % 360
    if (normalised == 0) return this
    val matrix = Matrix().apply { postRotate(normalised.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated != this) recycle()
    return rotated
}

private const val PIXELS_PER_BAND = 262_144

internal fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX[v ushr 4])
        out.append(HEX[v and 0x0F])
    }
    return out.toString()
}

private val HEX = "0123456789abcdef".toCharArray()
