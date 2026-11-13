package com.mansaketh.chitta.intake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How the share receiver and the inbox tell the open screens something arrived. */
@Singleton
class IntakeSignals @Inject constructor() {
    private val _open = MutableStateFlow<String?>(null)
    /** "review" or "upi:image" / "upi:pdf" / "upi:csv": the review screen should open (and read the shared copy). Cleared by [consume]. */
    val open: StateFlow<String?> = _open
    fun request(what: String) { _open.value = what }
    fun consume() { _open.value = null }

    private val _refresh = MutableStateFlow(0)
    /** Goes up each time something new may be in the inbox. */
    val refresh: StateFlow<Int> = _refresh
    fun refreshNow() { _refresh.value = _refresh.value + 1 }
}
