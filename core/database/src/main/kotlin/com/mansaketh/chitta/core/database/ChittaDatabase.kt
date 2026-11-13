package com.mansaketh.chitta.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class, BudgetEntity::class,
        CategoryLimitEntity::class, ObjectiveEntity::class, TitleMemoryEntity::class, ScanTemplateEntity::class,
        ActivityLogEntity::class, SettingEntity::class, LoanTermsEntity::class, FixedDepositEntity::class, CardDueEntity::class,
        PinnedEntryEntity::class, EntryVersionEntity::class, ExchangeRateEntity::class, RateOverrideEntity::class, RuleEntity::class,
    ],
    version = ChittaDatabase.VERSION,
    exportSchema = true,
)
abstract class ChittaDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun budgets(): BudgetDao
    abstract fun objectives(): ObjectiveDao
    abstract fun titles(): TitleMemoryDao
    abstract fun misc(): MiscDao
    abstract fun pinned(): PinnedDao
    abstract fun versions(): VersionDao
    abstract fun rates(): RateDao
    abstract fun rules(): RuleDao

    companion object {
        /** The schema number. Restore points and backup files are checked against it. */
        const val VERSION = 4
        const val FILE_NAME = "chitta.db"

        fun open(context: Context): ChittaDatabase =
            Room.databaseBuilder(context.applicationContext, ChittaDatabase::class.java, FILE_NAME)
                .addMigrations(Migrations.V1_TO_V2, Migrations.V2_TO_V3, Migrations.V3_TO_V4)
                .build()
    }
}
