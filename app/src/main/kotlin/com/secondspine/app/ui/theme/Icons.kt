package com.secondspine.app.ui.theme

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * THE ICON SET — SPEC §4.9: custom, **2px stroke**, and **no emoji, ever**.
 *
 * The emoji ban is not taste, it is positioning. An emoji is a third party's illustration of a
 * feeling, drawn by a committee, rendered differently on every device, and it is the fastest way to
 * tell a user that nobody designed this screen. This app's entire pitch is that a real coach built
 * it and that the savagery is *crafted*. One 💪 anywhere in the UI retracts that pitch in full.
 *
 * So: eleven icons, all hand-built here, all on the same 24×24 grid, all stroked at exactly 2px,
 * all round-capped. The consistency is the product. A stroke set that is uniformly 2px reads as a
 * *system* — as instrumentation — which is precisely the register the app needs to hold while the
 * character screams. It is the visual half of "savage content, premium craft".
 *
 * They are declared as lazily-built [ImageVector]s so the whole set costs nothing until drawn.
 */
object SsIcons {

    /**
     * THE SHUTTER. The most important affordance in the app — SPEC §4.4's signature moment.
     *
     * A ring and an aperture, not a camera body. The body is a picture of a *device*; the ring is a
     * picture of an *action*, and this button is only ever an action.
     */
    val Shutter: ImageVector by lazy {
        stroked("Shutter") {
            circle(12f, 12f, 9f)
            circle(12f, 12f, 3.5f)
        }
    }

    /**
     * FOR THE RECORD. Free, unlimited, warm, never priced (RESOLUTIONS §A1).
     *
     * A record dot inside brackets — the tape's own vocabulary, and deliberately *not* a warning
     * triangle, a flag, or anything else that pre-judges the tap. The button must be cheaper than
     * lying at every hour forever, and an icon that looks like an admission of guilt raises its
     * price before the user has even pressed it.
     */
    val ForTheRecord: ImageVector by lazy {
        stroked("ForTheRecord") {
            moveTo(7f, 5f); lineTo(4f, 5f); lineTo(4f, 19f); lineTo(7f, 19f)
            moveTo(17f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(17f, 19f)
            circle(12f, 12f, 3.5f)
        }
    }

    /**
     * BREAK GLASS. One tap, instant, always works, never confirmed, never mocked, never counted.
     *
     * A pane with a fracture through it. It is the only icon in the set that is allowed to be
     * *literal*, because this is the one control whose meaning must survive being read at 2am by
     * someone who is not okay. Cleverness here is a defect.
     */
    val BreakGlass: ImageVector by lazy {
        stroked("BreakGlass") {
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
            moveTo(9f, 4f); lineTo(13f, 11f); lineTo(8f, 13f); lineTo(14f, 20f)
        }
    }

    /** THE TAPE. Sunday, 20:00. A cassette: two hubs and a window. */
    val Tape: ImageVector by lazy {
        stroked("Tape") {
            moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(3f, 18f); close()
            circle(8.5f, 12f, 2.5f)
            circle(15.5f, 12f, 2.5f)
            moveTo(8.5f, 14.5f); lineTo(15.5f, 14.5f)
        }
    }

    /** THE LEDGER. The docket. Ruled lines on a card — and it only ever holds 28 days. */
    val Ledger: ImageVector by lazy {
        stroked("Ledger") {
            moveTo(5f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(5f, 21f); close()
            moveTo(9f, 8f); lineTo(15f, 8f)
            moveTo(9f, 12f); lineTo(15f, 12f)
            moveTo(9f, 16f); lineTo(13f, 16f)
        }
    }

    /**
     * THE ARCHIVE. The product at low jurisdiction — ~1,400 photographs of his own life.
     *
     * Stacked frames, not a folder. A folder is somewhere things are filed; a stack of frames is
     * something you *scrub*, which is what this surface is for.
     */
    val Archive: ImageVector by lazy {
        stroked("Archive") {
            moveTo(3f, 7f); lineTo(15f, 7f); lineTo(15f, 19f); lineTo(3f, 19f); close()
            moveTo(7f, 7f); lineTo(7f, 4f); lineTo(19f, 4f); lineTo(19f, 16f); lineTo(15f, 16f)
            moveTo(6f, 15f); lineTo(9f, 12f); lineTo(12f, 15f)
        }
    }

    /** SETTINGS. Where RETIRE RIP has lived since day one. Sliders, because that is what is in there. */
    val Settings: ImageVector by lazy {
        stroked("Settings") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17f); lineTo(20f, 17f)
            circle(9f, 7f, 2f)
            circle(15f, 12f, 2f)
            circle(8f, 17f, 2f)
        }
    }

    /**
     * RIP'S EYE. The phone camera, which is the only eye he has.
     *
     * Not a human eye — a lens with an aperture blade. He has no eyes; he has *your* camera, and the
     * distinction is the entire reason the app can be aggressive without being creepy. He is not
     * watching you. He is waiting for you to show him something.
     */
    val Lens: ImageVector by lazy {
        stroked("Lens") {
            circle(12f, 12f, 8.5f)
            circle(12f, 12f, 4f)
            moveTo(12f, 3.5f); lineTo(12f, 8f)
            moveTo(19.4f, 16.2f); lineTo(15.5f, 14f)
            moveTo(4.6f, 16.2f); lineTo(8.5f, 14f)
        }
    }

    /**
     * VERDICT. The stamp mark. **Not a checkmark in a circle** — that is the wellness-green gesture
     * with the colour removed, and the shape carries the connotation on its own. This is a stamp: a
     * hard bracket slammed onto a page.
     */
    val Stamp: ImageVector by lazy {
        stroked("Stamp") {
            moveTo(6f, 4f); lineTo(4f, 4f); lineTo(4f, 20f); lineTo(6f, 20f)
            moveTo(18f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(18f, 20f)
            moveTo(8f, 12.5f); lineTo(11f, 15.5f); lineTo(16.5f, 9f)
        }
    }

    /** THE FOLD. Below it: the trust ladder, the odometer made legible. A chevron, down. */
    val ChevronDown: ImageVector by lazy {
        stroked("ChevronDown") {
            moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
        }
    }

    /** Back / dismiss. Never a fade, always a slide — so the arrow points where the screen goes. */
    val ChevronLeft: ImageVector by lazy {
        stroked("ChevronLeft") {
            moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f)
        }
    }
}

// ---------------------------------------------------------------------------
// The 2px law, in one place
// ---------------------------------------------------------------------------

/**
 * SPEC §4.9's stroke width, expressed once so it cannot drift.
 *
 * Every icon is built through [stroked] and nothing else, which means the set physically cannot
 * develop a 1.5px outlier at 11pm on a Tuesday. That is the same trick as the missing food column:
 * make the wrong thing unrepresentable rather than discouraged.
 */
private const val STROKE_PX = 2f
private const val GRID = 24f

/**
 * Build a 24×24 icon whose every path is stroked at exactly [STROKE_PX], round-capped, round-joined,
 * and never filled.
 *
 * Tint comes from the call site via `tint`/`ColorFilter`, so [SolidColor] here is white and always
 * white. An icon that hard-codes its own colour is an icon that will eventually be gold in a place
 * where the app, not the character, is speaking — and that split is doctrine (see `Color.kt`).
 */
private fun stroked(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = GRID.dp,
        defaultHeight = GRID.dp,
        viewportWidth = GRID,
        viewportHeight = GRID,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(androidx.compose.ui.graphics.Color.White),
            strokeLineWidth = STROKE_PX,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()

/**
 * A circle, in cubic Béziers, because the vector path grammar has no `circle` verb.
 *
 * 0.5523 is the magic constant: the control-point distance that makes four cubics indistinguishable
 * from a true circle. Hand-rolling this once here is what lets every lens, aperture and record dot
 * in the set share one geometry rather than three slightly different hand-drawn ovals — which is
 * exactly the kind of drift that makes an icon set look homemade.
 */
private const val KAPPA = 0.5523f

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    val k = r * KAPPA
    moveTo(cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    close()
}
