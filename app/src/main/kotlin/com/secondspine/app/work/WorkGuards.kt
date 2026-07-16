package com.secondspine.app.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.secondspine.app.data.Graph
import com.secondspine.coach.DeviceContext
import com.secondspine.coach.inWindDownWindow
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * THE TWO THINGS EVERY BACKGROUND JOB MUST ASK BEFORE IT MAKES A NOISE.
 *
 * SPEC §8.8, and it is the flattest sentence in the spec: *"Zero jobs may emit a notification, alarm,
 * TTS, vibration, or lock between wind-down and wake."* Zero. Not "few", not "only important ones".
 *
 * The window is keyed on **the user's own times**, never on 22:00–08:00. RESOLUTIONS §D found the
 * hardcoded version and did the arithmetic: with a target bed of 21:30 the wind-down starts at 20:45,
 * and a constant 22:00 leaves *"75 minutes in which the ladder can fire an alarm, a TTS line and a
 * lock inside the wind-down window"* — on the pillar ranked #1 for all-cause mortality. The app would
 * be wrecking the most important habit it has in order to enforce the sixth most important one.
 *
 * The check delegates to `:coach`'s [inWindDownWindow] rather than reimplementing the comparison here,
 * because the window wraps past midnight and a second copy of wrap-around arithmetic is a second
 * chance to get it wrong. The brain's version is unit-tested; this file's job is only to fetch the two
 * numbers and the timezone.
 */
internal object WorkGuards {

    /**
     * Is it his night right now?
     *
     * Fails **closed**: if the settings read throws, we say yes. The failure mode of a wrong `true`
     * is a notification that arrives tomorrow instead of tonight. The failure mode of a wrong `false`
     * is the app waking him up. Those are not comparable, so the tie does not go to the feature.
     */
    suspend fun inWindDown(now: Long = System.currentTimeMillis()): Boolean = runCatching {
        val settings = Graph.settings
        val ctx = DeviceContext(
            installAt = settings.installAt.first(),
            winddownAtMinutes = settings.winddownAtMinutes.first(),
            wakeAtMinutes = settings.wakeAtMinutes.first(),
            zone = ZoneId.systemDefault(),
        )
        inWindDownWindow(ctx, now)
    }.getOrDefault(true)

    /**
     * May this job speak at all?
     *
     * Three gates, and none of them is "is the feature enabled":
     *  1. Not during his night.
     *  2. Not if he has turned notifications off — a channel he muted is an answer, and routing
     *     around it is what every app he has already deleted does.
     *  3. POST_NOTIFICATIONS may be denied on 33+; `notify` throws `SecurityException`, and a
     *     background job that dies on a permission the user is entitled to refuse is a crash report
     *     with a moral.
     */
    suspend fun mayNotify(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (inWindDown(now)) return false
        return runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(false)
    }
}
