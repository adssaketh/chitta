package com.mansaketh.chitta.core.database

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Repositories hand out plain model classes, never Room entities, so screens and domain code stay Android-free. */
class AccountRepository(private val db: ChittaDatabase) {
    fun observeAll(): Flow<List<Account>> = db.accounts().observeAll().map { rows -> rows.map { it.toModel() } }
    suspend fun get(id: String): Account? = db.accounts().get(id)?.toModel()
    suspend fun save(account: Account) = db.accounts().upsert(account.toEntity())
    suspend fun sums(): List<AccountSum> = db.transactions().sumsByAccount()
}

class CategoryRepository(private val db: ChittaDatabase) {
    fun observeAll(): Flow<List<Category>> = db.categories().observeAll().map { rows -> rows.map { it.toModel() } }
    suspend fun save(category: Category) = db.categories().upsert(category.toEntity())
    suspend fun get(id: String): Category? = db.categories().getAll().firstOrNull { it.id == id }?.toModel()
}

class TransactionRepository(private val db: ChittaDatabase) {
    suspend fun get(id: String): Transaction? = db.transactions().get(id)?.toModel()
    suspend fun save(transaction: Transaction) = db.transactions().upsert(transaction.toEntity())
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<Transaction>> =
        db.transactions().observeBetween(fromMs, toMs).map { rows -> rows.map { it.toModel() } }
    fun observeForAccount(accountId: String): Flow<List<Transaction>> =
        db.transactions().observeForAccount(accountId).map { rows -> rows.map { it.toModel() } }
}

/** Plain-language account/category edits used by the Accounts and Categories screens. */
class AccountEditor(private val db: ChittaDatabase) {
    suspend fun archive(id: String, archived: Boolean) {
        val a = db.accounts().get(id) ?: return
        db.accounts().upsert(a.copy(archived = archived))
    }
}

class TitleRepository(private val db: ChittaDatabase) {
    fun observeAll(): Flow<List<com.mansaketh.chitta.core.model.TitleMemory>> = db.titles().observeAll().map { rows -> rows.map { it.toModel() } }

    /** Remembers [title] for [categoryId] the first time it is saved (Paddhu's titles memory). */
    suspend fun remember(title: String, categoryId: String, nowMs: Long, newId: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        val all = db.titles().getAll()
        if (all.any { it.title.equals(t, ignoreCase = true) }) return
        db.titles().upsertAll(listOf(TitleMemoryEntity(newId, categoryId, t, nowMs, null, (all.maxOfOrNull { it.sortOrder } ?: -1) + 1, false)))
    }

    suspend fun saveAll(rows: List<com.mansaketh.chitta.core.model.TitleMemory>) = db.titles().upsertAll(rows.map { it.toEntity() })
}

/** Category writes that touch several rows at once (merge, reorder, convert). */
class CategoryWriter(private val db: ChittaDatabase) {
    suspend fun saveAll(rows: List<Category>) = db.categories().upsertAll(rows.map { it.toEntity() })
    suspend fun delete(ids: List<String>) = db.categories().deleteMany(ids)
}

class RatesRepository(private val db: ChittaDatabase) {
    fun observeRates(): Flow<List<ExchangeRateEntity>> = db.rates().observeRates()
    fun observeOverrides(): Flow<List<RateOverrideEntity>> = db.rates().observeOverrides()
    suspend fun rates(): List<ExchangeRateEntity> = db.rates().getRates()
    suspend fun store(rows: List<ExchangeRateEntity>) = db.rates().putRates(rows)
    suspend fun setOverride(from: String, to: String, rate: String, nowMs: Long) = db.rates().putOverride(RateOverrideEntity(from, to, rate, nowMs))
    suspend fun removeOverride(from: String, to: String) = db.rates().removeOverride(from, to)
}
