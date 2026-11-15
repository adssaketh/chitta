package com.mansaketh.chitta.cloud

import android.content.Context
import com.mansaketh.chitta.core.domain.SharedItems
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** What a viewer last saw of somebody's shared item: the owner's choices and the payload. Never part of the viewer's own book. */
data class SharedCopy(val meta: SharedItems.Meta, val payload: Map<String, Any?>)

/** The member whose edit the owner's phone applied last, and the `_u` of it. Shown as "Edited by" while nothing has changed here since. */
data class SharedEditor(val uid: String, val atMs: Long)

data class SharedItemsState(
    /** The items this person shares (private ones are not here), by document id. */
    val own: Map<String, SharedItems.Meta> = emptyMap(),
    /** Other people's items shared with this person. */
    val copies: Map<String, SharedCopy> = emptyMap(),
    /** Document ids to remove from the server on the next round (made private, or deleted). */
    val removals: Set<String> = emptySet(),
    val members: Map<String, String> = emptyMap(),
    /** Owner side: the member whose edit was applied last, by document id (step 5b). */
    val editors: Map<String, SharedEditor> = emptyMap(),
    /** Member side: items where somebody else's later change replaced this person's edit, until it is acknowledged (step 5b). */
    val replaced: Set<String> = emptySet(),
    val problem: String = "",
    val lastSyncMs: Long = 0L,
)

/**
 * Device-only, like [HouseholdPrefs] (DECISIONS D199): a restore or an import must never share an item or switch sharing on. The server is
 * the truth: after a reinstall the owner's choices are read back from the person's own documents, and copies come down again.
 *
 * Besides what [SharedItemsState] shows, the phone keeps what it last sent for each item (`_u` and, for a loan, the paid figure) here.
 * That is the upload queue: an item waits while its newest change is later than what was sent.
 */
@Singleton
class SharedItemsPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta.household.shared", Context.MODE_PRIVATE)

    private fun obj(key: String) = runCatching { JSONObject(p.getString(key, null) ?: "{}") }.getOrDefault(JSONObject())

    private fun metaOf(o: JSONObject): SharedItems.Meta? = SharedItems.metaFromDocument(toMap(o))

    private fun read(): SharedItemsState {
        val own = obj("own"); val copies = obj("copies")
        return SharedItemsState(
            own = own.keys().asSequence().mapNotNull { id -> own.optJSONObject(id)?.let(::metaOf)?.let { id to it } }.toMap(),
            copies = copies.keys().asSequence().mapNotNull { id ->
                val o = copies.optJSONObject(id) ?: return@mapNotNull null
                val meta = metaOf(o) ?: return@mapNotNull null
                val payload = o.optJSONObject("payload")?.let(::toMap) ?: return@mapNotNull null
                id to SharedCopy(meta, payload)
            }.toMap(),
            removals = runCatching { JSONArray(p.getString("removals", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet()),
            members = obj("members").let { m -> m.keys().asSequence().associateWith { m.optString(it) } },
            editors = obj("editors").let { e -> e.keys().asSequence().mapNotNull { id -> e.optJSONObject(id)?.let { o -> o.optString("uid").takeIf { u -> u.isNotEmpty() }?.let { u -> id to SharedEditor(u, o.optLong("at")) } } }.toMap() },
            replaced = runCatching { JSONArray(p.getString("replaced", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet()),
            problem = p.getString("problem", "").orEmpty(), lastSyncMs = p.getLong("lastSync", 0L),
        )
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SharedItemsState> = _state

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        _state.value = read()
    }

    @Synchronized fun setOwn(meta: SharedItems.Meta) {
        val o = obj("own").put(meta.docId, JSONObject(SharedItems.metaToFields(meta)))
        edit { putString("own", o.toString()).putString("removals", JSONArray(_state.value.removals - meta.docId).toString()) }
    }

    /** The item is private again, or gone: it stays on the server until a round removes it there. */
    @Synchronized fun dropOwn(docId: String) {
        val o = obj("own").also { it.remove(docId) }
        edit { putString("own", o.toString()).putString("removals", JSONArray(_state.value.removals + docId).toString()) }
    }

    /** The server no longer has it: forget it here too. */
    @Synchronized fun removalDone(docId: String) {
        val up = obj("uploaded").also { it.remove(docId) }; val paid = obj("paidSent").also { it.remove(docId) }; val ed = obj("editors").also { it.remove(docId) }
        edit { putString("removals", JSONArray(_state.value.removals - docId).toString()).putString("uploaded", up.toString()).putString("paidSent", paid.toString()).putString("editors", ed.toString()) }
    }

    /** Takes the owner's choices from the server (after a reinstall). Does not touch what was sent. */
    @Synchronized fun adoptOwn(meta: SharedItems.Meta) {
        edit { putString("own", obj("own").put(meta.docId, JSONObject(SharedItems.metaToFields(meta))).toString()) }
    }

    fun uploadedAt(docId: String): Long? = obj("uploaded").let { if (it.has(docId)) it.optLong(docId) else null }
    fun paidSent(docId: String): Long? = obj("paidSent").let { if (it.has(docId)) it.optLong(docId) else null }

    @Synchronized fun sent(docId: String, updatedAtMs: Long, paidPaise: Long?) {
        val up = obj("uploaded").put(docId, updatedAtMs); val paid = obj("paidSent")
        if (paidPaise != null) paid.put(docId, paidPaise)
        edit { putString("uploaded", up.toString()).putString("paidSent", paid.toString()) }
    }

    @Synchronized fun putCopies(docs: Map<String, SharedCopy>) {
        if (docs.isEmpty()) return
        val c = obj("copies")
        for ((id, copy) in docs) c.put(id, JSONObject(SharedItems.metaToFields(copy.meta)).put("payload", JSONObject(copy.payload)))
        edit { putString("copies", c.toString()) }
    }

    @Synchronized fun removeCopies(ids: Collection<String>) {
        val c = obj("copies"); val mine = obj("myEdits"); var changed = false
        for (id in ids) if (c.has(id)) { c.remove(id); changed = true }
        for (id in ids) mine.remove(id)
        if (changed) edit { putString("copies", c.toString()).putString("myEdits", mine.toString()).putString("replaced", JSONArray(_state.value.replaced - ids.toSet()).toString()) }
    }

    // ---- step 5b: members' edits

    /** Owner side: a member's edit was applied here. The attribution lasts until the owner changes the item here. */
    @Synchronized fun setEditor(docId: String, uid: String, atMs: Long) {
        val e = obj("editors").put(docId, JSONObject().put("uid", uid).put("at", atMs))
        edit { putString("editors", e.toString()) }
    }

    @Synchronized fun clearEditor(docId: String) {
        val e = obj("editors"); if (!e.has(docId)) return
        e.remove(docId); edit { putString("editors", e.toString()) }
    }

    /** Member side: the `_u` this person wrote for an item, so a later change by somebody else can be told apart from their own. */
    fun myEditU(docId: String): Long? = obj("myEdits").let { if (it.has(docId)) it.optLong(docId) else null }

    @Synchronized fun setMyEdit(docId: String, u: Long) {
        val m = obj("myEdits").put(docId, u)
        edit { putString("myEdits", m.toString()).putString("replaced", JSONArray(_state.value.replaced - docId).toString()) }
    }

    @Synchronized fun markReplaced(docId: String) {
        val m = obj("myEdits").also { it.remove(docId) }
        edit { putString("myEdits", m.toString()).putString("replaced", JSONArray(_state.value.replaced + docId).toString()) }
    }

    @Synchronized fun ackReplaced(docId: String) {
        if (docId in _state.value.replaced) edit { putString("replaced", JSONArray(_state.value.replaced - docId).toString()) }
    }

    fun setMembers(names: Map<String, String>) {
        if (_state.value.members != names) edit { putString("members", JSONObject(names).toString()) }
    }

    fun synced(nowMs: Long) = edit { putLong("lastSync", nowMs).remove("problem") }
    fun problem(text: String) { if (_state.value.problem != text) edit { putString("problem", text) } }

    /** Leaving, being removed, or signing out: the server's copies are gone or hidden, and nothing here may be sent again. */
    fun reset() = edit { clear() }

    // ---- JSON <-> plain maps. Payloads are nested maps, lists and numbers; nothing else.

    private fun toMap(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { fromJson(o.get(it)) }

    private fun fromJson(v: Any?): Any? = when (v) {
        JSONObject.NULL, null -> null
        is JSONObject -> toMap(v)
        is JSONArray -> (0 until v.length()).map { fromJson(v.get(it)) }
        else -> v
    }
}
