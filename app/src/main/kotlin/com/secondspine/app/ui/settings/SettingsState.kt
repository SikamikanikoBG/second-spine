package com.secondspine.app.ui.settings

import com.secondspine.coach.PausedMode

/**
 * THE SETTINGS STATE.
 *
 * SPEC §4.1's inventory forgot to list this route, which is a revealing omission: the inventory has
 * `standdown`, `help`, `wiring` and `goodbye` as separate surfaces and no menu that reaches them.
 * They are not four screens. They are one screen with four things on it, and the four things are the
 * entire ethics of the product made operable:
 *
 *  1. **RETIRE RIP** — present from day one. The product announcement, not an easter egg.
 *  2. **MUTE THE MAN** — one tap, always reachable.
 *  3. **THE STAND-DOWN MODES** — "I have the flu", uncapped.
 *  4. **THE ONE BREAK** — the safety explanation.
 *
 * Everything a commitment device does to a person is only legitimate while the exit is genuinely
 * available, and this is the screen where the exits live. That is why it is not a junk drawer.
 *
 * @param daysSinceExport null = never run, which renders loudly red. The app never holds the archive
 *   hostage, and the export footer is the receipt.
 */
data class SettingsState(
    val muted: Boolean = false,
    val pausedMode: PausedMode? = null,
    val daysSinceExport: Int? = null,
    val lastExportFileCount: Int = 0,
)

/**
 * The four modes, as the screen presents them.
 *
 * The copy is flat and mechanical on purpose. Every one of these is a sentence the user is telling
 * the app about his own life, and the app's job is to believe it instantly and say nothing. There is
 * no "how long?", no "are you sure?", no severity picker, no return date, and no cheerful "hope you
 * feel better!" — which would be the app performing sympathy at a man who just wants the alarms off.
 *
 * `DELOAD` is the odd one and is described as strategy rather than mercy, which is `Health.kt`'s own
 * framing: an autoregulator that reads as permission to be lazy gets refused by exactly the kind of
 * person who needs it most.
 */
internal val MODE_COPY: Map<PausedMode, Pair<String, String>> = mapOf(
    PausedMode.SICK to ("SICK" to "Nothing is prescribed and nothing is owed."),
    PausedMode.INJURED to ("INJURED" to "Nothing is prescribed and nothing is owed."),
    PausedMode.TRAVELLING to ("TRAVELLING" to "The Floor, without the demotion."),
    PausedMode.DELOAD to ("DELOAD" to "Reduced volume. This is programming, not a break."),
)
