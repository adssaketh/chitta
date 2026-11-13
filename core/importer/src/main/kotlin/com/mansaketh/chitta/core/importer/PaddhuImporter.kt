package com.mansaketh.chitta.core.importer

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.AccountWidget
import com.mansaketh.chitta.core.model.ActivityEntity
import com.mansaketh.chitta.core.model.ActivityEntry
import com.mansaketh.chitta.core.model.Book
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.CategoryLimit
import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.ScanTemplate
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.paiseFromDriftDouble
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.time.ZoneId

/**
 * Reads a Paddhu Pusthakam backup (Cashew's Drift SQLite file, schema 33 to 48) into a [Book].
 * Pure reading: nothing is written anywhere, so this is also the dry run (master section 13, Migration).
 *
 * Rules that matter:
 *  - enums are mapped by explicit code, never by name; an unknown code is an ERROR on that row, not a guess;
 *  - a NULL `transactions.type` is a normal entry (confirmed on a real backup: all rows had NULL);
 *  - Drift dates are epoch seconds; the book uses milliseconds;
 *  - amounts go double -> shortest decimal -> paise, HALF_EVEN; every value that moved is counted.
 */
class PaddhuImporter(private val zone: ZoneId = ZoneId.of("Asia/Kolkata")) {

    fun read(source: BackupSource): ImportOutcome {
        val version = source.userVersion()
        if (version !in MIN_VERSION..MAX_VERSION) {
            return ImportOutcome.Refused(
                if (version < MIN_VERSION) "This backup is from an older Paddhu Pusthakam (schema $version). Open it in Paddhu Pusthakam first so it updates, then export again."
                else "This backup is from a newer app than this one supports (schema $version, up to $MAX_VERSION)."
            )
        }
        val missing = REQUIRED_TABLES.filterNot(source::hasTable)
        if (missing.isNotEmpty()) {
            return ImportOutcome.Refused("This file is not a Paddhu Pusthakam backup: missing ${missing.joinToString()}.")
        }
        return readBook(source, version)
    }

    private fun readBook(source: BackupSource, version: Int): ImportOutcome.Ready {
        val ctx = Ctx()
        val walletRows = source.rows("wallets")
        val accounts = walletRows.mapNotNull { ctx.account(it) }
        val accountById = accounts.associateBy { it.id }
        ctx.accountById = accountById

        val categoryRows = source.rows("categories")
        val categories = categoryRows.mapNotNull { ctx.category(it) }
        val txRows = source.rows("transactions")
        val transactions = txRows.mapNotNull { ctx.transaction(it) }
        val budgetRows = source.rows("budgets")
        val budgets = budgetRows.mapNotNull { ctx.budget(it) }
        val limitRows = source.rows("category_budget_limits")
        val limits = limitRows.mapNotNull { ctx.limit(it) }
        val objectiveRows = source.rows("objectives")
        val objectives = objectiveRows.mapNotNull { ctx.objective(it) }
        val titleRows = source.rows("associated_titles")
        val titles = titleRows.mapNotNull { ctx.title(it) }
        val templateRows = if (source.hasTable("scanner_templates")) source.rows("scanner_templates") else emptyList()
        val templates = templateRows.mapNotNull { ctx.template(it) }
        val logRows = if (source.hasTable("delete_logs")) source.rows("delete_logs") else emptyList()
        val log = logRows.mapNotNull { ctx.log(it) }

        val settingsRows = source.rows("app_settings")
        // Paddhu keeps a single row; if there are several, the newest wins.
        val blob = settingsRows.maxByOrNull { it.long("date_updated") ?: 0L }?.str("settings_j_s_o_n")
        val parsed = parseSettingsBlob(blob, zone)
        ctx.issues += parsed.issues

        ctx.checkReferences(accounts, categories, transactions, budgets, limits, objectives, titles, templates, parsed.loanTerms, parsed.fixedDeposits)

        val book = Book(
            accounts = accounts, categories = categories, transactions = transactions, budgets = budgets,
            categoryLimits = limits, objectives = objectives, titleMemory = titles, scanTemplates = templates,
            activityLog = log, settings = parsed.settings, loanTerms = parsed.loanTerms,
            fixedDeposits = parsed.fixedDeposits, cardDues = parsed.cardDues,
        )
        val counts = listOf(
            TableCount("wallets", walletRows.size, accounts.size),
            TableCount("categories", categoryRows.size, categories.size),
            TableCount("transactions", txRows.size, transactions.size),
            TableCount("budgets", budgetRows.size, budgets.size),
            TableCount("category_budget_limits", limitRows.size, limits.size),
            TableCount("objectives", objectiveRows.size, objectives.size),
            TableCount("associated_titles", titleRows.size, titles.size),
            TableCount("scanner_templates", templateRows.size, templates.size),
            TableCount("delete_logs", logRows.size, log.size),
            TableCount("settings (kept)", parsed.settings.size + parsed.skippedDeviceKeys, parsed.settings.size),
            TableCount("loan terms", parsed.loanTerms.size, parsed.loanTerms.size),
            TableCount("fixed deposits", parsed.fixedDeposits.size, parsed.fixedDeposits.size),
            TableCount("card dues", parsed.cardDues.size, parsed.cardDues.size),
        )
        val dates = transactions.map { it.dateMs }
        val report = Report(
            schemaVersion = version,
            counts = counts,
            firstTransactionMs = dates.minOrNull(),
            lastTransactionMs = dates.maxOrNull(),
            movedAmounts = ctx.moved,
            notCarriedOver = ctx.notCarried.filterValues { it > 0 } + (if (parsed.skippedDeviceKeys > 0) mapOf("settings: device-only keys" to parsed.skippedDeviceKeys) else emptyMap()),
            accountChecks = ctx.accountChecks(accounts, txRows, transactions),
            issues = ctx.issues,
        )
        return ImportOutcome.Ready(book, report)
    }

    // ---- per-run state and row mappers -------------------------------------------------------------

    private inner class Ctx {
        val issues = mutableListOf<Issue>()
        var moved = 0
        val notCarried = linkedMapOf<String, Int>()
        var accountById: Map<String, Account> = emptyMap()

        fun err(table: String, id: String?, msg: String) { issues += Issue(Severity.ERROR, table, id, msg) }
        fun warn(table: String, id: String?, msg: String) { issues += Issue(Severity.WARNING, table, id, msg) }
        fun skip(key: String) { notCarried[key] = (notCarried[key] ?: 0) + 1 }

        fun ms(row: Row, key: String): Long? = row.long(key)?.let { secondsToMs(it) }

        fun paise(table: String, id: String?, row: Row, key: String): Long? {
            val d = row.double(key) ?: return null
            if (!d.isFinite()) { err(table, id, "$key is not a finite number."); return null }
            val c = paiseFromDriftDouble(d)
            if (c.moved) { moved++; warn(table, id, "$key $d is not a whole number of paise; rounded to ${c.paise.value} paise.") }
            return c.paise.value
        }

        fun stringList(table: String, id: String?, row: Row, key: String): List<String>? {
            val text = row.str(key) ?: return null
            return try {
                (Json.parseToJsonElement(text) as JsonArray).map { (it as JsonPrimitive).content }
            } catch (e: Exception) {
                err(table, id, "$key is not a JSON list of text; ignored."); null
            }
        }

        fun intList(table: String, id: String?, row: Row, key: String): List<Int>? {
            val text = row.str(key) ?: return null
            return try {
                (Json.parseToJsonElement(text) as JsonArray).map { (it as JsonPrimitive).contentOrNull!!.toDouble().toInt() }
            } catch (e: Exception) {
                err(table, id, "$key is not a JSON list of numbers; ignored."); null
            }
        }

        fun account(r: Row): Account? {
            val id = r.str("wallet_pk") ?: return null.also { err("wallets", null, "A wallet has no id; left out.") }
            val decimals = r.int("decimals") ?: 2
            if (decimals != 2) warn("wallets", id, "Account uses $decimals decimals. Amounts are stored as paise (2 decimals) whatever this says; check this account's figures.")
            val widgets = intList("wallets", id, r, "home_page_widget_display")?.mapNotNull { code ->
                AccountWidget.fromCode(code) ?: null.also { warn("wallets", id, "Unknown home widget code $code; ignored.") }
            }
            return Account(
                id = id, name = r.strOrEmpty("name"), colour = r.str("colour"), iconName = r.str("icon_name"),
                createdAtMs = ms(r, "date_created") ?: 0L, modifiedAtMs = ms(r, "date_time_modified"),
                sortOrder = r.int("order") ?: 0, currency = r.str("currency"), currencyFormat = r.str("currency_format"),
                decimals = decimals, homeWidgets = widgets, ownerUid = r.str("owner_uid"),
            )
        }

        fun category(r: Row): Category? {
            val id = r.str("category_pk") ?: return null.also { err("categories", null, "A category has no id; left out.") }
            return Category(
                id = id, name = r.strOrEmpty("name"), colour = r.str("colour"), iconName = r.str("icon_name"),
                emojiIconName = r.str("emoji_icon_name"), createdAtMs = ms(r, "date_created") ?: 0L,
                modifiedAtMs = ms(r, "date_time_modified"), sortOrder = r.int("order") ?: 0, income = r.bool("income"),
                methodAdded = methodAdded("categories", id, r), mainCategoryId = r.str("main_category_pk"),
            )
        }

        fun methodAdded(table: String, id: String?, r: Row): MethodAdded? {
            val code = r.int("method_added") ?: return null
            return MethodAdded.fromCode(code) ?: null.also { warn(table, id, "Unknown method_added code $code; ignored.") }
        }

        fun transaction(r: Row): Transaction? {
            val id = r.str("transaction_pk") ?: return null.also { err("transactions", null, "A transaction has no id; left out.") }
            val typeCode = r.int("type")
            val kind = TransactionKind.fromCode(typeCode)
            if (typeCode != null && kind == null) { err("transactions", id, "Unknown transaction type code $typeCode; left out so it is not misread."); return null }
            val repeatCode = r.int("reoccurrence")
            val repeat = Repeat.fromCode(repeatCode)
            if (repeatCode != null && repeat == null) { err("transactions", id, "Unknown repeat code $repeatCode; left out."); return null }
            val amount = paise("transactions", id, r, "amount") ?: return null.also { err("transactions", id, "Transaction has no amount; left out.") }
            val date = ms(r, "date_created") ?: return null.also { err("transactions", id, "Transaction has no date; left out.") }
            if (listOf("shared_key", "shared_old_key", "shared_status", "shared_reference_budget_pk", "transaction_owner_email", "transaction_original_owner_email").any { r[it] != null }) {
                skip("transactions: Cashew shared-budget columns")
            }
            val accountId = r.strOrEmpty("wallet_fk")
            return Transaction(
                id = id, pairedTransactionId = r.str("paired_transaction_fk"), name = r.strOrEmpty("name"), amount = amount,
                currency = accountById[accountId]?.currency, note = r.strOrEmpty("note"),
                categoryId = r.strOrEmpty("category_fk"), subCategoryId = r.str("sub_category_fk"), accountId = accountId,
                dateMs = date, modifiedAtMs = ms(r, "date_time_modified"), originalDueMs = ms(r, "original_date_due"),
                income = r.bool("income"), periodLength = r.int("period_length"), repeat = repeat, endDateMs = ms(r, "end_date"),
                upcomingNotification = r.boolOrNull("upcoming_transaction_notification"), kind = kind, paid = r.bool("paid"),
                createdAnotherFuture = r.boolOrNull("created_another_future_transaction"), skipPaid = r.bool("skip_paid"),
                methodAdded = methodAdded("transactions", id, r), objectiveId = r.str("objective_fk"),
                objectiveLoanId = r.str("objective_loan_fk"),
                excludeFromBudgetIds = stringList("transactions", id, r, "budget_fks_exclude") ?: emptyList(),
                ownerUid = r.str("owner_uid"), isPrivate = r.bool("is_private"), householdSyncedAtMs = ms(r, "household_synced_at"),
            )
        }

        fun budget(r: Row): Budget? {
            val id = r.str("budget_pk") ?: return null.also { err("budgets", null, "A budget has no id; left out.") }
            val repeatCode = r.int("reoccurrence")
            val repeat = Repeat.fromCode(repeatCode)
            if (repeatCode != null && repeat == null) { err("budgets", id, "Unknown repeat code $repeatCode; left out."); return null }
            val amount = paise("budgets", id, r, "amount") ?: return null.also { err("budgets", id, "Budget has no amount; left out.") }
            if (listOf("shared_key", "shared_owner_member", "shared_members", "shared_all_members_ever").any { r[it] != null }) skip("budgets: Cashew shared-budget columns")
            val filters = intList("budgets", id, r, "budget_transaction_filters")?.mapNotNull { code ->
                BudgetFilter.fromCode(code) ?: null.also { warn("budgets", id, "Unknown budget filter code $code; ignored.") }
            }
            return Budget(
                id = id, name = r.strOrEmpty("name"), amount = amount, colour = r.str("colour"),
                startMs = ms(r, "start_date") ?: 0L, endMs = ms(r, "end_date") ?: 0L,
                accountIds = stringList("budgets", id, r, "wallet_fks"), categoryIds = stringList("budgets", id, r, "category_fks"),
                excludedCategoryIds = stringList("budgets", id, r, "category_fks_exclude"), income = r.bool("income"),
                archived = r.bool("archived"), addedTransactionsOnly = r.bool("added_transactions_only"),
                periodLength = r.int("period_length") ?: 0, repeat = repeat, createdAtMs = ms(r, "date_created") ?: 0L,
                modifiedAtMs = ms(r, "date_time_modified"), pinned = r.bool("pinned"), sortOrder = r.int("order") ?: 0,
                accountId = r.strOrEmpty("wallet_fk"), filters = filters,
                memberFilters = stringList("budgets", id, r, "member_transaction_filters"),
                absoluteSpendingLimit = r.bool("is_absolute_spending_limit"),
            )
        }

        fun limit(r: Row): CategoryLimit? {
            val id = r.str("category_limit_pk") ?: return null.also { err("category_budget_limits", null, "A category limit has no id; left out.") }
            val amount = paise("category_budget_limits", id, r, "amount") ?: return null.also { err("category_budget_limits", id, "No amount; left out.") }
            return CategoryLimit(id, r.strOrEmpty("category_fk"), r.strOrEmpty("budget_fk"), amount, ms(r, "date_time_modified"), r.strOrEmpty("wallet_fk"))
        }

        fun objective(r: Row): Objective? {
            val id = r.str("objective_pk") ?: return null.also { err("objectives", null, "A goal or loan has no id; left out.") }
            val kind = ObjectiveKind.fromCode(r.int("type")) ?: return null.also { err("objectives", id, "Unknown goal/loan type ${r.int("type")}; left out.") }
            val amount = paise("objectives", id, r, "amount") ?: return null.also { err("objectives", id, "No amount; left out.") }
            return Objective(
                id = id, kind = kind, name = r.strOrEmpty("name"), amount = amount, sortOrder = r.int("order") ?: 0,
                colour = r.str("colour"), createdAtMs = ms(r, "date_created") ?: 0L, endDateMs = ms(r, "end_date"),
                modifiedAtMs = ms(r, "date_time_modified"), iconName = r.str("icon_name"), emojiIconName = r.str("emoji_icon_name"),
                income = r.bool("income"), pinned = r.bool("pinned"), archived = r.bool("archived"), accountId = r.strOrEmpty("wallet_fk"),
            )
        }

        fun title(r: Row): TitleMemory? {
            val id = r.str("associated_title_pk") ?: return null.also { err("associated_titles", null, "A remembered title has no id; left out.") }
            return TitleMemory(id, r.strOrEmpty("category_fk"), r.strOrEmpty("title"), ms(r, "date_created") ?: 0L, ms(r, "date_time_modified"), r.int("order") ?: 0, r.bool("is_exact_match"))
        }

        fun template(r: Row): ScanTemplate? {
            val id = r.str("scanner_template_pk") ?: return null.also { err("scanner_templates", null, "A scan template has no id; left out.") }
            return ScanTemplate(
                id, ms(r, "date_created") ?: 0L, ms(r, "date_time_modified"), r.strOrEmpty("template_name"), r.strOrEmpty("contains"),
                r.strOrEmpty("title_transaction_before"), r.strOrEmpty("title_transaction_after"),
                r.strOrEmpty("amount_transaction_before"), r.strOrEmpty("amount_transaction_after"),
                r.strOrEmpty("default_category_fk"), r.strOrEmpty("wallet_fk"), r.bool("ignore"),
            )
        }

        fun log(r: Row): ActivityEntry? {
            val id = r.str("delete_log_pk") ?: return null
            val entity = ActivityEntity.fromCode(r.int("type") ?: -1) ?: return null.also { warn("delete_logs", id, "Unknown log type ${r.int("type")}; left out.") }
            return ActivityEntry(id, r.strOrEmpty("entry_pk"), entity, ms(r, "date_time_modified") ?: 0L)
        }

        /** Rows that point at something missing are kept but flagged: a list is better than a silent gap. */
        fun checkReferences(
            accounts: List<Account>, categories: List<Category>, transactions: List<Transaction>, budgets: List<Budget>,
            limits: List<CategoryLimit>, objectives: List<Objective>, titles: List<TitleMemory>, templates: List<ScanTemplate>,
            loans: List<com.mansaketh.chitta.core.model.LoanTerms>, deposits: List<com.mansaketh.chitta.core.model.FixedDeposit>,
        ) {
            val accountIds = accounts.map { it.id }.toSet()
            val categoryIds = categories.map { it.id }.toSet()
            val objectiveIds = objectives.map { it.id }.toSet()
            val budgetIds = budgets.map { it.id }.toSet()
            val txIds = transactions.map { it.id }.toSet()
            transactions.forEach { t ->
                if (t.accountId !in accountIds) warn("transactions", t.id, "Entry points at account '${t.accountId}', which is not in the file.")
                if (t.categoryId !in categoryIds) warn("transactions", t.id, "Entry points at category '${t.categoryId}', which is not in the file.")
                t.subCategoryId?.let { if (it !in categoryIds) warn("transactions", t.id, "Entry points at subcategory '$it', which is not in the file.") }
                t.pairedTransactionId?.let { if (it !in txIds) warn("transactions", t.id, "Paired transfer entry '$it' is not in the file.") }
                t.objectiveId?.let { if (it !in objectiveIds) warn("transactions", t.id, "Entry points at goal '$it', which is not in the file.") }
                t.objectiveLoanId?.let { if (it !in objectiveIds) warn("transactions", t.id, "Entry points at loan '$it', which is not in the file.") }
            }
            categories.forEach { c -> c.mainCategoryId?.let { if (it !in categoryIds) warn("categories", c.id, "Subcategory points at '$it', which is not in the file.") } }
            limits.forEach { l ->
                if (l.budgetId !in budgetIds) warn("category_budget_limits", l.id, "Limit points at budget '${l.budgetId}', which is not in the file.")
                if (l.categoryId !in categoryIds) warn("category_budget_limits", l.id, "Limit points at category '${l.categoryId}', which is not in the file.")
            }
            titles.forEach { t -> if (t.categoryId !in categoryIds) warn("associated_titles", t.id, "Title points at category '${t.categoryId}', which is not in the file.") }
            loans.forEach { l -> if (l.objectiveId !in objectiveIds) warn("settings.loanTerms", l.objectiveId, "Loan terms point at a loan that is not in the file.") }
            deposits.forEach { d -> d.accountId?.let { if (it !in accountIds) warn("settings.fixedDeposits", d.id, "Deposit points at account '$it', which is not in the file.") } }
        }

        fun accountChecks(accounts: List<Account>, rows: List<Row>, imported: List<Transaction>): List<AccountCheck> {
            val importedIds = imported.map { it.id }.toSet()
            val sourceByAccount = rows.filter { it.str("transaction_pk") in importedIds }.groupBy { it.strOrEmpty("wallet_fk") }
            val importedByAccount = imported.groupBy { it.accountId }
            val ids = (accounts.map { it.id } + sourceByAccount.keys + importedByAccount.keys).distinct()
            return ids.map { id ->
                val src = sourceByAccount[id].orEmpty()
                val exact = src.fold(BigDecimal.ZERO) { acc, r -> acc + BigDecimal.valueOf(r.double("amount") ?: 0.0) }
                val srcPaise = exact.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_EVEN).longValueExact()
                AccountCheck(id, accountById[id]?.name ?: "(not in file)", src.size, srcPaise, importedByAccount[id].orEmpty().sumOf { it.amount })
            }
        }
    }

    companion object {
        const val MIN_VERSION = 33
        const val MAX_VERSION = 48
        private val REQUIRED_TABLES = listOf("wallets", "categories", "transactions", "budgets", "objectives", "associated_titles", "app_settings")

        /** Drift stores DateTime as epoch seconds (confirmed on a real backup: 101 integer values, 2010 to 2026). */
        internal fun secondsToMs(seconds: Long): Long = Math.multiplyExact(seconds, 1000L)
    }
}
