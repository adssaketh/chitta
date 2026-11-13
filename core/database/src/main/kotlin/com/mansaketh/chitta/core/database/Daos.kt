package com.mansaketh.chitta.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row per account with the plain sum of its transaction amounts in paise. */
data class AccountSum(val accountId: String, val sumPaise: Long, val count: Int)

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: AccountEntity)
    @Query("SELECT * FROM account ORDER BY sortOrder, name") fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM account ORDER BY sortOrder, name") suspend fun getAll(): List<AccountEntity>
    @Query("SELECT * FROM account WHERE id = :id") suspend fun get(id: String): AccountEntity?
    @Query("SELECT COUNT(*) FROM account") suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: CategoryEntity)
    @Query("SELECT * FROM category ORDER BY sortOrder, name") fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category ORDER BY sortOrder, name") suspend fun getAll(): List<CategoryEntity>
    @Query("SELECT COUNT(*) FROM category") suspend fun count(): Int
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: TransactionEntity)
    @Query("SELECT * FROM txn WHERE id = :id") suspend fun get(id: String): TransactionEntity?
    @Query("SELECT * FROM txn ORDER BY dateMs DESC, id") suspend fun getAll(): List<TransactionEntity>
    @Query("SELECT * FROM txn WHERE dateMs >= :fromMs AND dateMs < :toMs ORDER BY dateMs DESC, id")
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM txn WHERE accountId = :accountId ORDER BY dateMs DESC, id") fun observeForAccount(accountId: String): Flow<List<TransactionEntity>>
    @Query("SELECT COUNT(*) FROM txn") suspend fun count(): Int

    /**
     * Plain sums, no Paddhu balance rules applied. Which entries count toward a balance (paid, upcoming, lent,
     * borrowed) is decided in Phase 4 together with a comparison against Paddhu's own on-screen balances.
     */
    @Query("SELECT accountId, SUM(amountPaise) AS sumPaise, COUNT(*) AS count FROM txn GROUP BY accountId")
    suspend fun sumsByAccount(): List<AccountSum>
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<BudgetEntity>)
    @Query("SELECT * FROM budget ORDER BY sortOrder, name") suspend fun getAll(): List<BudgetEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLimits(rows: List<CategoryLimitEntity>)
    @Query("SELECT * FROM category_budget_limit ORDER BY id") suspend fun getLimits(): List<CategoryLimitEntity>
}

@Dao
interface ObjectiveDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<ObjectiveEntity>)
    @Query("SELECT * FROM objective ORDER BY sortOrder, name") suspend fun getAll(): List<ObjectiveEntity>
}

@Dao
interface TitleMemoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<TitleMemoryEntity>)
    @Query("SELECT * FROM title_memory ORDER BY sortOrder, id") suspend fun getAll(): List<TitleMemoryEntity>
}

@Dao
interface MiscDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTemplates(rows: List<ScanTemplateEntity>)
    @Query("SELECT * FROM scan_template ORDER BY id") suspend fun getTemplates(): List<ScanTemplateEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLog(rows: List<ActivityLogEntity>)
    @Query("SELECT * FROM activity_log ORDER BY atMs, id") suspend fun getLog(): List<ActivityLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSettings(rows: List<SettingEntity>)
    @Query("SELECT * FROM setting ORDER BY `key`") suspend fun getSettings(): List<SettingEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLoanTerms(rows: List<LoanTermsEntity>)
    @Query("SELECT * FROM loan_terms ORDER BY objectiveId") suspend fun getLoanTerms(): List<LoanTermsEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDeposits(rows: List<FixedDepositEntity>)
    @Query("SELECT * FROM fixed_deposit ORDER BY id") suspend fun getDeposits(): List<FixedDepositEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCardDues(rows: List<CardDueEntity>)
    @Query("SELECT * FROM card_due ORDER BY dateMs, id") suspend fun getCardDues(): List<CardDueEntity>
}
