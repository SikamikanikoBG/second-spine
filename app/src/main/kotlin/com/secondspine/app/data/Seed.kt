package com.secondspine.app.data

import com.secondspine.coach.MAX_ENFORCED
import com.secondspine.coach.Pillar
import com.secondspine.coach.Stage
import com.secondspine.coach.Tier

/**
 * THE V1 HABITS, seeded on first run.
 *
 * Two numbers decide the shape of this file, and they come from the brain:
 * `MAX_ENFORCED = 2` and `MAX_AUDITED = 2`. The odometer's range is 0..4. There are seven pillars.
 * So "seed the v1 habits" cannot mean "insert seven ENFORCED rows" — `jurisdiction()` would return
 * 7 on a fresh install, `canEnter()` would already be false for everything, and the app would open
 * with its central invariant violated before the user has done anything at all.
 *
 * The `Stage` enum has no CANDIDATE or INACTIVE value to park them in, and adding one would put a
 * fourth state into the pipeline that `shouldGraduate`/`demotionCause` would have to grow branches
 * for — editing the locked brain to solve a seeding problem. So the pillar set ships
 * **present-but-dormant** on the `habit.enabled` column, and [CoachRepository] passes only enabled
 * rows into `jurisdiction()`. Dormant habits are rows the user can switch on in the wizard; they are
 * not on Rip's desk, and `jurisdiction()`'s doc is explicit that what is not on his desk does not
 * count.
 *
 * The two that start enabled are the thesis:
 *
 *  - **EXERCISE** — the aggression lives here, and RESOLUTIONS §B: "Exercise is the only
 *    lock-eligible habit." It is the only row in this file with `lockEligible = true`, and R4 stays
 *    dormant for 14 days regardless (RESOLUTIONS §E).
 *  - **IDENTITY (reading)** — RESOLUTIONS §E: *"if he is still photographing pages in week 7, the
 *    thesis is proven."* The cheapest proof in the app and the one the whole experiment is scored
 *    on. It would be an odd thing to leave switched off.
 *
 * SMOKING seeds enabled-but-tiered-to-nothing and WEIGHT does not seed as a habit at all — see below.
 */
internal fun v1Habits(now: Long): List<HabitRow> = listOf(

    // ── ENABLED: the two that carry the experiment ──────────────────────────
    HabitRow(
        id = "exercise",
        pillar = Pillar.EXERCISE,
        title = "THE SET",
        stage = Stage.ENFORCED,
        // T3: high penalty class. Cardiorespiratory fitness is among the strongest mortality
        // predictors in the table, and it is the pillar where a penalty is actually honest — you
        // can walk out of the door in the next sixty seconds.
        tier = Tier.T3,
        stageSince = now,
        lockEligible = true,
        enabled = true,
    ),
    HabitRow(
        id = "reading",
        pillar = Pillar.IDENTITY,
        title = "THE PAGE",
        stage = Stage.ENFORCED,
        // T1: an identity pillar is not a stick. Rounding it up would be exactly the "identity
        // pillar rounded UP into synthesised speech" that Health.kt names as not a licence.
        tier = Tier.T1,
        stageSince = now,
        lockEligible = false,
        enabled = true,
    ),

    // ── DORMANT: real rows, off the odometer until he switches them on ──────
    HabitRow(
        id = "winddown",
        pillar = Pillar.SLEEP,
        title = "SCREENS DOWN",
        stage = Stage.ENFORCED,
        tier = Tier.T2,
        stageSince = now,
        lockEligible = false,
        enabled = false,
    ),
    HabitRow(
        id = "coffee_cutoff",
        pillar = Pillar.COFFEE,
        title = "THE CUTOFF",
        stage = Stage.ENFORCED,
        tier = Tier.T1,
        stageSince = now,
        lockEligible = false,
        enabled = false,
    ),
    HabitRow(
        id = "water",
        pillar = Pillar.WATER,
        title = "THE GLASS",
        stage = Stage.ENFORCED,
        // T0. RESOLUTIONS §B: water terminates at R2, and "locking a senior engineer's phone over a
        // glass of water is the fastest uninstall available".
        tier = Tier.T0,
        stageSince = now,
        lockEligible = false,
        enabled = false,
    ),

    /**
     * SMOKING. Tier.T0, `lockEligible = false`, and `Pillar.SMOKING.maxRung` is R0_NOTIFICATION.
     *
     * Zero penalties, forever, at every input. He mocks the donut; he NEVER mocks the cigarette.
     * Punishing a nicotine-dependent behaviour is punishing withdrawal is raising negative affect,
     * which is the best-documented relapse pathway there is — and the abstinence violation effect
     * turns one cigarette into a relapse, so a penalty is the app volunteering to supply the
     * violation. This row is a notebook, not a warden: cue awareness IS the intervention.
     *
     * It is a habit row rather than a special case because the guardrail in `:coach`
     * (`PENALTY_FREE_FOREVER`) already refuses it by name, and a special case here would be a second
     * place to get it wrong.
     */
    HabitRow(
        id = "smoking_cue",
        pillar = Pillar.SMOKING,
        title = "THE CUE",
        stage = Stage.ENFORCED,
        tier = Tier.T0,
        stageSince = now,
        lockEligible = false,
        enabled = false,
    ),

    // WEIGHT IS ABSENT FROM THIS LIST, DELIBERATELY.
    //
    // `Pillar.WEIGHT` exists so the guardrail can refuse it by name — "Rip does not know the number
    // exists". A weight *habit* would give it a `day` row, a compliance ratio and a stage, which is
    // the machinery for scoring an outcome nobody can will into being by Thursday. Weight enters
    // through `weight_entry` and leaves as an EWMA trend, and nothing in the pipeline can see it.
)

/**
 * Insert the v1 habits exactly once.
 *
 * `INSERT OR IGNORE` on a stable string primary key, so a second call cannot resurrect a habit the
 * user retired or reset a stage he earned. Seeding is not a migration and must never look like one.
 */
internal suspend fun seedV1Habits(dao: HabitDao) {
    if (dao.count() > 0) return
    dao.insertAll(v1Habits(System.currentTimeMillis()))
}

/**
 * The seed's own invariant, asserted at the point of definition rather than trusted.
 *
 * If someone later flips a fifth `enabled = true` on a whim, `jurisdiction()` starts returning a
 * number the odometer's declared range does not contain, and the first symptom is a register mix
 * that makes no sense eight months from now. Cheaper to fail here.
 */
internal fun assertSeedRespectsCaps(rows: List<HabitRow>) {
    val enforced = rows.count { it.enabled && it.stage == Stage.ENFORCED }
    check(enforced <= MAX_ENFORCED) {
        "Seed enables $enforced ENFORCED habits; MAX_ENFORCED is $MAX_ENFORCED. " +
            "If everything is penalised, nothing is."
    }
    val lockable = rows.filter { it.lockEligible }
    check(lockable.all { it.pillar == Pillar.EXERCISE }) {
        "Only EXERCISE may be lock-eligible (RESOLUTIONS §B). Offenders: " +
            lockable.filterNot { it.pillar == Pillar.EXERCISE }.map { it.id }
    }
}
