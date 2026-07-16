package com.secondspine.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondspine.coach.LedgerKind
import com.secondspine.coach.LifetimeCounters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Settings and profile. Single values, no rows, so DataStore rather than a table.
 *
 * The interesting part of this file is [winddownAtMinutes] and [wakeAtMinutes], which are not a
 * preference in the ordinary sense — they are a safety input.
 *
 * RESOLUTIONS §D: the wind-down silence window was hardcoded 22:00–08:00. "If his target bed is
 * 21:30, wind-down starts 20:45 and there are **75 minutes in which the ladder can fire an alarm, a
 * TTS line and a lock inside the wind-down window** — on the pillar ranked #1." `DeviceContext`
 * takes these as `winddownAtMinutes`/`wakeAtMinutes` for exactly that reason, and this store is
 * where they come from. They are minutes since local midnight, they are the user's, and no code path
 * may substitute a constant for them.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val WINDDOWN_AT = intPreferencesKey("winddown_at_minutes")
        val WAKE_AT = intPreferencesKey("wake_at_minutes")
        val INSTALL_AT = longPreferencesKey("install_at")
        val WIZARD_COMPLETE = booleanPreferencesKey("wizard_complete")
        val CONTRACT_SIGNED_AT = longPreferencesKey("contract_signed_at")
        val EXPORT_TREE_URI = stringPreferencesKey("export_tree_uri")
        val LAST_EXPORT_AT = longPreferencesKey("last_export_at")
        val LAST_EXPORT_FILE_COUNT = intPreferencesKey("last_export_file_count")
        val SCOFF_POSITIVE = booleanPreferencesKey("scoff_positive")
        val PARQ_POSITIVE = booleanPreferencesKey("parq_positive")
        val SAFETY_SHOWN = booleanPreferencesKey("safety_explained")
        fun lifetime(kind: LedgerKind) = longPreferencesKey("lifetime_${kind.name}")
    }

    private val prefs: Flow<Preferences> get() = context.dataStore.data

    // ── The times the ladder is keyed on ────────────────────────────────────

    /** Default 22:30. A default is a starting point for a conversation in the wizard, not a policy. */
    val winddownAtMinutes: Flow<Int> = prefs.map { it[Keys.WINDDOWN_AT] ?: (22 * 60 + 30) }

    /** Default 07:00. */
    val wakeAtMinutes: Flow<Int> = prefs.map { it[Keys.WAKE_AT] ?: (7 * 60) }

    suspend fun setWinddownAtMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.WINDDOWN_AT] = minutes.coerceIn(0, 24 * 60 - 1) }

    suspend fun setWakeAtMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.WAKE_AT] = minutes.coerceIn(0, 24 * 60 - 1) }

    // ── Install & intake ────────────────────────────────────────────────────

    /** Drives the 72-hour grace and the 14-day lock hold-back. Written once, never updated. */
    val installAt: Flow<Long> = prefs.map { it[Keys.INSTALL_AT] ?: 0L }

    suspend fun markInstalled(now: Long) = context.dataStore.edit { p ->
        if (p[Keys.INSTALL_AT] == null) p[Keys.INSTALL_AT] = now
    }

    val wizardComplete: Flow<Boolean> = prefs.map { it[Keys.WIZARD_COMPLETE] ?: false }
    suspend fun setWizardComplete(v: Boolean) = context.dataStore.edit { it[Keys.WIZARD_COMPLETE] = v }

    /**
     * The contract. It is the commitment device that makes the coercion autonomy-preserving — the
     * whole pipeline's authority traces back to this timestamp and the user's own prior consent.
     */
    val contractSignedAt: Flow<Long> = prefs.map { it[Keys.CONTRACT_SIGNED_AT] ?: 0L }
    suspend fun signContract(now: Long) = context.dataStore.edit { it[Keys.CONTRACT_SIGNED_AT] = now }

    /**
     * The break-glass explanation — the character's ONE break, UI type, flat delivery.
     *
     * Stored as "has been shown", never as "has been dismissed forever" by any other path: that one
     * break is what makes the other 100% trustworthy, so it is not something a coach voice may
     * pre-empt or a settings screen may bury.
     */
    val safetyExplained: Flow<Boolean> = prefs.map { it[Keys.SAFETY_SHOWN] ?: false }
    suspend fun setSafetyExplained(v: Boolean) = context.dataStore.edit { it[Keys.SAFETY_SHOWN] = v }

    // ── Clinical gates ──────────────────────────────────────────────────────

    /**
     * SCOFF-positive permanently removes the mocking registers, with no in-app override — which is
     * why it lives here and not behind a toggle the user can flip at 1am to get the jokes back.
     * RESOLUTIONS §B: the clinical gate OUTRANKS the register-mix assertion.
     */
    val scoffPositive: Flow<Boolean> = prefs.map { it[Keys.SCOFF_POSITIVE] ?: false }
    val parqPositive: Flow<Boolean> = prefs.map { it[Keys.PARQ_POSITIVE] ?: false }

    suspend fun setScoffPositive(v: Boolean) = context.dataStore.edit { it[Keys.SCOFF_POSITIVE] = v }
    suspend fun setParqPositive(v: Boolean) = context.dataStore.edit { it[Keys.PARQ_POSITIVE] = v }

    // ── The export — a v1 ship blocker (SPEC §8.6) ──────────────────────────

    val exportTreeUri: Flow<String?> = prefs.map { it[Keys.EXPORT_TREE_URI] }
    val lastExportAt: Flow<Long> = prefs.map { it[Keys.LAST_EXPORT_AT] ?: 0L }
    val lastExportFileCount: Flow<Int> = prefs.map { it[Keys.LAST_EXPORT_FILE_COUNT] ?: 0 }

    suspend fun setExportTreeUri(uri: String) = context.dataStore.edit { it[Keys.EXPORT_TREE_URI] = uri }

    suspend fun recordExport(at: Long, filesWritten: Int) = context.dataStore.edit {
        it[Keys.LAST_EXPORT_AT] = at
        it[Keys.LAST_EXPORT_FILE_COUNT] = filesWritten
    }

    // ── Lifetime counters — the only thing that outlives the purge ──────────

    /**
     * Dateless integers, accrued at write time, before the purge can ever see the rows.
     *
     * He may say "forty-one." He may not say "that Tuesday in March." Monotonic by design: the purge
     * does not decrement them, because that would make the tally a function of the window, and the
     * whole point is that it isn't. `LifetimeCounters` holds no timestamp and `LedgerTest` asserts
     * by reflection that it never grows one — so this store must not hand it one either.
     */
    val lifetimeCounters: Flow<LifetimeCounters> = prefs.map { p ->
        LifetimeCounters(LedgerKind.entries.associateWith { (p[Keys.lifetime(it)] ?: 0L) }
            .filterValues { it > 0L })
    }

    suspend fun accrueLifetime(kind: LedgerKind) = context.dataStore.edit { p ->
        p[Keys.lifetime(kind)] = (p[Keys.lifetime(kind)] ?: 0L) + 1L
    }

    suspend fun snapshot(): Preferences = prefs.first()
}
