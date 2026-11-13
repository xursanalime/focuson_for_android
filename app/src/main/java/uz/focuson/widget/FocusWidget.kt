package uz.focuson.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import uz.focuson.MainActivity
import uz.focuson.R
import uz.focuson.data.Catalog
import uz.focuson.data.Store
import uz.focuson.data.Streaks
import uz.focuson.ui.PlantCache
import uz.focuson.ui.formatShort
import uz.focuson.ui.todayStats
import java.time.LocalDate
import java.time.ZoneId

/** Home-screen widget: today's plants, focused time, streak; or the plant growing right now. */
class FocusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidget::class.java))
                if (ids.isEmpty()) return
                val v = views(context)
                ids.forEach { manager.updateAppWidget(it, v) }
            }
        }

        private fun views(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_focus)
            val zone = ZoneId.systemDefault()
            val history = Store.history.value
            val (count, ms) = todayStats(history)
            val streak = Streaks.current(Streaks.days(history, zone), LocalDate.now(zone))
            val active = Store.active.value
            val speciesId: String
            val growth: Float
            if (active != null) {
                speciesId = active.speciesId
                growth = active.growth(System.currentTimeMillis())
                v.setTextViewText(R.id.widget_title, "${Catalog.byId(speciesId).name} o'smoqda")
                val end = java.time.Instant.ofEpochMilli(active.endAt).atZone(zone).toLocalTime()
                v.setTextViewText(
                    R.id.widget_line1,
                    if (active.stopwatch) "Sekundomer ishlayapti" else "Tugaydi: %02d:%02d".format(end.hour, end.minute),
                )
            } else {
                speciesId = history.lastOrNull { it.success }?.speciesId ?: Store.settings.value.speciesId
                growth = 1f
                v.setTextViewText(R.id.widget_title, "Focus On")
                v.setTextViewText(
                    R.id.widget_line1,
                    if (count == 0) "Bugun hali o'simlik yo'q" else "Bugun: $count ta · ${formatShort(ms)}",
                )
            }
            v.setTextViewText(R.id.widget_line2, "Seriya: $streak kun · ${Store.coins.value} tanga")
            val px = (72 * context.resources.displayMetrics.density).toInt()
            val img = PlantCache.get(speciesId, growth, false, true, px, Density(context))
            v.setImageViewBitmap(R.id.widget_plant, img.asAndroidBitmap())
            val open = PendingIntent.getActivity(
                context, 5, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
