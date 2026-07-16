package com.secondspine.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * THE PALETTE — SPEC §4.9, and it is a short list on purpose.
 *
 * The hard rule, from the design panel and repeated in the brief: **NO GREEN. ANYWHERE. EVER.**
 * Wellness green files this app mentally next to every app he has already deleted, and that filing
 * happens in the first 200 ms, before a single word of Rip's has had a chance to work. Success is
 * therefore **white + gold on near-black**, which is what a trophy looks like and what a leaderboard
 * looks like and what a 1994 infomercial's "CALL NOW" super looks like — none of which are a salad.
 *
 * The division of ownership matters more than the hexes:
 *  - **The app** owns [Cyan]. Chrome, affordances, focus. Rip may not touch it.
 *  - **The character** owns [Gold] and [VerdictRed]. When gold appears, he is talking.
 *
 * That split is why the app still reads as an instrument when he is screaming: the parts you *use*
 * are never the parts he is allowed to colour. [VerdictRed] is scarce by contract — failure only,
 * never weight, never food, never a trend, never a streak.
 *
 * Enforcement is not left to reviewers: [assertNoGreen] runs at theme construction in debug builds
 * and throws. A design law that only lives in a markdown file is a design law that ships broken.
 */

// --- the ground ------------------------------------------------------------

/** SPEC §4.9 base. Near-black, not black: pure #000 reads as OLED void, this reads as a dark room. */
val Ink = Color(0xFF0B0C0E)

/** One step up. Cards, the demand, anything that must sit *on* the ground rather than in it. */
val InkRaised = Color(0xFF141619)

/** One step down. The letterbox behind the tape, the gutter under the fold. */
val InkSunken = Color(0xFF060708)

/** Hairlines and 2px strokes at rest. Never a fill. */
val Hairline = Color(0xFF23262B)

// --- the type --------------------------------------------------------------

/** SPEC §4.9 paper. Body, numbers, and half of what "success" means here. */
val Paper = Color(0xFFF2F1ED)

/** Secondary type. The DISAPPOINTED register lives at this weight — small, grey, quiet. */
val PaperDim = Color(0xFF8A8983)

/** Tertiary. Captions, mono footers, the export-age line. */
val PaperFaint = Color(0xFF55565A)

// --- the app's colour (Rip may not touch this) -----------------------------

/** SPEC §4.9 Voltage Cyan. App accent: chrome, focus, affordance. Deliberately not a warm colour. */
val Cyan = Color(0xFF00E5FF)

/** Cyan at rest, for 2px strokes that must be present without being loud. */
val CyanDim = Color(0xFF0A4A55)

// --- the character's colours ----------------------------------------------

/** SPEC §4.9 Hogan Gold. Rip's colour. If it is gold, he is talking. Success is white + gold. */
val Gold = Color(0xFFFFB300)

/** Gold burnt down for the GHOST register and for un-lit split-flap segments. */
val GoldDim = Color(0xFF6B4C0B)

/** Gold with the life gone out of it — the ARENA face at jurisdiction 0. */
val GoldGhost = Color(0xFF3A2E14)

/** SPEC §4.9 Verdict Red. **Failure only, and scarce.** Never weight. Never food. Never a trend. */
val VerdictRed = Color(0xFFFF2D2D)

// --- the tape --------------------------------------------------------------

/** The scanline. Black at 5% over everything, every frame. */
val Scanline = Color(0x0D000000)

/** The tracking bar's leading edge — the only pure white in the app, and it lasts ~80 ms. */
val TrackingEdge = Color(0x33FFFFFF)

/** Film grain. SPEC §4.9: 4%. It is what makes a black app read expensive instead of empty. */
val Grain = Color(0x0AF2F1ED)

// --- the law ---------------------------------------------------------------

/**
 * Every colour this app is allowed to render. If it is not here, it does not ship.
 *
 * The list is short because the palette is a *contract*, not a starting point. Any colour added here
 * must survive [assertNoGreen], which is the whole reason the set is enumerable at all.
 */
val PALETTE: List<Pair<String, Color>> = listOf(
    "Ink" to Ink,
    "InkRaised" to InkRaised,
    "InkSunken" to InkSunken,
    "Hairline" to Hairline,
    "Paper" to Paper,
    "PaperDim" to PaperDim,
    "PaperFaint" to PaperFaint,
    "Cyan" to Cyan,
    "CyanDim" to CyanDim,
    "Gold" to Gold,
    "GoldDim" to GoldDim,
    "GoldGhost" to GoldGhost,
    "VerdictRed" to VerdictRed,
)

/**
 * Hues 75°–170° are green. Nothing in [PALETTE] may live there, at any saturation that could read.
 *
 * The saturation floor is deliberate: near-grey colours have a nominally green hue and are harmless,
 * because at 12% saturation nobody has ever thought "wellness". The check targets what the eye
 * actually files, which is a *saturated* green, and it is the only thing it targets.
 */
private const val GREEN_HUE_MIN = 75f
private const val GREEN_HUE_MAX = 170f
private const val GREEN_SATURATION_FLOOR = 0.12f

/**
 * The design law, executable. Called from `SecondSpineTheme` in debug builds only.
 *
 * Throwing here is correct and the alternative is worse: a lint rule can be suppressed, a code
 * review can be tired at 1am, and a markdown file cannot fail a build. The one hard rule that
 * decides whether this app reads as an instrument or as the fourteenth wellness app gets an
 * assertion that stops the process.
 */
fun assertNoGreen(palette: List<Pair<String, Color>> = PALETTE) {
    for ((name, color) in palette) {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255f).toInt(),
            (color.green * 255f).toInt(),
            (color.blue * 255f).toInt(),
            hsv,
        )
        val (hue, saturation, value) = hsv
        val isGreen = hue in GREEN_HUE_MIN..GREEN_HUE_MAX &&
            saturation >= GREEN_SATURATION_FLOOR &&
            value >= GREEN_SATURATION_FLOOR
        check(!isGreen) {
            "NO GREEN ANYWHERE (SPEC §4.9): $name is hue ${hue.toInt()}° sat ${saturation}. " +
                "Success is white + gold on near-black."
        }
    }
}

private operator fun FloatArray.component1(): Float = this[0]
private operator fun FloatArray.component2(): Float = this[1]
private operator fun FloatArray.component3(): Float = this[2]
