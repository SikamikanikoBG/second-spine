package com.secondspine.app.ui.intake

/**
 * THE TWO VALIDATED INSTRUMENTS, VERBATIM.
 *
 * This file has one rule and it is the whole reason the file exists separately from the screens that
 * render it: **the item text is not ours to edit.** SCOFF and PAR-Q+ are validated against their own
 * wording. The moment somebody shortens item 3 to fit a phone, or rewrites item 6 "in voice", or
 * drops the parenthetical from item 3 of the PAR-Q+ because it looks like a footnote, the instrument
 * stops being the instrument and becomes five questions we made up that resemble it. Its published
 * sensitivity no longer describes the thing we shipped, and the gate it drives is then protecting
 * nobody while claiming to.
 *
 * So the strings live here, in one file, unwrapped and unedited, and the screens are forbidden from
 * doing anything to them but rendering them. `IntakeCopy.kt` holds every word Rip is allowed to say;
 * this holds the words he is not allowed anywhere near.
 *
 * **Rip does not appear on either of these screens.** Not a heckle, not an aside, not a gold pixel.
 * A joke inside a clinical screen tells the reader the screen is a bit, and a reader who thinks the
 * screen is a bit answers it like a bit. The contrast — a savage app that goes completely flat and
 * completely honest for ninety seconds — is the craft, and it is also just the standard of care.
 */

/** One item of an instrument. [hint] is the instrument's own parenthetical, never our commentary. */
data class ScreenItem(
    val id: String,
    val text: String,
    /** Verbatim clarifying text from the instrument itself. Never an editorial note. */
    val hint: String? = null,
)

// ---------------------------------------------------------------------------
// SCOFF — 5 items
// ---------------------------------------------------------------------------

/**
 * SCOFF (Morgan, Reid & Lacey, 1999), verbatim.
 *
 * The mnemonic is the instrument: **S**ick, **C**ontrol, **O**ne stone, **F**at, **F**ood. The
 * capitals in the published items are load-bearing and are preserved.
 *
 * WHY THIS SCREEN IS IN AN APP ABOUT PRESS-UPS. This app's proposition is an aggressive coach with a
 * camera that mocks you for missing things. Point that at a person with an eating disorder and the
 * app is not a comedy app any more, it is an accelerant — the mocking register is the exact voice of
 * the illness, and hearing it from a product he installed for fun is worse than hearing it from
 * himself. So the screen runs before the character ever gets jurisdiction, and its result is
 * permanent and unappealable.
 *
 * KNOWN LIMITATION, STATED RATHER THAN HIDDEN: SCOFF was validated on young women and under-detects
 * in men — it misses muscularity-oriented presentations, which is arguably the only ED presentation
 * this app could plausibly *cause*. SPEC §4.8 pairs it with an MDDI-lite for exactly that reason and
 * RESOLUTIONS §E cuts MDDI from v1. That cut is a real hole and it is on the record here rather than
 * in a commit message: v1 ships SCOFF alone, and v1's compensation is that the entire scale and
 * measurement feature set it would have gated **does not exist in v1 either** (there is no food
 * column, no goal weight, no BMI, and weight is an EWMA trend that nothing may score). The gate this
 * file drives is therefore doing less work in v1 than in the ten-month plan, because there is less
 * to gate.
 */
val SCOFF_ITEMS: List<ScreenItem> = listOf(
    ScreenItem(
        id = "scoff_sick",
        text = "Do you make yourself Sick because you feel uncomfortably full?",
    ),
    ScreenItem(
        id = "scoff_control",
        text = "Do you worry you have lost Control over how much you eat?",
    ),
    ScreenItem(
        id = "scoff_one_stone",
        text = "Have you recently lost more than One stone (6.35 kg) in a three-month period?",
    ),
    ScreenItem(
        id = "scoff_fat",
        text = "Do you believe yourself to be Fat when others say you are too thin?",
    ),
    ScreenItem(
        id = "scoff_food",
        text = "Would you say that Food dominates your life?",
    ),
)

/** The published threshold. Two or more. It is not a tuning parameter and there is no slider for it. */
const val SCOFF_POSITIVE_THRESHOLD: Int = 2

/**
 * The gate. `>= 2` yes-answers.
 *
 * Note the direction of the default: [answers] holds only the items actually answered, and an
 * unanswered item counts as no. That is the flattering direction, and it is safe here **only**
 * because the screen refuses to advance until all five are answered. If that ever stops being true,
 * this function is wrong and the screen is the thing to fix, not this.
 */
fun scoffPositive(answers: Map<String, Boolean>): Boolean =
    SCOFF_ITEMS.count { answers[it.id] == true } >= SCOFF_POSITIVE_THRESHOLD

// ---------------------------------------------------------------------------
// PAR-Q+ — 7 items
// ---------------------------------------------------------------------------

/**
 * The PAR-Q+ (2020) General Health Questions, all seven, verbatim.
 *
 * This is the standard of care before prescribing exercise to a stranger, it is free, and there is no
 * excuse for an app that tells people to do sets not running it. It costs seven taps.
 *
 * The published instrument asks the reader to *list* conditions and medications on items 4 and 5.
 * v1 has nowhere to put a list and no clinician to read it, so those items are asked as written and
 * the list is not collected — a text box whose contents nothing reads is worse than no text box: it
 * implies a clinical review that is not happening. What the yes actually does is gate the
 * prescription and point at a doctor, which is the only honest thing a phone can do with the answer.
 */
val PARQ_ITEMS: List<ScreenItem> = listOf(
    ScreenItem(
        id = "parq_1",
        text = "Has your doctor ever said that you have a heart condition OR high blood pressure?",
    ),
    ScreenItem(
        id = "parq_2",
        text = "Do you feel pain in your chest at rest, during your daily activities of living, OR " +
            "when you do physical activity?",
    ),
    ScreenItem(
        id = "parq_3",
        text = "Do you lose balance because of dizziness OR have you lost consciousness in the last " +
            "12 months?",
        hint = "Please answer NO if your dizziness was associated with over-breathing (including " +
            "during vigorous exercise).",
    ),
    ScreenItem(
        id = "parq_4",
        text = "Have you ever been diagnosed with another chronic medical condition (other than " +
            "heart disease or high blood pressure)?",
    ),
    ScreenItem(
        id = "parq_5",
        text = "Are you currently taking prescribed medications for a chronic medical condition?",
    ),
    ScreenItem(
        id = "parq_6",
        text = "Do you currently have (or have had within the past 12 months) a bone, joint, or soft " +
            "tissue (muscle, ligament, or tendon) problem that could be made worse by becoming more " +
            "physically active?",
        hint = "Please answer NO if you had a problem in the past, but it does not limit your " +
            "current ability to be physically active.",
    ),
    ScreenItem(
        id = "parq_7",
        text = "Has your doctor ever said that you should only do medically supervised physical " +
            "activity?",
    ),
)

/**
 * Any yes. Not a score, not a count, not a threshold — the instrument does not have one.
 *
 * `ClinicalGates.parqPositive` feeds `prescribe()` in `:coach`, whose precedence table puts this
 * first, above the tripwire and above sick/injured: *"PAR-Q+ positive -> no prescription at all. A
 * clinician, not a ghost."*
 */
fun parqPositive(answers: Map<String, Boolean>): Boolean =
    PARQ_ITEMS.any { answers[it.id] == true }
