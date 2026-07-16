package com.secondspine.app.ui.archive

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.secondspine.app.ui.theme.InkSunken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * THE PHOTO LOADER — hand-rolled, and deliberately so.
 *
 * There is no image-loading library in this project's dependency list, and adding one would be the
 * wrong instinct rather than a missing line. Coil and Glide exist to solve *network* image loading:
 * connection pooling, disk caching of remote bytes, request cancellation across a scrolling feed of
 * URLs. This app has no network permission and no remote image. Every photograph it will ever render
 * is a file it wrote itself, seconds after the shutter, to `filesDir/proofs/yyyy/MM`.
 *
 * What is actually needed is therefore small enough to read in one sitting: decode a JPEG at a
 * sensible sample size, keep the recent ones in memory, do it off the main thread. That is this file.
 * It also keeps SPEC §8.11's APK budget intact and keeps the offline guarantee provable by
 * inspection rather than by trusting a transitive dependency's fetcher not to exist.
 *
 * **No fade.** SPEC §4.9 is absolute and this is the one place a loader would smuggle one in, because
 * every image library on earth cross-fades by default and calls it polish. A frame that is not
 * decoded yet is [InkSunken] — the gutter colour — and when the bitmap lands it is simply *there*.
 * The archive is a stack of physical frames, and a physical frame does not ease in over 300ms.
 */

/**
 * ~24 MB of decoded thumbnails. At the grid's sample size a frame is well under 100 KB, so this holds
 * several screens' worth of scrollback — which is what makes a 1,400-proof timeline scrub without
 * re-decoding the same month every time the user changes direction.
 *
 * Keyed by path *and* target size: the montage and the grid ask for different sizes of the same file,
 * and a cache that ignored the size would hand a 96px thumbnail to a full-bleed montage cell.
 */
private object ThumbCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) = cache.put(key, value)
}

/**
 * Decode [path] downsampled to roughly [targetPx] on its short edge.
 *
 * `inSampleSize` is powers-of-two only, which is not a limitation here but the point: it is the
 * decoder's fast path, it is exact, and a 4000px camera JPEG lands as a 125px thumbnail in one pass
 * with no intermediate full-size allocation. Decoding 1,400 camera photographs at full size is the
 * one thing that would make this screen unusable at exactly the moment it becomes the product.
 */
private suspend fun decode(path: String, targetPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val key = "$path@$targetPx"
    ThumbCache.get(key)?.let { return@withContext it }

    val file = File(path)
    if (!file.exists()) return@withContext null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val short = minOf(bounds.outWidth, bounds.outHeight)
    if (short <= 0) return@withContext null

    var sample = 1
    while (short / (sample * 2) >= targetPx) sample *= 2

    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(path, opts) ?: return@withContext null
    bitmap.asImageBitmap().also { ThumbCache.put(key, it) }
}

/**
 * One photograph, from his own life.
 *
 * @param contentDescription is deliberately required and deliberately generic at the call site. The
 *   app does not know what is in the picture and must never imply that it does — "your proof from
 *   Tuesday" is true; anything describing the *contents* would be a classifier speaking, and there is
 *   no classifier and no column for one to write to.
 */
@Composable
fun ProofImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    targetPx: Int = 320,
) {
    var bitmap by remember(path, targetPx) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(path, targetPx) {
        bitmap = decode(path, targetPx)
    }

    Box(modifier.background(InkSunken)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Undecoded is the gutter colour and nothing else: no spinner, no shimmer, no skeleton.
        // A shimmer is an app apologising for a disk read that takes 4ms.
    }
}
