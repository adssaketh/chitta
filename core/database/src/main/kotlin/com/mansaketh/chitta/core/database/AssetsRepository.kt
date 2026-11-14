package com.mansaketh.chitta.core.database

import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.model.NpsAccount
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

    // ---- mutual funds (Phase 8c). A record only: no account balance moves.
    fun observeFunds(): Flow<List<MutualFund>> = db.misc().observeFunds().map { rows -> rows.map { it.toModel() } }
    suspend fun funds(): List<MutualFund> = db.misc().getFunds().map { it.toModel() }
    suspend fun saveFund(f: MutualFund) = db.misc().upsertFund(f.toEntity())

    /** Deletes the fund and returns it, so Undo can put it back. */
    suspend fun deleteFund(id: String): MutualFund? {
        val old = db.misc().getFunds().firstOrNull { it.id == id }?.toModel() ?: return null
        db.misc().deleteFund(id)
        return old
    }

    /** Writes a fetched price and the matched scheme code (and ISIN if the fund has none). Touches nothing else. */
    suspend fun setFundPrice(id: String, nav: String, date: String, schemeCode: String, isin: String) = db.misc().setFundPrice(id, nav, date, schemeCode, isin)

    /** Writes a fund's SIP estimate. Touches nothing else. */
    suspend fun setFundSipEstimate(id: String, units: String, investedPaise: Long, count: Int, through: String) = db.misc().setFundSipEstimate(id, units, investedPaise, count, through)

    /** Applies a statement import in one go: removes [removeIds], then saves [save]. Returns nothing; Undo is the caller's old copies. */
    suspend fun replaceFunds(removeIds: List<String>, save: List<MutualFund>) {
        if (removeIds.isNotEmpty()) db.misc().deleteFunds(removeIds)
        if (save.isNotEmpty()) db.misc().upsertFunds(save.map { it.toEntity() })
    }

    // ---- NPS (Phase 8c). Own tab; never added into mutual funds.
    fun observeNps(): Flow<List<NpsAccount>> = db.misc().observeNps().map { rows -> rows.map { it.toModel() } }
    suspend fun nps(): List<NpsAccount> = db.misc().getNps().map { it.toModel() }
    suspend fun saveNps(a: NpsAccount) = db.misc().upsertNps(a.toEntity())

    /** Deletes the account and its schemes; returns both so Undo can put them back. */
    suspend fun deleteNps(id: String): Pair<NpsAccount, List<MutualFund>>? {
        val acc = db.misc().getNps().firstOrNull { it.id == id }?.toModel() ?: return null
        val schemes = db.misc().getFunds().filter { it.groupId == id }.map { it.toModel() }
        db.misc().deleteFunds(schemes.map { it.id })
        db.misc().deleteNps(id)
        return acc to schemes
    }
}
