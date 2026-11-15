package com.mansaketh.chitta.core.domain

/**
 * Cross-device sync of one person's own book (Phase 9c, part 2). Pure rules only; the database and Firestore sides live elsewhere.
 *
 * The model: every synced row (an entry, an account, a loan, a setting ...) has one stamp, "last changed at T on device D", or "deleted at T on
 * device D" (a delete marker). When two devices disagree about a row, the newer stamp wins. A delete is just a stamp, so a later edit beats an
 * earlier delete and the other way round. No row is ever merged field by field.
 */

/** The stamp of one row on one device. [deleted] means a delete marker: the row is gone but the marker stays so other devices learn it. */
data class RowStamp(val atMs: Long, val deviceId: String, val deleted: Boolean)

enum class SyncDecision {
    /** Both sides already agree. Nothing to do. */
    Same,

    /** This device's row is newer: send it up. */
    KeepLocal,

    /** The cloud's row is newer: write it here (or delete the row here, if it is a delete marker). */
    TakeRemote,
}

object SyncMerge {
    /** Delete markers older than this are cleared away (days). A phone that stayed offline longer than this must not be trusted to merge. */
    const val TOMBSTONE_DAYS = 180

    /**
     * Who wins for one row. [local] / [remote] are null when that side has never heard of the row.
     * The newer [RowStamp.atMs] wins; on an exact tie the larger device id wins, so both devices reach the same answer.
     */
    fun decide(local: RowStamp?, remote: RowStamp?): SyncDecision = when {
        local == null && remote == null -> SyncDecision.Same
        remote == null -> SyncDecision.KeepLocal
        local == null -> SyncDecision.TakeRemote
        local.atMs != remote.atMs -> if (local.atMs > remote.atMs) SyncDecision.KeepLocal else SyncDecision.TakeRemote
        local.deviceId != remote.deviceId -> if (local.deviceId > remote.deviceId) SyncDecision.KeepLocal else SyncDecision.TakeRemote
        local.deleted != remote.deleted -> if (local.deleted) SyncDecision.KeepLocal else SyncDecision.TakeRemote // same device, same ms: the delete is the later act
        else -> SyncDecision.Same
    }

    /** Whether a delete marker made at [deletedAtMs] can be cleared away at [nowMs]. */
    fun tombstoneExpired(deletedAtMs: Long, nowMs: Long): Boolean = nowMs - deletedAtMs >= TOMBSTONE_DAYS * DAY_MS

    /** Whether a phone that last synced at [lastSyncMs] is too old to merge safely (its delete markers may already be cleared in the cloud). */
    fun tooStale(lastSyncMs: Long?, nowMs: Long): Boolean = lastSyncMs != null && nowMs - lastSyncMs >= TOMBSTONE_DAYS * DAY_MS

    private const val DAY_MS = 86_400_000L
}

object SyncRows {
    /** The Firestore document id of a row: `table~id`. A slash inside an id would break the path, so it is written `%2F` (and `%` as `%25`). */
    fun docId(table: String, rowId: String): String = table + "~" + rowId.replace("%", "%25").replace("/", "%2F")

    /** The reverse of [docId]: (table, row id), or null when it is not one of ours. */
    fun parseDocId(docId: String): Pair<String, String>? {
        val at = docId.indexOf('~')
        if (at <= 0 || at == docId.length - 1) return null
        return docId.substring(0, at) to docId.substring(at + 1).replace("%2F", "/").replace("%25", "%")
    }

    /**
     * Whether an entry belongs to this person's own book. Entries that came from other household members carry their uid and are not
     * this person's to sync (the household sync owns them).
     */
    fun isOwnEntry(ownerUid: String?, myUid: String): Boolean = ownerUid == null || ownerUid.isEmpty() || ownerUid == myUid

    /** Other members' placeholder accounts (id starts with `hh_`) are made by the household sync and never synced here. */
    fun isHouseholdPlaceholderAccount(accountId: String): Boolean = accountId.startsWith("hh_")
}

object SyncSettings {
    /**
     * Setting keys that describe one device or one login and so never sync. Copied from the importer's list (which copies Paddhu's
     * `kSettingsNotSynced`) with the sync's own bookkeeping added. Any key that starts with `household` is also device-only.
     */
    val DEVICE_ONLY: Set<String> = setOf(
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
    )

    fun syncs(key: String): Boolean = key !in DEVICE_ONLY && !key.startsWith("household")
}
