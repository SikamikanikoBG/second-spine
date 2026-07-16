package com.secondspine.app.vision

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * pHASH — AN ARCHIVE CLUSTERING KEY. IT NEVER ACCUSES, NEVER DEMOTES, NEVER SPEAKS.
 *
 * Read that twice before touching this file, because everything about a perceptual hash invites the
 * opposite conclusion and the opposite conclusion is the single most expensive mistake available in
 * this codebase.
 *
 * The trap: pHash *looks* like an anti-fake signal. It is not, and the reason it is not is the same
 * reason it is good at its actual job. **It is engineered to be robust.** Robust means "two photos of
 * the same mug on the same counter produce nearly the same hash" — which is exactly what you want
 * from a clustering key, and exactly what makes it useless as evidence of cheating. Real handheld
 * re-shots of the same static counter land at Hamming 10–22. So does a genuine second glass of water
 * on a Tuesday night, photographed from the same seat, by an honest man, doing the thing.
 *
 * RESOLUTIONS §A2 is unambiguous and it is a decision, not a preference: `caught_event.kind =
 * {BYTE_REPLAY}` only. **`FRAME_REPLAY` is deleted.** The arithmetic that killed it: the app has
 * exactly one insinuation, and if it is wired to a robust hash it fires on truthful nights. That is
 * the #1 rage-uninstall vector in every verification product ever shipped, and here it would be
 * bought for nothing — because a man who wants to fake it defeats pHash by taking a *new* photo of
 * the same glass, in four seconds, forever.
 *
 * There is a second, worse cost, and it is the one that decides the file. The app's one true signal
 * is SHA-256, which cannot be wrong. One demonstrated bluff — one night where Rip insinuated and was
 * wrong and the user *knew* he was wrong — contaminates SHA-256 by association. The honest signal is
 * only worth something while nothing next to it is bluffing.
 *
 * So what is it for? **The Tape's montage.** `hashObject < 6` with `hashFrame > 12` means "same glass,
 * different room", which is normal, and good, and is comedy material rather than an allegation:
 * fourteen mugs in fourteen kitchens, cut together, is the archive being beautiful. That is the whole
 * mandate, and this file has no API that serves any other one.
 *
 * The API enforces it. There is no `isReplay()`, no `suspicion()`, no `looksLikeAFake()`, and no
 * threshold constant named for an accusation. [hammingDistance] exists because clustering needs a
 * metric; it returns an Int and it is a neighbour test. If a function that returns a verdict ever
 * appears in this file, the file has been misunderstood.
 */

/**
 * 64-bit DCT perceptual hash.
 *
 * Standard construction, and standard is correct here — an exotic hash would be a worse clustering
 * key and would still be a terrible accuser: 32×32 luma, DCT-II, keep the top-left 8×8 low-frequency
 * block (skipping the DC term, which only carries overall brightness), threshold at the median.
 *
 * Cheap enough to run on the banked frame and never on the preview: this is archive metadata, and
 * archive metadata does not belong in a hot loop.
 */
fun Bitmap.perceptualHash(): Long {
    val small = Bitmap.createScaledBitmap(this, DCT_SIZE, DCT_SIZE, true)
    val luma = Array(DCT_SIZE) { DoubleArray(DCT_SIZE) }
    val pixels = IntArray(DCT_SIZE * DCT_SIZE)
    small.getPixels(pixels, 0, DCT_SIZE, 0, 0, DCT_SIZE, DCT_SIZE)
    if (small != this) small.recycle()

    for (y in 0 until DCT_SIZE) {
        for (x in 0 until DCT_SIZE) {
            val p = pixels[y * DCT_SIZE + x]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma. The camera is not colour-managed and the archive does not care.
            luma[y][x] = 0.299 * r + 0.587 * g + 0.114 * b
        }
    }

    val dct = dct2d(luma)

    // The low-frequency block, minus DC. DC is average brightness: including it makes the hash a
    // light-meter and every photo of the same room at a different hour a "different" scene.
    val values = ArrayList<Double>(KEEP * KEEP - 1)
    for (y in 0 until KEEP) {
        for (x in 0 until KEEP) {
            if (x == 0 && y == 0) continue
            values.add(dct[y][x])
        }
    }
    val median = values.sorted().let { sorted ->
        val mid = sorted.size / 2
        if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    var hash = 0L
    var bit = 0
    for (y in 0 until KEEP) {
        for (x in 0 until KEEP) {
            if (x == 0 && y == 0) continue
            if (dct[y][x] > median) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

/**
 * Bits that differ. A METRIC, NOT A VERDICT.
 *
 * It is here so the Tape can group fourteen photographs of the same mug into one montage cell. It is
 * not here so anything can decide that two nights were the same night. Nothing in this app is
 * permitted to reach that conclusion from this number, and no constant in this file names a
 * threshold at which it would be allowed to.
 */
fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

// --- internals -------------------------------------------------------------

private const val DCT_SIZE = 32
private const val KEEP = 8

/** Separable DCT-II. 32×32 twice is 2×32³ ops — microseconds, once per banked proof. */
private fun dct2d(input: Array<DoubleArray>): Array<DoubleArray> {
    val n = input.size
    val cosTable = Array(n) { u -> DoubleArray(n) { x -> cos((2 * x + 1) * u * Math.PI / (2.0 * n)) } }
    val alpha = DoubleArray(n) { if (it == 0) sqrt(1.0 / n) else sqrt(2.0 / n) }

    val rows = Array(n) { DoubleArray(n) }
    for (y in 0 until n) {
        for (u in 0 until n) {
            var sum = 0.0
            for (x in 0 until n) sum += input[y][x] * cosTable[u][x]
            rows[y][u] = alpha[u] * sum
        }
    }

    val out = Array(n) { DoubleArray(n) }
    for (u in 0 until n) {
        for (v in 0 until n) {
            var sum = 0.0
            for (y in 0 until n) sum += rows[y][u] * cosTable[v][y]
            out[v][u] = alpha[v] * sum
        }
    }
    return out
}
