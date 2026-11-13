package com.mansaketh.chitta.core.intake

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

/**
 * Reads what Ping sends (contract/ping-paddhu/SPEC.md, copied into this module's test resources): one message from the share sheet,
 * messages dropped into the inbox, or the date-range file. Each SMS is read again here with [SmsParser], the same rules Ping and the old
 * app use, so they agree on what a message means. A port of `pingImport.dart`.
 */
object PingExport {
    /** Unknown fields are ignored; that is what makes Ping's optional fields safe (SPEC section 2). */
    private val JSON = Json { ignoreUnknownKeys = true }

    private val cardBillDebitRe = Rx.of("credit ?card payment|cc ?bill|billpay|card bill|sbi card +payment|cc payment", ignoreCase = true)
    private val brandKeyRe = Regex("^[a-z0-9]{1,40}\\z")
    private val fdWord = Rx.of("fixed\\s*deposit|term\\s*deposit|\\bF\\.?D\\.?R?\\b|\\bT\\.?D\\.?R?\\b", ignoreCase = true)
    private val bookedWord = Rx.of("\\b(book(?:ed|ing)?|open(?:ed)?|creat(?:ed|ion)|renew(?:ed|al)?|placed|constitut(?:ed)?|established|set up)\\b", ignoreCase = true)
    private val notBooking = Rx.of("\\b(matured|closed|closure|premature|broken|liquidat|interest (?:has been )?credited|proceeds)\\b|\\botp\\b|will mature|is maturing|due for", ignoreCase = true)

    /** FNV-1a over UTF-16 code units, the same on every run and in Ping. Lower-case hex, no padding. */
    internal fun stableHash(text: String): String {
        var h = 0x811c9dc5L
        for (unit in text) {
            h = h xor unit.code.toLong()
            h = (h * 0x01000193L) and 0xFFFFFFFFL
        }
        return java.lang.Long.toHexString(h)
    }

    /** The same hash for any text, used for cache file names. */
    fun stableHashOf(text: String): String = stableHash(text)

    private fun two(n: Int) = n.toString().padStart(2, '0')

    /** The duplicate key of a message: the day (in [zone], the phone's) plus a hash of the whitespace-normalised text. Checked against Ping's golden vectors. */
    fun contentKey(body: String, dateMs: Long, zone: ZoneId): String {
        val normalized = body.replace(Rx.WHITESPACE_RUN, " ").trim()
        val day = Instant.ofEpochMilli(dateMs).atZone(zone).toLocalDate()
        return "${day.year}${two(day.monthValue)}${two(day.dayOfMonth)}-${stableHash(normalized)}"
    }

    /** Whether the text looks like a fixed deposit being opened or renewed (used to keep such messages for later). */
    fun looksLikeFixedDepositBooking(body: String) = fdWord.containsMatchIn(body) && bookedWord.containsMatchIn(body) && !notBooking.containsMatchIn(body)

    private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.content ?: ""

    private fun dateOf(value: JsonElement?): Long? {
        val p = value as? JsonPrimitive ?: return null
        return p.content.toLongOrNull() ?: p.content.toDoubleOrNull()?.toLong()
    }

    private fun brandKeyOf(value: JsonElement?): String = value.text().let { if (brandKeyRe.matches(it)) it else "" }

    /** Throws [IntakeFormatException] (with a readable message) when the text is not a Ping export. */
    fun items(jsonText: String): List<JsonObject> {
        val decoded = try { JSON.parseToJsonElement(jsonText) } catch (e: Exception) { throw IntakeFormatException("This file is not a Ping export.") }
        val obj = decoded as? JsonObject
        if (obj == null || obj["type"].text() != "paddhu-sms-export" || obj["sms"] !is JsonArray) throw IntakeFormatException("This file is not a Ping export.")
        return (obj["sms"] as JsonArray).mapNotNull { it as? JsonObject }
    }

    /** The export JSON for these items, so [parse] can read them (the review queue and the inbox keep single items). */
    fun exportOf(items: List<JsonObject>): String = buildJsonObject {
        put("app", "Ping"); put("type", "paddhu-sms-export"); put("version", 1)
        put("sms", buildJsonArray { items.forEach { add(it) } })
    }.toString()

    fun parse(jsonText: String, known: KnownAccounts, zone: ZoneId): PingIntake {
        val obj = try { JSON.parseToJsonElement(jsonText) as? JsonObject } catch (e: Exception) { null }
        val list = items(jsonText)
        val rows = ArrayList<IntakeRow>()
        val dues = ArrayList<CardDueItem>()
        val fds = ArrayList<String>()
        val seen = HashSet<String>()
        var skipped = 0

        for (item in list) {
            val id = item["id"].text()
            val sender = item["address"].text()
            val body = item["body"].text()
            val dateMs = dateOf(item["date"])
            if (id.isEmpty() || dateMs == null) { skipped++; continue }
            if (!seen.add(id)) continue

            val kind = item["kind"].text()
            if (kind == "fixed_deposit" || (kind != "transaction" && looksLikeFixedDepositBooking(body))) fds.add(id)

            val parsed = SmsParser.parse(sender, body)
            if (parsed != null && parsed.amountPaise > 0) cardDueOf(parsed, body, dateMs, zone, known)?.let { dues.add(it) }
            if (parsed == null || parsed.cardPayment || parsed.amountPaise <= 0) { skipped++; continue }

            val k = known.lookup(parsed.instrumentKey)
            rows.add(
                IntakeRow(
                    sourceId = id, contentKey = contentKey(body, dateMs, zone), sender = sender, dateMs = dateMs,
                    amountPaise = parsed.amountPaise, isExpense = parsed.isExpense, title = parsed.title, merchant = parsed.merchant,
                    categoryLabel = parsed.category, accountName = k?.name ?: parsed.instrumentName,
                    walletName = if (k != null && k.wallet.isNotEmpty()) k.wallet else walletForUnknown(parsed.instrumentKey),
                    // Only your own list decides: old Federal UPI SMS carry no account number, so they are not marked closed.
                    closed = k?.closed ?: false, knownAccount = k != null, brandKey = brandKeyOf(item["brandKey"]),
                    tickByDefault = (k?.tickByDefault ?: true) && !(parsed.isExpense && cardBillDebitRe.containsMatchIn(body)),
                    cardBillDebit = parsed.isExpense && cardBillDebitRe.containsMatchIn(body),
                ),
            )
        }
        rows.sortByDescending { it.dateMs }
        return PingIntake(rows, skipped, dateOf(obj?.get("from")), dateOf(obj?.get("to")), dues, fds)
    }

    private fun walletForUnknown(instrumentKey: String): String {
        val parts = instrumentKey.split(':')
        val kind = parts.getOrElse(1) { "" }
        // The SMS does not say which account it is, or the sender is not a bank or wallet we know by name: keep those together.
        if (kind.isEmpty() || kind == "unknown") return "Unassigned"
        return Instrument.knownIssuerLabel(instrumentKey) ?: "Unassigned"
    }

    private val creditCardNameRe = Rx.of("credit card|emi card", ignoreCase = true)

    /** What this message does to what you owe on a credit card, or null when it is not about one. The id comes from the day and text, so the same SMS counts once. */
    private fun cardDueOf(parsed: ParsedSms, body: String, dateMs: Long, zone: ZoneId, known: KnownAccounts): CardDueItem? {
        val k = known.lookup(parsed.instrumentKey)
        if (k != null && k.closed) return null
        val credit = parsed.cardPayment || parsed.instrumentKey.contains(":credit_card") || (k != null && creditCardNameRe.containsMatchIn(k.name))
        if (!credit) return null
        val kind = if (parsed.cardPayment) "payment" else if (parsed.isExpense) "spend" else "refund"
        val card = if (k != null && k.dueCard.isNotEmpty()) k.dueCard else (k?.name ?: parsed.instrumentName ?: parsed.instrumentKey)
        return CardDueItem("cd-${contentKey(body, dateMs, zone)}", card, kind, parsed.amountPaise, dateMs, parsed.title)
    }

    /** `brandKey` as the contract defines it (lower-case letters and digits), else "". Only used to ask Ping for a logo. */
    fun validBrandKey(key: String?): String = if (key != null && brandKeyRe.matches(key)) key else ""

    /** Finds the Ping message an entry was made from, from its note, or null when the note does not say enough to find it again. */
    fun refOfNote(note: String): PingSmsRef? {
        val id = Regex("Ping SMS #([\\w-]+)").find(note)?.groupValues?.get(1) ?: return null
        if (id.startsWith("pp-")) return null
        val address = Regex("Ping SMS from ([A-Za-z0-9._&-]{1,32})").find(note)?.groupValues?.get(1)
        val n = Regex("^n(\\d{10,15})-[0-9a-f]{1,8}\\z").find(id)
        if (n != null) {
            if (address == null) return null
            return PingSmsRef(address = address, dateMs = n.groupValues[1].toLong())
        }
        if (id.length > 40) return null
        return PingSmsRef(smsId = id, address = address)
    }
}
