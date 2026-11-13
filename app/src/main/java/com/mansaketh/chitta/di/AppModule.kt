package com.mansaketh.chitta.di

import android.content.Context
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.database.BookRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.ChittaDatabase
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.database.RuleRepository
import com.mansaketh.chitta.core.database.CategoryWriter
import com.mansaketh.chitta.core.database.PinnedRepository
import com.mansaketh.chitta.core.database.RatesRepository
import com.mansaketh.chitta.core.database.TitleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import java.util.UUID
import javax.inject.Singleton

/** The ids and clock used when saving, so tests can replace them. */
interface IdSource { fun newId(): String }
interface Clock { fun nowMs(): Long; fun zone(): ZoneId }

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): ChittaDatabase = ChittaDatabase.open(context)
    @Provides @Singleton fun ledger(db: ChittaDatabase) = LedgerRepository(db)
    @Provides @Singleton fun plan(db: ChittaDatabase, ledger: LedgerRepository) = PlanRepository(db, ledger)
    @Provides @Singleton fun assets(db: ChittaDatabase) = AssetsRepository(db)
    @Provides @Singleton fun rules(db: ChittaDatabase) = RuleRepository(db)
    @Provides @Singleton fun accounts(db: ChittaDatabase) = AccountRepository(db)
    @Provides @Singleton fun categories(db: ChittaDatabase) = CategoryRepository(db)
    @Provides @Singleton fun pinned(db: ChittaDatabase) = PinnedRepository(db)
    @Provides @Singleton fun book(db: ChittaDatabase) = BookRepository(db)
    @Provides @Singleton fun titles(db: ChittaDatabase) = TitleRepository(db)
    @Provides @Singleton fun categoryWriter(db: ChittaDatabase) = CategoryWriter(db)
    @Provides @Singleton fun rates(db: ChittaDatabase) = RatesRepository(db)
    @Provides @Singleton fun ids(): IdSource = object : IdSource { override fun newId() = UUID.randomUUID().toString() }
    @Provides @Singleton fun clock(): Clock = object : Clock {
        override fun nowMs() = System.currentTimeMillis()
        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
