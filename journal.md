# COACH — build journal

## 2026-07-15 — kickoff

**Idea (Arsen):** Android health coach that is sarcastic, aggressive and *unfakeable*.
Not another logging app — logging can be faked, so the coach demands **photo proof**.
Escalating penalties up to locking the phone. Must stay engaging for **10+ months**.

**Decisions taken at kickoff (asked, not assumed):**

| Fork | Decision | Why |
|---|---|---|
| Where the proof-AI runs | **On-device only** (TFLite/MediaPipe) | Works offline/instantly/free; no tailnet dependency; no privacy surface |
| Penalty ceiling | **Nuclear but escape-hatched** | Overlay lock + proof-to-unlock, but a logged "break glass" so it can never strand him |
| Coach voice | **Hulk Hogan × Jim Carrey** | Camp theatrics + real comedy; comedy has the longest half-life for retention |

**Environment:** no local Android SDK → APKs build in GitHub Actions only (same constraint as Sonora).
`gh` authed as SikamikanikoBG. Repo target: public, announcement-ready.

**Hardest constraint:** 10 months without losing interest. Every design decision is judged against that.

---

## Toolchain — fixing the constraint instead of inheriting it

Mined Sonora (the proven Kotlin/Compose + GH-Actions-only Android repo) for its build pattern. Key
reusable wins:

- `android-actions/setup-android@v3` is the whole "no local SDK" trick — no sdkmanager/licence dance in CI.
- The `hasKeystore` conditional: `keystore.properties` present → real signing; absent → debug signing,
  so **the build never fails for forks/PRs**. Same `assembleRelease` works either way.
- `.gitattributes` with `gradlew text eol=lf` — without it CI dies on `bad interpreter: ^M`.
- **Stable signing key is non-negotiable if the app self-updates** — debug-signed releases can't install
  over each other (signature mismatch). Sonora learned this the hard way in commit `d43aeaf`.

Anti-patterns from Sonora deliberately NOT copied:
- No Gradle cache action → cold dependency resolution every run.
- Versions hand-edited (`versionCode = 25`) and kept in sync with the git tag *by hand*. Will derive from tag.
- No tests at all (54 source files, zero).
- Shipped the AdMob **test** ID for 25 releases.

**The real tax:** no local Android SDK means no local compile — every type error costs a 3–5 min CI
round-trip. Sonora has commits literally named `Batch (unbuilt)` paying it. Installing Temurin JDK 17
(C:) + Android command-line tools (R:\android-sdk, since C: is at 92%) to compile locally. No Android
Studio, just the toolchain.

`winget` stalled (needs elevation) → switched to a **portable** Temurin zip. Final toolchain, 847 MB,
all on R:, nothing on C:, no elevation:
- `R:\jdk17` — Temurin 17.0.19
- `R:\android-sdk` — platforms 34+35, build-tools 35.0.0, licences accepted
- `R:\gradle-8.9`

---

## Design panel — 31 agents, 2.5M tokens

6 specialists (UX / trainer / psychologist / comedy writer / Android architect / red-team) → each
gauntleted by 3 lenses (retention / feasibility / harm) → 3 competing specs from different organising
principles → 3 judges → synthesis.

**2 agents failed:** `gauntlet:trainer/retention` (stream stall) and — importantly — `final-spec` blew
the **64k output-token ceiling**, so the workflow returned `final: null`. All 29 other results were
recovered from `journal.jsonl` into `design/` (~1 MB). Lesson: never ask one agent to emit a whole
spec; section it.

### Verdict

| Judge | Winner |
|---|---|
| Arsen's-own-taste | **Second Spine** (92) |
| Engineering-reality | TAPE (88) — Second Spine 81 |
| Behavioural-science | **Second Spine** (92) |

**WINNER: "Second Spine", coach "Rip Vandergriff".**

**The thesis:** *Rip is trying not to get fired.* Habits climb ENFORCED → AUDITED → TRUSTED → RETIRED.
The contract Arsen signed graduates them on measured evidence; **Rip has no vote** and can never take
jurisdiction back. `jurisdiction = count(ENFORCED)+count(AUDITED)` — one integer driving register mix,
speech budget, IA split and the ending. The character's arc is a pure function of the user's success,
at zero authored-content cost. Attribution lands on Arsen's own prior commitment and his own data —
which is the textbook shape of internalisation, and the actual answer to "10 months".

### The ship-blocking bug the judges caught

Second Spine demotes you for **confessing** and for being **caught** — identical price. At ~15% audit
sampling P(caught) is small, so the expected cost of faking is *lower* than the certain cost of
honesty. **The rational move is to fake.** The founding insight, inverted by its own incentive table.
→ Grafted from TAPE: confession is free, unlimited, warm, never demotes. Only being CAUGHT demotes.

### Mandatory grafts (judge-selected, from the losing specs)

- **THE LEDGER FORGETS** — rolling 28-day hard purge from Rip's *addressable memory*. A permanent
  record of failures is rumination infrastructure. Makes "I'm going to remember" *false* — the VHS
  ghost's tape degrades, so he structurally can't hold it against you.
- **Food absent from the schema**, not off-by-default. "A flag he wrote he can unwrite at 1am; a
  column that doesn't exist he cannot conjure."
- **Archive**: app-private SQLCipher + weekly SAF export (survives uninstall; not in his gallery, not
  in anyone's cloud). App must never hold the archive hostage.
- **Ceremony budget as a build-blocking table** (M1 ~4min → M8 ≤45–90s/day). Seconds are the scarce
  resource, not megabytes. Friction at week 5 kills these apps, not boredom at week 3.
- **DISAPPOINTED fires only on caught/confessed deception, never on a miss** — he's blind, he can't
  know *why* you missed, so being disappointed in a miss is inventing a reason to be cruel.
- **Decoy alarms** — `setAlarmClock` leaks the fire time via `getNextAlarmClock()`, defeating the
  unpredictability that carries all the deterrence.
- **k-NN re-enrolment tap** — objects churn (new mug in month 4) → app starts flagging valid proof →
  **the #1 rage-uninstall event is being called a liar on a night you told the truth.**
- **CYP1A2**: smoking induces it and ~doubles caffeine clearance; on quit day his 4 coffees hit like
  8, he blames the quit and relapses to calm down. So cut the coffee target ~50% for 2–4 weeks and
  say why. Real pharmacology, ~40 lines of code, and the moment he believes a real coach built this.
- Cheat leaderboard → one row in the Ledger (cheat variety is an exploration phase that ends by M3).
- Penalty debt ceiling 20 reps/day (not 40). Lock holds back 14 days: *"I'm building a file."*
- Exactly **one** sincere congratulation in the entire product. Graceful goodbye on uninstall.
- Kill criterion in the README, written sober. Grammar invariants as failing CI tests.
- Ship tesseract Cyrillic in v1 — he's Bulgarian, reading proof is step 1 of validating the thesis.

---

## Toolchain proven end-to-end (before writing a line of the real app)

Scaffolded `settings.gradle.kts` (`:app`, `:coach`, `:coach-cli`), wrapper 8.9, `.gitattributes`,
Sonora's `hasKeystore` conditional signing, and built a throwaway slice to prove the loop:

- `:coach` (pure JVM, no Android deps) — compiles + unit tests green **in seconds, no emulator**.
  This module is why the no-local-SDK problem evaporates: the whole coach brain is desktop-testable.
- `:app` — **`app-debug.apk`, 22 MB, built locally.** Compose + `:coach` linked.

**Gotcha found and fixed locally (would have been a baffling CI failure):** `local.properties` written
as `sdk.dir=R\:\android-sdk` — Java `.properties` treats `\a` as an escape, so the path silently
resolved to `R:android-sdk` and Gradle failed with the useless *"The filename, directory name, or
volume label syntax is incorrect"*. Fix: forward slashes, `sdk.dir=R:/android-sdk`.

Also improved on Sonora: `versionCode`/`versionName` now read from Gradle properties so CI can derive
them from the git tag instead of Sonora's hand-edited literals that desync from the tag.

---

## SHIPPED — v0.1.0

Repo: **github.com/SikamikanikoBG/second-spine** (public). Release v0.1.0, signed APK attached, CI green.

**The lesson of the build:** seven parallel agents each verified `assembleDebug` succeeds. None verified
the app *runs*. It compiled, signed, passed CI, installed — and launched to *"This surface is not
installed in this build."* An emulator found in four seconds what CI could not. Then a cascade of
built-but-never-switched-on seams: the Room DB was never installed; the odometer read a hardcoded
fiction; the app never asked for anything (no DemandResolver, and nothing wrote the *day* row —
bankProof/confess wrote answers, nothing wrote the question); the archive showed "NO TAPE YET" over real
photos; the WorkManager jobs were defined but never scheduled. Each invisible to a green build. The fix
that mattered most was structural: deleting the PendingSurface defaults so an unwired screen is now a
compile error, not a silent grey screen.

Two things caught that would have embarrassed him publicly:
- The APK requested INTERNET + a Google telemetry transport (merged in from ML Kit), contradicting the
  README's "no network" claim. Removed outright; a CI gate now fails the build if any network permission
  returns.
- 155 MB -> 45 MB (R8 + dropping emulator-only ABIs from release, kept in debug so it still runs in an
  emulator).

Verified end-to-end on Android 14, on the PUBLISHED R8-minified release APK (not just a debug build):
fresh install -> cold open -> walk the intake -> home raises a real demand -> tap -> camera -> capture
-> Rip reacts -> proof banks -> archive shows the frame. Zero crashes. 324 brain tests green.

Signing gotchas for the record: PKCS12 (JDK 9+ default) CANNOT hold a separate key password — keytool
silently ignores -keypass, so store password and key password must be identical or CI signing dies with
"Given final block not properly padded". keytool's flag is -alias not -keyalias (Sonora doc was wrong).
Keys at R:\second-spine-upload.jks + R:\second-spine-KEYS.txt, outside the repo.
