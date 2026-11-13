package com.mansaketh.chitta.core.database

import androidx.room.withTransaction
import com.mansaketh.chitta.core.model.Book

sealed interface WriteResult {
    data class Written(val rows: Int) : WriteResult
    /** The database already holds data; nothing was changed. Import is for a fresh install only. */
    data class NotEmpty(val accounts: Int, val transactions: Int) : WriteResult
}

/** Saves a whole [Book] (from the importer) and reads it back. */
class BookRepository(private val db: ChittaDatabase) {

    /**
     * Writes the book in ONE transaction: either every table is filled or nothing changes
     * (an exception rolls it all back, so an interrupted import leaves the database as it was).
     * Refuses a database that already has accounts or transactions.
     */
    suspend fun writeFresh(book: Book): WriteResult = db.withTransaction {
        val accounts = db.accounts().count()
        val transactions = db.transactions().count()
        if (accounts > 0 || transactions > 0) return@withTransaction WriteResult.NotEmpty(accounts, transactions)

        db.accounts().insertAll(book.accounts.map { it.toEntity() })
        db.categories().insertAll(book.categories.map { it.toEntity() })
        db.transactions().insertAll(book.transactions.map { it.toEntity() })
        db.budgets().insertAll(book.budgets.map { it.toEntity() })
        db.budgets().insertLimits(book.categoryLimits.map { it.toEntity() })
        db.objectives().insertAll(book.objectives.map { it.toEntity() })
        db.titles().insertAll(book.titleMemory.map { it.toEntity() })
        db.misc().insertTemplates(book.scanTemplates.map { it.toEntity() })
        db.misc().insertLog(book.activityLog.map { it.toEntity() })
        db.misc().putSettings(book.settings.map { it.toEntity() })
        db.misc().insertLoanTerms(book.loanTerms.map { it.toEntity() })
        db.misc().insertDeposits(book.fixedDeposits.map { it.toEntity() })
        db.misc().insertCardDues(book.cardDues.map { it.toEntity() })
        WriteResult.Written(
            book.accounts.size + book.categories.size + book.transactions.size + book.budgets.size + book.categoryLimits.size +
                book.objectives.size + book.titleMemory.size + book.scanTemplates.size + book.activityLog.size + book.settings.size +
                book.loanTerms.size + book.fixedDeposits.size + book.cardDues.size
        )
    }

    /** Everything in the database as a [Book], in a stable order (used by tests and by export later). */
    suspend fun readAll(): Book = db.withTransaction {
        Book(
            accounts = db.accounts().getAll().map { it.toModel() },
            categories = db.categories().getAll().map { it.toModel() },
            transactions = db.transactions().getAll().map { it.toModel() },
            budgets = db.budgets().getAll().map { it.toModel() },
            categoryLimits = db.budgets().getLimits().map { it.toModel() },
            objectives = db.objectives().getAll().map { it.toModel() },
            titleMemory = db.titles().getAll().map { it.toModel() },
            scanTemplates = db.misc().getTemplates().map { it.toModel() },
            activityLog = db.misc().getLog().map { it.toModel() },
            settings = db.misc().getSettings().map { it.toModel() },
            loanTerms = db.misc().getLoanTerms().map { it.toModel() },
            fixedDeposits = db.misc().getDeposits().map { it.toModel() },
            cardDues = db.misc().getCardDues().map { it.toModel() },
        )
    }
}
