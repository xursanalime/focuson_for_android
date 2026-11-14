package uz.focuson.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * All app state on the computer: ~/.local/share/focuson/ (state.json, sessions.json).
 * FOCUSON_DATA_DIR changes the folder (used by tests). Same public API as the phone's Store,
 * so the shared screens work unchanged.
 */
object Store {
    val dir: File = File(
        System.getProperty("focuson.dataDir") ?: System.getenv("FOCUSON_DATA_DIR")
            ?: (System.getProperty("user.home") + "/.local/share/focuson")
    ).apply { mkdirs() }
    private val stateFile = File(dir, "state.json")
    private val historyFile = File(dir, "sessions.json")

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
    val bestStreak: StateFlow<Int> = _bestStreak

    val newThemes = MutableStateFlow<List<GardenTheme>>(emptyList())
    val lastResult = MutableStateFlow<SessionRecord?>(null)

    private val _breakUntil = MutableStateFlow(0L)
    val breakEnd: StateFlow<Long> = _breakUntil

    /** Small values that are not settings (last reminder day, saved notification state…). */
    private var extra = JSONObject()

    const val CLEAR_PRICE = 20

    init {
        load()
    }

    @Synchronized
    private fun load() {
        val o = runCatching { JSONObject(stateFile.readText()) }.getOrNull() ?: JSONObject()
        _settings.value = settingsFromJson(o.optJSONObject("settings") ?: JSONObject())
        _active.value = o.optJSONObject("active")?.let { runCatching { ActiveSession.fromJson(it) }.getOrNull() }
        _coins.value = o.optInt("coins", 0)
        val ownedArr = o.optJSONArray("owned") ?: JSONArray()
        _owned.value = Catalog.freeIds + (0 until ownedArr.length()).map { ownedArr.getString(it) }
        val tagsArr = o.optJSONArray("tags")
        _tags.value = tagsArr?.let { a -> (0 until a.length()).map { Tag.fromJson(a.getJSONObject(it)) } }
            ?.takeIf { it.isNotEmpty() } ?: defaultTags
        _bestStreak.value = o.optInt("bestStreak", 0)
        _breakUntil.value = o.optLong("breakUntil", 0L)
        extra = o.optJSONObject("extra") ?: JSONObject()
        _history.value = runCatching {
            if (historyFile.exists()) SessionRecord.listFromJson(JSONArray(historyFile.readText())) else emptyList()
        }.getOrDefault(emptyList())
        updateStreak()
    }

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Synchronized
    private fun saveState() {
        val o = JSONObject()
            .put("settings", settingsToJson(_settings.value))
            .put("active", _active.value?.toJson() ?: JSONObject.NULL)
            .put("coins", _coins.value)
            .put("owned", JSONArray((_owned.value - Catalog.freeIds).toList()))
            .put("tags", JSONArray().apply { _tags.value.forEach { put(it.toJson()) } })
            .put("bestStreak", _bestStreak.value)
            .put("breakUntil", _breakUntil.value)
            .put("extra", extra)
        atomicWrite(stateFile, o.toString(2))
    }

    private fun saveHistory(list: List<SessionRecord>) {
        _history.value = list
        atomicWrite(historyFile, SessionRecord.listToJson(list).toString())
    }

    fun tag(id: String): Tag = _tags.value.firstOrNull { it.id == id } ?: Tag.removed

    @Synchronized
    fun setActive(session: ActiveSession?) {
        _active.value = session
        saveState()
    }

    @Synchronized
    fun addRecord(record: SessionRecord) {
        saveHistory(_history.value + record)
        if (record.coins > 0) _coins.value += record.coins
        val opened = updateStreak()
        if (opened.isNotEmpty()) newThemes.value = opened
        saveState()
    }

    private fun updateStreak(): List<GardenTheme> {
        val best = Streaks.best(Streaks.days(_history.value, java.time.ZoneId.systemDefault()))
        val old = _bestStreak.value
        if (best <= old) return emptyList()
        _bestStreak.value = best
        return GardenTheme.entries.filter { it.days in (old + 1)..best }
    }

    @Synchronized
    fun buy(species: Species): Boolean {
        if (species.id in _owned.value) return true
        if (_coins.value < species.price) return false
        _coins.value -= species.price
        _owned.value = _owned.value + species.id
        saveState()
        return true
    }

    @Synchronized
    fun clearWithered(records: List<SessionRecord>): Boolean {
        val targets = records.filter { !it.success }.toSet()
        if (targets.isEmpty()) return true
        val cost = targets.size * CLEAR_PRICE
        if (_coins.value < cost) return false
        _coins.value -= cost
        saveHistory(_history.value.filter { it !in targets })
        saveState()
        return true
    }

    @Synchronized
    fun setTags(list: List<Tag>) {
        _tags.value = list
        if (list.none { it.id == _settings.value.tagId }) _settings.value = _settings.value.copy(tagId = list.first().id)
        saveState()
    }

    @Synchronized
    fun updateSettings(change: (Settings) -> Settings) {
        _settings.value = change(_settings.value)
        saveState()
    }

    fun breakUntil(): Long = _breakUntil.value

    @Synchronized
    fun setBreakUntil(t: Long) {
        _breakUntil.value = t
        saveState()
    }

    fun extraString(key: String): String = extra.optString(key, "")
    fun extraLong(key: String): Long = extra.optLong(key, 0L)

    @Synchronized
    fun putExtra(key: String, value: Any) {
        extra.put(key, value)
        saveState()
    }

    // ---- Backup ----

    fun exportJson(): String = Backup.write(_history.value, _tags.value, _coins.value, _owned.value, "ubuntu")

    /**
     * Brings in a backup. merge = true adds the other device's plants, tags and bought plants
     * (coins stay as they are here); merge = false replaces everything. Returns new sessions count.
     */
    @Synchronized
    fun importJson(text: String, merge: Boolean): Int {
        val p = Backup.parse(text)
        val added: Int
        if (merge) {
            added = Backup.countNew(_history.value, p.history)
            saveHistory(Backup.mergeHistory(_history.value, p.history))
            _tags.value = Backup.mergeTags(_tags.value, p.tags)
            _owned.value = _owned.value + p.owned
        } else {
            added = p.history.size
            saveHistory(p.history)
            if (p.tags.isNotEmpty()) _tags.value = p.tags
            _coins.value = p.coins
            _owned.value = Catalog.freeIds + p.owned
        }
        updateStreak()
        saveState()
        return added
    }

    // ---- Settings as JSON (only what the computer uses) ----

    private fun settingsToJson(s: Settings) = JSONObject()
        .put("strict", s.strict)
        .put("minutes", s.minutes)
        .put("speciesId", s.speciesId)
        .put("tagId", s.tagId)
        .put("stopwatch", s.stopwatch)
        .put("sound", s.sound.name)
        .put("reminder", s.reminder)
        .put("reminderMinute", s.reminderMinute)
        .put("gardenTheme", s.gardenTheme)
        .put("dnd", s.dnd)
        .put("breakMinutes", s.breakMinutes)
        .put("autoBreak", s.autoBreak)
        .put("dailyGoalMinutes", s.dailyGoalMinutes)
        .put("themeMode", s.themeMode)
        .put("weeklyReport", s.weeklyReport)

    private fun settingsFromJson(o: JSONObject): Settings {
        val d = Settings(blockingEnabled = false)
        return d.copy(
            minutes = o.optInt("minutes", d.minutes),
            speciesId = o.optString("speciesId", d.speciesId),
            tagId = o.optString("tagId", d.tagId),
            stopwatch = o.optBoolean("stopwatch", d.stopwatch),
            sound = runCatching { Sound.valueOf(o.optString("sound", d.sound.name)) }.getOrDefault(d.sound),
            reminder = o.optBoolean("reminder", d.reminder),
            reminderMinute = o.optInt("reminderMinute", d.reminderMinute),
            gardenTheme = o.optString("gardenTheme", d.gardenTheme),
            dnd = o.optBoolean("dnd", d.dnd),
            breakMinutes = o.optInt("breakMinutes", d.breakMinutes),
            autoBreak = o.optBoolean("autoBreak", d.autoBreak),
            dailyGoalMinutes = o.optInt("dailyGoalMinutes", d.dailyGoalMinutes),
            themeMode = o.optString("themeMode", d.themeMode),
            weeklyReport = o.optBoolean("weeklyReport", d.weeklyReport),
        )
    }
}
