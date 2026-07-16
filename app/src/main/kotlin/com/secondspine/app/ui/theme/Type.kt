package com.secondspine.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * THE TYPE — SPEC §4.9, three families and a rule for each.
 *
 * The tension the whole product rests on is *savage content, premium craft*. Type is where that
 * tension is either sold or lost, because a joke app and a instrument are frequently the same words
 * in different fonts. So the families are split by **who is speaking**, not by size:
 *
 *  - [Display] — Rip. Chunky, condensed, black-weight, tracked tight. Archivo Black class. This is
 *    the font of a man shouting at you from a VHS tape in 1994, and he is the only one allowed it.
 *  - [Ui] — the app. Clean modern sans. Inter class. Every affordance, every label, every word the
 *    *product* says rather than the character. Also the font of the one character break, which is
 *    exactly why the split has to exist: when he drops into UI type for four seconds to tell you
 *    where the emergency exit is, the typeface itself is the tell that he has stopped performing.
 *  - [Mono] — every number, without exception (SPEC §4.9). Numbers in a proportional font are a
 *    claim; numbers in mono are a *measurement*. This app's entire argument is that it measures.
 *
 * **On the font files:** the named faces (Archivo Black / Inter / JetBrains Mono) are not vendored in
 * this repo, so each family resolves through the device's own equivalents — `sans-serif-condensed` at
 * black weight for display, `sans-serif` for UI, `monospace` for numbers. That is a real resolution
 * chain and not a placeholder: it renders correctly on every device at minSdk 29, adds zero KB to an
 * APK budget that SPEC §8.11 fights for, and picks up the user's own font scale. Dropping the three
 * licensed families into `res/font/` later changes these three declarations and nothing else.
 */
object SsFonts {

    /**
     * RIP. `sans-serif-condensed` at black weight is the closest thing the platform ships to Archivo
     * Black, and the condensed axis is load-bearing: infomercial supers are narrow because they had
     * to fit "LEGALLY DISTINCT FROM A DEFIBRILLATOR" onto a 4:3 broadcast-safe title card.
     */
    val Display: FontFamily = FontFamily(
        Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Black),
        Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Bold),
        Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Black),
    )

    /** THE APP. And the one break. */
    val Ui: FontFamily = FontFamily.SansSerif

    /** EVERY NUMBER. No exceptions — SPEC §4.9. */
    val Mono: FontFamily = FontFamily.Monospace
}

// ---------------------------------------------------------------------------
// Rip's voice, sized by register
// ---------------------------------------------------------------------------

/**
 * ARENA. Huge. This is the register the odometer rations to `0.10 × j` and the absolute cap holds to
 * three a week — so it gets to be this loud precisely because it is almost never allowed to happen.
 * A voice this size at every opportunity is noise by week two; at three times a week it is an event.
 */
val ArenaStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Display,
    fontWeight = FontWeight.Black,
    fontSize = 44.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.03).em,
    textAlign = TextAlign.Start,
)

/** PITCHMAN. Loud, gold, selling. The default shape of the man, and the warm register. */
val PitchmanStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Display,
    fontWeight = FontWeight.Black,
    fontSize = 28.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.02).em,
)

/** BIT. Where most of the comedy actually lives, and deliberately not the loudest thing on screen. */
val BitStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Display,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.01).em,
)

/**
 * GHOST. Washed out, and it *shrinks* as he does — the wound showing through as jurisdiction drains.
 * Paired with a heavy tracking overlay at the call site.
 */
val GhostStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Display,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.02.em,
)

/**
 * DISAPPOINTED. Small, grey, quiet — and it fires 0–3 times in ten months (RESOLUTIONS §A2).
 *
 * The size is the joke and the threat at once. Everything else he says is 28sp of gold; this is
 * 13sp of grey, because a man who stops performing to say one flat sentence is the only version of
 * him that is actually frightening. It is UI-adjacent on purpose. It is never mockery.
 */
val DisappointedStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Ui,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.01.em,
)

// ---------------------------------------------------------------------------
// The app's own voice
// ---------------------------------------------------------------------------

/** The demand card's one line. Gold display type — it is him asking, not the app. */
val DemandStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Display,
    fontWeight = FontWeight.Black,
    fontSize = 30.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.02).em,
)

/** Section headers, chips, buttons. The app talking. */
val LabelStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Ui,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.14.em,
)

/** Body, the contract, the safety break. */
val BodyStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Ui,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
)

/** The split-flap Ledger glyphs, the counters, the export age. Mono, always. */
val NumberStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Mono,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 36.sp,
    letterSpacing = 0.sp,
)

/** Mono footers: `LAST EXPORT: 4 DAYS AGO · Documents/SecondSpine`. */
val MonoCaptionStyle: TextStyle = TextStyle(
    fontFamily = SsFonts.Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.04.em,
)

/**
 * Material3's `Typography`, wired so any stray `Text` that forgets to name a style still lands
 * inside the system rather than in Roboto Regular 14. There is no green in a typeface, but there is
 * absolutely a "default Material app" tell, and this is where it would enter.
 */
val SecondSpineTypography: Typography = Typography(
    displayLarge = ArenaStyle,
    displayMedium = PitchmanStyle,
    displaySmall = DemandStyle,
    headlineMedium = BitStyle,
    headlineSmall = GhostStyle,
    titleMedium = LabelStyle,
    bodyLarge = BodyStyle,
    bodyMedium = BodyStyle,
    bodySmall = DisappointedStyle,
    labelLarge = LabelStyle,
    labelMedium = LabelStyle,
    labelSmall = MonoCaptionStyle,
)
