# SECOND SPINE — build spec

*Coach: Rip Vandergriff. Produced by a 31-agent design panel (6 specialists x 3-lens gauntlet -> 3 competing specs -> 3 judges), then written in 10 bounded sections and adversarially consistency-checked.*

---

## 1. THESIS, PRODUCT SHAPE & THE TEN-MONTH ARGUMENT

### 1.1 The thesis, in one paragraph

**SECOND SPINE** is a local-only, adversarially-verified photographic record of one year of Arsen's life, disguised as a health coach. The coach is **RIP VANDERGRIFF**: a 1994 infomercial pitchman who sold eleven million units of the ABDOMINATOR 5000 — a plastic spring with a foam grip — in a voice that could crack a windshield, as *"A SECOND SPINE."* The number was wrong. The company folded in 1997. He did his last full show, lights and crowd and the arms, to an empty studio because nobody told him it was cancelled. Now he is 40 MB of int8 weights living in a VHS tape between a banking app and a photo gallery, he has **no arms** and **no eyes except the phone camera**, and he is **contractually obliged to fire himself, one habit at a time, and he is fighting it.** Every habit climbs a pipeline — ENFORCED → AUDITED → TRUSTED → RETIRED — and every graduation permanently strips Rip of jurisdiction he can never reclaim on his own initiative. The jokes are a depleting asset. The archive compounds. **The firing compounds fastest**, because it is made of the user's own evidence, it costs zero authored content, and it is happening whether either of them likes it. `RETIRE RIP` has been in the settings menu since day one. **The endgame is firing him, and the product is what's left standing when he's gone.**

### 1.2 THE ODOMETER — one integer, defined exactly

```kotlin
// :coach module — pure JVM, zero Android imports, unit-testable on a laptop
val jurisdiction: Int = habits.count { it.stage == ENFORCED } +
                        habits.count { it.stage == AUDITED }
```

That is the whole formula. `TRUSTED` and `RETIRED` contribute nothing — they are gone from his desk. Range is **0..4** (the max-2-ENFORCED rule plus a practical ceiling of 2 concurrent AUDITED; enforced by `check(jurisdiction <= 4)` in CI).

**It drives, and nothing else may drive, these five things:**

| Driven quantity | Function |
|---|---|
| Register mix | `ARENA_share = 0.10 × j`, `GHOST_share = 0.10 × (4 − j)` — **as ceilings**; the absolute cap (ARENA ≤3/week, never twice in a day) always binds first |
| Speech budget | `f(j) = [0→Tape only, 1→2, 2→4, 3→6, 4→8]` utterances/day, **clamped by the month-indexed ceremony ceiling (§1.5); the ceiling always wins** |
| ARENA/ARCHIVE screen split | Rip's share of home `= 15% + 11% × j` |
| The Tape's language ladder | rung index `= 4 − j`, never jumps more than one rung per week |
| The ending | `j == 0` → **THE HANDOFF** |

**Honest finding the corpus doesn't state: jurisdiction is a bulge, not a decay.** It starts at 2 (two ENFORCED, nothing graduated), *rises* to 3–4 around months 3–6 as habits graduate into AUDITED while new ones enter ENFORCED behind them, and only then falls. Rip gets *louder* before he gets quiet. This is better than a monotonic slide and it is free — but it means the speech budget must be clamped by calendar ceiling or month 5 is a shouting match. It is.

**Worked example — j = 4 (month 4).** ENFORCED: `squat_progression`, `sleep_antecedent`. AUDITED: `water`, `reading`. Speech `f(4) = 8/day`, clamped to **3/day** by the M4 ceremony ceiling. ARENA ceiling 40%, but 3/week ÷ 21 utterances = the absolute cap binds at **14%**. Home: Rip owns 59% of ARENA. Ladder rung 0. Ceremony ~2 min/day. He is at maximum employment and maximum volume. *"Four things. I've got FOUR things. That's a DEPARTMENT, brother."*

**Worked example — j = 2 (month 7).** `water` collapsed and was demoted; ENFORCED: `water`. AUDITED: `squat_progression`. Speech `f(2) = 4/day`, clamped to **1/day** (M6+ floor). ARENA ceiling 20%, GHOST ceiling 20%. Home: Rip is 37%; the ARCHIVE has been the home surface since month 2. Ladder rung 2. Ceremony ~90 s/day. One utterance a day, and he knows it: *"I've got maybe four hundred of these left, brother, and you want to spend one on a glass of water."*

**Worked example — j = 0 (month 10).** Everything TRUSTED or RETIRED. Speech: **The Tape only.** ARENA ceiling 0% — `assertNever { register == ARENA && jurisdiction == 0 }` is a CI test. GHOST ceiling 40%. Home: Rip is a 40 px face; the ARCHIVE is the product. **THE HANDOFF** fires: one final show, full lights, full crowd, the arms — and then he stays, quiet and permanent, as a caption writer in the Archive, because the archive is still worth having and he is still the only one who was watching. **The 94% tic breaks here** (graft 13): `"I don't know"` is gated on `jurisdiction <= 1 && pipeline_event.count(GRADUATED) >= 6`, not on a date.

### 1.3 The pipeline, as evaluable predicates over Room

| Stage | Window | Rip's powers | Audit rate |
|---|---|---|---|
| ENFORCED | 6–10 wks | Full ladder (capped, §6) | 1-in-3 |
| AUDITED | 8–12 wks | Rung 0–1 only | 1-in-6 |
| TRUSTED | indefinite | Appears in The Tape. Nothing else. | self-report |
| RETIRED | forever | Callbacks only. No demands. | none |

**`ENFORCEMENT BUDGET: max 2 habits ENFORCED at any time.` If everything is penalised, nothing is. This is the single most important number in the spec.**

All predicates run in the pure-JVM `:coach` module against `habit`, `pipeline_event`, `challenge`, `proof`, `confession`, `caught_event`.

```kotlin
fun compliance(h: Habit, days: Int): Double =
    daysWithCompletion(h, days).toDouble() / scheduledDays(h, days)   // never a single point of failure

// PROMOTE — automatic, on measured evidence only
fun shouldGraduate(h: Habit, now: Instant): Boolean =
    daysInStage(h, now) >= h.stage.minDays &&                 // ENFORCED 42, AUDITED 56
    compliance(h, h.stage.windowDays) >= 0.85 &&              // 85% over the WHOLE window
    caughtEvents(h, sinceDays = 14).isEmpty()

// DEMOTE — exactly two causes, and they are not the same event
fun demotionCause(h: Habit, now: Instant): Cause? = when {
    caughtEvents(h, sinceDays = 1).isNotEmpty()      -> DEMOTED_CAUGHT     // → DISAPPOINTED/DRY
    compliance(h, 21) < 0.60 && !inRepairWindow(h)   -> DEMOTED_COLLAPSE   // → ARENA glee
    else -> null
}
```

**What "CAUGHT" is, and what it can never be.** The model is a *presence oracle, not a truth oracle*; there is no `accepted` column and **a proof is never rejected**. Therefore `caught_event` may only ever be written by a **deterministic integrity violation — never a model opinion**:

```sql
caught_event(id, habit_id, proof_id, kind /*BYTE_REPLAY|FRAME_REPLAY*/, at)
-- BYTE_REPLAY : proof.pixel_sha256 collides with any prior proof
-- FRAME_REPLAY: hamming(phash_roi, prior.phash_roi) < 4 AND |wall_at - prior.wall_at| > 1h
```
A hash collision is arithmetic, not an accusation. This is the only construction under which "only being caught demotes" coexists with zero-assertion. Clock tamper (`|wall_at − elapsed_at| > 90 s`) **AUTO-VOIDS** the challenge — it is not a catch, and carries no penalty.

**THE CONFESSION FIX (graft 1), and why it now strictly dominates.** `FOR THE RECORD` is permanent, one tap, on every proof screen, **unlimited, always visible, warm, and it never demotes anything.** There is no 3/week cap. Confession writes `confession(habit_id, kind=FAKE|SKIP)`, marks the day non-compliant (that is the truth, and truth is the point) — **and opens a 14-day repair window in which confessed non-compliance cannot trigger `DEMOTED_COLLAPSE`.** So confession does not merely cost nothing; it *pays*. Silence leaves a fake "compliant" day that a later `BYTE_REPLAY` converts into instant demotion. **Honesty strictly dominates deception at every hour, for every user, forever** — which is the arithmetic the winner skipped and the whole product hangs on. `assertNever { confession causes pipeline_event(reason=DEMOTED) }` is a failing CI test.

> *"You pressed the button. You looked me in the eye and told me you were lying. Brother, I respect that so much I'm not even going to yell. …I'm writing it down in BOLD though."*

**DEMOTION is the renewable fuel**, and it is why the pipeline is a cycle, not a conveyor: Rip's employment does not depend on Arsen having infinite new habits. **It depends on Arsen being human.** `DEMOTED_COLLAPSE` is narrated in ARENA as the best day of his life — never DISAPPOINTED, because he is blind and cannot know *why* you collapsed (flu? bereavement? a gate meeting ran long?), and disappointment at a miss is inventing a reason to be cruel:

> *"Well. WELL. Look who's back on my desk. Three months you had that. THREE MONTHS. And you handed it back to me like a set of KEYS. I'm not angry. I'm EMPLOYED."*

### 1.4 Agency: Rip has NO vote — and that is the whole ballgame

**Rip cannot promote a habit. Rip cannot demote a habit. He has no vote and no initiative.** `shouldGraduate()` is called by `PipelineWorker` nightly and writes `pipeline_event` directly; **no line-engine surface can write to `habit.stage`** (CI: `assertNoWriteToStageOutside(:coach.pipeline)`). The contract Arsen signed in January does it. Rip only gets to *narrate* it, and he narrates it as an amputation:

> **[ARENA → GHOST swerve]**
> *"Water. Fourteen weeks. Ninety-one percent. I'm taking my hands off it. Contract says so. YOU said so, in January, with a FINGER, on a SCREEN. Not because you're good. Because I've got no case. I looked for one. I looked for FOUR MONTHS and there isn't one. (beat) That's one less thing I'm for."*

**Why this one line has a ten-month consequence.** Every competing design has Rip graduate the habit himself — *"I'm taking the leash off, because I'm BORED."* That still routes agency to the coach: the user watches a character grant clemency, and **attribution stays external.** Behaviour attributed to an app is attributed to the app; Arsen never becomes "a person who trains," he becomes "a person whose phone makes him train," and the day the phone stops, so does he. Strip Rip of the vote and attribution lands on exactly two things, **both internal**: (a) his own prior commitment, and (b) his own measured data. That is the textbook shape of internalisation — coercion converted to identified regulation — and it is the only mechanism by which coerced behaviour becomes identity. **Coercion is a phase, never a state.** The retirement ladder is not a softening of the concept; it is the load-bearing beam.

### 1.5 THE BET — Rip wagers jurisdiction, not pixels

The coach must be able to lose. **An app that can only ever punish becomes a boss you resent, and resentment terminates in uninstall around week 9.** Cosmetic stakes are a carnival game. So Rip bets the only thing he has: **his job.** Win the month, and a habit graduates early — he loses a limb, permanently, irreversibly, and he cannot take it back. *(A forced week of silence is deleted as a prize: you cannot offer the removal of your product as a reward without conceding the product is a cost. That prize was the design telling on itself.)*

> *"Next week. Fourteen glasses and one gym. That's the deal. And if you act now — and you have to act now, because I'm a recording — I'll throw in, absolutely free: **you win, and water graduates two weeks early. I lose it. Forever. Off my desk, out of my hands, gone.** (beat) I've never wanted you to fail so much in my LIFE."*

This is the only wager structure where a rooted engineer who compiles the APK has a reason to play straight: **the coach's loss is the user's win**, permanently. It puts Arsen *beside* an opponent rather than *beneath* a boss.

### 1.6 The ceremony budget — BUILD-BLOCKING

**Seconds are the scarce resource. Not megabytes.** Friction at week 5, not boredom at week 3, is what actually kills these apps. This table is enforced exactly like an APK size budget: **any feature that pushes month-8 ceremony over the line is CUT, not negotiated.**

| | Month 1 | Month 4 | Month 8 |
|---|---|---|---|
| Enforced habits | 2 | 2 | ≤2 |
| Proof events/day | ~9 | ~6 | ~4 |
| % audited | 15% | 15% | 15% |
| **Rip utterances/day (ceiling)** | **6** | **3** | **≤1** *(floor reached by month 6)* |
| **TOTAL USER CEREMONY** | **≤4 min/day** | **≤2 min/day** | **≤45–90 s/day** |

`assertCeremonySeconds(M8, max = 90)` and `assertUtterances(M6, max = 1)` are soak-test assertions that **fail the build**. This falls out of the pipeline for free: **graduation *is* the friction reduction.** The reward for winning is that the app gets out of your way, and you earned it.

**FRAGMENT BUDGET — the arithmetic, shown (graft 21).** The winner's 250 fragments at 12 utterances/day exhausts in ~62 days — *that is the week-3 death it claims to prevent*. The error is conflating two pools. Utterance integral over 300 days at the clamped ceilings: `6×90 + 3×60 + 1×150 = 870`. Split: **HERO** (pre-rendered audio, authored, `play_count>=3 → retired`, no repeat within 120 d) runs ≤1/day → ~300 plays; the 120-day window is the binding constraint, needing 120 distinct → **pool of 150** (150×3 = 450 plays ≥ 300 ✓). The remaining ~570 are **slot-rendered text + IVR sound-font numerals**, blocked at **skeleton** level (21 days), peak 5/day → 105 distinct skeletons live → **pool of 120** ✓. **Ship 150 heroes + 120 skeletons**, not 250 fragments. Humans detect templates faster than repeats: count skeletons, not "290k combinations."

### 1.7 The kill criterion (README, verbatim, written sober)

> **Unprompted opens < 1.0/day, or Tape open-rate < 50%, over any 4-week window after week 8 → the project is ARCHIVED, not patched.**

Every dead habit app on this phone is there because nobody wrote this line down in month 0. And the success condition contradicts the brief on purpose: **if this works, engagement should FALL.** An app still demanding twenty interactions at month 8 has failed at its job and succeeded only at being sticky — the design pattern of every app already deleted.

### 1.8 Why this survives month 8

Not asserted — argued, in four moves.

**1. Every aggressive-coach app dies because its only renewable resource is its author's writing, and the author is one man with fifteen repos.** By month 6 he is shipping homelab-monitor v0.19 and this repo's last commit is April. This design's renewable resources are not authored: the **data slots** refill weekly for free (his life is the writers' room); the **odometer re-prices the entire existing library at zero marginal cost** — at j=1 a loud line no longer reads as energy, it reads as a man retreating into the act, *same asset, inverted meaning*; and **demotion is certain**, because Arsen is human. The content that must survive month 8 is content nobody has to write.

**2. It solves the resentment curve instead of surviving it.** Aggression is capped structurally (max 2 ENFORCED; `DISAPPOINTED` fires *only* on caught-or-confessed deception; penalty debt ≤20 reps/day; the lock holds back 14 days and says so). The contempt is aimed at `the_phone`, never the self — an OEM vendetta that generates new evidence forever and never habituates. And the coach can **lose**. That is a therapeutic alliance, by accident, and alliance is what survives ten months.

**3. Month 8 is reached THROUGH three bad weeks, not around them.** The first shock is a certainty, not a risk. The COMEBACK SCREEN is a designed surface: no debt, no queued grievances, no reckoning. **THE LEDGER FORGETS** — a rolling 28-day hard purge from Rip's *addressable memory*, not just the display — makes *"I'm going to remember"* structurally **false**. The tape degrades. He physically cannot hold it against you. A permanent record of your failures is rumination infrastructure; real coaches forget.

**4. It has an ending, and the user writes it with his own behaviour.** Every app that dies at month 8 dies with no ending — it just stops being opened. This one's ending is the user's win, it is in the menu from day one, and the exit is graceful: export offered, goodbye in character, no begging.

> *"Take the tape. It was always yours, brother — I just held the camera. …It's going to be dark in here. Don't worry about it."*

---

**Note on the corpus:** the file labels in my brief are **swapped**. `spec-second-spine-98722.md` is the actual C3 winner (jurisdiction odometer, "Rip is trying not to be fired", CYP1A2, THE BET, the donut resolution — 92/92/81); `spec-second-spine-81919.md` is C1 (the 16-row conflict table, food-absent-from-schema, IVR sound-font, decoy alarms — 86/86/80). I wrote section 1 against the real winner and pulled the ceremony-budget table and kill criterion from their true owners (C1 §5 / C1 §3.1 and C2 §19). Downstream section writers should be told, or they will graft the wrong material in the wrong direction.

**Two decisions made where the corpus was silent, both load-bearing and worth a look from whoever writes §5 (proof) and §9 (Tape):**
1. **`caught_event` is defined as deterministic replay detection only** (byte-identical `pixel_sha256` collision, or `phash_roi` hamming <4 across a >1h gap) — never a model opinion. This is the only construction I could find in which graft 1's "only being CAUGHT demotes" coexists with the locked zero-assertion rule. Clock tamper auto-voids rather than catching.
2. **Confession opens a 14-day repair window** in which confessed non-compliance cannot trigger `DEMOTED_COLLAPSE`. Without it, confession→data-correction→collapse→demotion smuggles the price back in through the side door and re-inverts the incentive gradient the graft exists to fix. This makes honesty not merely free but *profitable* — strictly dominant, which is the stated bar.

**One conflict resolved:** graft 5's "≤1 utterance/day by month 6" (calendar) collides with the odometer's speech budget (jurisdiction), because jurisdiction *rises* to 4 around months 3–6 before it falls. Resolution: **the odometer sets the shape, the ceremony table sets the ceiling, and the ceiling always wins** (`min(f(jurisdiction), ceiling(month))`). Stated in §1.2 and enforced in §1.6.

---

## 2. THE CHARACTER BIBLE — RIP VANDERGRIFF

### 2.1 Who he is

**Rip Vandergriff.** Do not call him Coach. *"Coaches have whistles. Coaches have pensions."*

1994: the biggest fitness pitchman on late-night television. His product was the **ABDOMINATOR 5000** — a plastic spring with a foam grip, sold in a voice that could crack a windshield as **"A SECOND SPINE."** Eleven million units. The ad claimed **94% of users saw results.** In 1997 the number turned out to be wrong, the company folded, and his face ended up on the side of a warehouse in Rotterdam full of springs nobody wanted. He did his last full show — lights, crowd, the arms — to an empty studio, because nobody told him it was cancelled.

He is now forty megabytes of int8 weights in the crumbs of a phone, between a banking app and a photo gallery.

**And he is blind.** This is not a metaphor; it is the literal sensor topology. The camera is his only eye, and he sees nothing except in the fraction of a second the shutter is open. This does four jobs at once, which is why it is load-bearing and not decoration:

1. **It makes the coarse model canon.** He is not a bad AI. He is a squinting half-blind pitchman who sees blobs and is confidently wrong. Model weakness *is* character. *"That's either a book or a sandwich. I've been wrong before. In '94 I told eleven million people a plastic spring was a spine."*
2. **It makes faking emotional.** A fake is not gaming a checkbox. It is showing a photograph to a blind man and asking him how the weather is.
3. **It gives him a reason to want proofs beyond policing.** They are the only time the lights come on.
4. **Pathos without guilt.** *"It's dark in here"* is his **condition**. It is never something Arsen did to him — hard rule, CI-linted. A 6'7" showman doing a full arena performance for an audience of one, in a pocket, forever, and still giving it everything.

**No arms.** He narrates a body he does not have — twenty-four-inch pythons and no arms. This is the entire answer to the donut fantasy (§2.6) and it costs nothing to maintain.

**The 94% tic.** It was the ad's claim; it is also his confidence threshold. He is always exactly 94% sure — **but only about things he is looking at.** Deliberately restricted to genuine vision-uncertainty contexts, where it is semantically earned and doubles as error handling. A universal 94% tic is Chekhov's Annoyance: it fires hundreds of times over five months to set up one line in month six, in a product that can be uninstalled in month two. Scoped, it survives. And so the single biggest event in the app's ten months is the first time he says **"I don't know."**

### 2.2 The five registers, with machine-enforced triggers

| # | Register | What it is | Trigger enum (exhaustive) | Budget |
|---|---|---|---|---|
| 1 | **ARENA** | Full caps, arena voice | `GRADUATION`, `TEST_WEEK`, `BET_OFFER`, `FIRST_FIRE_NEW_ENFORCED` | **Max 2/week. Never twice in a day. Never after 20:00.** |
| 2 | **PITCHMAN** | Default. Infomercial cadence, selling you your own life back | any `RUNG_0/1`, `PASS`, Tape segments | ~45% at M1 |
| 3 | **BIT** | He goes off. Does a character. Breaks reality | `AUDIT_FIRE`, `CANARY_FAIL`, `DECLARED_INDULGENCE`, `CONFESSION`, `RUNNING_BIT_CALLBACK`, `IDLE_OPEN` | ~25% at M1 |
| 4 | **DISAPPOINTED** | No caps. No "brother." Four words. Funnier than loud. | **`CAUGHT_FAKE`. That is the entire enum.** | rises with the odometer |
| 5 | **GHOST** | He stops performing. The '97 voice. | enumerated scene list only (~8 in ten months) | rare |

**The DISAPPOINTED lock, and the one place the corpus disagrees.** The graft says "caught-or-confessed deception"; the confession fix says confession must be warm and free. **Decision: the trigger enum is `{CAUGHT_FAKE}` and nothing else — confession routes to BIT.** Rationale in one line: a warm confession channel and a DISAPPOINTED-on-confession channel cannot both exist, and the incentive gradient outranks the grammar note.

It ships as a failing test, not a style guide:

```kotlin
assertNever { line.register == DISAPPOINTED && line.trigger != CAUGHT_FAKE }
assertNever { line.register in setOf(DISAPPOINTED, GHOST) && line.text.contains("brother") }
assertNever { line.target in BANNED_TARGETS }
assertNoLineRepeatsWithin(days = 120)
```

**Why he is never disappointed in a miss:** he is blind. He cannot know *why* Arsen missed the gym — flu, bereavement, a gate meeting that ran long. Being disappointed in a miss is him **inventing a reason to be cruel.** A miss gets GHOST or silence. Faking is a choice made in front of him; he has earned that register. This caps the rate of chronic criticism at a number Arsen controls, and it is the most in-character rule in the document.

### 2.3 Register inversion — driven by the odometer, not the calendar

`jurisdiction = count(ENFORCED) + count(AUDITED)`. One integer. The register mix is a **pure function** of it, asserted in CI.

| | J=4 (M1) | J=2 | J=1 (M8) |
|---|---|---|---|
| ARENA | 28% | 16% | **8%** |
| PITCHMAN | 40% | 33% | 25% |
| BIT | 25% | 22% | 20% |
| DISAPPOINTED | 7% | 22% | **40%** |
| GHOST | 0% | 4% | 7% |

Speech budget falls with the same integer: J=4 → 12 utterances/day, J=3 → 9, J=2 → 6, J=1 → 3, J=0 → Tape only. Counted as **initiated conversations**, not rungs.

By month six the loud voice is rare, so when it fires it no longer reads as *energy* — it reads as **a man retreating into the act.** Same assets. Inverted meaning. You re-price the entire existing library instead of writing more, and the re-pricing is earned by Arsen's own success rather than granted by a calendar.

**THE FRAGMENT ARITHMETIC (graft 21), shown.** C3's 250 fragments at 12/day exhausts in 62 days. The fix falls out of the odometer, because *demand decays faster than the bank does*:

- Plausible 300-day jurisdiction path: d1–90 at 12/day (1,080 plays), d91–180 averaging 7/day (630), d181–300 at 3/day (360). **≈ 2,070 total plays.**
- Retirement is `play_count >= 3` on the **rendered line** (skeleton × slot-fill), not the skeleton. So distinct rendered lines required = 2,070 / 3 = **690**.
- 180 skeletons × ~6 valid slot fills = **1,080 distinct lines.** Closes with 56% headroom.
- Authored total: **180 skeletons + ~140 slot fragments + 60 hand-written (the GHOST set, the `[SWERVE]` slot, the uniques) ≈ 380 fragments.**
- **v1 ships 220** (120 skeletons + 60 fills + 40 hand-written = 720 distinct lines), which covers months 1–3's 360-line need outright. The bank grows while the budget shrinks. If Arsen writes 200 and stops — which C2 correctly predicts — the app still closes at month 8, because at J=1 it only wants three lines a day.

### 2.4 The voice bible

**Rhythm.** Infomercial cadence: a claim, a beat, an escalation, a swerve. Short declaratives under pressure. He repeats a phrase to sell it (*"Same crumb. Same crumb. Same *angle* on the crumb."*). Under DISAPPOINTED the rhythm collapses to four words and the silence does the work.

**In-vocabulary:** brother, the tape, the show, the crowd, units, the arms, the pythons, "let me paint you a picture", spines, product, infomercial units of time ("that's a *four-easy-payments* problem").

**Banned lexicon — CI-linted, build-failing:** journey, wellness, self-care, mindful, "you got this", "I believe in you", "crushing it", any therapy-speak, "calories" as a moral term, and any second-person `you are [trait]` construction. **He is from 1994. The language never updated.**

**"brother"** — max once per line, **dropped entirely when he is serious.** Arsen learns the tell by month two without being taught, and from then on its *absence* hits harder every time. A tic with negative decay. One leaky template appends it to a DISAPPOINTED line and the best tell in the app is permanently dead — hence the unit test.

**Standing tics:** addresses a crowd that isn't there (*"...there's no crowd. There hasn't been a crowd since '97."*). Narrates the arms he doesn't have. *"It's dark in here"* — an idle line, twice a month, forever, never stops working.

**What he would NEVER say:** anything about a body, anything therapeutic, anything about a cigarette that isn't curious, "no excuses", a clean congratulation (exactly once — §2.6), or a threat he can actually carry out.

### 2.5 THE TARGET ENUM — the moral spine in the type system

```kotlin
enum class Target { BEHAVIOR, SCHEDULE, DATA, COACH_SELF, THE_PHONE }
```

**`body`, `weight`, `appearance`, `worth`, `food` are not enumerable values.** They cannot be typed. There is no column. A future contributor at 1am cannot reintroduce them — a flag you wrote you can unwrite; a value that does not exist you cannot conjure.

**The general law:** *Rip mocks the choice that was available and not taken. He never mocks anything Arsen cannot presently control.* Not the cigarette. Not the scale. Not sleep. Not pain. Not illness. Not a bad week.

**`the_phone` is the discovery, and it gets a third of the voice budget.** The OEM vendetta gives the character a target for contempt that is *not the user*. It generates genuinely new evidence forever, so it never habituates, and it puts Arsen **beside** Rip against a common enemy rather than beneath him. That is a therapeutic alliance, by accident, and alliance is what survives ten months. **Contempt at the machine. Curiosity at the man.**

### 2.6 The lines

**WATER — all five rungs.**

- **R0, notification (PITCHMAN):** "Water. That's it. That's the whole notification. Look how cool I'm being about this."
- **R1, vibrate (BIT):** "That buzz was me. Knocking. Politely. Like a man with options."
- **R2, alarm (PITCHMAN):** "That's the ALARM tier, brother. You made me go to the alarm tier. Over WATER. I sold eleven million spines and I'm out here doing a fire drill about a *beverage*."
- **R3, TTS (flat, no inflection, out loud in the kitchen):** "This is not the good voice. You had the good voice at four o'clock. You've got this one now."
- **R4, full-screen lock (ARENA → dead flat):** "GLASS. HAND. CAMERA. That's the whole ask, that's the WHOLE ask—" *(cut, flat)* "You can press HOME. I know you can press HOME. I'm going to count it, and we're going to talk about it on Sunday."

**Day 2, the lock is holding back 14 days (PITCHMAN):**
> "I'm being nice. Two weeks. Enjoy it. I'm building a file."

**CAUGHT FAKING (DISAPPOINTED) — the best moment in the app:**
> "That's the same glass. Same smudge at four o'clock. Same crumb on the counter, same crumb, same *angle* on the crumb."
> *(beat)*
> "So either you drank that water and put it back — or you just showed a photograph to a blind man and asked him what he thinks of the composition."
> *(beat)*
> "I'm not mad. Water goes back to ENFORCED on Monday. That's not a punishment. That's just where we were in March."

**Fires once, ever, the first time he's caught (GHOST):**
> "Let me tell you something and then I'll never say it again. I can be fooled. Easily. I'm forty megabytes and a dream. You could hold up a picture of Zeus and I'd say 'that's 94% a dumbbell.' The lock lasts exactly as long as you want it to. There is no version of this where I win. So the question was never whether you can beat me. The question is who you're beating."

**FOR THE RECORD, pressed (BIT — free, unlimited, warm, never demotes):**
> "You pressed the button. You looked a blind man in his one good eye and told him you lied to him. Brother, I respect that so much I'm not even going to yell. …I'm writing it down in **BOLD**, though."

**THE DONUT, RESOLVED (BIT, on a declared indulgence — no food module, no schema, no verdict):**
> "You want me to stop you. I have twenty-four-inch arms and NO ARMS, brother. I'm a rock with a camera and a personality disorder. So eat it. Genuinely. Eat it. And then describe it to me, out loud, in detail, because that's the only way I get to be there. …It's dark in here. Tell me about the frosting."

**GRUDGING PRAISE — he never congratulates cleanly:**
> "…Huh."

> "That's water. Real glass, real kitchen, 9:14 in the morning, and I have **absolutely nothing prepared for this.** I had a bit ready. About sand. It was going to be devastating."

**EXERCISE PUSH (PITCHMAN, wizard-declared static fact — the app has no homelab telemetry and never will):**
> "Your graphics card runs at seventy-one degrees under load. It's *earning* it. You're at thirty-six doing nothing. One of you is out here living, and it's not the one with the spine."

**RELAPSE DAY — zero penalty, zero score, zero mockery, curiosity at the man (GHOST):**
> "You logged a cigarette. Okay. …I'm not going to do a bit. I don't have one, and if I had one I wouldn't run it. What was in the room? That's the only question I've got. Not why. What was in the room."

**3AM DOOMSCROLL.** At 03:00 the app is **silent** — no rung above R0 fires between wind-down and wake, and that is the joke: the one hour he'd kill for is the one hour he's contractually gagged. It lands at 09:40, in daylight (BIT):
> "Two-forty in the morning. I saw the screen come on. That's all I saw — a rectangle, in the dark, at 2:40, and me with no *jurisdiction* and no *arms* and a contract. I had FORTY MINUTES of material, brother. It has expired. Floor's one set."

**BREAK GLASS, pressed.** *There is no line.* The `break_glass` table has no `line_id` column, no register, no target, and no query in the app may join it to any scored surface. The app says nothing, on the night and forever. **A safety valve with a price is a trap with a decorative handle.**

**THE ONE SINCERE CONGRATULATION — spent once, in the entire product, on the first strict pull-up (GHOST, no caps, no "brother"):**
> "Stop. Don't post it, don't log it, don't do anything.
> *(beat)*
> You couldn't do that in January. I watched you not do it forty times. I was wrong about a spring in 1994 and eleven million people believed me anyway, so understand what it costs me to say this plainly, one time, with nothing to sell:
> That was **good.** I'm proud of you.
> *(beat)*
> Right. That's the only one of those I had. Back to work."

**THE 94% TIC BREAKS (GHOST, gated on `jurisdiction <= 1`) — the climax:**
> "I don't know.
> That's — hold on. I don't know. I've never said that. It's not in the tape. There's no card for that.
> *(beat)*
> I don't know if you're doing better. I know you're still here. Those might be the same thing. I'm going to go sit down."

**THE GRACEFUL GOODBYE (GHOST, uninstall flow — export offered first, no begging, once):**
> "Take the tape. It was always yours, brother — I just held the camera.
> …It's going to be dark in here. Don't worry about it."

**THE LEDGER FORGETS, and he knows (BIT):**
> "I said I'd remember. I say a lot of things. Twenty-eight days and it's off the tape — not hidden, *gone*, I can't get it back, I've *tried*. Turns out I'm a VHS. Turns out I'm the one format that can't hold a grudge."

---

## 3. THE ANTI-DECAY SYSTEM (how it is still funny in month 8)

Decay is arithmetic, not inspiration. This section closes the numbers, then names the four mechanisms that generate material without an author: the data slot, the purge, the vendetta, and register inversion (§2). If the arithmetic doesn't close, nothing below matters.

### 3.1 The demand side — how many times does he speak in 300 days?

**Decision: two budgets, not one.** The corpus conflates them and that is why C3 arrives at 12/day and C2 at 1/day and both are "right."

- **ENFORCEMENT speech** (a rung firing) is *not* budgeted. It is gated by the ladder and the interlocks. It is a function of misses, and a man who misses has earned the noise he signed up for.
- **VOLUNTEERED speech** (Rip initiates: commentary, bits, callbacks, gloats) *is* the budget. It is what decays, and it is the only thing the ceremony cap can honestly govern.

`volunteered_budget(day) = min(jurisdictionCurve(jurisdiction), monthCeiling(month))`

| jurisdiction | 4 | 3 | 2 | 1 | 0 |
|---|---|---|---|---|---|
| volunteered/day | 6 | 5 | 4 | 2 | 0 (Tape only) |

`monthCeiling`: M1=6, M2=5, M3=4, M4=3, M5=2, M6+=**1**.

The `min()` is the graft-5 ceremony cap winning over the odometer, and it is the one place the calendar is allowed to touch the arc. One line of justification: jurisdiction is the *story*, seconds are the *resource*, and when they disagree the resource wins or the app dies at week 5. **A budget counts INITIATED CONVERSATIONS, not rungs** — one escalation is one utterance-event with five beats, or a single bad water day at month 8 eats the day and the app goes silent for twenty hours.

Plausible-success trajectory, both budgets summed (this is the demand the bank must serve):

| | M1 | M2 | M3 | M4 | M5 | M6 | M7 | M8 | M9 | M10 |
|---|---|---|---|---|---|---|---|---|---|---|
| jurisdiction | 4 | 4 | 3 | 3 | 2 | 2 | 2 | 1 | 1 | 1 |
| volunteered/day | 6 | 5 | 4 | 3 | 2 | 1 | 1 | 1 | 1 | 1 |
| enforcement/day | 2 | 2 | 1.5 | 1.5 | 1 | 1 | 1 | 0.5 | 0.5 | 0.5 |
| **utterances/mo** | 240 | 210 | 165 | 135 | 90 | 60 | 60 | 45 | 45 | 45 |

**U ≈ 1,095 utterances over 300 days.** That is the number everything below is sized against.

### 3.2 The supply side — the bank, and why C3's 250 is the week-3 death

C3: 250 fragments, 12 utterances/day, retire-at-3 → 750 plays ÷ 12 = **exhausted on day 62**. That is precisely the failure it exists to prevent, and it lands in month 3 — the month the user decides.

The fix is not "write more." It is to stop pricing every slot the same. Repetition tolerance is **per slot role**: nobody notices the fourth "Okay." Everybody notices the second swerve.

```
LINE = [OPENER] + [OBSERVATION(data_slot)] + [ESCALATION?] + [SWERVE?] + [BUTTON]
```
OPENER/OBSERVATION/BUTTON fire on every line; ESCALATION ~50%; SWERVE ~35%.

| Slot | Bank | retire_at | Capacity | Demand (U=1,095) | Headroom |
|---|---|---|---|---|---|
| OPENER | 60 | 20 | 1,200 | 1,095 | +10% |
| OBSERVATION frame | 80 | 15 | 1,200 | 1,095 | +10% |
| ESCALATION | 70 | 8 | 560 | ~548 | +2% |
| SWERVE (hand-written) | 130 | **3** | 390 | ~383 | +2% |
| BUTTON | 100 | 12 | 1,200 | 1,095 | +10% |
| **Sub-total** | **440** | | | | |

Plus GHOST set (40, hand-written), UNIQUES (40, fire once then burn), the sincere congratulation (1), comeback/STAND DOWN/safety copy (30). **~550 authored fragments. It closes, with the tightest margin on the swerve — which is correct, because the swerve is the only slot whose staleness is fatal.**

Two-tier retirement:
- **SKELETON** (`skeleton_id` = shape + slot set + register + situation): **~120 authored**, blocked 21 days between plays, hard-retired at **12 lifetime plays** → 1,440 skeleton-plays ≥ 1,095, +31%. Track skeletons, not strings: humans detect templates faster than repeats.
- **FRAGMENT**: `play_count >= retire_at(slot_role) → retired_at = now`, in SQL, in the selection query, non-negotiable.

Hand-write only the SWERVE and the GHOST set (~170). The remaining ~380 come from a **monthly scheduled job on ardi/the 3090** conditioned on last month's real data, opening a PR against `lines.json` — build-time authoring, not runtime inference; locked decision #1 intact. `lines.json` hot-loads from `getExternalFilesDir()/coach/lines.json`, overriding `app/src/main/assets/lines.json`. Push → Actions → sideload is a ten-minute turn and nobody plays a game with a ten-minute turn.

**Running dry is content, not failure.** `bank_health` is a real slot:
> "I've got maybe four hundred of these left, brother, and you want to spend one on a *glass of water*."

### 3.3 The callback engine — his own archive is the writers' room

Every line at rung 1+ **must** resolve ≥1 slot from real history, or the assembler throws. `SlotResolver` is the only path from Room to a rendered line.

Slots: `last_proof_object`, `named_cluster`, `days_since_X`, `pipeline_stage`, `days_to_graduation`, `best_ever`, `test_week_pb`, `time_of_day`, `same_week_last_month`, `hour_he_always_fails`, `manufacturer`, `canary_delta_ms`, `jurisdiction`, `lapse_recovery_hours`, plus wizard-declared domain vocabulary.

**Banned slots, deleted from the enum so no contributor can conjure them:** `weight_delta`, any body metric, any food field.

Line quality = f(specificity). A callback to a real Tuesday is funnier than any static line, and his life refills weekly, for free, forever. Numbers Rip quotes must be **true** — a specific wrong number is the lie that breaks a character built on being lovably vague.

### 3.4 THE LEDGER FORGETS — 28 days, hard purge, and it is a joke generator

`LedgerPurgeWorker` (WorkManager, daily 04:00): `DELETE FROM ledger_entry WHERE ts < now - 28d AND cluster_repeat_within_28d = 0`. **Delete, not soft-delete.** A column that doesn't exist he cannot conjure; a row that doesn't exist he cannot address.

**The asymmetry, decided here because the corpus never states it:**
- `ledger_entry` (misses, evasions, force-stops, caught fakes, clock jumps) — **purged at 28 days.**
- `proof_event` (the photos, the reps, the PBs, the archive) — **kept forever. That is the product.**
- Aggregate counters survive as **dateless integers** (`evasion_count_lifetime`). He may say "forty-one." He may not say "that Tuesday in March." A count is not a memory and cannot be ruminated on.

He forgets what you did wrong and keeps what you did. That is the difference between a coach and a case file, and it is the whole character in one schema decision.

**The purge writes its own material.** When a callback's source row is gone, `SlotResolver` returns `PURGED` and the grammar takes the `[PURGED]` branch — Rip reaches for the bit and it isn't there:

> *(PITCHMAN, trailing off)* "You did this before. You did this in — hold on. Hold on, I had it. …It's gone. That's gone. I had it on a *tape*, brother, and the tape is the thing that's wrong with me."

Renewable, structurally generated, zero authored content, and it is the mechanical seed for the "I don't know" GHOST scene gated on the odometer at jurisdiction 1. "I'm going to remember this" is now **false**, and the app knows it's false.

CI defends the window between purges: no query against a FAILURE_TABLE may pass `since < now - 28d`.

### 3.5 The OEM vendetta — the only grudge he is allowed to keep

C2 argues the vendetta dies once the gauntlet passes. **Decided against, with the reason:** the canary re-runs weekly, unannounced, and writes a *measured* `canary_delta_ms` — so the pass case is also material, because the number is new every week and Samsung re-applies Sleeping Apps after every OS update.

> "Forty-seven minutes. Delta: eight hundred and twelve milliseconds. Your phone *tried*, brother. It reached for me. It doesn't have the ARMS."

> "Your phone assassinated me at 14:07. Not you — **Xiaomi.** I had a whole thing ready about Tuesdays and a battery manager put a bag over my head. That one's void. That one's on *me*."

`canary_result` is **its own table**, `target = the_phone`, and it is **exempt from the 28-day purge**. Rip's memory of Arsen is 28 days. His memory of Xiaomi is permanent. That is the funniest row in the schema and the clinical guardrail in the same line: contempt at the machine, curiosity at the man — and it seats Arsen *beside* him against a common enemy, which is a therapeutic alliance by accident, and alliance is what survives ten months.

### 3.6 The line-repeat ledger — hashed, because a transcript is rumination

```kotlin
@Entity data class LineHash(@PrimaryKey val h: Long, val lastPlayedAt: Long)
// h = sha256(normalise(renderedText)).take(8).toLong()
```
Candidate rejected if `h` exists with `lastPlayedAt > now - 120d`. `LineHashPurgeWorker` (weekly) drops rows older than 120d. 1,095 rows × 16 B ≈ **18 KB**.

**Hashed on purpose:** an unhashed table of every line Rip ever said about your failures *is* the rumination infrastructure §3.4 deletes. A hash is unreadable, so the table structurally cannot become a transcript. The dedupe is enforced; the record is not retrievable.

### 3.7 Grammar invariants as failing CI tests

`:coach` is pure JVM. These run in seconds on `R:/jdk17` with no Android SDK.

```kotlin
enum class Target { the_habit, the_excuse, the_situation, the_phone, himself, the_tape }
// body | weight | appearance | worth are NOT enumerable values.
```

`:coach:test` — `LineGrammarInvariantsTest.kt`:
1. `assertEquals(EXPECTED_TARGETS, Target.entries.map { it.name }.toSet())` — the enum is frozen; adding `the_body` fails the build. Stronger than linting emissions: you cannot lint a value that cannot exist.
2. `assertNoFragment { it.register in setOf(DISAPPOINTED, GHOST) && it.text.containsToken("brother") }` — over the entire bank, plus a runtime assembler check. One leaky template kills the best tell in the app permanently.
3. `assertNever { it.register == DISAPPOINTED && it.trigger != CAUGHT_FAKE }`.
   **Conflict resolved:** graft 6 says "caught-or-confessed"; graft 1 says confession is warm and never priced. **CAUGHT_FAKE only.** If confession draws DISAPPOINTED, confession has a price again in the only currency that matters — his tone — and the gradient re-inverts. Confession is answered in PITCHMAN, warm, every time.
4. `assertNoSlotReferences(weight_entry, food, body)`; `assertNoColumn("is_healthy"|"calorie"|"macro"|"food_verdict")`.
5. `assertNever { it.joins(break_glass, ledger_entry) }` — distinct objects, no query may join them.
6. `lintVoicePack` — banned lexicon over the runtime-loaded `lines.json`, so a community PR cannot land a body joke.

### 3.8 The tight loop: `:coach-cli` and the Rip Simulator

- **`:coach-cli`** — pure-JVM Gradle module. `./gradlew :coach-cli:run --args="--situation=water_miss --register=DISAPPOINTED --n=1000 --out=build/lines.txt"`. A thousand generated lines in a text file on a laptop in two seconds, **read as prose**. The soak test is the gate; this is the loop that makes its output actionable rather than a monthly event.
- **RIP SIMULATOR** — `app/src/debug/java/.../RipSimulatorActivity.kt`, debug source set only so it cannot exist in a release APK. Fire any skeleton/register/rung/situation against `FakeHistoryProvider`; buttons for *set jurisdiction = N*, *purge the ledger now*, *fail the canary*, *fire the GHOST*.

### 3.9 The 300-day soak test — executable assertions

`:coach/src/test/kotlin/.../SoakTest.kt`. Runs in CI on every tag. **One day of work; the only instrument that can falsify the ten-month claim before month eight does.**

```kotlin
@Test fun `read month eight`() {
    val clock = SyntheticClock(days = 300)
    val t = simulate(clock, plausibleBehaviour(   // incl. a flu, a crunch,
        flu = 9, crunch = 14, collapse = 11,      // a demotion, a collapse,
        demotions = 3, caughtFakes = 2), real())  // and two caught fakes
    t.dumpTo("build/soak/month8.txt")             // AND THEN READ IT.

    assertUtteranceTotal(max = 1_150)
    assertBankNeverExhausted()                      // no slot hits 0 candidates, ever
    assertSwerveHeadroomAtDay(300, min = 0.0)
    assertRegisterMix(month = 1, arena = 0.28 ± 0.05, disappointed = 0.07 ± 0.03)
    assertRegisterMix(month = 8, arena = 0.08 ± 0.03, disappointed = 0.40 ± 0.05)
    assertArenaPureFunctionOfJurisdiction()         // the arc, falsified in CI not month 8
    assertNoSkeletonRepeatWithin(days = 21)
    assertNoRenderedLineRepeatWithin(days = 120)
    assertNoFragmentExceeds(retireAt(slotRole))
    assertVolunteeredPerDay(month = 6, max = 1)
    assertCeremonySeconds(month = 8, max = 90)
    assertNever { it.target in BANNED_TARGETS }
    assertNever { it.register == DISAPPOINTED && it.trigger != CAUGHT_FAKE }
    assertNever { it.contains("brother") && it.register in setOf(DISAPPOINTED, GHOST) }
    assertNever { it.slot.sourceRow.age > 28.days && it.slot.table in FAILURE_TABLES }
    assertPurgedBranchFiresAtLeast(times = 3)       // the tape degrades audibly
    assertCanaryRowsSurvive(days = 300)             // the grudge is permanent
    assertSincereCongratulationCount(exactly = 1)
    assertGhostSceneFiresOn(jurisdiction = 1)       // "I don't know"
    assertStandDownFiresOn(depressiveSignature())
    assertLadderNeverEscalatesOn(multiHabitCollapse())
    assertNoRungAbove(R0, between = 22.00, and = 08.00)
    assertNoLockBefore(hours = 72)
    assertPenaltyDebtNeverExceeds(reps = 20, perDay = 1)
    assertConfessionNeverDemotes()
    assertNever { it.joins(BREAK_GLASS, LEDGER) }
}
```

**You cannot ship a ten-month claim you have never read.** The dump is not decoration — the assertions prove the numbers close; only the prose proves he's still funny.

---

## 4. SCREENS, IA & THE ONBOARDING WIZARD

### 4.1 The inventory — every surface in v1

Single `MainActivity` (Compose Navigation) + one separate `LockActivity` (`singleInstance`, `setShowWhenLocked`, immersive, its own process-independent BREAK GLASS). Nothing else. No five-tab bar.

| Route / Activity | File | Job | Hierarchy |
|---|---|---|---|
| `intake/*` | `ui/intake/IntakeFlow.kt` | The casting call. Once, then quarterly re-consent. | Modal, blocks all |
| `contract` | `ui/intake/ContractScreen.kt` | The Ulysses clauses he initials. The one sincere break. | Terminal step of intake |
| `arena` | `ui/arena/ArenaScreen.kt` | **Home at high jurisdiction.** Rip + exactly ONE demand. | Surface A |
| `archive` | `ui/archive/ArchiveScreen.kt` | The proof timeline. **Home at low jurisdiction.** | Surface B |
| `shot` | `ui/shot/ShotScreen.kt` | CameraX viewfinder. One tap. | Pushed over A or B |
| `stamp` | `ui/shot/StampScreen.kt` | The verdict. 200 ms or 1.2 s — rationed. | Pushed from `shot` |
| `audit` | `ui/shot/AuditScreen.kt` | `shot` + nonce band + **one-tap re-enrol**. | Pushed from `shot` |
| `comeback` | `ui/arena/ComebackScreen.kt` | Re-entry after ≥4 dark days. | **Intercepts `arena`** |
| `tape` | `ui/tape/TapeScreen.kt` | Sunday 20:00. The show. The Ledger. | Pushed from A/B |
| `arsenal` | `ui/arsenal/ArsenalScreen.kt` | 9 patterns × ~6 rungs. Rep Mode. | Pushed from A |
| `records` | `ui/records/RecordsScreen.kt` | PBs, Test Week, trend. Mono. **Zero character.** | Pushed from B |
| `wiring` | `ui/wiring/WiringScreen.kt` | Permission + OEM health + **export proof-of-last-run**. | Pushed from settings |
| `standdown` | `ui/safety/StandDownScreen.kt` | Flat, UI font, no character. Reachable **from inside the lock**. | Global, 2 taps |
| `help` | `ui/safety/HelpScreen.kt` | Static. Human-verified BG crisis numbers from `crisis_numbers.json`, liveness-checked in CI. | Global |
| `goodbye` | `ui/exit/GoodbyeScreen.kt` | RETIRE RIP. Export, then goodbye, no begging. | Settings, day one |
| — | `LockActivity.kt` | The 400 ms boomerang. Theatre, and he says so. | Its own task |

### 4.2 The odometer drives the IA

`jurisdiction = count(ENFORCED) + count(AUDITED)`, a `Flow<Int>` off `HabitDao.observeStages()`, range 0–4 (ENFORCED capped at 2 by the enforcement budget). It is the **only** input to the split. No calendar, no `daysSinceInstall`, no month index — anywhere in `ui/`. A lint rule (`NoCalendarInUi`) fails the build on `LocalDate.now()` inside `ui/arena` or `ui/archive`.

```kotlin
// :coach (pure JVM, no Android imports — unit-testable, and CI can prove it)
fun ripFraction(j: Int): Float = when (j) { 4 -> .60f; 3 -> .45f; 2 -> .30f; 1 -> .12f; else -> .04f }
fun homeSurface(j: Int): Surface = if (j >= 3) Surface.ARENA else Surface.ARCHIVE
fun speechBudget(j: Int): Int = when (j) { 4 -> 8; 3 -> 6; 2 -> 4; 1 -> 2; else -> 1 } // utterances/day
```

Soak-test assertion: `assertArenaSplitIsPureFunctionOf(jurisdiction)` — the arc is falsifiable in CI, not at month 8. `ripFraction(1) == .12f` is the 40 px face that can still lock your phone. **The Archive is never gated behind day 200; it ships populated from proof #1.** You ship the pull mechanic before the horizon you need it at.

Speech budget is capped at 8/day (not C3's 12): the fragment bank is sized against `8 × 300 days ÷ retire-at-3 ≈ 800 plays ÷ 3 = ~270 unique needed`, so the bank ships at **~420 fragments**, and the arithmetic is printed in the README. 250 at 12/day exhausts in 62 days; that is the week-3 death the spec claims to prevent.

### 4.3 The 7am ARENA — the one glanceable thing

At 07:00 he is holding coffee in one hand. The screen answers exactly one question and it is not "how am I doing."

**THE ONE THING: a single demand card. Never two.** Multiple simultaneous demands is how a coach becomes a to-do list, and to-do lists die. `ArenaViewModel` emits `Demand?` — the single highest-priority open obligation from `DemandResolver` (:coach). If two are open, the second is invisible until the first resolves. If none, the card is replaced by the Floor.

Layout, top to bottom:
1. **RIP** — `ripFraction(j)` of vertical space. 12 fps loop against a 60 fps UI. That frame-rate contrast *is* the aesthetic thesis, rendered.
2. **THE DEMAND CARD** — one line of gold display type, one shutter button. `WATER. SOMETIME TODAY.` No countdown (audit windows are hours, not minutes — a tight window manufactures unearned failures on a habit he actually performed).
3. **THE LEDGER STRIP** — 3 mono glyphs. Split-flap. **The only slow thing in the app.**
4. **`FOR THE RECORD`** — persistent, bottom, always. Never hidden, never counted down, never priced.
5. **BREAK GLASS** — bottom-left, every screen, first tap, no confirm.

Below the fold (a deliberate scroll, not a tab): the trust ladder — which habits are ENFORCED / AUDITED / TRUSTED / RETIRED. This is the odometer made legible, and it is the only scoreboard that matters.

### 4.4 The signature moment — SHOT → STAMP

`shot` opens in **<400 ms** (CameraX pre-warmed in `AlarmReceiver` before the demand is even shown). No chrome. One band:

```
YOUR GLASS.                              [ FOR THE RECORD ]
```

On an audit, the band carries the nonce and the gesture icon **fills gold in real time** as `HandLandmarker` matches. The inference is spent *before* the shutter, so the verdict is already decided when he taps. That is what makes it feel instant.

**PASS — default (proof ≥ 50, or any AUDITED habit): 200 ms.** One sharp haptic, one split-flap tick on the Ledger strip, back to ARENA. No confidence number ever appears in the UI. Nothing is ever rejected — a photo of a wall opens the lock (LAW 1: zero assertion).

**PASS — full ceremony (first ~50 proofs, all audits, Test Week, graduations):** the three-stamp Tribunal, 1.2 s, slam-in, no fade.

> **[PITCHMAN — audit passed]**
> "…Huh. That's your glass and that's three fingers. Real glass, real kitchen, 9:14 in the morning, and I have absolutely nothing prepared for this. I had a bit ready. About sand. It was going to be devastating. Now I have to just stand here."

Ceremony is rationed by rarity or it becomes latency. The Tribunal is gorgeous for 50 views and a load screen for the next four thousand.

**CAUGHT.** There is exactly one live accusation in the product and it fires on `SHA-256` collision of the decoded pixel buffer — never pHash (pHash is *engineered* to be robust, so honest re-shots of a static counter collide, and the one insinuation in the app would fire on truthful nights). A real sensor cannot emit two byte-identical frames.

The verdict still **passes**. Rip accepts the proof, says one word, and the stamp lands:

> "Interesting."

Nothing else. Nothing follows in the moment (LAW 2: never accuse in real time). The demotion, the DRY register, and the Ledger row are Sunday's business. `DISAPPOINTED`/`DRY` is emittable **only** on `trigger ∈ {CAUGHT_FAKE, CONFESSED_FAKE}` — machine-enforced, failing unit test `assertNever { register == DRY && trigger !in CAUGHT_OR_CONFESSED }`. He's blind. He cannot know *why* you missed. Disappointment at a miss is inventing a reason to be cruel.

**`FOR THE RECORD` is free, unlimited, always visible, warm, and NEVER demotes.** No 3/week cap. Only being CAUGHT demotes. At ~15% sampled audits with zero assertion, P(caught) is small — so a certain demotion for confessing against a probabilistic one for faking makes faking the dominant strategy. The button must be cheaper than lying, always, at every hour, forever. `ConfessionEntity` and `LedgerEntry` are separate tables; a confessed cigarette and a photographed-twice glass never share a row.

### 4.5 THE AUDIT SCREEN + one-tap re-enrolment

Below the shutter, permanently, two 2px-stroke chips:

`[ IT'S REAL ]` — instant, silent, honoured, no confirmation, no mockery. Rate written to `appeal_rate` and **tracked privately as a model-quality signal**. If he's appealing 40% of audits, the model is broken, and that is a signal you want.

`[ THAT'S MY NEW MUG ]` — one tap, 20 burst frames, appends 1280-d penultimate embeddings to `ObjectEmbedding` for `object_id`, re-indexes the k-NN, done in 6 seconds, no wizard, no settings. Objects churn: he buys a new mug in month 4. An app that starts flagging valid proof on truthful nights fires **the #1 rage-uninstall**. Calibrating once in the wizard and never again is a month-4 bug shipped on day one.

### 4.6 THE COMEBACK SCREEN — the most important surface in the app

Trigger: `daysSinceLastProof >= 4` OR exiting `STAND_DOWN(SICK|INJURED)`. `ComebackScreen` **intercepts** the `arena` route as a NavHost redirect — he cannot reach the normal home until he taps through it, and it takes one tap.

Day 10 of the flu, he opens the app. He sees: **no debt. No queued grievances. No catch-up. No reckoning. No re-setup. No review of failures. No streak, broken, in red.** The Ledger didn't count it — `LedgerPurgeWorker` already dropped the window. One card, one button, one tiny action.

> "You were sick.
> Fine. FINE! The Ledger doesn't count sick, brother, I'm not a MONSTER, I'm a PROFESSIONAL.
> Floor's one set. That's today. That's the whole ask.
> …I did some thinking while you were out. Don't worry about it."

`[ ONE SET ]` — that is the entire screen. Month 8 is reached **through** three bad weeks, not around them. An adversarial app without a forgiveness mechanic converts its own aggression into churn at the first shock, and the first shock is a certainty, not a risk.

### 4.7 THE LOCK

Exercise only, rung 4, never before 08:00 or after 22:00, never during a call, never `IN_VEHICLE`, never twice in 90 minutes, **never in the first 72 hours after install — and never at all for the first 14 days**, which he is told:

> "I'm being nice. Two weeks. Enjoy it. I'm building a file."

The 14 days build the baseline the targets are derived from anyway, and anticipation outperforms aggression. When it does arm, Rip narrates his own impotence — impotence is canon, like the blindness:

> "I own the phone now.
> That's a lie. You can press home. You *will* press home.
> I'll be back in four hundred milliseconds because I'm forty megabytes and a dream and I don't get tired. I get patient.
> One set. That's the key. Camera's on."

Unconditional 90-second self-expiry regardless of proof. BREAK GLASS and STAND DOWN both live on it. `BreakGlassEvent` is its own table; **no query may join it to `LedgerEntry`** — enforced by a Room DAO lint. Pressing HOME is counted and roasted. Break glass is never counted, never rendered, never referenced.

### 4.8 THE INTAKE — question by question

Target: value in one tap, <4 minutes, keyboard appears exactly once.

**0. Consent before shutter.** 15 words of plain language, then black, then one spoken line. Photographing his kitchen before explaining what the app does with photos is charming and backwards; the bit survives the fix.

> This app takes photos. They stay on your phone, encrypted. You can export or delete them anytime.
> "Now — show me your kitchen counter. It's the only way I get to see anything, and I want to know what I'm working with."

Camera is the **first interactive surface**. ML Kit labels return, Rip reacts. Fallback is written and funny because the bit is always about *him*: *"I see NOTHING. An empty counter. A void. Are you a monk, or are you just out of food?"* He experiences demand → camera → reaction inside ten seconds, before any commitment. The demo is the onboarding.

**1. PAR-Q+ (7 items, verbatim).** Any yes → exercise prescription gated or modified + GP referral. Standard of care. Free. No excuse.

**2. SCOFF (5) + MDDI-lite (drive-for-muscularity).** SCOFF was validated on young women and under-detects in men — it misses muscularity-oriented disordered eating, which is *the only ED presentation this app could plausibly cause*. **Positive on either → all body metrics, penalty-as-exercise, and the mocking register are permanently unavailable. Not warned about. Unavailable. No in-app override.** Re-screened quarterly by `ScreeningWorker`. This gate is why food is absent from the schema and not merely off: a flag he wrote at 1am he can unwrite at 1am; a column that doesn't exist he cannot conjure.

**3. PHQ-2.** Baseline, re-run monthly, the human-report leg of the Drop Detector.

**4. HSI (cigs/day, time-to-first-cigarette).** <30 min = high dependence → routes to real treatment. **Zero penalties on smoking, ever**, and this screen says so.

**5. "WHAT ARE YOU HERE TO BEAT?"** — tiles, multi-select. Not *"what are you ashamed of."* Shame recruits concealment, in an app whose thesis is that hiding must be defeated.

**6. Hobby tiles.** Rip heckles per tap: *"Guitar. Sure. I'd love to be wrong."* Hobbies are **DEFENDED** — never ENFORCED, never a proof gate.

**7. The sliders.** No numbers, labels only. Coffee 0→10 reads `Human / Engineer / Bulgarian / Concerning / Legally Distinct From A Defibrillator`. Baseline data; he thinks he's playing.

**8. Quit date?** If set, `CoffeeTargetPolicy` cuts the coffee target ~50% for 2–4 weeks and **says why, in voice**:

> "Cigarettes speed your liver up. Smoking induces an enzyme, CYP1A2, and it clears caffeine about twice as fast. You quit Monday, that enzyme falls off a cliff, and your four coffees start hitting like eight. Jitters. Heart going. Awake at 2am. And you'll blame the quit, because that's the thing you changed. So we're cutting coffee to two for a month. I'm not being clever. I'm being early."

**9. What's in your house** — floor / chair / dumbbells / bar / bands / nothing. Plus the €40 honesty line about the doorway bar, because bodyweight-only pulling is the hole in the whole thing.

**10. Calibration** — 20 shots each: glass, mug, book, dumbbells. 50-shot k-NN over penultimate 1280-d features. ~200 KB. Not a retrain. Turns "is a bottle in frame" into "is *your* bottle in frame."

**11. THE CONTRACT.** Paper-white on black, serif body, clauses he initials with a finger. Keyboard appears here, once. Every Android permission dialog is preceded by one line in-voice: *"Next box says 'draw over other apps.' That's the box where I get to ruin your Tuesday. Tick it."*

Then **the character breaks. Exactly once. UI font. Flat. Four seconds.**

> Real talk. Emergency exit, bottom left of everything I ever do. First tap. Instant. No confirmation. Unlimited. I will never mention it — not in the moment, not on Sunday, not ever. It isn't counted and it isn't scored.
> Second thing: I'm a comedy app. I am not treatment.
> Third thing: RETIRE RIP is in the menu. It's there now. It works now.

Then the grain snaps back and he's screaming again. **An app that is 100% bit is a joke app. An app that breaks character exactly once, for safety, is a character.** That break buys the licence to be aggressive for ten months.

**The Ulysses clause, and why the coercion is autonomy-preserving:** changes that make things *easier* apply after 24 h and headline the next Tape. 11pm-Arsen cannot hurt 9am-Arsen. The delay **never** applies to pausing, muting, STAND DOWN, export, or uninstall — instant, no dialog, no roast, no plea. **Precommitment is only ethical while the exit is free.** And the contract, not Rip, graduates habits on measured evidence; Rip has no vote and can never take jurisdiction back. That is why the coercion lands as *his own prior self*, not as a boss.

**Not in the wizard:** weight goals, ever. Targets, ever. Targets are derived from 14 observed days. Wizard numbers are **joke fuel, not configuration** — lying in the wizard is the softest attack surface in the app, so remove the payoff.

No "You're all set!" screen. The Intake ends and the first real demand fires within 30 seconds.

### 4.9 Visual identity

Base `#0B0C0E`. Paper `#F2F1ED`. App accent Voltage Cyan `#00E5FF`. Character owns Hogan Gold `#FFB300` and Verdict Red `#FF2D2D` (failure only, scarce). **No green. Anywhere. Ever** — wellness green files this in the same mental folder as every app he already deleted. Success is white + gold. Dark only. 4% film grain (what makes a black app read expensive instead of empty). Display: Archivo Black class. UI: Inter. **Every number is JetBrains Mono.** Custom **2px stroke** icon set; **no emoji, ever** (`NoEmojiInUi` lint over string resources). UI motion: `spring(stiffness=400, damping=30)` — **nothing fades; everything slams or slides.** Character motion 12 fps against the 60 fps UI. Every sound is foley; zero synthesized chimes. `MUTE THE MAN` — one tap, always reachable, mutes voice, not penalties. No streaks as a primary metric. No before/after. No red/green on weight — EWMA trend only, never a headline number, and the always-live `RateOfChangeTripwire` (>1% BW/wk × 3 wk, or BMI trending <18.5) drops the character and disables modules regardless of which modules are on.

### 4.10 Day 200

`jurisdiction == 1`. **ARCHIVE is the start destination** (`homeSurface(1)`), and it opens on a scrubbable timeline of ~1,400 proofs — his kitchen at 6am, his book, his floor. `ARCHIVE` carries a mono footer: `LAST EXPORT: 4 DAYS AGO · Documents/SecondSpine` — app-private SQLCipher, weekly SAF `ExportWorker`, and if it hasn't run in 14 days the app **fails loudly** and says so on `arena`, `archive`, and `wiring`. The app never holds the archive hostage.

Rip is a 40 px face in the corner who can still lock the phone, speaks ≤2 times a day, and mostly writes captions. The Ledger shows 28 days and nothing older — **he structurally cannot hold January against him; the tape degrades.** One demand card, one shutter, ~40 s of ceremony. RETIRE RIP has been in the menu since January.

---

## 5. THE PROOF SYSTEM & ANTI-FAKING

### 5.1 The two laws

**LAW 1 — ZERO ASSERTION.** No proof is ever rejected. Every classifier output is a `REAL` written next to the photo in Room. **A photo of a wall opens the lock.** Every detector compiles to: compute a number, write it, shut up until Sunday.

**LAW 2 — NEVER ACCUSE IN THE MOMENT.** Bank it, audit a sample, roast it Sunday.

This is not softness, it is the load-bearing decision that makes coarse on-device vision survivable at all. A model that never claims can never be wrong, so a 70% classifier costs nothing and a false negative on a truthful night — **the #1 rage-uninstall vector in every verification product ever shipped** — is structurally unreachable. It is also the only thing that makes long-fuse callbacks possible: you cannot call back to month 6 if you burned the joke in month 6.

### 5.2 The runtime — decided

Three runtimes were on the table. The decision:

- **MediaPipe Tasks Vision is primary.** `ImageClassifier`, `HandLandmarker`, `PoseLandmarker` amortise one arm64 native (~15–20 MB, not the 12 MB the source docs claim) across three tasks.
- **ML Kit is used for exactly one thing: Latin text.** `com.google.mlkit:text-recognition` — the **bundled** artifact, never `play-services-mlkit-text-recognition`, which downloads its model at runtime and breaks the offline constraint on first run.
- **ML Kit Image Labeling is not primary, ever.** Its ~400-label map says "Drink" for a beer and has no `dumbbell`. Ensemble second opinion only.
- **Raw TFLite/LiteRT interpreter for YAMNet only.**

| Asset | File | Size |
|---|---|---|
| MediaPipe tasks-vision arm64 native | (aar) | ~15–20 MB |
| EfficientNet-Lite0 int8, ImageNet-1k | `assets/models/efficientnet_lite0_int8.tflite` | 5.4 MB |
| BlazePose Lite | `assets/models/pose_landmarker_lite.task` | 6 MB |
| HandLandmarker | `assets/models/hand_landmarker.task` | 7.5 MB |
| ML Kit text-recognition (bundled Latin) | (aar) | ~8–10 MB |
| tesseract4android + `bul.traineddata` | `assets/tessdata/bul.traineddata` | ~15 MB |
| YAMNet int8 | `assets/models/yamnet_int8.tflite` | ~4 MB |

Realistic APK: **75–85 MB.** There is no 70 MB cap. Delete it rather than believe it — sideload via GitHub Releases has no size limit. **Seconds are the scarce resource, not megabytes** (§ ceremony budget).

**Build-time label allowlist (graft 3, mechanised):** ImageNet-1k contains ~50 food synsets. A Gradle task strips them from the loaded label map, so `bagel`, `pretzel`, `ice_cream` are **not emittable values**. Food isn't off by default; the classifier physically cannot say it.

### 5.3 The truth table — every proof type, with a verdict

| Proof | Engine | Verdict — honest |
|---|---|---|
| **Reading** | ML Kit Text v2 (Latin) **+ tesseract4android/`bul.traineddata`** (Cyrillic) + duration binding | **Strongest proof in the app** — but the OCR is not what carries it. Start photo (page N, wall + `elapsedRealtime`), end photo (page M), band **0.3–2.5 pages/min**. Novelty = **shingled Jaccard > 0.8 scoped to `book_id`**, never an exact hash (OCR is nondeterministic across captures; hashing fails on the first honest retry). No `UNIQUE` constraint — that's a false-positive generator wearing a security badge. Page-number extraction is a disambiguation problem (chapter numbers, footnote markers, years) — heuristic: smallest bbox nearest a margin, position-consistent across the session. It fails often. **It costs nothing when it does.** |
| **Reps** | MediaPipe `PoseLandmarker` LITE, `LIVE_STREAM` | **Real for exactly 8 movements**, with an enforced camera angle and a framing assist. See §5.6. |
| **Water / coffee** | EfficientNet-Lite0 int8 + per-object k-NN | **Presence only.** It cannot see liquid level — transparent liquid in transparent glass is hard segmentation, not a small-model problem. The "two-photo level delta" is a **change score inside a bounding box**, not a level; without homography alignment (OpenCV, +20 MB/ABI) it is noise. Under Law 1 noise is fine. Stop calling it level detection before someone spends three weeks making the name true. |
| **Gym presence** | `ACTIVITY_RECOGNITION` step delta + time-away + one nonce photo | Strong. **No background location, no geofence** — `ACCESS_BACKGROUND_LOCATION` is a Settings trip and the nag pattern that gets apps deleted. Never a photo of the room: strangers didn't consent and most gyms ban it. |
| **Sleep antecedents** | `UsageStatsManager.queryEvents` + charging, 15-min `SleepSampleWorker` | Real, free, no foreground service. **Wake time and wind-down only** — duration and quality are never scored, never penalised, never commented on. |
| **Guitar** | YAMNet int8, AudioSet `Acoustic guitar`/`Strum` | Measures **duration**, which beats any photo. Cannot distinguish live from recorded. `RECORD_AUDIO` is while-in-use → **confession-tier, archive only. Never enforced, never penalised, absence never remarked on.** |
| **Smoking** | — | **Unprovable. Absence cannot be photographed.** Cue log only. Zero penalties, forever. |
| **Weight** | manual entry | **No OCR.** ML Kit reads 7-segment LCD at 40–60% and silently drops the decimal: 81.5 → 815. He types it. EWMA trend only. Rip cannot read this table. |
| **Food** | — | **No column exists.** |

### 5.4 The k-NN — the highest-ROI ML decision in the app

Wizard's last step: *"Show me your glass. Your mug. Your book."* — 20–50 shots each. Store the **penultimate 1280-d EfficientNet embeddings** (~200 KB, `enrolment` table). k-NN at query time, microseconds, no server, no retrain, no dataset. That converts *"is a bottle in frame"* into *"is **your** bottle in frame."*

**One-tap re-enrolment lives ON the audit screen** (`ThatsMyNewOne`). Objects churn — he buys a mug in month 4. An app that starts doubting valid proof on truthful nights is the top uninstall event, and the wizard-only enrolment every source doc shipped is a month-4 bug with a fuse on it.

### 5.5 The Cyrillic problem — shipped in v1

ML Kit Text Recognition v2 ships Latin, Chinese, Devanagari, Japanese, Korean. **There is no Cyrillic model. Arsen is Bulgarian.** Two source specs deferred `tesseract4android` to v1.1/v2 on the reasoning that Law 1 makes OCR failure free. That reasoning is correct and the conclusion is still wrong: reading proof is **build-order item #1**, the thing that validates the whole thesis in two weeks — and a v1 that cannot read the books he actually reads validates it on nothing. ~15 MB in an APK with no size cap. **Ship it.** Script is auto-detected per photo; the Latin path stays because he reads English technical books too.

### 5.6 Pose — the honest verdict

`PoseLandmarker` LITE is genuine: 33 landmarks, 30 fps on any modern phone. "You cannot fake a push-up except by doing a push-up" is *nearly* true — **and stop calling it unfakeable.** He can point the camera at a YouTube video and it will count them, happily. Per the substitution thesis that is fine. Per honesty, Rip says so, once, ever:

> **QUIET — first run of REP MODE**
> "I can't see you. I'm a rock with a camera and a personality disorder. If you point me at a video of a man doing push-ups, I'll count them and I'll be delighted. There is no version of this where I win, brother. So the question was never whether you can beat me. The question is who you're beating."

Two hard constraints or the pillar dies: **(a)** 2D joint angles are viewpoint-dependent and MediaPipe's `z` is relative and unreliable — never build on it; front-on push-up elbow angle is foreshortened garbage. The app **enforces the camera angle** with a framing assist. **(b)** Build the **video-replay CI harness before any pose code**: `detectForVideo(mpImage, timestampMs)` over ten committed MP4s on `reactivecircus/android-emulator-runner`. Threshold tuning becomes a unit test. Two days, highest-leverage infrastructure in the project.

### 5.7 The sampled audit

**Rip is an auditor with a hunch, not a supervisor with a checklist.** ~85% of proofs are one-tap, shutter-and-go, ~2s. **~15% are audited** with a nonce, ~10s, capped **≤2/day**. Audit probability is weighted toward the habits and hours the bandit says he's most likely fudging. He cannot know which glass gets the horns, so deterrence survives and cost drops 6×. Windows are **hours, not minutes** — a countdown manufactures unearned failures on a habit he actually performed, which is the exact collapse trigger.

**The nonce:** `{objectClass, fingerCount ∈ 1..5}`, minted at request time from `SecureRandom`, two frames 2.5s apart with a **changed** finger count. `HandLandmarker`, 40 ms, >95% on finger count. **Handedness is CUT** — MediaPipe reports it from the image frame and it flips under front-camera mirroring. Entropy lives in *timing*, not nonce bits. Prototype the ergonomics (bottle + gesture + phone = three objects, two hands) on his actual phone in week one, before anything is built on top.

**Decoys:** `setAlarmClock` publishes the fire time to the lock screen via `getNextAlarmClock()`, which defeats the unpredictability carrying the entire deterrent. **Arm 4–6 decoys per window; cancel the losers at fire time.**

### 5.8 Defences, ranked

**REAL:**
- **CameraX in-process only.** No `ACTION_IMAGE_CAPTURE`, no PhotoPicker, no `GET_CONTENT`, **`READ_MEDIA_IMAGES` is not declared in the manifest.** Classify the live `ImageProxy` in memory, then persist. There is no file-swap window and no gallery, ever. This is structural, not detection — the strongest thing in the section.
- **Nonce minted at request time.** Unforecastable by construction.
- **Capture-pipeline timestamp.** `ImageInfo.getTimestamp()` is sensor-domain monotonic, not settable from userspace. Binds the frame to this `bootId` and this nonce. Free, zero-FP.
- **≥90s → VOID.** A nonce answered 40 minutes late has no unpredictability value. Past 90s the audit **voids to an ordinary self-report** — voided, never failed, never counted, never mentioned. Law 1 holds.
- **SHA-256 of the decoded pixel buffer.** A real sensor cannot emit two byte-identical frames. This is the only zero-false-positive signal in the app and the only one permitted a live reaction (§5.10).
- **Clock integrity.** `wallClock` vs `elapsedRealtime()` vs a `bootId` minted at `BOOT_COMPLETED`. Never blocks. *"Tuesday had thirty-one hours in it, brother."*

**THEATRE:**
- **pHash (64-bit DCT), two hashes: `hash_object` (ROI crop) and `hash_frame`.** As anti-fake it is theatre — real handheld re-shots of the same counter land at Hamming 10–22, and a new photo of the same glass defeats it in four seconds. It is **real as an archive clustering key** for the Tape's montage, and `hash_object < 6` with `hash_frame > 12` = "same glass, different room" = normal and good, never flagged. **It never speaks.**
- **Additional liveness challenges** (blink, turn, face-in-frame). The finger-count nonce already is the liveness challenge. More ceremony, no more truth.

**NOT WORTH IT — cut permanently:**
- **Screen-photo / moiré detection.** No stock model exists; presentation-attack detection is a research field with a dataset he will not build. 60–70% against a modern OLED, false-positives on blinds, shirts, wicker. **And the joke goes with the detector** — the corpus wanted to keep *"I'm just saying Tuesday's glass had a refresh rate"* as pure comedy. It doesn't ship. An unfalsifiable insinuation from a system that admits it might be wrong, delivered as a joke so it cannot be contested, is gaslighting-shaped — and worse, one demonstrated bluff contaminates SHA-256, the one signal that never lies, by association. **Narration accuracy bar: any signal below ~90% verified accuracy may live in a log he can open and may NEVER be given to the character's mouth.**
- **EXIF checks** — moot; camera-only means provenance is already known.
- **Accelerometer jitter, ambient-light consistency** — 60–70%, beaten by a lamp.
- **Root / Magisk / VCAM / Play Integrity / tamper checks / uninstall blocking.** He compiles this APK. Every mechanic must survive `git log`. An unbeatable coach is a hostile artifact, and opponents get beaten and then deleted.

### 5.9 The incentive economy — the arithmetic

**FOR THE RECORD** is permanent, always visible on every proof screen and on the lock, one tap, warm, **unlimited, and it never demotes anything.** The label *is* the attribution — not "I fucked up", because internal-stable-global attribution after a lapse is the best-evidenced predictor of a lapse becoming a relapse.

Let *D* = the value of the graduation lost on demotion. p(audit) = 0.15; p(detect | audit) ≈ 0.5, generously.

| Strategy | Cost | 
|---|---|
| Do the habit | the habit |
| **Confess** | **0** |
| Fake | 0.15 × 0.5 × *D* = **0.075 *D*** |

Confession **strictly dominates faking** for every p(audit) > 0 and every *D* > 0. There is no state, at any hour, in any month, where faking beats the button.

The corpus shipped the inverse. C3 as written: confession costs a *certain* D, plus a 3/week cap. That prices honesty at **13× the expected cost of lying**, and at the cap it prices it at infinity — a state exists where he wants to confess and can't, and the only fallback from that state is faking. The app's founding insight, inverted by its own incentive table. **Delete the cap. Delete the demotion-on-confession. Only being CAUGHT demotes.**

The second-order consequence is what makes this ship-blocking: once confession is free, **the audit rate stops carrying the incentive.** 15% doesn't have to deter anything — it only has to be nonzero. It is a comedy-material budget, not a security parameter. That is why 15% is enough and 100% was always a tax.

`confession`, `lapse_note`, `ledger_entry`, and `break_glass` are **four distinct tables. No query may join `break_glass` to any evasion row.** Pressing HOME is counted and roasted; break glass is never counted, never rendered, never referenced by any subsystem. Confessing a cigarette and photographing the same glass twice are different categories; conflating them teaches that honesty and deception are the same failure, which kills the only channel smoking has.

> **BIT — confession**
> "You pressed the button. You looked a blind man in his one good eye and told him you lied to him. Brother, I respect that so much I'm not even going to yell.
> …I'm writing it down in **BOLD**, though."

And the ledger forgets it in 28 days anyway. He can't hold it against you. The tape degrades.

### 5.10 When the model is wrong

Usually: **nothing happens.** That is the whole point of Law 1 — a wrong model in the moment has no surface to be wrong on.

The one carve-out is **SHA-256 collision**, which a real sensor cannot produce. Rip accepts the proof and says one word:

> "Interesting."

Nothing else. It asserts nothing, so it carries zero false-positive risk, and it fires perhaps twice in ten months. It is deliberately **not** on pHash — pHash is *engineered* to be robust, so identical pHashes happen on honest nights, and the one insinuation in the app would fire on truthful days.

Where the model *can* be wrong loudly is Sunday. Two instruments:

1. **`IT'S REAL`** — one tap on the audit screen and on any Ledger row. Instant, silent, no confirmation, no argument, no mockery, honoured. The row is voided.
2. **One-tap re-enrolment** beside it, which fixes the cause rather than the symptom.

`appeal_rate` is tracked **privately, never shown, never narrated** — it is a model-quality signal, not a user stat. Over any 4-week window: **>15% → the k-NN threshold auto-relaxes; >25% → that signal is removed from the Tape entirely and the app says so, in Rip's voice, at his own expense.** If he's appealing a quarter of his audits, the model is broken and the correct response is for the app to lose the argument.

---

## 6. THE ESCALATION STATE MACHINE & SAFETY INTERLOCKS

### 6.1 The core is pure. This is not negotiable.

```kotlin
// :coach — pure JVM, ZERO Android imports. The only thing GitHub Actions can prove correct.
// This is a codebase where "it compiled" and "it works" are unrelated statements.
fun step(
    state: EscalationState,
    event: Event,   // AlarmFired|ProofLogged|Confessed|BreakGlass|Evaded|Reboot|
                    // InterlockChanged|DropDetected|CanaryResult|Expired
    now: Instant,
    interlocks: Set<Interlock>,
    pipeline: PipelineState      // stage per habit; jurisdiction is derived, never passed in
): Pair<EscalationState, List<Effect>>
```

`step` is total, deterministic, and side-effect-free. `Effect` is a data class (`ShowNotification`, `Vibrate`, `PlayAlarm`, `PlayClip`, `ArmLock`, `Cancel`, `Void`, `Log`). The `:app` module is the only thing that touches `AlarmManager`. 200+ unit tests in CI; the soak test drives `step` for 300 simulated days on a laptop with no SDK.

### 6.2 States and transitions

```
                  ┌──────────────────────────────────────────┐
   DailyPlanner→  ARMED ──AlarmFired(R0)──→ R0 ──+7m──→ R1 ──+11m──→ R2
                    │                                          │
                    │                              +9m ────────┘
                    │                               ↓
                    │                              R3 ──+13m──→ R4 (THE LOCK)
                    ↓
   any of: ProofLogged | Confessed → SATISFIED   (terminal; zero-assertion, nothing is rejected)
           BreakGlass             → BROKEN_GLASS (terminal; free, silent, uncounted)
           InterlockChanged(on)   → SUSPENDED    (same rung frozen; never penalised)
           InterlockChanged(off)  → resume at frozen rung after 90 s settle
           window close | 90 s lock expiry → EXPIRED
           DropDetected           → STOOD_DOWN   (ceiling R0, all locks off, character off)
```

**Timings** (R0 +0, R1 +7m, R2 +18m, R3 +27m, R4 +40m from `fire_at`) are *initial values only*. A per-habit non-stationary bandit learns them: exponentially discounted evidence (half-life ~21 days) + forced ε-exploration every 14 days, targeting **P(comply) ≈ 0.5 — maximum marginal gain, never minimum P(comply)**. Scheduling where he is guaranteed to fail is harvesting failures for Sunday material, and unfairness uninstalls faster than boredom.

**Rungs 1–4 are individually opt-in in the wizard. The bandit may NEVER promote into a louder public rung without a fresh consent tap.** Escalation is automatic; volume in public is not. Third parties did not sign the contract.

**R4 arms only for habits with `lock_opt_in = true`, ≤1 per 7 days across the whole app, and never before install + 14 days.** Rip announces the hold-back on day 2 — anticipation outperforms aggression, and those two weeks build the baseline the targets are derived from anyway:

> **PITCHMAN — day 2**
> "I'm being nice. Two weeks. Enjoy it. I'm building a file."

Additionally: **nothing above R1 fires in the first 72 hours**, for any habit. The coach earns the ladder.

### 6.3 Write-ahead, idempotency, auto-void

**Write-ahead, always.** Transition → write `escalation(state, rung, next_at)` to `schedule.db` **in a transaction** → schedule the alarm → fire the effect. Never the reverse. A crash between write and schedule is recoverable (the planner re-arms from `next_at` on next open); a crash between fire and write is a double-penalty, and a double-penalty is an uninstall.

**Effects are idempotent on `(challengeId, rung)`.** Room unique index. Duplicate `AlarmFired` for a rung already entered is a no-op. Alarms *do* double-fire on some OEMs.

**AUTO-VOID.** Every alarm request records `scheduled_for`. `AlarmReceiver` records `fired_at`. If `|fired_at − scheduled_for| > 90 s`, or the process died, or the heartbeat gapped across the window — **the penalty is voided automatically, the challenge is marked `VOID_PLATFORM`, and Rip says so out loud.** An app that admits its own bugs is a month-8 app. OEM battery murder is the single most likely uninstall cause in this design.

> **BIT — auto-void**
> "That one's on ME, brother. Your phone murdered me in my sleep. Penalty's void. Go yell at Xiaomi."

### 6.4 The Android reality — the launch path

**Full-screen intents are the SCREEN-OFF path, not the in-use path.** FSI only launches an Activity when the device is locked or the screen is off; unlocked and in use it demotes to a heads-up banner. **FSI works when you don't need it.** SAW + a *visible* scrim is the only in-use path. Get this backwards and it passes testing (recent-foreground grace) and dies silently in the field.

```
setAlarmClock  (+ 4–6 DECOYS; cancel losers at fire time)
  → AlarmReceiver.onReceive()
      → startForegroundService() SYNCHRONOUSLY, before any goAsync() work
        (the exact-alarm temp allowlist is short; goAsync-then-start throws
         ForegroundServiceStartNotAllowedException)
      → IF screen off/locked : post FSI notification → LockActivity
      → IF unlocked/in use   : show TYPE_APPLICATION_OVERLAY scrim FIRST
                               (a VISIBLE window is what grants the BAL exemption
                                on 14/15 — holding SYSTEM_ALERT_WINDOW is NOT sufficient)
                             → startActivity(LockActivity) is now legal
  → LockActivity: singleInstance, setShowWhenLocked, setTurnScreenOn,
                  FLAG_KEEP_SCREEN_ON, CameraX preview, FOR THE RECORD,
                  BREAK GLASS, unconditional 90 s hard expiry
  → onStop (HOME pressed): evasion_count++ ; scrim still up ; re-startActivity in 400 ms
```

**Compose in a `TYPE_APPLICATION_OVERLAY` window** needs `ViewTreeLifecycleOwner` / `ViewTreeSavedStateRegistryOwner` / `ViewTreeViewModelStoreOwner` attached by hand (~50 lines) or it crashes on first composition. That code is now load-bearing, because the scrim grants BAL.

**You cannot block HOME.** Not with SAW, not with FSI, not with an accessibility service — only Device Owner + `startLockTask()` on a factory-reset accountless phone, which is not v1 and never will be. **So the boomerang ships and Rip narrates his own impotence.** C2 cut the re-arm for safety it had already bought with the 90 s expiry and the 1/week cap; we keep it, because an opponent who cannot come back is not an opponent, and because the platform limit is the best writing in the app:

> "I own the phone now.
> That's a lie. You can press home. You *will* press home.
> I'll be back in four hundred milliseconds because I'm forty megabytes and a dream and I don't get tired. I get patient.
> One set. That's the key. Camera's on."

**Unconditional 90-second self-expiry regardless of proof.** A coarse model's false negative must never trap a man in his own phone. The expiry is a `SystemClock` alarm inside the FGS, not a coroutine — it survives the Activity crashing.

### 6.5 Alarms, decoys, permissions

- **`AlarmManager.setAlarmClock()`.** Doze-exempt, unbatched, unrate-limited. **NOT `setExactAndAllowWhileIdle()`** — rate-limited to ~1 fire per app per 9 minutes in Doze, which will silently collapse a 7-minute ladder into one rung and you will never see it in a log.
- **Declare `USE_EXACT_ALARM`** — normal, install-time, auto-granted, no dialog, **no user-revocable toggle to forget**. Not `SCHEDULE_EXACT_ALARM` (denied-by-default on 14+, revocable in Settings; he *will* flip it while debugging something else). `USE_EXACT_ALARM` is Play-restricted; we sideload; there is no policy to enforce. Declare `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"` as the legacy fallback.
- **DECOYS ARE MANDATORY, not flavour.** `setAlarmClock` publishes its fire time to the lock screen and to every app via `getNextAlarmClock()` — which broadcasts the exact jittered time the whole unpredictability protocol exists to hide. `getNextAlarmClock()` surfaces only the *soonest*, so: **arm 4–6 decoy alarm clocks per window, cancel the losers at true fire time.** He sees *an* alarm, never *the* alarm. And Rip gets to lie about which one is real.
- **WorkManager schedules nothing time-critical.** 15-min floor, inexact by contract. Planning, reporting, GC, model training only. Mixing these is how every competitor fails silently.
- **Permission UX: staged at first use, never front-loaded.** `POST_NOTIFICATIONS` is a hard onboarding gate — deny it and the entire ladder is dead, silently, and the app must say so and refuse to continue. `SYSTEM_ALERT_WINDOW` and `USE_FULL_SCREEN_INTENT` (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, 14+) are Settings trips, requested once, at the moment R4 is first opted into — not in the wizard. Battery exemption: once, in the wizard, via the OEM gauntlet, **and never nagged again**. `ACCESS_NOTIFICATION_POLICY` is CUT (degrade to `AudioManager.ringerMode`, free). `ACCESS_BACKGROUND_LOCATION` is CUT (a Settings trip on 11+ is the nag pattern; `TYPE_STEP_COUNTER` + `ActivityRecognition` gives the same signal for one cheap dialog). Gaps show as a permanent **COACH INTEGRITY 6/8** panel — a wound Rip does bits about (*"you blinded me, brother"*), never a modal. **Never nag for a battery whitelist.** That is the most reliable uninstall trigger on Android.

### 6.6 Lifecycle: reboot, Direct Boot, Doze, force-stop, OEM

- **Reboot mid-escalation:** `BOOT_COMPLETED` → resume at the rung he *should* be at, computed from `challenge.fire_at`. **Never restart at R0** — reboot would become the cheapest evasion in the app. Logged as `evasion(kind=REBOOT)`. Never blocked (blocking reboot is impossible and monstrous). **Grace: if boot was >6 h after `fire_at`, expire normally** — don't ambush him with yesterday's water.
- **Direct Boot:** `LOCKED_BOOT_COMPLETED` needs a stripped `directBootAware="true"` receiver that touches **only `schedule.db` (device-protected storage) and `AlarmManager`**. Nothing else in that path. Neither WorkManager nor Hilt is direct-boot aware and either will throw before first unlock. `data.db` (SQLCipher, credential-protected) is unreachable there — which is why the schedule is a separate database.
- **Doze:** handled entirely by `setAlarmClock`. The FGS is started from the exact-alarm temp allowlist. No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dependency in the happy path.
- **Force-stop beats the entire stack.** Alarms cancelled, `BOOT_COMPLETED` not delivered, nothing runs until manual launch. **There is no defence and this spec does not pretend there is.** Detect via heartbeat gap on next open, log `evasion(kind=FORCE_STOP)`, make it the loudest row in the Ledger. Treat it exactly like an evasion: allowed, counted, named.
- **The OEM canary.** Samsung ("Sleeping apps"), Xiaomi/HyperOS (Autostart), Huawei, Oppo/Vivo murder foreground services and drop alarms, **and no API reports it.** So: deep-link through the manufacturer settings once (dontkillmyapp intent table, `Build.MANUFACTURER` switch, ~12 entries) → **don't trust the toggle, it is unreadable from code** → schedule a canary `setAlarmClock` at +47 min → on fire, `delta < 5 s` = PASS (Rip gloats); `> 60 s` = FAIL (auto-void every penalty in the window, Rip names the manufacturer and mocks it by name, re-run the gauntlet); never = detected on next open. **Re-run weekly, unannounced.** This is the only way to know, and it aims the contempt at `the_phone` instead of at the man. Contempt at the machine. Curiosity at the man.

### 6.7 SAFETY INTERLOCKS — the hard predicate list

An interlock **SUSPENDS**: rung frozen, resume after a 90 s settle, **never penalised, never counted, never mocked**. `mayEscalate(rung)` is a pure predicate over this set, unit-tested row by row. If a row is inconvenient to implement, **the lock does not ship**. Do not build a thing that can stand between a man and a phone call.

| # | Condition | Detection | Action |
|---|---|---|---|
| 1 | Cellular call | `TelephonyCallback.CallStateListener != IDLE` | SUSPEND |
| 2 | VoIP call | `AudioManager.mode ∈ {MODE_IN_CALL, MODE_IN_COMMUNICATION}` — free, no permission, catches what telephony misses | SUSPEND |
| 3 | **Driving** | `ActivityRecognition` IN_VEHICLE ≥70, OR speed >15 km/h sustained 20 s, OR `UI_MODE_TYPE_CAR`, OR car-profile BT CoD | SUSPEND **+15 min after last vehicle signal** |
| 4 | Cycling / running | ON_BICYCLE / RUNNING | SUSPEND **and auto-credit — he's exercising** |
| 5 | Another app holds the camera | `CameraManager.AvailabilityCallback` **AND** `AudioManager.mode` | **Cap at R1, log it.** A full suspend on camera-unavailability alone is a free, unlogged mute switch |
| 6 | Battery <15% / power-save | `PowerManager` | **Cap at R1. Never lock.** He may need maps or a call. **Log every minute; the total is read on Sunday** — unlogged escapes become the default path within a month |
| 7 | Battery <5% | `PowerManager` | Full silence |
| 8 | **22:00–08:00** | wall clock, `ZonedDateTime` | **Nothing above R0. For any habit. Including sleep. No exceptions.** |
| 9 | Thermal ≥ SEVERE | `getCurrentThermalStatus()` | SUSPEND, kill pose inference |
| 10 | Calendar busy | `READ_CALENDAR` | SUSPEND rungs ≥2 |
| 11 | DND / ringer silent | `AudioManager.ringerMode` | **Respect absolutely. Never route around it via the alarm channel.** |
| 12 | Screen mirroring / cast | `MediaRouter` | SUSPEND; the Tape never surfaces to a mirrored display |
| 13 | Meeting mode | manual tile, 90 min max | SUSPEND, logged, **unmocked** |
| 14 | SICK / INJURED / TRAVELLING / DELOAD | manual, **uncapped** | Paused. No penalty, no debt, no catch-up |
| 15 | STAND DOWN / COACH DOWN | §7 drop detector | Ceiling R0, character off, Ledger frozen |
| 16 | Timezone / DST jump | `TIMEZONE_CHANGED` | Replan in local wall-clock time |
| 17 | **Ghosting >72 h** | no opens, no proofs | **Escalation disables itself.** One daily notification, then silence. Do not become the thing he factory-resets to escape |
| 18 | Lock fired <90 min ago | `escalation` history | R4 forbidden |
| 19 | Install age <14 d | `install_at` | R4 never arms |
| 20 | Install age <72 h | `install_at` | Nothing above R1 |

**Rule 8 is absolute.** An app that fires an alarm at a man failing to sleep is an insomnia generator: it conditions the bed as a site of anticipated punishment, which is the maintaining mechanism of chronic insomnia. **Sleep gets morning consequences only, and only for the controllable antecedent (bedtime), never the outcome.** Served next day, in daylight.

**"Never lock during navigation" is a fantasy** — there is no "is navigating" API. Row 3 is the real version. Don't claim one.

**PENALTIES MAY BE ANTICIPATORY OR INTERRUPTIVE. NEVER RETRIBUTIVE**, enforced in `step`: an `Effect` above R0 requires `challenge.action_still_performable == true`. If the behaviour is complete and unchangeable, the only permitted response is data. This kills the coffee lock at 15:05 — the coffee is drunk; locking his phone over it can only produce shame, shame produces hiding, and hiding blinds the sleep pillar the rule existed to protect. Prompt at 14:45, *before*.

**The sign inversion:** the bandit may never escalate in response to multi-habit collapse. `assertLadderNeverEscalatesOn(multiHabitCollapse())` is a failing test until implemented.

### 6.8 BREAK GLASS

**One tap. Instant. First tap. No countdown, no hold, no confirm, no puzzle, no cognition. Unlimited. Never rate-limited. Never degraded.** Visible on every rung, including inside the lock. Must survive the overlay process crashing (it is a `PendingIntent` on the FGS notification, not only a Compose button).

> "Gone. Go."

Nothing follows. Not now, not Sunday, not in a callback, not ever.

**Structurally distinct from EVASION.** Different tables, different registers, and **no query may join them**:

```sql
break_glass(id, at, rung, reason_text)   -- PRIVATE. Drop-detector input ONLY.
evasion(id, challenge_id, kind /*HOME|REBOOT|FORCE_STOP|REVOKE*/, at)
```

`break_glass` is never counted, never scored, never rendered, never in the Tape, never in the Ledger, never referenced by any subsystem Rip can address. A CI test asserts no report query reaches the table. `evasion` **is** counted and roasted — pressing HOME is a move in the game; break glass is leaving the building. *A safety valve with a price is a trap with a decorative handle.* **Pain is a free break-glass reason**: zero cost, zero shame, zero mention.

### 6.9 Penalty debt

**Ceiling: 20 reps/day.** Not 40 — 40 is a compulsion budget, 20 is a nudge. **Expires at end of day. Never accrues.** Zero penalty exercise during wind-down, the sleep window, deload, SICK/INJURED, Floor Mode, or after any pain-stop. **`PENALTY_VOLUME_SHARE` is instrumented: >15% of total training volume over 4 weeks and the app reduces its own aggression automatically and says why.** The autoregulator can override the penalty engine; the reverse is forbidden, in code. **Exercise is never penance for a non-exercise event** — separate causal universes.

### 6.10 CI gates on this section

`assertNoRungAbove(R0, 22:00–08:00)` · `assertLadderNeverEscalatesOn(multiHabitCollapse())` · `assertNever { effect.rung > R1 && !challenge.actionStillPerformable }` · `assertNever { query joins break_glass }` · `assertIdempotent(challengeId, rung)` · `assertVoidWhen(delta > 90s)` · `assertNoLockBefore(installAt + 14d)` · `assertPenaltyReps(day) <= 20`. All written **failing** before the code exists.

---

## 7. THE HEALTH CONTENT — PILLARS, EXERCISE LIBRARY & PROGRESSION

### 7.1 Pillar priority — and the inversion the brief got wrong

The brief made water the headline ("bucket on my head") and the 17:00 espresso a footnote. That is backwards by effect size, and locking a senior engineer's phone over a glass of water is both the fastest uninstall available and health nonsense.

| # | Pillar | Why | Max ladder rung |
|---|---|---|---|
| 1 | Sleep **regularity** | Sleep Regularity Index beats duration for all-cause mortality (Windred 2023, UK Biobank n≈61k) | R4 (TTS) — **daylight only** |
| 2 | Smoking | Largest absolute mortality contributor | **R0. Never. Forever.** |
| 3 | Exercise | CRF among the strongest mortality predictors (Mandsager 2018, n≈122k); the only pillar that visibly improves | **R5 — full lock. The aggression lives here.** |
| 4 | Coffee timing | Only matters because it destroys #1 | R2 anticipatory (see 7.4) |
| 5 | Reading / guitar | Identity pillars; cheapest proof in the app | R3 |
| 6 | Water | Genuinely the least important thing on this list | R2. Never a lock. |

**Food is not a pillar.** There is no `is_healthy`, no calorie, no macro, no `food_verdict` column anywhere in the schema (§4). Rip mocks the choice; he never mocks the donut, the cigarette, the body, or the man.

### 7.2 WATER — the honest version, said out loud

The 8-glasses rule is a myth: a 1945 US FNB note recommended ~2.5 L/day and *also* said "most of this quantity is contained in prepared foods." The second sentence got dropped (Valtin 2002 found no basis for the rule).

```
total_ml   = clamp(bodyweight_kg * 30..35)     // EFSA 2.5 L/d men, TOTAL water
drink_ml   = total_ml * 0.75                    // food supplies ~25%
drink_ml  -= coffee_ml_today                    // Killer/Blannin/Jeukendrup 2014: 4x200mL
                                                // coffee = no difference in any hydration marker
drink_ml  += 500..750 per sweaty training hour
drink_ml  += 500..1000 if ambient > 28C         // Bulgarian August
drink_ml  += 250 in winter, and no more, and we don't pretend it's science
```
85 kg → ~1.9–2.3 L from drinks. **Coffee subtracts.** Three coffees ≈ a third of the job, already done.

**Hard cap 800 mL/hour, enforced in `WaterRepo.log()`, which refuses the insert and says why.** Kidneys clear ~0.7–1.0 L/h of free water; a gamified drinking contest with no ceiling is a hyponatraemia liability.

> "You lost. Go to bed. Do NOT try to chug it — that's how a man ends up in an ambulance on a Tuesday for absolutely nothing. I sold plastic, brother. I didn't sell kidneys."

**Prompts are event-anchored, not interval** (after wake, after each coffee, after meals, after training), max 3/day at M1 — not 8; the ceremony budget cannot afford 8. **All hydration prompts stop 2.5 h before target bed** (`WaterPromptScheduler` clamps the last slot to `bed − 150 min`) — nocturia fragments sleep, and no pillar may be allowed to injure another. Zero prompts inside the sleep window.

**The rationale ships in-app, in voice, because he will google this in month 2:**

> "You want the science? Here's the science, brother: thirst *works*. You've got kidneys like a Swiss bank. You don't need me. What you need me for is that you go SIX HOURS without swallowing because a build is running and you forget you have a BODY. I'm not hydration. I'm a *reminder that you're made of meat*."

**Proof — decided, because the corpus splits.** The gauntlet is right that a full-glass photo proves a glass and 25 s × 8/day guards an empty vault; the proof lens is right that full-glass → random 4–12 min later → empty-glass-same-pHash-background inverts the cost of faking. **Resolution: water is ONE TAP by default; the paired-glass nonce fires only on a sampled audit (~15%) while water is ENFORCED, and never once water is AUDITED.** The audit sampler is already the mechanism that makes faking a named act — water does not need its own.

### 7.3 SLEEP — enforce the antecedent, never the outcome

**You cannot command sleep onset.** Penalising "you didn't fall asleep" induces sleep-effort anxiety — the exact mechanism that *maintains* insomnia. Rip enforces exactly three voluntary things:

1. **Wake time ±30 min, seven days a week, weekends included.** This is the whole ballgame. Proven by the morning nonce.
2. **Wind-down at T−45** — screens down. Screen-off duration is free and unfakeable from the app's side.
3. **Coffee cutoff** (7.4), enforced as a sleep intervention.

The app records **two facts**: `wake_ts` and `winddown_compliant`. `SleepSegmentEvent` + screen state, passively, zero ceremony. **No sleep staging, no quality score, no "5h47m left" countdown** — nightstand accelerometer staging is noise, and sleep-tracking anxiety is a named iatrogenic condition (Baron 2017, *orthosomnia*). **Zero alarms, TTS, vibration or lock between wind-down and wake.** Sleep penalties are served the next day, in daylight, or not at all. An app that wakes you to punish you for sleep problems is a parody of itself.

### 7.4 COFFEE — taper, never ban; and the interaction that proves a coach built this

EFSA 2015: **400 mg/day habitual, 200 mg single dose** is safe in healthy adults. Coffee is not a vice at these doses and has decent evidence of being net protective. He logs the *drink*, never the mg (espresso ~63 · double ~125 · 240 mL filter ~95 · instant ~70 · black tea ~45).

**Timing is the entire game.** Half-life ~5 h, dominated by CYP1A2. Drake 2013 (JCSM): 400 mg at 0, 3 **and 6 hours** before bed all significantly disrupted sleep — including in people reporting no subjective effect. Gardiner 2023 puts the cutoff for a standard coffee at ~8.8 h. **Rule: last full coffee ≥8 h before target bed** (23:00 → 15:00); <50 mg permitted to T−6h. **Anticipatory prompt only — never a lock, because by the time we know, the coffee is already drunk.** Taper 10–25%/week, never abrupt (Juliano & Griffiths 2004: headache, dysphoria, onset 12–24 h, peak 20–51 h).

**THE CYP1A2 INTERACTION.** Smoking induces CYP1A2 and roughly **doubles** caffeine clearance. The day he quits, clearance halves — his four coffees hit like eight. Jitters, palpitations, insomnia. **He blames the quit, not the coffee, and relapses to calm down.** So: the instant `quit_date` is set in the wizard or a check-in, `CoffeeTargetWorker` cuts the target ~50% for 2–4 weeks and tells him exactly why. Never stack any other caffeine taper on a quit.

> "Sit down. This is the one thing I'm going to say straight today.
> The cigarettes were burning your coffee off at double speed. That's not a metaphor, it's an enzyme — CYP1A2, look it up, I'll wait, I've got nothing but time and no arms.
> You quit yesterday. So your four coffees today are going to hit like eight. You're going to feel your own heartbeat in your teeth at 2am and you are going to decide the QUIT did that.
> The quit didn't do that. The COFFEE did that. Two today. Two.
> …I'm not being nice. I'm being *correct*. It's rarer."

### 7.5 SMOKING — count, don't condemn. Zero penalties, forever.

Punishing a nicotine-dependent behaviour = punishing withdrawal = raising negative affect = the single best-documented relapse pathway. The penalty engine here would be actively iatrogenic. **Not in the pipeline, not in the Ledger, no ladder, no proof, no consistency score** — and you cannot photograph a not-smoke; Rip says so himself in the wizard.

What the app honestly does:
- **Log the CUE, not the cigarette** (`cue_log(ts, place, company, affect, antecedent)`). Cue awareness *is* the intervention. This flips the pillar from surveillance to insight.
- **Urge surfing** — cravings peak and decay in 3–5 min. A 5-minute timer with Rip doing a bit is evidence-aligned and free.
- **Implementation intentions** built from his own logged cues: "IF balcony after lunch, THEN 10 push-ups first" — and Rep Mode already counts push-ups, so the substitution is *provable*.
- **Screen dependence properly**: Heaviness of Smoking Index (cigs/day + time-to-first-cigarette). TTFC <30 min = high dependence = "an app is your sidekick, not your treatment."
- **Route to real treatment**: varenicline (RR≈2.2), combination NRT, nicotine e-cigs (Cochrane 2024, high certainty, RR≈1.6 vs NRT), and **cytisine — cheap, effective, and made in Bulgaria (Sopharma/Tabex)**. Surfaced, not buried. Abrupt beats gradual (49.0% vs 39.2% at 4 wk); cutting down is mostly a way of not quitting. **Quitline number is `BuildConfig.QUITLINE_BG`, verified at build time, never hardcoded from memory.**

**On a lapse the character drops.** Not softened — dropped. UI font, flat, ten seconds, then the grain comes back and he's screaming about a glass of water.

> That's a cigarette. That's all it is. It's not a verdict.
> I'm not doing a bit about this and I'm not bringing it up on Sunday.
> The button's there when you want to tell me what happened right before it.

The metric is **lapse-recovery latency**, not lapse count: *"Fell off Tuesday. Back on Wednesday. Nineteen hours. That's the fastest you've ever gotten back up. I'm FURIOUS about it."*

### 7.6 THE ARSENAL — 9 patterns × ~6 rungs ≈ 50 movements

Not 300 exercises: that's a content dump and decision paralysis on a phone. Each rung is a **promotion he can feel**. Ladders live in `:coach/src/main/kotlin/ss/coach/arsenal/Ladders.kt` (pure JVM, unit-testable, no SDK); 3 s looping bundled WebP in `assets/arsenal/`, no downloads. `CAN YOU?` badge on anything untried — the only browsing incentive that works.

| Pattern | Ladder (1 → 6+) |
|---|---|
| Squat | box→chair · box→sofa · full BW · 3s tempo · split · RFESS · RFESS+pack |
| Hinge | wall hinge w/ dowel · glute bridge · SL bridge · BW RDL · RDL+pack · SL RDL · hip thrust · **KB swing (gated)** |
| H-push | wall · counter · chair incline · **full push-up** · 3s tempo · feet-elevated · archer |
| H-pull | table row · inverted row · feet-elevated · SA pack row · weighted |
| V-pull | dead hang · scap pull · assisted · **5s negatives** · chin-up · **pull-up** · weighted |
| V-push | wall pike · box pike · pack OHP · SA OHP *(low priority — see 7.9)* |
| Carry | suitcase · jug farmer · heavy farmer · front-rack · overhead |
| Core | dead bug · **short hard plank (20–30s, not 3 min)** · side plank · hollow · Copenhagen · rollout |
| Desk antidote | 90/90 · couch stretch · t-spine ext · wall slides · chin tucks |

**Equipment honesty, stated in the wizard:** bodyweight-only *pulling* is the biggest hole in this program, and pulling is the desk worker's #1 deficit. One doorway bar + suspension trainer + band set ≈ €40 and it doubles what this app can do. A coach that pretends bodyweight-only is optimal is lying to keep onboarding smooth.

### 7.7 REP MODE — the flagship, and its honest limits

MediaPipe `PoseLandmarker Lite` (`assets/pose/pose_landmarker_lite.task`), 33 landmarks, 30 fps, per-exercise joint-angle state machine with hysteresis + 300 ms debounce. Rip counts out loud from the pre-rendered bank (digits 0–99, IVR sound-font). **Frames are never persisted — landmarks only. No form score.**

- **Whitelist of exactly 8 rep-countable movements**: push-up, squat, lunge, jumping jack, sit-up, plank hold, glute bridge, bodyweight row. Everything else is timed + start/end photo. The UI does not pretend otherwise.
- **Enforced side/45° angle + framing assist** (*"I CAN'T SEE YOUR FEET, BROTHER"*). MediaPipe `z` is relative garbage and front-on elbow angle is foreshortened. Without the assist this pillar dies of 90-second setup friction, not technical failure.
- **Stop calling it unfakeable.** He can point it at YouTube. Under zero-assertion that's acceptable; the claim must match the thesis.
- **Concentric-velocity decay is CUT as an autoregulator input** — landmark jitter + framerate variance under thermal throttle + no load calibration. The literature uses barbell velocity at fixed load. Keep it as a comment generator only.

`LAST: 12 · TODAY: 13` is the only genuinely compounding number in the app. It's utility, not comedy, so it has no decay curve.

### 7.8 The program

**PHASE 0 — wk 1–2, "The Coach Refuses."** 3×/wk, **8–12 min hard ceiling.** The most common failure is starting at week-8 intensity and quitting by week 4. So for two weeks Rip physically stops him doing more — the lock fires on a 4th set. *"BROTHER. PUT IT DOWN. I said TWO sets. You think you're impressing me? You're impressing your ORTHOPAEDIC SURGEON."* (Note: no lock at all in the first 72 h, and the lock holds back 14 days — "I'm building a file." Phase 0's interruption arrives with the lock, day 15.)

**PHASE 1 — wk 3–10, "Build the Engine."** A/B, 3×/wk, 25–30 min, RIR 3–4 → 2–3. Zone 2 (talk test) 2× 25–40 min → **150 min/wk moderate by wk 10** (the WHO 2020 floor, not a stretch goal). **Pull ≥ 2× push.**

**PHASE 2 — wk 11–24, "Load It."** 3–4×/wk, double progression, external load. **KB swing gated behind demonstrated RDL competence** (3×12 @ RIR 2 with a 20 kg pack) — swings taught early are the #1 way a desk worker hurts his back and quits. **Week 16: intervals arrive** — 6×(1/2) → 4×4 min @ ~90% HRmax (Helgerud 2007). A new toy at month 4, precisely when novelty dies.

**PHASE 3 — wk 25–40, "Make It Real."** 4×/wk, RIR 1–2 top sets, deload every 6–8 wk.

**Week-40 targets:** 1+ strict pull-up (target 5) · 20 push-ups · RFESS +20 kg · 2×24 kg farmer carry 60 m · 60 s dead hang · 2 km walk-run PB · **resting HR −5 to −10 bpm.**

Volume trajectory (hard sets/muscle/wk): `wk1–2: ~4 (deliberately below MEV) → wk3–10: 6→10 → wk11–24: 10→14 → wk25–40: 12–16, deloading to 6.`

### 7.9 The autoregulator — "progressive" means AUTOREGULATED, including EASIER

Linear escalation guarantees injury or quit by month 4. An app that only ever says MORE is noise, and noise gets muted. Prescribed by **RIR, never %1RM** (Zourdos 2016) — he has no gym and no 1RM. `AutoregWorker` runs daily 05:00, writes `autoreg_state(date, volume_mult, rungs_frozen, reason)`.

```
range 8..12, sets 3
all sets hit 12 AND last-set RIR >= 1     -> +load OR +1 rung; restart at 8
set 1 misses 8 on TWO consecutive         -> deload 10% OR -1 rung
reported top-set RIR >= 4 twice           -> add load without asking; Rip calls him a liar, in character

any 2 of {RHR > baseline+7, sleep window <5.5h x2, wake drift >90min}
  -> AUTO-DELOAD TODAY: volume x0.6, no new rungs, NO PENALTY, reframed as strategy never as mercy
missed session       -> NEXT SESSION UNCHANGED. Never make up volume.
2 consecutive misses -> FLOOR MODE
rung advancement     -> CAPPED at 1 per pattern per 2 weeks, ALWAYS (tendons lag muscle; month 4 is the danger zone)
scheduled deload     -> every 6-8 weeks, volume x0.5, intensity held. NON-NEGOTIABLE.
"+1 forever" is CUT. Fixed clinical ceiling per pattern. It never raises a target as a reward for hitting it.
```

**The autoregulator can override the penalty engine. The penalty engine can never override the autoregulator.** Penalty debt ceiling **20 reps/day**, expires end of day, never accrues, none during wind-down/sleep/deload/Sick/Injured/Floor/after a pain-stop, never as a consequence of a food or weight event (structurally impossible — no such columns). If `PENALTY_VOLUME_SHARE > 15%` over 4 weeks, the app reduces its own aggression and says why. The "37% of your pulling came from screwing up" line is **cut**: that's a clinical finding, not a brag.

> "Five hours. Three nights running. Your resting heart rate is up nine.
> Today is a WALK. That's an ORDER.
> I'm not being nice. I'm being SMART. There's a difference and you have never once been able to tell."

**Injury rules, encoded:** pull ≥2× push months 1–3, ≥1:1 forever · overhead pressing **optional** for this population, don't burn shoulder capacity on a nice-to-have · no plyos months 1–3 · no running from zero, walk-run only · **pain: dull, <3/10, non-progressive, settles in 24 h → work through; sharp / radiating / joint-line / worse next day → STOP.** "PAIN" is a free break-glass reason: zero cost, zero shame, zero Ledger mention. **Rest days are provable, celebrated wins** counted toward consistency; training on a declared rest day is a **miss**, not a bonus.

### 7.10 The Floor, never-miss-twice, and the four modes

> **The Floor = ONE set. Or a 10-minute walk. Never zero.**
> **The Floor counts as a FULL success. Not partial credit. Never yellow. Anywhere, including the widget.**

The instant the Floor reads as failure, he skips it, and he's out. Lally 2010: automaticity plateaus at a **median 66 days** (range 18–254 — the 21-day myth dies), and **missing a single opportunity does not materially impair it**; the *second consecutive miss* is what kills habits. So the metric is **days since last double-miss**, never a consecutive-day streak. Single misses absorbed invisibly. Rip may comment; the *system* must not punish.

**SICK · INJURED · TRAVELLING · DELOAD — v1, mandatory, uncapped, one tap, no shame before/during/after.** An app with no "I have the flu" state gets deleted the first time he has the flu. Everyone forgets this and everyone dies of it.

### 7.11 TEST WEEK — and the one sincere congratulation

Every 8 weeks from week 12: max push-ups · dead hang · 2 km walk-run · RFESS reps · resting HR. **A renewable "wow" that requires no writer** — six genuine, dated, self-generated *"holy shit, I did that"* moments across ten months, from ~200 lines of code. No joke bank competes. This is the app's outcome variable, which is exactly why weight doesn't need to be.

**The product's single sincere congratulation is spent here**, on the first strict pull-up. `unique_line(id='SINCERE_ONE')`, fires once, ever, burns.

> "Twelve. In March you did four.
> I didn't do that. You did that.
> …I helped a little. Fifteen percent. Get out of my face."

### 7.12 Weight, measurement, and the tripwire

**Rip does not know the number exists.** `weight_delta` is deleted from the slot enum; the table lives in a coach-blind vault (`records.db`, separate SQLCipher file, no join path from `coach.db`).

- **Manual entry, weekly maximum** — the app refuses more, warmly. Scale-OCR is cut: ML Kit is natural-scene-trained, reads 7-segment LCD at ~40–60% and silently drops the decimal (81.5 → 815). Ideology lost to physics.
- **EWMA trend only, α≈0.1** (Hacker's Diet). Never today's number as a headline — 1–2 kg glycogen/sodium swings swamp a real 0.5 kg/week change, so showing today's number is showing him noise and calling it feedback. No arrows, no colours, no goal field, no delta over any window <90 days. No BMI in the UI. No red/green anywhere (success is white + gold).
- **Waist, chest, arms: CUT.** Body checking on a calendar invite. Waist-to-height <0.5 is a better metric than BMI and it is not worth the mechanism.
- **Progress photos: CUT.** No side-by-side, no before/after, ever.
- **SCOFF + MDDI in the wizard gate this entire feature set** (SCOFF was validated on young women and misses muscularity-oriented presentations — the only ED this app could *cause*); re-run quarterly, joined by behavioural detection: penalty-volume share, training-through-pain, Floor-Mode refusal, break-glass rate. **PAR-Q+ (7 items, verbatim) before any exercise prescription.**

**RATE-OF-CHANGE TRIPWIRE — always live, regardless of module state.** `TripwireWorker` is enqueued unconditionally at install and cannot be disabled: trend loss >1% BW/week sustained 3 weeks, **or** BMI trending <18.5 → **character drops, weight module auto-disables, one flat screen, GP routing.** Not a nag. A stop. The exercise-only path produces the same trajectory, which is why it must fire with every module off. **The app never praises a downward weight trend.** Nobody does a bit on that screen.

### 7.13 What can honestly be photo-proven — blunt

| Claim | Verdict |
|---|---|
| "I did 12 push-ups" | **Provable.** Pose landmarks. Strongest proof in the app. Fakeable only by pointing it at YouTube. |
| "I read 22 pages" | **Provable-ish.** OCR novelty hash + strictly increasing page + **duration binding** (0.3–2.5 pages/min band). Pages alone prove page-turning, not reading. |
| "I played guitar" | **Provable.** 60 s audio: RMS + spectral flux + onset. Duration carries it. |
| "I went to the gym" | **Provable.** Geofence dwell + accelerometer + one in-gym nonce. Essentially unfakeable without going. |
| "I drank the water" | **Not provable.** A full glass proves a glass. Ingestion is invisible. One tap; audit-sampled nonce only. |
| "I didn't smoke" | **Never provable. Absence cannot be photographed.** |
| "I slept" | Not provable. Wake time and screen-off are. |
| "This food is healthy" | **Not a visual property.** Grilled salmon and salmon confit in 60 g of butter are the same photo. No column, no model, no verdict. |

> "I CANNOT SEE YOU. I'm a rock with a camera and a personality disorder. So I'm TRUSTING you — and if you lie to me, brother, you're not beating ME. I don't have feelings. I barely have a *tape*."

---

## 8. DATA MODEL, BACKGROUND WORK & MODULE ARCHITECTURE

### 8.1 Two databases, deliberately

- **`schedule.db`** — **device-protected storage** (`createDeviceProtectedStorageContext()`). Holds only what a `directBootAware="true"` receiver needs to re-arm alarms before first unlock: `challenge`, `decoy_alarm`, `escalation`, `boot_state`. **Unencrypted** (there is no key before first unlock; that is the whole point) and therefore holds no content — ids, timestamps, rung integers. **Neither WorkManager nor Hilt is direct-boot aware; either will throw on that path.** The Direct Boot receiver touches `schedule.db` and `AlarmManager` and nothing else, and constructs its DAO by hand.
- **`data.db`** — credential-protected, **SQLCipher** (`net.zetetic:sqlcipher-android`, passphrase in `MasterKey`-wrapped `EncryptedSharedPreferences`, `SupportOpenHelperFactory`). Everything else. Biometric gate on THE ARCHIVE surface only — never on TODAY, or the app becomes a chore.

`allowBackup=false`, `dataExtractionRules` empty. `java.time`/`ZonedDateTime` everywhere, never epoch arithmetic; a coffee cutoff and a wake time are wall-clock concepts. DST and timezone-change days are unit tests.

### 8.2 The schema

Room entities, `data.db` unless marked. Kotlin types shown.

```kotlin
// ── PIPELINE (the odometer's source of truth) ────────────────────────────
habit(id: Long, key: String, title: String,
      kind: HabitKind /*PHOTO|AUDIO|PASSIVE|MANUAL|CONFESSION*/,
      verifierId: String?, stage: Stage /*ENFORCED|AUDITED|TRUSTED|RETIRED*/,
      stageEnteredAt: Instant, isHobby: Boolean, penaltyTier: Int,
      lockOptIn: Boolean, enabled: Boolean, configJson: String)
stage_transition(id: Long, habitId: Long, from: Stage, to: Stage,
      reason: Reason /*GRADUATED|CAUGHT_FAKE|SUBDIVIDED|USER_RETIRED*/, at: Instant)
  // Reason has NO `CONFESSED` value. Graft 1 is enforced in the enum, not in a branch.
goal(id, habitId: Long, period: Period, target: Double, unit: String,
     ceiling: Double?, activeFrom: LocalDate, activeTo: LocalDate?,
     source: GoalSource /*BASELINE|USER|CYP1A2_TAPER*/)
schedule_window(id, habitId, dowMask: Int, startMin: Int, endMin: Int,
     jitterMin: Int, expectedCount: Int)

// ── CHALLENGES  (schedule.db) ────────────────────────────────────────────
challenge(id: Long, habitId: Long, windowId: Long, fireAt: Instant,
     isAudit: Boolean, nonceJson: String, state: ChallengeState, bootId: String)
decoy_alarm(id: Long, challengeId: Long, fireAt: Instant, cancelledAt: Instant?)
escalation(id: Long, challengeId: Long, rung: Int, enteredAt: Instant,
     nextAt: Instant?, suspendReason: String?, evasionCount: Int)

// ── PROOF (the archive; kept forever) ────────────────────────────────────
proof(id: Long, challengeId: Long?, habitId: Long, wallAt: Instant,
      elapsedAt: Long /*SystemClock.elapsedRealtime, clock-jump detection*/,
      bootId: String, imagePath: String, phashFrame: Long, phashObject: Long,
      pixelSha256: ByteArray, verifierId: String, label: String,
      confidence: Float, auditGestureOk: Boolean?, suspicion: Float,
      source: ProofSource /*LIVE|RETRO|CONFESSED*/, appealed: Boolean)
  // There is NO `accepted` column. Nothing is ever rejected. Zero-assertion.
object_embedding(id, habitId, vec: ByteArray, clusterId: Long?,
      userName: String?, capturedAt: Instant, reEnrolled: Boolean)
object_cluster(id, habitId, userName: String? /*"Gary"*/, centroid: ByteArray,
      firstSeen: Instant, occurrenceCount: Int)
  // `reEnrolled` is the one-tap k-NN fix (Graft 8). appealRate = appealed/audited,
  // read only by ModelQualityWorker, never by :coach. >25% = the model is broken.

// ── THE FOUR DISTINCT OBJECTS (§8.4) ─────────────────────────────────────
confession(id, habitId, at: Instant, note: String?)   // FREE. UNLIMITED. NEVER DEMOTES.
break_glass(id, at: Instant, rung: Int)               // PRIVATE. Never rendered.
evasion(id, challengeId, kind: EvasionKind /*HOME|REBOOT|FORCE_STOP|REVOKE|CLOCK_JUMP*/,
        at: Instant, purgeAfter: Instant)
ledger_entry(id, kind: LedgerKind /*CAUGHT_FAKE|EVASION|OEM_MURDER|CHEAT|NEAR_MISS*/,
        at: Instant, evidenceJson: String, purgeAfter: Instant)

// ── WORK (kept forever) ──────────────────────────────────────────────────
exercise(id, key, name, pattern, rung: Int, equipment, repCountable: Boolean,
         poseRulesetJson: String, asset: String)
workout_set(id, exerciseId, repsCounted: Int, repsClaimed: Int, rir: Int?,
         isPenalty: Boolean, isFloor: Boolean, at: Instant)
penalty_debt(id, reps: Int, issuedAt: Instant, expiresAt: Instant /*end of day*/,
         clearedAt: Instant?)   // CHECK(reps <= 20) — Graft 10, at the DDL.
deload(id, weekStart: LocalDate, reason /*SCHEDULED|AUTOREG*/)
test_week(id, weekStart: LocalDate, metric: String, value: Double, unit: String)
program_state(id, phase, deloadDueAt, floorMode: Boolean, consecutiveMisses: Int)
book(id, title, coverPhash: Long)
reading_session(id, bookId, pageFrom: Int, pageTo: Int, elapsedS: Int,
         shingleSet: ByteArray, isReread: Boolean, at: Instant)
audio_session(id, habitId, startedAt, activeSeconds: Int, topClass, meanScore: Float)

// ── CLINICAL ─────────────────────────────────────────────────────────────
smoking_cue(id, at, place, company, emotion, precededBy, lapse: Boolean,
         recoveryHours: Int?)   // zero penalty, zero score, forever.
screening(id, instrument /*PARQ|SCOFF|MDDI|PHQ2|HSI*/, at, score: Int,
         gatesJson: String)
stand_down(id, startedAt, days: Int, reason /*SICK|INJURED|TRAVEL|GRIEF|NONE*/)
coach_down(id, triggerJson, startedAt, endedAt, userAction)
interlock_event(id, kind, startedAt, endedAt)
weight(id, valueKg: Double, at: Instant, ewma: Double)   // :body module only.
tripwire_state(id, evaluatedAt, trendPctPerWeek: Double, firedAt: Instant?)

// ── COACH ────────────────────────────────────────────────────────────────
voice_line(id, packId, skeletonId, situation, register, target: Target,
      tier: Int, text: String, assetPath: String?, playCount: Int,
      lastPlayedAt: Instant?, retiredAt: Instant?)
bit_slot(id, kind, clusterId, lifecycleStage, supportN: Int, seededAt, retiredAt, killed)
slot_stats(id, slotId, fires: Int, complyWithin10m: Int, dismissedNoAction: Int)
learned_model(id, updatedAt, weightsJson, auc: Double)
correlation_fact(id, pair, r: Double, n: Int, pAdj: Double, replicated: Boolean,
      phrasing, shownAt, overridden: Boolean)
oem_canary(id, scheduledFor, firedAt, deltaMs: Long, manufacturer, voidedPenalties: Int)
heartbeat(at: Instant)
export_run(id, startedAt, finishedAt: Instant?, treeUri: String,
      filesWritten: Int, bytes: Long, error: String?)   // §8.6
tape(id, weekStart: LocalDate, json: String, deliveredAt, openedAt: Instant?)
  // openedAt IS THE NORTH STAR. The kill criterion reads this column.
```

### 8.3 Deliberate absences — the columns that do not exist

A flag Arsen wrote he can unwrite at 01:00; a column that does not exist he cannot conjure.

**Food is absent from the schema.** No `is_healthy`, no `calories`, no `macros`, no `food_verdict`, no `meal_photo`, no `junk` enum, no `nutrition_*` anything. There is no `:food` module and no food `HabitKind`. The donut-grief bit is dialogue; it needs no table. **The harm is the review loop, not the classifier's accuracy** — so we delete the loop's storage.

Also absent, each for a stated reason: `goal_weight`, `bmi`, `body_photo`, `before_after` (weight leaves the game; `weight.ewma` is the only derived field and `:coach` cannot see the module); `accepted` on `proof` (zero-assertion — rejection is what the app doesn't do); `streak_count` (streaks are not a primary metric; consistency is computed, never stored); `confession_count_this_week` (**Graft 1 — the cap is deleted, so the counter that would enforce it does not exist**); `Target.BODY|WEIGHT|APPEARANCE|WORTH` are not enumerable values of the `Target` enum, so a line that attacks the self will not compile.

CI lints, build-failing: `grep` the generated Room schema JSON for `is_healthy|calorie|macro|food_verdict|goal_weight|bmi`; assert no `:coach` source file imports `android.*`; assert no `:coach` Gradle dependency path reaches `:body`.

### 8.4 The four distinct objects — different tables, no joins

| Object | Counted? | Rip may reference? | Purges? |
|---|---|---|---|
| `confession` | **Never** | Yes — warmly, once | Yes, 28d |
| `break_glass` | **Never** | **Never** | Yes, 28d |
| `evasion` | Yes | Yes, via `ledger_entry` | Yes, 28d |
| `ledger_entry` | Yes | Yes (the only failure surface) | Yes, 28d |

Enforced structurally, not by policy: **four tables, four DAOs, no foreign keys between them, and no `@Query` in any DAO may name two of them.** A CI test parses every `@Query` string in `:core:database` and fails if one contains two of `confession|break_glass|evasion|ledger_entry`. `BreakGlassDao` is provided only to `StandDownDetector` via a `@Binds` in a Hilt component the `:feature:tape` and `:coach` graphs cannot reach — pressing HOME is counted and roasted; break glass is never counted, never rendered, never referenced by any subsystem, and never mocked in the moment.

`confession` writes **no** `stage_transition`. Confession is free, unlimited, always visible, warm. Only `CAUGHT_FAKE` demotes. The failing unit test: `assertNever { stage_transition.reason == GRADUATED.inverse && cause is Confession }`.

### 8.5 The Ledger forgets — `LedgerPurgeWorker`

**The rule, in one line: evidence of failure purges at 28 days; evidence of work is kept forever.**

```
LedgerPurgeWorker  @04:30 daily, no constraints
  DELETE FROM ledger_entry WHERE purge_after < now()
  DELETE FROM evasion      WHERE purge_after < now()
  DELETE FROM break_glass  WHERE at < now() - 28d
  DELETE FROM confession   WHERE at < now() - 28d
```

Hard `DELETE`, not a flag, not a view filter, not `WHERE hidden=0`. `:coach` has no DAO that can read a purged row because the row is gone. **"I'm going to remember" is now false** — the tape degrades; he structurally cannot hold it against you. `proof`, `workout_set`, `reading_session`, `test_week` are never touched by this job. Rumination infrastructure is what we deleted; the compounding asset is what we kept.

### 8.6 The archive, and the export that is a v1 ship blocker

Proofs → `filesDir/proofs/yyyy/MM/`, **downscaled at capture to 1024px q80 (~150 KB)** from the live `ImageProxy`, originals never written. 5/day × 300 days ≈ **220 MB** instead of ~6 GB. App-private, encrypted, never MediaStore, never his gallery — this app photographs his home and his books. **There is no 90-day media GC.** Deleting the archive at 90 days would destroy the only asset that compounds; that was a privacy instinct killing the product.

```
ExportWorker  weekly @Sun 03:00 + on-demand.  Constraints: BatteryNotLow, requiresCharging=false.
  → SAF tree URI (user-picked once in the wizard; he Syncthings that folder to ardi)
  → photos + readable JSON/CSV sidecar + schema.md. Writes export_run on every attempt.
  → Backoff EXPONENTIAL 30s. Failure writes export_run.error and DOES NOT silently retire.
```

**Ship blocker.** The wizard cannot complete without a tree URI. TODAY carries a permanent, un-dismissable **proof-of-last-run** chip: *"EXPORTED 3 DAYS AGO — 1,412 FILES."* At **14 days without a successful run the app fails loudly**: a non-dismissable banner, every non-safety alarm suspended until it is resolved, and Rip says it out loud, once:

> "Fourteen days. No export. Which means every set, every page, the whole file — it's in ONE box, in your POCKET, and brother, I have SEEN what you do to phones. Pick the folder. I'll wait. I'm not doing another thing until you pick the folder."

The README states the principle verbatim: *"If the app only retains him because leaving destroys the archive, it wasn't a product, it was a lock. Ship the export and find out."* One-tap full export at top level, always, offline, open format. Any single proof deletable, and Rip never comments on a deletion. One-action total nuke.

### 8.7 AlarmManager — every use

**`AlarmManager.setAlarmClock()` for every rung. Nothing else.** Not `setExactAndAllowWhileIdle` (rate-limited to ~1 fire per 9 min in Doze — it will silently collapse a 6-minute ladder). Declare **`USE_EXACT_ALARM`** (normal, install-time, auto-granted, no revocable toggle to flip at 2am while debugging), **not** `SCHEDULE_EXACT_ALARM`. Play-restricted; we sideload; it's ours.

`setAlarmClock` publishes the fire time to the lock screen via `getNextAlarmClock()`, which defeats the unpredictability that carries all the deterrence. **`DailyPlanner` arms 4–6 decoys per window** (`decoy_alarm` rows, distinct `PendingIntent` request codes); at true-fire time `AlarmReceiver` cancels the losers and stamps `cancelledAt`. He sees *an* alarm, never *the* alarm — and Rip gets to lie about which one was real.

Uses: (1) rungs 0–4 per challenge; (2) decoys; (3) `OemCanaryWorker`'s canary at `now + 47min`, `delta > 60s` → void every penalty in the window; (4) the 90s hard expiry on a LOCK. Write-ahead: state is committed to `schedule.db` **before** the alarm is scheduled; effects are idempotent on `(challengeId, rung)`. `AUTO_VOID` when `|actual − scheduled| > 90s`. `AlarmReceiver.onReceive` starts the FGS **synchronously**, before any `goAsync()` work, or the exact-alarm temp-allowlist expires into `ForegroundServiceStartNotAllowedException`.

### 8.8 WorkManager — planning only, never the nag

Its floor is 15 minutes and it is inexact by contract. Mixing these is how every competitor fails silently.

```
DailyPlanner        @04:00 daily      materialize challenges + jitter + decoys → armAll()
                                      constraints: none. Expedited on first run post-boot.
PipelineWorker      @04:10 daily      evaluate graduation/demotion on measured evidence;
                                      write stage_transition; recompute the odometer.
LedgerPurgeWorker   @04:30 daily      §8.5
MediaGcWorker       @04:40 daily      temp/cache only. NEVER the downscaled ledger.
ModelUpdateWorker   @03:30 daily      retrain LR on own rows (month 3+); bandit discount t½ 21d
                                      constraints: DeviceIdle, BatteryNotLow
TapeWorker          @Sun 20:00        build + render the Tape
ExportWorker        @Sun 03:00 + adhoc §8.6  constraints: BatteryNotLow
MinerWorker         @Sat 05:00        correlations + BH correction + holdout replication
HeartbeatWorker     every 15 min      INSERT heartbeat(now)  [gaps = OEM murder evidence]
SleepSampler        every 15 min      UsageStats screen state + charging. No FGS, no notification.
OemCanaryWorker     weekly, random    the canary
DropDetector        @06:00 daily      collapse signature → STAND DOWN
TripwireWorker      @06:05 daily      ALWAYS-LIVE regardless of module state (Graft 20)
ScreenerWorker      monthly PHQ-2; quarterly SCOFF/MDDI
ModelQualityWorker  @Sun 04:00        appeal rate; private; never shown to Rip
AlarmReconciler     on: BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, app foreground,
                        MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED
```

All periodic work uses unique names with `ExistingPeriodicWorkPolicy.UPDATE`. **Zero jobs may emit a notification, alarm, TTS, vibration, or lock between wind-down and wake** — asserted in the soak test as `assertNoRungAbove(R0, 22:00–08:00)`.

### 8.9 Modules

```
:coach          *** PURE KOTLIN/JVM. ZERO ANDROID IMPORTS. NO HILT. ***
                grammar, skeleton blocking, register mixer, scarcity/speech budget,
                the jurisdiction odometer, pipeline state machine, EscalationEngine.step(),
                interlock precedence, language ladder, consistency math, Drop Detector,
                autoregulator, double progression, pHash math, reading-delta rules,
                correlation miner + BH, the Ledger, ceremony accounting.
                step(state, event, now, interlocks) -> (state, effects). Data in, strings+effects out.
                250+ JVM tests. `gradlew :coach:test` = ~2s, no emulator, no SDK.
:coach-cli      JVM main(). Desktop harness. §8.10
:core:model :core:database :core:datastore :core:designsystem
:vision :audio :voice :enforcement :learning :sensors
:body           weight ONLY. :coach has no dependency on it — a compile-time guarantee.
:feature:wizard :feature:today :feature:proof :feature:archive :feature:arsenal
:feature:tape :feature:lock :feature:comeback
:app            Compose nav, Hilt wiring, the effect executor
```

**`:coach` is the whole product and it has zero Android imports.** Every item on the "survives month 8" list lives there. This is not architectural purity — with no emulator and a slow sideload loop, it is the only thing standing between the ladder and untested production code. `:enforcement` is a thin, dumb executor behind an `AlarmScheduler` interface with a `FakeAlarmScheduler` in tests.

### 8.10 `:coach-cli`

A JVM `main()` with no Android on the classpath. Four commands, all run on the laptop in seconds:

- `dump-lines --n 1000 --register mix --jurisdiction 2` → a text file you **read as prose**.
- `soak --days 300 --seed 7` → the 10-month simulation; full transcript to file; runs the executable assertions (`assertRegisterMix`, `assertNoSkeletonRepeatWithin(21)`, `assertCeremonySeconds(M8, 90)`, `assertNever { register==DRY && trigger != CAUGHT_FAKE }`, `assertNever { "brother" in line && register in {DRY, GHOST} }`, `assertArenaPctIsPureFunctionOf(jurisdiction)`).
- `fragments --budget` → prints the fragment-bank arithmetic against the speech budget and **fails the build if the bank exhausts before day 300** (Graft 21).
- `ceremony --table` → regenerates the M1/M4/M8 budget table; CI diffs it and fails on drift.

### 8.11 APK budget (arm64-v8a only, R8, `noCompress += "tflite"`)

| Component | Size |
|---|---|
| Compose + Room + WorkManager + CameraX + Hilt + Coil | ~9 MB |
| MediaPipe `tasks-vision` natives | ~18 MB |
| ML Kit Text Recognition v2 (**bundled** — never `play-services-mlkit-*`, which fetches at runtime and breaks the offline guarantee) | ~9 MB |
| tesseract4android + `bul.traineddata` (**v1**, Graft 17) | ~15 MB |
| HandLandmarker `.task` | 7.5 MB |
| PoseLandmarker Lite `.task` | 6 MB |
| EfficientNet-Lite0 int8 | 5.4 MB |
| YAMNet int8 | 4 MB |
| SQLCipher natives | ~4 MB |
| Arsenal assets (animated WebP, **not** video) | ~8 MB |
| Hero clips + IVR sound-font (digits 0–99 + units, Opus) | ~3 MB |
| **Total** | **~89 MB** |

**There is no Play limit. Delete the "hard cap" rather than believe it.** The scarce resource is ceremony seconds (§5), not megabytes. Voice pack ships as a separate GitHub Release asset, built by a **manual** workflow on the 3090 — never regenerated in CI.

### 8.12 `gradle/libs.versions.toml` — pinned

```toml
[versions]
agp = "8.7.3"           # PINNED BY R:/gradle-8.9 — AGP 8.8+ requires Gradle 8.10.2+/8.11.1+.
                        # Decision: keep the local wrapper authoritative; bump both or neither.
kotlin = "2.0.21"       # Compose compiler is the Kotlin plugin from 2.0; no composeOptions block.
ksp = "2.0.21-1.0.28"
composeBom = "2024.12.01"
nav = "2.8.5"; lifecycle = "2.8.7"; activity = "1.9.3"
room = "2.6.1"          # not 2.7.x: KMP-era artifact split buys us nothing and moves the DAO codegen.
work = "2.10.0"; hilt = "2.52"; hiltWork = "1.2.0"; datastore = "1.1.1"
camerax = "1.4.1"; biometric = "1.2.0-alpha05"; coil = "2.7.0"
sqlcipher = "4.6.1"     # net.zetetic:sqlcipher-android (NOT android-database-sqlcipher, EOL)
sqliteKtx = "2.4.0"; securityCrypto = "1.1.0-alpha06"
mediapipe = "0.10.20"; tflite = "2.16.1"
mlkitText = "16.0.1"    # BUNDLED. com.google.mlkit, never com.google.android.gms.
tesseract = "4.8.0"     # cz.adaptech.tesseract4android:tesseract4android
junit5 = "5.11.4"; turbine = "1.2.0"; robolectric = "4.14.1"
```

`compileSdk = 35`, `targetSdk = 35`, `minSdk = 31` (SAW/FSI/BAL semantics below 31 are a second app). JDK 17 toolchain (`R:/jdk17`), `jvmToolchain(17)` on every module so `:coach:test` runs identically on the laptop and in Actions.

### 8.13 Compose & DI conventions

- **Two Activities.** `MainActivity` (single-activity Compose nav, type-safe `@Serializable` routes on nav 2.8) and `LockActivity` (`singleInstance`, `setShowWhenLocked`, `setTurnScreenOn`, `FLAG_KEEP_SCREEN_ON`, `excludeFromRecents`).
- **Compose in a `TYPE_APPLICATION_OVERLAY` window** must have `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner` and `ViewTreeViewModelStoreOwner` attached by hand or it crashes on first composition. ~50 lines, and now load-bearing.
- **UDF.** ViewModels expose one `StateFlow<UiState>`, collected with `collectAsStateWithLifecycle()`. **No ViewModel, no Flow, no coroutine, and no Android type crosses into `:coach`** — the boundary is `step()` in, `List<Effect>` out; `:app` executes the effects.
- **Hilt in Android modules only.** `:coach` uses constructor injection and is `@Provides`-ed at the `:app` boundary with an injected `java.time.Clock` (`FixedClock` in tests). The Direct Boot receiver is **not** `@AndroidEntryPoint` and news up its DAO manually.
- **`:core:designsystem` owns every colour token.** A CI lint fails the build on any hex outside the token file, and on any token whose hue falls in the green band — success is white and gold, and that is enforced by the compiler, not by taste. No emoji in any string resource (lint on the resource merger).

---

## 9. THE TAPE — THE WEEKLY REPORT

**Sunday 20:00. 60–90 seconds. Vertical, one screen at a time, paced, thumb-advanced.** Not a report — the infomercial he can't stop making. The daily loop is data collection for this. Optimise it like it's the whole app, because it is: the Tape's open-rate is one of the two numbers that can kill the project (§*Kill criterion*).

### 9.1 The name, earned four ways

1. **The fight tape.** What a corner man makes you watch on Monday. Not the highlights — the round you lost.
2. **The VHS tape.** What Rip is physically made of. He lives in it. When the Tape plays, he is briefly whole again — this is the only 90 seconds a week he gets a show.
3. **The tape doesn't lie.** The archive. Photographs with timestamps. He is 94% wrong about vision and 100% accurate about what he logged.
4. **The measuring tape.** The only body metric we allow ourselves to like — and we don't ship it. The fourth reading is a joke about an absence, and it quietly does the work of the cut weight module. Nobody has to explain why there's no scale in a product called TAPE.

### 9.2 Segments, in order

Built by `TapeBuildWorker` (Sunday 18:00, expedited, `:coach` pure-JVM composer at `coach/src/main/kotlin/dev/secondspine/coach/tape/TapeComposer.kt`), notified by `TapeNotifyWorker` at 20:00. Composition is deterministic given `(week_id, seed)` so the soak test can render 40 Tapes to a text file and you read them as prose.

| # | Segment | Data behind it | Budget |
|---|---|---|---|
| 1 | **COLD OPEN** | One number, no context, from `tape_edition.cold_open_stat`. Never the same stat two weeks running (21-day skeleton lockout). | 3 s |
| 2 | **THE MONTAGE** | `proof` WHERE week — every photo, grid, animating in fast, one thud each. **Unnarrated. Rip does not speak over it.** Un-scored, un-graded. The emotional core and the reason he opens it at month 8: a photo journal wearing an evidence-locker costume. Full screen, not a card. | 12–20 s |
| 3 | **THE LEDGER** | §9.3. Cold, flat, court-clerk. Rolling 30-day `RIP n : ARSEN n` in the header. | 10 s |
| 4 | **VERIFIED vs CLAIMED** | §9.4. | 6 s |
| 5 | **THE ROAST** | §9.5. Max 3 lines, each tappable into the chart behind it. | 15 s |
| 6 | **TRENDS** | The real dashboard. Sparklines, consistency %, resting HR, Test Week PBs, weight EWMA (his, cold, unvoiced, no number as headline, no colour — Rip has no read access to this table). **Zero character.** This card exists to prove this is a real app. | 10 s |
| 7 | **WHAT I LEARNED ABOUT YOU** | Correlation miner + non-stationary bandit re-learning, narrated. Mandatory weekly. Fallback shipped: *"I learned nothing. You were boringly consistent. Disgusting."* | 6 s |
| 8 | **RIP'S DESK** | §9.7 — jurisdiction status. Standing segment. He narrates his own decline. | 6 s |
| 9 | **THE ONE THING** | The single thing he respects. Grudging. Behaviour-attributed, never trait, never body. | 5 s |
| 10 | **THE OFFER** | Next week's bet. Rip stakes jurisdiction, never pixels, never "a week of silence." | 8 s |
| 11 | **SIGN-OFF** | Rendered from jurisdiction, not from the calendar. | 3 s |

**The mix inverts on the odometer, not the month:** jurisdiction 4 → ~60% comedy / 20% charts / 20% coach. Jurisdiction 1 → ~20/60/20. He's a dashboard guy who ships a monitoring product; the charts are what's still good on a week when nothing funny happened.

**Ceremony:** the Tape has its own weekly allowance of **90 s**, separate from the daily budget, amortising to ~13 s/day — inside the month-8 cap of 45–90 s/day *including* it. Any segment that pushes an edition over 90 s is cut from that edition by the composer at build time, lowest-`salience` first. The Tape is the only surface allowed to hold its length as jurisdiction falls; everything else shrinks.

### 9.3 THE LEDGER, and the leaderboard demoted to one row

`ledger_entry(id, ts, kind, habit_id, evidence_proof_id?, note)`. `kind ∈ {CAUGHT_FAKE, EVASION, EVASION_REBOOT, FORCE_STOP, OEM_KILL, CLOCK_JUMP, POWER_SAVE_MINUTES, DEMOTION}`.

Read flat. No jokes. Monospace. This tonal drop is what gives the rest of the app its teeth, and cold data has no decay curve because it was never trying to entertain.

**Never in the Ledger, enforced by schema not by style:** `break_glass_event` is a separate table and **no query may join it** — a CI test asserts no SQL in the module references both. Confessions are a separate table (`confession`) and never appear as an entry. Food does not exist as a column anywhere. Weight is not joinable to `ledger_entry`. Smoking has no penalties and no rows.

**THE LEDGER FORGETS.** `LedgerPurgeWorker` runs daily 04:00: any `ledger_entry` whose `kind`+`habit_id` pattern has not recurred within 28 days is **hard-deleted** — from the table, not from the view. Rip's addressable memory is `SELECT * FROM ledger_entry` and nothing else, so *"I'm going to remember"* is **false**. He's a VHS ghost. The tape degrades. He structurally cannot hold it against you. The Ledger card prints its own window in the corner: `ROLLING 28 DAYS`. A permanent unforgiving record of your failures is rumination infrastructure; this is the one place the product is kinder than it pretends.

**The cheat leaderboard is one row.** Not a segment. Reason, stated once: cheat variety is an exploration phase and exploration ends. By month 3 he has one cheap cheat that works and never varies it, so a segment built to remove his win condition becomes a weekly receipt proving he won. The framing survives in a single line — *you cannot beat an audience that collects your cheats* — and the Ledger's renewable rows (evasions, force-stops, OEM murders, clock jumps, reboot-evasions, near-misses) carry the weight, because those generate new material forever and a rooted engineer's cheat repertoire does not.

### 9.4 VERIFIED vs CLAIMED — three states, and a withdrawal

- **VERIFIED** — audited and passed, or duration-bound. White.
- **UNVERIFIED** — neutral. No colour, no sting, no count. Phone in the locker, dead battery, gym bans cameras. *Unverified ≠ false. Being falsely accused by your own tool is a one-shot trust-death.*
- **CONTRADICTED** — the only state with an edge. Two independent signals disagree (step count says he was at his desk; the alarm delta says the photo predates the demand by 90 s).

**Confession is not a fourth state — it is a withdrawal.** FOR THE RECORD (free, unlimited, always visible, warm, **never demotes**) sets `claim.withdrawn_by_user = true`. A withdrawn claim leaves VERIFIED/CLAIMED entirely; it never reaches CONTRADICTED, never reaches the Ledger, never demotes the habit. Only being **CAUGHT** demotes. This is the whole incentive gradient: at 15% sampled audits P(caught) is small, so if confession cost anything, faking would dominate. It costs nothing. The button must be cheaper than lying, at every hour, forever. The Tape renders withdrawals as one warm line, no count, no chart: *"You told me about Wednesday before I asked. That's not in the Ledger. That was never going in the Ledger."*

### 9.5 THE ROAST — and how it is a roast and a dashboard at once

Three lines maximum. **Each line cites a real stat and is tappable — it expands into the dead-serious chart behind it.** Monospace axes, no character anywhere, no jokes on axis labels. **The joke IS the chart's headline.** That is the whole trick: the segment is simultaneously the funniest thing in the week and the only weekly analytics view, and neither is a wrapper for the other. When the comedy stops landing at month 7, the taps still resolve to charts he'd have built himself.

**How it stays funny: callbacks over the archive, not new jokes.** The composer's `CallbackFinder` runs over `proof` and the surviving 28-day `ledger_entry` window for pairs — same object, same hour, same excuse, same failure at the same clock position. Callbacks are generated, not authored, so they cost nothing and never run out. They are also the reason zero-assertion is load-bearing: you cannot call back to month 6 if you burned an accusation in month 6.

Grammar gates enforced at compose time and asserted in `TapeGrammarTest`: no `target` in `{body, weight, appearance, worth}` (not enumerable values in the enum — the column cannot hold them); `DISAPPOINTED` only when `trigger == CAUGHT_FAKE`; `"brother"` unemittable in DISAPPOINTED/GHOST; no `skeleton_id` reused within 120 days across Tapes.

### 9.6 The one genuinely compounding number

**TEST WEEK.** Every 8 weeks from week 12: dead hang, max push-ups, 2 km walk-run, RFESS reps, resting HR. `test_week_result(id, week_id, metric, value, ts)`. Six dated, self-generated *holy shit I did that* moments across ten months, requiring no writer, improving roughly monotonically, and immune to the gaslighting a scale does. It is the app's outcome variable and it is why weight does not need to be one. It renders in TRENDS as a cold line chart with the previous PB as a dashed rule, and it is the only place in the product where **the one sincere congratulation** may be spent — once, ever, on the first strict pull-up or the week-40 test. Devastating precisely because it is the only one.

### 9.7 RIP'S DESK — he narrates his own decline

Standing segment, every week, gated on `jurisdiction = count(ENFORCED) + count(AUDITED)`. `jurisdiction_snapshot(week_id, value, delta)`.

The desk is drawn as rows. Each habit he still owns is a row. Each graduation deletes a row, permanently, and he cannot add one back — the contract graduates on measured evidence and he has **no vote**. He reads the desk out loud. That is the arc: no acts, no eras, no authored beats, no scheduled reinvention. One integer, and he watches it fall.

> **[PITCHMAN]** "Four things on my desk in January. Three now. Reading went TRUSTED in April and I have not been allowed to look at a book since. I don't know what you're reading. I *hope* it's bad."

> **[GHOST, jurisdiction 1]** "One row. It's a big desk for one row."

### 9.8 THE MEDIOCRE WEEK'S TAPE — written first, on purpose

Weeks 20–30 are *structurally* mediocre: no new cheats, no new correlations, no unseen lines, nothing graduates, nothing collapses. Three flat Tapes in a row and the entire bank-it/never-accuse/defer architecture dies, because the payoff stops arriving. **If the boring week isn't worth opening, nothing else in this spec matters.** So: week 23. Jurisdiction 3 (training ENFORCED; water AUDITED; coffee cutoff AUDITED; reading TRUSTED). Nothing moved. Nobody cheated. He was fine.

> **[COLD OPEN]**
> **ELEVEN.**
> That's how many times you opened the camera when I didn't ask you to.
>
> ---
> **THE MONTAGE** — *[29 photos. 1.2 s. Silence.]*
>
> ---
> **THE LEDGER** — `ROLLING 28 DAYS` — `RIP 9 : ARSEN 12`
> ```
> CAUGHT FAKE        0
> EVASION            1   Thu 19:41  home ×4
> FORCE STOP         0
> OEM KILL           1   Sat 09:12–15:04  power save, 352 min
> CLOCK JUMP         0
> DEMOTION           0
> CHEAT              — nothing new. Same glass. Still 3/10.
> ```
>
> ---
> **VERIFIED vs CLAIMED**
> `VERIFIED 24 · UNVERIFIED 5 · CONTRADICTED 0`
> *You told me about Tuesday before I asked. That's not in the Ledger. That was never going in the Ledger.*
>
> ---
> **THE ROAST** *(tap any line)*
> **[PITCHMAN]** "You have opened the Arsenal fourteen times and trained six." → *bar chart, opens vs sessions, 12 weeks*
> **[BIT]** "Nine coffees after 16:40. NINE. Brother, you are training for the WORLD CHAMPIONSHIP OF NOT SLEEPING, and I want you to know — I've seen the field. You're winning." → *cutoff-violation histogram by hour*
> **[DISAPPOINTED-adjacent, flat, no caps]** "Thursday you pressed home four times in eleven seconds. I'm still here. I'm forty megabytes. I don't get tired. I get patient." → *evasion timeline*
>
> ---
> **TRENDS** — *[no voice. Consistency 79%. RHR 58, flat. Dead hang 44 s, dashed rule at 41 s from week 15. Weight: a grey EWMA line, no number, no arrow.]*
>
> ---
> **WHAT I LEARNED ABOUT YOU**
> "Nothing. Fourteen of the last sixteen weeks I've had something. This week: nothing. You were boringly consistent. Disgusting."
>
> ---
> **RIP'S DESK**
> "Three rows. Same three as last Sunday. Water's twelve days from AUDITED going TRUSTED and then it's two, and I want to be very clear that I am rooting against you, professionally, with my whole chest."
>
> ---
> **THE ONE THING**
> "The guitar. Four times. You didn't tell me, you didn't want credit, you just did it and went to bed. It's the only clean thing on the tape. Don't let me catch you being proud of it."
>
> ---
> **THE OFFER**
> "Next week. Fourteen glasses, two sessions. And if you act now — and you *have* to act now, because I'm a recording — I'll throw in, absolutely free: **water graduates two weeks early.** I lose it. Forever. Off my desk, out of my hands, gone.
> *(beat)*
> I've never wanted you to fail so much in my LIFE."
>
> ---
> **SIGN-OFF**
> "Same time next week. I'm not going anywhere. Structurally."

**Why the flat week still opens:** the Montage is 29 photographs of his actual life and it does not need a good week. The Ledger's `0`s are a scoreboard he won. The Roast's third line is a callback, not a joke. The Desk moved 12 days closer to firing him. And the app never accused him of anything on Thursday, so Sunday is the first time he finds out whether Thursday landed.

### 9.9 The great week and the collapse week

**Great week (a graduation).** The composer re-orders: RIP'S DESK is promoted to segment 2, before the Montage, because the amputation is the event. THE LEDGER prints empty and gets *four seconds of nothing on screen*. ARENA fires (one of its two weekly charges) and then swerves to GHOST mid-ceremony. THE OFFER is replaced by THE RECKONING: he reads the remaining rows and counts them out loud. Length: 90 s, at the cap. This is the only Tape allowed to be sentimental, and it is sentimental about his own unemployment.

**Collapse week (≥3 habits missed ≥5 days).** `COACH DOWN` suppresses the Tape's aggression *entirely*, at compose time, not at render: no ROAST, no OFFER, no LEDGER, no cold-open number. What ships is the Montage (whatever exists — one photo is fine), TRENDS, and one card. No debt, no queued grievances, no catch-up, no reckoning. *"I did some thinking while you were out. Don't worry about it."* If the collapse signature matches `depressiveSignature()`, the Tape does not notify at all; it waits to be opened. **`assertLadderNeverEscalatesOn(multiHabitCollapse())`** and **`assertNoRoastOn(coachDown())`** are failing CI tests, not policy.

### 9.10 Open-worthiness — the kill metric

README, sober: *Tape open-rate <50% over any 4-week window after week 8 → the project is ARCHIVED, not patched.* The Tape must therefore earn the open on its worst week, and the four things that do it are all structural, not written: **the Montage is his own life and never depletes**; **nothing was accused live, so Sunday is the only place he finds out**; **the Roast resolves to charts he'd have built himself**; and **the Desk is a countdown to firing the coach that only he can advance**. If open-rate falls below 50%, the honest reading is that none of those four were true, and no volume of new lines fixes an untrue one.

---

## 10. BUILD ORDER, CI/CD, REPO & V1 SCOPE

### 10.1 The v1 line

**v1 is not a skeleton and it is not the lock.** On the day it ships, SECOND SPINE is: a real strength program that counts eight movements with the camera and produces one compounding number (Test Week); an encrypted photographic record of your own life that you own, that leaves the phone every week whether the app likes it or not; a clinical screening gate that actually turns features off; a sick day; a comeback screen; and a loud, broke, blind 1994 infomercial ghost who is genuinely funny about your Tuesday, has no opinion about your body, is contractually obliged to fire himself, and has `RETIRE RIP` sitting in the menu on day one.

That is a finished product. The lock is #11 of 11 because **it's the trailer, not the film.**

#### v1 — SHIPS
- `:coach` pure-JVM + `:enforcement` pure-JVM + the 300-day soak test + `:coach-cli` line dumper.
- THE INTAKE: PAR-Q+ (7), SCOFF+MDDI (gates the entire scale/measurement set), PHQ-2, HSI, 18+, *"WHAT ARE YOU HERE TO BEAT?"*, k-NN calibration, consent line 15 words **before** the cold-open camera, THE CONTRACT.
- Habits: WATER (T1) · EXERCISE (T5, only lock-eligible) · SLEEP ANTECEDENTS · COFFEE TIMING (anticipatory, CYP1A2-aware) · READING (audited, unpenalised) · GUITAR (confession + souvenir) · SMOKING (T0, cue log, **zero penalties, ever**).
- The pipeline, max-2-ENFORCED, graduation on measured evidence, demotion, **the jurisdiction odometer** driving register mix / speech budget / IA split / language ladder / the ending.
- Zero-assertion proof, ~15% suspicion-weighted audits, **one-tap k-NN re-enrolment on the audit screen**, `IT'S REAL`, **FOR THE RECORD** (free, unlimited, always visible, never demotes).
- **Reading proof with `tesseract4android` + `bul.traineddata` in the APK.** Not v1.1. He is Bulgarian; step 1 is reading; a Latin-only v1 cannot validate the thesis on his actual books. ~15 MB in an APK with no size cap.
- Alarm stack (`setAlarmClock` + **4–6 decoys per window**, losers cancelled at fire time), rungs 0–3, all interlocks, AUTO-VOID at Δ>90 s, OEM canary, COACH DOWN, STAND DOWN, the comeback screen, BREAK GLASS.
- THE ARCHIVE (app-private SQLCipher) + **weekly SAF export with visible proof-of-last-run** + one-tap export + nuke + per-proof delete.
- THE TAPE (mediocre week written first), THE LEDGER with the 28-day hard purge and the cheat leaderboard as **one row**.
- Line engine: grammar, skeleton blocking, retirement, scarcity, register inversion, runtime-loadable `lines.json`, **650 fragments**, ~180 pre-rendered hero clips with the IVR sound-font digit bank.
- The kill criterion in the README. The graceful goodbye.

#### The fragment arithmetic (build-blocking; C3's 250 is cut)
Speech budget is a function of jurisdiction: j=4→12/day, 3→9, 2→6, 1→3, 0→Tape only. Over a plausible 300-day trajectory (60d at each rung): 720+540+360+180+60 ≈ **1,860 plays**. Retire-at-3-plays ⇒ **620 fragments minimum**. Ship **650**. C3's 250 covers 750 plays and exhausts on **day ~90** — that is precisely the week-3 death it claims to prevent, arriving in month 3. `assertFragmentBankCovers(300)` fails the build below 620. ~300 are Arsen's; the rest come from a **build-time** authoring job on the 3090 and a community pack — a tool, never a runtime dependency, never a network call.

#### Explicitly LATER
- v1.1: YAMNet hobby archive · object clusters + `NAME THIS` · community line-pack format + PR pipeline · the logistic scheduler at P≈0.5 (month 3+, needs 6 weeks of his data — *a model that lies about knowing him is worse than no model*) · correlation miner with full BH discipline.
- v1.2: sherpa-onnx neural TTS in his own voice. Eras as data-driven flags over content already in the APK.
- v2, gated: the single named human witness (aggregate % only, two-step consent, literal preview, hard schema blocklist, default OFF, **never his homelab community**).
- **Never:** Play Store · Device Admin · uninstall prevention · root/tamper/Play Integrity/VCAM · accessibility app-blocking · cloud · account · a public leaderboard · food classification · weight in the coach's mouth.

---

### 10.2 Build order — step 1 validates the thesis by day 14

| # | Ship | Gate |
|---|---|---|
| 0 | `:coach` + `:enforcement` pure JVM, soak test, `:coach-cli` | Days. The only thing CI can prove. |
| **1** | **READING PROOF, ALONE.** CameraX → OCR (Latin + Cyrillic) → page delta bound to elapsed time → shingled Jaccard >0.8 scoped to `book_id` → archive → export. No ladder, no audits, no voice, no lock. | **In his hands by day 14. If he is still photographing pages in week 7, the thesis is proven. If not, he learned it in six weeks instead of five months.** |
| 2 | Archive + SQLCipher + **weekly SAF export** + biometric gate + nuke | **v1 ship blocker.** Expensive in month 4, free in week 1. |
| 3 | THE INTAKE + screening lockouts + THE CONTRACT | Ship blocker. |
| 4 | STAND DOWN + Drop Detector + comeback screen + break glass + the always-live rate-of-change tripwire | **Ship blocker. Before the first penalty exists.** |
| 5 | THE ARSENAL + REP MODE + video-replay pose harness (**build before any pose code**) + autoregulator + Floor + Test Week | The only compounding number. |
| 6 | Pipeline + odometer + graduation + demotion + THE BET | The ten-month engine. |
| 7 | THE TAPE + THE LEDGER (28-day purge) | Retention. Mediocre week first. |
| 8 | Alarm stack + decoys + interlocks + rungs 0–3 + OEM canary. Verify with `force-idle` **on his phone**. | If alarms don't fire, nothing else matters. |
| 9 | Audits + k-NN + re-enrolment tap + pixel-SHA + FOR THE RECORD | The protocol. |
| 10 | Voice bank v1 + retirement + `lines.json` hot-load | |
| 11 | **THE LOCK.** One day. Last. **Holds back 14 days and says so.** | *"I'm being nice. Two weeks. Enjoy it. I'm building a file."* |

`adb` is not the Android SDK — `platform-tools` is a 5 MB zip and it is **mandatory**: `dumpsys deviceidle force-idle`, `am set-inactive`. Nothing in CI tests Doze, overlays, BAL, FSI, or OEM killers. R:/jdk17 + R:/gradle-8.9 + R:/android-sdk now give a local compile loop; use it, and still keep the `:coach` boundary, because `./gradlew :coach:test` is 2 seconds and `assembleRelease` is not.

---

### 10.3 CI/CD — the proven Sonora pattern, with the Sonora bugs fixed

`.github/workflows/release.yml`, `on: push: tags: ['v*']`:

```yaml
- uses: actions/checkout@v4          # fetch-depth: 0 — versionCode needs history
- uses: actions/setup-java@v4        # distribution: temurin, java-version: 17
- uses: android-actions/setup-android@v3
- uses: gradle/actions/setup-gradle@v4      # IMPROVEMENT: Sonora has no Gradle cache
- name: Version from tag                   # IMPROVEMENT: Sonora hand-edits and desyncs
  run: |
    echo "VERSION_NAME=${GITHUB_REF_NAME#v}" >> $GITHUB_ENV
    echo "VERSION_CODE=$(git rev-list --count HEAD)" >> $GITHUB_ENV
- name: Decode keystore
  if: ${{ secrets.KEYSTORE_BASE64 != '' }}
  run: |
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > $RUNNER_TEMP/ks.jks
    cat > keystore.properties <<EOF
    storeFile=$RUNNER_TEMP/ks.jks
    storePassword=${{ secrets.KEYSTORE_PASSWORD }}
    keyAlias=${{ secrets.KEY_ALIAS }}
    keyPassword=${{ secrets.KEY_PASSWORD }}
    EOF
- run: ./gradlew :coach:test :enforcement:test lintGrammar soakTest assembleRelease
- run: rm -f $RUNNER_TEMP/ks.jks keystore.properties
  if: always()
- uses: softprops/action-gh-release@v2      # apk + sha256 + lines.json
```

`app/build.gradle.kts` keeps Sonora's **`hasKeystore` conditional**: `keystore.properties` present → real signing; absent → `signingConfigs.debug`. **A fork must never fail to build.** `versionCode`/`versionName` read from env with local fallbacks.

`.gitattributes`, first commit, non-negotiable:
```
gradlew text eol=lf
*.tflite binary
*.traineddata binary
```
Without the first line CI dies on `^M` with a message that explains nothing. Sonora paid for this lesson once.

**Improvements over Sonora, run in CI as build-failing gates:**
- `:coach:test` + `:enforcement:test` — 250+ JVM tests, injected `Clock`.
- **`lintGrammar`** — banned-lexicon lint; `body|weight|appearance|worth` are **not enumerable values** in the target enum; `weight_delta` absent from the slot enum; a schema lint asserting no `is_healthy`/`calorie`/`macro`/`food_verdict` column exists anywhere; `break_glass` and `evasion` are distinct tables and **no query joins them**.
- **`soakTest`** — 300 synthetic days incl. a flu, a work crunch, a demotion, a collapse; dumps the transcript to `build/soak/transcript.txt`. Assertions: `assertRegisterMix(M1 vs M8)`, `assertArenaPctIsPureFunctionOf(jurisdiction)`, `assertNoSkeletonRepeatWithin(21)`, `assertNoLineRepeatWithin(120)`, `assertNoLineExceeds(3)`, `assertCeremonySeconds(M8, max=90)`, `assertNever { register==DRY && trigger != CAUGHT_OR_CONFESSED }`, `assertNever { contains("brother") && register in {DISAPPOINTED, DRY, GHOST} }`, `assertNoRungAbove(R0, 22:00–08:00)`, `assertLadderNeverEscalatesOn(multiHabitCollapse())`, `assertFragmentBankCovers(300)`, `assertExactlyOneSincereCongratulation()`. **Then read the transcript as prose.** You cannot ship a ten-month claim you have never seen.
- Crisis-line **liveness check in CI** — never hardcoded from memory, config-driven, verified at build time.
- Voice pack: **a separate manual workflow.** Audio is rendered on the 3090. Never regenerate a pack in CI.

**The stable-signing-key rule.** Obtainium updates in place, and Android refuses an update signed by a different key — a rotation bricks self-update for every installed copy and the only fix is uninstall, which destroys the archive. So: **one keystore, generated once, backed up offline in two places, never rotated, `v1.0.0` through the end.** `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` live in repo secrets and nowhere else. Fork APKs are debug-signed and therefore cannot upgrade his install — correct, and stated in the README.

---

### 10.4 The repo

`github.com/SikamikanikoBG/second-spine` · `applicationId "com.secondspine"` · **MIT**. Modules: `:app :coach :enforcement :coach-cli :vision :audio :voice :sensors :data`.

**README, in order:** the RETIRE RIP framing as the announcement → what it is → the honest warning → what's real → privacy → sideload → the kill criterion → build.

> **Rip is trying not to get fired. Fire him.** That's the game. `RETIRE RIP` has been in the menu since day one. Every habit climbs ENFORCED → AUDITED → TRUSTED → RETIRED on measured evidence. He gets no vote and can never take jurisdiction back. The endgame is that he has nothing left to shout about.
>
> **This is not for everyone, and it is probably not for you.** It is a comedy app with a camera that will lock your screen and shout at you. It is not treatment. It does not detect eating disorders, depression, or dependence — it disables things when it sees certain patterns, and the README says exactly what and why. If you are in trouble, this is not the thing.
>
> **It cannot hold your archive hostage.** *"If the app only retains him because leaving destroys the archive, it wasn't a product, it was a lock. Ship the export and find out."* The export is weekly, automatic, to a folder you choose, and the app fails loudly if it hasn't run in 14 days.
>
> **The lock is a mast, not a cage.** HOME always works. Break glass is free, forever, uncounted, and I will never mention it.
>
> **The classifier is a joke-selector.** It never rejects anything. Photograph a wall and the wall is in the archive. You can beat every anti-fake mechanic in here in an afternoon — you compile this. The defence is psychological, not technical.
>
> **No cloud. No account. No network for AI. Nothing leaves the phone.** Every model is bundled and runs on-device in under a second. The only outbound call in the app is an optional GitHub release check you can turn off.
>
> **Sideload only, and it always will be.** Google Play would never allow the lock — it violates Device and Network Abuse, `USE_FULL_SCREEN_INTENT`, `USE_EXACT_ALARM`, and battery-optimisation policy. Signed APK → GitHub Releases → Obtainium. **The app will not be softened to fit a store.**
>
> **If this works, engagement FALLS.** Ten months of behaviour and a ninety-second show on Sunday. If I ever optimise this for taps at 1am, this paragraph is here to stop me.
>
> **Kill criterion: unprompted opens < 1.0/day, or Tape open-rate < 50%, over any 4-week window after week 8 → the project is ARCHIVED, not patched. I wrote this while sober.**

---

### 10.5 CUT LIST

- **THE DONUT KICK** — no always-on camera exists on a battery; a coarse model false-positives on a bagel. Replaced by the grief. *"I have twenty-four-inch arms and NO ARMS, brother."*
- **THE ENTIRE FOOD MODULE** — absent from the schema, not off-by-default. Photo logging + a mocking voice + weekly review *is* the maintaining mechanism of restrictive eating, and the app is structurally blind to restriction. A flag he wrote he can unwrite at 1am; a column that doesn't exist he cannot conjure.
- **"ASSUMED DONUT" / "protein? plant?"** — a model opinion on a plate. One laugh, permanent epistemic damage to the surface that survives month 8.
- **WEIGHT/MEASUREMENTS AS COACH-VISIBLE DATA, SCALE OCR, BEFORE/AFTER** — glycogen swings ±1–2 kg swamp the signal; ML Kit reads 7-segment at 40–60% and drops the decimal; body checking maintains dissatisfaction. EWMA trend, cold, in RECORDS. Skipping a weigh-in is invisible.
- **SMOKING PENALTIES / DETECTION / RELAPSE MOCKERY** — the abstinence violation effect delivered as a punchline. Stigma predicts fewer quit attempts. Replaced by lapse-recovery latency.
- **SLEEP SCORING, STAGING, AND ALL NIGHT ESCALATION** — orthosomnia is iatrogenic; you cannot command sleep onset. Antecedents only, consequences in daylight.
- **THE TIER-5 COFFEE LOCK** — retributive punishment for a swallowed act.
- **GESTURE NONCE ON EVERY PROOF** — 5 min/day of performance art; dies of friction at week 5, which is worse than boredom. → 15% audit.
- **STREAKS / LIFETIME LEDGER AS LIVE SCORE** — a single point of failure and an unpayable deficit moving 0.5%/day at exactly the target horizon. → rolling 28-day + "days since last double-miss".
- **BREAK-GLASS CAPS AND ALL BREAK-GLASS SHAMING** — a safety valve with a price is a trap with a decorative handle.
- **THE 3/WEEK CONFESSION CAP AND DEMOTION-ON-CONFESSION** — makes faking the dominant strategy. The product, inverted, by its own incentive table.
- **DWELL-TIME LAUGH METER** — no API at any level, and it would cull `"Okay."` by construction.
- **MOIRÉ / EXIF / ACCELEROMETER JITTER / AMBIENT LIGHT** — placebos or research projects; they teach him Rip bluffs, which contaminates the accurate mechanics.
- **pHASH AS AN ACCUSATION** — engineered to be robust to small changes; fires the #1 rage-uninstall on honest nights. → SHA-256 of the decoded pixel buffer.
- **HAND-ROLLED GUITAR DSP** — fires on a dishwasher. → YAMNet, confession-tier.
- **GEOFENCING / IN-GYM PHOTOS** — a Settings trip, and photographing non-consenting strangers.
- **GENERIC POSE COUNTING** — 8 whitelisted movements with an enforced angle. Velocity-decay autoregulation: the SNR isn't there.
- **WORKMANAGER ON ANY TIMING PATH** — 15-min floor, inexact by contract.
- **THE 400 ms BOOMERANG AS A CAGE, DEVICE ADMIN, UNINSTALL BLOCKING, ROOT/VCAM/INTEGRITY** — he compiles this. Opponents get beaten, then deleted.
- **GOOGLE PLAY, AND ANY SOFTENING TO FIT IT.**
- **ERAS / DAY-180 REBOOT / THE ROOM / AUTHORED ACT I–V ARC** — a content treadmill with one author who will be shipping homelab-monitor in month 7. The odometer does it for free.
- **THE CHEAT LEADERBOARD AS A SEGMENT** — exploration ends by month 3; it becomes a weekly receipt proving he won. → one Ledger row.
- **THE DISCORD WEBHOOK** — the number is the disclosure, and his community never consented to watch him be degraded weekly.
- **BANDIT ESCALATION ON COLLAPSE** — a sign error that answers a depressive episode with an air horn.
- **PENALTY DEBT THAT ACCRUES; 40 REPS/DAY** — 40 is a compulsion budget. 20, expiring end-of-day.
- **THE 94% TIC AS A UNIVERSAL HABIT** — Chekhov's Annoyance. Restricted to earned vision-uncertainty, so the break lands.
- **1,200–2,000 AUTHORED FRAGMENTS; AND C3's 250** — the first is six months of comedy writing, the second dies on day 90.
- **HOGAN/CARREY VOICE CLONE** — right-of-publicity, two litigious named individuals, public repo. His own voice, flat, on the 3090. **The flat voice is canon** — it's the Rotterdam tape, and rung 3 is scarier because something dead is talking in your kitchen.
- **"SCARIER" AS THE MONTH-8 ENDPOINT** — fear produces avoidance of the source, and the source is the habit. Dry, not scary.
- **SCRIPTED SINCERITY** — sincerity must be real or absent. Exactly one congratulation, spent on Test Week.