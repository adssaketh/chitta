package com.mansaketh.chitta.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room tables. Rules (Phase 0 audit, section 4):
 *  - ids stay TEXT uuids, so Firestore ids, Ping notes and paired-transaction links survive the import;
 *  - money is Long paise, never REAL; times are epoch milliseconds;
 *  - enums are stored as the explicit codes from core:model, never by name or ordinal;
 *  - list columns are JSON text (Phase 5 may normalise budget filters into join tables).
 * No foreign-key constraints: a Paddhu file can hold dangling references, the importer reports them
 * instead of failing, and nothing is dropped.
 */

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colour: String?,
    val iconName: String?,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val sortOrder: Int,
    val currency: String?,
    val currencyFormat: String?,
    val decimals: Int,
    val homeWidgetsJson: String?,
    val ownerUid: String?,
    val archived: Boolean = false, // new in Chitta (audit C4)
)

@Entity(tableName = "category", indices = [Index("mainCategoryId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colour: String?,
    val iconName: String?,
    val emojiIconName: String?,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val sortOrder: Int,
    val income: Boolean,
    val methodAddedCode: Int?,
    val mainCategoryId: String?,
    val archived: Boolean = false,
)

@Entity(
    tableName = "txn",
    indices = [Index("accountId"), Index("categoryId"), Index("dateMs"), Index("pairedTransactionId"), Index("objectiveId"), Index("objectiveLoanId")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val pairedTransactionId: String?,
    val name: String,
    val amountPaise: Long,
    val currency: String?,
    val note: String,
    val categoryId: String,
    val subCategoryId: String?,
    val accountId: String,
    val dateMs: Long,
    val modifiedAtMs: Long?,
    val originalDueMs: Long?,
    val income: Boolean,
    val periodLength: Int?,
    val repeatCode: Int?,
    val endDateMs: Long?,
    val upcomingNotification: Boolean?,
    val kindCode: Int?, // null = a normal entry
    val paid: Boolean,
    val createdAnotherFuture: Boolean?,
    val skipPaid: Boolean,
    val methodAddedCode: Int?,
    val objectiveId: String?,
    val objectiveLoanId: String?,
    val excludeFromBudgetIdsJson: String?,
    val ownerUid: String?,
    val isPrivate: Boolean,
    val householdSyncedAtMs: Long?,
    // Added in schema 2 (exchange): what was typed in another currency, and the rate used.
    val originalAmountPaise: Long? = null,
    val originalCurrency: String? = null,
    val exchangeRate: String? = null,
    // Added in schema 3 (Phase 5): the day of month a monthly or yearly bill belongs on (a bill on the 31st).
    val anchorDay: Int? = null,
)

@Entity(tableName = "budget")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountPaise: Long,
    val colour: String?,
    val startMs: Long,
    val endMs: Long,
    val accountIdsJson: String?,
    val categoryIdsJson: String?,
    val excludedCategoryIdsJson: String?,
    val income: Boolean,
    val archived: Boolean,
    val addedTransactionsOnly: Boolean,
    val periodLength: Int,
    val repeatCode: Int?,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val pinned: Boolean,
    val sortOrder: Int,
    val accountId: String,
    val filtersJson: String?,
    val memberFiltersJson: String?,
    val absoluteSpendingLimit: Boolean,
)

@Entity(tableName = "category_budget_limit", indices = [Index("budgetId"), Index("categoryId")])
data class CategoryLimitEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val budgetId: String,
    val amountPaise: Long,
    val modifiedAtMs: Long?,
    val accountId: String,
)

@Entity(tableName = "objective")
data class ObjectiveEntity(
    @PrimaryKey val id: String,
    val kindCode: Int,
    val name: String,
    val amountPaise: Long,
    val sortOrder: Int,
    val colour: String?,
    val createdAtMs: Long,
    val endDateMs: Long?,
    val modifiedAtMs: Long?,
    val iconName: String?,
    val emojiIconName: String?,
    val income: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val accountId: String,
)

@Entity(tableName = "title_memory", indices = [Index("categoryId")])
data class TitleMemoryEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val title: String,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val sortOrder: Int,
    val exactMatch: Boolean,
)

@Entity(tableName = "scan_template")
data class ScanTemplateEntity(
    @PrimaryKey val id: String,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val name: String,
    val contains: String,
    val titleBefore: String,
    val titleAfter: String,
    val amountBefore: String,
    val amountAfter: String,
    val defaultCategoryId: String,
    val accountId: String,
    val ignore: Boolean,
)

@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val entityCode: Int,
    val atMs: Long,
)

@Entity(tableName = "setting")
data class SettingEntity(@PrimaryKey val key: String, val json: String)

@Entity(tableName = "loan_terms")
data class LoanTermsEntity(
    @PrimaryKey val objectiveId: String,
    val principalPaise: Long,
    val annualRatePct: String,
    val months: Int,
    val emiPaise: Long,
    val firstEmiMs: Long,
    val feePaise: Long,
    val gstPct: String,
    val feePaid: Boolean,
    val paidInstalmentsJson: String,
    val accountId: String?,
    val note: String,
    val updatedAtMs: Long?,
)

@Entity(tableName = "fixed_deposit")
data class FixedDepositEntity(
    @PrimaryKey val id: String,
    val bank: String,
    val fdNumber: String,
    val principalPaise: Long,
    val ratePct: String,
    val startMs: Long,
    val maturityMs: Long,
    val compoundingPerYear: Int,
    val maturityAmountPaise: Long?,
    val accountId: String?,
    val closed: Boolean,
    val note: String,
    val fromSms: Boolean,
    val smsId: String,
    val updatedAtMs: Long?,
)

@Entity(tableName = "card_due", indices = [Index("card")])
data class CardDueEntity(
    @PrimaryKey val id: String,
    val card: String,
    val kind: String,
    val amountPaise: Long,
    val dateMs: Long,
    val note: String,
    val updatedAtMs: Long?,
)

// ---- Added in schema 2 (Phase 4) ----

@Entity(tableName = "pinned_entry")
data class PinnedEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amountPaise: Long?,
    val income: Boolean,
    val categoryId: String,
    val accountId: String,
    val sortOrder: Int,
    val createdAtMs: Long,
)

/** One saved version of an entry. Rows are only ever added, never changed or removed (master section 13). */
@Entity(tableName = "entry_version", indices = [Index("entryId")])
data class EntryVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    val actionCode: Int,
    val atMs: Long,
    val snapshotJson: String,
)

/** Latest daily rates: units of the base currency's rate table per one unit of [currency] is derived from these. */
@Entity(tableName = "exchange_rate")
data class ExchangeRateEntity(
    @PrimaryKey val currency: String, // upper-case code, e.g. "USD"
    val perBase: String,              // decimal text: units of this currency per 1 unit of the base
    val base: String,                 // e.g. "INR"
    val asOfDate: String,             // the file's own date, e.g. "2026-11-12"
    val fetchedAtMs: Long,
)

/** A rate the person typed, which wins over the daily rate for that pair. */
@Entity(tableName = "rate_override", primaryKeys = ["fromCurrency", "toCurrency"])
data class RateOverrideEntity(
    val fromCurrency: String,
    val toCurrency: String,
    val rate: String,
    val setAtMs: Long,
)
