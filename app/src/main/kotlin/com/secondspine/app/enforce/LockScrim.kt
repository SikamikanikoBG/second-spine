package com.secondspine.app.enforce

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.secondspine.app.LockActivity
import com.secondspine.app.ui.theme.InkSunken

/**
 * THE SCRIM — and it is not decoration. It is the thing that makes R4 legal.
 *
 * SPEC §6.4:
 *
 *   *"IF unlocked/in use : show `TYPE_APPLICATION_OVERLAY` scrim FIRST — **a VISIBLE window is what
 *   grants the BAL exemption on 14/15; holding `SYSTEM_ALERT_WINDOW` is NOT sufficient** →
 *   `startActivity(LockActivity)` is now legal."*
 *
 * ### The trap this file exists to avoid
 *
 * Background activity launch is blocked on modern Android. The intuitive reading — "we hold
 * `SYSTEM_ALERT_WINDOW`, so we can start an Activity" — is **false**: the permission grants the right
 * to *add a window*, and it is the existence of a **visible** window belonging to this app that
 * grants the BAL exemption. So the order is not stylistic. Scrim first, then the Activity, or the
 * Activity does not launch and nothing tells you why.
 *
 * And it fails in the most expensive way possible: during development the app has almost always been
 * in the foreground within the last few seconds, which grants BAL through the *recent foreground*
 * grace instead. So the wrong order passes every test you will run at your desk and dies silently on
 * his phone at 06:41. SPEC §6.4 is blunt about it — *"Get this backwards and it passes testing and
 * dies silently in the field."*
 *
 * ### The ~50 lines of ViewTree owners
 *
 * A `ComposeView` in a raw `WindowManager` window crashes on first composition: Compose resolves its
 * lifecycle, saved-state registry and ViewModel store from the view tree, and a window added through
 * `WindowManager.addView` has no Activity above it to provide any of them. So they are attached by
 * hand. SPEC §6.4 calls this out and notes what it means: *"That code is now load-bearing, because
 * the scrim grants BAL."* It is not boilerplate — if it throws, R4 does not fire.
 */
internal object LockScrim {

    private var view: View? = null
    private var owner: ScrimOwner? = null

    /**
     * Show the scrim, then launch the lock.
     *
     * Deliberately not a suspend function and deliberately not awaiting a frame: `addView` makes the
     * window visible synchronously enough for the BAL check, and the Activity launch follows in the
     * same call. Inserting a `post {}` or a coroutine here would reintroduce the exact race this
     * ordering exists to close.
     */
    fun show(context: Context, challengeId: String) {
        if (!canDrawOverlays(context)) {
            // No overlay permission, so no BAL exemption, so `startActivity` from here is a silent
            // no-op on 14/15. The full-screen intent is the only remaining path — it is the wrong
            // one for an in-use device (it demotes to a heads-up banner), but a banner he can tap is
            // strictly better than a lock that never appears and never explains itself.
            LockNotification.post(context, challengeId)
            return
        }
        addScrim(context)
        runCatching {
            context.startActivity(
                Intent(context, LockActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(LockActivity.EXTRA_CHALLENGE_ID, challengeId)
                },
            )
        }.onFailure {
            // The launch was refused anyway. Take the scrim down — a scrim with no lock on top of it
            // is a black rectangle over a man's phone with no buttons and no explanation, which is
            // the single worst artefact this whole package could produce.
            hide(context)
            LockNotification.post(context, challengeId)
        }
    }

    @Synchronized
    private fun addScrim(context: Context) {
        if (view != null) return
        runCatching {
            val wm = context.getSystemService(WindowManager::class.java)
            val scrimOwner = ScrimOwner().apply { create() }
            val compose = ComposeView(context).apply {
                setViewTreeLifecycleOwner(scrimOwner)
                setViewTreeViewModelStoreOwner(scrimOwner)
                setViewTreeSavedStateRegistryOwner(scrimOwner)
                setContent { Scrim() }
            }
            wm.addView(compose, params())
            scrimOwner.resume()
            view = compose
            owner = scrimOwner
        }
    }

    @Synchronized
    fun hide(context: Context) {
        val current = view ?: return
        runCatching { context.getSystemService(WindowManager::class.java).removeView(current) }
        runCatching { owner?.destroy() }
        view = null
        owner = null
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        },
        // NOT_FOCUSABLE so the scrim never eats key events — including, and this is the one that
        // matters, the ones that get a man to his dialer. The scrim is a visible window that grants
        // BAL; it is not a barrier, and it must not become one. FLAG_SHOW_WHEN_LOCKED so it does not
        // vanish if the keyguard comes up underneath it mid-launch.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun canDrawOverlays(context: Context): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    /**
     * The Settings trip for `SYSTEM_ALERT_WINDOW`. SPEC §6.5: requested **once, at the moment R4 is
     * first opted into** — not in the wizard, and never nagged.
     */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        )
}

/**
 * The scrim itself: near-black, and that is the whole design.
 *
 * It is `InkSunken` rather than pure black because pure black on OLED reads as the screen being off
 * — and a man whose screen appears to have died is a man who holds the power button, which is a
 * reboot, which is an evasion row he did not earn. This reads as a dark room, which is where the
 * whole product lives.
 *
 * No content. The lock is the Activity above this. The scrim's only jobs are to exist, to be visible,
 * and to be black.
 */
@Composable
private fun Scrim() {
    Box(Modifier.fillMaxSize().background(InkSunken))
}

/**
 * `LifecycleOwner` + `SavedStateRegistryOwner` + `ViewModelStoreOwner` for a window with no Activity.
 *
 * SPEC §6.4's "~50 lines". Compose reaches up the view tree for all three of these during its first
 * composition and throws if any is missing — and this window's tree ends at `WindowManager`.
 */
private class ScrimOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun create() {
        // performRestore must happen before the lifecycle passes CREATED, or the registry throws.
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
