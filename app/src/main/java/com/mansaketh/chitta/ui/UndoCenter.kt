package com.mansaketh.chitta.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The one Undo bar for the whole app: every save, edit and delete posts here and can be undone for 10 seconds (master section 13). */
@Singleton
class UndoCenter @Inject constructor() {
    class Item(val message: String, val undo: suspend () -> Unit)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _current = MutableStateFlow<Item?>(null)
    val current: StateFlow<Item?> = _current
    private var timer: Job? = null

    fun post(message: String, undo: suspend () -> Unit) {
        _current.value = Item(message, undo)
        timer?.cancel()
        timer = scope.launch { delay(UNDO_WINDOW_MS); _current.value = null }
    }

    fun undo() {
        val item = _current.value ?: return
        _current.value = null
        timer?.cancel()
        scope.launch { item.undo() }
    }

    companion object { const val UNDO_WINDOW_MS = 10_000L }
}
