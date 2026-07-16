<div align="center">

# SECOND SPINE

**A health coach that doesn't believe you.**

*On-device. No account, no cloud, no telemetry. The only network use is checking GitHub for app updates.*

</div>

---

Every habit app counts on you logging honestly. You can fake a log in half a second, and then the app
congratulates you. That's not a coach — it's a witness you've bribed.

**Second Spine asks for photographic proof, and keeps the photos — for you, not from you.**

The coach is **RIP VANDERGRIFF**, a 1994 infomercial pitchman who sold eleven million units of a
plastic spring called *"a second spine."* The company folded in 1997. He's now forty megabytes living
in a VHS tape between your banking app and your photo gallery, he has no arms, and he has no eyes
except your camera. ([the rest of him](CHARACTER.md))

And he is contractually obliged to fire himself, one habit at a time.

## The game is firing him

```
ENFORCED  ->  AUDITED  ->  TRUSTED  ->  RETIRED
```

The contract **you** signed graduates habits on measured evidence. **Rip gets no vote** — he can't
promote you, can't hold you back, and can never take jurisdiction back once it's gone.

```kotlin
val jurisdiction = habits.count { it.stage == ENFORCED || it.stage == AUDITED }
```

That integer drives his register mix, his speech budget, how much of your home screen he owns, and
the ending. **His arc is a pure function of your success.** Month one he's a wall of noise. Month
eight he's a forty-pixel face and one line a day, and the archive of your own year is what you
actually open.

`RETIRE RIP` is in the settings menu from day one. Not an easter egg — the win condition.

## What it does

- **Proof, not logs.** Camera-only capture. No gallery import, no `ACTION_IMAGE_CAPTURE`, no
  `READ_MEDIA_IMAGES` in the manifest. Not discouraged — impossible.
- **Zero assertion.** No proof is ever rejected in real time. A photo of a wall opens the lock. The
  app banks it, audits a sample, and roasts you Sunday. It will never call you a liar on a night you
  told the truth.
- **An escalating ladder** — notification, vibrate, alarm, TTS, full-screen lock. Twenty interlocks:
  never during a call, never above 15 km/h, never over the dialer, never inside your own wind-down
  window.
- **BREAK GLASS.** One tap, instant, never confirmed, never mocked in the moment. Always works. Shame
  arrives Sunday, not while you need your phone.
- **The Tape** — a weekly report that's a roast and a real dashboard at once.
- **The Ledger forgets.** Rolling 28-day hard purge, unconditional, no carve-out for repeat offences.
  *"I'm going to remember"* is false. The tape degrades. He structurally cannot hold it against you.

## Honesty is cheaper than lying. That's the whole product.

Every proof screen has a **FOR THE RECORD** button. Free, unlimited, always visible. It never demotes
anything.

That sounds like a detail. It's the load-bearing wall, and it broke twice during design:

1. The first spec priced confessing and getting caught identically. At 15% audit sampling, the
   expected cost of faking is *lower* than the certain cost of honesty — **the rational move was to
   fake.** The founding idea, inverted by its own incentive table.
2. The fix ("confession never demotes") was applied one layer too high. The graduation gate
   independently required 85% compliance, so a confessed day still failed it — **while an uncaught
   fake day passed.** Same inversion, one floor down.

Confessed days now leave the compliance ratio entirely. The data stays true; the promotion gate can't
see it.

`DominanceTest` fails the build if a faker can ever outperform an honest man. If it goes red, the app
is a liar detector that rewards liars, and nothing else being green matters.

## What it will never do

Not settings. Several are enforced by tests, one by a column that doesn't exist.

- **Never classify food. Never show a calorie.** There is no `is_healthy` column — not a flag that's
  off, an absence. A flag you wrote you can unwrite at 1am; a column that doesn't exist you cannot
  conjure. **The donut is allowed.**
- **Never penalise smoking.** Punishment → shame → negative affect → relapse is a well-documented
  pathway ([Marlatt's abstinence violation effect](https://en.wikipedia.org/wiki/Abstinence_violation_effect)).
  He mocks the donut. He never mocks the cigarette.
- **Never penalise an outcome you can't voluntarily produce in the next sixty seconds.** Sleep
  duration: never. Weight: never. Only the controllable antecedent.
- **Never comment on your body, weight, appearance, or worth.** Not values in the target enum.
  Frozen-set test.
- **Never wake you to punish you for sleeping badly.** Zero alarms between wind-down and wake. Sleep
  penalties are served next day, in daylight.
- **Never lock your phone over a glass of water.** Exercise is the only lock-eligible habit.

Weight is trend-only, never a headline, never red, never a penalty. A validated eating-disorder
screen in onboarding permanently gates the entire scale feature set, with no in-app override.

## It cannot hold your archive hostage

Your photos are yours. The app exports them plus a readable manifest to a folder you choose, weekly,
whether the app likes it or not. It fails loudly if it hasn't exported in fourteen days.

There is one network use, and it is narrow: the app checks the public GitHub Releases feed for a
newer version and, if you tap **Update**, downloads that APK and hands it to Android's installer. No
account, no cloud, no telemetry, no analytics — your photos and your encrypted archive never leave
the device. The only bytes that cross the network are a release check and an update you asked for.

> If the app only retains you because leaving destroys the archive, it wasn't a product. It was a
> lock. Ship the export and find out.

## The kill criterion

> **Unprompted opens < 1.0/day, or Tape open-rate < 50%, over any 4-week window after week 8 → this
> project is archived, not patched.**

The app records app-opens specifically so this number can be computed and the promise kept.

## Install

Sideload from [Releases](../../releases). **This will never be on Google Play** — a full-screen lock
and an overlay aren't Play-policy-compatible, and pretending otherwise would mean gutting the only
part with teeth.

## Build

```bash
./gradlew :coach:test        # the brain. pure JVM, no emulator, ~30s
./gradlew :app:assembleDebug # the app
```

`:coach` is pure JVM with zero Android imports — the odometer, pipeline, voice engine, escalation
state machine, interlocks and health logic all unit-test on a laptop in seconds. That's why a 300-day
simulation runs in milliseconds, and why CI can simulate the ten-month arc instead of hoping.

## Is this for you?

Probably not. It's rude, it locks your phone, it's sideload-only, and it wants you to photograph your
own life to prove you lived it.

And he can be beaten — he says so himself:

> *"There is no version of this where I win. So the question was never whether you can beat me. The
> question is who you're beating."*

---

<div align="center">

**Fire him.** That's the game.

</div>
