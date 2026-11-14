package com.mansaketh.chitta.core.database

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.AccountWidget
import com.mansaketh.chitta.core.model.ActivityEntity
import com.mansaketh.chitta.core.model.ActivityEntry
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.CategoryLimit
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.model.NpsAccount
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.ScanTemplate
import com.mansaketh.chitta.core.model.Setting
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

// Lists are stored as JSON text. null stays null (for budgets, null means "all").
private fun List<String>?.toJson(): String? = this?.let { l -> Json.encodeToString(JsonArray.serializer(), buildJsonArray { l.forEach { add(JsonPrimitive(it)) } }) }
private fun String?.toStrings(): List<String>? = this?.let { Json.parseToJsonElement(it) as JsonArray }?.map { it.jsonPrimitive.content }
private fun List<Int>?.intsToJson(): String? = this?.let { l -> Json.encodeToString(JsonArray.serializer(), buildJsonArray { l.forEach { add(JsonPrimitive(it)) } }) }
private fun String?.toInts(): List<Int>? = this?.let { Json.parseToJsonElement(it) as JsonArray }?.map { it.jsonPrimitive.int }

fun Account.toEntity() = AccountEntity(id, name, colour, iconName, createdAtMs, modifiedAtMs, sortOrder, currency, currencyFormat, decimals, homeWidgets?.map { it.code }.intsToJson(), ownerUid, archived, creditLimit, statementDay, dueDay, minimumDue, remindDaysBefore)
fun AccountEntity.toModel() = Account(id, name, colour, iconName, createdAtMs, modifiedAtMs, sortOrder, currency, currencyFormat, decimals, homeWidgetsJson.toInts()?.mapNotNull { AccountWidget.fromCode(it) }, ownerUid, archived, creditLimitPaise, statementDay, dueDay, minimumDuePaise, remindDaysBefore)

fun Category.toEntity() = CategoryEntity(id, name, colour, iconName, emojiIconName, createdAtMs, modifiedAtMs, sortOrder, income, methodAdded?.code, mainCategoryId, archived)
fun CategoryEntity.toModel() = Category(id, name, colour, iconName, emojiIconName, createdAtMs, modifiedAtMs, sortOrder, income, MethodAdded.fromCode(methodAddedCode), mainCategoryId, archived)

fun Transaction.toEntity() = TransactionEntity(
    id, pairedTransactionId, name, amount, currency, note, categoryId, subCategoryId, accountId, dateMs, modifiedAtMs, originalDueMs,
    income, periodLength, repeat?.code, endDateMs, upcomingNotification, kind?.code, paid, createdAnotherFuture, skipPaid,
    methodAdded?.code, objectiveId, objectiveLoanId, excludeFromBudgetIds.takeIf { it.isNotEmpty() }.toJson(), ownerUid, isPrivate, householdSyncedAtMs,
    originalAmount, originalCurrency, exchangeRate, anchorDay, splitGroupId,
)
fun TransactionEntity.toModel() = Transaction(
    id, pairedTransactionId, name, amountPaise, currency, note, categoryId, subCategoryId, accountId, dateMs, modifiedAtMs, originalDueMs,
    income, periodLength, Repeat.fromCode(repeatCode), endDateMs, upcomingNotification, TransactionKind.fromCode(kindCode), paid, createdAnotherFuture, skipPaid,
    MethodAdded.fromCode(methodAddedCode), objectiveId, objectiveLoanId, excludeFromBudgetIdsJson.toStrings() ?: emptyList(), ownerUid, isPrivate, householdSyncedAtMs,
    originalAmountPaise, originalCurrency, exchangeRate, anchorDay, splitGroupId,
)

fun Budget.toEntity() = BudgetEntity(
    id, name, amount, colour, startMs, endMs, accountIds.toJson(), categoryIds.toJson(), excludedCategoryIds.toJson(), income, archived,
    addedTransactionsOnly, periodLength, repeat?.code, createdAtMs, modifiedAtMs, pinned, sortOrder, accountId,
    filters?.map { it.code }.intsToJson(), memberFilters.toJson(), absoluteSpendingLimit,
)
fun BudgetEntity.toModel() = Budget(
    id, name, amountPaise, colour, startMs, endMs, accountIdsJson.toStrings(), categoryIdsJson.toStrings(), excludedCategoryIdsJson.toStrings(), income, archived,
    addedTransactionsOnly, periodLength, Repeat.fromCode(repeatCode), createdAtMs, modifiedAtMs, pinned, sortOrder, accountId,
    filtersJson.toInts()?.mapNotNull { BudgetFilter.fromCode(it) }, memberFiltersJson.toStrings(), absoluteSpendingLimit,
)

fun CategoryLimit.toEntity() = CategoryLimitEntity(id, categoryId, budgetId, amount, modifiedAtMs, accountId)
fun CategoryLimitEntity.toModel() = CategoryLimit(id, categoryId, budgetId, amountPaise, modifiedAtMs, accountId)

fun Objective.toEntity() = ObjectiveEntity(id, kind.code, name, amount, sortOrder, colour, createdAtMs, endDateMs, modifiedAtMs, iconName, emojiIconName, income, pinned, archived, accountId)
fun ObjectiveEntity.toModel() = Objective(id, ObjectiveKind.fromCode(kindCode)!!, name, amountPaise, sortOrder, colour, createdAtMs, endDateMs, modifiedAtMs, iconName, emojiIconName, income, pinned, archived, accountId)

fun TitleMemory.toEntity() = TitleMemoryEntity(id, categoryId, title, createdAtMs, modifiedAtMs, sortOrder, exactMatch)
fun TitleMemoryEntity.toModel() = TitleMemory(id, categoryId, title, createdAtMs, modifiedAtMs, sortOrder, exactMatch)

fun ScanTemplate.toEntity() = ScanTemplateEntity(id, createdAtMs, modifiedAtMs, name, contains, titleBefore, titleAfter, amountBefore, amountAfter, defaultCategoryId, accountId, ignore)
fun ScanTemplateEntity.toModel() = ScanTemplate(id, createdAtMs, modifiedAtMs, name, contains, titleBefore, titleAfter, amountBefore, amountAfter, defaultCategoryId, accountId, ignore)

fun ActivityEntry.toEntity() = ActivityLogEntity(id, entryId, entity.code, atMs)
fun ActivityLogEntity.toModel() = ActivityEntry(id, entryId, ActivityEntity.fromCode(entityCode)!!, atMs)

fun Setting.toEntity() = SettingEntity(key, json)
fun SettingEntity.toModel() = Setting(key, json)

fun LoanTerms.toEntity() = LoanTermsEntity(objectiveId, principal, annualRatePct, months, emi, firstEmiMs, fee, gstPct, feePaid, paidInstalments.intsToJson()!!, accountId, note, updatedAtMs)
fun LoanTermsEntity.toModel() = LoanTerms(objectiveId, principalPaise, annualRatePct, months, emiPaise, firstEmiMs, feePaise, gstPct, feePaid, paidInstalmentsJson.toInts()!!, accountId, note, updatedAtMs)

fun FixedDeposit.toEntity() = FixedDepositEntity(id, bank, fdNumber, principal, ratePct, startMs, maturityMs, compoundingPerYear, maturityAmount, accountId, closed, note, fromSms, smsId, updatedAtMs)
fun FixedDepositEntity.toModel() = FixedDeposit(id, bank, fdNumber, principalPaise, ratePct, startMs, maturityMs, compoundingPerYear, maturityAmountPaise, accountId, closed, note, fromSms, smsId, updatedAtMs)

fun CardDue.toEntity() = CardDueEntity(id, card, kind, amount, dateMs, note, updatedAtMs)
fun CardDueEntity.toModel() = CardDue(id, card, kind, amountPaise, dateMs, note, updatedAtMs)

fun MutualFund.toEntity() = MutualFundEntity(id, name, isin, schemeCode, units, investedPaise, source, unitsAsOfMs, navText, navDate, note, updatedAtMs, folio, groupId, sipAmountPaise, sipDay, sipUnits, sipInvestedPaise, sipCount, sipThrough)
fun MutualFundEntity.toModel() = MutualFund(id, name, isin, schemeCode, units, investedPaise, source, unitsAsOfMs, navText, navDate, note, updatedAtMs, folio, groupId, sipAmountPaise, sipDay, sipUnits, sipInvestedPaise, sipCount, sipThrough)
fun NpsAccount.toEntity() = NpsAccountEntity(id, name, netContributionsPaise, asOfMs, note, updatedAtMs)
fun NpsAccountEntity.toModel() = NpsAccount(id, name, netContributionsPaise, asOfMs, note, updatedAtMs)
