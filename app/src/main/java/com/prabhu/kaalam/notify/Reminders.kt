package com.prabhu.kaalam.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.prabhu.kaalam.MainActivity
import com.prabhu.kaalam.R
import com.prabhu.kaalam.data.DateMath
import com.prabhu.kaalam.data.Event
import com.prabhu.kaalam.data.EventType
import com.prabhu.kaalam.data.Person
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Texts
import com.prabhu.kaalam.google.DriveSync
import com.prabhu.kaalam.google.GoogleAuth
import com.prabhu.kaalam.security.Prefs
import com.prabhu.kaalam.security.Vault
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Kaalam's own reminders (separate from Google Calendar's). */
object Reminders {
    const val CHANNEL = "reminders"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Birthdays, anniversaries and your own events"
        }
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(
            "kaalam-reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(3, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniqueWork("kaalam-reminders-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts every reminder that is due today and hasn't been shown yet. */
    fun checkAndNotify(ctx: Context) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        if (now.hour < Prefs.reminderHour) return
        val sent = Prefs.sentKeys.toMutableSet()
        val before = sent.toSet()
        for (p in Repo.data.value.people) for (ev in p.events) {
            val next = DateMath.next(ev, today) ?: continue
            for (d in ev.remindDays) {
                if (next.minusDays(d.toLong()) != today) continue
                val key = "${ev.id}|$next|$d"
                if (key in sent) continue
                if (post(ctx, p, ev, next, d)) sent += key
            }
        }
        // Forget keys for dates that are over.
        sent.removeAll { k -> runCatching { LocalDate.parse(k.split("|")[1]).isBefore(today) }.getOrDefault(true) }
        if (sent != before) Prefs.sentKeys = sent
    }

    private fun post(ctx: Context, p: Person, ev: Event, next: LocalDate, daysBefore: Int): Boolean {
        if (!canNotify(ctx)) return false
        val title = Texts.notificationTitle(p, ev, next, daysBefore)
        val text = next.format(Texts.eeeeDMMMMyyyy) +
            if (ev.type != EventType.CUSTOM && daysBefore > 0) " · Don't forget to wish" else ""
        val id = (ev.id + daysBefore).hashCode()

        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0F6E78.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setSubText("Kaalam")

        val wish = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_WISH_PERSON, p.id)
            .putExtra(MainActivity.EXTRA_WISH_EVENT, ev.id)
        builder.addAction(
            0, "Send wish",
            PendingIntent.getActivity(ctx, id + 1, wish, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        return try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Prefs.init(ctx)
        Repo.init(ctx)
        runCatching { Reminders.checkAndNotify(ctx) }
        // Daily Drive copy, even on days the app isn't opened.
        if (Vault.hasPin() && Prefs.autoSave && Prefs.dailyBackup &&
            Prefs.lastDailyBackup != LocalDate.now().toString() && GoogleAuth.isConnected(ctx)
        ) {
            runCatching { DriveSync.uploadNow(ctx, auto = true) }
        }
        return Result.success()
    }
}
