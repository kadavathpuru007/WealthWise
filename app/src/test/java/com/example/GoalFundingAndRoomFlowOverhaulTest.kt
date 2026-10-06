package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BudgetEntity
import com.example.data.local.GoalAllocationEntity
import com.example.data.local.GoalEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.WealthWiseDatabase
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.MoneyUtils
import com.example.ui.dashboard.formatInr
import com.example.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoalFundingAndRoomFlowOverhaulTest {

    private lateinit var database: WealthWiseDatabase
    private lateinit var repository: WealthWiseRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WealthWiseRepository(database, context)
        repository.startingBankBalance = 50000.0
        repository.isLoggedIn = true
        repository.hasCompletedSetup = true
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. GOAL ENTITY VERIFICATION
    @Test
    fun test1_goalEntity_structureAndFields() {
        val now = System.currentTimeMillis()
        val goal = GoalEntity(
            id = 100L,
            userId = "user_alpha",
            title = "Dream Home",
            targetAmount = 500000.0,
            timelineMonths = 24,
            savedAmount = 25000.0,
            createdAt = now
        )

        assertEquals(100L, goal.id)
        assertEquals("user_alpha", goal.userId)
        assertEquals("Dream Home", goal.title)
        assertEquals(500000.0, goal.targetAmount, 0.001)
        assertEquals(24, goal.timelineMonths)
        assertEquals(25000.0, goal.savedAmount, 0.001)
        assertEquals(now, goal.createdAt)
    }

    // 2. GOAL ALLOCATION ENTITY VERIFICATION
    @Test
    fun test2_goalAllocationEntity_structureAndFields() {
        val now = System.currentTimeMillis()
        val allocation = GoalAllocationEntity(
            id = 1L,
            goalId = 100L,
            userId = "user_alpha",
            amount = 5000.0,
            timestamp = now,
            note = "Monthly savings deposit"
        )

        assertEquals(1L, allocation.id)
        assertEquals(100L, allocation.goalId)
        assertEquals("user_alpha", allocation.userId)
        assertEquals(5000.0, allocation.amount, 0.001)
        assertEquals(now, allocation.timestamp)
        assertEquals("Monthly savings deposit", allocation.note)
    }

    // 3. ROOM REACTIVE FLOWS (DAO LEVEL)
    @Test
    fun test3_roomReactiveFlows_goalAndAllocationPersistence() = runBlocking {
        val goalDao = database.goalDao()
        val userId = "user_room_test"

        // Insert Goal
        val goal = GoalEntity(
            id = 1L,
            userId = userId,
            title = "New Laptop",
            targetAmount = 100000.0,
            timelineMonths = 10,
            savedAmount = 10000.0,
            createdAt = System.currentTimeMillis()
        )
        goalDao.insertGoal(goal)
        val goalId = 1L

        // Observe getGoalsFlow
        val goalsList = goalDao.getGoalsFlow(userId).first()
        assertEquals(1, goalsList.size)
        assertEquals("New Laptop", goalsList[0].title)

        // Initial total allocated flow
        val initialAllocated = goalDao.getTotalGoalAllocatedFlow(userId).first()
        assertEquals(0.0, initialAllocated, 0.001)

        // Insert Goal Allocation
        val alloc = GoalAllocationEntity(
            id = 0L,
            goalId = goalId,
            userId = userId,
            amount = 15000.0,
            timestamp = System.currentTimeMillis(),
            note = "Funded Goal: New Laptop"
        )
        val allocId = goalDao.insertGoalAllocation(alloc)
        assertTrue(allocId > 0)

        // Check updated total allocation flow
        val updatedAllocated = goalDao.getTotalGoalAllocatedFlow(userId).first()
        assertEquals(15000.0, updatedAllocated, 0.001)

        // Update saved amount via DAO
        goalDao.updateGoalSavedAmount(goalId, 15000.0)
        val updatedGoal = goalDao.getGoalById(goalId)
        assertNotNull(updatedGoal)
        assertEquals(25000.0, updatedGoal!!.savedAmount, 0.001)
    }

    // 4. HOME 3-ROW BALANCE CALCULATIONS
    @Test
    fun test4_home3RowBalanceCalculations() {
        val startingBalance = 50000.0

        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "user1",
                title = "Monthly Salary",
                amount = 30000.0,
                type = "INCOME",
                category = "Salary",
                paymentMethod = "Bank Transfer",
                date = "2026-10-01",
                timestamp = System.currentTimeMillis()
            ),
            TransactionEntity(
                id = 2,
                userId = "user1",
                title = "Groceries",
                amount = 4000.0,
                type = "EXPENSE",
                category = "Food",
                paymentMethod = "UPI",
                date = "2026-10-01",
                timestamp = System.currentTimeMillis()
            ),
            TransactionEntity(
                id = 3,
                userId = "user1",
                title = "Shopping",
                amount = 6000.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "Credit Card",
                date = "2026-10-01",
                timestamp = System.currentTimeMillis()
            )
        )

        val budgets = listOf(
            BudgetEntity(userId = "user1", category = "Food", monthlyLimit = 8000.0),
            BudgetEntity(userId = "user1", category = "Shopping", monthlyLimit = 10000.0)
        )

        val totalGoalsAllocated = 12000.0

        val result = HomeViewModel.computeBalances(
            transactions = txns,
            startingBalance = startingBalance,
            totalGoalsAllocated = totalGoalsAllocated,
            budgets = budgets
        )

        // Row 3: Actual Bank Balance = Starting (50,000) + Income (30,000) - Expenses (10,000) = 70,000
        assertEquals(70000.0, result.actualBalance, 0.001)

        // Row 2: Safe to Spend = Total Budget (18,000) - Category Spent (10,000) = 8,000
        assertEquals(8000.0, result.safeToSpend, 0.001)

        // Row 1: Available Balance = Actual Balance (70,000) - Total Goals Allocated (12,000) = 58,000
        assertEquals(58000.0, result.availableBalance, 0.001)
    }

    // 5. DOUBLE ACCOUNTING PREVENTION
    @Test
    fun test5_doubleAccountingPrevention_goalFundingDoesNotDoubleDeduct() {
        val startingBalance = 40000.0

        // User funded a goal of ₹5,000
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "user1",
                title = "Funded Goal: Emergency Fund",
                amount = 5000.0,
                type = "EXPENSE",
                category = "Savings Goal",
                eventType = "GOAL_ALLOCATION",
                paymentMethod = "Bank Transfer",
                date = "2026-10-01",
                timestamp = System.currentTimeMillis()
            )
        )

        val totalGoalsAllocated = 5000.0

        val result = HomeViewModel.computeBalances(
            transactions = txns,
            startingBalance = startingBalance,
            totalGoalsAllocated = totalGoalsAllocated
        )

        // Actual bank balance still holds the total cash (40,000)
        assertEquals(40000.0, result.actualBalance, 0.001)

        // Available balance reserves the 5,000 allocated to goals -> 35,000
        assertEquals(35000.0, result.availableBalance, 0.001)

        // Notice: Available balance is 35,000, NOT 30,000 (which would happen if double-deducted)
        assertFalse("Available balance must not double deduct to 30,000", result.availableBalance == 30000.0)
    }

    // 6. SMART GOAL FUNDING TARGET CALCULATION & CHIPS
    @Test
    fun test6_smartGoalFundingTargetCalculation() {
        // Goal: Target 50,000, Saved 15,000, 7 months remaining
        val targetAmount = 50000.0
        val savedAmount = 15000.0
        val timelineMonths = 7

        val remainingAmount = (targetAmount - savedAmount).coerceAtLeast(0.0)
        assertEquals(35000.0, remainingAmount, 0.001)

        val monthlyTarget = if (timelineMonths > 0) remainingAmount / timelineMonths else 0.0
        assertEquals(5000.0, monthlyTarget, 0.001)

        val roundedTarget = (ceil(monthlyTarget / 100.0) * 100.0).toInt()
        assertEquals(5000, roundedTarget)

        // If target is already completed
        val fullySaved = 50000.0
        val remZero = (targetAmount - fullySaved).coerceAtLeast(0.0)
        val monthlyZero = if (timelineMonths > 0) remZero / timelineMonths else 0.0
        val roundedZero = (ceil(monthlyZero / 100.0) * 100.0).toInt()
        assertEquals(0, roundedZero)
        assertTrue("Zero target must be rejected from chips", roundedZero <= 0)
    }

    // 7. ATOMIC REPOSITORY GOAL FUNDING WITH TRANSACTION RECORD
    @Test
    fun test7_atomicRepositoryFundGoalOperation() = runBlocking {
        val userId = "user_atomic"
        val goal = GoalEntity(
            id = 50L,
            userId = userId,
            title = "Vacation Trip",
            targetAmount = 30000.0,
            timelineMonths = 6,
            savedAmount = 5000.0,
            createdAt = System.currentTimeMillis()
        )
        database.goalDao().insertGoal(goal)

        // Fund ₹4,000
        repository.fundGoal(50L, 4000.0, userId)

        // Verify Goal savedAmount updated
        val updatedGoal = database.goalDao().getGoalById(50L)
        assertNotNull(updatedGoal)
        assertEquals(9000.0, updatedGoal!!.savedAmount, 0.001)

        // Verify GoalAllocationEntity inserted
        val totalAllocated = database.goalDao().getTotalGoalAllocated(userId)
        assertNotNull(totalAllocated)
        assertEquals(4000.0, totalAllocated!!, 0.001)

        // Verify TransactionEntity created with required fields
        val txns = database.transactionDao().getAllTransactions(userId).first()
        assertEquals(1, txns.size)
        val txn = txns[0]
        assertEquals("Funded Goal: Vacation Trip", txn.title)
        assertEquals(4000.0, txn.amount, 0.001)
        assertEquals("GOAL_TRANSFER", txn.type)
        assertEquals("Savings Goal", txn.category)
        assertTrue(txn.eventType == "GOAL_TRANSFER" || txn.eventType == "GOAL_ALLOCATION")
    }

    // 8. FUNDING INSUFFICIENT BALANCE VALIDATION
    @Test
    fun test8_insufficientBalanceRejection() = runBlocking {
        val userId = "user_broke"
        repository.setInitialBalance(1000.0, userId)

        val goal = GoalEntity(
            id = 60L,
            userId = userId,
            title = "Luxury Watch",
            targetAmount = 50000.0,
            timelineMonths = 12,
            savedAmount = 0.0
        )
        database.goalDao().insertGoal(goal)

        try {
            // Attempt to fund ₹5,000 when available balance is only ₹1,000
            repository.fundGoal(60L, 5000.0, userId)
            fail("Must block funding when amount > availableBalance")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Insufficient available balance") == true)
        }
    }

    // 9. GOAL OWNERSHIP SECURITY VALIDATION
    @Test
    fun test9_goalOwnershipValidation() = runBlocking {
        val ownerId = "real_owner"
        val hackerId = "intruder"

        val goal = GoalEntity(
            id = 70L,
            userId = ownerId,
            title = "Private Vault",
            targetAmount = 20000.0,
            savedAmount = 1000.0
        )
        database.goalDao().insertGoal(goal)

        try {
            // Hacker attempts to fund owner's goal
            repository.fundGoal(70L, 500.0, hackerId)
            fail("Must block funding goals belonging to a different user")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Unauthorized") == true)
        }
    }

    // 10. CURRENCY FORMATTING (INR)
    @Test
    fun test10_currencyFormattingInr() {
        assertEquals("₹0", formatInr(0.0))
        assertEquals("₹500", formatInr(500.0))
        assertEquals("₹1,000", formatInr(1000.0))
        assertEquals("₹2,500", formatInr(2500.0))
        assertEquals("₹50,000", formatInr(50000.0))
        assertEquals("₹1,50,000", formatInr(150000.0))
    }
}
