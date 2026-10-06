package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Crash-Proof Room AppDatabase configuration.
 * Contains all active financial entities with fallbackToDestructiveMigration().
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        GoalEntity::class,
        GoalAllocationEntity::class,
        SubscriptionEntity::class,
        TradeEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalDao(): GoalDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun tradeDao(): TradeDao
    abstract fun loanDao(): LoanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wealthwise.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Backward compatibility provider for both AppDatabase and WealthWiseDatabase.
 */
object AppDatabaseProvider {
    fun getDatabase(context: Context): WealthWiseDatabase {
        return WealthWiseDatabase.getDatabase(context)
    }

    fun getAppDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
}
