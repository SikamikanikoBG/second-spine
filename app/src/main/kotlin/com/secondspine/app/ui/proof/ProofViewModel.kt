package com.secondspine.app.ui.proof

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondspine.app.data.ChallengeState
import com.secondspine.app.data.ConfessionKind
import com.secondspine.app.data.Graph
import com.secondspine.app.vision.ClockBaseline
import com.secondspine.app.vision.NonceState
import com.secondspine.app.vision.ObservationStore
import com.secondspine.app.vision.ProofCamera
import com.secondspine.app.vision.ProofFrame
import com.secondspine.app.vision.ProofNonce
import com.secondspine.app.vision.Sight
import com.secondspine.app.vision.SightTarget
import com.secondspine.app.vision.Sighting
import com.secondspine.app.vision.nonceStateAt
import com.secondspine.app.vision.perceptualHash
import com.secondspine.app.vision.sightTargetFor
import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.FragmentSituation
import com.secondspine.coach.Pillar
import com.secondspine.coach.PlayLedger
import com.secondspine.coach.Register
import com.secondspine.coach.SlotResolver
import com.secondspine.coach.SpeechHistory
import com.secondspine.coach.Trigger
import com.secondspine.coach.jurisdiction
import com.secondspine.coach.speak
import java.util.Calendar
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SHOT → STAMP.
 *
 * The whole of this class is one law expressed as control flow: **there is no path from a captured
 * frame to a refusal.** Look for it. There is no `if (!sighted) return`, no `if (nonce.invalid)
 * reject`, no `if (caught) block`. Every branch in [capture] ends in a banked proof and a stamp. The
 * branches differ only in what gets written down and whether a man who has been dead since 1994 says
 * one word about it on the way past.
 *
 * That is LAW 1, and it is the load-bearing decision that makes coarse on-device vision survivable at
 * all: a model that never claims can never be wrong. A 70% classifier costs nothing here, and the
 * false negative on a truthful night — the #1 rage-uninstall vector in every verification product
 * ever shipped — is not rare, it is *unreachable*, because there is no code that could express it.
 */
class ProofViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ProofUiState())
    val state: StateFlow<ProofUiState> = _state.asStateFlow()

    val camera = ProofCamera(app)
    private val observations = ObservationStore(app)

    private var habitId: String = ""
    private var challengeId: Long? = null
    private var isAudit = false
    private var target: SightTarget = SightTarget.NONE
    private var pillar: Pillar = Pillar.WATER

    /** Sampled the instant the nonce is put in front of him. See [start]. */
    private var issuedAt: ClockBaseline = ClockBaseline.now()

    /**
     * THE VOICE'S MEMORY — process-scoped, and it should not live here forever.
     *
     * `speak()` is pure: history in, choice out. Somebody has to hold that history, and the honest
     * answer is that nobody does yet — there is no `VoiceState` in the shell and no table for it in a
     * schema I may not edit. So it lives here, which is correct for a single screen and *wrong* for
     * the product: the speech budget (`[4→6, 3→5, 2→4, 1→2, 0→0]` volunteered lines a day) is a
     * per-day, per-user budget, and a per-screen copy of it means the budget resets when the user
     * leaves the camera. Rip cannot currently overspend on this screen (he speaks at most once per
     * capture) so nothing is broken today, but the moment a second surface speaks, this must be
     * hoisted to a process-scoped holder and persisted across process death.
     *
     * Flagged in my report rather than left as a comment nobody reads.
     */
    private var speech = SpeechHistory()
    private var plays = PlayLedger()

    /**
     * Open the screen for a habit.
     *
     * `Graph.install` is called defensively because `SecondSpineApp.onCreate` currently installs only
     * `AppGraph` (the shell's in-memory seam) and not the real data graph — and `Graph.db` throws
     * rather than returning null when it has not been installed. The call is idempotent and
     * synchronized, so calling it here is safe and costs one null-check on the common path. This is a
     * seam, not a home: the line belongs in `Application.onCreate`, in a file I do not own.
     */
    fun start(habitId: String) {
        if (this.habitId == habitId) return
        this.habitId = habitId

        viewModelScope.launch {
            withContext(Dispatchers.IO) { Graph.install(getApplication()) }

            val habit = runCatching { Graph.db.habitDao().byId(habitId) }.getOrNull()
            pillar = habit?.pillar ?: Pillar.WATER
            target = sightTargetFor(pillar)

            val challenge = runCatching { Graph.db.challengeDao().openForHabit(habitId) }.getOrNull()
            challengeId = challenge?.id

            // THE NONCE WINDOW OPENS WHEN HE SEES IT, not when the alarm armed it — "minted at request
            // time" means the request as *he* experiences it. A nonce he has not read yet has lost no
            // unpredictability, and starting the 90 s clock at an alarm he slept through would void
            // every audit the ladder ever issues.
            //
            // The nonce CONTENT comes from the persisted challenge rather than being re-minted here,
            // so the band and the archive can never disagree about what was asked.
            val now = System.currentTimeMillis()
            val live = challenge != null && challenge.isAudit && now <= challenge.expiresAt
            val nonce = if (live) ProofNonce.decode(challenge!!.nonce) else null
            isAudit = live && nonce != null
            issuedAt = ClockBaseline.now()

            if (isAudit && challenge != null && challenge.state == ChallengeState.ARMED) {
                runCatching { Graph.db.challengeDao().setState(challenge.id, ChallengeState.ISSUED.name) }
            }

            _state.value = _state.value.copy(
                habitTitle = habit?.title ?: habitId.uppercase(),
                nonce = nonce,
                // Nothing has been looked at yet, and an assist that opens by accusing the user of
                // pointing the camera wrong before the sensor has delivered a frame is worse than no
                // assist. Start quiet.
                sighted = true,
            )
        }
    }

    /**
     * THE FRAMING ASSIST — the one place vision touches the UI, and it happens BEFORE the shutter.
     *
     * SPEC §4.4: *"the inference is spent before the shutter, so the verdict is already decided when
     * he taps. That is what makes it feel instant."* This is that inference. It moves one word on a
     * band. It cannot disable the shutter, it is not stored, and it is never consulted again after the
     * capture — see [capture], which does not read `sighted` at all.
     *
     * Uncertainty reaches the user as **suspicion in Rip's voice** ("I CANNOT SEE IT. CLOSER.") and
     * never as a percentage. The confidence number stops inside `Sight.kt` and does not have a route
     * to this class, let alone to the screen. A percentage invites an argument with the model; a
     * suspicion invites a better photograph.
     */
    suspend fun onAssistFrame(bitmap: Bitmap) {
        if (_state.value.phase != ProofPhase.FRAMING) return
        val sighting = Sight.look(bitmap, target)
        _state.value = _state.value.copy(sighted = sighting.sighted)
    }

    /**
     * THE SHUTTER.
     *
     * Trace every exit. There are four, and all four bank the proof and land the stamp:
     *  1. Ordinary capture → banked, day complete, stamp.
     *  2. Nonce expired (>90 s) → the audit **voids to an ordinary self-report**. Banked, day
     *     complete, stamp. Never failed, never counted, never mentioned.
     *  3. Clock jump → auto-void, identically. Not a catch, no penalty, and Rip is never told: the
     *     honest cause is almost always a timezone or an NTP resync, and per SPEC §5.8's narration bar
     *     a signal must be ~90% accurate before it reaches the character's mouth. "Your clock moved,
     *     therefore you lied" is nowhere near it.
     *  4. BYTE_REPLAY → **the proof still passes**. It banks, the day completes, the stamp lands, and
     *     he says one word. The demotion and the Ledger row are Sunday's business (LAW 2: never accuse
     *     in the moment).
     *
     * The only failure mode is the camera itself not producing a frame, which is treated as "nothing
     * happened" rather than as anything the user did.
     */
    fun capture() {
        if (_state.value.capturing || _state.value.phase != ProofPhase.FRAMING) return
        _state.value = _state.value.copy(capturing = true)

        viewModelScope.launch {
            val frame = camera.capture()
            if (frame == null) {
                // The hardware blinked. That is not his fault and it is not a verdict.
                _state.value = _state.value.copy(capturing = false)
                return@launch
            }
            bank(frame)
        }
    }

    private suspend fun bank(frame: ProofFrame) {
        val nonceState = if (isAudit) {
            nonceStateAt(issuedAt, frame.capturedAtWall, frame.capturedAtElapsed)
        } else {
            NonceState.LIVE
        }

        // A voided audit degrades to an ordinary self-report: we drop the binding, mark the challenge
        // VOIDED, and say nothing. `bankProof` with a null challengeId is the *documented* shape of an
        // unprompted proof — "banking one uninvited is allowed and never punished".
        val bindingId = if (nonceState == NonceState.LIVE) challengeId else null
        if (nonceState != NonceState.LIVE) {
            challengeId?.let {
                runCatching { Graph.db.challengeDao().setState(it, ChallengeState.VOIDED.name) }
            }
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = frame.capturedAtWall }
        val directory = observations.directoryFor(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
        )
        val path = camera.persist(frame, directory)
        if (path == null) {
            // Nowhere to put the frame. Do not bank a row that points at a file that does not exist —
            // the archive is the one asset this product claims compounds and a dangling row corrupts
            // it. Treat as "nothing happened"; he can tap again.
            _state.value = _state.value.copy(capturing = false)
            return
        }

        val banked = runCatching {
            Graph.repository.bankProof(
                habitId = habitId,
                challengeId = bindingId,
                pixelSha256 = frame.pixelSha256,
                imagePath = path,
                capturedAtWall = frame.capturedAtWall,
                capturedAtElapsed = frame.capturedAtElapsed,
            )
        }.getOrNull()

        // ── Everything below here is bookkeeping. The proof is already banked. ──────────────────

        val sighting = runCatching { Sight.look(frame.bitmap, target) }.getOrDefault(Sighting())
        val pHash = runCatching { frame.bitmap.perceptualHash() }.getOrDefault(0L)
        if (banked != null) {
            observations.write(banked.proofId, path, sighting, pHash)
        }
        frame.bitmap.recycle()

        val caught = banked?.caught == true
        val ceremony = caught || isAudit || proofCount() <= CEREMONY_PROOFS

        _state.value = _state.value.copy(
            capturing = false,
            phase = ProofPhase.VERDICT,
            stamps = stampsFor(frame.capturedAtWall, caught),
            ripLine = reactionTo(caught, sighting),
            ripRegister = if (caught) Register.DISAPPOINTED else Register.PITCHMAN,
            ceremony = ceremony,
        )
    }

    /**
     * The three stamps. Notice the third one never changes.
     *
     * `REAL` is stamped on a photograph of a wall, and it is stamped on a byte-for-byte replay of last
     * Tuesday. That is not a bug being tolerated — it is the promise being kept in the one place the
     * user would notice it being broken.
     */
    private fun stampsFor(at: Long, caught: Boolean): List<VerdictStampSpec> {
        val calendar = Calendar.getInstance().apply { timeInMillis = at }
        val time = "%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        return listOf(
            VerdictStampSpec("SHOT", gold = false, tilt = -5f),
            VerdictStampSpec(time, gold = false, tilt = 3f),
            // Gold, because this one is him. And it says REAL either way — including here.
            VerdictStampSpec("REAL", gold = true, tilt = -2f),
        )
    }

    /**
     * What he says. Once, briefly, and often not at all.
     *
     * **CAUGHT is authored and hardcoded, and it must stay that way.** SPEC §5.10 and §4.4 both spend
     * the entire moment on one word — *"Rip accepts the proof, says one word, and the stamp lands:
     * 'Interesting.' Nothing else. Nothing follows in the moment."* It is deliberately not assembled
     * from the bank: the bank's 13 CAUGHT fragments are **Sunday's** material, for the DISAPPOINTED
     * scene where the accusation is actually made, and letting the assembler reach them here would
     * import Sunday's argument into a moment whose entire power is that it refuses to have one.
     *
     * This is also, in ten months, the rarest thing in the app. BYTE_REPLAY fires perhaps twice
     * (RESOLUTIONS §A2) — and a single unadorned word, from a man who has never once been quiet, after
     * an event that cannot be a false positive, is worth more than any line the bank could assemble.
     */
    private fun reactionTo(caught: Boolean, sighting: Sighting): String? {
        if (caught) return "Interesting."

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val j = currentJurisdiction()

        // `assemble()` throws IllegalStateException by design when the bank has OBSERVATIONs for a
        // (register, situation, trigger) but none of them calls back to real history. That assertion
        // is a CI gate aimed at whoever is authoring the bank — it is emphatically not a runtime
        // contract the user's shutter should be held to. Silence is a first-class outcome here
        // (`speak()` returns null all the time and that is not a failure), so a bank that cannot serve
        // this moment produces a quiet man, never a crash on the most important tap in the product.
        val decision = runCatching {
            speak(
                trigger = Trigger.PROOF_LOGGED,
                jurisdiction = j,
                gates = gates(),
                hourOfDay = hour,
                history = speech,
                ledger = plays,
                resolver = resolver(sighting),
                now = System.currentTimeMillis(),
                rng = Random.Default,
                situation = situation(),
                volunteered = false,
            )
        }.getOrNull() ?: return null

        speech = decision.history
        plays = decision.ledger
        return decision.line.text
    }

    /**
     * FOR THE RECORD — one tap, and it is already done.
     *
     * Read the body: there is no dialog, no reason picker, no "are you sure", no confirmation, no
     * counter, and no cap. It writes the confession and leaves. RESOLUTIONS §A1 is the arithmetic that
     * demands this shape — confessed days leave the compliance denominator *entirely*, so honesty
     * strictly dominates deception at every hour, for every user, forever — **but only if the button
     * is reachable in the second the temptation lands.** A confession control behind a menu is a
     * confession control that loses to the fake at 11pm, and the whole product is downstream of that
     * one second.
     *
     * `ConfessionKind.FOR_THE_RECORD` rather than a picker, deliberately. Making him choose between
     * MISSED_IT / DID_NOT_TRY / CANNOT_TODAY at the moment of confessing is asking him to grade his
     * own failure on the way in, which is exactly the internal-stable-global attribution that predicts
     * a lapse becoming a relapse. The label is the attribution. He said it happened. That is enough,
     * and it is all we asked for.
     */
    fun forTheRecord(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { Graph.repository.confess(habitId = habitId, kind = ConfessionKind.FOR_THE_RECORD) }
            onDone()
        }
    }

    private suspend fun proofCount(): Int =
        runCatching { Graph.db.proofDao().recent(CEREMONY_PROOFS + 1).size }.getOrDefault(0)

    private fun currentJurisdiction(): Int =
        runCatching { jurisdiction(com.secondspine.app.AppGraph.habits.value) }.getOrDefault(2)

    private fun gates(): ClinicalGates = com.secondspine.app.AppGraph.gates.value

    private fun situation(): FragmentSituation = when (pillar) {
        Pillar.WATER -> if (isAudit) FragmentSituation.AUDIT else FragmentSituation.WATER
        Pillar.EXERCISE -> if (isAudit) FragmentSituation.AUDIT else FragmentSituation.EXERCISE
        else -> if (isAudit) FragmentSituation.AUDIT else FragmentSituation.ANY
    }

    /**
     * The bridge from real history to a rendered line. The key space is `FRAGMENT_DATA_SLOTS` — a
     * frozen set with no body, no weight and no food field in it, and this resolver could not fill one
     * if it wanted to.
     *
     * Returning null is legitimate and is not an error: it is the `[PURGED]` branch, which the bank
     * calls the single best piece of free material in the app.
     */
    private fun resolver(sighting: Sighting): SlotResolver = SlotResolver { slot ->
        val calendar = Calendar.getInstance()
        when (slot) {
            "time" -> "%d:%02d".format(
                calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it },
                calendar.get(Calendar.MINUTE),
            )
            "habit" -> _state.value.habitTitle.lowercase()
            // His entire visual world, and only ever from the allowlist. There is no arrangement of
            // this line that resolves to a food.
            "object" -> sighting.objects.firstOrNull()?.name?.lowercase()
            else -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
        camera.release()
    }

    private companion object {
        /** SPEC §4.4: full ceremony for the first ~50 proofs, then it is a load screen. */
        const val CEREMONY_PROOFS = 50
    }
}

/** Which half of the signature moment we are in. There is no `REJECTED` and there is no `FAILED`. */
enum class ProofPhase { FRAMING, VERDICT }

/**
 * The proof screen's state.
 *
 * Note the absences, as ever: no confidence, no score, no model name, no `valid`, no `rejected`, no
 * countdown. A ticking clock on a demand manufactures unearned failures on a habit the user actually
 * performed, and an app that calls you a failure on a day you succeeded gets uninstalled that evening
 * and deserves to.
 */
data class ProofUiState(
    val habitTitle: String = "",
    /** Non-null only on the ~15% of proofs that are audited. */
    val nonce: ProofNonce? = null,
    /** The framing assist. Pre-shutter only. Never gates anything. */
    val sighted: Boolean = true,
    val phase: ProofPhase = ProofPhase.FRAMING,
    val capturing: Boolean = false,
    val stamps: List<VerdictStampSpec> = emptyList(),
    val ripLine: String? = null,
    val ripRegister: Register = Register.PITCHMAN,
    val ceremony: Boolean = true,
)
