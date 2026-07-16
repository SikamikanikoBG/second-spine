package com.secondspine.app.ui.settings

import com.secondspine.coach.PausedMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * THE TWO CONTROLS THE USER OWNS, AND WHERE THEY LIVE.
 *
 * `data/SettingsStore` is the project's DataStore and it holds the wind-down times, the export
 * bookkeeping, the clinical gates and the lifetime counters. It does **not** hold these two, and
 * this file is not a squatter on that name — it follows the precedent `AppOpenLog` set in `App.kt`:
 * a small, file-backed, dependency-free store for something that must work today and must not break
 * when the real owner arrives.
 *
 * The precedent is worth restating because it is the same argument: a control that does not survive
 * process death is not a control, it is a toggle in a demo. "MUTE THE MAN" that forgets it was
 * pressed the next time the app is cold-started is worse than no mute at all — the user pressed it,
 * the man came back, and now the button is a liar. Same for the flu.
 *
 * **Migration is one function.** When `SettingsStore` grows `mutedThe Man` and `pausedMode` keys,
 * [wire] takes their flows and this file's file-backed defaults become unreachable. Nothing in the
 * screens changes.
 *
 * On the format: two lines of text in `filesDir`. Not a database, because these two values must be
 * readable when the database is not — including from the escalation path, which is the one place that
 * must never fire an alarm at a man who told it he has the flu.
 */
class SettingsControls(
    private val file: File,
    private val scope: CoroutineScope,
) {

    private val _muted = MutableStateFlow(false)

    /**
     * MUTE THE MAN — one tap, always reachable.
     *
     * SPEC §4.9: it "mutes voice, not penalties", and the distinction is the whole ethics of the
     * control. Muting the *penalties* would be a free win button, and a commitment device with a free
     * win button is decoration. Muting the *voice* costs the user nothing he agreed to and costs Rip
     * everything he has — which is the correct direction for a man who is contractually obliged to
     * fire himself.
     *
     * It is therefore not a "notification setting". It is the user removing the character's ability
     * to perform while leaving the contract he signed entirely intact.
     */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _pausedMode = MutableStateFlow<PausedMode?>(null)

    /**
     * SICK · INJURED · TRAVELLING · DELOAD.
     *
     * `PausedMode` is `:coach`'s own enum, and `DeviceContext.pausedMode` is the field the interlocks
     * read. SPEC §6.7 row 14: **manual, uncapped, unpriced.** No cap on how often, no cap on how
     * long, no justification, no "are you sure", no shame before, during, or after.
     *
     * RESOLUTIONS and `Health.kt` are blunt about why this is v1 and not v1.1: *"An app with no 'I
     * have the flu' state gets deleted the first time he has the flu. Everyone forgets this and
     * everyone dies of it."*
     */
    val pausedMode: StateFlow<PausedMode?> = _pausedMode.asStateFlow()

    init {
        scope.launch { load() }
    }

    fun setMuted(value: Boolean) {
        _muted.value = value
        persist()
    }

    /** Null clears it. Clearing is one tap and is never celebrated — see `SettingsScreen`. */
    fun setPausedMode(mode: PausedMode?) {
        _pausedMode.value = mode
        persist()
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching
            for (line in file.readLines()) {
                val (key, raw) = line.split('=', limit = 2).let {
                    if (it.size == 2) it[0] to it[1] else return@runCatching
                }
                when (key) {
                    KEY_MUTED -> _muted.value = raw.toBooleanStrictOrNull() ?: false
                    KEY_PAUSED -> _pausedMode.value =
                        if (raw.isBlank()) null else runCatching { PausedMode.valueOf(raw) }.getOrNull()
                }
            }
        }
    }

    /**
     * Fire-and-forget, and the state flow is updated *before* the write rather than after it.
     *
     * The user tapping MUTE must mute him now, not once a disk write has completed. If the write
     * throws, he is still muted for this process and the failure costs a preference, not the tap.
     * The same reasoning as `CoachRepository.breakGlass`: the record is our bookkeeping, not the
     * user's obligation.
     */
    private fun persist() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    file.writeText(
                        "$KEY_MUTED=${_muted.value}\n" +
                            "$KEY_PAUSED=${_pausedMode.value?.name ?: ""}\n",
                    )
                }
            }
        }
    }

    /** The data agent's migration point. See the class doc. */
    fun wire(muted: StateFlow<Boolean>, paused: StateFlow<PausedMode?>) {
        scope.launch { muted.collect { _muted.value = it } }
        scope.launch { paused.collect { _pausedMode.value = it } }
    }

    private companion object {
        const val KEY_MUTED = "muted"
        const val KEY_PAUSED = "paused"
    }
}
