package com.secondspine.app.enforce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.secondspine.coach.REBOOT_GRACE_MS
import com.secondspine.coach.rungAt

/**
 * THE LADDER SURVIVES A REBOOT, AND THE DEFENCE IS ARITHMETIC RATHER THAN FORCE.
 *
 * SPEC §6.6: *"Reboot mid-escalation: `BOOT_COMPLETED` → resume at the rung he **should** be at,
 * computed from `challenge.fire_at`. **Never restart at R0** — reboot would become the cheapest
 * evasion in the app. Logged as `evasion(kind=REBOOT)`. Never blocked (blocking reboot is impossible
 * and monstrous)."*
 *
 * ### The two boots, and why they do different amounts of work
 *
 * `LOCKED_BOOT_COMPLETED` arrives **before the user's first unlock**, in Direct Boot. This receiver
 * is `directBootAware`, so it runs there — and that is a privilege with a very short leash:
 *
 *   *"Direct Boot: a stripped `directBootAware="true"` receiver that touches **only `schedule.db`
 *   (device-protected storage) and `AlarmManager`**. Nothing else in that path. Neither WorkManager
 *   nor Hilt is direct-boot aware and either will throw before first unlock."*
 *
 * So the locked path **re-arms and returns**. It does not run `step`, does not start the service,
 * does not touch Room, does not touch DataStore, and does not touch WorkManager. It reads `next_at`
 * out of a hand-rolled SQLite database on device-protected storage and calls `setAlarmClock`. That is
 * the entire contract, and every line below is written to keep it.
 *
 * The *thinking* — the evasion row, the 6-hour grace, resuming at the right rung — needs a
 * `DeviceContext` full of things that do not exist before unlock (his wind-down time is in DataStore;
 * the Ledger is in `data.db`). That work waits for `BOOT_COMPLETED`, which arrives after the unlock,
 * and is handed to the service like every other event.
 *
 * ### Why re-arming before unlock is worth doing at all
 *
 * Because the alternative is that a phone which reboots at 04:00 and is not unlocked until 07:30 has
 * no ladder for the 06:41 challenge — and the man never learns that the reason his coach was quiet is
 * that his phone restarted. The alarms go back up at 04:00. Whether they are *allowed* to fire is
 * still decided later, at fire time, by `step` and every interlock, with a real context.
 */
internal class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // DIRECT BOOT. Re-arm and get out. Nothing else is safe here.
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> rearm(context)

            Intent.ACTION_BOOT_COMPLETED -> {
                // Belt and braces: on a device with no Direct Boot support, or where the locked
                // broadcast was not delivered, this is the first chance to re-arm. `setAlarmClock` is
                // idempotent per PendingIntent, so doing it twice costs nothing.
                rearm(context)
                // Now the credential-protected world exists, so the ladder may think.
                deliverReboots(context)
            }

            // The clock moved under us. SPEC §6.7 row 16: replan in local wall-clock time. The
            // alarms are absolute epoch instants, so they are still correct — but `Interlocks.kt`
            // keys the wind-down window on the user's zone, and the mirror is what it reads.
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED ->
                runCatching {
                    ScheduleStore.get(context).putBoot(BootKeys.ZONE, java.time.ZoneId.systemDefault().id)
                }

            // A package replace kills every alarm this app owns, silently.
            Intent.ACTION_MY_PACKAGE_REPLACED -> rearm(context)
        }
    }

    /**
     * Put the alarms back. `schedule.db` and `AlarmManager`, and not one other thing.
     *
     * Read what this does **not** do: no `step`, no `DeviceContextReader.read()`, no service start,
     * no ledger write. `next_at` was committed by the write-ahead before the reboot, so re-arming is
     * a pure read of a column and a call to the alarm manager. That is what makes it legal here.
     */
    private fun rearm(context: Context) {
        runCatching {
            val store = ScheduleStore.get(context)
            val scheduler = AlarmScheduler(context)
            val now = System.currentTimeMillis()

            for (live in store.live()) {
                val state = live.state

                // Don't ambush him with yesterday's water. SPEC §6.6's 6-hour grace, applied before
                // an alarm goes back up rather than after it has already gone off — the same rule
                // `step` applies on the Reboot event, checked here so that a stale ladder does not
                // even get a PendingIntent.
                if (now - state.armedAt > REBOOT_GRACE_MS || now >= state.expiresAt) continue

                val nextAt = live.nextAt
                val nextRung = live.nextRung

                if (nextAt != null && nextRung != null && nextAt > now) {
                    // The ordinary case: the next rung is still in the future. Put it back exactly
                    // where it was.
                    scheduler.armRung(state.challengeId, nextRung, nextAt)
                    continue
                }

                // The rung was due while the phone was off. Re-arm it for the rung he SHOULD be at,
                // computed from armedAt — never R0, or a reboot would be the cheapest evasion in the
                // app. `rungAt` is the brain's own function; the arithmetic is not re-derived here.
                //
                // It is armed a beat in the future rather than fired: firing is the service's job,
                // and the service cannot run yet. The alarm will land after unlock, where `step` gets
                // a real context and every interlock gets a real answer.
                val shouldBe = rungAt(now - state.armedAt, state.terminalRung)
                scheduler.armRung(state.challengeId, shouldBe, now + RESUME_DELAY_MS)
            }

            // The decoys go back up too — the SAME decoys, at the same instants, from the rows the
            // write-ahead already committed. A reboot that quietly removed every lie would leave the
            // real alarm alone on the lock screen, which is the one place the protocol cannot afford
            // to be honest. Re-planning instead of restoring would be worse than doing nothing: it
            // would strand the original rows as orphans that nothing can ever cancel.
            for (decoy in store.allLiveDecoys()) {
                if (decoy.fireAt <= now) continue
                runCatching { scheduler.rearmDecoy(decoy) }
            }
        }
    }

    /**
     * Tell the ladder it was rebooted. After unlock only.
     *
     * This is what writes `evasion(kind=REBOOT)` and what applies the grace properly. It is a service
     * start, so it cannot be on the locked path — but by `BOOT_COMPLETED` the user has unlocked and
     * the whole app is available.
     */
    private fun deliverReboots(context: Context) {
        runCatching {
            for (live in ScheduleStore.get(context).live()) {
                ContextCompat.startForegroundService(
                    context,
                    serviceIntentFor(context, live.state.challengeId, ServiceEvent.REBOOT),
                )
            }
        }
    }

    private companion object {
        /**
         * A missed rung is re-armed this far out rather than fired on the spot.
         *
         * Boot is the worst moment on the phone: every app's receivers are running, the disk is
         * saturated, and the system is least willing to grant a foreground service start. Twenty
         * seconds costs nothing on a rung that is already late and puts the fire attempt somewhere
         * the system will actually honour it.
         */
        const val RESUME_DELAY_MS = 20_000L
    }
}
