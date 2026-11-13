package com.mansaketh.chitta.data

import com.mansaketh.chitta.core.database.ExchangeRateEntity
import com.mansaketh.chitta.core.database.RatesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RefreshResult {
    data class Updated(val count: Int, val asOf: String) : RefreshResult
    data class Failed(val reason: String) : RefreshResult
}

/**
 * Downloads the daily rates file (units of each currency per 1 INR) and stores it as text, so no rate ever passes
 * through a float. Two addresses are tried; if both fail the old rates stay and the screen says so.
 */
@Singleton
class RateFetcher @Inject constructor(private val rates: RatesRepository) {
    suspend fun refresh(nowMs: Long): RefreshResult = withContext(Dispatchers.IO) {
        var last = "No connection."
        for (url in URLS) {
            try {
                val rows = parse(download(url), nowMs)
                if (rows.isEmpty()) { last = "The rates file was empty."; continue }
                rates.store(rows)
                return@withContext RefreshResult.Updated(rows.size, rows.first().asOfDate)
            } catch (e: Exception) {
                last = "Could not load rates (${e.javaClass.simpleName})."
            }
        }
        RefreshResult.Failed(last)
    }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    companion object {
        val URLS = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/inr.json",
            "https://latest.currency-api.pages.dev/v1/currencies/inr.json",
        )

        /** Pure, so it is unit-tested: `{date, inr:{code:number}}` to rows with the rate kept as plain decimal text. */
        fun parse(text: String, nowMs: Long): List<ExchangeRateEntity> {
            val root = Json.parseToJsonElement(text).jsonObject
            val date = root["date"]?.jsonPrimitive?.content ?: return emptyList()
            val table = root["inr"] as? JsonObject ?: return emptyList()
            return table.mapNotNull { (code, v) ->
                val raw = (v as? JsonPrimitive)?.content ?: return@mapNotNull null
                val d = raw.toBigDecimalOrNull() ?: return@mapNotNull null
                if (d.signum() <= 0) null else ExchangeRateEntity(code.uppercase(), d.toPlainString(), "INR", date, nowMs)
            }
        }
    }
}
