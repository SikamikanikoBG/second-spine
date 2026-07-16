package com.secondspine.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.data.Graph
import com.secondspine.app.data.StageTransitionRow
import com.secondspine.app.enforce.Enforcement
import com.secondspine.app.export.ExportStatus
import com.secondspine.app.export.Exporter
import com.secondspine.coach.PausedMode
import com.secondspine.coach.Stage
import com.secondspine.coach.TransitionReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * THE SETTINGS STATE, PRODUCED.
 *
 * `SettingsState` and `SettingsControls` both already existed and nothing anywhere built either one —
 * the screen with the exits on it had no state producer, so RETIRE RIP, MUTE THE MAN and the
 * stand-down modes were four controls with no wire behind them. That is the specific failure this
 * class exists to end: SPEC's whole defence of an adversarial coach is that the exit is genuinely
 * available, and an exit that is drawn but not connected is worse than no exit, because the user
 * pressed it and stayed.
 *
 * It is shared by `SettingsScreen` and `GoodbyeScreen`, which is why the mute/paused store is a
 * process-scoped singleton rather than a field: those two are different back-stack entries and
 * therefore different `ViewModelStoreOwner`s, and a per-destination copy of the mute flag would mean
 * the goodbye screen disagreed with the settings screen about whether the man is muted.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    init {
        // See ShellViewModel: `SecondSpineApp.onCreate` never installs the real data graph.
        Graph.install(app)
    }

    private val controls = SettingsControlsHolder.get(app)

    /**
     * `daysSinceExport` is null when the export has **never successfully run**, and `ExportFooter`
     * renders that loudly ("LAST EXPORT: NEVER · EXPORT IS BROKEN"). NO_FOLDER maps to null too, and
     * that is the honest reading rather than a pedantic one: no folder means nothing has left, and
     * `ExportStatus.health` already counts it as failing for exactly that reason.
     */
    val state: StateFlow<SettingsState> = combine(
        controls.muted,
        controls.pausedMode,
        ExportStatus.observe(app),
    ) { muted, paused, health ->
        SettingsState(
            muted = muted,
            pausedMode = paused,
            daysSinceExport = when (health.state) {
                ExportStatus.State.NEVER_RUN, ExportStatus.State.NO_FOLDER -> null
                else -> health.daysSince
            },
            lastExportFileCount = health.filesInArchive,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    /** Mutes the voice, never the penalties. See `SettingsControls.muted`. */
    fun setMuted(value: Boolean) = controls.setMuted(value)

    /**
     * SICK · INJURED · TRAVELLING · DELOAD, and null clears it.
     *
     * Written to **both** stores, and the second one is not redundant. `SettingsControls` is what the
     * screen renders; `Enforcement.setPausedMode` is what the escalation ladder actually reads
     * (`DeviceContextReader` pulls `BootKeys.PAUSED_MODE` out of device-protected storage). Writing
     * only the first would give the user a screen that says SICK and an alarm that goes off anyway,
     * which is the one failure this control cannot have.
     */
    fun setPausedMode(mode: PausedMode?) {
        controls.setPausedMode(mode)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { Enforcement.setPausedMode(getApplication(), mode) }
            }
        }
    }

    /** The export, on demand. Fire-and-forget: `Exporter` holds its own mutex and its own run log. */
    fun exportNow() {
        viewModelScope.launch {
            runCatching { Exporter.export(getApplication()) }
        }
    }

    /**
     * RETIRE RIP — the real one.
     *
     * Every habit goes to `Stage.RETIRED` with a `USER_RETIRED` transition row, every habit is
     * disabled, and the man is muted. That is not a gesture: `jurisdiction()` counts ENFORCED +
     * AUDITED, so this drops the odometer to **0**, and 0 is the ending the whole product is
     * pointed at — a 40px face, no voice, and an archive that is entirely the user's. The screen he
     * lands on is home, and home renders that ending from the same integer it renders everything
     * else from. Nothing is deleted, because the archive was never the app's to take away.
     *
     * `USER_RETIRED` is written as a transition rather than a silent stage flip so the archive
     * manifest can say what happened and when. It is the one stage move that is not the pipeline's.
     */
    fun retire(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    Graph.db.habitDao().all().forEach { row ->
                        if (row.stage != Stage.RETIRED) {
                            Graph.db.stageTransitionDao().insert(
                                StageTransitionRow(
                                    habitId = row.id,
                                    from = row.stage,
                                    to = Stage.RETIRED,
                                    reason = TransitionReason.USER_RETIRED,
                                    at = now,
                                )
                            )
                            Graph.db.habitDao().setStage(row.id, Stage.RETIRED.name, now)
                        }
                        Graph.db.habitDao().setEnabled(row.id, false)
                    }
                }
            }
            // He is retired whether or not the write landed. A failed disk write must not be the
            // reason a man who pressed RETIRE HIM still has a coach.
            controls.setMuted(true)
            onDone()
        }
    }
}

/**
 * The one [SettingsControls] in the process.
 *
 * Scoped to `Graph.appScope` rather than to a `viewModelScope`: the mute flag and the flu are read by
 * the escalation path, which outlives every ViewModel in this app, and a store whose writes are
 * cancelled when the user leaves the settings screen is a store that loses the tap that mattered.
 */
internal object SettingsControlsHolder {

    @Volatile private var instance: SettingsControls? = null

    fun get(context: Context): SettingsControls =
        instance ?: synchronized(this) {
            instance ?: SettingsControls(
                file = File(context.applicationContext.filesDir, "controls.txt"),
                scope = Graph.appScope,
            ).also { instance = it }
        }
}
