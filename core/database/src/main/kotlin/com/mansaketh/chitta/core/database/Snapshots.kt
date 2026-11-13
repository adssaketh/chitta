package com.mansaketh.chitta.core.database

import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** An entry as JSON text, for the history table. Every field is written, so a restore brings back the whole entry. */
object Snapshots {
    fun encode(t: Transaction): String = buildJsonObject {
        put("id", t.id); put("paired", t.pairedTransactionId); put("name", t.name); put("amount", t.amount)
        put("currency", t.currency); put("note", t.note); put("category", t.categoryId); put("subCategory", t.subCategoryId)
        put("account", t.accountId); put("date", t.dateMs); put("modified", t.modifiedAtMs); put("originalDue", t.originalDueMs)
        put("income", t.income); put("periodLength", t.periodLength); put("repeat", t.repeat?.code); put("endDate", t.endDateMs)
        put("upcomingNotification", t.upcomingNotification); put("kind", t.kind?.code); put("paid", t.paid)
        put("createdAnotherFuture", t.createdAnotherFuture); put("skipPaid", t.skipPaid); put("method", t.methodAdded?.code)
        put("objective", t.objectiveId); put("objectiveLoan", t.objectiveLoanId)
        put("excludeFromBudgets", buildJsonArray { t.excludeFromBudgetIds.forEach { add(JsonPrimitive(it)) } })
        put("owner", t.ownerUid); put("private", t.isPrivate); put("householdSyncedAt", t.householdSyncedAtMs)
        put("originalAmount", t.originalAmount); put("originalCurrency", t.originalCurrency); put("exchangeRate", t.exchangeRate)
    }.toString()

    fun decode(json: String): Transaction {
        val o = Json.parseToJsonElement(json) as JsonObject
        fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        fun lng(k: String) = (o[k] as? JsonPrimitive)?.longOrNull
        fun int(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
        fun bool(k: String) = (o[k] as? JsonPrimitive)?.booleanOrNull
        return Transaction(
            id = str("id")!!, pairedTransactionId = str("paired"), name = str("name")!!, amount = lng("amount")!!, currency = str("currency"),
            note = str("note")!!, categoryId = str("category")!!, subCategoryId = str("subCategory"), accountId = str("account")!!,
            dateMs = lng("date")!!, modifiedAtMs = lng("modified"), originalDueMs = lng("originalDue"), income = bool("income")!!,
            periodLength = int("periodLength"), repeat = Repeat.fromCode(int("repeat")), endDateMs = lng("endDate"),
            upcomingNotification = bool("upcomingNotification"), kind = TransactionKind.fromCode(int("kind")), paid = bool("paid")!!,
            createdAnotherFuture = bool("createdAnotherFuture"), skipPaid = bool("skipPaid")!!, methodAdded = MethodAdded.fromCode(int("method")),
            objectiveId = str("objective"), objectiveLoanId = str("objectiveLoan"),
            excludeFromBudgetIds = (o["excludeFromBudgets"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
            ownerUid = str("owner"), isPrivate = bool("private")!!, householdSyncedAtMs = lng("householdSyncedAt"),
            originalAmount = lng("originalAmount"), originalCurrency = str("originalCurrency"), exchangeRate = str("exchangeRate"),
        )
    }
}
