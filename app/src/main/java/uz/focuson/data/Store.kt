package uz.focuson.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * All app state lives here. Small values go to SharedPreferences,
 * finished sessions to `sessions.json` (written atomically).
 * Initialised once from [uz.focuson.FocusApp], so services can use it too.
 */
object Store {
    private lateinit var prefs: SharedPreferences
    private lateinit var historyFile: File

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _history = MutableStateFlow<List<SessionRecord>>(emptyList())
    val history: StateFlow<List<SessionRecord>> = _history

    private val _tags = MutableStateFlow(defaultTags)
    val tags: StateFlow<List<Tag>> = _tags

    private val _coins = MutableStateFlow(0)
    val coins: StateFlow<Int> = _coins

    private val _owned = MutableStateFlow(Catalog.freeIds)
    val owned: StateFlow<Set<String>> = _owned

    private val _bestStreak = MutableStateFlow(0)
    /** Longest run of days in a row with a grown plant, ever. Opens garden looks; never goes down. */
    val bestStreak: StateFlow<Int> = _bestStreak

    /** Garden looks opened by the session that just ended (shown once in the result dialog). */
    val newThemes = MutableStateFlow<List<GardenTheme>>(emptyList())

    /** The session that just ended, shown once on the home screen. */
    val lastResult = MutableStateFlow<SessionRecord?>(null)

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("focuson", Context.MODE_PRIVATE)
        historyFile = File(context.filesDir, "sessions.json")
        _active.value = prefs.getString("active", null)?.let {
            runCatching { ActiveSession.fromJson(JSONObject(it)) }.getOrNull()
        }
        _settings.value = loadSettings()
        _history.value = runCatching {
            if (historyFile.exists()) SessionRecord.listFromJson(JSONArray(historyFile.readText())) else emptyList()
        }.getOrDefault(emptyList())
        _tags.value = prefs.getString("tags", null)?.let { text ->
            runCatching {
                val arr = JSONArray(text)
                (0 until arr.length()).map { Tag.fromJson(arr.getJSONObject(it)) }
            }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: defaultTags
        _coins.value = prefs.getInt("coins", 0)
        _owned.value = Catalog.freeIds + (prefs.getStringSet("owned", emptySet()) ?: emptySet())
        _bestStreak.value = prefs.getInt("bestStreak", 0)
        _breakUntil.value = prefs.getLong("breakUntil", 0L)
        _limits.value = loadLimits()
        _pending.value = loadPending()
        applyDueLimits()
        updateStreak()
    }

    /** Recomputes the best streak from history; returns garden looks that just opened. */
    private fun updateStreak(): List<GardenTheme> {
        val best = Streaks.best(Streaks.days(_history.value, java.time.ZoneId.systemDefault()))
        val old = _bestStreak.value
        if (best <= old) return emptyList()
        _bestStreak.value = best
        prefs.edit().putInt("bestStreak", best).apply()
        return GardenTheme.entries.filter { it.days in (old + 1)..best }
    }

    fun tag(id: String): Tag = _tags.value.firstOrNull { it.id == id } ?: Tag.removed

    @Synchronized
    fun setActive(session: ActiveSession?) {
        _active.value = session
        prefs.edit().apply {
            if (session == null) remove("active") else putString("active", session.toJson().toString())
        }.commit()
    }

    @Synchronized
    fun addRecord(record: SessionRecord) {
        saveHistory(_history.value + record)
        if (record.coins > 0) addCoins(record.coins)
        val opened = updateStreak()
        if (opened.isNotEmpty()) newThemes.value = opened
    }

    private fun saveHistory(list: List<SessionRecord>) {
        _history.value = list
        val tmp = File(historyFile.parentFile, "sessions.json.tmp")
        tmp.writeText(SessionRecord.listToJson(list).toString())
        tmp.renameTo(historyFile)
    }

    fun lastAutoBackup(): Long = prefs.getLong("lastAutoBackup", 0L)
    fun setLastAutoBackup(t: Long) = prefs.edit().putLong("lastAutoBackup", t).apply()

    /** Do-not-disturb state saved before focus, so it can be put back afterwards. */
    fun savedDnd(): Int = prefs.getInt("savedDnd", -1)
    fun setSavedDnd(v: Int) = prefs.edit().putInt("savedDnd", v).commit()

    /** Break (rest) after a session: ends at this moment, 0 = no break. */
    fun breakUntil(): Long = prefs.getLong("breakUntil", 0L)
    fun setBreakUntil(t: Long) {
        prefs.edit().putLong("breakUntil", t).commit()
        _breakUntil.value = t
    }
    private val _breakUntil = MutableStateFlow(0L)
    val breakEnd: StateFlow<Long> = _breakUntil

    // ---- App limits ----

    private val _limits = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Package name -> daily limit in minutes. */
    val limits: StateFlow<Map<String, Int>> = _limits

    @Synchronized
    fun setLimit(pkg: String, minutes: Int?) {
        val m = _limits.value.toMutableMap()
        if (minutes == null) m.remove(pkg) else m[pkg] = minutes
        _limits.value = m
        prefs.edit().putString("limits", JSONObject(m as Map<*, *>).toString()).apply()
    }

    /** A looser limit (higher or removed) waiting for tomorrow. minutes = null means "remove". */
    data class PendingLimit(val minutes: Int?, val from: java.time.LocalDate)

    private val _pending = MutableStateFlow<Map<String, PendingLimit>>(emptyMap())
    val pendingLimits: StateFlow<Map<String, PendingLimit>> = _pending

    /**
     * Change a limit the hard-to-cheat way: a new or stricter limit works right away;
     * a higher limit or removing it only starts tomorrow. Returns true if applied now.
     */
    @Synchronized
    fun requestLimit(pkg: String, minutes: Int?, today: java.time.LocalDate = java.time.LocalDate.now()): Boolean {
        val cur = _limits.value[pkg]
        val now = LimitChange.appliesNow(cur, minutes)
        if (now) {
            setLimit(pkg, minutes)
            savePending(_pending.value - pkg)
        } else {
            savePending(_pending.value + (pkg to PendingLimit(minutes, today.plusDays(1))))
        }
        return now
    }

    @Synchronized
    fun cancelPending(pkg: String) = savePending(_pending.value - pkg)

    /** Puts waiting changes into effect once their day has come. */
    @Synchronized
    fun applyDueLimits(today: java.time.LocalDate = java.time.LocalDate.now()) {
        val due = _pending.value.filter { !it.value.from.isAfter(today) }
        if (due.isEmpty()) return
        due.forEach { (pkg, p) -> setLimit(pkg, p.minutes) }
        savePending(_pending.value - due.keys)
    }

    private fun savePending(m: Map<String, PendingLimit>) {
        _pending.value = m
        val o = JSONObject()
        m.forEach { (pkg, p) -> o.put(pkg, JSONObject().put("m", p.minutes ?: -1).put("from", p.from.toString())) }
        prefs.edit().putString("pendingLimits", o.toString()).apply()
    }

    private fun loadPending(): Map<String, PendingLimit> = runCatching {
        val o = JSONObject(prefs.getString("pendingLimits", "{}")!!)
        o.keys().asSequence().associateWith { k ->
            val p = o.getJSONObject(k)
            PendingLimit(p.getInt("m").takeIf { it >= 0 }, java.time.LocalDate.parse(p.getString("from")))
        }
    }.getOrDefault(emptyMap())

    private fun loadLimits(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString("limits", "{}")!!)
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    /** Today's passes for [pkg]; they reset at midnight. */
    @Synchronized
    fun grant(pkg: String): Grant {
        val o = grantsToday()
        val g = o.optJSONObject(pkg) ?: return Grant()
        return Grant(g.optInt("count"), g.optLong("until"))
    }

    @Synchronized
    fun setGrant(pkg: String, g: Grant) {
        val o = grantsToday()
        o.put(pkg, JSONObject().put("count", g.count).put("until", g.until))
        prefs.edit().putString("grants", o.toString()).putString("grantsDay", java.time.LocalDate.now().toString()).commit()
    }

    private fun grantsToday(): JSONObject {
        if (prefs.getString("grantsDay", "") != java.time.LocalDate.now().toString()) return JSONObject()
        return runCatching { JSONObject(prefs.getString("grants", "{}")!!) }.getOrDefault(JSONObject())
    }

    /** Coins it costs to clear one withered plant from the garden. */
    const val CLEAR_PRICE = 20

    /**
     * Pays [CLEAR_PRICE] per plant and removes these withered plants from the garden and history.
     * Grown plants are never removed. Returns false if there are not enough coins.
     */
    @Synchronized
    fun clearWithered(records: List<SessionRecord>): Boolean {
        val targets = records.filter { !it.success }.toSet()
        if (targets.isEmpty()) return true
        val cost = targets.size * CLEAR_PRICE
        if (_coins.value < cost) return false
        _coins.value -= cost
        prefs.edit().putInt("coins", _coins.value).apply()
        saveHistory(_history.value.filter { it !in targets })
        return true
    }

    @Synchronized
    fun addCoins(n: Int) {
        _coins.value += n
        prefs.edit().putInt("coins", _coins.value).apply()
    }

    /** Buys a plant. Returns false if there are not enough coins. */
    @Synchronized
    fun buy(species: Species): Boolean {
        if (species.id in _owned.value) return true
        if (_coins.value < species.price) return false
        _coins.value -= species.price
        _owned.value = _owned.value + species.id
        prefs.edit()
            .putInt("coins", _coins.value)
            .putStringSet("owned", _owned.value - Catalog.freeIds)
            .apply()
        return true
    }

    @Synchronized
    fun setTags(list: List<Tag>) {
        _tags.value = list
        prefs.edit().putString("tags", JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
        if (list.none { it.id == _settings.value.tagId }) updateSettings { it.copy(tagId = list.first().id) }
    }

    @Synchronized
    fun updateSettings(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        _settings.value = s
        prefs.edit()
            .putBoolean("blockingEnabled", s.blockingEnabled)
            .putString("mode", s.mode.name)
            .putStringSet("allowed", s.allowed)
            .putStringSet("blocked", s.blocked)
            .putBoolean("strict", s.strict)
            .putBoolean("vibrate", s.vibrate)
            .putInt("minutes", s.minutes)
            .putString("speciesId", s.speciesId)
            .putString("tagId", s.tagId)
            .putBoolean("stopwatch", s.stopwatch)
            .putString("sound", s.sound.name)
            .putBoolean("reminder", s.reminder)
            .putInt("reminderMinute", s.reminderMinute)
            .putString("gardenTheme", s.gardenTheme)
            .putBoolean("dnd", s.dnd)
            .putInt("breakMinutes", s.breakMinutes)
            .putBoolean("autoBreak", s.autoBreak)
            .putInt("dailyGoalMinutes", s.dailyGoalMinutes)
            .putString("themeMode", s.themeMode)
            .putInt("graceCount", s.graceCount)
            .putBoolean("weeklyReport", s.weeklyReport)
            .apply()
    }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            blockingEnabled = prefs.getBoolean("blockingEnabled", d.blockingEnabled),
            mode = runCatching { BlockMode.valueOf(prefs.getString("mode", d.mode.name)!!) }.getOrDefault(d.mode),
            allowed = prefs.getStringSet("allowed", d.allowed)!!.toSet(),
            blocked = prefs.getStringSet("blocked", d.blocked)!!.toSet(),
            strict = prefs.getBoolean("strict", d.strict),
            vibrate = prefs.getBoolean("vibrate", d.vibrate),
            minutes = prefs.getInt("minutes", d.minutes),
            speciesId = prefs.getString("speciesId", d.speciesId)!!,
            tagId = prefs.getString("tagId", d.tagId)!!,
            stopwatch = prefs.getBoolean("stopwatch", d.stopwatch),
            sound = runCatching { Sound.valueOf(prefs.getString("sound", d.sound.name)!!) }.getOrDefault(d.sound),
            reminder = prefs.getBoolean("reminder", d.reminder),
            reminderMinute = prefs.getInt("reminderMinute", d.reminderMinute),
            gardenTheme = prefs.getString("gardenTheme", d.gardenTheme)!!,
            dnd = prefs.getBoolean("dnd", d.dnd),
            breakMinutes = prefs.getInt("breakMinutes", d.breakMinutes),
            autoBreak = prefs.getBoolean("autoBreak", d.autoBreak),
            dailyGoalMinutes = prefs.getInt("dailyGoalMinutes", d.dailyGoalMinutes),
            themeMode = prefs.getString("themeMode", d.themeMode)!!,
            graceCount = prefs.getInt("graceCount", d.graceCount),
            weeklyReport = prefs.getBoolean("weeklyReport", d.weeklyReport),
        )
    }

    // ---- Backup ----

    fun exportJson(): String = JSONObject()
        .put("app", "focuson")
        .put("version", 1)
        .put("history", SessionRecord.listToJson(_history.value))
        .put("tags", JSONArray().apply { _tags.value.forEach { put(it.toJson()) } })
        .put("coins", _coins.value)
        .put("owned", JSONArray((_owned.value - Catalog.freeIds).toList()))
        .toString(2)

    /** Replaces history, tags, coins and owned plants with the backup. Throws on a bad file. */
    @Synchronized
    fun importJson(text: String) {
        val o = JSONObject(text)
        require(o.optString("app") == "focuson") { "Bu Focus On fayli emas" }
        val history = SessionRecord.listFromJson(o.getJSONArray("history"))
        val tagsArr = o.getJSONArray("tags")
        val tags = (0 until tagsArr.length()).map { Tag.fromJson(tagsArr.getJSONObject(it)) }
        val ownedArr = o.optJSONArray("owned") ?: JSONArray()
        val owned = (0 until ownedArr.length()).map { ownedArr.getString(it) }.toSet()
        saveHistory(history)
        if (tags.isNotEmpty()) setTags(tags)
        _coins.value = o.optInt("coins", 0)
        _owned.value = Catalog.freeIds + owned
        prefs.edit().putInt("coins", _coins.value).putStringSet("owned", owned).apply()
        updateStreak()
    }
}

/** Which limit changes take effect immediately (pure rule, testable). */
object LimitChange {
    /** New or stricter limits apply now; raising or removing waits for tomorrow. */
    fun appliesNow(current: Int?, wanted: Int?): Boolean = when {
        current == null -> true
        wanted == null -> false
        else -> wanted <= current
    }
}
