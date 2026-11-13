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
}

class TransactionRepository(private val db: ChittaDatabase) {
    suspend fun get(id: String): Transaction? = db.transactions().get(id)?.toModel()
    suspend fun save(transaction: Transaction) = db.transactions().upsert(transaction.toEntity())
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<Transaction>> =
        db.transactions().observeBetween(fromMs, toMs).map { rows -> rows.map { it.toModel() } }
    fun observeForAccount(accountId: String): Flow<List<Transaction>> =
        db.transactions().observeForAccount(accountId).map { rows -> rows.map { it.toModel() } }
}
