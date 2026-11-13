package com.mansaketh.chitta.core.model

/**
 * Every enum here has an explicit stored [code]. Paddhu's Drift file stores enums as ordinals, and the
 * order there must never be relied on by name; the codes below are the contract (Phase 0 audit, risks).
 */

/** Paddhu `transactions.type`. A NULL type in the file is a normal entry and maps to `null`, not to a value here. */
enum class TransactionKind(val code: Int) {
    UPCOMING(0),
    SUBSCRIPTION(1),
    REPETITIVE(2),
    LENT(3),      // Paddhu "credit": money you lent, owed to you
    BORROWED(4);  // Paddhu "debt": money you borrowed, owed by you

    companion object {
        fun fromCode(code: Int?): TransactionKind? = code?.let { c -> entries.firstOrNull { it.code == c } }
    }
}

/** Paddhu `reoccurrence` on transactions and budgets (BudgetReoccurence). */
enum class Repeat(val code: Int) {
    CUSTOM(0), DAILY(1), WEEKLY(2), MONTHLY(3), YEARLY(4);

    companion object {
        fun fromCode(code: Int?): Repeat? = code?.let { c -> entries.firstOrNull { it.code == c } }
    }
}

enum class ObjectiveKind(val code: Int) {
    GOAL(0),
    LOAN(1); // with income = true it is lent, otherwise borrowed

    companion object {
        fun fromCode(code: Int?): ObjectiveKind? = code?.let { c -> entries.firstOrNull { it.code == c } }
    }
}

/** Paddhu `BudgetTransactionFilters`, stored in budgets as a JSON list of ordinals. */
enum class BudgetFilter(val code: Int) {
    ADDED_TO_OTHER_BUDGET(0),
    SHARED_TO_OTHER_BUDGET(1),
    INCLUDE_INCOME(2),
    INCLUDE_DEBT_AND_CREDIT(3),
    ADDED_TO_OBJECTIVE(4),
    DEFAULT_FILTERS(5),
    INCLUDE_BALANCE_CORRECTION(6);

    companion object {
        fun fromCode(code: Int): BudgetFilter? = entries.firstOrNull { it.code == code }
    }
}

/** Paddhu `HomePageWidgetDisplay`, stored on accounts as a JSON list of ordinals. */
enum class AccountWidget(val code: Int) {
    WALLET_SWITCHER(0), WALLET_LIST(1), NET_WORTH(2), ALL_SPENDING_SUMMARY(3), PIE_CHART(4);

    companion object {
        fun fromCode(code: Int): AccountWidget? = entries.firstOrNull { it.code == code }
    }
}

/** Paddhu `DeleteLogType` (the `type` column of delete_logs). */
enum class ActivityEntity(val code: Int) {
    ACCOUNT(0), CATEGORY(1), BUDGET(2), CATEGORY_BUDGET_LIMIT(3),
    TRANSACTION(4), TITLE_MEMORY(5), SCAN_TEMPLATE(6), OBJECTIVE(7),
    UNUSED(8); // Paddhu kept this slot after retiring a scanner-template log type

    companion object {
        fun fromCode(code: Int): ActivityEntity? = entries.firstOrNull { it.code == code }
    }
}

/** How an entry got into the app (Paddhu MethodAdded). */
enum class MethodAdded(val code: Int) {
    EMAIL(0), SHARED(1), CSV(2), PREVIEW(3), APP_LINK(4);

    companion object {
        fun fromCode(code: Int?): MethodAdded? = code?.let { c -> entries.firstOrNull { it.code == c } }
    }
}
