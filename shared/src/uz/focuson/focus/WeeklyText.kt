package uz.focuson.focus

import uz.focuson.data.Store
import uz.focuson.data.WeeklyReport
import uz.focuson.ui.formatShort

private val dayNames = listOf("Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba", "Yakshanba")

/** The report as short lines of Uzbek text. */
fun weeklyLines(r: WeeklyReport): List<String> {
    val out = ArrayList<String>()
    out += "${r.grown} ta o'simlik o'sdi" + if (r.withered > 0) ", ${r.withered} ta qurigan." else "."
    r.changePercent?.let { p ->
        out += when {
            p > 0 -> "O'tgan haftadan $p% ko'p. Zo'r!"
            p < 0 -> "O'tgan haftadan ${-p}% kam. Keyingi hafta qaytaramiz."
            else -> "O'tgan hafta bilan bir xil."
        }
    }
    r.topTagId?.let { out += "Eng ko'p: ${Store.tag(it).name} (${formatShort(r.topTagMs)})" }
    r.bestDay?.let { out += "Eng yaxshi kun: ${dayNames[it.dayOfWeek.value - 1]} (${formatShort(r.bestDayMs)})" }
    out += "Faol kunlar: ${r.activeDays} / 7"
    return out
}
