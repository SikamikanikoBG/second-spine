package com.secondspine.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.secondspine.app.R

/**
 * THE TWO NOTIFICATIONS BACKGROUND WORK IS ALLOWED TO POST. There is no third.
 *
 * Both are IMPORTANCE_DEFAULT. Neither is an alarm, neither is full-screen, neither vibrates, and
 * neither uses the alarm channel — the ladder is the product's aggression and it belongs to the
 * escalation path, which fires from an exact alarm at a time the user cannot predict. WorkManager's
 * floor is fifteen minutes and it is inexact by contract; SPEC §8.8's title says the rest:
 * **planning only, never the nag.** Mixing the two is how every competitor fails silently — a
 * "reminder" that Doze deferred by forty minutes is not a coach, it is a lottery.
 *
 * So: the Tape is ready, and the export is broken. A weekly report and an apology. Nothing here ever
 * demands a set.
 *
 * On the icons: these use the app's own mark, because `res/` belongs to another agent and inventing a
 * drawable there would corrupt the build. A dedicated 2px-stroke glyph per channel is a real design
 * task and it is noted as one — no emoji ever reaches this file, which is the part that is not
 * negotiable.
 */
internal object WorkNotifications {

    private const val CHANNEL_TAPE = "second_spine.tape"
    private const val CHANNEL_EXPORT = "second_spine.export"

    const val ID_TAPE = 8_001
    const val ID_EXPORT = 8_002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TAPE, "The Tape", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Sunday's report. One a week."
                enableVibration(false)
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            // The export failure is the one thing in this app that is the app's fault, so its channel
            // is separate: he must be able to keep this and mute the Tape, or keep the Tape and mute
            // this, without one decision costing him the other.
            NotificationChannel(CHANNEL_EXPORT, "Export", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Only when your archive has stopped leaving the phone."
                enableVibration(false)
            },
        )
    }

    /**
     * THE TAPE IS READY.
     *
     * Deliberately toneless. The Tape's open-rate is a kill-criterion metric (SPEC §9.10), which
     * makes this notification the single most tempting surface in the app to write a hook for — and
     * the single worst one to write a hook for, because a metric the app is scored on is a metric the
     * app must not be allowed to buy. If the report is only opened when the notification tricks him
     * into opening it, the report is not worth opening and we need to find that out.
     */
    suspend fun postTapeReady(context: Context, weekId: Int) {
        if (!WorkGuards.mayNotify(context)) return
        ensureChannels(context)
        post(
            context,
            ID_TAPE,
            NotificationCompat.Builder(context, CHANNEL_TAPE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("THE TAPE — WEEK $weekId")
                .setContentText("It's ready.")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(launchIntent(context))
                .build(),
        )
    }

    /**
     * THE EXPORT IS BROKEN — fourteen days, and it is the app apologising, not accusing.
     *
     * Ongoing and un-cancellable by swipe, which is the one place this file is aggressive, and it is
     * aggressive about the app's own failure rather than about his. The banner in TODAY is the real
     * surface; this exists so that a man who has not opened the app in nine days still finds out that
     * his archive has been sitting in one box in his pocket for a fortnight.
     */
    suspend fun postExportFailing(context: Context, days: Int) {
        if (!WorkGuards.mayNotify(context)) return
        ensureChannels(context)
        post(
            context,
            ID_EXPORT,
            NotificationCompat.Builder(context, CHANNEL_EXPORT)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("NO EXPORT IN $days DAYS")
                .setContentText("Your archive is not leaving this phone. Pick the folder.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Your archive is not leaving this phone. Everything you have banked is in one " +
                            "box, in your pocket. Pick the folder.",
                    ),
                )
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setContentIntent(launchIntent(context))
                .build(),
        )
    }

    /** It came back. Clear it without comment — a fixed problem is not a teaching moment. */
    fun clearExportFailing(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_EXPORT) }
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        // SecurityException on 33+ when POST_NOTIFICATIONS is denied. A refusal is an answer.
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun launchIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return null
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
