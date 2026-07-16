package com.secondspine.app.ui.proof

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondspine.app.ui.theme.BodyStyle
import com.secondspine.app.ui.theme.ForTheRecordButton
import com.secondspine.app.ui.theme.Ink
import com.secondspine.app.ui.theme.InkSunken
import com.secondspine.app.ui.theme.LabelStyle
import com.secondspine.app.ui.theme.Paper
import com.secondspine.app.ui.theme.PaperDim
import com.secondspine.app.ui.theme.PaperFaint
import com.secondspine.app.ui.theme.ProofButton
import com.secondspine.app.ui.theme.RipSpeech
import com.secondspine.app.ui.theme.SsPanel
import com.secondspine.app.ui.theme.SsSectionLabel
import com.secondspine.app.ui.theme.TapeWear
import com.secondspine.app.ui.theme.vhsTracking

/**
 * THE SIGNATURE MOMENT — SPEC §4.4's `shot`.
 *
 * *"No chrome. One band."* That is the entire brief for this screen and it is worth defending against
 * every instinct that will arrive later: no grid overlay, no flash toggle, no lens switcher, no
 * gallery thumbnail in the corner (there is no gallery — see `CameraCapture.kt`), no timer, no
 * countdown, no settings gear. The screen is a viewfinder, one band, one shutter, and the one button
 * that is never priced.
 *
 * ## The two buttons at the bottom, and why only one of them is loud
 *
 * The shutter is gold and 72dp and slams on press. **FOR THE RECORD is paper, full width, and always
 * there** — on this screen, in every phase, including while the verdict is landing and including when
 * the camera permission has been refused and there is no camera at all.
 *
 * That last part is not thoroughness, it is the product. RESOLUTIONS §A1: confession is free,
 * unlimited, warm, never priced, and it never demotes — which makes honesty strictly dominate
 * deception at every hour, for every user, forever. But the arithmetic only holds if the button is
 * *reachable in the second the temptation lands*. The second it is behind a menu, or absent from the
 * screen where the lie would be told, faking wins again — and it wins at 11pm, which is the only hour
 * that matters. **The button must be cheaper than lying. Always. At every hour. Forever.** Two rounds
 * of review caught this being mispriced. It is not mispriced here.
 *
 * ## The band
 *
 * One line. On an ordinary proof it is the demand. On an audit it carries the nonce. When the assist
 * cannot see the thing, it becomes Rip's suspicion — *"I CANNOT SEE IT. CLOSER."* — and that is the
 * only form uncertainty is ever allowed to take. Never a percentage. Never a confidence. Never a
 * model name. A percentage invites an argument with the model; a suspicion invites a better
 * photograph, and the shutter stays live either way, so the argument is one the user always wins by
 * simply pressing the button.
 */
@Composable
fun ProofScreen(
    habitId: String,
    onDone: () -> Unit,
    viewModel: ProofViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    LaunchedEffect(habitId) { viewModel.start(habitId) }
    LaunchedEffect(granted) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        if (granted) {
            CameraSurface(viewModel)
        } else {
            NoEye()
        }

        // Scrim under the band and the controls. The viewfinder is the screen; the chrome sits on it
        // rather than beside it, because a camera with a toolbar is an app and a camera without one
        // is an eye.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Band(state)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // What he says about the shot he just accepted. On the ordinary path this is usually
                // nothing at all, and nothing is correct: `speak()` returning null is a first-class
                // outcome and a coach who comments on every glass of water is a coach with a
                // nine-day half-life.
                state.ripLine?.let { line ->
                    RipSpeech(
                        text = line,
                        register = state.ripRegister,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }

                if (state.phase == ProofPhase.FRAMING && granted) {
                    ProofButton(onClick = viewModel::capture)
                    Spacer(Modifier.height(18.dp))
                }

                // ALWAYS. Every phase. Every hour. Including with no camera permission, including
                // mid-verdict, including on the night he would otherwise lie. See the file header.
                ForTheRecordButton(onClick = { viewModel.forTheRecord(onDone) })
            }
        }

        if (state.phase == ProofPhase.VERDICT) {
            VerdictOverlay(
                stamps = state.stamps,
                ceremony = state.ceremony,
                onSettled = onDone,
            )
        }
    }
}

/**
 * The viewfinder.
 *
 * `PreviewView` in an `AndroidView` because CameraX's preview is a Surface and there is no Compose
 * equivalent that does not go through this. `FILL_CENTER` so the frame reads as an eye rather than as
 * a letterboxed document scanner.
 */
@Composable
private fun CameraSurface(viewModel: ProofViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }.also { previewView = it }
        },
        modifier = Modifier
            .fillMaxSize()
            // He is tape; the world through his eye is not. The wear here is light on purpose — the
            // 1994 aesthetic must never make the viewfinder hard to aim.
            .vhsTracking(TapeWear.Stock, seed = 0.41f),
    )

    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        viewModel.camera.bind(lifecycleOwner, view) { bitmap -> viewModel.onAssistFrame(bitmap) }
    }

    // No `DisposableEffect { camera.release() }` here, deliberately. The ViewModel outlives the
    // composition: releasing on dispose would shut the executors down under a screen that is about to
    // rebind after a rotation or a permission grant, and the rebind would hit a dead pool. CameraX
    // unbinds its own use cases through the LifecycleOwner, and `ProofViewModel.onCleared` owns the
    // teardown. See `ProofCamera.release`.
}

/**
 * THE BAND. One line, and never two.
 *
 * Priority is deliberate: the nonce outranks the demand, and the suspicion outranks both. At any
 * moment there is exactly one thing to know, and the band says that one thing.
 */
@Composable
private fun Band(state: ProofUiState) {
    SsPanel(modifier = Modifier.fillMaxWidth(), wear = TapeWear.Worn) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            when {
                // The assist, in his voice. Not a rejection — the shutter below is live, and pressing
                // it right now works, produces REAL, and opens the lock. This is a man squinting.
                !state.sighted -> {
                    SsSectionLabel("THE EYE")
                    Text(
                        text = "I CANNOT SEE IT. CLOSER.",
                        style = LabelStyle,
                        color = Paper,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                state.nonce != null -> {
                    SsSectionLabel("AUDIT")
                    Text(
                        text = state.nonce.prompt().uppercase(),
                        style = LabelStyle,
                        color = Paper,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                else -> {
                    SsSectionLabel("PROOF")
                    Text(
                        text = state.habitTitle.uppercase(),
                        style = LabelStyle,
                        color = Paper,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * No camera permission — and this is the one screen where the character does not get to perform.
 *
 * He is not put in an error state, and that is a rule rather than a mood: a character who narrates
 * the app's broken plumbing becomes a mascot, and a mascot is a thing you dismiss. Flat UI type, the
 * app's own voice, no gold. And FOR THE RECORD is still on the screen underneath this, because a man
 * who has refused the camera is precisely a man who still needs a free way to tell the truth.
 */
@Composable
private fun NoEye() {
    Box(
        Modifier
            .fillMaxSize()
            .background(InkSunken),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            SsSectionLabel("NO CAMERA")
            Text(
                text = "Proof needs the camera. There is no gallery import and there never will be — " +
                    "that is the point of it.",
                style = BodyStyle,
                color = PaperDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = "FOR THE RECORD still works. It always works.",
                style = BodyStyle,
                color = PaperFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}
