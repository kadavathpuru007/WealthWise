package com.example.data.local

import kotlinx.coroutines.flow.Flow
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class CategoryRawSpent(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "totalSpent") val totalSpent: Double
)

@Dao
interface TransactionDao {
    @Query("""
        SELECT category, SUM(ABS(amount)) AS totalSpent 
        FROM transactions 
        WHERE userId = :userId 
        AND (UPPER(type) = 'EXPENSE' OR UPPER(type) = 'DEBIT') 
        AND timestamp >= :startOfMonth 
        GROUP BY category
    """)
    fun getMonthlySpendingByCategoryFlow(
        userId: String,
        startOfMonth: Long
    ): Flow<List<CategoryRawSpent>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getTransactionsFlow(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND timestamp >= :startTime ORDER BY timestamp ASC")
    fun getTransactionsSinceFlow(userId: String, startTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(userId: String, limit: Int = 10): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE fingerprint = :fingerprint AND userId = :userId LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String, userId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getRecentTransactionsSince(userId: String, startTime: Long): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Int): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getTransactionById(id: Int, userId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE referenceId = :referenceId LIMIT 1")
    suspend fun findByReferenceId(referenceId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE referenceId = :referenceId AND userId = :userId LIMIT 1")
    suspend fun findByReferenceId(referenceId: String, userId: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Int, userId: String)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}