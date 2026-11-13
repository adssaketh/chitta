package com.mansaketh.chitta.core.intake

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Test files live under src/test/resources. */
internal fun resource(path: String): String =
    object {}.javaClass.getResourceAsStream("/$path")?.readBytes()?.toString(Charsets.UTF_8) ?: error("missing test resource $path")

internal fun json(path: String): JsonObject = Json.parseToJsonElement(resource(path)).jsonObject

internal fun JsonElement.str(key: String): String = jsonObject[key]!!.jsonPrimitive.content
internal fun JsonElement.bool(key: String): Boolean = jsonObject[key]!!.jsonPrimitive.boolean
internal fun JsonElement.list(key: String): JsonArray = jsonObject[key]!!.jsonArray
internal fun JsonElement.has(key: String): Boolean = jsonObject.containsKey(key) && jsonObject[key] !is kotlinx.serialization.json.JsonNull
/** A rupee amount written as a JSON number (38.0, 697.43) as exact paise, with no Double arithmetic. */
internal fun JsonElement.paise(key: String): Long = Rupees.toPaise((jsonObject[key] as JsonPrimitive).content)!!
