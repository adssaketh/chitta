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

    /** Phase 5: the day of month a monthly or yearly bill belongs on, so a bill on the 31st survives short months. Nothing is dropped. */
    val V2_TO_V3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `txn` ADD COLUMN `anchorDay` INTEGER")
        }
    }
}
