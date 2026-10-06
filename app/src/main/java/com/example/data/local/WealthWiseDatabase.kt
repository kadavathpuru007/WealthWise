package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
        GoalEntity::class,
        GoalAllocationEntity::class,
        BillEntity::class,
        SubscriptionEntity::class,
        NotificationEntity::class,
        LoanEntity::class,
        TradeEntity::class,
        LoanRepaymentEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class WealthWiseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalDao(): GoalDao
    abstract fun billDao(): BillDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun loanDao(): LoanDao
    abstract fun tradeDao(): TradeDao

    companion object {
        @Volatile
        private var INSTANCE: WealthWiseDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN eventType TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN referenceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourceAccount TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN destinationAccount TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN originalTransactionId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN merchantName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN amount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN billingPeriod TEXT NOT NULL DEFAULT 'MONTHLY'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN category TEXT NOT NULL DEFAULT 'Subscription'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN nextExpectedPaymentDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN lastConfirmedPaymentDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN lastConfirmedAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN linkedPackageName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN usageTrackingAvailable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN usagePeriodDays INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN usageDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN previousAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE subscriptions SET amount = monthlyCost WHERE amount = 0.0")
                db.execSQL("UPDATE subscriptions SET merchantName = name WHERE merchantName = ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS loans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL DEFAULT '',
                        title TEXT NOT NULL,
                        lender TEXT NOT NULL,
                        principal REAL NOT NULL,
                        remaining REAL NOT NULL,
                        interestRate REAL NOT NULL,
                        monthlyEmi REAL NOT NULL,
                        nextDueDate TEXT NOT NULL,
                        tenureMonthsLeft INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId_timestamp ON transactions(userId, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId_referenceId ON transactions(userId, referenceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId_fingerprint ON transactions(userId, fingerprint)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN timelineMonths INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE goals ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS goal_allocations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        userId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_allocations_userId ON goal_allocations(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_allocations_goalId ON goal_allocations(goalId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN lastChargedDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE subscriptions SET lastChargedDate = createdAt WHERE lastChargedDate = 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL,
                        budgetLimit REAL NOT NULL DEFAULT 0.0,
                        colorHex TEXT NOT NULL DEFAULT '#EF4444'
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_userId ON categories(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_userId_name ON categories(userId, name)")
                db.execSQL("""
                    INSERT INTO categories (userId, name, budgetLimit, colorHex)
                    SELECT userId, category, monthlyLimit, colorHex FROM budgets
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trades (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        tradeType TEXT NOT NULL,
                        buyPrice REAL NOT NULL,
                        sellPrice REAL NOT NULL,
                        quantity INTEGER NOT NULL,
                        realizedPnl REAL NOT NULL,
                        tradeDate INTEGER NOT NULL,
                        brokerName TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trades_userId ON trades(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trades_tradeDate ON trades(tradeDate)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create loans_new table with new schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `loans_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `loanName` TEXT NOT NULL,
                        `lenderName` TEXT NOT NULL,
                        `loanType` TEXT NOT NULL,
                        `principalAmount` REAL NOT NULL,
                        `emiAmount` REAL NOT NULL,
                        `interestRate` REAL NOT NULL,
                        `totalTenureMonths` INTEGER NOT NULL,
                        `emiDueDayOfMonth` INTEGER NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `isClosed` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Check if existing loans table has old column names
                var hasOldColumns = false
                try {
                    val cursor = db.query("PRAGMA table_info(loans)")
                    while (cursor.moveToNext()) {
                        val colName = cursor.getString(1)
                        if (colName == "title" || colName == "lender") {
                            hasOldColumns = true
                            break
                        }
                    }
                    cursor.close()
                } catch (_: Throwable) {}

                if (hasOldColumns) {
                    db.execSQL("""
                        INSERT INTO `loans_new` (
                            `id`, `userId`, `loanName`, `lenderName`, `loanType`,
                            `principalAmount`, `emiAmount`, `interestRate`,
                            `totalTenureMonths`, `emiDueDayOfMonth`, `startDate`, `isClosed`
                        )
                        SELECT 
                            `id`, 
                            `userId`, 
                            `title`, 
                            `lender`, 
                            'PERSONAL', 
                            `principal`, 
                            `monthlyEmi`, 
                            `interestRate`, 
                            `tenureMonthsLeft`, 
                            1, 
                            0, 
                            CASE WHEN `remaining` <= 0 THEN 1 ELSE 0 END
                        FROM `loans`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `loans`")
                } else {
                    db.execSQL("DROP TABLE IF EXISTS `loans`")
                }

                db.execSQL("ALTER TABLE `loans_new` RENAME TO `loans`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_userId` ON `loans` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_userId_isClosed` ON `loans` (`userId`, `isClosed`)")

                // 2. Create loan_repayments table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `loan_repayments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `loanId` INTEGER NOT NULL,
                        `userId` TEXT NOT NULL,
                        `paidAmount` REAL NOT NULL,
                        `paymentDate` INTEGER NOT NULL,
                        `transactionRef` TEXT,
                        `isAutoDetected` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_repayments_loanId` ON `loan_repayments` (`loanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_repayments_userId` ON `loan_repayments` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_repayments_paymentDate` ON `loan_repayments` (`paymentDate`)")
            }
        }

        fun getDatabase(context: Context): WealthWiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WealthWiseDatabase::class.java,
                    "wealthwise_database.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
