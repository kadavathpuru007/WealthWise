package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Subscriptions in WealthWise.
 * Reactive Flow emits on insert, update, or delete.
 */
@Dao
interface SubscriptionDao {

    @Query("""
        SELECT * FROM subscriptions
        WHERE userId = :userId
        ORDER BY lastChargedDate DESC, createdAt DESC
    """)
    fun getSubscriptionsFlow(
        userId: String
    ): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE userId = :userId ORDER BY lastChargedDate DESC, createdAt DESC")
    fun getAllSubscriptions(userId: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY lastChargedDate DESC, createdAt DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionById(id: Int): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getSubscriptionById(id: Int, userId: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE subscriptions SET daysUnused = :daysUnused, isUnusedFlagged = :isUnusedFlagged, usageDays = :usageDays, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUsage(id: Int, daysUnused: Int, isUnusedFlagged: Boolean, usageDays: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE subscriptions SET lastConfirmedPaymentDate = :date, lastConfirmedAmount = :amount, nextExpectedPaymentDate = :nextDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun recordPayment(id: Int, date: String, amount: Double, nextDate: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: Int)

    @Query("DELETE FROM subscriptions WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()
}
