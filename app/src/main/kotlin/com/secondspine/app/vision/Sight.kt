package com.secondspine.app.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.secondspine.coach.Pillar
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * WHAT THE ONE EYE CAN ACTUALLY SEE.
 *
 * Everything in this file compiles to the same instruction: **compute a number, hand it back, and
 * never decide anything.** No function here returns a verdict, a score, a confidence, a boolean named
 * `valid`, or a reason to reject. `Sighting` is an observation, and the only place it is allowed to
 * touch the UI is *before* the shutter (the framing assist — see `ProofScreen.kt`), where being wrong
 * costs a suspicion the user can answer by stepping closer.
 *
 * After the shutter it is archive metadata and nothing else. That is LAW 1, and LAW 1 is what makes a
 * coarse on-device model survivable at all: a model that never claims can never be wrong, so a 70%
 * classifier costs nothing, and a false negative on a truthful night is structurally unreachable
 * rather than merely rare.
 *
 * ## THE FOOD ALLOWLIST — the clinical guardrail, mechanised
 *
 * **NEVER classify food. No calories. No `is_healthy`. THE DONUT IS ALLOWED.**
 *
 * ML Kit's default labeler carries ~400 general labels and a good number of them are food. SPEC §5.2
 * mechanises the guardrail as a Gradle task that strips food synsets from the loaded label map, so
 * that `bagel`, `pretzel` and `ice_cream` are *not emittable values*. That technique assumes a label
 * map this module owns. It does not own one: the labels are baked inside the bundled ML Kit AAR, and
 * there is no supported way to edit them from a Gradle task.
 *
 * So the guardrail is enforced one layer out, at the only boundary a label can cross to become part
 * of this app — and it is enforced as an **allowlist, never a denylist**:
 *
 *  - [ProofObject] is a closed enum of four values. It is the only way a label leaves this file.
 *  - [LABEL_MAP] is the only construction site for it, and it is an exhaustive whitelist of raw ML Kit
 *    strings. A label not in that map is dropped before it exists.
 *  - There is therefore **no value of any type in this module that can express a food judgement.**
 *
 * That is strictly stronger than the Gradle strip, and it fails in the safe direction. A denylist
 * fails open — someone adds a label, forgets the list, and the model says "Doughnut" on a Tuesday. An
 * allowlist fails *closed*: the worst case is that the framing assist stays quiet, which is a
 * suspicion nobody hears, which costs nothing. And it matches the schema's own logic (`Entities.kt`:
 * *"a flag he wrote he can unwrite at 1am; a column that does not exist he cannot conjure"*). A label
 * the type system cannot carry is a label nobody can conjure either.
 *
 * ## WHAT IS RELIABLE, AND WHAT IS NOT — blunt, per the brief
 *
 * | Target | Engine | Honest verdict |
 * |---|---|---|
 * | Drink present | ML Kit labeler | **Coarse but usable as a nudge.** The general map answers "Drink" for a glass of water and also for a beer. It is presence, not identity, and it is *emphatically not level*: transparent liquid in a transparent glass is a hard segmentation problem, not a small-model problem. Nothing here reads a liquid level and nothing should claim to. |
 * | Book / page | ML Kit Latin text | **The strongest thing in this file.** Text-in-frame is a near-binary question and the recogniser is good at it. |
 * | Dumbbell | — | **Not expressible.** SPEC §5.2, verbatim: the ~400-label map *"has no `dumbbell`"*. I have not invented one. The exercise assist is therefore silent — see [sightTargetFor]. |
 * | Anything about the body | — | **Never.** No pose runtime is a dependency (RESOLUTIONS §E cut them). Rep counting is v1.1 and this file does not gesture at it. |
 *
 * ## THE CYRILLIC PROBLEM — not solved here, and not papered over
 *
 * **ML Kit Text Recognition v2 has no Cyrillic model.** It ships Latin, Chinese, Devanagari, Japanese
 * and Korean, and that is the complete list. The user is Bulgarian.
 *
 * RESOLUTIONS §C files this under "platform lies to delete" and concludes that `tesseract4android` +
 * `bul.traineddata` (~15 MB) is *genuinely required, not a preference* — SPEC §5.5 goes further and
 * argues it into v1 on the grounds that reading proof is build-order item #1 and a v1 that cannot
 * read the books he actually reads validates the thesis on nothing.
 *
 * **I have not added that dependency, and I will not pretend the Latin recogniser covers it.** The
 * brief for this module is explicit on both halves. So:
 *  - [readText] uses the Latin recogniser, which is real and works on his English technical books.
 *  - On a Bulgarian page it returns few or no lines. Under LAW 1 that costs exactly nothing: the
 *    shutter still fires, the proof still banks, the day still completes, and the stamp still lands.
 *    The only thing lost is the page-number heuristic and the framing assist's nudge.
 *  - It is v1.1, it needs `tesseract4android` + `assets/tessdata/bul.traineddata`, and it needs script
 *    auto-detection per photo with the Latin path retained. That is the real work and it is not done.
 *
 * The failure is therefore *quiet and free* rather than *loud and wrong*, which is the only acceptable
 * shape for a gap in this product.
 */

/**
 * The complete set of things this app is permitted to notice.
 *
 * Four values. No `FOOD`, no `DONUT`, no `MEAL`, no `BODY`, no `SCALE`. Not because they are switched
 * off — because they do not exist, and the review that adds one has to add it *here*, in a file whose
 * header explains why it must not.
 */
enum class ProofObject {
    /** A drink, of some kind, in frame. Presence only. Never level. Never contents. Never a verdict. */
    DRINK,

    /** A bottle. Same caveats. */
    BOTTLE,

    /** Coffee — because COFFEE is a pillar with a taper (SPEC §7.4), not because it is a food. */
    COFFEE,

    /** A book. The cheapest and strongest proof in the app. */
    BOOK,
}

/**
 * The only construction site for [ProofObject], and therefore the guardrail.
 *
 * Best-effort against a general ~400-label model, and the mapping is honest about being coarse.
 * Anything not named here is discarded — including every food label ML Kit knows, which is the point.
 */
private val LABEL_MAP: Map<String, ProofObject> = mapOf(
    "Drink" to ProofObject.DRINK,
    "Drinking water" to ProofObject.DRINK,
    "Juice" to ProofObject.DRINK,
    "Bottle" to ProofObject.BOTTLE,
    "Coffee" to ProofObject.COFFEE,
    "Coffee cup" to ProofObject.COFFEE,
    "Book" to ProofObject.BOOK,
    "Publication" to ProofObject.BOOK,
    "Paper" to ProofObject.BOOK,
)

/**
 * What the assist should look for on this screen. There is no `EXERCISE` value and that is deliberate.
 *
 * `Pillar.EXERCISE` maps to [NONE] because nothing in this build can see a rep, and an assist that
 * pretends would be the app bluffing on the one pillar where it is loudest. `Pillar.SMOKING` maps to
 * [NONE] because **absence cannot be photographed** — smoking is a cue log, zero penalties, forever,
 * and the camera has no business being pointed at it. `Pillar.WEIGHT` maps to [NONE] because ML Kit
 * reads a 7-segment LCD at 40–60% and silently drops the decimal (81.5 → 815); he types it.
 */
enum class SightTarget { DRINK, BOOK_PAGE, NONE }

fun sightTargetFor(pillar: Pillar): SightTarget = when (pillar) {
    Pillar.WATER -> SightTarget.DRINK
    Pillar.COFFEE -> SightTarget.DRINK
    Pillar.IDENTITY -> SightTarget.BOOK_PAGE
    // Everything below is honestly outside this build's sight. Silence, not theatre.
    Pillar.EXERCISE -> SightTarget.NONE
    Pillar.SLEEP -> SightTarget.NONE
    Pillar.SMOKING -> SightTarget.NONE
    else -> SightTarget.NONE
}

/**
 * One observation of one frame. Every field is a measurement; none is a judgement.
 *
 * @param sighted whether the target was seen. Consumed by the framing assist BEFORE the shutter and
 *   by nothing else, ever. It is not stored as a verdict, it does not gate the capture, and it is not
 *   an input to compliance. `false` means "I cannot SEE it. Closer." — a suspicion, not a rejection.
 */
data class Sighting(
    val objects: List<ProofObject> = emptyList(),
    val textLines: Int = 0,
    /** Best guess at a page number. Wrong often. Costs nothing (LAW 1). See [guessPageNumber]. */
    val pageGuess: Int? = null,
    val sighted: Boolean = false,
)

/**
 * The vision facade. Two ML Kit clients, held for the process — construction is expensive and these
 * are stateless.
 */
object Sight {

    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val latinText by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * Look at a frame. Never throws: a vision failure must never be the reason a proof does not bank.
     *
     * Every error path returns an empty [Sighting], which reads downstream as "not sighted", which
     * reads in the UI as a suspicion, which the user answers by stepping closer or by simply taking
     * the shot anyway — because the shutter is always live. There is no branch from here to a refusal.
     */
    suspend fun look(bitmap: Bitmap, target: SightTarget): Sighting {
        if (target == SightTarget.NONE) {
            // Nothing to look for. Do not spend the inference, and do not manufacture an opinion:
            // `sighted = true` so the assist stays quiet rather than nagging about a thing this build
            // cannot see. Silence is the honest output.
            return Sighting(sighted = true)
        }
        val image = runCatching { InputImage.fromBitmap(bitmap, 0) }.getOrNull() ?: return Sighting()

        return when (target) {
            SightTarget.DRINK -> {
                val objects = labelObjects(image)
                Sighting(objects = objects, sighted = objects.isNotEmpty())
            }
            SightTarget.BOOK_PAGE -> {
                val text = readText(image)
                val objects = labelObjects(image)
                Sighting(
                    objects = objects,
                    textLines = text.lines,
                    pageGuess = text.pageGuess,
                    // A page fills the frame with text; the labeler's opinion is a bonus, not a gate.
                    sighted = text.lines >= MIN_LINES_FOR_A_PAGE || objects.contains(ProofObject.BOOK),
                )
            }
            SightTarget.NONE -> Sighting(sighted = true)
        }
    }

    /**
     * Object presence, allowlisted.
     *
     * The confidence floor is here and the confidence *number* stops here. It never leaves this
     * function, is never stored, and is never rendered — the brief and SPEC §4.4 agree: no confidence
     * score ever appears in the UI. A percentage invites an argument with the model; a suspicion
     * invites a better photograph.
     */
    private suspend fun labelObjects(image: InputImage): List<ProofObject> =
        runCatching {
            labeler.process(image).await()
                .filter { it.confidence >= LABEL_FLOOR }
                .mapNotNull { LABEL_MAP[it.text] }
                .distinct()
        }.getOrDefault(emptyList())

    private suspend fun readText(image: InputImage): TextReading =
        runCatching {
            val result = latinText.process(image).await()
            val lines = result.textBlocks.sumOf { it.lines.size }
            TextReading(lines = lines, pageGuess = guessPageNumber(result))
        }.getOrDefault(TextReading())

    private data class TextReading(val lines: Int = 0, val pageGuess: Int? = null)
}

/**
 * PAGE NUMBER — a heuristic that fails often, and that is priced in.
 *
 * SPEC §5.3 is explicit that this is a disambiguation problem rather than an OCR problem: chapter
 * numbers, footnote markers, years and ISBN fragments are all short integers on a page. The heuristic
 * it settles on is *"smallest bbox nearest a margin, position-consistent across the session"*, and
 * this implements the first half — the smallest standalone integer sitting in the top or bottom
 * margin band.
 *
 * The session-consistency half is not implemented. It needs a per-book positional prior across
 * captures, and the honest place for that is the reading-session code that owns `book_id`, not the
 * camera. Without it this is roughly as good as it sounds, which is: often right, regularly wrong.
 *
 * **It costs nothing when it is wrong.** Nothing is gated on it. It is banked to the sidecar and read
 * on Sunday by a pages-per-minute band (0.3–2.5) that is itself a comedy input rather than a gate,
 * and the novelty test SPEC §5.3 specifies is a shingled Jaccard over the *text*, not an exact hash —
 * precisely because OCR is nondeterministic across captures and hashing fails on the first honest
 * retry. There is no `UNIQUE` constraint anywhere near this, which SPEC calls what it is: *"a
 * false-positive generator wearing a security badge"*.
 */
internal fun guessPageNumber(text: com.google.mlkit.vision.text.Text): Int? {
    var best: Pair<Int, Int>? = null      // value to bbox area
    val frameHeight = text.textBlocks.mapNotNull { it.boundingBox?.bottom }.maxOrNull() ?: return null
    if (frameHeight <= 0) return null

    for (block in text.textBlocks) {
        for (line in block.lines) {
            val raw = line.text.trim()
            val value = raw.toIntOrNull() ?: continue
            if (value !in 1..PAGE_MAX) continue
            val box = line.boundingBox ?: continue

            // Margin band: the top or bottom ~12% of what we can see of the page.
            val margin = frameHeight * PAGE_MARGIN_BAND
            val inMargin = box.top <= margin || box.bottom >= frameHeight - margin
            if (!inMargin) continue

            val area = box.width() * box.height()
            if (best == null || area < best.second) best = value to area
        }
    }
    return best?.first
}

/**
 * Await a Play Services `Task` without pulling in `kotlinx-coroutines-play-services`.
 *
 * Deliberately resolves the failure case to `null` rather than rethrowing, at the one call site that
 * matters: a model that fell over must degrade to "saw nothing", which degrades to a suspicion,
 * which degrades to nothing at all. It must never propagate an exception up a path whose next step is
 * banking the user's proof.
 */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
        addOnFailureListener { error -> if (cont.isActive) cont.cancel(error) }
        addOnCanceledListener { if (cont.isActive) cont.cancel() }
    }

/** ML Kit's own default is 0.5; this is a nudge's floor, not a gate's, and nothing downstream sees it. */
private const val LABEL_FLOOR = 0.55f

/** Enough lines that a page is plausibly filling the frame. Not a threshold anyone is judged against. */
private const val MIN_LINES_FOR_A_PAGE = 3

private const val PAGE_MAX = 2000
private const val PAGE_MARGIN_BAND = 0.12f
