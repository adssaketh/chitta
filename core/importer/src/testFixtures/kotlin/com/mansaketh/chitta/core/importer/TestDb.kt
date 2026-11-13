package com.mansaketh.chitta.core.importer

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Reads a backup file through sqlite-jdbc, opened read-only like the real importer must. */
class JdbcBackupSource(private val file: File) : BackupSource, AutoCloseable {
    private val conn: Connection = DriverManager.getConnection(
        "jdbc:sqlite:${file.absolutePath}",
        SQLiteConfig().apply { setReadOnly(true) }.toProperties(),
    )

    override fun userVersion(): Int = conn.createStatement().use { st -> st.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) } }

    override fun hasTable(name: String): Boolean = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { ps ->
        ps.setString(1, name); ps.executeQuery().use { it.next() }
    }

    override fun rows(table: String): List<Row> = conn.createStatement().use { st ->
        st.executeQuery("SELECT * FROM \"$table\"").use { rs ->
            val md = rs.metaData
            buildList {
                while (rs.next()) add((1..md.columnCount).associate { md.getColumnName(it) to rs.getObject(it) })
            }
        }
    }

    override fun close() = conn.close()
}

/**
 * Builds a small Paddhu v48 database with the exact table and column names and types seen on a real backup
 * (PRAGMA table_info and the Drift schema file `drift_schema_v48.json`). Fake data only.
 */
object TestDb {
    private val ddl = listOf(
        """CREATE TABLE wallets (wallet_pk TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, colour TEXT, icon_name TEXT, date_created INTEGER NOT NULL, date_time_modified INTEGER, "order" INTEGER NOT NULL, currency TEXT, currency_format TEXT, decimals INTEGER NOT NULL DEFAULT 2, home_page_widget_display TEXT, owner_uid TEXT)""",
        """CREATE TABLE categories (category_pk TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, colour TEXT, icon_name TEXT, emoji_icon_name TEXT, date_created INTEGER NOT NULL, date_time_modified INTEGER, "order" INTEGER NOT NULL, income INTEGER NOT NULL DEFAULT 0, method_added INTEGER, main_category_pk TEXT)""",
        """CREATE TABLE transactions (transaction_pk TEXT PRIMARY KEY NOT NULL, paired_transaction_fk TEXT, name TEXT NOT NULL, amount REAL NOT NULL, note TEXT NOT NULL, category_fk TEXT NOT NULL, sub_category_fk TEXT, wallet_fk TEXT NOT NULL DEFAULT '0', date_created INTEGER NOT NULL, date_time_modified INTEGER, original_date_due INTEGER, income INTEGER NOT NULL DEFAULT 0, period_length INTEGER, reoccurrence INTEGER, end_date INTEGER, upcoming_transaction_notification INTEGER DEFAULT 1, type INTEGER, paid INTEGER NOT NULL DEFAULT 0, created_another_future_transaction INTEGER DEFAULT 0, skip_paid INTEGER NOT NULL DEFAULT 0, method_added INTEGER, transaction_owner_email TEXT, transaction_original_owner_email TEXT, shared_key TEXT, shared_old_key TEXT, shared_status INTEGER, shared_date_updated INTEGER, shared_reference_budget_pk TEXT, objective_fk TEXT, objective_loan_fk TEXT, budget_fks_exclude TEXT, owner_uid TEXT, is_private INTEGER NOT NULL DEFAULT 0, household_synced_at INTEGER)""",
        """CREATE TABLE budgets (budget_pk TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, amount REAL NOT NULL, colour TEXT, start_date INTEGER NOT NULL, end_date INTEGER NOT NULL, wallet_fks TEXT, category_fks TEXT, category_fks_exclude TEXT, income INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, added_transactions_only INTEGER NOT NULL DEFAULT 0, period_length INTEGER NOT NULL, reoccurrence INTEGER, date_created INTEGER NOT NULL, date_time_modified INTEGER, pinned INTEGER NOT NULL DEFAULT 0, "order" INTEGER NOT NULL, wallet_fk TEXT NOT NULL DEFAULT '0', budget_transaction_filters TEXT, member_transaction_filters TEXT, shared_key TEXT, shared_owner_member INTEGER, shared_date_updated INTEGER, shared_members TEXT, shared_all_members_ever TEXT, is_absolute_spending_limit INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE category_budget_limits (category_limit_pk TEXT PRIMARY KEY NOT NULL, category_fk TEXT NOT NULL, budget_fk TEXT NOT NULL, amount REAL NOT NULL, date_time_modified INTEGER, wallet_fk TEXT NOT NULL DEFAULT '0')""",
        """CREATE TABLE associated_titles (associated_title_pk TEXT PRIMARY KEY NOT NULL, category_fk TEXT NOT NULL, title TEXT NOT NULL, date_created INTEGER NOT NULL, date_time_modified INTEGER, "order" INTEGER NOT NULL, is_exact_match INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE objectives (objective_pk TEXT PRIMARY KEY NOT NULL, type INTEGER NOT NULL DEFAULT 0, name TEXT NOT NULL, amount REAL NOT NULL, "order" INTEGER NOT NULL, colour TEXT, date_created INTEGER NOT NULL, end_date INTEGER, date_time_modified INTEGER, icon_name TEXT, emoji_icon_name TEXT, income INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 1, archived INTEGER NOT NULL DEFAULT 0, wallet_fk TEXT NOT NULL DEFAULT '0')""",
        """CREATE TABLE app_settings (settings_pk INTEGER PRIMARY KEY NOT NULL, settings_j_s_o_n TEXT NOT NULL, date_updated INTEGER NOT NULL)""",
        """CREATE TABLE scanner_templates (scanner_template_pk TEXT PRIMARY KEY NOT NULL, date_created INTEGER NOT NULL, date_time_modified INTEGER, template_name TEXT NOT NULL, contains TEXT NOT NULL, title_transaction_before TEXT NOT NULL, title_transaction_after TEXT NOT NULL, amount_transaction_before TEXT NOT NULL, amount_transaction_after TEXT NOT NULL, default_category_fk TEXT NOT NULL, wallet_fk TEXT NOT NULL DEFAULT '0', ignore INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE delete_logs (delete_log_pk TEXT PRIMARY KEY NOT NULL, entry_pk TEXT NOT NULL, type INTEGER NOT NULL, date_time_modified INTEGER NOT NULL)""",
    )

    const val DAY = 86_400L
    /** 2026-04-01 00:00:00 UTC in epoch seconds. */
    const val T0 = 1_775_001_600L

    fun create(file: File, version: Int = 48, populate: (Connection) -> Unit = { sampleBook(it) }, skipTables: Set<String> = emptySet()): File {
        file.delete()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
            c.createStatement().use { st ->
                ddl.filterNot { stmt -> skipTables.any { stmt.startsWith("CREATE TABLE $it ") } }.forEach(st::execute)
                st.execute("PRAGMA user_version = $version")
            }
            populate(c)
        }
        return file
    }

    fun exec(c: Connection, sql: String, vararg args: Any?) = c.prepareStatement(sql).use { ps ->
        args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }; ps.executeUpdate()
    }

    fun settings(c: Connection, json: String) = exec(c, "INSERT INTO app_settings VALUES (0, ?, ?)", json, T0)

    /**
     * One account (id "0") plus a card, a food and a travel category with a subcategory, a split-sized set of
     * amounts (613.34 + 613.33 + 613.33), a lent entry, an upcoming entry, a transfer pair and a goal.
     * Transaction `type` is NULL on ordinary rows, as on the real backup.
     */
    fun sampleBook(c: Connection) {
        exec(c, "INSERT INTO wallets VALUES ('0','HDFC','0xff1b6b4a','bank.png',?,?,0,'inr','₹',2,'[0,1]','uid-1')", T0, T0)
        exec(c, "INSERT INTO wallets VALUES ('w2','Card','0xffd9381e','card.png',?,NULL,1,'inr',NULL,2,NULL,NULL)", T0)
        exec(c, "INSERT INTO categories VALUES ('c-food','Food',NULL,'food.png',NULL,?,NULL,0,0,NULL,NULL)", T0)
        exec(c, "INSERT INTO categories VALUES ('c-rest','Restaurants',NULL,'rest.png',NULL,?,NULL,0,0,3,'c-food')", T0)
        exec(c, "INSERT INTO categories VALUES ('c-pay','Salary',NULL,'pay.png',NULL,?,NULL,1,1,NULL,NULL)", T0)
        fun tx(id: String, amount: Double, cat: String, wallet: String, date: Long, type: Int? = null, extra: String = "") =
            exec(c, "INSERT INTO transactions (transaction_pk,name,amount,note,category_fk,wallet_fk,date_created,income,paid,skip_paid,type,is_private,budget_fks_exclude,paired_transaction_fk,objective_fk) VALUES (?,?,?,?,?,?,?,?,1,0,?,0,?,?,?)",
                id, "t-$id", amount, "", cat, wallet, date, if (amount > 0) 1 else 0, type,
                if (id == "t1") "[\"b1\"]" else null, null, null)
        tx("t1", -613.34, "c-food", "0", T0 + DAY)
        tx("t2", -613.33, "c-food", "0", T0 + 2 * DAY)
        tx("t3", -613.33, "c-rest", "0", T0 + 3 * DAY)
        tx("t4", 60_000.0, "c-pay", "0", T0 + 4 * DAY)
        tx("t5", -2_000.0, "c-food", "0", T0 + 5 * DAY, type = 3)   // lent (credit)
        tx("t6", -1_499.0, "c-food", "w2", T0 + 6 * DAY, type = 0)  // upcoming
        tx("t7", -5_000.0, "c-food", "0", T0 + 7 * DAY)
        tx("t8", 5_000.0, "c-food", "w2", T0 + 7 * DAY)
        exec(c, "UPDATE transactions SET paired_transaction_fk='t8' WHERE transaction_pk='t7'")
        exec(c, "UPDATE transactions SET paired_transaction_fk='t7' WHERE transaction_pk='t8'")
        exec(c, "INSERT INTO budgets (budget_pk,name,amount,colour,start_date,end_date,wallet_fks,category_fks,income,archived,added_transactions_only,period_length,reoccurrence,date_created,pinned,\"order\",wallet_fk,budget_transaction_filters,is_absolute_spending_limit) VALUES ('b1','Monthly',60000.0,NULL,?,?,'[\"0\",\"w2\"]','[\"c-food\"]',0,0,0,1,3,?,1,0,'0','[5]',0)", T0, T0 + 30 * DAY, T0)
        exec(c, "INSERT INTO category_budget_limits VALUES ('l1','c-food','b1',12000.5,NULL,'0')")
        exec(c, "INSERT INTO objectives VALUES ('o1',1,'Bike loan',45000.0,0,NULL,?,NULL,NULL,NULL,NULL,0,1,0,'0')", T0)
        exec(c, "INSERT INTO associated_titles VALUES ('a1','c-rest','Zomato',?,NULL,0,0)", T0)
        exec(c, "INSERT INTO scanner_templates VALUES ('s1',?,NULL,'HDFC UPI','debited','Info:','on','Rs.','from','c-food','0',0)", T0)
        exec(c, "INSERT INTO delete_logs VALUES ('d1','gone-1',4,?)", T0 + 9 * DAY)
        settings(c, SAMPLE_SETTINGS)
    }

    val SAMPLE_SETTINGS = """
    {"theme":"dark","accentColor":"0xff1b6b4a","hasSignedIn":true,"lastBackup":"2026-11-01","householdId":"hh1","settingsModified":{"theme":1},
     "loanTerms":{"o1":{"principal":45000.0,"rate":12.5,"months":12,"emi":4010.5,"first":"2026-05-05T00:00:00.000","fee":450.0,"gst":18,"feePaid":true,"paid":[3,1,2],"wallet":"0","note":"bike","_u":1790000000000,"_d":false},
                 "gone":{"_d":true,"_u":1790000000001}},
     "fixedDeposits":[{"id":"fd1","bank":"HDFC","no":"123","principal":100000.0,"rate":7.25,"start":"2026-01-10T00:00:00.000","maturity":"2027-01-10T00:00:00.000","comp":4,"maturityAmount":107450.55,"wallet":"0","closed":false,"note":"","sms":true,"smsId":"sms9","_u":1790000000002,"_d":false},
                      {"id":"fd-old","_d":true,"_u":1}],
     "cardDues":[{"id":"cd1","card":"HDFC Millennia","kind":"spend","amount":1499.0,"date":"2026-04-07T10:30:00.000","note":"","_u":1790000000003,"_d":false},
                 {"id":"cd2","card":"HDFC Millennia","kind":"payment","amount":500.25,"date":"2026-04-20T00:00:00.000Z","note":"","_u":1790000000004,"_d":false}]}
    """.trimIndent()
}
