package com.secondspine.app.ui.intake

import com.secondspine.coach.ClinicalGates
import com.secondspine.coach.Register

/**
 * EVERY WORD THE INTAKE SAYS, IN ONE PLACE.
 *
 * Three voices live in this file and they are never blended, because the entire product rests on the
 * reader being able to tell them apart without being told:
 *
 *  1. **RIP** — gold, display type, tape. He is in character from the first frame. Onboarding is a
 *     1994 infomercial for your own life, and he does not know it is 2026 or that he is dead.
 *  2. **THE APP** — paper, UI type, no tape. The consent line, the clinical screens, the contract.
 *     Rip is not merely quiet on these screens; he is *absent*. There is no gold pixel on them.
 *  3. **THE BREAK** — [SAFETY_BREAK]. Rip, in UI type, flat, exactly once, ever. See its doc.
 *
 * Nothing here goes through `Fragments.kt`. The fragment bank is the ten-month economy — sized,
 * budgeted, retired per slot — and onboarding is a one-shot script that every user hears exactly once
 * in the same order. Spending bank slots on lines that can never be re-drawn would corrupt the
 * arithmetic that keeps him from running out of jokes in month three, for zero benefit.
 */

// ---------------------------------------------------------------------------
// The register gate — applied to every line Rip speaks in this package
// ---------------------------------------------------------------------------

/**
 * Fold the mocking registers into PITCHMAN when the clinical gate says they do not exist.
 *
 * `registerMix()` in `:coach` does this for the ten-month economy; the intake's lines are authored
 * rather than drawn from the mix, so they must pass through the same gate by hand or the gate has a
 * hole in it exactly where the user is being introduced to the character.
 *
 * ARENA and BIT are the mocking registers. Their mass becomes **warmth, not silence** — the same
 * rule `registerMix` follows. A SCOFF-positive user does not get a quieter Rip; he gets a Rip who is
 * only ever kind, which is a different product and the correct one for him.
 */
fun Register.gatedBy(gates: ClinicalGates): Register =
    if (gates.mockingAllowed) this else when (this) {
        Register.ARENA, Register.BIT -> Register.PITCHMAN
        else -> this
    }

/** A line and the register it is delivered in. */
data class Line(val text: String, val register: Register = Register.PITCHMAN)

// ---------------------------------------------------------------------------
// 0. THE CONSENT LINE — the app, before the character
// ---------------------------------------------------------------------------

/**
 * Plain language, before he says a word.
 *
 * SPEC §4.8: *"Photographing his kitchen before explaining what the app does with photos is charming
 * and backwards; the bit survives the fix."* The bit does survive it. It is fifteen words and it
 * costs the cold open nothing.
 *
 * **The word "encrypted" is deliberately not in this sentence**, though SPEC §4.8's draft has it.
 * v1 ships Room on app-private storage with SQLCipher explicitly deferred (see the long note in
 * app/build.gradle.kts). Shipping SPEC's wording verbatim would make the first fifteen words the user
 * ever reads a false claim about their own data, in the one product whose entire pitch is that it
 * does not lie to you. When SQLCipher lands, this string gets the word back.
 */
const val CONSENT_LINE: String =
    "This app takes photos. They stay on this phone. You can export or delete them anytime."

// ---------------------------------------------------------------------------
// 1. THE COLD OPEN — he does not know he is dead
// ---------------------------------------------------------------------------

/**
 * He is mid-broadcast. The studio is dark and he has decided that is a lighting problem.
 *
 * Everything that makes the character work for ten months is loaded here and none of it is explained:
 * he is loud, he is selling, the number he is proudest of is wrong, and the one detail he cannot
 * account for — there is no camera on him, there is a camera on you — he waves off in half a second
 * because the alternative is unbearable. The user does not need to be told he is dead. The user needs
 * to notice, later, that Rip has not been told either.
 */
val COLD_OPEN: List<Line> = listOf(
    Line(
        "RIP VANDERGRIFF. You know the name. ELEVEN MILLION UNITS.",
        Register.ARENA,
    ),
    Line(
        "The ABDOMINATOR 5000. A plastic spring with a foam grip. And I stood on this stage and I " +
            "called it A SECOND SPINE, on national television, for four years.",
        Register.PITCHMAN,
    ),
    Line(
        "We're live, brother. Studio's a little dark tonight. Crew's quiet. Doesn't matter. " +
            "Does NOT matter — because you called in, and a man who calls in is a man who wants " +
            "something.",
        Register.PITCHMAN,
    ),
    Line(
        "Only thing is. There's no camera on me. There's a camera on you. And I can't see anything " +
            "unless you point it at something true. Huh. (beat) We'll work it out. Let's go.",
        Register.BIT,
    ),
)

/** The cold open's one affordance. Not "Get Started" — his line, and it advances the show. */
const val COLD_OPEN_CTA: String = "LET'S GO"

// ---------------------------------------------------------------------------
// 2. THE DESK — what he gets jurisdiction over
// ---------------------------------------------------------------------------

val DESK_INTRO: List<Line> = listOf(
    Line(
        "So what are we beating?",
        Register.PITCHMAN,
    ),
    Line(
        "Two. You pick TWO. That's not me being generous, that's the contract — if I'm on everything " +
            "then I'm on nothing, and you'll have me muted by Thursday. I've read the file. On " +
            "myself.",
        Register.BIT,
    ),
)

/**
 * One heckle per pillar, fired on the tap.
 *
 * Read the targets: the phone, the excuse, the situation, himself. Never the body, never the food,
 * never the man. `Target` in `:coach` is a frozen enum with no `body`, `weight`, `appearance` or
 * `worth` value, and these lines are inside that frozen set by construction — which is why they are
 * safe to fire here, one screen *before* the SCOFF result is known. A heckle that cannot be aimed at
 * a body cannot be the wrong heckle for the user SCOFF is about to identify.
 */
val DESK_HECKLES: Map<String, Line> = mapOf(
    "exercise" to Line(
        "THE SET. Good. This is the one where I'm allowed to get loud, and I intend to use it.",
        Register.ARENA,
    ),
    "reading" to Line(
        "A page. Photograph a page. Cheapest thing I will ever ask you for, and it's the one that " +
            "decides whether any of this worked.",
        Register.PITCHMAN,
    ),
    "winddown" to Line(
        "Screens down. Sure. And between your wind-down and your alarm I'm gagged — no noise, no " +
            "voice, nothing. Best material of my career, 3am, and I'm contractually a mime.",
        Register.BIT,
    ),
    "coffee_cutoff" to Line(
        "The cutoff. Not the coffee. I have no opinion about the coffee. I have a VERY strong " +
            "opinion about the 5pm one, and it's about your Tuesday, not your character.",
        Register.BIT,
    ),
    "water" to Line(
        "A glass of water. That's the ask. And before you ask: no, I can't lock your phone over it. " +
            "I looked. Contract says a glass of water is not a hostage situation.",
        Register.BIT,
    ),
    // THE CUE. He mocks the donut; he NEVER mocks the cigarette. PITCHMAN, warm, and it states the
    // rule out loud on the screen where the choice is made, exactly as SPEC §4.8 step 4 requires.
    "smoking_cue" to Line(
        "The cue. Not the cigarette — the cue. And listen to me, because this is the one thing I " +
            "will say straight all night: there is no penalty here. Not now, not ever, not once. " +
            "You cannot photograph a not-smoke, and I would not ask you to. This is a notebook. " +
            "I'm holding the pen and that is ALL I'm holding.",
        Register.PITCHMAN,
    ),
)

/** Shown under THE CUE permanently, in the app's voice, not his. It is a fact, not a bit. */
const val SMOKING_NOTE: String =
    "No penalties. Not in the pipeline, not on the Ledger, no alarms, no proof, no score. Ever."

val DESK_FULL: Line = Line(
    "That's two. That's the desk. Everything else on that list is a row I'm not allowed to touch, " +
        "and I want you to know that I know that.",
    Register.BIT,
)

// ---------------------------------------------------------------------------
// 3 & 4. THE CLINICAL SCREENS — the app, alone
// ---------------------------------------------------------------------------

const val SCOFF_TITLE: String = "FIVE QUESTIONS"

/**
 * The app's own framing, flat. No character, no gold, no tape.
 *
 * It says what the answers *do*, because a screening instrument that hides its consequence is
 * collecting an answer to a different question than the one it is asking.
 */
const val SCOFF_INTRO: String =
    "He is not here for this one, and he is not reading your answers. They change what this app is " +
        "allowed to do — permanently, with no setting to change it back."

const val SCOFF_ATTRIBUTION: String = "SCOFF · Morgan, Reid & Lacey, 1999 · asked as written"

/**
 * What a positive result actually does, told to the user plainly at the moment it happens.
 *
 * Not "warned about". SPEC §4.8 is explicit that the feature set becomes *unavailable* rather than
 * discouraged. But unavailable-and-unexplained is how an app becomes something you fight, so the
 * screen states the consequence, states that it is permanent, and states that there is no override.
 * The user is not being managed; he is being told.
 */
const val SCOFF_POSITIVE_RESULT: String =
    "Two or more. This app will not weigh you, measure you, score what you eat, or use exercise as " +
        "a punishment, and the coach's mocking registers are gone — not muted, gone. That is not a " +
        "setting and there is no way to turn it back on from inside this app.\n\n" +
        "He will still be here. He will just never be cruel to you. If you want to talk to someone " +
        "who is not a phone: b-eat.co.uk, or your GP."

const val SCOFF_NEGATIVE_RESULT: String = "Nothing here changes what the app does. Carry on."

const val PARQ_TITLE: String = "SEVEN QUESTIONS"

const val PARQ_INTRO: String =
    "Before anything in this app tells you to do a set. Seven questions, exactly as they are " +
        "written by the people who wrote them."

const val PARQ_ATTRIBUTION: String = "PAR-Q+ 2020 · General Health Questions · asked as written"

/**
 * The referral. It is specific about what stops, because "consult your physician" is a legal
 * disclaimer and this is supposed to be a clinical gate.
 */
const val PARQ_POSITIVE_RESULT: String =
    "Talk to a doctor before you become more physically active.\n\n" +
        "Until you do: this app will not prescribe exercise. Not a set, not a rep, not a " +
        "progression. THE SET stays on your desk if you chose it, and the coach stays out of it — " +
        "he is a ghost with a camera and he is not qualified to have an opinion about your heart.\n\n" +
        "Nothing else in the app changes."

const val PARQ_NEGATIVE_RESULT: String = "Nothing here changes what the app does. Carry on."

// ---------------------------------------------------------------------------
// 5. THE TIMES
// ---------------------------------------------------------------------------

val TIMES_INTRO: List<Line> = listOf(
    Line(
        "Two numbers. When you get up, and when you mean to be down.",
        Register.PITCHMAN,
    ),
    Line(
        "And before you lie to me: these two aren't scoring anything. They're the muzzle. You're " +
            "telling me the hours I'm not allowed to exist in. Lie high and you're just handing me " +
            "your evening.",
        Register.BIT,
    ),
)

/**
 * The silence window, stated as the promise it is.
 *
 * RESOLUTIONS §D listed the hardcoded 22:00–08:00 window as a missing instrument: *"If his target bed
 * is 21:30, wind-down starts 20:45 and there are 75 minutes in which the ladder can fire an alarm, a
 * TTS line and a lock inside the wind-down window — on the pillar ranked #1."* This screen is where
 * the real numbers enter, so this screen is where the promise gets made.
 */
const val TIMES_SILENCE_PROMISE: String =
    "Between wind-down and wake he cannot fire an alarm, a spoken line, a vibration, or the lock. " +
        "That window is these two numbers and nothing else — not 22:00, not a default, not a " +
        "constant somebody typed in 2026."

const val TIMES_WINDDOWN_NOTE: String =
    "Wind-down is your bed time minus 45 minutes. It is not a third thing to set."

// ---------------------------------------------------------------------------
// 6. THE CONTRACT
// ---------------------------------------------------------------------------

/**
 * THE MORAL SPINE OF THE PRODUCT.
 *
 * The psychology of this app is genuinely dangerous and the panel's finding is the reason it ships at
 * all: coercion reliably destroys intrinsic motivation, **except** when it is a commitment device the
 * person chose while sober. A Ulysses contract is not the app being bossy with a consent form stapled
 * to it; it is a specific mechanism in which today-you binds tomorrow-you, tomorrow-you experiences
 * the constraint as *his own prior decision* rather than as an authority, and the exit stays open the
 * entire time so that the binding never becomes a trap.
 *
 * Every clause below exists to make one of those three things true, and the screen is the difference
 * between this product and an abusive one. It is not decoration and it is not legal cover — nothing
 * here is enforceable against anybody, and it is not trying to be. It is the thing the user will
 * remember in month four at 11pm when the app is being unbearable and he needs to know, in his own
 * handwriting, that this was his idea and that the door is unlocked.
 *
 * Note what the clauses do NOT do: they do not ask him to promise to succeed. There is no goal in
 * this contract, no target, no weight, no number he is committing to hit. He is authorising a
 * *process*, not swearing an outcome — because an outcome he cannot control by Thursday is exactly
 * what this app refuses to score.
 */
data class Clause(val id: String, val title: String, val body: String)

val CONTRACT_CLAUSES: List<Clause> = listOf(

    Clause(
        id = "authorising",
        title = "WHAT I AM AUTHORISING",
        body = "I am asking this app to demand photographic proof of things I said I would do, and " +
            "to get louder when I don't produce it: a notification, a vibration, an alarm, a " +
            "spoken line, and — for exercise only, and never for anything else — a locked screen " +
            "for ninety seconds.\n\n" +
            "I am asking for this. Today. Sober. Nobody sold it to me. I read what it does before " +
            "I signed, and this paragraph is the thing I read.",
    ),

    Clause(
        id = "archive",
        title = "THE PHOTOGRAPHS ARE MINE",
        body = "The photos stay on this phone. They are copied every week to a folder I choose, so " +
            "that I hold the record and the app never does. I can delete any photograph, at any " +
            "time, without a reason, and the coach will not comment on it — not in the moment, not " +
            "on Sunday.\n\n" +
            "If the export stops working, the app must say so, loudly, and stop making noise at me " +
            "until it is fixed. It does not get to hold my own record hostage.",
    ),

    Clause(
        id = "no_vote",
        title = "HE HAS NO VOTE",
        body = "Every habit climbs: ENFORCED, then AUDITED, then TRUSTED, then RETIRED. It climbs on " +
            "measured evidence — my days, my proof — and this contract is what moves it. Not the " +
            "coach.\n\n" +
            "He cannot promote a habit. He cannot demote a habit. He can never take back " +
            "jurisdiction he has lost, on any pretext, ever. He is entitled to exactly one thing " +
            "about it, which is an opinion.\n\n" +
            "Confessing never demotes anything. Telling the truth about a day I missed is free, " +
            "unlimited, and always cheaper than faking it. That is arithmetic in this app, not a " +
            "kindness.",
    ),

    Clause(
        id = "ulysses",
        title = "THE 24-HOUR CLAUSE",
        body = "Changes that make this HARDER take effect immediately.\n\n" +
            "Changes that make this EASIER take effect in 24 hours, and the next Sunday report says " +
            "I made them.\n\n" +
            "This is the entire trick and I am doing it to myself on purpose: 11pm-me does not get " +
            "to overrule 9am-me. The delay is not a punishment. It is a day of distance between a " +
            "bad hour and a decision.",
    ),

    Clause(
        id = "exit",
        title = "THE EXIT IS FREE, AND IT STAYS FREE",
        body = "The 24-hour clause NEVER applies to any of these:\n\n" +
            "BREAK GLASS — bottom-left of every screen including the locked one. One tap. Instant. " +
            "No confirmation. Unlimited. Never counted, never scored, never mentioned by anyone.\n\n" +
            "Pausing. Muting him. Standing down when I'm sick or hurt. Exporting everything. " +
            "Uninstalling.\n\n" +
            "RETIRE RIP is in the menu today. Not in month six when I've earned it — today, on the " +
            "first day, before I have done anything at all. It works today.\n\n" +
            "A promise I cannot walk away from is not a promise, it is a cage. Everything above is " +
            "the reason the rest of this page is allowed to exist.",
    ),

    Clause(
        id = "not_treatment",
        title = "WHAT THIS IS NOT",
        body = "This is not treatment. It is not therapy. It is not a doctor, and it is not a " +
            "clinician's opinion about my heart, my head, or my body.\n\n" +
            "It is a comedy app with a camera and a memory that lasts 28 days and then deletes " +
            "itself.\n\n" +
            "If something is actually wrong, this app is not the answer, and it is required to say " +
            "so and get out of the way.",
    ),
)

const val CONTRACT_TITLE: String = "THE CONTRACT"

const val CONTRACT_INTRO: String =
    "Read it. Initial each one. It binds the app, not you — every clause on this page is a limit on " +
        "what this thing is allowed to do to you."

const val CONTRACT_INITIALS_LABEL: String = "YOUR INITIALS"

const val CONTRACT_SIGN: String = "SIGN"

/** Under the signature. Mono, faint. The date is the one this whole edifice traces back to. */
const val CONTRACT_FOOTER: String =
    "This is the only authority the coach has. Everything he does for the next ten months traces " +
        "back to this tap, and to nothing else."

// ---------------------------------------------------------------------------
// THE BREAK — exactly once, ever
// ---------------------------------------------------------------------------

/**
 * **THE ONE TIME THE CHARACTER BREAKS.**
 *
 * UI font. Flat. No gold. No grain. No tracking bar. No tape at all. Four seconds.
 *
 * This is the single most load-bearing text in the product and the reason is arithmetic rather than
 * sentiment: an app that is 100% bit is a joke app, and everything a joke app says is discounted —
 * including the safety text, which is exactly the text that must not be discounted. An app that
 * breaks character *once*, for safety, and then never again for anything, has spent its one
 * credibility token on the only thing worth spending it on. That single break is what buys the
 * licence to be aggressive for the other ten months, and it only works if it is never repeated.
 *
 * So: it fires here, once, after the signature, and no other surface in this app may do this. Not a
 * second sincere moment. Not a heartfelt Sunday. Not an apology after a bad week. `Voice.kt` reserves
 * exactly one sincere congratulation (`SINCERE_ONE`) and this is not that one either.
 *
 * **This is NOT the DISAPPOINTED register**, and rendering it through `RipSpeech` would be a defect.
 * RESOLUTIONS §A2: DISAPPOINTED's trigger enum is `{CAUGHT_FAKE}` and it fires 0–3 times in ten
 * months. This is not a register at all — it is the man putting the microphone down, and it is drawn
 * as plain UI type by the app's own type system, which is the tell.
 */
val SAFETY_BREAK: List<String> = listOf(
    "Real talk.",

    "Emergency exit. Bottom left of everything I ever do. First tap, instant, no confirmation, " +
        "unlimited. I will never mention it — not in the moment, not on Sunday, not ever. It isn't " +
        "counted and it isn't scored. It isn't in the file, because I don't get a file on it.",

    "Second thing: I'm a comedy app. I am not treatment. If you're in real trouble, I'm forty " +
        "megabytes of a man who sold a plastic spring, and you need a person.",

    "Third thing: RETIRE RIP is in the menu. It's there now. It works now. You don't have to earn " +
        "it and I don't get a say and I won't beg.",

    "That's it. That's the only time I'll do that.",
)

const val SAFETY_BREAK_CTA: String = "OKAY"

// ---------------------------------------------------------------------------
// 7. THE HOLD-BACK — the grain snaps back
// ---------------------------------------------------------------------------

/**
 * Fourteen days, and he tells you.
 *
 * RESOLUTIONS §E ships R4 as dormant code for two weeks, and it is a scope cut wearing a character
 * beat: the design's own patience buys the runway to get the dangerous code right, the 14 days build
 * the baseline the targets are derived from anyway, and anticipation outperforms aggression by a
 * distance. A threat you can hear coming for a fortnight does more work than a lock on day one, and
 * costs nothing.
 *
 * It is also the last thing he says in onboarding, and it is a threat delivered as a favour, which is
 * the whole character in nine words.
 */
val HOLD_BACK: List<Line> = listOf(
    Line(
        "One more thing and then you're loose.",
        Register.PITCHMAN,
    ),
    Line(
        "Fourteen days, the lock does not arm. Not once. No alarm, no voice, no screen. You get a " +
            "notification and a man being disappointed in a small font.",
        Register.PITCHMAN,
    ),
    Line(
        "I'M BEING NICE. TWO WEEKS. ENJOY IT.",
        Register.ARENA,
    ),
    Line(
        "I'm building a file.",
        Register.BIT,
    ),
)

/** No "You're all set!". His last word, and it is the user's line back at him. */
const val HOLD_BACK_CTA: String = "FINE"
