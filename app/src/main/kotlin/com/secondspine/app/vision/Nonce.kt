package com.secondspine.app.vision

import java.security.SecureRandom

/**
 * THE NONCE — unforecastable by construction, and it never rejects anything.
 *
 * The nonce is `{objectClass, fingerCount ∈ 1..5}`, minted from `SecureRandom` at the moment the
 * proof is *requested* rather than at the moment it is scheduled. That timing is the entire property:
 * a photograph taken yesterday cannot answer a question that did not exist yesterday. Entropy lives
 * in the timing, not in the bits — five finger counts is 2.3 bits and that is fine, because the
 * attacker is not a cryptographer, he is a tired man at 11pm deciding whether it is easier to
 * photograph a glass or to fake one, and the honest path is already free (`FOR THE RECORD`).
 *
 * ## What this cannot do, said plainly
 *
 * **Nothing automatically counts the fingers.** SPEC §5.7 assigns that to MediaPipe `HandLandmarker`
 * (40 ms, >95% on finger count), and RESOLUTIONS §E cut the extra ML runtimes out of v1 — the
 * MediaPipe native is not a dependency of this module and I have not added one. So the gesture is
 * *photographed*, banked in the frame, and legible to a human on Sunday; it is not machine-verified.
 *
 * That is a smaller loss than it looks, and it is worth being precise about why rather than
 * pretending:
 *  - Under LAW 1 the count could not have gated anything in the moment anyway. Nothing is rejected in
 *    real time, so a verified count and an unverified count produce the *identical* live behaviour:
 *    the shutter works, the proof banks, the stamp lands.
 *  - The frame still contains the answer. The nonce binds this capture to this request whether or not
 *    a model reads it, because the user had to produce an unforecastable gesture *before* the shutter.
 *  - What is genuinely deferred is Sunday's automated commentary on a wrong count — and per
 *    RESOLUTIONS §A2 that commentary could never have demoted anyone regardless. Only BYTE_REPLAY and
 *    collapse demote.
 *
 * `HandLandmarker` is v1.1. Until then this file does not pretend, and neither does the UI: the band
 * asks for the gesture and the archive keeps it.
 *
 * **Handedness is CUT** and must not return. MediaPipe reports it from the image frame and it flips
 * under front-camera mirroring, which would make the app wrong about a thing the user can see it is
 * wrong about — the most expensive kind of wrong there is.
 */

/** SPEC §5.8. Past this, the nonce has no unpredictability value left to spend. */
const val NONCE_WINDOW_MS: Long = 90_000L

/**
 * A minted challenge answer.
 *
 * @param objectClass what to put in frame — always the enrolled object for this habit's pillar, never
 *   a surprise object. Asking for a surprise object is asking the user to go and find something,
 *   which converts a two-second proof into an errand and an errand into an uninstall.
 * @param fingerCount 1..5, held up beside it.
 */
data class ProofNonce(
    val objectClass: String,
    val fingerCount: Int,
) {
    /** The wire form stored in `challenge.nonce`. Deliberately human-readable — it ends up in an export. */
    fun encode(): String = "$objectClass/$fingerCount"

    /** What the band says. UI type, no emoji, no exclamation — the app asks, Rip demands. */
    fun prompt(): String = "$objectClass · $fingerCount ${if (fingerCount == 1) "FINGER" else "FINGERS"}"

    companion object {
        private val rng = SecureRandom()

        /** Minted at request time. `SecureRandom` because a predictable audit is a scheduled audit. */
        fun mint(objectClass: String): ProofNonce =
            ProofNonce(objectClass = objectClass, fingerCount = 1 + rng.nextInt(5))

        /** Null on anything malformed. A nonce we cannot parse voids the audit; it never fails the user. */
        fun decode(raw: String): ProofNonce? {
            val slash = raw.lastIndexOf('/')
            if (slash <= 0) return null
            val count = raw.substring(slash + 1).toIntOrNull() ?: return null
            if (count !in 1..5) return null
            return ProofNonce(raw.substring(0, slash), count)
        }
    }
}

/**
 * The state of a nonce at capture time — and read the value names, because none of them is `FAILED`.
 *
 * There is no failure branch in this enum and there is no arrangement of this module that adds one.
 * A voided audit degrades to an ordinary self-report: the proof banks, the day completes, the stamp
 * lands, and nobody is told anything. SPEC §5.8: *"voided, never failed, never counted, never
 * mentioned. Law 1 holds."*
 */
enum class NonceState {
    /** Inside the window, clocks agree. The capture is bound to the request. */
    LIVE,

    /** > 90 s. The nonce has expired into an ordinary self-report. No penalty. No comment. */
    VOID_EXPIRED,

    /** The wall clock and the monotonic clock disagree. AUTO-VOID. Not a catch. See `ClockIntegrity.kt`. */
    VOID_CLOCK,
}

/**
 * Was this capture inside the nonce's window?
 *
 * Measured on `elapsedRealtime`, not on the wall clock, and that is the whole point of taking both:
 * the wall clock is a setting. If the window were measured on a number the user can edit, the
 * interlock would be advisory. `elapsedRealtime` is monotonic since boot and is not settable from
 * userspace, so this is arithmetic about a duration rather than a question about a date.
 */
fun nonceStateAt(
    issued: ClockBaseline,
    capturedAtWall: Long,
    capturedAtElapsed: Long,
    windowMs: Long = NONCE_WINDOW_MS,
): NonceState = when {
    clockJumped(issued, capturedAtWall, capturedAtElapsed) -> NonceState.VOID_CLOCK
    capturedAtElapsed - issued.elapsed > windowMs -> NonceState.VOID_EXPIRED
    // A capture that appears to precede its own request is a clock jump we already caught above, or a
    // reboot (elapsedRealtime resets to 0). Either way: void, silently. Never a catch.
    capturedAtElapsed < issued.elapsed -> NonceState.VOID_CLOCK
    else -> NonceState.LIVE
}
