package com.secondspine.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.AppGraph
import com.secondspine.app.data.ConfessionKind
import com.secondspine.app.data.Graph
import com.secondspine.app.enforce.Enforcement
import com.secondspine.app.enforce.ScheduleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * THE SHELL'S TWO FREE BUTTONS.
 *
 * `HomeScreen` takes `onForTheRecord` and `onBreakGlass` as lambdas and both were empty. That is not
 * a cosmetic gap: they are the only two controls in the product that are never counted, never priced
 * and never argued with, and the whole ethical case for everything else the character is allowed to
 * do rests on both of them working on the night they are needed. A stub here is the app charging the
 * user for a promise it does not keep.
 *
 * This class is Activity-scoped rather than per-destination because both actions must survive the
 * back stack, and it holds no state of its own — every call goes straight to a real writer.
 */
class ShellViewModel(app: Application) : AndroidViewModel(app) {

    init {
        // `SecondSpineApp.onCreate` installs only `AppGraph` (the shell's in-memory seam), never the
        // real data graph, and `Graph.db` throws rather than returning null when it has not been
        // installed. Idempotent and synchronized, so calling it here costs one null-check.
        Graph.install(app)
        wireOnce()
    }

    /**
     * FOR THE RECORD, from home.
     *
     * `ProofViewModel.forTheRecord` already exists and is the same call — but it is only reachable
     * once the user is on the camera, and RESOLUTIONS §A1's arithmetic only pays if the button is
     * reachable **in the second the temptation lands**. So home gets a real write rather than a
     * route to a screen with a shutter on it.
     *
     * No dialog, no reason picker, no confirmation, no counter, no cap — see `CoachRepository.confess`.
     * The habit is the open demand's; with no demand open, the first enabled habit, because "he said
     * it happened" is the whole content of the confession and refusing to record one for want of a
     * subject would be the app arguing with an admission.
     */
    fun forTheRecord(habitId: String?) {
        viewModelScope.launch {
            val id = habitId
                ?: runCatching { Graph.repository.habitsNow().firstOrNull()?.id }.getOrNull()
                ?: return@launch
            runCatching { Graph.repository.confess(habitId = id, kind = ConfessionKind.FOR_THE_RECORD) }
        }
    }

    /**
     * BREAK GLASS. First tap, no confirm, and it must work at 2am.
     *
     * Two shapes, because the button means two different things depending on whether the ladder is
     * actually running:
     *  - **Something is live** → `Enforcement.breakGlass` per live challenge, which stops the
     *    escalation *and* records it. Killing the noise is the entire point of the button.
     *  - **Nothing is live** → `CoachRepository.breakGlass`, which records and nothing else. Pressing
     *    it on a quiet Tuesday is allowed and costs nothing.
     *
     * Nothing reads what either one writes (see `BreakGlassDao.kt`) and that is deliberate. The record
     * is our bookkeeping, never the user's obligation.
     */
    fun breakGlass() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val context = getApplication<Application>()
                    val live = ScheduleStore.get(context).live()
                    if (live.isEmpty()) {
                        Graph.repository.breakGlass(reason = REASON)
                    } else {
                        live.forEach { Enforcement.breakGlass(context, it.state.challengeId, REASON) }
                    }
                }
            }
        }
    }

    private companion object {
        const val REASON = "home"

        @Volatile private var wired = false

        /**
         * Point the odometer at the real habit table, once per process.
         *
         * `AppGraph.habits` is a seam with a *fictional* default — two hardcoded ENFORCED habits — and
         * its own doc names [AppGraph.wireHabits] as the data layer's one-call wiring point. Nothing
         * called it, so the trust ladder on home rendered "EXERCISE / WATER" no matter which habits
         * the user actually picked in the intake, and the odometer that drives the register mix, the
         * speech budget and the entire arc was reading a constant.
         *
         * The initial value is the seam's own default rather than `emptyList()` on purpose: an empty
         * list is `jurisdiction = 0`, which is *the ending*, and starting Eagerly with it would flash
         * the last scene of the ten-month story for one frame on every cold start.
         */
        fun wireOnce() {
            if (wired) return
            synchronized(this) {
                if (wired) return
                wired = true
                AppGraph.wireHabits(
                    Graph.repository.habits.stateIn(
                        scope = Graph.appScope,
                        started = SharingStarted.Eagerly,
                        initialValue = AppGraph.habits.value,
                    )
                )
                // The comeback trigger. `AppGraph.comebackDue` gates the start destination in `Nav.kt`
                // and nothing computed it, so the forgiveness screen was unreachable. Wired here, next
                // to the odometer, from the same one-call seam its own doc names. Eager + false initial:
                // a fresh install is not "back", it has not left.
                AppGraph.wireComebackDue(
                    Graph.repository.observeComebackDue().stateIn(
                        scope = Graph.appScope,
                        started = SharingStarted.Eagerly,
                        initialValue = false,
                    )
                )
            }
        }
    }
}
