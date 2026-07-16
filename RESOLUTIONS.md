# RESOLUTIONS — the authoritative decisions

`SPEC.md` was written as 10 sections in parallel. They agree on doctrine and **disagree on numbers**,
and in this product the numbers *are* the design. Three adversarial checks (grafts / logic / build)
found 30 ship-blocking conflicts. Where SPEC.md and this file disagree, **this file wins.**

---

## A. The three that go to the product, not the prose

### A1. THE CONFESSION FIX, ACTUALLY FIXED (the graft-1 patch was applied one layer too high)

The design panel caught the original spec making faking the dominant strategy, and grafted a fix:
*confession never demotes.* **That fix did not work**, and the consistency check caught it:

`shouldGraduate()` independently requires `compliance >= 0.85`, where
`compliance = daysWithCompletion / scheduledDays`. A confessed day is non-compliant and **stays in
the denominator**. So a confessed day fails your graduation gate — while an *uncaught fake day passes
it*. Faking still dominated, one layer below the patch.

**DECISION:** confessed days leave the compliance ratio **entirely** — excluded from `scheduledDays`,
exactly as an interlock-suspended day is. The data stays true (the day is recorded non-compliant in
`proof`/`confession`); the *promotion gate cannot see it*. Plus the 14-day repair window.

Honesty now strictly dominates deception at every hour, for every user, forever. That is the whole
product, and it survived two attempts to invert it.

### A2. DISAPPOINTED IS A RARE EVENT, NOT A SHARE — and collapse demotes

`DISAPPOINTED`'s only trigger is `CAUGHT_FAKE`. Its only honest cause is `BYTE_REPLAY` — a SHA-256
collision on the decoded pixel buffer, which a real sensor cannot produce. But the capture path is
CameraX in-process, no gallery, no `ACTION_IMAGE_CAPTURE`, no `READ_MEDIA_IMAGES`. **So BYTE_REPLAY
is near-unreachable: it fires perhaps twice in ten months.** A register budgeted at 40% of month-8
speech is therefore structurally dead, and `DEMOTED_CAUGHT` is a near-dead branch.

The tempting fix — `FRAME_REPLAY` on pHash — is **banned**: pHash is *engineered to be robust*, so
honest re-shots of the same enrolled mug on the same static counter collide. The app's one
insinuation would fire on truthful nights. That is the #1 rage-uninstall, bought for nothing.

**DECISIONS:**
- `caught_event.kind = { BYTE_REPLAY }` only. Delete `FRAME_REPLAY`. pHash stays an archive
  clustering key for the Tape montage; **it never speaks, never demotes.**
- DISAPPOINTED has **no scheduled share**. It fires 0–3 times in ten months and that is correct.
  Delete it from every register-mix table and assertion.
- **Collapse demotes.** Restate the formula everywhere as: *"no confession ever demotes; only being
  caught, or collapsing, does."* `stage_transition.reason += { DEMOTED_CAUGHT, DEMOTED_COLLAPSE }`.
  This is also what keeps Rip employed without inventing infinite new habits.

### A3. CEREMONY IS WALL-CLOCK SECONDS, NOT "INITIATED CONVERSATIONS"

The ceremony cap is build-blocking (graft 5) — but it was measured in *initiated conversations*, so a
40-minute ladder ending in a 90-second lock counted as **one**. The gate could not cut its only real
target.

**DECISION:** ceremony = wall-clock seconds of demanded attention from **all** sources — every rung,
the lock's expiry, the proof capture. Keep the two *speech* budgets (that split is correct); make
*ceremony* one unified number.

---

## B. Single sources of truth (the parallel writers each invented their own)

| Thing | Was | **DECISION** |
|---|---|---|
| Speech budget | 5 incompatible curves | §3.1's two-budget split. `volunteered/day = [4→6, 3→5, 2→4, 1→2, 0→0]`. Enforcement speech ungoverned but gated by the ladder. §3.1 is sole owner. |
| Fragment bank | 5 sizes (270 / 220 / ~550 / ~420 / 650) | §3.2 recomputes once against the curve above and is **sole owner**; everyone else cites it. Per-slot retirement, not global. |
| `Target` enum | 2 incompatible, both CI-frozen | §3.7's verbatim: `{ the_habit, the_excuse, the_situation, the_phone, himself, the_tape }`. Matches the guardrail's own wording; carries the frozen-set test. |
| Line retirement | global `retireAt(3)` vs per-slot | **Per-slot** (OPENER 20 / OBSERVATION 15 / ESCALATION 8 / SWERVE 3 / BUTTON 12). Nobody notices the fourth "Okay."; everybody notices the second swerve. `assertNoFragmentExceeds(retireAt(slotRole))`. |
| Ladder scales | R0–R5 vs T0–T5 conflated | **Two scales, no overlap.** TIER (T0–T5) = habit penalty class. RUNG (R0–R4) = ladder position: R0 notification, R1 vibrate, R2 alarm, R3 TTS, R4 lock. |
| Lock eligibility | water shows a lock in 1 section, 3 forbid it | **Exercise is the only lock-eligible habit.** Water terminates at R2. Locking a senior engineer's phone over a glass of water is the fastest uninstall available. |
| Audit rate | 1-in-3 / 1-in-6 vs ~15% | **~15% suspicion-weighted, ≤2/day**, all non-TRUSTED stages. Once confession is free the audit rate stops carrying the incentive and becomes a comedy-material budget. |
| Ledger purge | 3 semantics, 2 defeat the graft | **Unconditional** hard DELETE at 28 days. No carve-out for repeat offences — the point is he *structurally cannot* hold a pattern against you. |
| ARENA cap | 3/week vs 2/week vs "two charges" | **3/week**, never twice a day, never after 20:00. `0.10 × j` is a *ceiling*; the absolute cap always binds first. |
| Sincere congratulation | 2 scripts shipped | **One**: §2.6's GHOST scene on the first strict pull-up, `id=SINCERE_ONE`. §7.11's becomes GRUDGING PRAISE. |
| Package root | 3 different | `com.secondspine.*` |
| Break-glass isolation | invariant blocked a required feature | Narrow it: **no query may name `break_glass` at all.** Collapse `evasion` into `ledger_entry`. |
| Clinical gates vs "pure function" | assertion failed for the user the gate protects | `registerMix(jurisdiction, clinicalGates)`. **The gate outranks the assertion.** |

---

## C. Platform lies to delete

- **MediaPipe `ImageClassifier` cannot emit penultimate embeddings** — the entire k-NN enrolment and
  graft 8 rest on it. Use `ImageEmbedder` (a different task) or cut k-NN.
- **Room cannot hold a `CHECK` constraint or a cross-database foreign key.** Both are declared.
- The driving interlock's speed clause **needs background location**, which the spec cuts on the
  facing page.
- A 15-minute WorkManager heartbeat **invents `OEM_KILL` evidence out of ordinary Doze** and hands it
  to the character's mouth, violating the spec's own >90%-accuracy narration bar.
- ML Kit Text Recognition v2 has **no Cyrillic** (Latin/Chinese/Devanagari/Japanese/Korean only) —
  so tesseract4android + `bul.traineddata` is genuinely required, not a preference. Graft 17 stands.

## D. Missing instruments

- **The kill criterion has no instrument for its own primary metric.** The project pre-commits to die
  on *"unprompted opens < 1.0/day"* and nothing anywhere records an app open. Needs
  `app_open(at, source: NOTIFICATION|ALARM|LAUNCHER)` + a 4-week rolling read. ~30 lines.
- **Never lock over the dialer** — a mandatory guardrail with no implementation. Call detection only
  catches an *active* call, not "he is typing 112". Gate on foreground package.
- **The wind-down silence window is hardcoded 22:00–08:00** instead of keyed to `(winddown_at,
  wake_at)`. If his target bed is 21:30, wind-down starts 20:45 and there are **75 minutes in which
  the ladder can fire an alarm, a TTS line and a lock inside the wind-down window** — on the pillar
  ranked #1. Key the interlock and its assertion on the user's actual times.
- `caught_event` is used by the pipeline and **declared in no schema section**.
- **No AUDITED cap**, so the odometer's declared range `0..4` is fiction and `check(jurisdiction<=4)`
  throws at the moment the user is doing best. Cap concurrent AUDITED at 2.

---

## E. SCOPE — the verdict I am acting on

> **"Is v1 shippable by one person? Not as scoped, and it is not close."** Sixteen screens, five ML
> runtimes, two SQLCipher databases, fourteen WorkManager jobs, 650 authored fragments and ~180
> pre-rendered voice clips — *"a studio project, not a sprint."* Nine to fourteen months of evenings
> **"and it lands past the horizon of its own kill criterion."**
>
> *"Ship the current v1 line and the most likely outcome is month 4 with nothing working and a repo
> whose last commit is April — which §1.8 predicts at length, about somebody else."*

The spec indicts itself, and the judge is right. **SPEC.md is the 10-month plan, not the first
release.**

### v1 ships the thesis, not the sprawl

The build order already contained the honest v1 and didn't know it:

**IN v1** — the falsifiable experiment:
- `:coach` — the pure-JVM brain: odometer, pipeline (with A1's corrected compliance), registers, the
  five-slot line grammar, per-slot retirement, the 28-day purge. Desktop-testable, no emulator.
- The intake: the contract (the commitment device that makes the coercion autonomy-preserving),
  SCOFF gate, PAR-Q+.
- **Proof capture** — CameraX, camera-only, nonce, zero-assertion banking. The signature moment.
- **The archive + SAF export** — a ship blocker, with visible proof-of-last-run.
- **FOR THE RECORD** — free, unlimited, warm.
- **The Tape** — the weekly report. Its open-rate is a kill-criterion metric.
- The ladder **R0–R3** + the full interlock set.
- The safety floor.

**R4 (the lock) ships as code but stays dormant**, because graft 11 already says it holds back 14
days: *"I'm being nice. Two weeks. Enjoy it. I'm building a file."* The design's own patience is the
scope cut — anticipation outperforms aggression, and it buys the runway to get the dangerous code
right.

**NOT in v1:** the 50-movement library with animated assets, pose rep-counting for 8 movements, the
OEM canary's 12-manufacturer table, 650 fragments + 180 voice clips, the correlation miner.

The rule the judge set, and the one that decides this project: **if he is still photographing pages
in week 7, the thesis is proven.** Everything else is downstream of that sentence.
