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
    @Query("SELECT COUNT(*) FROM account WHERE archived = 0") suspend fun countActive(): Int
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: CategoryEntity)
    @Query("SELECT * FROM category ORDER BY sortOrder, name") fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category ORDER BY sortOrder, name") suspend fun getAll(): List<CategoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<CategoryEntity>)
    @Query("DELETE FROM category WHERE id IN (:ids)") suspend fun deleteMany(ids: List<String>)
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
    @Query("SELECT * FROM txn ORDER BY dateMs DESC, id") fun observeAll(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM txn WHERE id = :id") fun observe(id: String): Flow<TransactionEntity?>
    @Query("SELECT * FROM txn WHERE id IN (:ids)") suspend fun getMany(ids: List<String>): List<TransactionEntity>
    @Query("DELETE FROM txn WHERE id IN (:ids)") suspend fun deleteMany(ids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<TransactionEntity>)

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

    // ---- Phase 5
    @Query("SELECT * FROM budget ORDER BY sortOrder, name") fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budget WHERE id = :id") suspend fun get(id: String): BudgetEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: BudgetEntity)
    @Query("DELETE FROM budget WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM category_budget_limit ORDER BY id") fun observeLimits(): Flow<List<CategoryLimitEntity>>
    @Query("SELECT * FROM category_budget_limit WHERE budgetId = :budgetId ORDER BY id") suspend fun getLimitsFor(budgetId: String): List<CategoryLimitEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLimits(rows: List<CategoryLimitEntity>)
    @Query("DELETE FROM category_budget_limit WHERE budgetId = :budgetId") suspend fun deleteLimitsFor(budgetId: String)
    @Query("DELETE FROM category_budget_limit WHERE id = :id") suspend fun deleteLimit(id: String)
}

@Dao
interface ObjectiveDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<ObjectiveEntity>)
    @Query("SELECT * FROM objective ORDER BY sortOrder, name") suspend fun getAll(): List<ObjectiveEntity>

    // ---- Phase 5
    @Query("SELECT * FROM objective ORDER BY sortOrder, name") fun observeAll(): Flow<List<ObjectiveEntity>>
    @Query("SELECT * FROM objective WHERE id = :id") suspend fun get(id: String): ObjectiveEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: ObjectiveEntity)
    @Query("DELETE FROM objective WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface TitleMemoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(rows: List<TitleMemoryEntity>)
    @Query("SELECT * FROM title_memory ORDER BY sortOrder, id") suspend fun getAll(): List<TitleMemoryEntity>
    @Query("SELECT * FROM title_memory ORDER BY sortOrder, id") fun observeAll(): Flow<List<TitleMemoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<TitleMemoryEntity>)
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
    @Query("SELECT * FROM loan_terms ORDER BY objectiveId") fun observeLoanTerms(): Flow<List<LoanTermsEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLoanTerms(row: LoanTermsEntity)
    @Query("DELETE FROM loan_terms WHERE objectiveId = :objectiveId") suspend fun deleteLoanTerms(objectiveId: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDeposits(rows: List<FixedDepositEntity>)
    @Query("SELECT * FROM fixed_deposit ORDER BY id") suspend fun getDeposits(): List<FixedDepositEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCardDues(rows: List<CardDueEntity>)
    @Query("SELECT * FROM card_due ORDER BY dateMs, id") suspend fun getCardDues(): List<CardDueEntity>
    @Query("SELECT * FROM fixed_deposit ORDER BY maturityMs, id") fun observeDeposits(): Flow<List<FixedDepositEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDeposit(row: FixedDepositEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDepositIfNew(row: FixedDepositEntity): Long
    @Query("DELETE FROM fixed_deposit WHERE id = :id") suspend fun deleteDeposit(id: String)
    @Query("SELECT * FROM card_due ORDER BY dateMs, id") fun observeCardDues(): Flow<List<CardDueEntity>>
    /** Returns the row id of each insert, or -1 for a message that was already there (same id), so a message counts once. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCardDuesIfNew(rows: List<CardDueEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCardDue(row: CardDueEntity)
    @Query("DELETE FROM card_due WHERE id IN (:ids)") suspend fun deleteCardDues(ids: List<String>)
    @Query("SELECT * FROM mutual_fund ORDER BY name, id") fun observeFunds(): Flow<List<MutualFundEntity>>
    @Query("SELECT * FROM mutual_fund ORDER BY name, id") suspend fun getFunds(): List<MutualFundEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFund(row: MutualFundEntity)
    @Query("DELETE FROM mutual_fund WHERE id = :id") suspend fun deleteFund(id: String)
    @Query("DELETE FROM mutual_fund WHERE id IN (:ids)") suspend fun deleteFunds(ids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFunds(rows: List<MutualFundEntity>)
    @Query("SELECT * FROM nps_account ORDER BY name, id") fun observeNps(): Flow<List<NpsAccountEntity>>
    @Query("SELECT * FROM nps_account ORDER BY name, id") suspend fun getNps(): List<NpsAccountEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNps(row: NpsAccountEntity)
    @Query("DELETE FROM nps_account WHERE id = :id") suspend fun deleteNps(id: String)
}

@Dao
interface PinnedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: PinnedEntryEntity)
    @Query("SELECT * FROM pinned_entry ORDER BY sortOrder, createdAtMs") fun observeAll(): Flow<List<PinnedEntryEntity>>
    @Query("DELETE FROM pinned_entry WHERE id = :id") suspend fun delete(id: String)
}

/** Insert only. There is deliberately no update or delete here: history cannot be edited. */
@Dao
interface VersionDao {
    @Insert suspend fun add(rows: List<EntryVersionEntity>)
    @Query("SELECT * FROM entry_version WHERE entryId = :entryId ORDER BY id") fun observeFor(entryId: String): Flow<List<EntryVersionEntity>>
    @Query("SELECT * FROM entry_version WHERE entryId = :entryId ORDER BY id") suspend fun getFor(entryId: String): List<EntryVersionEntity>
    /** The newest version of every entry whose last action was DELETED (actionCode 2): the recently deleted list. */
    @Query("SELECT * FROM entry_version v WHERE actionCode = 2 AND id = (SELECT MAX(id) FROM entry_version WHERE entryId = v.entryId) ORDER BY atMs DESC LIMIT :limit")
    fun observeDeleted(limit: Int): Flow<List<EntryVersionEntity>>
}

@Dao
interface RateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRates(rows: List<ExchangeRateEntity>)
    @Query("SELECT * FROM exchange_rate ORDER BY currency") fun observeRates(): Flow<List<ExchangeRateEntity>>
    @Query("SELECT * FROM exchange_rate ORDER BY currency") suspend fun getRates(): List<ExchangeRateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOverride(row: RateOverrideEntity)
    @Query("DELETE FROM rate_override WHERE fromCurrency = :from AND toCurrency = :to") suspend fun removeOverride(from: String, to: String)
    @Query("SELECT * FROM rate_override") fun observeOverrides(): Flow<List<RateOverrideEntity>>
}

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: RuleEntity)
    @Query("SELECT * FROM entry_rule ORDER BY ruleOrder, id") fun observeAll(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM entry_rule ORDER BY ruleOrder, id") suspend fun getAll(): List<RuleEntity>
    @Query("DELETE FROM entry_rule WHERE id = :id") suspend fun delete(id: String)
}
