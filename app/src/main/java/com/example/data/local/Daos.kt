package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow



@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :userId")
    fun getAllBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Query("DELETE FROM budgets WHERE category = :category AND userId = :userId")
    suspend fun deleteByCategory(category: String, userId: String)
}

@Dao
interface GoalDao {
    @Query("""
        SELECT * FROM goals
        WHERE userId = :userId
        ORDER BY createdAt DESC
    """)
    fun getGoalsFlow(userId: String): Flow<List<GoalEntity>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM goal_allocations
        WHERE userId = :userId
    """)
    fun getTotalGoalAllocatedFlow(userId: String): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM goal_allocations
        WHERE userId = :userId
    """)
    suspend fun getTotalGoalAllocated(userId: String): Double?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoalAllocation(allocation: GoalAllocationEntity): Long

    @Query("""
        UPDATE goals
        SET savedAmount = savedAmount + :amount
        WHERE id = :goalId
    """)
    suspend fun updateGoalSavedAmount(goalId: Long, amount: Double)

    @Query("SELECT * FROM goals WHERE userId = :userId")
    fun getAllGoals(userId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals")
    fun getAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: Long): GoalEntity?

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: Int): GoalEntity? = getGoalById(id.toLong())

    @Query("SELECT * FROM goals WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getGoalById(id: Long, userId: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getGoalById(id: Int, userId: String): GoalEntity? = getGoalById(id.toLong(), userId)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("UPDATE goals SET savedAmount = savedAmount + :amount WHERE id = :id")
    suspend fun addFundsToGoal(id: Long, amount: Double)

    suspend fun addFundsToGoal(id: Int, amount: Double) = addFundsToGoal(id.toLong(), amount)

    @Query("DELETE FROM goals WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills WHERE userId = :userId ORDER BY dueDate ASC")
    fun getAllBills(userId: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    fun getAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getBillById(id: Int): BillEntity?

    @Query("SELECT * FROM bills WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getBillById(id: Int, userId: String): BillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBill(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(bills: List<BillEntity>)

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Query("UPDATE bills SET isPaid = 1 WHERE id = :id")
    suspend fun markAsPaid(id: Int)

    @Query("DELETE FROM bills WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}



@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Int)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllAsRead(userId: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Int)
}
