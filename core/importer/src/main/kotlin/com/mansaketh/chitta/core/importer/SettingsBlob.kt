package com.mansaketh.chitta.core.importer

import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Setting
import com.mansaketh.chitta.core.money.paiseFromDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Paddhu keeps one JSON blob in `app_settings`. Besides ordinary settings it holds the fork's own data:
 * `loanTerms`, `fixedDeposits` and `cardDues` (Phase 0 audit, risk 2). Dropping the blob would lose them silently,
 * so each of them is parsed into its own list here.
 */

/** Keys that describe one device or one login. Copied from Paddhu `settingsMerge.dart` (kSettingsNotSynced). */
internal val DEVICE_ONLY_KEYS: Set<String> = setOf(
    "settingsModified",
    "databaseJustImported", "backupLimit", "backupSync", "syncEveryChange", "devicesHaveBeenSynced",
    "numBackups", "autoBackups", "autoBackupsFrequency", "lastBackup", "hasSignedIn", "lastSynced",
    "lastLoginVersion", "numLogins", "forceAutoLogin", "enableGoogleLoginFlyIn", "openedStoreRating",
    "dismissedStoreRating", "submittedFeedback", "canShowBackupReminderPopup",
    "canShowTransactionActionButtonTip", "autoLoginDisabledOnWebTip", "allSpendingPageTip",
    "notifications", "notificationHour", "notificationMinute", "notificationsUpcomingTransactions",
    "notificationsReminderType", "appOpenedHour", "appOpenedMinute", "notificationScanning",
    "notificationScanningDebug", "notificationSmartParse", "emailScanning", "emailScanningPullToRefresh",
    "sendTransactionsToServerQueue", "currentUserEmail", "requireAuth", "selectedWalletPk", "locale",
    "logging", "iOSEmulate", "iOSAnimatedGoo", "previewDemo", "purchaseID", "selectedSubscriptionType",
    "premiumPopupAddTransactionCount", "premiumPopupAddTransactionLastShown", "premiumPopupFreeSeen",
    "hasOnboarded", "appAnimations", "animationSpeed", "batterySaver", "materialYou", "accentSystemColor",
    "expandedNavigationSidebar", "cachedCurrencyExchange", "sharedBudgets", "fdReminderCount",
    "allSpendingLastPage", "loansLastPage",
    "householdId", "householdName", "householdRole", "householdNickname",
    "householdSyncReady", "householdDeleteCursor", "householdCategoriesHash", "householdLastSync",
    "householdShowBalances", "householdView", "householdCanDelete", "householdMemberNames",
    "householdSharedUploaded", "householdSharedPaidSent", "householdSharedCopies",
)

private val COLLECTION_KEYS = setOf("loanTerms", "fixedDeposits", "cardDues", "householdSharedOwn")

internal class ParsedSettings(
    val settings: List<Setting>,
    val loanTerms: List<LoanTerms>,
    val fixedDeposits: List<FixedDeposit>,
    val cardDues: List<CardDue>,
    val skippedDeviceKeys: Int,
    val issues: List<Issue>,
)

internal fun parseSettingsBlob(text: String?, zone: ZoneId): ParsedSettings {
    val issues = mutableListOf<Issue>()
    if (text.isNullOrBlank()) return ParsedSettings(emptyList(), emptyList(), emptyList(), emptyList(), 0, issues)
    val root = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (e: Exception) {
        null
    }
    if (root == null) {
        issues += Issue(Severity.ERROR, "app_settings", null, "The settings blob is not a JSON object; settings, loans, deposits and card dues were not read.")
        return ParsedSettings(emptyList(), emptyList(), emptyList(), emptyList(), 0, issues)
    }

    val settings = mutableListOf<Setting>()
    var skipped = 0
    for ((key, value) in root) {
        when {
            key in DEVICE_ONLY_KEYS -> skipped++
            key in COLLECTION_KEYS -> Unit // parsed below (householdSharedOwn is rebuilt from the server, not copied)
            else -> settings += Setting(key, Json.encodeToString(JsonElement.serializer(), value))
        }
    }

    val loans = (root["loanTerms"] as? JsonObject)?.mapNotNull { (id, item) -> parseLoanTerms(id, item, zone, issues) } ?: emptyList()
    val deposits = (root["fixedDeposits"] as? JsonArray)?.mapNotNull { parseDeposit(it, zone, issues) } ?: emptyList()
    val dues = (root["cardDues"] as? JsonArray)?.mapNotNull { parseCardDue(it, zone, issues) } ?: emptyList()
    return ParsedSettings(settings.sortedBy { it.key }, loans, deposits, dues, skipped, issues)
}

// ---- helpers -------------------------------------------------------------------------------------------

private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive
private fun JsonObject.text(key: String): String? = this[key].prim()?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.decimal(key: String): BigDecimal? = text(key)?.toBigDecimalOrNull()
private fun JsonObject.flag(key: String): Boolean = this[key].prim()?.booleanOrNull ?: false
private fun JsonObject.updatedAt(): Long? = this["_u"].prim()?.contentOrNull?.toDoubleOrNull()?.toLong()
private fun JsonObject.deleted(): Boolean = flag("_d")

private fun parseInstant(text: String?, zone: ZoneId): Long? {
    if (text.isNullOrBlank()) return null
    return try {
        if (text.endsWith("Z") || Regex(".*[+-]\\d\\d:?\\d\\d$").matches(text)) OffsetDateTime.parse(text).toInstant().toEpochMilli()
        else LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try { Instant.parse(text).toEpochMilli() } catch (e2: Exception) { null }
    }
}

private fun plainDecimal(value: BigDecimal?, default: String): String = value?.stripTrailingZeros()?.toPlainString() ?: default

private fun parseLoanTerms(id: String, item: JsonElement, zone: ZoneId, issues: MutableList<Issue>): LoanTerms? {
    val o = item as? JsonObject ?: return null
    if (o.deleted()) return null
    val first = parseInstant(o.text("first"), zone)
    val months = o.decimal("months")?.toInt() ?: 0
    if (first == null || months <= 0) {
        issues += Issue(Severity.ERROR, "settings.loanTerms", id, "Loan terms are missing the first instalment date or the number of months; left out.")
        return null
    }
    return LoanTerms(
        objectiveId = id,
        principal = paiseFromDecimal(o.decimal("principal") ?: BigDecimal.ZERO).paise.value,
        annualRatePct = plainDecimal(o.decimal("rate"), "0"),
        months = months,
        emi = paiseFromDecimal(o.decimal("emi") ?: BigDecimal.ZERO).paise.value,
        firstEmiMs = first,
        fee = paiseFromDecimal(o.decimal("fee") ?: BigDecimal.ZERO).paise.value,
        gstPct = plainDecimal(o.decimal("gst"), "18"),
        feePaid = o.flag("feePaid"),
        paidInstalments = (o["paid"] as? JsonArray)?.mapNotNull { it.prim()?.contentOrNull?.toDoubleOrNull()?.toInt() }?.sorted() ?: emptyList(),
        accountId = o.text("wallet"),
        note = o.text("note") ?: "",
        updatedAtMs = o.updatedAt(),
    )
}

private fun parseDeposit(item: JsonElement, zone: ZoneId, issues: MutableList<Issue>): FixedDeposit? {
    val o = item as? JsonObject ?: return null
    if (o.deleted()) return null
    val id = o.text("id") ?: ""
    val start = parseInstant(o.text("start"), zone)
    val maturity = parseInstant(o.text("maturity"), zone)
    if (id.isEmpty() || start == null || maturity == null) {
        issues += Issue(Severity.ERROR, "settings.fixedDeposits", id.ifEmpty { null }, "A fixed deposit is missing its id or dates; left out.")
        return null
    }
    return FixedDeposit(
        id = id,
        bank = o.text("bank") ?: "",
        fdNumber = o.text("no") ?: "",
        principal = paiseFromDecimal(o.decimal("principal") ?: BigDecimal.ZERO).paise.value,
        ratePct = plainDecimal(o.decimal("rate"), "0"),
        startMs = start,
        maturityMs = maturity,
        compoundingPerYear = o.decimal("comp")?.toInt() ?: 4,
        maturityAmount = o.decimal("maturityAmount")?.let { paiseFromDecimal(it).paise.value },
        accountId = o.text("wallet"),
        closed = o.flag("closed"),
        note = o.text("note") ?: "",
        fromSms = o.flag("sms"),
        smsId = o.text("smsId") ?: "",
        updatedAtMs = o.updatedAt(),
    )
}

private fun parseCardDue(item: JsonElement, zone: ZoneId, issues: MutableList<Issue>): CardDue? {
    val o = item as? JsonObject ?: return null
    if (o.deleted()) return null
    val id = o.text("id")
    val date = parseInstant(o.text("date"), zone)
    val amount = o.decimal("amount")
    val card = o.text("card").orEmpty()
    if (id == null || date == null || amount == null || card.isEmpty()) {
        issues += Issue(Severity.ERROR, "settings.cardDues", id, "A card due entry is missing its id, card, date or amount; left out.")
        return null
    }
    return CardDue(id, card, o.text("kind") ?: "", paiseFromDecimal(amount).paise.value, date, o.text("note") ?: "", o.updatedAt())
}
