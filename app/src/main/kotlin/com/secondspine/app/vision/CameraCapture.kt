package com.secondspine.app.vision

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * CAMERAX, IN-PROCESS, AND THAT IS THE ENTIRE ANTI-FAKE PROPERTY.
 *
 * SPEC §5.8 ranks this first among the real defences and calls it *"structural, not detection — the
 * strongest thing in the section"*. It is worth being exact about why, because the strength is not in
 * anything this file computes. It is in what the app **cannot do**:
 *
 *  - No `ACTION_IMAGE_CAPTURE`. That intent hands control to a camera app and returns a file — and a
 *    file is a thing that can be swapped. There is no swap window here because there is no file until
 *    we have already hashed the pixels.
 *  - No `PhotoPicker`, no `GET_CONTENT`, no gallery.
 *  - **`READ_MEDIA_IMAGES` is not in the manifest**, and the manifest says at length why. Gallery
 *    import is not discouraged, it is *impossible*: the permission is not held, so no future screen,
 *    no library, and nobody at 1am can add a "pick from gallery" affordance without that file changing
 *    in a code review.
 *
 * That absence is what makes RESOLUTIONS §A2 true. BYTE_REPLAY is near-unreachable — it fires perhaps
 * twice in ten months — precisely because a real sensor cannot emit two byte-identical frames and
 * **there is no other way in**. Delete the structural property and the app's one live accusation
 * starts firing on honest nights.
 *
 * So this class binds three use cases and never touches the filesystem for input. Frames go
 * `ImageProxy → decoded buffer → SHA-256 → Room`. The JPEG reaches the disk after it has already been
 * fingerprinted, and nothing ever reads it back to re-verify.
 */
class ProofCamera(private val context: Context) {

    /**
     * TWO EXECUTORS, AND THEY MUST STAY TWO.
     *
     * The shutter and the framing assist do not share a thread. If they did, an assist inference
     * already running when the user taps would sit at the head of the queue and the capture callback
     * would wait behind it — silently spending someone else's milliseconds out of SPEC §4.4's 200 ms
     * budget, on the one interaction the entire product is built around. The assist is a nicety; the
     * shutter is the thesis. They do not queue together.
     */
    private val captureExecutor = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /** The assist's inference runs here, off the analyser thread, so frames are never held hostage. */
    private val assistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    /**
     * Bind preview + capture + analysis to a lifecycle.
     *
     * `CAPTURE_MODE_MINIMIZE_LATENCY` because SPEC §4.4 budgets the whole shot at ~200 ms and the
     * ceremony budget is measured in wall-clock seconds of demanded attention (RESOLUTIONS §A3).
     * Quality here buys nothing: nothing is judged on sharpness, and the archive is a memento rather
     * than an exhibit.
     *
     * **Back camera, always.** Not a preference — the front camera mirrors, and SPEC §5.7 cut
     * handedness from the nonce for exactly that reason. Photographing your own kitchen is the ask.
     *
     * @param onFrame the framing assist. Throttled, best-effort, and consumed ONLY before the shutter.
     *   It can never gate a capture; see `ProofScreen.kt`.
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: suspend (android.graphics.Bitmap) -> Unit,
    ) {
        val cameraProvider = awaitProvider() ?: return
        provider = cameraProvider

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture

        val analysis = ImageAnalysis.Builder()
            // KEEP_ONLY_LATEST: the assist is a glance, not a queue. A backed-up analyser is a
            // preview that lags, and a preview that lags is the app feeling broken at the exact
            // moment it is asking to be trusted.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val busy = AtomicBoolean(false)
        var lastAt = 0L
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            val now = android.os.SystemClock.elapsedRealtime()
            // Throttle hard. Two inferences a second is more than enough to move a word on a band,
            // and the difference in battery over a 10-second audit is the difference between a
            // feature and a complaint.
            if (now - lastAt < ASSIST_INTERVAL_MS || !busy.compareAndSet(false, true)) {
                proxy.close()
                return@setAnalyzer
            }
            lastAt = now

            // Copy out and close IMMEDIATELY, then infer off-thread. `toBitmap()` allocates a new
            // buffer, so the proxy is free the moment we have it — and holding an ImageProxy across
            // an ML Kit inference stalls the camera pipeline (KEEP_ONLY_LATEST can only drop frames
            // it is allowed to deliver) and shows up as a preview that lags exactly while the user
            // is trying to aim.
            val bitmap = runCatching {
                proxy.toBitmap().rotated(proxy.imageInfo.rotationDegrees)
            }.getOrNull()
            proxy.close()

            if (bitmap == null) {
                busy.set(false)
                return@setAnalyzer
            }
            assistScope.launch {
                try {
                    onFrame(bitmap)
                } catch (t: Throwable) {
                    // The assist is a nicety. It is never allowed to be the reason the screen dies.
                    Log.w(TAG, "framing assist frame dropped", t)
                } finally {
                    bitmap.recycle()
                    busy.set(false)
                }
            }
        }

        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
                analysis,
            )
        }.onFailure { Log.e(TAG, "camera bind failed", it) }
    }

    /**
     * THE SHUTTER. Returns a decoded, hashed, upright frame — or null.
     *
     * Both clocks are sampled at the callback rather than at the request, because the interval that
     * matters to the nonce is mint → *shutter*, and a 400 ms capture latency inside a 90 s window is
     * noise we should not attribute to the user in either direction.
     *
     * Null on error, and the caller treats null as "nothing happened" rather than as a failure. There
     * is no state in this app where a camera fault becomes the user's problem.
     */
    suspend fun capture(): ProofFrame? {
        val capture = imageCapture ?: return null
        val proxy = takePicture(capture) ?: return null
        return try {
            withContext(Dispatchers.Default) {
                val jpeg = proxy.toJpegBytes()
                decodeProofFrame(
                    jpeg = jpeg,
                    rotationDegrees = proxy.imageInfo.rotationDegrees,
                    capturedAtWall = System.currentTimeMillis(),
                    capturedAtElapsed = android.os.SystemClock.elapsedRealtime(),
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "capture decode failed", t)
            null
        } finally {
            proxy.close()
        }
    }

    /**
     * Persist the ORIGINAL sensor JPEG.
     *
     * `filesDir/proofs/yyyy/MM`, matching `ProofRow.imagePath`'s stated contract, and **never
     * MediaStore**. This app photographs his home: the frames are app-private, they are not in the
     * gallery, they are not in anyone's cloud, and `allowBackup=false` keeps Android from quietly
     * disagreeing. The only copy that ever leaves is the SAF export the user performs himself.
     */
    suspend fun persist(frame: ProofFrame, directory: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val file = File(directory, "${frame.capturedAtWall}.jpg")
            file.writeBytes(frame.jpeg)
            file.absolutePath
        }.getOrNull()
    }

    /**
     * Tear down. Owned by the ViewModel's `onCleared`, and by nothing else.
     *
     * Specifically NOT called from a `DisposableEffect` in the composable: the ViewModel outlives the
     * composition, so releasing on dispose would shut these executors down under a screen that is
     * about to rebind after a rotation or a permission grant, and the rebind would take a
     * `RejectedExecutionException` on a dead pool. CameraX already unbinds its use cases via the
     * LifecycleOwner; the executors belong to the object that owns the camera.
     */
    fun release() {
        runCatching { provider?.unbindAll() }
        assistScope.cancel()
        captureExecutor.shutdown()
        analysisExecutor.shutdown()
    }

    private suspend fun awaitProvider(): ProcessCameraProvider? = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (cont.isActive) {
                cont.resume(runCatching { future.get() }.getOrNull())
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun takePicture(capture: ImageCapture): ImageProxy? =
        suspendCancellableCoroutine { cont: CancellableContinuation<ImageProxy?> ->
            capture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        if (cont.isActive) cont.resume(image) else image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "takePicture failed", exception)
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }

    private companion object {
        const val TAG = "ProofCamera"

        /** ~2 assists/second. Enough to feel live; cheap enough to be free. */
        const val ASSIST_INTERVAL_MS = 450L
    }
}

/** `ImageCapture` gives JPEG in a single plane. Copy it out before the proxy is closed under us. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
