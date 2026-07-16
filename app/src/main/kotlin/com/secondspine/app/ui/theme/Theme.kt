package com.secondspine.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.secondspine.app.BuildConfig

/**
 * THE THEME. Dark only — SPEC §4.9, and it is not a preference.
 *
 * A light mode would be a second visual identity to fund, but that is the small reason. The real one
 * is that the entire premise is a man on a degraded tape in a dark room at 7am, and a tape does not
 * have a light mode. [isSystemInDarkTheme] is deliberately not consulted anywhere in this file; it is
 * imported only so that the next person to reach for it finds this paragraph first.
 *
 * Material3's `ColorScheme` is filled in completely rather than partially, because every unfilled
 * slot falls back to the *baseline Material palette* — which contains purple, and, in
 * `tertiaryContainer`, contains something the eye reads as green. The one hard rule in this product
 * would have shipped broken through a slot nobody named. So: every slot, named, from `PALETTE`.
 */
@Composable
fun SecondSpineTheme(content: @Composable () -> Unit) {
    // The design law, executed. Debug only — this must stop a developer, never a user.
    if (BuildConfig.DEBUG) {
        remember { assertNoGreen(); true }
    }

    CompositionLocalProvider(
        LocalTapeWear provides TapeWear.Stock,
    ) {
        MaterialTheme(
            colorScheme = SecondSpineColors,
            typography = SecondSpineTypography,
            content = content,
        )
    }
}

/**
 * Every Material3 slot, accounted for.
 *
 * Note what is *not* here: there is no success colour, because Material has no such slot and this
 * app would not fill it if it did. Success is `Paper` + `Gold`, expressed by the composables that
 * mean it, not by a token any screen can reach for. `error` is the only alarming colour in the
 * scheme and it is `VerdictRed`, which SPEC §4.9 rations to failure alone — never weight, never
 * food, never a trend.
 */
private val SecondSpineColors = darkColorScheme(
    // The app's own colour. Chrome, focus, affordance. Rip may not touch it.
    primary = Cyan,
    onPrimary = Ink,
    primaryContainer = CyanDim,
    onPrimaryContainer = Cyan,
    inversePrimary = CyanDim,

    // The character's colour. If it is gold, he is talking.
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = GoldDim,
    onSecondaryContainer = Gold,

    // Deliberately not a third accent — a third accent is where a palette starts to leak. Tertiary
    // is paper, i.e. "no accent", so a component that reaches for it gets type colour and nothing.
    tertiary = Paper,
    onTertiary = Ink,
    tertiaryContainer = InkRaised,
    onTertiaryContainer = Paper,

    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = InkRaised,
    onSurfaceVariant = PaperDim,
    surfaceTint = Color.Transparent,   // Material's elevation tint is a purple wash. Not here.
    inverseSurface = Paper,
    inverseOnSurface = Ink,

    surfaceContainerLowest = InkSunken,
    surfaceContainerLow = Ink,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkRaised,
    surfaceContainerHighest = InkRaised,
    surfaceBright = InkRaised,
    surfaceDim = InkSunken,

    error = VerdictRed,
    onError = Paper,
    errorContainer = InkRaised,
    onErrorContainer = VerdictRed,

    outline = Hairline,
    outlineVariant = Hairline,
    scrim = InkSunken,
)

/**
 * How worn the tape is on this branch of the tree.
 *
 * A composition local rather than a parameter because wear is *ambient*: it belongs to the surface a
 * component finds itself on, not to the component. The GHOST speech bubble does not know it is on a
 * failing tape; the tape knows.
 */
val LocalTapeWear = staticCompositionLocalOf { TapeWear.Stock }
