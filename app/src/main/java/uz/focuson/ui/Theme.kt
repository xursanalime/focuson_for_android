package uz.focuson.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** App colours. They follow [dark], which FocusTheme sets from the chosen mode. */
object C {
    var dark by mutableStateOf(false)

    private fun pick(light: Long, darkHex: Long) = Color(if (dark) darkHex else light)

    val bgTop get() = pick(0xFFE3EFE6, 0xFF121C18)
    val bgBottom get() = pick(0xFFF7F2E6, 0xFF1A231F)
    val primary get() = pick(0xFF3C7A5A, 0xFF74C495)
    val onPrimary get() = pick(0xFFFFFFFF, 0xFF0E2219)
    val primaryDark get() = pick(0xFF2B5C43, 0xFFA3DBB8)
    val ink get() = pick(0xFF22332B, 0xFFE4ECE7)
    val muted get() = pick(0xFF6B7C72, 0xFF98AAA0)
    val card get() = pick(0xFFFFFFFF, 0xFF232E29)
    val cardAlt get() = pick(0xFFF6F7F4, 0xFF1E2824)
    val line get() = pick(0xFFDCE6DE, 0xFF35443C)
    val chip get() = if (dark) Color(0xFF2A3731) else Color.White.copy(alpha = 0.8f)
    val selected get() = pick(0xFFEAF4EC, 0xFF2E4739)
    val warnBg get() = pick(0xFFFFF1DC, 0xFF3A3123)
    val dialInner get() = pick(0xFFFFFFFF, 0xFF25312C)
    val accent = Color(0xFFE9A23B)
    val danger get() = pick(0xFFC8553D, 0xFFE8806A)
    val night = Color(0xFF1E3A30)

    val background get() = Brush.verticalGradient(listOf(bgTop, bgBottom))
}

@Composable
fun FocusTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    C.dark = dark
    val scheme = if (dark) {
        darkColorScheme(
            primary = C.primary, onPrimary = C.onPrimary, secondary = C.accent,
            background = C.bgBottom, surface = C.card, onSurface = C.ink, onBackground = C.ink,
            error = C.danger, surfaceVariant = C.cardAlt, outline = C.line,
            surfaceContainerHigh = C.card, surfaceContainer = C.card,
        )
    } else {
        lightColorScheme(
            primary = C.primary, onPrimary = C.onPrimary, secondary = C.accent,
            background = C.bgBottom, surface = C.card, onSurface = C.ink, onBackground = C.ink,
            error = C.danger, surfaceVariant = Color(0xFFEFF4F0), outline = C.line,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** "1 soat 15 daqiqa", "25 daqiqa", "40 soniya". */
fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin == 0L -> "${ms / 1000} soniya"
        h == 0L -> "$m daqiqa"
        m == 0L -> "$h soat"
        else -> "$h soat $m daqiqa"
    }
}

/** Short form for tight places: "1 s 15 daq", "25 daq". */
fun formatShort(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h == 0L) "$m daq" else if (m == 0L) "$h soat" else "$h soat $m daq"
}

/** "24:59" or "1:05:00". */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Compact: "25 daq", "4.5 soat". */
fun formatHours(ms: Long): String {
    val min = ms / 60_000
    if (min < 60) return "$min daq"
    val tenths = (ms / 360_000L)
    return if (tenths % 10 == 0L) "${tenths / 10} soat" else "${tenths / 10}.${tenths % 10} soat"
}
