package com.secondspine.app

import android.app.Application
import android.content.Context
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Habit
import com.secondspine.coach.Stage
import com.secondspine.coach.Tier
import com.secondspine.coach.jurisdiction
import com.secondspine.app.work.SecondSpineWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * THE APPLICATION.
 *
 * Small on purpose. Everything expensive belongs to the data layer, and the one thing that lives
 * here — [AppOpenLog] — lives here because the alternative is that it lives nowhere, and it living
 * nowhere is a documented hole in the project (RESOLUTIONS §D, "missing instruments").
 */
class SecondSpineApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.install(this)
        // The workers exist and are carefully reasoned (scheduleAll is idempotent, UPDATE-policy),
        // but nothing called them — so the purge never purged, the pipeline never graduated a habit,
        // the Tape never composed and the export never ran. The same unwired-seam class of bug that
        // left the app with no demand: built, verified to compile, never switched on.
        SecondSpineWork.scheduleAll(this)
    }
}

// ---------------------------------------------------------------------------
// The seam
// ---------------------------------------------------------------------------

/**
 * THE SHELL'S SEAM ONTO THE DATA LAYER.
 *
 * The brief nominates `com.secondspine.app.data.Graph` as the data agent's object. It does not exist
 * in the tree yet, and this file is deliberately **not** a squatter on that name: defining
 * `data.Graph` here would either collide with the real one at merge time (a duplicate-class build
 * failure across two agents' work) or, worse, silently win and leave the app running against a
 * shell's in-memory fiction. So this is `AppGraph`, in the shell's own package, and it is a *seam*
 * rather than a stand-in.
 *
 * Two of the three things here are real and final:
 *  - [appOpens] is the kill criterion's instrument, persisted to disk, and it is complete. Nothing
 *    downstream needs to replace it.
 *  - [gates] is the clinical gate the register mix reads. It is defaulted to negative and must be
 *    fed from the intake's screening result the moment that lands; the gate outranks everything
 *    (RESOLUTIONS §B) and a wrong default here mocks a user the screen exists to protect. See the
 *    note on [wireHabits].
 *
 * One is a seam with a working default: [habits], which drives the odometer and therefore the entire
 * IA split. Wiring the real DAO is a single call to [wireHabits] from `Graph`'s init, and this file
 * does not change.
 */
object AppGraph {

    /** Process-scoped. The app-open log outlives any one Activity and must not be cancelled by one. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var appContext: Context

    /** The kill criterion's instrument. Real, durable, done. */
    lateinit var appOpens: AppOpenLog
        private set

    /**
     * The habit table, as the shell sees it.
     *
     * The default is two ENFORCED habits, which is `jurisdiction = 2` — a mid-arc install, and it is
     * the honest default rather than a flattering one. Zero habits would render `jurisdiction = 0`,
     * which is *the ending*: Rip at 15% of the screen, silent, the Tape only. Booting a fresh
     * install straight into the last scene of the ten-month story would be the single funniest bug
     * this codebase could ship, and it would ship the first time somebody ran it before the data
     * layer landed. So the seam defaults to the middle of the arc and says so.
     *
     * `MAX_ENFORCED = 2`, so this is also the maximum the pipeline permits under full enforcement.
     */
    private val _habits = MutableStateFlow(
        listOf(
            Habit(id = "exercise", stage = Stage.ENFORCED, tier = Tier.T3, stageSince = 0L, lockEligible = true),
            Habit(id = "water", stage = Stage.ENFORCED, tier = Tier.T1, stageSince = 0L, lockEligible = false),
        )
    )

    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    /**
     * THE ODOMETER, as a flow. `count(ENFORCED) + count(AUDITED)`, range 0..4.
     *
     * SPEC §4.2: this is the **only** input to the ARENA/ARCHIVE split, the register mix, the speech
     * budget, the Tape's ladder and the ending. No calendar, no `daysSinceInstall`, no month index —
     * anywhere in `ui/`. The arc is therefore falsifiable in CI rather than at month 8.
     */
    val jurisdiction: kotlinx.coroutines.flow.Flow<Int> = habits.map { jurisdiction(it) }

    /**
     * The clinical gates from intake.
     *
     * Defaulted negative because a fresh install has not been screened yet and the intake blocks the
     * app until it has — the gate is never *read* in the un-screened state in a shipped flow. It is
     * mutable through [wireGates] rather than baked, because SCOFF is re-screened quarterly by
     * `ScreeningWorker` and a positive result must take effect on the next composition, permanently,
     * with no in-app override.
     */
    private val _gates = MutableStateFlow(ClinicalGates())
    val gates: StateFlow<ClinicalGates> = _gates.asStateFlow()

    fun install(context: Context) {
        appContext = context.applicationContext
        appOpens = AppOpenLog(File(appContext.filesDir, "app_open.log"), scope)
    }

    /**
     * The data agent's wiring point for the odometer.
     *
     * Call this from `Graph` with `HabitDao.observeStages()` collected into a flow. One call, and
     * every surface downstream — the split, the mix, the budget, the ending — is on real data with
     * no other edit anywhere in `ui/`. That is the whole reason the odometer is one integer.
     */
    fun wireHabits(source: StateFlow<List<Habit>>) {
        scope.launch { source.collect { _habits.value = it } }
    }

    /** The data agent's wiring point for the screening result. See [gates]. */
    fun wireGates(source: StateFlow<ClinicalGates>) {
        scope.launch { source.collect { _gates.value = it } }
    }

    /**
     * `daysSinceLastProof >= 4`, or exiting `STAND_DOWN(SICK|INJURED)`.
     *
     * SPEC §4.6 calls the Comeback the most important surface in the app, and the reason is a piece
     * of arithmetic rather than a piece of kindness: month 8 is reached *through* three bad weeks,
     * not around them, and an adversarial app with no forgiveness mechanic converts its own
     * aggression into churn at the first shock — which is a certainty, not a risk. This boolean is
     * the whole trigger. It is defaulted false because a fresh install has not had four dark days.
     */
    private val _comebackDue = MutableStateFlow(false)
    val comebackDue: StateFlow<Boolean> = _comebackDue.asStateFlow()

    fun wireComebackDue(source: StateFlow<Boolean>) {
        scope.launch { source.collect { _comebackDue.value = it } }
    }

    /**
     * Has the intake been completed? Gates the start destination (see `Nav.kt`).
     *
     * Backed by a marker file rather than by a flag in a database the shell cannot yet see. It is a
     * genuine persisted answer — it survives process death, which is the only property the nav gate
     * actually needs from it — and `Graph` overrides it by pointing [wireIntakeComplete] at the real
     * `intake_state` row when the schema lands.
     */
    private val _intakeComplete = MutableStateFlow(false)
    val intakeComplete: StateFlow<Boolean> = _intakeComplete.asStateFlow()

    fun wireIntakeComplete(source: StateFlow<Boolean>) {
        scope.launch { source.collect { _intakeComplete.value = it } }
    }

    /** Called by the intake's terminal step (the contract) when the last clause is initialled. */
    fun markIntakeComplete() {
        scope.launch {
            File(appContext.filesDir, "intake.done").writeText("1")
            _intakeComplete.value = true
        }
    }

    internal fun loadIntakeState() {
        scope.launch {
            _intakeComplete.value = File(appContext.filesDir, "intake.done").exists()
        }
    }
}

// ---------------------------------------------------------------------------
// THE KILL CRITERION'S INSTRUMENT
// ---------------------------------------------------------------------------

/** How the user got here. The distinction is the entire measurement. */
enum class AppOpenSource {
    /** He opened it himself. **This is the only one that counts.** */
    LAUNCHER,

    /** Rip pulled him in with a notification. */
    NOTIFICATION,

    /** Rip pulled him in with an alarm — i.e. the ladder did this, not the user. */
    ALARM,
}

/**
 * THE APP-OPEN LOG.
 *
 * RESOLUTIONS §D, verbatim: *"The kill criterion has no instrument for its own primary metric. The
 * project pre-commits to die on 'unprompted opens < 1.0/day' and nothing anywhere records an app
 * open. Needs `app_open(at, source)` + a 4-week rolling read. ~30 lines."*
 *
 * This is those thirty lines, and shipping them is not optional bookkeeping — it is what makes the
 * README's kill criterion an actual pre-commitment rather than a pose. A project that writes down
 * the number it will die on, and then cannot measure that number, has written a mission statement.
 * SPEC §1.8 predicts, at length, a repo whose last commit is April; it is predicting this exact
 * failure about somebody else.
 *
 * Why a flat file rather than a Room table: this instrument must be readable when the databases are
 * not — including before the intake exists, after a schema migration fails, and while the archive DB
 * is locked. Its value comes entirely from being uninterrupted for ten months, so it depends on
 * nothing.
 *
 * It is also **not** in the archive DB and never joins to anything. It is a tally of opens with no
 * habit, no proof, no failure, and no user text in it.
 */
class AppOpenLog(
    private val file: File,
    private val scope: CoroutineScope,
) {

    /**
     * Record an open. Fire-and-forget onto IO — this is called from `onResume` and must never be
     * the reason a frame is dropped on the app's most latency-visible path.
     */
    fun record(source: AppOpenSource, at: Long = System.currentTimeMillis()) {
        scope.launch {
            runCatching {
                file.appendText("$at,${source.name}\n")
                pruneIfLarge()
            }
        }
    }

    /** Every open still inside the rolling window. */
    suspend fun opens(now: Long = System.currentTimeMillis()): List<Pair<Long, AppOpenSource>> =
        withContext(Dispatchers.IO) { read(now) }

    /**
     * **THE NUMBER.** Unprompted (i.e. [AppOpenSource.LAUNCHER]) opens per day, over a 4-week rolling
     * window.
     *
     * The kill criterion is `< 1.0`. The denominator is a fixed 28 rather than "days since install"
     * on purpose: a fixed denominator cannot be gamed by a quiet fortnight, and an instrument whose
     * denominator moves is an instrument that will eventually be argued with instead of obeyed.
     */
    suspend fun unpromptedOpensPerDay(now: Long = System.currentTimeMillis()): Double =
        withContext(Dispatchers.IO) {
            val launcher = read(now).count { it.second == AppOpenSource.LAUNCHER }
            launcher.toDouble() / WINDOW_DAYS
        }

    /** True when the project has met the condition it pre-committed to die on. Read it soberly. */
    suspend fun killCriterionMet(now: Long = System.currentTimeMillis()): Boolean =
        unpromptedOpensPerDay(now) < KILL_THRESHOLD_PER_DAY

    private fun read(now: Long): List<Pair<Long, AppOpenSource>> {
        if (!file.exists()) return emptyList()
        val cutoff = now - WINDOW_MS
        return file.readLines().mapNotNull { line ->
            val comma = line.lastIndexOf(',')
            if (comma <= 0) return@mapNotNull null
            val at = line.substring(0, comma).toLongOrNull() ?: return@mapNotNull null
            if (at < cutoff) return@mapNotNull null
            val source = runCatching { AppOpenSource.valueOf(line.substring(comma + 1)) }.getOrNull()
                ?: return@mapNotNull null
            at to source
        }
    }

    /**
     * Keep the file bounded. Rewrites only when it has grown past a window's worth of plausible
     * opens, so the common path is one `appendText` and nothing else.
     */
    private fun pruneIfLarge() {
        if (file.length() < PRUNE_BYTES) return
        val now = System.currentTimeMillis()
        val surviving = read(now)
        file.writeText(surviving.joinToString("\n") { "${it.first},${it.second.name}" } + "\n")
    }

    companion object {
        const val WINDOW_DAYS = 28
        const val WINDOW_MS = WINDOW_DAYS * 86_400_000L

        /** SPEC §1.7. The README says this number out loud, sober. */
        const val KILL_THRESHOLD_PER_DAY = 1.0

        private const val PRUNE_BYTES = 64 * 1024L
    }
}
