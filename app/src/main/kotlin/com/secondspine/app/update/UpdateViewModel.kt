package com.secondspine.app.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * THE UPDATER'S ONE PIECE OF STATE.
 *
 * On init it fires a single check against GitHub; if a newer release exists the UI raises a small
 * dialog. Everything here is safe to run on every cold start — the check never throws, and a failure
 * (offline, rate-limited) just leaves the state Idle with no dialog. There is no polling and no
 * background work; the network is touched exactly at launch and again only if the user taps Update.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    /** The states the update overlay can be in. */
    sealed interface UpdateUiState {
        /** Nothing to show. Either not yet checked, up to date, or dismissed. */
        data object Idle : UpdateUiState

        /** A check is in flight. */
        data object Checking : UpdateUiState

        /** A newer release is available and awaiting the user's tap. */
        data class Available(val version: String, val apkUrl: String) : UpdateUiState

        /** The APK is downloading before the installer hands off. */
        data object Downloading : UpdateUiState

        /** Something went wrong during download; the user may dismiss. */
        data class Error(val message: String) : UpdateUiState
    }

    private val installer = UpdateInstaller(app)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        check()
    }

    private fun check() {
        viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            when (val result = UpdateChecker.latest(BuildConfig.VERSION_NAME)) {
                is UpdateChecker.Result.Update ->
                    _state.value = UpdateUiState.Available(result.version, result.apkUrl)
                // Up to date or a failed check both mean: show nothing. The updater is quiet.
                else -> _state.value = UpdateUiState.Idle
            }
        }
    }

    /**
     * Download the available APK, then hand it to the system installer.
     *
     * Only valid from the [UpdateUiState.Available] state; ignored otherwise. A download failure
     * surfaces as [UpdateUiState.Error]; the successful path ends with the OS installer on screen.
     */
    fun startUpdate() {
        val available = _state.value as? UpdateUiState.Available ?: return
        viewModelScope.launch {
            _state.value = UpdateUiState.Downloading
            runCatching { installer.download(available.apkUrl) }
                .onSuccess { file ->
                    installer.install(file)
                    // Leave the state Idle; the OS installer now owns the interaction.
                    _state.value = UpdateUiState.Idle
                }
                .onFailure {
                    _state.value = UpdateUiState.Error(it.message ?: "download failed")
                }
        }
    }

    /** "Later". Drop the overlay for this process; the next launch will check again. */
    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }
}
