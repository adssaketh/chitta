package com.mansaketh.chitta.core.database

import com.mansaketh.chitta.core.domain.Rule
import com.mansaketh.chitta.core.domain.RuleActions
import com.mansaketh.chitta.core.domain.RuleCondition
import com.mansaketh.chitta.core.domain.RuleField
import com.mansaketh.chitta.core.domain.RuleOp
import com.mansaketh.chitta.core.money.Paise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Rules as JSON text. Enum names are stored by name on purpose: they are ours (not Paddhu's) and a bad name is skipped, not crashed on. */
object RuleJson {
    fun conditions(list: List<RuleCondition>): String = buildJsonArray {
        list.forEach { c ->
            add(buildJsonObject {
                put("field", c.field.name); put("op", c.op.name); put("text", c.text)
                put("amount", c.amount?.value); put("account", c.accountId)
            })
        }
    }.toString()

    fun actions(a: RuleActions): String = buildJsonObject {
        put("category", a.categoryId); put("subCategory", a.subCategoryId); put("account", a.accountId)
        put("exclude", a.excludeFromBudgetIds?.let { l -> buildJsonArray { l.forEach { add(JsonPrimitive(it)) } } } ?: JsonNull)
    }.toString()

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    fun parseConditions(json: String): List<RuleCondition> =
        (Json.parseToJsonElement(json) as JsonArray).mapNotNull { e ->
            val o = e.jsonObject
            val field = RuleField.entries.firstOrNull { it.name == o.str("field") } ?: return@mapNotNull null
            val op = RuleOp.entries.firstOrNull { it.name == o.str("op") } ?: return@mapNotNull null
            RuleCondition(field, op, o.str("text") ?: "", (o["amount"] as? JsonPrimitive)?.longOrNull?.let { Paise(it) }, o.str("account"))
        }

    fun parseActions(json: String): RuleActions {
        val o = Json.parseToJsonElement(json).jsonObject
        return RuleActions(o.str("category"), o.str("subCategory"), o.str("account"), (o["exclude"] as? JsonArray)?.map { it.jsonPrimitive.content })
    }
}

fun Rule.toEntity() = RuleEntity(id, name, RuleJson.conditions(conditions), RuleJson.actions(actions), order, enabled)
fun RuleEntity.toModel() = Rule(id, name, RuleJson.parseConditions(conditionsJson), RuleJson.parseActions(actionsJson), ruleOrder, enabled)

class RuleRepository(private val db: ChittaDatabase) {
    fun observeAll(): Flow<List<Rule>> = db.rules().observeAll().map { rows -> rows.map { it.toModel() } }
    suspend fun all(): List<Rule> = db.rules().getAll().map { it.toModel() }
    suspend fun save(rule: Rule) = db.rules().upsert(rule.toEntity())
    suspend fun delete(id: String) = db.rules().delete(id)
}
