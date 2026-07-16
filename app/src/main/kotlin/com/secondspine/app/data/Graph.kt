package com.secondspine.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI. No Hilt, on purpose.
 *
 * SPEC §8.13 wires Hilt through every module. Sonora ships without DI and is fine, and this app's
 * dependency graph is genuinely one database, one DataStore and one repository — a graph that fits
 * in an object initialiser does not need a compiler plugin to construct it.
 *
 * There is also a specific reason to refuse it here rather than a general taste for less machinery:
 * SPEC §8.1 records that **Hilt throws on the Direct Boot path** and that the Direct Boot receiver
 * must therefore construct its DAO by hand. When the framework has to be bypassed exactly where
 * wiring failure is unrecoverable, it is not carrying the weight it charges for.
 *
 * Call [install] from `Application.onCreate`. Everything downstream is a `val`.
 */
object Graph {

    /**
     * Process-lifetime scope. Seeding, purges and the app-open write must outlive the ViewModel or
     * Activity that triggered them — an app open that is only recorded if the user stays on screen
     * long enough would bias the one metric the project has pre-committed to die on.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var database: SecondSpineDatabase? = null
    @Volatile private var settingsStore: SettingsStore? = null

    val db: SecondSpineDatabase
        get() = database ?: error("Graph.install() was not called. Do it in Application.onCreate().")

    val settings: SettingsStore
        get() = settingsStore ?: error("Graph.install() was not called. Do it in Application.onCreate().")

    val repository: CoachRepository by lazy { CoachRepository(db, settings, appScope) }

    fun install(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database != null) return
            settingsStore = SettingsStore(context.applicationContext)
            database = SecondSpineDatabase.build(context.applicationContext, appScope)
        }
    }

    /** Test seam: an in-memory database and a temp-file DataStore. */
    fun installForTest(db: SecondSpineDatabase, settings: SettingsStore) {
        synchronized(this) {
            database = db
            settingsStore = settings
        }
    }
}
