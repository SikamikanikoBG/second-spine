package com.secondspine.app.data

import androidx.room.TypeConverter
import com.secondspine.coach.CaughtKind
import com.secondspine.coach.LedgerKind
import com.secondspine.coach.Pillar
import com.secondspine.coach.Stage
import com.secondspine.coach.Tier
import com.secondspine.coach.TransitionReason

/**
 * Brain enums <-> columns.
 *
 * Stored by NAME, never by ordinal. Ordinals are a trap here specifically: `Stage`, `Tier` and
 * `LedgerKind` are all enums whose members are ordered by meaning, and the day someone inserts a
 * stage between ENFORCED and AUDITED, every stored ordinal silently re-points at a different value —
 * a migration that corrupts the pipeline's history without erroring. Names cost bytes this app has.
 *
 * Unknown names throw rather than defaulting. A row whose stage cannot be read is a bug to find in a
 * crash, not a habit quietly reset to ENFORCED under a user who earned TRUSTED.
 */
class Converters {

    @TypeConverter fun stageToString(v: Stage): String = v.name
    @TypeConverter fun stringToStage(v: String): Stage = Stage.valueOf(v)

    @TypeConverter fun tierToString(v: Tier): String = v.name
    @TypeConverter fun stringToTier(v: String): Tier = Tier.valueOf(v)

    @TypeConverter fun reasonToString(v: TransitionReason): String = v.name
    @TypeConverter fun stringToReason(v: String): TransitionReason = TransitionReason.valueOf(v)

    /** [CaughtKind] has exactly one value, BYTE_REPLAY. RESOLUTIONS §A2 deleted the other one. */
    @TypeConverter fun caughtKindToString(v: CaughtKind): String = v.name
    @TypeConverter fun stringToCaughtKind(v: String): CaughtKind = CaughtKind.valueOf(v)

    @TypeConverter fun ledgerKindToString(v: LedgerKind): String = v.name
    @TypeConverter fun stringToLedgerKind(v: String): LedgerKind = LedgerKind.valueOf(v)

    @TypeConverter fun pillarToString(v: Pillar): String = v.name
    @TypeConverter fun stringToPillar(v: String): Pillar = Pillar.valueOf(v)

    // --- app-local enums -----------------------------------------------------

    @TypeConverter fun challengeStateToString(v: ChallengeState): String = v.name
    @TypeConverter fun stringToChallengeState(v: String): ChallengeState = ChallengeState.valueOf(v)

    @TypeConverter fun proofKindToString(v: ProofKind): String = v.name
    @TypeConverter fun stringToProofKind(v: String): ProofKind = ProofKind.valueOf(v)

    @TypeConverter fun confessionKindToString(v: ConfessionKind): String = v.name
    @TypeConverter fun stringToConfessionKind(v: String): ConfessionKind = ConfessionKind.valueOf(v)

    @TypeConverter fun appOpenSourceToString(v: AppOpenSource): String = v.name
    @TypeConverter fun stringToAppOpenSource(v: String): AppOpenSource = AppOpenSource.valueOf(v)
}
