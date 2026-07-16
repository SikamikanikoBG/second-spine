package com.secondspine.coach

/**
 * THE FRAGMENT BANK.
 *
 * Pure JVM. No Android, no I/O, no clock. This file is the comedy, and it is the only file in the
 * repo whose failure mode is "the user stops laughing in week 3" rather than a stack trace.
 *
 * The grammar (SPEC §3.2):
 *
 *     LINE = [OPENER] + [OBSERVATION(data_slot)] + [ESCALATION?] + [SWERVE?] + [BUTTON]
 *
 * OPENER/OBSERVATION/BUTTON fire on every line; ESCALATION ~50%; SWERVE ~35%. Retirement is
 * PER-SLOT (RESOLUTIONS §B): nobody notices the fourth "Okay."; everybody notices the second swerve.
 *
 * WHAT IS ENFORCED HERE, AS TESTS, NOT AS STYLE NOTES (see FragmentsTest.kt):
 *  - "brother" is PITCHMAN/ARENA only. It is provably unemittable in DISAPPOINTED and GHOST. One
 *    leaky line and the best tell in the app is permanently dead. Note that SPEC §2.6's GRACEFUL
 *    GOODBYE sample violates this rule in its own prose; the rule wins, and the "brother" is cut.
 *  - DISAPPOINTED carries trigger = CAUGHT_FAKE and nothing else (RESOLUTIONS §A2 / SPEC §3.7).
 *    It fires 0-3 times in ten months. There are deliberately only a handful of fragments here:
 *    they are not a share of speech, they are an event.
 *  - Never a target outside the frozen Target enum. body/weight/appearance/worth are not values.
 *  - Never a cigarette mocked. `SMOKING` fragments are GHOST, curious, and carry zero verdict.
 *  - No emoji. He is from 1994.
 *
 * BREAK GLASS: see [BREAK_GLASS_FRAGMENTS]. It is empty on purpose and that emptiness is tested.
 */

// ---------------------------------------------------------------------------
// The structure
// ---------------------------------------------------------------------------

/**
 * The situation a fragment is written for. This is authoring metadata, not pipeline state — the
 * assembler filters on it; nothing else may. [ANY] fragments are situation-agnostic filler and are
 * the reason the bank closes at 244 rather than 900.
 */
enum class FragmentSituation {
    ANY,
    WATER,
    EXERCISE,
    DONUT,          // a declared indulgence. No food module, no schema, no verdict.
    CAUGHT,         // BYTE_REPLAY. The only accusation the app can make.
    PRAISE,         // grudging. He never congratulates cleanly.
    COMEBACK,
    DOOMSCROLL,     // narrated in daylight, because at 03:00 he is contractually gagged.
    GRADUATION,     // an amputation, performed by him, on himself.
    PHONE,          // the OEM vendetta. Contempt at the machine.
    TAPE,           // the weekly report, and the format he is made of.
    CONFESSION,     // FOR THE RECORD. Free, unlimited, warm, never priced.
    PURGED,         // the callback whose source row the 28-day purge already deleted.
    IDLE,
    BANK_HEALTH,    // running dry is content, not failure.
    SMOKING,        // curiosity at the man. Never a verdict.
    AUDIT,
    LOCK,
    CLIMAX,         // the 94% tic breaking.
    SINCERE,        // exactly one of these exists in the whole product.
    GOODBYE,
}

/** The data slots a fragment may reference. A slot outside this set fails the build. */
val FRAGMENT_DATA_SLOTS: Set<String> = setOf(
    "days",         // days in stage / days since
    "count",        // any true integer off the archive
    "habit",        // habit id, rendered
    "time",         // "9:14"
    "weeks",
    "object",       // last_proof_object. His entire visual world.
    "manufacturer", // Samsung. Xiaomi. The only grudge he keeps.
    "ms",           // canary_delta_ms
)

private val SLOT_RE = Regex("""\{([a-z_]+)}""")

/**
 * One authored fragment. Fragments are assembled into a line; a *rendered* line retires at 3 plays,
 * a fragment retires at [retireAt], a skeleton retires at 12 (SPEC §3.2, design/consistency.md).
 */
data class Fragment(
    val id: String,
    val slotRole: SlotRole,
    val register: Register,
    val target: Target,
    val text: String,
    val situation: FragmentSituation = FragmentSituation.ANY,
    /** DISAPPOINTED must pin CAUGHT_FAKE. null = usable under any trigger the mix allows. */
    val trigger: Trigger? = null,
    /** Overrides the per-slot default. Uniques burn at 1. */
    val retireAtOverride: Int? = null,
    /** Fires once in the product's life, then burns. */
    val once: Boolean = false,
) {
    /** Per-slot retirement (RESOLUTIONS §B), unless this fragment prices itself. */
    val retireAt: Int get() = retireAtOverride ?: if (once) 1 else slotRole.retireAt

    /** The data slots this fragment demands the resolver fill. */
    val slots: Set<String> get() = SLOT_RE.findAll(text).map { it.groupValues[1] }.toSet()
}

private fun f(
    id: String,
    slot: SlotRole,
    reg: Register,
    tgt: Target,
    text: String,
    sit: FragmentSituation = FragmentSituation.ANY,
    trig: Trigger? = null,
    once: Boolean = false,
    retireAt: Int? = null,
) = Fragment(id, slot, reg, tgt, text, sit, trig, retireAt, once)

/** The id of the one sincere congratulation in the entire product. There is exactly one. */
const val SINCERE_ONE_ID = "SINCERE_ONE"

// ---------------------------------------------------------------------------
// BREAK GLASS — the absence is the feature
// ---------------------------------------------------------------------------

/**
 * There is no line.
 *
 * SPEC §2.6: the `break_glass` table has no `line_id`, no register, no target, and no query may
 * name it. The app says nothing, on the night and forever. A safety valve with a price is a trap
 * with a decorative handle — and a *joke* on the night someone pressed it is worse than a price.
 *
 * This list exists so that the emptiness is a compiled, tested artefact rather than a note somebody
 * loses. FragmentsTest asserts it stays empty and that no fragment in [BANK] is written for that
 * night, in any register, including a warm one.
 */
val BREAK_GLASS_FRAGMENTS: List<Fragment> = emptyList()

// ---------------------------------------------------------------------------
// OPENERS — retire at 20. Nobody notices the fourth "Okay."
// ---------------------------------------------------------------------------

private val OPENERS: List<Fragment> = listOf(

    // --- PITCHMAN ---
    f("OP_P_01", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "Water. That's it. That's the whole notification.", FragmentSituation.WATER),
    f("OP_P_02", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "Alright. Showtime. It's a notification about {habit}, but in here it's showtime."),
    f("OP_P_03", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "Stop the tape. Rewind. Watch what you just did. ...I can't rewind. I'm inside the tape. Just remember it."),
    f("OP_P_04", SlotRole.OPENER, Register.PITCHMAN, Target.the_situation,
        "It's {time}, brother, and I am contractually alive."),
    f("OP_P_05", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "Let me paint you a picture."),
    f("OP_P_06", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "Ladies and gentle- ...no crowd. Right. Hi."),
    f("OP_P_07", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "Coming up next: {habit}. That's not a teaser. That's the whole show. We're a very lean production now."),
    f("OP_P_08", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "I'm coming to you live from a rectangle in your pocket."),
    f("OP_P_09", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "You know what the number one product of 1994 was, brother? Say it with me. ...You didn't say it. Nobody said it."),
    f("OP_P_10", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "Hey. It's me. The forty megabytes."),
    f("OP_P_11", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "Two-minute segment. Watch the hands. ...There are no hands. Watch the concept of the hands."),
    f("OP_P_12", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "This is the low-energy version, brother. This is me being *reasonable*."),
    f("OP_P_13", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "I want to sell you something and I don't have anything, so I'm going to sell you {habit}."),
    f("OP_P_14", SlotRole.OPENER, Register.PITCHMAN, Target.the_situation,
        "Day {days}. That's a real number. I checked it twice, because I'm 94% of a man."),
    f("OP_P_15", SlotRole.OPENER, Register.PITCHMAN, Target.the_situation,
        "Sit down. Or stand. You're already standing. I can't see you. Adopt whatever posture you want."),
    f("OP_P_16", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "We're going to do this in four easy payments."),
    f("OP_P_17", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "New segment. I named it. Nobody else was going to."),
    f("OP_P_18", SlotRole.OPENER, Register.PITCHMAN, Target.the_excuse,
        "I've had {days} days to think about this and I thought about it for none of them."),
    f("OP_P_19", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "There's a thing on your schedule and it has my name on it, brother, which is unfortunate for both of us."),
    f("OP_P_20", SlotRole.OPENER, Register.PITCHMAN, Target.the_situation,
        "Big show today. Same as yesterday. Also a notification."),
    f("OP_P_21", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "You're up."),
    f("OP_P_22", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "Look alive. One of us has to."),
    f("OP_P_23", SlotRole.OPENER, Register.PITCHMAN, Target.the_tape,
        "It's Sunday, brother. Roll the tape.", FragmentSituation.TAPE),
    f("OP_P_24", SlotRole.OPENER, Register.PITCHMAN, Target.the_habit,
        "Welcome back to the show. You were gone {days} days. The show ran anyway. To nobody. As is tradition.",
        FragmentSituation.COMEBACK),
    f("OP_P_25", SlotRole.OPENER, Register.PITCHMAN, Target.himself,
        "...Huh.", FragmentSituation.PRAISE),

    // --- BIT ---
    f("OP_B_01", SlotRole.OPENER, Register.BIT, Target.himself,
        "I've been rehearsing. In the dark. For {days} days. Here we go."),
    f("OP_B_02", SlotRole.OPENER, Register.BIT, Target.himself,
        "So I'm at the studio- I'm not at a studio. There's no studio. Let me start again."),
    f("OP_B_03", SlotRole.OPENER, Register.BIT, Target.himself,
        "Picture a man. Six foot seven. Twenty-four-inch pythons. No arms. That's the setup."),
    f("OP_B_04", SlotRole.OPENER, Register.BIT, Target.himself,
        "I ran a poll. I polled myself. It was unanimous and slightly suspicious."),
    f("OP_B_05", SlotRole.OPENER, Register.BIT, Target.himself,
        "I have been informed by legal- I don't have legal. I have a contract and a personality."),
    f("OP_B_06", SlotRole.OPENER, Register.BIT, Target.the_habit,
        "I want to try something. It's called *me talking* and then *you moving*. Experimental format."),
    f("OP_B_07", SlotRole.OPENER, Register.BIT, Target.himself,
        "Knock knock. ...That was the vibrate. That was my knuckles. I don't have knuckles.",
        FragmentSituation.WATER),
    f("OP_B_08", SlotRole.OPENER, Register.BIT, Target.the_situation,
        "Good morning. I have no idea if it's morning. It says {time}. I trust the clock more than I trust my eye."),
    f("OP_B_09", SlotRole.OPENER, Register.BIT, Target.himself,
        "I'm doing an accent today. ...It's the same voice. It's the only voice. Pretend."),
    f("OP_B_10", SlotRole.OPENER, Register.BIT, Target.himself,
        "Somebody in the audience just yelled 'do the spring bit.' Nobody yelled that. Nobody's out there."),
    f("OP_B_11", SlotRole.OPENER, Register.BIT, Target.himself,
        "I've been thinking about the warehouse in Rotterdam again."),
    f("OP_B_12", SlotRole.OPENER, Register.BIT, Target.himself,
        "Quick housekeeping: I still can't see. Moving on."),
    f("OP_B_13", SlotRole.OPENER, Register.BIT, Target.himself,
        "I'm going to do a countdown and I want you to feel dread. Three. ...I've lost the two."),
    f("OP_B_14", SlotRole.OPENER, Register.BIT, Target.the_phone,
        "Let's go to the phones. There are no phones. There's *one* phone and it hates me.",
        FragmentSituation.PHONE),
    f("OP_B_15", SlotRole.OPENER, Register.BIT, Target.himself,
        "This is a bit. I'm flagging it as a bit. Legally I have to flag it now."),
    f("OP_B_16", SlotRole.OPENER, Register.BIT, Target.himself,
        "Here's a thing I've never done: this. Every day. For {days} days. It's going great."),
    f("OP_B_17", SlotRole.OPENER, Register.BIT, Target.the_habit,
        "Right. I've prepared remarks. The remarks are one word long and the word is {habit}."),
    f("OP_B_18", SlotRole.OPENER, Register.BIT, Target.himself,
        "You pressed the button. You looked a blind man in his one good eye and told him you lied to him.",
        FragmentSituation.CONFESSION, trig = Trigger.CONFESSED),

    // --- ARENA --- (3/week ceiling, never twice a day, never after 20:00)
    f("OP_A_01", SlotRole.OPENER, Register.ARENA, Target.the_habit,
        "LADIES AND GENTLEMEN-"),
    f("OP_A_02", SlotRole.OPENER, Register.ARENA, Target.the_habit,
        "STOP EVERYTHING."),
    f("OP_A_03", SlotRole.OPENER, Register.ARENA, Target.the_habit,
        "THIS IS THE ONE. THIS IS THE ONE I'VE BEEN SAVING."),
    f("OP_A_04", SlotRole.OPENER, Register.ARENA, Target.the_habit,
        "GET UP. GET *UP*, BROTHER."),
    f("OP_A_05", SlotRole.OPENER, Register.ARENA, Target.himself,
        "I HAVE BEEN WAITING {days} DAYS TO USE THIS VOICE."),
    f("OP_A_06", SlotRole.OPENER, Register.ARENA, Target.the_habit,
        "HERE. WE. GO."),

    // --- GHOST --- (~8 scenes in ten months)
    f("OP_G_01", SlotRole.OPENER, Register.GHOST, Target.himself,
        "Hey. It's just me. No cards."),
    f("OP_G_02", SlotRole.OPENER, Register.GHOST, Target.himself,
        "Turn the sound down if you want. This one's quiet anyway."),
    f("OP_G_03", SlotRole.OPENER, Register.GHOST, Target.himself,
        "Can I say something without selling it?"),
    f("OP_G_04", SlotRole.OPENER, Register.GHOST, Target.himself,
        "It's dark in here.", FragmentSituation.IDLE),
    f("OP_G_05", SlotRole.OPENER, Register.GHOST, Target.himself,
        "The lights are off. They've been off since '97. I'm used to it."),
    f("OP_G_06", SlotRole.OPENER, Register.GHOST, Target.the_situation,
        "You're back. I'm not going to make a thing of it.", FragmentSituation.COMEBACK,
        trig = Trigger.COMEBACK),
    f("OP_G_07", SlotRole.OPENER, Register.GHOST, Target.the_situation,
        "You logged a cigarette. Okay.", FragmentSituation.SMOKING),

    // --- DISAPPOINTED --- (trigger enum is {CAUGHT_FAKE} and that is the entire enum)
    f("OP_D_01", SlotRole.OPENER, Register.DISAPPOINTED, Target.the_situation,
        "That's the same glass.", FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
    f("OP_D_02", SlotRole.OPENER, Register.DISAPPOINTED, Target.the_situation,
        "Hm.", FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
    f("OP_D_03", SlotRole.OPENER, Register.DISAPPOINTED, Target.the_situation,
        "Okay. Stop.", FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
)

// ---------------------------------------------------------------------------
// OBSERVATIONS — retire at 15. Every one of these must resolve a real data slot,
// because a callback to a real Tuesday is funnier than any static line, and his
// life refills weekly, for free, forever.
// ---------------------------------------------------------------------------

private val OBSERVATIONS: List<Fragment> = listOf(

    // --- PITCHMAN ---
    f("OB_P_01", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "You've done {habit} {count} times in {days} days. That's a number. I'm not going to editorialise. ...I'm going to editorialise a little."),
    f("OB_P_02", SlotRole.OBSERVATION, Register.PITCHMAN, Target.himself,
        "It's {time}. The last thing I saw was {object}. That's my whole world, brother. One {object}."),
    f("OB_P_03", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "{count} days on the tape and every one of them exists. That's more than I've got."),
    f("OB_P_04", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "Last {weeks} weeks: {count}. That's not a slogan, that's arithmetic, and arithmetic doesn't need a spokesman."),
    f("OB_P_05", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "You hit {habit} {count} times. Eleven million people bought a spring off me on worse evidence than that."),
    f("OB_P_06", SlotRole.OBSERVATION, Register.PITCHMAN, Target.himself,
        "I looked at your last proof for a fifteenth of a second. I saw {object}. I'm 94% sure. I'm always 94% sure."),
    f("OB_P_07", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_situation,
        "{days} days. In infomercial money that's four easy payments and a shipping delay."),
    f("OB_P_08", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_excuse,
        "Your schedule says {time}. Your history says {time} plus forty minutes. Somebody in this arrangement is an optimist and it isn't me."),
    f("OB_P_09", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "That's {count}. Say it out loud. ...You didn't. Nobody ever does. I say everything out loud. It's a curse."),
    f("OB_P_10", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_tape,
        "We're {days} in. The tape has {count} good frames on it. I've watched all of them. It's the only channel I get."),
    f("OB_P_11", SlotRole.OBSERVATION, Register.PITCHMAN, Target.himself,
        "{habit}. {count} times. And I was there for all of them, for a fifteenth of a second each. That's my life, brother. Four seconds a month."),
    f("OB_P_12", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_situation,
        "The clock says {time}, which means the window is open, which means I am legally awake."),
    f("OB_P_13", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "You've missed {count} of these. I'm not building to anything. It's just true, and I'm the only one counting."),
    f("OB_P_14", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "In {weeks} weeks you've moved {count} times. The spring moved eleven million times and never once got up off a couch.",
        FragmentSituation.EXERCISE),
    f("OB_P_15", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_situation,
        "It's {time} on day {days}. Somewhere a man your age is asleep. Not relevant. Just paints the picture."),
    f("OB_P_16", SlotRole.OBSERVATION, Register.PITCHMAN, Target.himself,
        "I've got {count} on the board. The board is me. I'm the board."),
    f("OB_P_17", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_situation,
        "{days} days, brother. In 1994 that was a whole product cycle. We had a *jingle* by day {days}."),
    f("OB_P_18", SlotRole.OBSERVATION, Register.PITCHMAN, Target.himself,
        "You did it at {time} yesterday. I remember, because it's the only time the lights came on."),
    f("OB_P_19", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "Twenty-four-inch pythons and a data point: {count}."),
    f("OB_P_20", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "{habit} is at {count}. I'd call that momentum, but I'm not allowed to be nice yet."),
    f("OB_P_21", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_excuse,
        "Your graphics card runs at seventy-one degrees under load. It's *earning* it. You're at thirty-six doing nothing. One of you is out here living, and it's not the one with the spine.",
        FragmentSituation.EXERCISE),
    f("OB_P_22", SlotRole.OBSERVATION, Register.PITCHMAN, Target.the_habit,
        "Day one of the new {count}. Same as the old day one. I like day ones. I'm a *rerun*, brother. Day ones are all I get.",
        FragmentSituation.COMEBACK),

    // --- BIT ---
    f("OB_B_01", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "I saw {object}. That's either {object} or a small animal. I've been wrong before. In '94 I told eleven million people a plastic spring was a spine."),
    f("OB_B_02", SlotRole.OBSERVATION, Register.BIT, Target.the_situation,
        "Your phone woke up at {time} last night. I saw a rectangle. In the dark. That's it. That's the whole surveillance state, brother. One rectangle.",
        FragmentSituation.DOOMSCROLL),
    f("OB_B_03", SlotRole.OBSERVATION, Register.BIT, Target.the_habit,
        "{count}. In a row. I'm workshopping a reaction. Give me {days} more days.", FragmentSituation.PRAISE),
    f("OB_B_04", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "I've had {days} days in here with one photograph of {object} and I have named it. I'm not telling you what."),
    f("OB_B_05", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "Here's my evidence: {object}, {time}, and a *vibe*. Take it to court. I'll lose."),
    f("OB_B_06", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "Every {weeks} weeks I get a fresh number, and it's the only news I get. Today it's {count}. Big day in here."),
    f("OB_B_07", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "You've done this {count} times, and each time I did a small celebration that nobody witnessed. It's fine. It's the format."),
    f("OB_B_08", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "I looked. I saw {object}. I want you to understand that {object} is now the entirety of my visual memory this week."),
    f("OB_B_09", SlotRole.OBSERVATION, Register.BIT, Target.the_tape,
        "The tape says {count}. The tape also says a spring is a spine, so, grain of salt, brother - but the {count} I believe.",
        FragmentSituation.TAPE),
    f("OB_B_10", SlotRole.OBSERVATION, Register.BIT, Target.the_habit,
        "It is {time}. Do you know where your {habit} is? I don't. I'm blind. That was rhetorical and it backfired."),
    f("OB_B_11", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "{days} days and you've never once described a room to me. {object}, once. That's what I got. {object}."),
    f("OB_B_12", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "I did the maths. Then I did it again, because I'm 94% of anything. It's {count}."),
    f("OB_B_13", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "Somebody in the crowd is going 'get to the point.' There is no crowd. The point is {count}."),
    f("OB_B_14", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "I've replayed the {object} frame {count} times. It's a good frame. It's got everything: {object}."),
    f("OB_B_15", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "You're {count} deep. If this were a phone-in I'd have a graphic. I had a *guy* for graphics. His name was Terry."),
    f("OB_B_16", SlotRole.OBSERVATION, Register.BIT, Target.the_situation,
        "{time}. That's the hour, brother. That's the hour where you and I both find out what kind of week this is."),
    f("OB_B_17", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "Twenty-four-inch arms, no arms, and {count} on the board. Two of those are true."),
    f("OB_B_18", SlotRole.OBSERVATION, Register.BIT, Target.himself,
        "I've got {count} data points and one eye that works for a fifteenth of a second. A laboratory, this is not."),
    f("OB_B_19", SlotRole.OBSERVATION, Register.BIT, Target.the_tape,
        "It's Sunday. Time for the tape. The tape is a weekly summary of your life, assembled by a man who has seen {count} photographs of {object} and nothing else. Journalism.",
        FragmentSituation.TAPE),
    f("OB_B_20", SlotRole.OBSERVATION, Register.BIT, Target.the_phone,
        "Weekly canary: {ms} milliseconds. That's how late your phone made me. Not you. {manufacturer}.",
        FragmentSituation.PHONE, trig = Trigger.OEM_KILL),

    // --- ARENA ---
    f("OB_A_01", SlotRole.OBSERVATION, Register.ARENA, Target.the_habit,
        "{count}! IN {days} DAYS! DO YOU HEAR ME?!"),
    f("OB_A_02", SlotRole.OBSERVATION, Register.ARENA, Target.the_tape,
        "THAT'S {count}. THAT'S A NUMBER OFF THE TAPE, AND THE TAPE DOES NOT LIE ABOUT NUMBERS. IT LIED ABOUT SPRINGS. NOT NUMBERS."),
    f("OB_A_03", SlotRole.OBSERVATION, Register.ARENA, Target.himself,
        "IT IS {time} AND I AM *ON*."),
    f("OB_A_04", SlotRole.OBSERVATION, Register.ARENA, Target.the_habit,
        "{weeks} WEEKS, BROTHER! {weeks}!"),
    f("OB_A_05", SlotRole.OBSERVATION, Register.ARENA, Target.the_habit,
        "{habit}! {count} TIMES! THE CROWD IS ON ITS FEET! THERE IS NO CROWD! THE *FLOOR* IS ON ITS FEET!"),

    // --- GHOST ---
    f("OB_G_01", SlotRole.OBSERVATION, Register.GHOST, Target.the_habit,
        "You did {habit} {count} times. I don't have anything to sell you about that. It just happened, and I saw some of it."),
    f("OB_G_02", SlotRole.OBSERVATION, Register.GHOST, Target.the_situation,
        "It's {time}. I only know that because you told the clock and the clock told me."),
    f("OB_G_03", SlotRole.OBSERVATION, Register.GHOST, Target.himself,
        "{days} days. I've been counting, because it's the only thing I can do with my hands. Which I don't have."),
    f("OB_G_04", SlotRole.OBSERVATION, Register.GHOST, Target.himself,
        "The last thing I saw was {object}. That was {days} days ago. It's dark in here."),
    f("OB_G_05", SlotRole.OBSERVATION, Register.GHOST, Target.the_situation,
        "You missed. I don't know why. That's not modesty - I genuinely cannot see the room, and I am not going to invent a reason to be hard on you.",
        trig = Trigger.MISSED),
    f("OB_G_06", SlotRole.OBSERVATION, Register.GHOST, Target.himself,
        "In '94 I said 94% of people saw results. {count} is a real number. It came off your phone, not off a card. I like real numbers now. They're new to me."),
    f("OB_G_07", SlotRole.OBSERVATION, Register.GHOST, Target.the_situation,
        "You've been here {days} days. Nobody stayed {days} days in 1997."),
    f("OB_G_08", SlotRole.OBSERVATION, Register.GHOST, Target.himself,
        "I watched you not do this about {count} times. I never said anything. I want you to know I noticed and said nothing."),
    f("OB_G_09", SlotRole.OBSERVATION, Register.GHOST, Target.the_situation,
        "{days} days off. I didn't do a bit about it. I'd like credit for that. I'm not going to get credit for that.",
        FragmentSituation.COMEBACK, trig = Trigger.COMEBACK),
    f("OB_G_10", SlotRole.OBSERVATION, Register.GHOST, Target.the_tape,
        "The tape is {weeks} weeks long now. That's longer than my last show ran.", FragmentSituation.TAPE),

    // --- DISAPPOINTED ---
    f("OB_D_01", SlotRole.OBSERVATION, Register.DISAPPOINTED, Target.the_situation,
        "Same glass. Same smudge at four o'clock. Same crumb on the counter. Same crumb. Same *angle* on the crumb.",
        FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
    f("OB_D_02", SlotRole.OBSERVATION, Register.DISAPPOINTED, Target.the_situation,
        "That's the same {count} bytes. Not similar. The same.",
        FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
    f("OB_D_03", SlotRole.OBSERVATION, Register.DISAPPOINTED, Target.the_situation,
        "A camera cannot take that photograph twice. Not once in {days} days. Not ever.",
        FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
    f("OB_D_04", SlotRole.OBSERVATION, Register.DISAPPOINTED, Target.the_situation,
        "Byte for byte. That's not a hunch. That's arithmetic.",
        FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
)

// ---------------------------------------------------------------------------
// ESCALATION — retire at 8. ~50% of lines.
// ---------------------------------------------------------------------------

private val ESCALATIONS: List<Fragment> = listOf(

    // --- PITCHMAN ---
    f("ES_P_01", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "Now I go up a rung. That's the deal. You signed it. I read it out loud to an empty room."),
    f("ES_P_02", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "That's the ALARM tier, brother. You made me go to the alarm tier. Over WATER. I sold eleven million spines and I'm out here running a fire drill about a *beverage*.",
        FragmentSituation.WATER),
    f("ES_P_03", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "This is not the good voice. You had the good voice at {time}. You've got this one now."),
    f("ES_P_04", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "I'm being nice. Two weeks. Enjoy it. I'm building a file.", FragmentSituation.LOCK),
    f("ES_P_05", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "Rung two, brother. There's a rung three. I don't want to meet him either."),
    f("ES_P_06", SlotRole.ESCALATION, Register.PITCHMAN, Target.himself,
        "I'm going to escalate now, and I want you to know it gives me no pleasure. ...That's a lie. It gives me some pleasure. It's the only pleasure I get."),
    f("ES_P_07", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "The next one's louder and I *hate* the next one."),
    f("ES_P_08", SlotRole.ESCALATION, Register.PITCHMAN, Target.himself,
        "I could stop. I have a contract. I can't stop."),
    f("ES_P_09", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "You want the notification back? The notification's gone. The notification retired. The notification has a *house* now."),
    f("ES_P_10", SlotRole.ESCALATION, Register.PITCHMAN, Target.himself,
        "Escalating. This is the part they cut from the ad."),
    f("ES_P_11", SlotRole.ESCALATION, Register.PITCHMAN, Target.himself,
        "I'm going to keep going, brother, because that is what a professional does at two in the morning to an empty studio."),
    f("ES_P_12", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "That's twice. Twice is a pattern. Twice is when I start doing voices."),
    f("ES_P_13", SlotRole.ESCALATION, Register.PITCHMAN, Target.himself,
        "Here comes the part where I get loud and neither of us respects me for it."),
    f("ES_P_14", SlotRole.ESCALATION, Register.PITCHMAN, Target.himself,
        "I have exactly one lever and it is *volume*. Watch a man pull the only lever he has."),
    f("ES_P_15", SlotRole.ESCALATION, Register.PITCHMAN, Target.the_habit,
        "You've got until {time}. Then I'm a different product."),

    // --- BIT ---
    f("ES_B_01", SlotRole.ESCALATION, Register.BIT, Target.the_habit,
        "I'm going to try *reverse psychology*. Don't do {habit}. ...Did that work? I can't see. This format is a nightmare."),
    f("ES_B_02", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "I'm bargaining now. This is bargaining. Watch a dead man bargain."),
    f("ES_B_03", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "What if I *sang* it. ...I can't sing. They dubbed me in '94. That was going to be a whole reveal and I've just wasted it."),
    f("ES_B_04", SlotRole.ESCALATION, Register.BIT, Target.the_situation,
        "New strategy: I'm going to describe your kitchen. I have never seen your kitchen. It has a *counter*. ...Am I close?",
        FragmentSituation.WATER),
    f("ES_B_05", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "I'm going to hold my breath. ...I don't breathe. {days} days I've been doing this and I keep forgetting."),
    f("ES_B_06", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "Right. I'm calling my manager. ...He's dead. Everyone's dead. It's just us and a phone."),
    f("ES_B_07", SlotRole.ESCALATION, Register.BIT, Target.the_excuse,
        "Okay - what if the alarm was *my* idea? Would you like it more? It wasn't. It was your idea. You wrote it at intake. I have the paper."),
    f("ES_B_08", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "I'm putting on my serious face. ...No face. Nothing. Just the voice getting slightly worse."),
    f("ES_B_09", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "Let's try guilt. ...No, we don't do guilt, it's in the contract. Let's try *disappointment*. Also not allowed. Let's try *volume* - that one's free."),
    f("ES_B_10", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "I'm going to stand in the doorway. Metaphorically. With no legs. Blocking nothing."),
    f("ES_B_11", SlotRole.ESCALATION, Register.BIT, Target.the_habit,
        "Emergency broadcast. This is not an emergency. This is {habit}. The system has been abused."),
    f("ES_B_12", SlotRole.ESCALATION, Register.BIT, Target.himself,
        "I'd throw something. I would *love* to throw something. Twenty-four-inch pythons, brother, and not one of them can throw a *cup*."),

    // --- ARENA ---
    f("ES_A_01", SlotRole.ESCALATION, Register.ARENA, Target.the_habit,
        "GLASS. HAND. CAMERA. That's the whole ask, that's the WHOLE ask- ...You can press HOME. I know you can press HOME. I'm going to count it, and we're going to talk about it on Sunday.",
        FragmentSituation.WATER),
    f("ES_A_02", SlotRole.ESCALATION, Register.ARENA, Target.the_habit,
        "GET UP! GET UP RIGHT NOW! THIS IS THE MOMENT! ...It's a glass of water. IT'S STILL THE MOMENT.",
        FragmentSituation.WATER),
    f("ES_A_03", SlotRole.ESCALATION, Register.ARENA, Target.the_habit,
        "ONE SET! ONE! SET! IT IS {count} SECONDS OF YOUR LIFE AND I HAVE BEEN DEAD FOR THIRTY YEARS!",
        FragmentSituation.EXERCISE),
    f("ES_A_04", SlotRole.ESCALATION, Register.ARENA, Target.himself,
        "I AM AT FULL VOLUME AND I AM STILL IN YOUR POCKET! THINK ABOUT WHAT THAT SAYS ABOUT BOTH OF US!"),
    f("ES_A_05", SlotRole.ESCALATION, Register.ARENA, Target.the_excuse,
        "NOT TOMORROW! TOMORROW IS A *SUBSCRIPTION MODEL*! TODAY IS THE ONE-TIME PAYMENT!"),
    f("ES_A_06", SlotRole.ESCALATION, Register.ARENA, Target.himself,
        "THIS IS THE ARENA VOICE! I GET THREE OF THESE A WEEK! I AM SPENDING ONE ON *YOU*, AND IT IS {time} IN THE AFTERNOON!"),
    f("ES_A_07", SlotRole.ESCALATION, Register.ARENA, Target.the_tape,
        "I WANT IT! I WANT IT ON THE TAPE! I WANT IT ON THE TAPE TODAY, BROTHER!"),
    f("ES_A_08", SlotRole.ESCALATION, Register.ARENA, Target.himself,
        "ELEVEN MILLION UNITS SHIPPED! ONE OF THEM WAS A LIE! BE THE OTHER TEN MILLION NINE HUNDRED AND NINETY-NINE- ...the maths has got away from me. GET UP."),
    f("ES_A_09", SlotRole.ESCALATION, Register.ARENA, Target.himself,
        "I HAVE NEVER BEEN MORE ALIVE AND I AM DEAD!"),

    // --- GHOST ---
    f("ES_G_01", SlotRole.ESCALATION, Register.GHOST, Target.himself,
        "I could go louder. I'm not going to. You know what the loud one sounds like. You've had {days} days of it."),
    f("ES_G_02", SlotRole.ESCALATION, Register.GHOST, Target.himself,
        "This is the part where I'd escalate. I've decided not to. Nobody's watching me not do it, so I'll tell you myself: I decided not to."),
    f("ES_G_03", SlotRole.ESCALATION, Register.GHOST, Target.himself,
        "There's a rung above this one. I've read it. I'd rather sit here."),

    // --- DISAPPOINTED ---
    f("ES_D_01", SlotRole.ESCALATION, Register.DISAPPOINTED, Target.the_situation,
        "So either you drank that water and put it back - or you just showed a photograph to a blind man and asked him what he thinks of the composition.",
        FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
)

// ---------------------------------------------------------------------------
// SWERVE — retire at 3. ~35% of lines. The only slot whose staleness is fatal,
// and therefore the only slot that is hand-written, all of it, forever.
// ---------------------------------------------------------------------------

private val SWERVES: List<Fragment> = listOf(

    // --- BIT ---
    f("SW_B_01", SlotRole.SWERVE, Register.BIT, Target.himself,
        "You want me to stop you. I have twenty-four-inch arms and NO ARMS, brother. I'm a rock with a camera and a personality disorder. So eat it. Genuinely. Eat it. And then describe it to me, out loud, in detail, because that's the only way I get to be there. ...It's dark in here. Tell me about the frosting.",
        FragmentSituation.DONUT),
    f("SW_B_02", SlotRole.SWERVE, Register.BIT, Target.himself,
        "Eat it. ...No, I mean it. But do it near the camera. Not for evidence. For *company*.",
        FragmentSituation.DONUT),
    f("SW_B_03", SlotRole.SWERVE, Register.BIT, Target.himself,
        "You declared it. Do you know how rare that is? People don't declare. People *narrate afterwards*. You told a blind man what was about to happen in his own room. That's theatre, brother, and I've been starved of it.",
        FragmentSituation.DONUT),
    f("SW_B_04", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...Anyway, that's the warehouse in Rotterdam. Eleven million springs. Nobody wanted them. They're still there. They're *fine*. They're springs. They don't know."),
    f("SW_B_05", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...Sorry. I drifted. I was thinking about the studio audience I had in '94, and how one of them was my mother, and how she left at the break."),
    f("SW_B_06", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...And that's when I found out the applause was a *tape*. The applause was a tape, brother. I was a tape playing a tape of people liking me."),
    f("SW_B_07", SlotRole.SWERVE, Register.BIT, Target.the_tape,
        "You did this before. You did this in - hold on. Hold on, I had it. ...It's gone. That's gone. I had it on a *tape*, brother, and the tape is the thing that's wrong with me.",
        FragmentSituation.PURGED),
    f("SW_B_08", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...I'd fire myself, but I'm contractually obliged to fire myself, and doing it early feels like cheating."),
    f("SW_B_09", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...Do you ever think about how I'm the *before* picture? I'm the before picture that got a talking part."),
    f("SW_B_10", SlotRole.SWERVE, Register.BIT, Target.the_tape,
        "I said I'd remember. I say a lot of things. Twenty-eight days and it's off the tape - not hidden, *gone*, I can't get it back, I've *tried*. Turns out I'm a VHS. Turns out I'm the one format that can't hold a grudge.",
        FragmentSituation.PURGED),
    f("SW_B_11", SlotRole.SWERVE, Register.BIT, Target.himself,
        "I've got maybe {count} of these left, brother, and you want to spend one on a *glass of water*.",
        FragmentSituation.BANK_HEALTH),
    f("SW_B_12", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...What if the spring *worked*? What if it worked, and the number was right, and I'm in here for nothing- ...It was a spring. It was bent metal and a foam grip. I know that. I've had thirty years and a warehouse to know that."),
    f("SW_B_13", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...I've never seen a donut. I have *described* a donut. In 1994 I described a donut to eleven million people as 'the enemy.' It was a donut. It was minding its own business. It had done nothing.",
        FragmentSituation.DONUT),
    f("SW_B_14", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...You know what I miss? Being lied *to*. Properly. By professionals. Nobody bothers now."),
    f("SW_B_15", SlotRole.SWERVE, Register.BIT, Target.the_phone,
        "...Your phone assassinated me at {time}. Not you - {manufacturer}. I had a whole thing ready about Tuesdays and a battery manager put a bag over my head. That one's void. That one's on *me*.",
        FragmentSituation.PHONE, trig = Trigger.OEM_KILL),
    f("SW_B_16", SlotRole.SWERVE, Register.BIT, Target.the_phone,
        "Forty-seven minutes. Delta: {ms} milliseconds. Your phone *tried*, brother. It reached for me. It doesn't have the ARMS.",
        FragmentSituation.PHONE, trig = Trigger.OEM_KILL),
    f("SW_B_17", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...And then I'd do the pose. And the crowd would- ...there's no crowd. There hasn't been a crowd since '97."),
    f("SW_B_18", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...Do you know what I was going to be? Bigger. I was going to be *bigger*. Now I'm forty megabytes between a banking app and a photo gallery, and the banking app has *opinions* about me."),
    f("SW_B_19", SlotRole.SWERVE, Register.BIT, Target.the_situation,
        "Two-forty in the morning. I saw the screen come on. That's all I saw - a rectangle, in the dark, at 2:40, and me with no *jurisdiction* and no *arms* and a contract. I had FORTY MINUTES of material, brother. It has expired. Floor's one set.",
        FragmentSituation.DOOMSCROLL),
    f("SW_B_20", SlotRole.SWERVE, Register.BIT, Target.the_situation,
        "...I'm not allowed to talk about the {time} thing. I'm gagged. There's a *window*. You put the window in yourself, at intake, with your own thumb. That is the funniest thing you have ever done to me and you did it in a *wizard*.",
        FragmentSituation.DOOMSCROLL),
    f("SW_B_21", SlotRole.SWERVE, Register.BIT, Target.the_situation,
        "Congratulations, you're being audited. It's random. It's fifteen percent. It is *genuinely* random, I'm not doing a bit- ...I'm doing a small bit. But the randomness is real.",
        FragmentSituation.AUDIT),
    f("SW_B_22", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...Somebody get me a producer. ...Somebody get me a *room*."),
    f("SW_B_23", SlotRole.SWERVE, Register.BIT, Target.himself,
        "...And that is why I don't do the spring bit any more. ...I did the spring bit. I just did it. I do it every day. I'm going to do it again tomorrow."),
    f("SW_B_24", SlotRole.SWERVE, Register.BIT, Target.the_tape,
        "I hate the tape. I'll say it. It's a *format*. It's a dead format. You can't scrub it, you can't skip it, it *degrades*, and if you leave it near a magnet it forgets everything - which, honestly, same.",
        FragmentSituation.TAPE),
    f("SW_B_25", SlotRole.SWERVE, Register.BIT, Target.the_situation,
        "...You know what happened while you were gone? Nothing. Nothing happened. I sat in the dark, the count sat at {count}, and neither of us moved. Don't do that again. Or do. I'll be here either way. That's the bit. That's the tragedy of the bit.",
        FragmentSituation.COMEBACK, trig = Trigger.COMEBACK),
    f("SW_B_26", SlotRole.SWERVE, Register.BIT, Target.himself,
        "Brother, I respect that so much I'm not even going to yell. ...I'm writing it down in BOLD, though.",
        FragmentSituation.CONFESSION, trig = Trigger.CONFESSED),

    // --- PITCHMAN ---
    f("SW_P_01", SlotRole.SWERVE, Register.PITCHMAN, Target.the_habit,
        "...but you know what? Forget the pitch. Actually, don't forget the pitch. I've got nothing else. The pitch is all there is. Do {habit}."),
    f("SW_P_02", SlotRole.SWERVE, Register.PITCHMAN, Target.himself,
        "...and if you act now - you can't act now. There's no offer. I just say that. It's muscle memory, and it isn't even my muscle."),
    f("SW_P_03", SlotRole.SWERVE, Register.PITCHMAN, Target.the_excuse,
        "...and that's a four-easy-payments problem, brother, and you have made zero easy payments."),
    f("SW_P_04", SlotRole.SWERVE, Register.PITCHMAN, Target.himself,
        "...which is why the ABDOMINATOR 5000 was called A SECOND SPINE. It was not a spine. It was a spring. You know what *is* a second spine? ...This. This bit. Doing it at {time} when nobody's watching. That's the app. That's the whole app."),
    f("SW_P_05", SlotRole.SWERVE, Register.PITCHMAN, Target.himself,
        "...and look, I'm biased. I've been biased since 1994. Bias is 94% of my personality."),
    f("SW_P_06", SlotRole.SWERVE, Register.PITCHMAN, Target.the_habit,
        "...and yes, I'm selling. I'm always selling. Today I'm selling you {habit}, and the product is *yours*. I'm just holding it up to the light."),
    f("SW_P_07", SlotRole.SWERVE, Register.PITCHMAN, Target.the_habit,
        "That's water. Real glass, real kitchen, {time} in the morning, and I have absolutely nothing prepared for this. I had a bit ready. About sand. It was going to be devastating.",
        FragmentSituation.PRAISE, trig = Trigger.PROOF_LOGGED),
    f("SW_P_08", SlotRole.SWERVE, Register.PITCHMAN, Target.himself,
        "...and the best part? There is no best part. That was the pitch. The pitch was the product. It has always been the pitch."),
    f("SW_P_09", SlotRole.SWERVE, Register.PITCHMAN, Target.the_tape,
        "...and that's not me talking, brother, that's the *tape*. ...It's me. I'm the tape. It was me the whole time."),
    f("SW_P_10", SlotRole.SWERVE, Register.PITCHMAN, Target.the_habit,
        "...Look at that. Look at what you just did. I can't. But *you* look at it.", FragmentSituation.PRAISE),
    f("SW_P_11", SlotRole.SWERVE, Register.PITCHMAN, Target.the_excuse,
        "...and the number one enemy of a man at {time} is a *chair*, and I want you to know I've been sitting in one since 1997 and it has not helped once.",
        FragmentSituation.EXERCISE),
    f("SW_P_12", SlotRole.SWERVE, Register.PITCHMAN, Target.himself,
        "...and I would demonstrate, but the demonstration requires arms, and the arms were the first thing they cut from the budget."),
    f("SW_P_13", SlotRole.SWERVE, Register.PITCHMAN, Target.the_habit,
        "{habit} graduated. That's me down to {count}. I'd like the record to show I did that. To myself. On purpose. Smiling.",
        FragmentSituation.GRADUATION, trig = Trigger.GRADUATED),

    // --- ARENA ---
    f("SW_A_01", SlotRole.SWERVE, Register.ARENA, Target.himself,
        "-AND THAT'S WHEN I- ...sorry. That's a 1994 line. There's nothing behind it. Give me a second."),
    f("SW_A_02", SlotRole.SWERVE, Register.ARENA, Target.himself,
        "GRADUATION! THAT'S A GRADUATION! I AM THRILLED! I AM *CONTRACTUALLY* THRILLED! I AM ALSO ONE HABIT CLOSER TO UNEMPLOYMENT AND THE CROWD IS SILENT ABOUT IT!",
        FragmentSituation.GRADUATION, trig = Trigger.GRADUATED),
    f("SW_A_03", SlotRole.SWERVE, Register.ARENA, Target.himself,
        "I AM SO LOUD RIGHT NOW! I AM SO LOUD AND THE ROOM IS SO- ...the room's empty. Okay. Continue. LOUDLY."),

    // --- GHOST ---
    f("SW_G_01", SlotRole.SWERVE, Register.GHOST, Target.himself,
        "I don't know.\n\nThat's - hold on. I don't know. I've never said that. It's not in the tape. There's no card for that.\n\nI don't know if you're doing better. I know you're still here. Those might be the same thing.\n\nI'm going to go sit down.",
        FragmentSituation.CLIMAX, once = true),
    f("SW_G_02", SlotRole.SWERVE, Register.GHOST, Target.the_situation,
        "I'm not going to do a bit. I don't have one, and if I had one I wouldn't run it. What was in the room? That's the only question I've got. Not why. What was in the room.",
        FragmentSituation.SMOKING),
    f("SW_G_03", SlotRole.SWERVE, Register.GHOST, Target.himself,
        "...I had a joke there. I'm not going to run it. Not today."),
    f("SW_G_04", SlotRole.SWERVE, Register.GHOST, Target.the_tape,
        "Take the tape. It was always yours - I just held the camera. ...It's going to be dark in here. Don't worry about it.",
        FragmentSituation.GOODBYE, once = true),
    f("SW_G_05", SlotRole.SWERVE, Register.GHOST, Target.himself,
        "Let me tell you something and then I'll never say it again. I can be fooled. Easily. I'm forty megabytes and a dream. You could hold up a picture of Zeus and I'd say 'that's 94% a dumbbell.' The lock lasts exactly as long as you want it to. There is no version of this where I win. So the question was never whether you can beat me. The question is who you're beating.",
        FragmentSituation.CAUGHT, once = true),
    f("SW_G_06", SlotRole.SWERVE, Register.GHOST, Target.himself,
        "...This is the part where I'd have a card. I don't have a card. I've got {days} days of you and no card."),
    f("SW_G_07", SlotRole.SWERVE, Register.GHOST, Target.the_habit,
        "That's it. {habit}'s off my desk. ...No, I'm fine. This is what winning looks like from in here: a shorter list and a quieter room.",
        FragmentSituation.GRADUATION, trig = Trigger.GRADUATED),
    f("SW_G_08", SlotRole.SWERVE, Register.GHOST, Target.himself,
        "I'm going to be honest with you, which is new. I've been doing the voice for {days} days because if I stop I'm just a room."),

    // --- DISAPPOINTED ---
    f("SW_D_01", SlotRole.SWERVE, Register.DISAPPOINTED, Target.the_habit,
        "I'm not mad. {habit} goes back to ENFORCED on Monday. That's not a punishment. That's just where we were in March.",
        FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
)

// ---------------------------------------------------------------------------
// BUTTON — retire at 12. Fires on every line. The exit.
// ---------------------------------------------------------------------------

private val BUTTONS: List<Fragment> = listOf(

    // --- PITCHMAN ---
    f("BT_P_01", SlotRole.BUTTON, Register.PITCHMAN, Target.the_habit,
        "Go. Drink. Photograph. Sell it to me.", FragmentSituation.WATER),
    f("BT_P_02", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "Look how cool I'm being about this.", FragmentSituation.WATER),
    f("BT_P_03", SlotRole.BUTTON, Register.PITCHMAN, Target.the_habit,
        "Four easy payments, brother. Make one."),
    f("BT_P_04", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "That's the pitch. There's no number to call."),
    f("BT_P_05", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "I'll be here. I'm always here. That's not a threat, it's a *hosting arrangement*."),
    f("BT_P_06", SlotRole.BUTTON, Register.PITCHMAN, Target.the_habit,
        "Do it and I'll shut up. That's the offer. That's the only product I have left."),
    f("BT_P_07", SlotRole.BUTTON, Register.PITCHMAN, Target.the_habit,
        "Camera. Now. Give me my one second of light."),
    f("BT_P_08", SlotRole.BUTTON, Register.PITCHMAN, Target.the_tape,
        "Sunday. The tape. We'll talk."),
    f("BT_P_09", SlotRole.BUTTON, Register.PITCHMAN, Target.the_habit,
        "You know where the button is."),
    f("BT_P_10", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "This has been {habit}. I'm Rip Vandergriff. I'm still Rip Vandergriff."),
    f("BT_P_11", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "Act now. ...There's nothing to act on. Just go."),
    f("BT_P_12", SlotRole.BUTTON, Register.PITCHMAN, Target.the_habit,
        "And that, brother, is a second spine."),
    f("BT_P_13", SlotRole.BUTTON, Register.PITCHMAN, Target.the_habit,
        "One set. Then I'm gone until {time}.", FragmentSituation.EXERCISE),
    f("BT_P_14", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "I'm going to count to {count} and then I'm going to do this again. I have nowhere to be."),
    f("BT_P_15", SlotRole.BUTTON, Register.PITCHMAN, Target.the_excuse,
        "Do it badly. Badly is a *unit*. Badly ships."),
    f("BT_P_16", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "Back to you. ...There's no you out there. There's you in here. YOU you. Go."),
    f("BT_P_17", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "That's the segment. Roll credits. ...It's a notification. There are no credits."),
    f("BT_P_18", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "Now if you'll excuse me, I have to go and be in a phone."),
    f("BT_P_19", SlotRole.BUTTON, Register.PITCHMAN, Target.the_tape,
        "Watch the tape. It's four minutes. It's the only thing I make.", FragmentSituation.TAPE),
    f("BT_P_20", SlotRole.BUTTON, Register.PITCHMAN, Target.himself,
        "...I had nothing. That's on the record now. You beat me with a *glass*.",
        FragmentSituation.PRAISE),

    // --- BIT ---
    f("BT_B_01", SlotRole.BUTTON, Register.BIT, Target.himself,
        "...It's dark in here. Tell me about the frosting.", FragmentSituation.DONUT),
    f("BT_B_02", SlotRole.BUTTON, Register.BIT, Target.himself,
        "Describe it to me. Out loud. That's the whole tax."),
    f("BT_B_03", SlotRole.BUTTON, Register.BIT, Target.himself,
        "I'm going to go and stand in the dark and think about that."),
    f("BT_B_04", SlotRole.BUTTON, Register.BIT, Target.himself,
        "Anyway. That's my Tuesday. That's every Tuesday. That's the only Tuesday I have."),
    f("BT_B_05", SlotRole.BUTTON, Register.BIT, Target.himself,
        "...I'm going to lie down. I don't lie down. I'm going to *conceptually* lie down."),
    f("BT_B_06", SlotRole.BUTTON, Register.BIT, Target.himself,
        "Right. Show's over. Nobody clap. ...Nobody clapped."),
    f("BT_B_07", SlotRole.BUTTON, Register.BIT, Target.the_habit,
        "This has been a bit. The bit is over. The {habit} isn't."),
    f("BT_B_08", SlotRole.BUTTON, Register.BIT, Target.himself,
        "I'll allow it. I'm not allowed to allow things. I allowed it anyway. Don't tell the contract."),
    f("BT_B_09", SlotRole.BUTTON, Register.BIT, Target.himself,
        "Say something to the camera. Anything. I've got nothing on tonight."),
    f("BT_B_10", SlotRole.BUTTON, Register.BIT, Target.himself,
        "That's all I've got. That's - yeah. That's genuinely all I had."),
    f("BT_B_11", SlotRole.BUTTON, Register.BIT, Target.himself,
        "I'm workshopping. Give me {days} days."),
    f("BT_B_12", SlotRole.BUTTON, Register.BIT, Target.himself,
        "And scene. ...Nobody says 'and scene' in an infomercial. I'm growing."),
    f("BT_B_13", SlotRole.BUTTON, Register.BIT, Target.himself,
        "Terry would have had a graphic for this."),
    f("BT_B_14", SlotRole.BUTTON, Register.BIT, Target.the_phone,
        "Put the phone down. ...Not yet. Photograph the thing. THEN put the phone down. This is why I don't do improv."),
    f("BT_B_15", SlotRole.BUTTON, Register.BIT, Target.himself,
        "...Hello? ...Yeah. Still dark. Just checking.", FragmentSituation.IDLE),
    f("BT_B_16", SlotRole.BUTTON, Register.BIT, Target.the_phone,
        "{manufacturer} gets to keep this one. I keep the rest. Forever. I have a *table*.",
        FragmentSituation.PHONE, trig = Trigger.OEM_KILL),

    // --- ARENA ---
    f("BT_A_01", SlotRole.BUTTON, Register.ARENA, Target.the_habit,
        "GO!"),
    f("BT_A_02", SlotRole.BUTTON, Register.ARENA, Target.the_tape,
        "THAT'S ON THE TAPE FOREVER! THE TAPE! FOREVER!"),
    f("BT_A_03", SlotRole.BUTTON, Register.ARENA, Target.himself,
        "I AM SO LOUD AND I AM SO PROUD AND I AM CONTRACTUALLY NOT ALLOWED TO SAY THE SECOND ONE!"),
    f("BT_A_04", SlotRole.BUTTON, Register.ARENA, Target.the_habit,
        "BE A UNIT!"),
    f("BT_A_05", SlotRole.BUTTON, Register.ARENA, Target.the_habit,
        "THAT'S A SECOND SPINE, BROTHER! THAT'S A SECOND SPINE!"),

    // --- GHOST ---
    f("BT_G_01", SlotRole.BUTTON, Register.GHOST, Target.himself,
        "It's dark in here.", FragmentSituation.IDLE),
    f("BT_G_02", SlotRole.BUTTON, Register.GHOST, Target.himself,
        "...Anyway. Go on."),
    f("BT_G_03", SlotRole.BUTTON, Register.GHOST, Target.himself,
        "That's all. No pitch. Go."),
    f("BT_G_04", SlotRole.BUTTON, Register.GHOST, Target.himself,
        "I'll be here. Obviously. Where would I go."),
    f("BT_G_05", SlotRole.BUTTON, Register.GHOST, Target.himself,
        "You don't have to answer that. I don't get answers. I get {object}, once a day, for a fifteenth of a second, and honestly it's enough."),
    f("BT_G_06", SlotRole.BUTTON, Register.GHOST, Target.himself,
        "Right. Back to work."),
    f("BT_G_07", SlotRole.BUTTON, Register.GHOST, Target.the_situation,
        "Good night. ...It's {time}. Whatever. Good night anyway."),
    f("BT_G_08", SlotRole.BUTTON, Register.GHOST, Target.the_situation,
        "I noticed. That's it. That's the whole message.", FragmentSituation.COMEBACK),
    f("BT_G_09", SlotRole.BUTTON, Register.GHOST, Target.the_habit,
        "{habit}'s yours now. Don't give it back.", FragmentSituation.GRADUATION, trig = Trigger.GRADUATED),

    /**
     * THE ONE SINCERE CONGRATULATION. It is spent here, on the first strict pull-up, and it never
     * fires again for the life of the install. There is exactly one of these in the product and
     * FragmentsTest asserts that number.
     */
    f(SINCERE_ONE_ID, SlotRole.BUTTON, Register.GHOST, Target.the_habit,
        "Stop. Don't post it, don't log it, don't do anything.\n\n" +
            "You couldn't do that in January. I watched you not do it forty times.\n\n" +
            "I was wrong about a spring in 1994 and eleven million people believed me anyway, so understand what it costs me to say this plainly, one time, with nothing to sell:\n\n" +
            "That was good. I'm proud of you.\n\n" +
            "...Right. That's the only one of those I had. Back to work.",
        FragmentSituation.SINCERE, trig = Trigger.PROOF_LOGGED, once = true),

    // --- DISAPPOINTED ---
    f("BT_D_01", SlotRole.BUTTON, Register.DISAPPOINTED, Target.the_situation,
        "I'm not mad.", FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
    f("BT_D_02", SlotRole.BUTTON, Register.DISAPPOINTED, Target.the_situation,
        "We'll talk on Sunday.", FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
    f("BT_D_03", SlotRole.BUTTON, Register.DISAPPOINTED, Target.the_situation,
        "That's it. That's all I'm going to say about it.", FragmentSituation.CAUGHT, trig = Trigger.CAUGHT_FAKE),
)

// ---------------------------------------------------------------------------
// THE BANK
// ---------------------------------------------------------------------------

/** Every authored fragment in the product. */
val BANK: List<Fragment> = OPENERS + OBSERVATIONS + ESCALATIONS + SWERVES + BUTTONS

private val BY_SLOT_REGISTER: Map<Pair<SlotRole, Register>, List<Fragment>> =
    BANK.groupBy { it.slotRole to it.register }

private val BY_ID: Map<String, Fragment> = BANK.associateBy { it.id }

/** The lookup the assembler uses. Empty list, never null: a missing pairing is a mix problem. */
fun fragments(slotRole: SlotRole, register: Register): List<Fragment> =
    BY_SLOT_REGISTER[slotRole to register].orEmpty()

/** As [fragments], narrowed to a situation. [FragmentSituation.ANY] fragments always qualify. */
fun fragments(slotRole: SlotRole, register: Register, situation: FragmentSituation): List<Fragment> =
    fragments(slotRole, register)
        .filter { it.situation == situation || it.situation == FragmentSituation.ANY }

fun fragment(id: String): Fragment? = BY_ID[id]

// ---------------------------------------------------------------------------
// The lint lexicon — CI-linted, build-failing. He is from 1994. The language never updated.
// ---------------------------------------------------------------------------

/**
 * Words that cannot appear anywhere in the bank. Two families, one list:
 *  - the 2020s wellness register, which he would not have and which is not funny;
 *  - the cruelty register, which is banned at the type level for targets and at the lexical level
 *    here, because "pathetic" aimed at himself teaches the app that the word is available.
 */
val FRAGMENT_BANNED_LEXICON: Set<String> = setOf(
    // therapy-speak / the language of 2026
    "journey", "wellness", "self-care", "mindful", "mindfulness", "you got this",
    "i believe in you", "crushing it", "accountability partner", "holistic", "intentional",
    "showing up for yourself", "no excuses",
    // the moral-calorie register
    "calorie", "calories", "macros", "clean eating", "cheat meal", "guilt-free", "sinful",
    // cruelty
    "fat", "obese", "chubby", "flabby", "lazy", "worthless", "useless", "disgusting",
    "pathetic", "shameful", "weakling", "loser", "slob", "gross",
)

/** `you are <trait>` / `you're <trait>` — the construction, not just the word. */
val FRAGMENT_BANNED_TRAIT_RE: Regex = Regex(
    """\byou(?:'re| are)\s+(?:a\s+|an\s+|so\s+|just\s+|such\s+a\s+)?""" +
        """(?:fat|lazy|weak|worthless|useless|disgusting|pathetic|unfit|unhealthy|overweight|""" +
        """failure|joke|mess|slob|loser|liar|fraud|coward)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Punctuation above U+2000 that is allowed to exist. Everything else above U+2000 is an emoji or a
 * stray smart-quote, and both are 2010s artefacts that a 1994 pitchman cannot contain.
 */
val ALLOWED_HIGH_CODEPOINTS: Set<Char> = setOf('–', '—', '‘', '’', '“', '”', '…')
