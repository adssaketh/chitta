package com.mansaketh.chitta.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /** Phase 4: exchange fields on entries, pinned entries, edit history, daily rates and rate overrides. Nothing is dropped. */
    val V1_TO_V2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `txn` ADD COLUMN `originalAmountPaise` INTEGER")
            db.execSQL("ALTER TABLE `txn` ADD COLUMN `originalCurrency` TEXT")
            db.execSQL("ALTER TABLE `txn` ADD COLUMN `exchangeRate` TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS `pinned_entry` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `amountPaise` INTEGER, `income` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `accountId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `entry_version` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` TEXT NOT NULL, `actionCode` INTEGER NOT NULL, `atMs` INTEGER NOT NULL, `snapshotJson` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_version_entryId` ON `entry_version` (`entryId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rate` (`currency` TEXT NOT NULL, `perBase` TEXT NOT NULL, `base` TEXT NOT NULL, `asOfDate` TEXT NOT NULL, `fetchedAtMs` INTEGER NOT NULL, PRIMARY KEY(`currency`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `rate_override` (`fromCurrency` TEXT NOT NULL, `toCurrency` TEXT NOT NULL, `rate` TEXT NOT NULL, `setAtMs` INTEGER NOT NULL, PRIMARY KEY(`fromCurrency`, `toCurrency`))")
        }
    }

    /** Phase 8a: split group on entries, credit-card fields on accounts, the rules table. Nothing is dropped. */
    val V3_TO_V4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `txn` ADD COLUMN `splitGroupId` TEXT")
            db.execSQL("ALTER TABLE `account` ADD COLUMN `creditLimitPaise` INTEGER")
            db.execSQL("ALTER TABLE `account` ADD COLUMN `statementDay` INTEGER")
            db.execSQL("ALTER TABLE `account` ADD COLUMN `dueDay` INTEGER")
            db.execSQL("ALTER TABLE `account` ADD COLUMN `minimumDuePaise` INTEGER")
            db.execSQL("ALTER TABLE `account` ADD COLUMN `remindDaysBefore` INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS `entry_rule` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `conditionsJson` TEXT NOT NULL, `actionsJson` TEXT NOT NULL, `ruleOrder` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    /** Phase 8c: the mutual fund holdings table and NPS accounts. Nothing is dropped. */
    val V4_TO_V5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `mutual_fund` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isin` TEXT NOT NULL, `schemeCode` TEXT NOT NULL, `units` TEXT NOT NULL, `investedPaise` INTEGER, `source` TEXT NOT NULL, `unitsAsOfMs` INTEGER NOT NULL, `navText` TEXT, `navDate` TEXT, `note` TEXT NOT NULL, `updatedAtMs` INTEGER, `folio` TEXT NOT NULL, `groupId` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `nps_account` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `netContributionsPaise` INTEGER, `asOfMs` INTEGER NOT NULL, `note` TEXT NOT NULL, `updatedAtMs` INTEGER, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mutual_fund_isin` ON `mutual_fund` (`isin`)")
        }
    }

    /** Phase 8d: the optional monthly SIP on a fund and its estimated units. Nothing is dropped; every fund starts with no SIP and no estimate. */
    val V5_TO_V6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `mutual_fund` ADD COLUMN `sipAmountPaise` INTEGER")
            db.execSQL("ALTER TABLE `mutual_fund` ADD COLUMN `sipDay` INTEGER")
            db.execSQL("ALTER TABLE `mutual_fund` ADD COLUMN `sipUnits` TEXT NOT NULL DEFAULT '0'")
            db.execSQL("ALTER TABLE `mutual_fund` ADD COLUMN `sipInvestedPaise` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `mutual_fund` ADD COLUMN `sipCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `mutual_fund` ADD COLUMN `sipThrough` TEXT NOT NULL DEFAULT ''")
        }
    }

    /** Phase 8e: rollover switch on budgets (off for every existing budget) and the sinking fund table. Nothing is dropped. */
    val V6_TO_V7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `budget` ADD COLUMN `rollover` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS `sinking_fund` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `targetPaise` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `targetDateMs` INTEGER NOT NULL, `categoryId` TEXT, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, `modifiedAtMs` INTEGER, PRIMARY KEY(`id`))")
        }
    }

    /** Phase 5: the day of month a monthly or yearly bill belongs on, so a bill on the 31st survives short months. Nothing is dropped. */
    val V2_TO_V3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `txn` ADD COLUMN `anchorDay` INTEGER")
        }
    }
}
