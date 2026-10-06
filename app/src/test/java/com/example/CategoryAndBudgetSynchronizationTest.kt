package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.repository.WealthWiseRepository
import com.example.ui.viewmodel.BudgetsViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.SuggestedCategoryUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CategoryAndBudgetSynchronizationTest {

    private lateinit var database: WealthWiseDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var repository: WealthWiseRepository
    private lateinit var context: Context

    private val userIdAlpha = "user_alpha"
    private val userIdBeta = "user_beta"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        repository = WealthWiseRepository(database, context)
        repository.userName = userIdAlpha
        repository.isLoggedIn = true
        repository.hasCompletedSetup = true
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. CATEGORY DAO REACTIVE FLOW & USER ISOLATION
    @Test
    fun test1_categoryDao_reactiveFlowAndUserIsolation() = runBlocking {
        // Insert for User Alpha
        val cat1 = CategoryEntity(userId = userIdAlpha, name = "Food & Dining", budgetLimit = 10000.0)
        val cat2 = CategoryEntity(userId = userIdAlpha, name = "Shopping", budgetLimit = 5000.0)
        categoryDao.insertCategory(cat1)
        categoryDao.insertCategory(cat2)

        // Insert for User Beta
        val catBeta = CategoryEntity(userId = userIdBeta, name = "Transportation", budgetLimit = 3000.0)
        categoryDao.insertCategory(catBeta)

        val alphaCategories = categoryDao.getCategoriesFlow(userIdAlpha).first()
        assertEquals(2, alphaCategories.size)
        assertEquals("Food & Dining", alphaCategories[0].name)
        assertEquals("Shopping", alphaCategories[1].name)

        val betaCategories = categoryDao.getCategoriesFlow(userIdBeta).first()
        assertEquals(1, betaCategories.size)
        assertEquals("Transportation", betaCategories[0].name)
    }

    // 2. TRANSACTION DAO MONTHLY SPENDING QUERY WITH POSITIVE ABS(AMOUNT)
    @Test
    fun test2_transactionDao_monthlySpendingByCategoryFlow() = runBlocking {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis

        // Insert transactions with signed or positive amounts
        transactionDao.insertTransaction(
            TransactionEntity(
                userId = userIdAlpha,
                title = "Dinner",
                amount = -450.0,
                type = "EXPENSE",
                category = "Food & Dining",
                paymentMethod = "UPI",
                date = "2026-10-01",
                timestamp = now,
                status = "COMPLETED"
            )
        )
        transactionDao.insertTransaction(
            TransactionEntity(
                userId = userIdAlpha,
                title = "Lunch",
                amount = 320.0,
                type = "EXPENSE",
                category = "Food & Dining",
                paymentMethod = "Cash",
                date = "2026-10-01",
                timestamp = now,
                status = "COMPLETED"
            )
        )
        transactionDao.insertTransaction(
            TransactionEntity(
                userId = userIdAlpha,
                title = "Groceries",
                amount = 1200.0,
                type = "EXPENSE",
                category = "Groceries",
                paymentMethod = "Debit Card",
                date = "2026-10-01",
                timestamp = now,
                status = "COMPLETED"
            )
        )

        val spending = transactionDao.getMonthlySpendingByCategoryFlow(userIdAlpha, startOfMonth).first()
        val foodSpending = spending.find { it.category == "Food & Dining" }
        assertNotNull(foodSpending)
        assertEquals(770.0, foodSpending!!.totalSpent, 0.001)

        val groceriesSpending = spending.find { it.category == "Groceries" }
        assertNotNull(groceriesSpending)
        assertEquals(1200.0, groceriesSpending!!.totalSpent, 0.001)
    }

    // 3. ZERO-SPEND PROGRESS FIX
    @Test
    fun test3_zeroSpendProgress_mustSitAtZero_not100Percent() {
        val categoryLimit = 5000.0

        val spending = HomeViewModel.computeCategorySpending("Food & Dining", categoryLimit, emptyList())
        assertEquals(0.0, spending.spent, 0.001)
        assertEquals(5000.0, spending.remaining, 0.001)
        assertEquals(0f, spending.progress, 0.001f)
        assertEquals(0, spending.percentUsed)
        assertFalse(spending.isExceeded)
        assertEquals(0.0, spending.overrun, 0.001)
    }

    // 4. BUDGET OVERAGE HANDLING: CLAMPED VISUAL PROGRESS & TRUTHFUL SPENDING
    @Test
    fun test4_budgetOverage_clampedVisualProgressAndTruthfulSpending() {
        val now = System.currentTimeMillis()
        val txns = listOf(
            TransactionEntity(
                userId = userIdAlpha,
                title = "Shopping Spree",
                amount = 6000.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "Credit Card",
                date = "2026-10-01",
                timestamp = now,
                status = "COMPLETED"
            )
        )

        val spending = HomeViewModel.computeCategorySpending("Shopping", 5000.0, txns)
        assertEquals(6000.0, spending.spent, 0.001)
        assertEquals(0.0, spending.remaining, 0.001)
        assertEquals(1f, spending.progress, 0.001f) // Clamped visual progress
        assertTrue(spending.isExceeded)
        assertEquals(1000.0, spending.overrun, 0.001) // Truthful overage
    }

    // 5. CASE-INSENSITIVE CATEGORY MATCHING WITH TRIM
    @Test
    fun test5_caseInsensitiveCategoryMatching_withTrim() {
        val now = System.currentTimeMillis()
        val txns = listOf(
            TransactionEntity(
                userId = userIdAlpha,
                title = "Dinner",
                amount = 450.0,
                type = "EXPENSE",
                category = "  food & dining  ",
                paymentMethod = "UPI",
                date = "2026-10-01",
                timestamp = now,
                status = "COMPLETED"
            )
        )

        val spending = HomeViewModel.computeCategorySpending("Food & Dining", 5000.0, txns)
        assertEquals(450.0, spending.spent, 0.001)
        assertEquals(4550.0, spending.remaining, 0.001)
        assertEquals(9, spending.percentUsed)
    }

    // 6. SUGGESTED CATEGORIES FROM LAST 30 DAYS TRANSACTIONS
    @Test
    fun test6_suggestedCategories_calculatedFromRecentSpending() = runBlocking {
        val now = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L

        // Transactions in last 30 days for new categories
        transactionDao.insertTransaction(
            TransactionEntity(
                userId = userIdAlpha,
                title = "Petrol",
                amount = 2430.0,
                type = "EXPENSE",
                category = "Fuel",
                paymentMethod = "UPI",
                date = "2026-10-01",
                timestamp = now - (5 * dayMillis),
                status = "COMPLETED"
            )
        )
        transactionDao.insertTransaction(
            TransactionEntity(
                userId = userIdAlpha,
                title = "Netflix",
                amount = 649.0,
                type = "EXPENSE",
                category = "Subscriptions",
                paymentMethod = "Credit Card",
                date = "2026-10-01",
                timestamp = now - (10 * dayMillis),
                status = "COMPLETED"
            )
        )

        // Seed an existing category (so it is not suggested)
        categoryDao.insertCategory(CategoryEntity(userId = userIdAlpha, name = "Food", budgetLimit = 5000.0))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val budgetsViewModel = BudgetsViewModel(app, repository)

        val state = budgetsViewModel.uiState.first { !it.isLoading }
        val suggestions = state.suggestedCategories

        assertTrue(suggestions.isNotEmpty())
        val fuelSuggestion = suggestions.find { it.name.equals("Fuel", ignoreCase = true) }
        assertNotNull(fuelSuggestion)
        assertEquals(2430.0, fuelSuggestion!!.averageMonthlySpend, 0.001)
        assertEquals(2500.0, fuelSuggestion.suggestedLimit, 0.001) // ceil(2430 / 100) * 100

        val subSuggestion = suggestions.find { it.name.equals("Subscriptions", ignoreCase = true) }
        assertNotNull(subSuggestion)
        assertEquals(649.0, subSuggestion!!.averageMonthlySpend, 0.001)
        assertEquals(700.0, subSuggestion.suggestedLimit, 0.001) // ceil(649 / 100) * 100

        // Existing category "Food" should not be suggested
        assertNull(suggestions.find { it.name.equals("Food", ignoreCase = true) })
    }

    // 7. DUPLICATE CATEGORY PREVENTION (CASE-INSENSITIVE)
    @Test
    fun test7_duplicateCategoryPrevention_addAndUpdateValidation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val budgetsViewModel = BudgetsViewModel(app, repository)

        // Add "Food & Dining"
        budgetsViewModel.addCategory("Food & Dining", 5000.0)

        val state1 = budgetsViewModel.uiState.first { it.categories.any { cat -> cat.name == "Food & Dining" } }
        val foodCat = state1.categories.first { it.name == "Food & Dining" }

        // Attempt to add duplicate "  food & dining  "
        budgetsViewModel.addCategory("  food & dining  ", 6000.0)
        val state2 = budgetsViewModel.uiState.first { it.errorMessage != null }
        assertNotNull(state2.errorMessage)
        assertTrue(state2.errorMessage!!.contains("already exists", ignoreCase = true))

        // Update Food & Dining keeping its own name -> should succeed!
        budgetsViewModel.clearError()
        budgetsViewModel.updateCategory(foodCat.id, "Food & Dining", 7000.0)
        val state3 = budgetsViewModel.uiState.first { it.categories.any { cat -> cat.id == foodCat.id && cat.budgetLimit == 7000.0 } }
        assertNull(state3.errorMessage)
        assertEquals(7000.0, state3.categories.first { it.id == foodCat.id }.budgetLimit, 0.001)
    }

    // 8. QUICK ADD SUGGESTED CATEGORY
    @Test
    fun test8_quickAddSuggestedCategory_insertsToRoomAndEmitsImmediately() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val budgetsViewModel = BudgetsViewModel(app, repository)

        val suggested = SuggestedCategoryUiModel(
            name = "Health & Fitness",
            averageMonthlySpend = 2400.0,
            suggestedLimit = 2500.0,
            transactionCount = 3
        )

        budgetsViewModel.quickAddSuggestedCategory(suggested)

        val updatedState = budgetsViewModel.uiState.first { state ->
            state.categories.any { it.name == "Health & Fitness" }
        }
        val addedCat = updatedState.categories.first { it.name == "Health & Fitness" }
        assertEquals("Health & Fitness", addedCat.name)
        assertEquals(2500.0, addedCat.budgetLimit, 0.001)

        // Verify it was stored in Room
        val fromDb = categoryDao.getCategories(userIdAlpha)
        assertTrue(fromDb.any { it.name == "Health & Fitness" && it.budgetLimit == 2500.0 })
    }

    // 9. DELETE CATEGORY
    @Test
    fun test9_deleteCategory_removesFromRoomAndEmitsImmediately() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val budgetsViewModel = BudgetsViewModel(app, repository)

        budgetsViewModel.addCategory("Entertainment", 3000.0)
        val stateWithCat = budgetsViewModel.uiState.first { state ->
            state.categories.any { it.name == "Entertainment" }
        }
        val cat = stateWithCat.categories.first { it.name == "Entertainment" }

        budgetsViewModel.deleteCategory(cat.id)

        val stateAfterDelete = budgetsViewModel.uiState.first { state ->
            state.categories.none { it.name == "Entertainment" }
        }
        assertTrue(stateAfterDelete.categories.none { it.name == "Entertainment" })
    }
}
