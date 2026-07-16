package com.secondspine.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * `data.db`.
 *
 * ONE database, not two. SPEC §8.1 specifies a second, device-protected `schedule.db` for a
 * `directBootAware` receiver to re-arm alarms before first unlock. That is a real requirement and it
 * is a v1.1 one: RESOLUTIONS §E cut the sprawl to the falsifiable experiment, and re-arming alarms
 * before the first unlock of the day is not on the path to "is he still photographing pages in week
 * 7". `AlarmReconciler` on BOOT_COMPLETED after unlock covers v1 honestly.
 *
 * NOT ENCRYPTED. See the long note in app/build.gradle.kts — SQLCipher is a stated v1.1 deferral,
 * not an oversight, and the README says so rather than the app implying a guarantee it lacks.
 *
 * `exportSchema = true`: the generated JSON is what SPEC §8.3's build-failing CI lint greps for
 * `is_healthy|calorie|macro|food_verdict|goal_weight|bmi`. The absences are only enforced if
 * something checks them.
 */
@Database(
    entities = [
        HabitRow::class,
        DayRow::class,
        StageTransitionRow::class,
        ChallengeRow::class,
        ProofRow::class,
        CaughtEventRow::class,
        ConfessionRow::class,
        BreakGlassRow::class,
        LedgerEntryRow::class,
        AppOpenRow::class,
        WeightEntryRow::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SecondSpineDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun dayDao(): DayDao
    abstract fun stageTransitionDao(): StageTransitionDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun proofDao(): ProofDao
    abstract fun caughtEventDao(): CaughtEventDao
    abstract fun confessionDao(): ConfessionDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun appOpenDao(): AppOpenDao
    abstract fun weightDao(): WeightDao

    /**
     * The isolate's DAO is exposed here because Room requires it, and nothing else in the app holds
     * a reference to [SecondSpineDatabase] except [Graph]. [Graph] hands this out to the break-glass
     * action and to the purge worker, and to nothing else — see BreakGlassDao.kt.
     */
    abstract fun breakGlassDao(): BreakGlassDao

    companion object {
        const val NAME = "data.db"

        fun build(context: Context, scope: CoroutineScope): SecondSpineDatabase {
            lateinit var db: SecondSpineDatabase
            db = Room.databaseBuilder(context.applicationContext, SecondSpineDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration. Ever. The archive is the one asset this product
                // claims compounds; a schema bump that silently wipes it would make the claim a lie.
                // A missing migration must be a loud crash in development, not a quiet deletion in
                // production.
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(connection: SupportSQLiteDatabase) {
                        super.onCreate(connection)
                        // First run: seed the v1 habits. Room forbids touching the database from
                        // inside onCreate (it is mid-transaction), so this hops off.
                        scope.launch(Dispatchers.IO) { seedV1Habits(db.habitDao()) }
                    }
                })
                .build()
            return db
        }
    }
}
