package uz.focuson.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file, the same on the phone and the computer. Both apps can write it,
 * and both can either replace their data with it or merge it in.
 */
object Backup {
    class Parsed(
        val history: List<SessionRecord>,
        val tags: List<Tag>,
        val coins: Int,
        val owned: Set<String>,
    )

    fun write(history: List<SessionRecord>, tags: List<Tag>, coins: Int, owned: Set<String>, from: String): String =
        JSONObject()
            .put("app", "focuson")
            .put("version", 1)
            .put("from", from)
            .put("history", SessionRecord.listToJson(history))
            .put("tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
            .put("coins", coins)
            .put("owned", JSONArray((owned - Catalog.freeIds).toList()))
            .toString(2)

    /** Throws if the text is not a Focus On backup. */
    fun parse(text: String): Parsed {
        val o = JSONObject(text)
        require(o.optString("app") == "focuson") { "Bu Focus On fayli emas" }
        val history = SessionRecord.listFromJson(o.getJSONArray("history"))
        val tagsArr = o.optJSONArray("tags") ?: JSONArray()
        val tags = (0 until tagsArr.length()).map { Tag.fromJson(tagsArr.getJSONObject(it)) }
        val ownedArr = o.optJSONArray("owned") ?: JSONArray()
        val owned = (0 until ownedArr.length()).map { ownedArr.getString(it) }.toSet()
        return Parsed(history, tags, o.optInt("coins", 0), owned)
    }

    /** Same session on both devices = same start, end and plant. */
    private fun key(r: SessionRecord) = "${r.startAt}/${r.endAt}/${r.speciesId}"

    /** Local sessions plus the incoming ones not seen before, oldest first. */
    fun mergeHistory(local: List<SessionRecord>, incoming: List<SessionRecord>): List<SessionRecord> {
        val seen = local.map(::key).toHashSet()
        return (local + incoming.filter { seen.add(key(it)) }).sortedBy { it.startAt }
    }

    /** How many incoming sessions are new here. */
    fun countNew(local: List<SessionRecord>, incoming: List<SessionRecord>): Int {
        val seen = local.map(::key).toHashSet()
        return incoming.count { key(it) !in seen }
    }

    /** Local tags first; incoming tags with an unknown id are added. */
    fun mergeTags(local: List<Tag>, incoming: List<Tag>): List<Tag> {
        val ids = local.map { it.id }.toHashSet()
        return local + incoming.filter { ids.add(it.id) }
    }
}
