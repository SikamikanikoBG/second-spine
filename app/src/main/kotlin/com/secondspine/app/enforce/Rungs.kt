package com.secondspine.app.enforce

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.secondspine.coach.Rung
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The notification small icon, in one place, because it is a placeholder and must be easy to kill.
 *
 * SPEC §4.9's icon law is *"custom, 2px stroke, and no emoji, ever"*, and `SsIcons` honours it for
 * every surface the app draws itself. A notification small icon is not one of those surfaces: it is a
 * `res/drawable` resource ID resolved by the system process, and `app/src/main/res/` is not this
 * agent's directory. Inventing `ic_stat_coach.xml` here would be an edit to another agent's tree.
 *
 * So it is a platform drawable, it is honest about being wrong, and replacing it is a one-line change
 * at one call site: add a 24×24 white 2px-stroke vector at `res/drawable/ic_stat_coach.xml` and point
 * this constant at it. Every notification in the ladder reads it.
 */
internal val NOTIFICATION_SMALL_ICON: Int = android.R.drawable.ic_lock_idle_alarm

/**
 * THE FOUR THINGS THE LADDER CAN DO TO A ROOM.
 *
 * R0 notification · R1 vibrate · R2 alarm · R3 the voice. R4 is `LockActivity` and lives elsewhere,
 * because it is the only rung that takes something away rather than adding something.
 *
 * Nothing in this file decides *whether* to fire. By the time any of it is called, `step` has already
 * consulted every interlock, the ceiling has already been applied, and the effect has already been
 * committed to `schedule.db`. These are hands, not judgement.
 */

// ---------------------------------------------------------------------------
// Channels
// ---------------------------------------------------------------------------

internal object Channels {

    /** The ladder itself. HIGH, because a rung that arrives silently is not a rung. */
    const val LADDER = "ladder"

    /**
     * The foreground service's own notification. LOW on purpose.
     *
     * This is the notification that says the coach is *running*, not the one that says he *wants*
     * something. It is also — see [ServiceNotification] — the thing carrying BREAK GLASS as a
     * `PendingIntent`, which is the only reason the service is allowed to be quietly present at all.
     */
    const val SERVICE = "service"

    /** R4's full-screen intent. HIGH, and it is the only channel permitted to use one. */
    const val LOCK = "lock"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(LADDER, "The ladder", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rip wants something."
                // No sound on the channel: R2 owns the noise, and it owns it on the alarm stream so
                // that SPEC §6.7 row 11 stays true. A channel sound here would be a notification
                // making noise at R0 — one rung early, on the wrong stream, past the ringer switch.
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(SERVICE, "Coach running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The ladder is live. Break glass lives here."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(LOCK, "The lock", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rung four."
                setSound(null, null)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// R0 — the notification
// ---------------------------------------------------------------------------

internal object RungNotifications {

    const val LADDER_ID = 4101

    fun show(context: Context, challengeId: String, line: String) {
        Channels.ensure(context)
        val n = NotificationCompat.Builder(context, Channels.LADDER)
            .setSmallIcon(NOTIFICATION_SMALL_ICON)
            .setContentTitle("RIP VANDERGRIFF")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Enforcement.openIntent(context, challengeId))
            // BREAK GLASS on every rung, including this one, including from the shade. SPEC §6.8:
            // "Visible on every rung." A safety valve that is only on some surfaces is a safety valve
            // he has to remember the location of, at the moment he is least able to.
            .addAction(0, "BREAK GLASS", Enforcement.breakGlassIntent(context, challengeId))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(LADDER_ID, n)
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(LADDER_ID)
    }
}

// ---------------------------------------------------------------------------
// R1 — the vibration
// ---------------------------------------------------------------------------

internal object Buzz {

    /**
     * The escalating pattern.
     *
     * It gets longer and harder across its own two seconds rather than being one flat buzz, because
     * R1 has to be distinguishable from every other vibration his phone makes all day. A single
     * 400 ms pulse is a text message. This is not a text message.
     *
     * It does **not** repeat. A looping vibration is a thing you wait out, and a rung you can wait out
     * is a rung that has taught him he can wait out the ladder.
     */
    private val PATTERN = longArrayOf(0, 120, 90, 180, 90, 260, 120, 420, 140, 700)
    private val AMPLITUDES = intArrayOf(0, 90, 0, 130, 0, 170, 0, 215, 0, 255)

    fun fire(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(PATTERN, AMPLITUDES, -1)
            } else {
                VibrationEffect.createWaveform(PATTERN, -1)
            }
            vibrator.vibrate(effect)
        }
    }

    fun stop(context: Context) {
        runCatching { vibrator(context)?.cancel() }
    }

    private fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()
}

// ---------------------------------------------------------------------------
// R2 — the alarm
// ---------------------------------------------------------------------------

/**
 * The alarm tone, on the alarm stream.
 *
 * **On "R2 ignores silent":** it does, and it does so honestly. The alarm stream is not gated by the
 * ringer switch — that is what the stream is for — but the ladder never reaches R2 with the ringer
 * down anyway, because `RINGER_SILENT` caps at R1 (`Interlocks.kt`). Those two facts together are
 * the whole of SPEC §6.7 row 11: *"Respect absolutely. Never route around it via the alarm channel."*
 * The stream is loud; the interlock is what makes using it legitimate. Removing the interlock and
 * keeping the stream is the exact end-run the row forbids.
 */
internal object AlarmTone {

    private var player: MediaPlayer? = null

    @Synchronized
    fun play(context: Context) {
        stop()
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { stop() }
    }

    @Synchronized
    fun stop() {
        runCatching { player?.takeIf { it.isPlaying }?.stop() }
        runCatching { player?.release() }
        player = null
    }
}

// ---------------------------------------------------------------------------
// R3 — THE VOICE
// ---------------------------------------------------------------------------

/**
 * THE FLAT VOICE, AND IT IS NOT AN APOLOGY.
 *
 * The platform `TextToSpeech`, unmodified, uninflected, and shipped exactly as it sounds. The brief
 * is explicit that this is canon and not a compromise: it is the Rotterdam tape, and rung 3 is
 * scarier *because* something dead is talking in your kitchen. A warm, well-acted line read here
 * would be a performance, and a performance is something you can enjoy and therefore something you
 * can get used to. This cannot be enjoyed. That is the feature.
 *
 * It is also the only rung in the ladder that a third party in the room can hear and understand,
 * which is why SPEC §6.2 makes rungs 1–4 individually opt-in: *"Third parties did not sign the
 * contract."* The ladder does not promote into this rung on its own.
 *
 * ### The engineering under it
 *
 * `TextToSpeech` initialises asynchronously and its constructor returns long before `onInit`. At R3
 * we are inside a foreground service that has seconds to live, so a line spoken "when ready" with no
 * queue is a line that is silently dropped about a third of the time. Hence [pending]: the line is
 * held, and spoken on init if init has not landed yet.
 */
internal object FlatVoice {

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var pending: String? = null

    /** Speech rate and pitch: slightly slow, slightly low. Not acted. Not friendly. Not sad. */
    private const val RATE = 0.94f
    private const val PITCH = 0.88f

    @Synchronized
    fun speak(context: Context, line: String, onDone: () -> Unit = {}) {
        pending = line
        val existing = tts
        if (existing != null && ready.get()) {
            enqueue(existing, line, onDone)
            return
        }
        if (existing != null) return // init in flight; `pending` will be picked up by onInit
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                // No engine. The rung is simply gone — and it is NOT escalated to R4 to compensate.
                // A missing TTS engine is our platform problem and turning it into more pressure on
                // him would be the app charging him for its own gap.
                ready.set(false)
                onDone()
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            runCatching {
                engine.language = Locale.getDefault().takeIf {
                    engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
                } ?: Locale.UK
                engine.setSpeechRate(RATE)
                engine.setPitch(PITCH)
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_ALARM, for the same reason and with the same justification as
                        // AlarmTone: R3 is above R2, so RINGER_SILENT has already capped the ladder
                        // at R1 long before this line could be reached with the switch down.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
            ready.set(true)
            pending?.let { enqueue(engine, it, onDone) }
        }
    }

    private fun enqueue(engine: TextToSpeech, line: String, onDone: () -> Unit) {
        runCatching {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = onDone()
                @Deprecated("Required by the abstract class; the 2-arg overload is the live one.")
                override fun onError(utteranceId: String?) = onDone()
                override fun onError(utteranceId: String?, errorCode: Int) = onDone()
            })
            engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }.onFailure { onDone() }
        pending = null
    }

    @Synchronized
    fun stop() {
        runCatching { tts?.stop() }
    }

    /** Let the engine go. Called when the service dies — an orphaned engine leaks a whole process. */
    @Synchronized
    fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready.set(false)
        pending = null
    }

    private const val UTTERANCE_ID = "rip"
}

// ---------------------------------------------------------------------------
// The service's own notification — and the break-glass guarantee
// ---------------------------------------------------------------------------

internal object ServiceNotification {

    const val ID = 4100

    /**
     * The FGS notification, and BREAK GLASS is on it as a `PendingIntent`.
     *
     * SPEC §6.8 requires exactly this and gives the reason: break glass *"must survive the overlay
     * process crashing (it is a `PendingIntent` on the FGS notification, not only a Compose button)"*.
     * The Compose button inside `LockActivity` is the pleasant path. This is the one that still works
     * when the Activity has crashed, when the overlay has failed to attach, and when the man holding
     * the phone is not interested in finding out which.
     *
     * A `PendingIntent` is dispatched by the system, not by us. Our process can be in any state at
     * all — dying, ANRing, mid-crash — and this still fires.
     */
    fun build(context: Context, challengeId: String, rung: Rung): Notification {
        Channels.ensure(context)
        return NotificationCompat.Builder(context, Channels.SERVICE)
            .setSmallIcon(NOTIFICATION_SMALL_ICON)
            .setContentTitle("RIP VANDERGRIFF")
            .setContentText(
                when (rung) {
                    Rung.R0_NOTIFICATION -> "He wants something."
                    Rung.R1_VIBRATE, Rung.R2_ALARM -> "He is not going away."
                    Rung.R3_VOICE -> "He is talking."
                    Rung.R4_LOCK -> "He has the phone."
                },
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(Enforcement.openIntent(context, challengeId))
            .addAction(0, "BREAK GLASS", Enforcement.breakGlassIntent(context, challengeId))
            .build()
    }

    /**
     * A notification for a service that has nothing to say.
     *
     * `startForegroundService` gives us ~5 seconds to call `startForeground` or the process is killed
     * with a `ForegroundServiceDidNotStartInTimeException`. Every path into the service must be able
     * to produce a notification synchronously, including the ones that will immediately discover
     * there is nothing to do.
     */
    fun placeholder(context: Context): Notification {
        Channels.ensure(context)
        return NotificationCompat.Builder(context, Channels.SERVICE)
            .setSmallIcon(NOTIFICATION_SMALL_ICON)
            .setContentTitle("RIP VANDERGRIFF")
            .setContentText("Standing by.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }
}

// ---------------------------------------------------------------------------
// R4's launch path — the full-screen intent
// ---------------------------------------------------------------------------

internal object LockNotification {

    const val ID = 4102

    /**
     * THE SCREEN-OFF PATH, AND ONLY THE SCREEN-OFF PATH.
     *
     * SPEC §6.4, and this is the paragraph that decides whether R4 works in the field:
     *
     *   *"Full-screen intents are the SCREEN-OFF path, not the in-use path. FSI only launches an
     *   Activity when the device is locked or the screen is off; unlocked and in use it demotes to a
     *   heads-up banner. **FSI works when you don't need it.** SAW + a visible scrim is the only
     *   in-use path. Get this backwards and it passes testing (recent-foreground grace) and dies
     *   silently in the field."*
     *
     * So this is used when the screen is off, and [LockScrim] is used when it is not. The choice is
     * made in [EffectInterpreter], once, against `PowerManager.isInteractive`.
     */
    fun post(context: Context, challengeId: String) {
        Channels.ensure(context)
        val full = PendingIntent.getActivity(
            context,
            challengeId.hashCode(),
            Intent(context, com.secondspine.app.LockActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(com.secondspine.app.LockActivity.EXTRA_CHALLENGE_ID, challengeId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Channels.LOCK)
            .setSmallIcon(NOTIFICATION_SMALL_ICON)
            .setContentTitle("RIP VANDERGRIFF")
            .setContentText("One set. Camera's on.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .addAction(0, "BREAK GLASS", Enforcement.breakGlassIntent(context, challengeId))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID, n)
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
