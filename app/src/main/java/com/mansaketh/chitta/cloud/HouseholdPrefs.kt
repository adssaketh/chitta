package com.mansaketh.chitta.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What this phone last knew about the person's household. Only a copy: the server is the truth and the Household page re-checks it. */
data class HouseholdLocal(val id: String, val name: String, val isOwner: Boolean, val nickname: String, val showBalances: Boolean) {
    val inHousehold: Boolean get() = id.isNotEmpty()
}

/**
 * Device-only, like [CloudPrefs]: never part of the book, so a restore or an import cannot put someone in a household or take them out
 * of one (DECISIONS D173, D199). Joining, leaving or being removed never touches entries (master section 7).
 */
@Singleton
class HouseholdPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta.household", Context.MODE_PRIVATE)

    private fun read() = HouseholdLocal(
        id = p.getString("id", "").orEmpty(), name = p.getString("name", "").orEmpty(), isOwner = p.getString("role", "") == "owner",
        nickname = p.getString("nickname", "").orEmpty(), showBalances = p.getBoolean("showBalances", false),
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<HouseholdLocal> = _state

    fun save(id: String, name: String, isOwner: Boolean, nickname: String, showBalances: Boolean) {
        p.edit().putString("id", id).putString("name", name).putString("role", if (isOwner) "owner" else "member")
            .putString("nickname", nickname).putBoolean("showBalances", showBalances).apply()
        _state.value = read()
    }

    fun clear() {
        p.edit().clear().apply()
        _state.value = read()
    }
}
