package com.mansaketh.chitta.core.database

import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.core.model.FixedDeposit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Fixed deposits and the card dues read from messages (Phase 8b). Both tables have existed since schema 1 (the importer fills them), so
 * there is no schema change. Neither moves an account balance: a deposit is a record, and a card due is a side note that the cockpit reads.
 */
class AssetsRepository(private val db: ChittaDatabase) {
    fun observeDeposits(): Flow<List<FixedDeposit>> = db.misc().observeDeposits().map { rows -> rows.map { it.toModel() } }
    suspend fun deposits(): List<FixedDeposit> = db.misc().getDeposits().map { it.toModel() }
    suspend fun saveDeposit(d: FixedDeposit) = db.misc().upsertDeposit(d.toEntity())

    /** Adds the deposit unless one with the same id is already there. True when it was added. */
    suspend fun addDepositIfNew(d: FixedDeposit): Boolean = db.misc().insertDepositIfNew(d.toEntity()) != -1L

    /** Deletes the deposit and returns it, so Undo can put it back. */
    suspend fun deleteDeposit(id: String): FixedDeposit? {
        val old = db.misc().getDeposits().firstOrNull { it.id == id }?.toModel() ?: return null
        db.misc().deleteDeposit(id)
        return old
    }

    fun observeCardDues(): Flow<List<CardDue>> = db.misc().observeCardDues().map { rows -> rows.map { it.toModel() } }
    suspend fun cardDues(): List<CardDue> = db.misc().getCardDues().map { it.toModel() }
    suspend fun saveCardDue(d: CardDue) = db.misc().upsertCardDue(d.toEntity())

    /** Adds the messages that are not there yet (by id) and returns those that were added, so Undo removes exactly them. */
    suspend fun addCardDues(dues: List<CardDue>): List<CardDue> {
        if (dues.isEmpty()) return emptyList()
        val unique = dues.distinctBy { it.id }
        val ids = db.misc().insertCardDuesIfNew(unique.map { it.toEntity() })
        return unique.filterIndexed { i, _ -> ids[i] != -1L }
    }

    suspend fun deleteCardDues(ids: List<String>) { if (ids.isNotEmpty()) db.misc().deleteCardDues(ids) }
}
