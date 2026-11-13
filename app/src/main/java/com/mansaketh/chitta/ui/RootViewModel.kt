package com.mansaketh.chitta.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.data.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Decides whether to show the first-run chooser: an empty book that nobody has chosen what to do with yet. null = still loading. */
@HiltViewModel
class RootViewModel @Inject constructor(accounts: AccountRepository, private val prefs: AppPrefs, val undo: UndoCenter) : ViewModel() {
    val needsFirstRun: StateFlow<Boolean?> = accounts.observeAll().map { it.isEmpty() && !prefs.firstRunDone }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
