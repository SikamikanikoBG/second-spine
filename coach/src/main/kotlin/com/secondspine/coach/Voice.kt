package com.secondspine.coach

import java.security.MessageDigest
import kotlin.random.Random

/**
 * THE VOICE ENGINE — the anti-decay machine.
 *
 * Decay is arithmetic, not inspiration. A character at 100% volume is noise by week 2, so this file
 * exists to make Rip *quieter* as the user gets better, and to make his own archive the writers' room
 * so the bank does not have to be infinite.
 *
 * Four mechanisms, none of which require an author:
 *   1. The odometer shrinks the demand faster than the bank shrinks ([speechBudget]).
 *   2. Register inversion re-prices the existing library instead of growing it ([registerMix]).
 *   3. Per-slot retirement prices repetition where it is actually noticed (`SlotRole.retireAt`).
 *   4. The DATA SLOT — a callback to a real Tuesday is funnier than any static line, and his life
 *      refills weekly, for free, forever ([SlotResolver]).
 *
 * DIVISION OF LABOUR: `Fragments.kt` owns the bank (the `Fragment` type, [BANK], the lint lexicon,
 * the situation taxonomy). This file owns the *machine* — budgets, mix, caps, ledger, assembly. It
 * reads the bank and never redefines it.
 *
 * Everything here is pure JVM. Time enters as an explicit `now: Long`; hour-of-day enters as an
 * explicit `Int` because a timezone is the caller's problem, not the brain's. Randomness enters as a
 * seeded [Random] so the 300-day soak test is deterministic and runs in milliseconds.
 *
 * Where this file and SPEC.md disagree, RESOLUTIONS.md decided. Citations are inline.
 */

// ---------------------------------------------------------------------------
// The constants that are single sources of truth (RESOLUTIONS §B)
// ---------------------------------------------------------------------------

/** No rendered line may repeat within this window. SPEC §3.6 — enforced on the hash, never a transcript. */
const val LINE_REPEAT_WINDOW_DAYS = 120

/** RESOLUTIONS §B: **3/week**, never twice a day, never after 20:00. The absolute cap always binds first. */
const val ARENA_MAX_PER_WEEK = 3

/** He does not do the arena voice at night. Ever. */
const val ARENA_CURFEW_HOUR = 20

/** ESCALATION fires ~50% of lines, SWERVE ~35%. SPEC §3.2's grammar. */
const val ESCALATION_RATE = 0.50
const val SWERVE_RATE = 0.35

/**
 * He never does a victory lap at a man who is down, and never at a machine that just killed him.
 * SPEC §2.2's ARENA trigger list is exhaustive in spirit; of the triggers Core.kt actually models,
 * these are the ones where the arena voice would be cruelty rather than theatre.
 */
val ARENA_FORBIDDEN_TRIGGERS: Set<Trigger> = setOf(
    Trigger.MISSED, Trigger.COLLAPSED, Trigger.CONFESSED, Trigger.CAUGHT_FAKE,
    Trigger.EVASION, Trigger.OEM_KILL,
)

// ---------------------------------------------------------------------------
// The two budgets, and the split
// ---------------------------------------------------------------------------

/**
 * VOLUNTEERED speech per day — RESOLUTIONS §B adopts §3.1's curve verbatim and §3.1 is sole owner:
 * `[4→6, 3→5, 2→4, 1→2, 0→0]`.
 *
 * ENFORCEMENT speech (a rung firing) is a SEPARATE budget and is deliberately **not** governed here.
 * It is gated by the ladder and the interlocks: a man who misses has earned the noise he signed up
 * for. Conflating the two is why the corpus arrived at 12/day and 1/day and both were "right".
 *
 * At jurisdiction 0 he has nothing left to have an opinion about. **The Tape only.** That is the
 * ending, and it is spelled `0`.
 */
fun speechBudget(jurisdiction: Int): Int = when (jurisdiction.coerceIn(0, 4)) {
    4 -> 6
    3 -> 5
    2 -> 4
    1 -> 2
    else -> 0
}

/**
 * The ARENA/ARCHIVE home split: **15% + 11% × j** (RESOLUTIONS). The odometer is the *only* input —
 * no calendar, no `daysSinceInstall`, no month index. The arc is therefore falsifiable in CI rather
 * than at month 8.
 *
 * j=4 → 0.59 (he owns the screen). j=0 → 0.15 (a 40px face and an archive that is entirely yours).
 * Note it never reaches 0: he never fully leaves, he just stops being the point.
 */
fun jurisdictionShare(jurisdiction: Int): Double = 0.15 + 0.11 * jurisdiction.coerceIn(0, 4)

/**
 * REGISTER INVERSION, as a pure function of one integer.
 *
 * RESOLUTIONS §B / §A2:
 *  - `ARENA_share  = 0.10 × j`         — a *ceiling*. The absolute cap (3/week, never twice a day,
 *                                        never after 20:00) always binds first, in [chooseRegister].
 *  - `GHOST_share  = 0.10 × (4 - j)`   — the wound shows as the jurisdiction drains away.
 *  - remainder → PITCHMAN / BIT.
 *  - `DISAPPOINTED` has **no scheduled share** and is absent from this map by construction. It is a
 *    rare event with trigger enum `{CAUGHT_FAKE}`, firing 0–3 times in ten months. §A2 says delete it
 *    from every register-mix table; a share of 0.0 would still be a row someone could tune upward, so
 *    it is not a key at all.
 *
 * The two ceilings sum to 0.40 at every j, so the PITCHMAN/BIT remainder is a constant 0.60 and the
 * inversion is a clean rotation of loud→quiet with no re-tuning. By month six the loud voice is rare,
 * so when it fires it no longer reads as energy — it reads as a man retreating into the act. Same
 * assets, inverted meaning, earned by the user's own success rather than granted by a calendar.
 *
 * CLINICAL GATES OUTRANK THE MIX (RESOLUTIONS §B: "the gate outranks the assertion"). SCOFF-positive
 * removes ARENA and BIT permanently, and their mass goes to PITCHMAN — the warm register. For that
 * user Rip is a pitchman who, as jurisdiction drains, becomes a ghost. He never mocks. Not once. The
 * failure mode this shape avoids is silencing him for the very user the gate exists to protect.
 *
 * @return shares summing to 1.0.
 */
fun registerMix(jurisdiction: Int, gates: ClinicalGates): Map<Register, Double> {
    val j = jurisdiction.coerceIn(0, 4)
    val arenaCeiling = 0.10 * j
    val ghostCeiling = 0.10 * (4 - j)
    val remainder = 1.0 - arenaCeiling - ghostCeiling   // == 0.60 at every j

    if (!gates.mockingAllowed) {
        // ARENA and BIT do not exist for this user. Their mass becomes warmth, not silence.
        return mapOf(
            Register.PITCHMAN to remainder + arenaCeiling,
            Register.GHOST to ghostCeiling,
        ).filterValues { it > 0.0 }
    }

    return mapOf(
        Register.PITCHMAN to remainder * 0.6,
        Register.BIT to remainder * 0.4,      // most of the comedy lives here; it is not the loudest
        Register.ARENA to arenaCeiling,
        Register.GHOST to ghostCeiling,
    ).filterValues { it > 0.0 }
}

// ---------------------------------------------------------------------------
// Recent history — what he has already said, and when
// ---------------------------------------------------------------------------

/**
 * One thing Rip said. `volunteered` separates the two budgets: an escalation rung is not a
 * conversation he chose to start, and it must not eat the budget that governs the ones he did.
 */
data class Utterance(
    val register: Register,
    val trigger: Trigger,
    val at: Long,
    val volunteered: Boolean = true,
)

/** Everything he has said lately. Pure input; nothing in here reads a clock. */
data class SpeechHistory(val utterances: List<Utterance> = emptyList()) {

    fun record(u: Utterance): SpeechHistory = copy(utterances = utterances + u)

    fun arenaInTrailingWeek(now: Long): Int =
        utterances.count { it.register == Register.ARENA && now - it.at < 7.days }

    fun arenaToday(now: Long): Boolean =
        utterances.any { it.register == Register.ARENA && it.at.epochDayOf == now.epochDayOf }

    fun volunteeredToday(now: Long): Int =
        utterances.count { it.volunteered && it.at.epochDayOf == now.epochDayOf }

    /** How many self-initiated conversations he has left today. Enforcement is not counted. */
    fun budgetRemaining(jurisdiction: Int, now: Long): Int =
        (speechBudget(jurisdiction) - volunteeredToday(now)).coerceAtLeast(0)
}

/**
 * THE ABSOLUTE CAP, and it binds before the mix does.
 *
 * RESOLUTIONS §B settled 3/week over 2/week over "two charges", and settled the precedence: `0.10 × j`
 * is a ceiling, this is a wall. Four gates, any one of which is fatal to an ARENA line:
 *   - jurisdiction 0 — he has no jurisdiction to shout about. Shouting anyway is the character
 *     failing to notice he has been fired, which is a different (later, sadder) scene.
 *   - SCOFF-positive — the mocking registers do not exist for this user.
 *   - 20:00 — the arena voice at night is not a bit, it is a neighbour problem.
 *   - once a day, three a week.
 */
fun arenaAllowed(
    jurisdiction: Int,
    gates: ClinicalGates,
    hourOfDay: Int,
    history: SpeechHistory,
    now: Long,
): Boolean {
    if (jurisdiction.coerceIn(0, 4) < 1) return false
    if (!gates.mockingAllowed) return false
    if (hourOfDay >= ARENA_CURFEW_HOUR) return false
    if (history.arenaToday(now)) return false
    return history.arenaInTrailingWeek(now) < ARENA_MAX_PER_WEEK
}

/**
 * CHOOSE THE REGISTER. Pure: caps, gates, trigger, time-of-day and recent history in; one register out.
 *
 * Order of precedence, and it is not negotiable:
 *   1. `CAUGHT_FAKE → DISAPPOINTED`, and **nothing else may produce DISAPPOINTED, ever.** This is the
 *      entire trigger enum (RESOLUTIONS §A2). The inverse also holds and is the more important half:
 *      *he is never disappointed in a miss.* He is blind — he cannot know why the gym did not happen.
 *      Flu, bereavement, a gate meeting that ran long. Being disappointed in a miss is him inventing a
 *      reason to be cruel, and this caps the rate of chronic criticism at a number the user controls.
 *   2. Confession is answered warm, every time. The corpus splits on BIT (§2.2's table and the
 *      authored line in §2.6) vs PITCHMAN (§3.7's prose); *warm* is the load-bearing half and both
 *      are warm. Decided: **BIT**, because two of three sources say so and one of them is the shipped
 *      line — degrading to PITCHMAN when the gates removed BIT.
 *   3. ARENA's absolute cap.
 *   4. The mix.
 *
 * @param roll a draw in [0,1) from the caller's seeded RNG. Kept as a parameter so this stays a
 *   function of its arguments and the soak test can replay 300 days deterministically.
 */
fun chooseRegister(
    trigger: Trigger,
    jurisdiction: Int,
    gates: ClinicalGates,
    hourOfDay: Int,
    history: SpeechHistory,
    now: Long,
    roll: Double,
): Register {
    // 1. The rare event. No share, no cap, no negotiation — and no other route in.
    if (trigger == Trigger.CAUGHT_FAKE) return Register.DISAPPOINTED

    // 2. Confession is free, unlimited and warm. It is never priced in the only currency that
    //    matters — his tone. If confession could draw DISAPPOINTED the gradient re-inverts and the
    //    whole product goes upside down. (RESOLUTIONS §A1.)
    if (trigger == Trigger.CONFESSED) {
        return if (gates.mockingAllowed) Register.BIT else Register.PITCHMAN
    }

    // A collapse gets the quiet voice. He has just been handed his best day and he does not gloat.
    if (trigger == Trigger.COLLAPSED) return Register.GHOST

    // 3. & 4. The mix, minus anything the caps or the clock just took off the table.
    var mix = registerMix(jurisdiction, gates)
    val arenaOk = arenaAllowed(jurisdiction, gates, hourOfDay, history, now) &&
        trigger !in ARENA_FORBIDDEN_TRIGGERS
    if (!arenaOk) mix = mix - Register.ARENA

    return drawRegister(mix, roll)
}

/** Weighted draw. Zero-share registers are filtered out, so a ceiling of 0.0 is unreachable, not rare. */
internal fun drawRegister(mix: Map<Register, Double>, roll: Double): Register {
    val live = mix.filterValues { it > 0.0 }
    if (live.isEmpty()) return Register.PITCHMAN     // he defaults to selling. He always did.
    val total = live.values.sum()
    val r = roll.coerceIn(0.0, 0.999_999) * total
    var acc = 0.0
    for ((register, share) in live.entries.sortedBy { it.key.ordinal }) {
        acc += share
        if (r < acc) return register
    }
    return live.keys.last()
}

// ---------------------------------------------------------------------------
// The line-repeat ledger
// ---------------------------------------------------------------------------

/** One fragment play. Counted, never narrated. */
data class PlayRecord(val fragmentId: String, val at: Long)

/** One rendered line, hashed. SPEC §3.6 — hashed on purpose. */
data class LinePlay(val hash: Long, val at: Long)

/**
 * THE LEDGER. Pure: play history in, choice out. Nothing here reads a clock or a database.
 *
 * Two rules, and reconciling them is the whole trick (RESOLUTIONS §B vs SPEC §3.6):
 *   - **Per-slot retirement, by fragment play count.** `retire_at` is OPENER 20 / OBSERVATION 15 /
 *     ESCALATION 8 / SWERVE 3 / BUTTON 12. Nobody notices the fourth "Okay."; everybody notices the
 *     second swerve. A global retire-at-3 wastes the openers; a global retire-at-20 kills the app.
 *   - **The 120-day window applies to the RENDERED LINE**, not the fragment. It has to: a fragment
 *     barred for 120 days could never reach a retire_at of 20 inside a ten-month product, and the two
 *     rules would silently annihilate each other. So the fragment is priced by *plays* and the
 *     assembled sentence is priced by *time*.
 *
 * The line table is hashed and unreadable by construction. An unhashed table of every line Rip ever
 * said about your failures *is* the rumination infrastructure the 28-day purge exists to delete. The
 * dedupe is enforced; the record is not retrievable. He cannot read you your own file because he
 * cannot read it either.
 */
data class PlayLedger(
    val fragmentPlays: List<PlayRecord> = emptyList(),
    val linePlays: List<LinePlay> = emptyList(),
) {
    fun playCount(fragmentId: String): Int = fragmentPlays.count { it.fragmentId == fragmentId }

    /** Retired means retired. There is no "unless we're running low" branch — running dry is content. */
    fun isRetired(f: Fragment): Boolean = playCount(f.id) >= f.retireAt

    fun lineSeenWithin(hash: Long, now: Long, windowDays: Int = LINE_REPEAT_WINDOW_DAYS): Boolean =
        linePlays.any { it.hash == hash && now - it.at < windowDays.days }

    fun record(line: Line, at: Long): PlayLedger = copy(
        fragmentPlays = fragmentPlays + line.fragmentIds.map { PlayRecord(it, at) },
        linePlays = linePlays + LinePlay(line.hash, at),
    )

    /** The weekly `LineHashPurgeWorker`, as a pure function. 1,095 rows × 16 B ≈ 18 KB before it runs. */
    fun purgeLineHashes(now: Long, windowDays: Int = LINE_REPEAT_WINDOW_DAYS): PlayLedger =
        copy(linePlays = linePlays.filter { now - it.at < windowDays.days })
}

/**
 * Every fragment that is still allowed to speak for this (slot, register, trigger, situation).
 *
 * Four filters, and the trigger pin is the one that matters most: a fragment that declares
 * `trigger = CAUGHT_FAKE` can never be reached under any other trigger, so the DISAPPOINTED rule is
 * enforced twice — once in [chooseRegister] and once in the bank's own metadata. Belt and braces on
 * the only line in the app that accuses the user of something.
 */
fun eligible(
    slot: SlotRole,
    register: Register,
    trigger: Trigger,
    situation: FragmentSituation,
    ledger: PlayLedger,
    bank: List<Fragment> = BANK,
): List<Fragment> = bank.filter {
    it.slotRole == slot &&
        it.register == register &&
        (it.situation == situation || it.situation == FragmentSituation.ANY) &&
        (it.trigger == null || it.trigger == trigger) &&
        !ledger.isRetired(it)
}

// ---------------------------------------------------------------------------
// Assembly
// ---------------------------------------------------------------------------

/**
 * The only path from real history to a rendered line. The key space is `Fragments.kt`'s
 * [FRAGMENT_DATA_SLOTS] — a frozen set with no body, no weight and no food field in it.
 *
 * Returns `null` when the row is gone — which is not an error. It is the `[PURGED]` branch, and it is
 * the single best piece of free material in the app.
 */
fun interface SlotResolver {
    fun resolve(slot: String): String?
}

/** A rendered line, ready to speak. */
data class Line(
    val register: Register,
    val trigger: Trigger,
    val target: Target,
    val text: String,
    val fragmentIds: List<String>,
    val hash: Long,
    val purged: Boolean = false,
)

/** `h = sha256(normalise(renderedText)).take(8).toLong()`. SPEC §3.6, verbatim. */
fun lineHash(text: String): Long {
    val normalised = text.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val digest = MessageDigest.getInstance("SHA-256").digest(normalised.toByteArray())
    var h = 0L
    for (i in 0 until 8) h = (h shl 8) or (digest[i].toLong() and 0xFF)
    return h
}

private val BROTHER = Regex("""\bbrother\b""", RegexOption.IGNORE_CASE)

private fun render(f: Fragment, resolver: SlotResolver): String? {
    var out = f.text
    for (s in f.slots) {
        val value = resolver.resolve(s) ?: return null   // PURGED
        out = out.replace("{$s}", value)
    }
    return out
}

/**
 * THE PURGE WRITES ITS OWN MATERIAL (SPEC §3.4).
 *
 * When a callback's source row is gone, `SlotResolver` returns null and the grammar takes the
 * `[PURGED]` branch: Rip reaches for the bit and it isn't there. Zero authored content, structurally
 * generated, renewable forever, and it is the mechanical seed for the "I don't know" GHOST scene.
 * "I'm going to remember this" is now false, and the app knows it's false.
 *
 * The fragments live in the bank under [FragmentSituation.PURGED]; only slot-free ones qualify, since
 * a purged line that needs the archive to render would be a joke about amnesia that requires a memory.
 */
private fun purgedLine(
    trigger: Trigger,
    ledger: PlayLedger,
    bank: List<Fragment>,
    rng: Random,
): Line? {
    val candidates = bank.filter {
        it.situation == FragmentSituation.PURGED && it.slots.isEmpty() && !ledger.isRetired(it)
    }
    if (candidates.isEmpty()) return null
    val f = candidates[rng.nextInt(candidates.size)]
    return Line(
        register = f.register,
        trigger = trigger,
        target = f.target,
        text = f.text,
        fragmentIds = listOf(f.id),
        hash = lineHash(f.text),
        purged = true,
    )
}

/**
 * THE FIVE-SLOT GRAMMAR.
 *
 * `LINE = [OPENER] + [OBSERVATION(data_slot)] + [ESCALATION?] + [SWERVE?] + [BUTTON]`
 *
 * OPENER / OBSERVATION / BUTTON fire on every line; ESCALATION ~50%; SWERVE ~35%. The optional slots
 * *skip* when the bank has nothing eligible rather than failing the line — that is what makes them
 * optional, and it is also how DISAPPOINTED gets its shape for free: it authors almost no escalations
 * and no swerves, so the rhythm collapses to four words and the silence does the work, with no
 * special case anywhere in this function.
 *
 * Three hard runtime checks, each one a bug that would otherwise ship:
 *   - **the callback constraint.** At rung 1+ the OBSERVATION is restricted to fragments that
 *     actually carry a data slot, so "every line calls back to real history" is true *by
 *     construction* rather than by luck. If the bank cannot serve one for this pairing, that is an
 *     authoring bug and it throws — in CI, at build time, not at 1am on someone's phone.
 *   - **the "brother" check.** Max once per line, and never in DISAPPOINTED or GHOST. The bank's own
 *     lint covers the fragments; this covers the *assembler*, because a perfectly clean bank composed
 *     carelessly still says "brother" twice in one breath.
 *   - **the 120-day check**, on the rendered hash.
 *
 * @param requireCallback true for rung 1+. SPEC §3.3.
 * @return null when the bank cannot serve this register — running dry is content, not a crash.
 */
fun assemble(
    register: Register,
    trigger: Trigger,
    ledger: PlayLedger,
    resolver: SlotResolver,
    now: Long,
    rng: Random,
    situation: FragmentSituation = FragmentSituation.ANY,
    requireCallback: Boolean = true,
    bank: List<Fragment> = BANK,
    attempts: Int = 24,
): Line? {
    repeat(attempts) {
        val slots = buildList {
            add(SlotRole.OPENER)
            add(SlotRole.OBSERVATION)
            if (rng.nextDouble() < ESCALATION_RATE) add(SlotRole.ESCALATION)
            if (rng.nextDouble() < SWERVE_RATE) add(SlotRole.SWERVE)
            add(SlotRole.BUTTON)
        }

        val picked = mutableListOf<Fragment>()
        var mandatoryMissing = false
        for (slot in slots) {
            var candidates = eligible(slot, register, trigger, situation, ledger, bank)

            // His archive is the writers' room, and this is the line that enforces it.
            if (slot == SlotRole.OBSERVATION && requireCallback) {
                val withCallback = candidates.filter { it.slots.isNotEmpty() }
                if (withCallback.isEmpty() && candidates.isNotEmpty()) {
                    throw IllegalStateException(
                        "The bank has OBSERVATIONs for ($register, $situation, $trigger) but not one " +
                            "of them calls back to real history. A line that could have shipped in " +
                            "1994 is exactly the line that dies in month 2.",
                    )
                }
                candidates = withCallback
            }

            if (candidates.isEmpty()) {
                // OPENER / OBSERVATION / BUTTON are load-bearing; the other two are garnish.
                if (slot == SlotRole.ESCALATION || slot == SlotRole.SWERVE) continue
                mandatoryMissing = true
                break
            }
            picked += candidates[rng.nextInt(candidates.size)]
        }
        if (mandatoryMissing) return null

        val rendered = picked.map { render(it, resolver) }
        if (rendered.any { it == null }) return purgedLine(trigger, ledger, bank, rng)

        val text = rendered.filterNotNull().joinToString(" ").trim()

        // "brother": max once per line, and dropped entirely when he is serious. Arsen learns the
        // tell by month two without being taught, and from then on its ABSENCE hits harder every
        // time. A tic with negative decay — and exactly one leaky line kills it forever.
        val brothers = BROTHER.findAll(text).count()
        if (brothers > 1) return@repeat
        if (brothers > 0 && (register == Register.DISAPPOINTED || register == Register.GHOST)) {
            throw IllegalStateException(
                "The assembler emitted \"brother\" under $register. That is the best tell in the app " +
                    "and it only has to leak once to be dead. Fragments: ${picked.map { it.id }}",
            )
        }

        val hash = lineHash(text)
        if (ledger.lineSeenWithin(hash, now)) return@repeat

        return Line(
            register = register,
            trigger = trigger,
            // The OBSERVATION names what he is actually talking about; the opener is throat-clearing.
            target = picked.first { it.slotRole == SlotRole.OBSERVATION }.target,
            text = text,
            fragmentIds = picked.map { it.id },
            hash = hash,
        )
    }
    return null
}

/**
 * THE WHOLE ENGINE, as one pure function: history in, choice out.
 *
 * Returns null when he has nothing to say, which is a first-class outcome and not a failure:
 *  - the volunteered budget is spent (at jurisdiction 0 it is spent before the day starts — the Tape
 *    only, forever, and that is the ending);
 *  - or the bank cannot serve the drawn register.
 *
 * Enforcement speech bypasses the budget entirely (`volunteered = false`) — RESOLUTIONS §B: that
 * budget is ungoverned here and gated by the ladder.
 */
fun speak(
    trigger: Trigger,
    jurisdiction: Int,
    gates: ClinicalGates,
    hourOfDay: Int,
    history: SpeechHistory,
    ledger: PlayLedger,
    resolver: SlotResolver,
    now: Long,
    rng: Random,
    situation: FragmentSituation = FragmentSituation.ANY,
    volunteered: Boolean = true,
    requireCallback: Boolean = true,
    bank: List<Fragment> = BANK,
): VoiceDecision? {
    if (volunteered && history.budgetRemaining(jurisdiction, now) <= 0) return null

    val register = chooseRegister(trigger, jurisdiction, gates, hourOfDay, history, now, rng.nextDouble())
    val line = assemble(
        register, trigger, ledger, resolver, now, rng, situation, requireCallback, bank,
    ) ?: return null

    return VoiceDecision(
        line = line,
        history = history.record(Utterance(line.register, trigger, now, volunteered)),
        ledger = ledger.record(line, now),
    )
}

/** What he said, plus the two ledgers that now know he said it. */
data class VoiceDecision(
    val line: Line,
    val history: SpeechHistory,
    val ledger: PlayLedger,
)

// --- small helpers ---------------------------------------------------------

internal val Long.epochDayOf: Long get() = this / 86_400_000L
