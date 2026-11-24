package com.mansaketh.chitta.shots

import androidx.test.core.app.ApplicationProvider
import com.mansaketh.chitta.ChittaApp
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.database.toEntity
import kotlinx.coroutines.runBlocking

/** An invented book (no real data) so every screen has something to show. Amounts are paise. */
object Seed {
    private const val DAY = 86_400_000L

    fun book() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ChittaApp>()
        val now = System.currentTimeMillis()
        app.prefs.firstRunDone = true
        // The app's one open database (read from the ledger repository) so the screens see what is written here.
        val db = com.mansaketh.chitta.core.database.LedgerRepository::class.java.getDeclaredField("db").apply { isAccessible = true }.get(app.ledger) as com.mansaketh.chitta.core.database.ChittaDatabase
        fun acc(id: String, name: String, order: Int, limit: Long? = null) =
            Account(id, name, null, null, now - 90 * DAY, null, order, "INR", null, 2, null, null, creditLimit = limit, statementDay = if (limit != null) 5 else null, dueDay = if (limit != null) 25 else null)
        val accRepo = com.mansaketh.chitta.core.database.AccountRepository(db)
        accRepo.save(acc("a-hdfc", "HDFC Bank", 0)); accRepo.save(acc("a-cash", "Cash", 1)); accRepo.save(acc("a-card", "Scapia card", 2, 15_000_000))
        val spend = listOf("Food & dining", "Groceries", "Transport", "Bills", "Rent", "Shopping", "Health", "Entertainment", "Travel", "Other")
        val earn = listOf("Salary", "Other income")
        fun cat(i: Int, n: String, income: Boolean) = Category("cat-${if (income) "in" else "out"}-$i", n, null, null, null, now, null, i, income, null, null)
        com.mansaketh.chitta.core.database.CategoryWriter(db).saveAll(spend.mapIndexed { i, n -> cat(i, n, false) } + earn.mapIndexed { i, n -> cat(i, n, true) })
        var n = 0
        fun tx(daysAgo: Int, name: String, rupees: Long, catId: String, acct: String = "a-hdfc", income: Boolean = false) = Transaction(
            id = "t${n++}", pairedTransactionId = null, name = name, amount = (if (income) rupees else -rupees) * 100, currency = "INR", note = "", categoryId = catId, subCategoryId = null,
            accountId = acct, dateMs = now - daysAgo * DAY, modifiedAtMs = now, originalDueMs = null, income = income, periodLength = null, repeat = null,
            endDateMs = null, upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null,
            objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
            originalAmount = null, originalCurrency = null, exchangeRate = null,
        )
        val rows = listOf(
            tx(20, "Salary", 85_000, "cat-in-0", income = true),
            tx(19, "Rent", 18_000, "cat-out-4"), tx(18, "Big Basket", 2_340, "cat-out-1"), tx(17, "Swiggy", 486, "cat-out-0", "a-card"),
            tx(15, "Metro card", 500, "cat-out-2", "a-cash"), tx(13, "Electricity", 1_420, "cat-out-3"), tx(12, "Zomato", 312, "cat-out-0", "a-card"),
            tx(10, "Apollo Pharmacy", 640, "cat-out-6"), tx(8, "Amazon", 1_899, "cat-out-5", "a-card"), tx(7, "Cinema", 700, "cat-out-7", "a-card"),
            tx(5, "Big Basket", 1_760, "cat-out-1"), tx(4, "Ola", 240, "cat-out-2", "a-cash"), tx(3, "Swiggy", 620, "cat-out-0", "a-card"),
            tx(2, "Cafe Coffee Day", 280, "cat-out-0", "a-cash"), tx(1, "Broadband", 999, "cat-out-3"), tx(0, "Dmart", 1_120, "cat-out-1"),
        )
        app.ledger.add(rows)
        val zone = java.time.ZoneId.systemDefault(); val ym = java.time.YearMonth.now(zone)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(); val end = ym.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val budget = com.mansaketh.chitta.core.model.Budget("b-month", "November budget", 6_000_000, null, start, end, null, null, null, false, false, false, 1,
            com.mansaketh.chitta.core.model.Repeat.MONTHLY, now, null, true, 0, "a-hdfc", listOf(com.mansaketh.chitta.core.model.BudgetFilter.DEFAULT_FILTERS), null, false)
        val weekly = budget.copy(id = "b-food", name = "Food & dining", amount = 180_000, repeat = com.mansaketh.chitta.core.model.Repeat.WEEKLY, periodLength = 1, categoryIds = listOf("cat-out-0"), sortOrder = 1)
        db.budgets().insertAll(listOf(budget.toEntity(), weekly.toEntity()))
        // Goals: money moved into a goal is an entry linked to it.
        val endGoal = now + 330 * DAY
        db.objectives().insertAll(listOf(
            com.mansaketh.chitta.core.model.Objective("g1", com.mansaketh.chitta.core.model.ObjectiveKind.GOAL, "Emergency fund", 30_000_000, 0, null, now, endGoal, null, null, null, false, false, false, "a-hdfc").toEntity(),
            com.mansaketh.chitta.core.model.Objective("g2", com.mansaketh.chitta.core.model.ObjectiveKind.GOAL, "Goa trip", 4_000_000, 1, null, now, now + 40 * DAY, null, null, null, false, false, false, "a-hdfc").toEntity(),
        ))
        app.ledger.add(listOf(
            tx(6, "Emergency fund", 13_000, "cat-out-9").copy(objectiveId = "g1"),
            tx(9, "Goa trip", 31_500, "cat-out-8").copy(objectiveId = "g2"),
            // One-time loans and upcoming bills
            tx(11, "Kiran", 7_500, "cat-out-9").copy(kind = com.mansaketh.chitta.core.model.TransactionKind.LENT, income = false),
            tx(14, "Dad", 50_000, "cat-in-1", income = true).copy(kind = com.mansaketh.chitta.core.model.TransactionKind.BORROWED),
            tx(-1, "ICICI card bill", 12_480, "cat-out-3", "a-card").copy(kind = com.mansaketh.chitta.core.model.TransactionKind.UPCOMING, paid = false, originalDueMs = now + DAY),
            tx(-4, "Netflix", 649, "cat-out-7", "a-card").copy(kind = com.mansaketh.chitta.core.model.TransactionKind.SUBSCRIPTION, paid = false, originalDueMs = now + 4 * DAY, periodLength = 1, repeat = com.mansaketh.chitta.core.model.Repeat.MONTHLY),
            tx(2, "Electricity", 1_240, "cat-out-3").copy(kind = com.mansaketh.chitta.core.model.TransactionKind.UPCOMING, paid = false, originalDueMs = now - 2 * DAY),
        ))
        // Rules and mutual funds, so those screens can be set beside the PDF.
        val rs = { id: String, n: String, c: List<com.mansaketh.chitta.core.domain.RuleCondition>, a: com.mansaketh.chitta.core.domain.RuleActions, o: Int ->
            com.mansaketh.chitta.core.domain.Rule(id, n, c, a, o).toEntity() }
        db.rules().upsert(rs("r1", "Swiggy or Zomato", listOf(com.mansaketh.chitta.core.domain.RuleCondition(com.mansaketh.chitta.core.domain.RuleField.Title, com.mansaketh.chitta.core.domain.RuleOp.Contains, "swiggy")), com.mansaketh.chitta.core.domain.RuleActions(categoryId = "cat-out-0"), 0))
        db.rules().upsert(rs("r2", "Uber", listOf(com.mansaketh.chitta.core.domain.RuleCondition(com.mansaketh.chitta.core.domain.RuleField.Title, com.mansaketh.chitta.core.domain.RuleOp.Contains, "uber")), com.mansaketh.chitta.core.domain.RuleActions(categoryId = "cat-out-2"), 1))
        fun fund(id: String, n: String, units: String, inv: Long, nav: String, src: String) = com.mansaketh.chitta.core.model.MutualFund(id, n, "", "", units, inv, src, now, nav, "2026-11-11", "", now)
        db.misc().upsertFund(fund("f1", "Parag Parikh Flexi Cap", "842.610", 5_900_000, "93.0", "Zerodha").toEntity())
        db.misc().upsertFund(fund("f2", "UTI Nifty 50 Index", "3120.450", 5_000_000, "19.6", "Zerodha").toEntity())
        db.misc().upsertFund(fund("f3", "Axis Bluechip", "368.900", 2_000_000, "66.5", "Excel report").toEntity())
    }
}
