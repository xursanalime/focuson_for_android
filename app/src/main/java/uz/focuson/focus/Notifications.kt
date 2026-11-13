package uz.focuson.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uz.focuson.MainActivity
import uz.focuson.R
import uz.focuson.data.ActiveSession
import uz.focuson.data.Catalog
import uz.focuson.data.SessionRecord
import uz.focuson.ui.formatDuration

object Notifications {
    const val ONGOING_ID = 10
    private const val RESULT_ID = 11
    private const val WARN_ID = 12
    private const val REMIND_ID = 13
    private const val BREAK_ID = 14
    private const val BREAK_OVER_ID = 15
    private const val CH_ONGOING = "focus"
    private const val CH_RESULT = "result"
    private const val CH_WARN = "warn"
    private const val CH_REMIND = "remind"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Fokus jarayoni", NotificationManager.IMPORTANCE_LOW).apply {
                description = "O'simlik o'sayotganda qolgan vaqt"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Fokus natijasi", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "O'simlik o'sib bo'lganda yoki qurib qolganda"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WARN, "Qattiq rejim ogohlantirishi", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Focus On'dan chiqib ketganingizda: qayting, aks holda o'simlik quriydi"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Kunlik eslatma", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Bugun hali o'simlik ekmagan bo'lsangiz eslatadi"
            }
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ongoing(context: Context, s: ActiveSession?): Notification {
        val b = NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notif)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(context))
        if (s == null) return b.setContentTitle("Focus On").build()
        val name = Catalog.byId(s.speciesId).name
        when {
            s.warnDeadline > 0 -> b.setContentTitle("Qayting! $name qurib qolyapti")
                .setContentText("Focus On'ni oching, aks holda o'simlik quriydi")
                .setWhen(s.warnDeadline)
                .setChronometerCountDown(true)
            s.stopwatch -> b.setContentTitle("$name o'smoqda · sekundomer")
                .setContentText("Tugatish uchun Focus On'ni oching.")
                .setWhen(s.startAt)
                .setChronometerCountDown(false)
            else -> b.setContentTitle("$name o'smoqda")
                .setContentText("Telefonni qo'ying. Tugashiga qolgan vaqt.")
                .setWhen(s.endAt)
                .setChronometerCountDown(true)
        }
        return b.setUsesChronometer(true).setShowWhen(true).build()
    }

    fun showWarning(context: Context, s: ActiveSession, deadline: Long) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CH_WARN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Qayting! ${Catalog.byId(s.speciesId).name} qurib qolyapti")
            .setContentText("10 soniya ichida Focus On'ga qayting.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(deadline)
            .setTimeoutAfter(FocusController.STRICT_GRACE_MS + 1_000)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { nm.notify(WARN_ID, n) }
    }

    fun cancelWarning(context: Context) {
        NotificationManagerCompat.from(context).cancel(WARN_ID)
    }

    fun showResult(context: Context, r: SessionRecord) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val name = Catalog.byId(r.speciesId).name
        val (title, text) = if (r.success) {
            "$name o'sdi!" to "${formatDuration(r.focusedMs)} diqqat bilan ishladingiz. +${r.coins} tanga."
        } else {
            "$name qurib qoldi" to "Hechqisi yo'q. Yangi o'simlik eking va yana urinib ko'ring."
        }
        val n = NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { nm.notify(RESULT_ID, n) }
    }

    fun showBreak(context: Context, end: Long) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Tanaffus")
            .setContentText("Dam oling: suv iching, cho'zilib oling.")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(end)
            .setShowWhen(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { nm.notify(BREAK_ID, n) }
    }

    fun cancelBreak(context: Context) = NotificationManagerCompat.from(context).cancel(BREAK_ID)

    fun showBreakOver(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Tanaffus tugadi")
            .setContentText("Yana bitta o'simlik ekamizmi?")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { nm.notify(BREAK_OVER_ID, n) }
    }

    fun showLimitSoon(context: Context, app: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CH_WARN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("$app: 5 daqiqa qoldi")
            .setContentText("Bugungi limit tugayapti. Ishni yakunlab qo'ying.")
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .build()
        runCatching { nm.notify(16, n) }
    }

    fun showWeekly(context: Context, r: uz.focuson.data.WeeklyReport) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val lines = weeklyLines(r)
        val open = PendingIntent.getActivity(
            context, 6,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_WEEK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Hafta yakuni: ${formatDuration(r.focusedMs)} fokus")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(17, n) }
    }

    fun showReminder(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Bugun hali o'simlik ekmadingiz")
            .setContentText("25 daqiqa ajrating. Bog'ingiz sizni kutyapti.")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { nm.notify(REMIND_ID, n) }
    }
}
