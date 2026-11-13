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
    ],
    version = 1,
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

    companion object {
        const val FILE_NAME = "chitta.db"

        fun open(context: Context): ChittaDatabase =
            Room.databaseBuilder(context.applicationContext, ChittaDatabase::class.java, FILE_NAME).build()
    }
}
