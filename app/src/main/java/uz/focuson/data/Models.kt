package uz.focuson.data

import org.json.JSONArray
import org.json.JSONObject

enum class BlockMode { ALLOWLIST, BLOCKLIST }

enum class Sound(val title: String) {
    NONE("Tovushsiz"),
    RAIN("Yomg'ir"),
    WIND("Shamol"),
    OCEAN("Dengiz"),
    WHITE("Oq shovqin"),
}

data class Species(val id: String, val name: String, val price: Int, val about: String)

object Catalog {
    val species = listOf(
        Species("eman", "Eman", 0, "Mustahkam, keng shoxli daraxt."),
        Species("archa", "Archa", 0, "Qishda ham yashil qoladi."),
        Species("olcha", "Olcha gullari", 0, "Bahordagi pushti gullar."),
        Species("kaktus", "Kaktus", 0, "Sabrli va chidamli."),
        Species("lola", "Lola", 0, "Bahor xabarchisi."),
        Species("kungaboqar", "Kungaboqar", 250, "Doim quyoshga qaraydi."),
        Species("qayin", "Qayin", 300, "Oq po'stloqli nozik daraxt."),
        Species("paxta", "Paxta", 350, "Oppoq ko'saklar."),
        Species("zarang", "Kuzgi zarang", 400, "Kuzning olovrang barglari."),
        Species("olma", "Olma daraxti", 450, "Qizil olmalar pishdi."),
        Species("anor", "Anor", 500, "Qip-qizil donador mevalar."),
        Species("orik", "O'rik gullari", 550, "Oq-pushti bahor gullari."),
        Species("qaragay", "Qarag'ay", 600, "Baland, tog' daraxti."),
        Species("majnuntol", "Majnuntol", 700, "Shoxlari suvga egilgan tol."),
        Species("palma", "Palma", 800, "Issiq o'lkalar daraxti."),
        Species("chinor", "Chinor", 1200, "Asrlar osha o'sadigan ulkan daraxt."),
    )

    fun byId(id: String): Species = species.firstOrNull { it.id == id } ?: species.first()
    val freeIds: Set<String> = species.filter { it.price == 0 }.map { it.id }.toSet()
}

data class Tag(val id: String, val name: String, val color: Long) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("color", color)

    companion object {
        fun fromJson(o: JSONObject) = Tag(o.getString("id"), o.getString("name"), o.getLong("color"))
        val removed = Tag("?", "O'chirilgan teg", 0xFFB0B8B3)
    }
}

object TagColors {
    val all = listOf(
        0xFF5B8DEF, 0xFFE5735A, 0xFF3C9D6E, 0xFFE9A23B, 0xFF9A7FD1,
        0xFFE07BAA, 0xFF4BB3C8, 0xFF8A9A3C, 0xFFB5764F, 0xFF6B7C8C,
    )
}

val defaultTags = listOf(
    Tag("oqish", "O'qish", 0xFF5B8DEF),
    Tag("ielts", "IELTS", 0xFFE5735A),
    Tag("ish", "Ish", 0xFF3C9D6E),
    Tag("sport", "Sport", 0xFFE9A23B),
    Tag("boshqa", "Boshqa", 0xFF9A7FD1),
)

/** A focus session that is running right now. */
data class ActiveSession(
    val startAt: Long,
    /** Planned length. For a stopwatch session this is the upper limit. */
    val durationMs: Long,
    val speciesId: String,
    val tagId: String,
    /** Strict mode: when > 0, the plant withers at this moment unless the user comes back. */
    val warnDeadline: Long = 0L,
    val stopwatch: Boolean = false,
) {
    val endAt: Long get() = startAt + durationMs

    /** 0..1: how grown the plant is at [now]. */
    fun growth(now: Long): Float {
        val span = if (stopwatch) STOPWATCH_FULL_MS else durationMs
        return ((now - startAt).toFloat() / span).coerceIn(0f, 1f)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("startAt", startAt)
        .put("durationMs", durationMs)
        .put("speciesId", speciesId)
        .put("tagId", tagId)
        .put("warnDeadline", warnDeadline)
        .put("stopwatch", stopwatch)

    companion object {
        const val STOPWATCH_FULL_MS = 30 * 60_000L
        const val STOPWATCH_MIN_MS = 10 * 60_000L
        const val STOPWATCH_MAX_MS = 180 * 60_000L

        fun fromJson(o: JSONObject) = ActiveSession(
            startAt = o.getLong("startAt"),
            durationMs = o.getLong("durationMs"),
            speciesId = o.getString("speciesId"),
            tagId = o.getString("tagId"),
            warnDeadline = o.optLong("warnDeadline", 0L),
            stopwatch = o.optBoolean("stopwatch", false),
        )
    }
}

/** A finished session, grown or withered. */
data class SessionRecord(
    val startAt: Long,
    val endAt: Long,
    val plannedMs: Long,
    val speciesId: String,
    val tagId: String,
    val success: Boolean,
    val coins: Int = 0,
    val stopwatch: Boolean = false,
) {
    val focusedMs: Long get() = (endAt - startAt).coerceAtLeast(0L)

    fun toJson(): JSONObject = JSONObject()
        .put("startAt", startAt)
        .put("endAt", endAt)
        .put("plannedMs", plannedMs)
        .put("speciesId", speciesId)
        .put("tagId", tagId)
        .put("success", success)
        .put("coins", coins)
        .put("stopwatch", stopwatch)

    companion object {
        fun fromJson(o: JSONObject) = SessionRecord(
            startAt = o.getLong("startAt"),
            endAt = o.getLong("endAt"),
            plannedMs = o.getLong("plannedMs"),
            speciesId = o.getString("speciesId"),
            tagId = o.getString("tagId"),
            success = o.getBoolean("success"),
            coins = o.optInt("coins", 0),
            stopwatch = o.optBoolean("stopwatch", false),
        )

        fun listToJson(list: List<SessionRecord>): JSONArray =
            JSONArray().apply { list.forEach { put(it.toJson()) } }

        fun listFromJson(arr: JSONArray): List<SessionRecord> =
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }
}

data class Settings(
    val blockingEnabled: Boolean = true,
    val mode: BlockMode = BlockMode.ALLOWLIST,
    val allowed: Set<String> = emptySet(),
    val blocked: Set<String> = emptySet(),
    val strict: Boolean = false,
    val vibrate: Boolean = true,
    val minutes: Int = 25,
    val speciesId: String = "eman",
    val tagId: String = "oqish",
    val stopwatch: Boolean = false,
    val sound: Sound = Sound.NONE,
    val reminder: Boolean = false,
    val reminderMinute: Int = 19 * 60,
    /** Chosen garden look (enum name); empty = the newest one opened. */
    val gardenTheme: String = "",
    /** Turn on Do Not Disturb while focusing. */
    val dnd: Boolean = false,
    val breakMinutes: Int = 5,
    /** Start the break by itself when a timer session grows. */
    val autoBreak: Boolean = false,
    val dailyGoalMinutes: Int = 120,
    /** "system", "light" or "dark". */
    val themeMode: String = "system",
    /** App limits: how many 5-minute passes per app per day after the limit is reached. */
    val graceCount: Int = 1,
)

/** Today's 5-minute passes for one app. */
data class Grant(val count: Int = 0, val until: Long = 0L)
