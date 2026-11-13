package uz.focuson.focus

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.focuson.data.Sound
import uz.focuson.data.Store

/** Keeps the session alive in the background, shows the countdown and plays the ambient sound. */
class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var player: AmbientPlayer? = null

    /** Putting the phone down (screen off) is not leaving: cancel a strict-mode warning. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            FocusController.clearWarning(applicationContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = Store.active.value
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(
            this, Notifications.ONGOING_ID, Notifications.ongoing(this, session), type,
        )
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
            val sound = Store.settings.value.sound
            if (sound != Sound.NONE) player = AmbientPlayer(sound).also { it.start() }
            scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        var lastWarn = -1L
        while (true) {
            FocusController.check(this)
            val s = Store.active.value
            if (s == null) {
                stopSelf()
                return
            }
            if (s.warnDeadline != lastWarn) {
                lastWarn = s.warnDeadline
                if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    runCatching {
                        NotificationManagerCompat.from(this)
                            .notify(Notifications.ONGOING_ID, Notifications.ongoing(this, s))
                    }
                }
            }
            delay(500)
        }
    }

    override fun onDestroy() {
        player?.stop()
        if (started) runCatching { unregisterReceiver(screenOff) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, FocusService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusService::class.java))
        }
    }
}

/** Fires at the planned end time, even in Doze, so the plant finishes on time. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        FocusController.check(context)
    }
}

/** After a reboot: resume a running session, and set the daily reminder again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        FocusController.check(context)
        if (Store.active.value != null) runCatching { FocusService.start(context) }
        Reminder.schedule(context)
    }
}
