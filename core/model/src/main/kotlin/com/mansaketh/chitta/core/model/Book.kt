package com.mansaketh.chitta.core.model

/**
 * What the Paddhu importer produces and the database stores. Plain data, no Android, no JSON.
 * Money is `Long` paise (master prompt section 2, rule 4). Times are epoch milliseconds (Drift stores seconds).
 *
 * Paddhu's upstream `shared*` columns (shared budgets, Firestore sharing from Cashew) are not carried over
 * (DECISIONS D10); the dry run counts how many rows had them so nothing disappears silently.
 */
data class Book(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val categoryLimits: List<CategoryLimit>,
    val objectives: List<Objective>,
    val titleMemory: List<TitleMemory>,
    val scanTemplates: List<ScanTemplate>,
    val activityLog: List<ActivityEntry>,
    val settings: List<Setting>,
    val loanTerms: List<LoanTerms>,
    val fixedDeposits: List<FixedDeposit>,
    val cardDues: List<CardDue>,
)

data class Account(
    val id: String,
    val name: String,
    val colour: String?,
    val iconName: String?,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val sortOrder: Int,
    val currency: String?,
    val currencyFormat: String?,
    val decimals: Int,
    val homeWidgets: List<AccountWidget>?,
    val ownerUid: String?,
)

data class Category(
    val id: String,
    val name: String,
    val colour: String?,
    val iconName: String?,
    val emojiIconName: String?,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val sortOrder: Int,
    val income: Boolean,
    val methodAdded: MethodAdded?,
    val mainCategoryId: String?, // null = main category, set = subcategory
)

data class Transaction(
    val id: String,
    val pairedTransactionId: String?,
    val name: String,
    val amount: Long,            // paise, as stored in Paddhu (sign convention is kept, see [income])
    val currency: String?,       // copied from the account at import so the amount keeps its currency
    val note: String,
    val categoryId: String,
    val subCategoryId: String?,
    val accountId: String,
    val dateMs: Long,
    val modifiedAtMs: Long?,
    val originalDueMs: Long?,
    val income: Boolean,
    val periodLength: Int?,
    val repeat: Repeat?,
    val endDateMs: Long?,
    val upcomingNotification: Boolean?,
    val kind: TransactionKind?,  // null = a normal entry
    val paid: Boolean,
    val createdAnotherFuture: Boolean?,
    val skipPaid: Boolean,
    val methodAdded: MethodAdded?,
    val objectiveId: String?,
    val objectiveLoanId: String?,
    val excludeFromBudgetIds: List<String>,
    val ownerUid: String?,
    val isPrivate: Boolean,
    val householdSyncedAtMs: Long?,
)

data class Budget(
    val id: String,
    val name: String,
    val amount: Long,
    val colour: String?,
    val startMs: Long,
    val endMs: Long,
    val accountIds: List<String>?,       // null = all accounts
    val categoryIds: List<String>?,      // null = all categories
    val excludedCategoryIds: List<String>?,
    val income: Boolean,
    val archived: Boolean,
    val addedTransactionsOnly: Boolean,
    val periodLength: Int,
    val repeat: Repeat?,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val pinned: Boolean,
    val sortOrder: Int,
    val accountId: String,
    val filters: List<BudgetFilter>?,
    val memberFilters: List<String>?,
    val absoluteSpendingLimit: Boolean,
)

data class CategoryLimit(
    val id: String,
    val categoryId: String,
    val budgetId: String,
    val amount: Long,
    val modifiedAtMs: Long?,
    val accountId: String,
)

data class Objective(
    val id: String,
    val kind: ObjectiveKind,
    val name: String,
    val amount: Long,
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

data class TitleMemory(
    val id: String,
    val categoryId: String,
    val title: String,
    val createdAtMs: Long,
    val modifiedAtMs: Long?,
    val sortOrder: Int,
    val exactMatch: Boolean,
)

data class ScanTemplate(
    val id: String,
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

data class ActivityEntry(
    val id: String,
    val entryId: String,
    val entity: ActivityEntity,
    val atMs: Long,
)

/** One app setting that survived the "not synced" filter. [json] is the raw JSON value, kept exactly. */
data class Setting(val key: String, val json: String)

/** Terms of an instalment loan; the key is the loan's objective id. Rates are decimal text, not Double. */
data class LoanTerms(
    val objectiveId: String,
    val principal: Long,
    val annualRatePct: String,
    val months: Int,
    val emi: Long,
    val firstEmiMs: Long,
    val fee: Long,
    val gstPct: String,
    val feePaid: Boolean,
    val paidInstalments: List<Int>,
    val accountId: String?,
    val note: String,
    val updatedAtMs: Long?,
)

data class FixedDeposit(
    val id: String,
    val bank: String,
    val fdNumber: String,
    val principal: Long,
    val ratePct: String,
    val startMs: Long,
    val maturityMs: Long,
    val compoundingPerYear: Int,
    val maturityAmount: Long?,
    val accountId: String?,
    val closed: Boolean,
    val note: String,
    val fromSms: Boolean,
    val smsId: String,
    val updatedAtMs: Long?,
)

/** Credit card dues from SMS. [kind] is "spend", "opening" or a payment/refund kind; see Paddhu cardDues.dart. */
data class CardDue(
    val id: String,
    val card: String,
    val kind: String,
    val amount: Long,
    val dateMs: Long,
    val note: String,
    val updatedAtMs: Long?,
)
