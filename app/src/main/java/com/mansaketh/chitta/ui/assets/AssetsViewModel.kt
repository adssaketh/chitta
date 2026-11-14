package com.mansaketh.chitta.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.domain.AmountExpression
import com.mansaketh.chitta.core.domain.ManualAssets
import com.mansaketh.chitta.core.model.AssetKind
import com.mansaketh.chitta.core.model.AssetValue
import com.mansaketh.chitta.core.model.ManualAsset
import com.mansaketh.chitta.data.UiPrefs
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class AssetsUi(
    val assets: List<ManualAsset> = emptyList(),
    val values: List<AssetValue> = emptyList(),
    val countGold: Boolean = true,
    val countEpf: Boolean = true,
    val countPpf: Boolean = true,
    val nowMs: Long = 0L,
    val zone: ZoneId = ZoneId.systemDefault(),
    val loading: Boolean = true,
) {
    fun counts(kind: AssetKind) = when (kind) { AssetKind.GOLD -> countGold; AssetKind.EPF -> countEpf; AssetKind.PPF -> countPpf }
    fun valuesOf(assetId: String) = values.filter { it.assetId == assetId }
}

/** Gold, EPF and PPF typed in by hand, with a history of dated values (Phase 8i). Records only: nothing here moves an account balance. */
@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val repo: AssetsRepository,
    private val prefs: UiPrefs,
    private val ids: IdSource,
    private val clock: Clock,
    private val undo: UndoCenter,
) : ViewModel() {
    private val switches = combine(prefs.netWorthGold, prefs.netWorthEpf, prefs.netWorthPpf) { g, e, p -> Triple(g, e, p) }

    val ui: StateFlow<AssetsUi> = combine(repo.observeAssets(), repo.observeAssetValues(), switches) { a, v, sw ->
        AssetsUi(a, v, sw.first, sw.second, sw.third, clock.nowMs(), clock.zone(), loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUi(zone = clock.zone(), nowMs = clock.nowMs()))

    fun setCounted(kind: AssetKind, on: Boolean) = when (kind) {
        AssetKind.GOLD -> prefs.setNetWorthGold(on)
        AssetKind.EPF -> prefs.setNetWorthEpf(on)
        AssetKind.PPF -> prefs.setNetWorthPpf(on)
    }

    /** Typed rupees (a sum is fine) as paise, or null when empty, negative or not a number. Zero is allowed: it means sold or emptied. */
    private fun parse(raw: String): Long? = AmountExpression.evaluate(raw)?.value?.takeIf(ManualAssets::validValue)

    /** Adds an asset with its first value. Returns a message when something is missing, null when it was saved. */
    fun add(kind: AssetKind, name: String, valueText: String, asOfMs: Long, note: String): String? {
        if (name.isBlank()) return "Give it a name."
        val paise = parse(valueText) ?: return "Enter what it is worth now, zero or more."
        val now = clock.nowMs()
        val asset = ManualAsset(ids.newId(), kind, name.trim(), note.trim(), now)
        val first = AssetValue(ids.newId(), asset.id, asOfMs, paise, now)
        viewModelScope.launch {
            repo.addAsset(asset, first)
            undo.post("${kind.label} added · ${asset.name}") { repo.deleteAsset(asset.id) }
        }
        return null
    }

    /** Adds a new dated value to an asset. The older values stay as history. */
    fun addValue(asset: ManualAsset, valueText: String, asOfMs: Long): String? {
        val paise = parse(valueText) ?: return "Enter what it is worth, zero or more."
        val v = AssetValue(ids.newId(), asset.id, asOfMs, paise, clock.nowMs())
        viewModelScope.launch {
            repo.saveAssetValue(v)
            undo.post("Value saved · ${asset.name}") { repo.deleteAssetValue(v.id) }
        }
        return null
    }

    fun removeValue(v: AssetValue) {
        viewModelScope.launch {
            repo.deleteAssetValue(v.id)
            undo.post("Value removed") { repo.saveAssetValue(v) }
        }
    }

    fun edit(asset: ManualAsset, name: String, note: String): String? {
        if (name.isBlank()) return "Give it a name."
        val next = asset.copy(name = name.trim(), note = note.trim())
        viewModelScope.launch {
            repo.saveAsset(next)
            undo.post("Saved changes") { repo.saveAsset(asset) }
        }
        return null
    }

    fun delete(asset: ManualAsset) {
        viewModelScope.launch {
            val gone = repo.deleteAsset(asset.id) ?: return@launch
            undo.post("Deleted · ${asset.name}") { repo.restoreAsset(gone.first, gone.second) }
        }
    }
}
