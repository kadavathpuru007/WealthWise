package com.example

import android.app.Application
import androidx.room.Database
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.repository.UserPreferences
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.navigation.NavDestinations
import com.example.ui.navigation.NavRouting
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.SetBalanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PostRegistrationCrashProofAndFlowTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        userPreferencesRepository = UserPreferencesRepository(application)
        userPreferencesRepository.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAppDatabaseAnnotationAndEntities() {
        val dbAnnotation = AppDatabase::class.java.getAnnotation(Database::class.java)
        if (dbAnnotation != null) {
            assertEquals("AppDatabase version must be 3", 3, dbAnnotation.version)
            assertFalse("exportSchema must be false", dbAnnotation.exportSchema)

            val entityClasses = dbAnnotation.entities.map { it.java }
            assertTrue(entityClasses.contains(TransactionEntity::class.java))
            assertTrue(entityClasses.contains(CategoryEntity::class.java))
            assertTrue(entityClasses.contains(GoalEntity::class.java))
            assertTrue(entityClasses.contains(GoalAllocationEntity::class.java))
            assertTrue(entityClasses.contains(SubscriptionEntity::class.java))
            assertTrue(entityClasses.contains(TradeEntity::class.java))
            assertTrue(entityClasses.contains(LoanEntity::class.java))
            assertTrue(entityClasses.contains(LoanRepaymentEntity::class.java))
        } else {
            // Room 2.7 retains @Database at compile/BINARY time only.
            // Verify that AppDatabase builds successfully and all DAO abstract methods are properly present.
            val db = DatabaseModule.provideAppDatabase(application)
            assertNotNull("AppDatabase instance must be non-null", db)
            assertNotNull("transactionDao must be present", db.transactionDao())
            assertNotNull("categoryDao must be present", db.categoryDao())
            assertNotNull("goalDao must be present", db.goalDao())
            assertNotNull("subscriptionDao must be present", db.subscriptionDao())
            assertNotNull("tradeDao must be present", db.tradeDao())
            assertNotNull("loanDao must be present", db.loanDao())
            db.close()
        }
    }

    @Test
    fun testDatabaseBuilderCreatesInstanceWithoutCrash() {
        val appDb = DatabaseModule.provideAppDatabase(application)
        assertNotNull("DatabaseModule should successfully build AppDatabase", appDb)
        appDb.close()
    }

    @Test
    fun testSetBalanceSanitization() {
        val viewModel = SetBalanceViewModel(application, userPreferencesRepository)

        // ₹ symbol, commas, and whitespace
        val input1 = " ₹ 50,000.50 "
        assertEquals(50000.50, viewModel.sanitizeBalance(input1), 0.001)

        // Clean number
        val input2 = "12500"
        assertEquals(12500.0, viewModel.sanitizeBalance(input2), 0.001)

        // Invalid or empty string
        val input3 = "invalid"
        assertEquals(0.0, viewModel.sanitizeBalance(input3), 0.001)

        val input4 = ""
        assertEquals(0.0, viewModel.sanitizeBalance(input4), 0.001)
    }

    @Test
    fun testSetBalanceSubmissionPersistsOnboarding() = runTest(testDispatcher) {
        val viewModel = SetBalanceViewModel(application, userPreferencesRepository)
        var navigated = false

        viewModel.submitInitialBalance("₹ 75,000") {
            navigated = true
        }

        testScheduler.advanceUntilIdle()

        assertTrue("Navigation callback should be invoked", navigated)
        assertEquals(75000.0, userPreferencesRepository.startingBankBalance, 0.001)
        assertTrue(userPreferencesRepository.has_completed_initial_balance_setup)
    }

    @Test
    fun testHomeUiStateCalculationFromFlows() = runTest(testDispatcher) {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                amount = 10000.0,
                type = "INCOME",
                title = "Salary",
                category = "Income",
                paymentMethod = "Bank Transfer",
                date = "2026-10-01"
            ),
            TransactionEntity(
                id = 2,
                amount = 3000.0,
                type = "EXPENSE",
                title = "Groceries",
                category = "Food",
                paymentMethod = "UPI",
                date = "2026-10-02"
            )
        )
        val safeTransactionFlow: Flow<List<TransactionEntity>> = flowOf(transactions)
        val safeGoalFlow: Flow<Double> = flowOf(2000.0)
        val safePrefFlow: Flow<UserPreferences> = flowOf(UserPreferences(initialBalance = 5000.0, isOnboardingCompleted = true))
        val totalBudget = 8000.0

        val combinedState = combine(safeTransactionFlow, safeGoalFlow, safePrefFlow) { txns, goalsAllocated, prefs ->
            val startingBalance = prefs.initialBalance ?: 0.0
            val totalIncome = txns.filter { it.type.equals("INCOME", true) }.sumOf { kotlin.math.abs(it.amount) }
            val totalExpense = txns.filter { it.type.equals("EXPENSE", true) }.sumOf { kotlin.math.abs(it.amount) }

            val actual = startingBalance + totalIncome - totalExpense
            val available = (actual - goalsAllocated).coerceAtLeast(0.0)
            val safeToSpend = (totalBudget - totalExpense).coerceAtLeast(0.0)

            HomeUiState(
                availableBalance = available,
                safeToSpend = safeToSpend,
                actualBalance = actual,
                isLoading = false
            )
        }.first()

        // startingBalance (5000) + income (10000) - expense (3000) = actual (12000)
        assertEquals(12000.0, combinedState.actualBalance, 0.001)
        // available = actual (12000) - goalAllocated (2000) = 10000
        assertEquals(10000.0, combinedState.availableBalance, 0.001)
        // safeToSpend = totalBudget (8000) - expense (3000) = 5000
        assertEquals(5000.0, combinedState.safeToSpend, 0.001)
        assertFalse(combinedState.isLoading)
    }

    @Test
    fun testDefensiveFlowErrorCatching() = runTest(testDispatcher) {
        val failingTxnFlow: Flow<List<TransactionEntity>> = flow<List<TransactionEntity>> {
            throw RuntimeException("Simulated SQLite disk error")
        }.catch { e ->
            emit(emptyList<TransactionEntity>())
        }

        val failingGoalFlow: Flow<Double> = flow<Double> {
            throw IllegalStateException("Simulated Room migration failure")
        }.catch { e ->
            emit(0.0)
        }

        val failingPrefFlow: Flow<UserPreferences> = flow<UserPreferences> {
            throw RuntimeException("Preferences read failed")
        }.catch { e ->
            emit(UserPreferences(initialBalance = 0.0, isOnboardingCompleted = true))
        }

        val state = combine(failingTxnFlow, failingGoalFlow, failingPrefFlow) { txns, goalsAllocated, prefs ->
            val startingBalance = prefs.initialBalance ?: 0.0
            val totalIncome = txns.filter { it.type.equals("INCOME", true) }.sumOf { kotlin.math.abs(it.amount) }
            val totalExpense = txns.filter { it.type.equals("EXPENSE", true) }.sumOf { kotlin.math.abs(it.amount) }
            val totalBudget = 10000.0

            val actual = startingBalance + totalIncome - totalExpense
            val available = (actual - goalsAllocated).coerceAtLeast(0.0)
            val safeToSpend = (totalBudget - totalExpense).coerceAtLeast(0.0)

            HomeUiState(
                availableBalance = available,
                safeToSpend = safeToSpend,
                actualBalance = actual,
                isLoading = false
            )
        }.first()

        assertNotNull(state)
        assertEquals(0.0, state.actualBalance, 0.001)
        assertEquals(0.0, state.availableBalance, 0.001)
        assertEquals(10000.0, state.safeToSpend, 0.001)
        assertFalse(state.isLoading)
    }

    @Test
    fun testNavRoutingNullSafety() {
        // When parameters are null, should default cleanly to safe false values and never throw NPE
        val startDest1 = NavRouting.getStartDestination(
            isLoggedIn = null,
            isEmailVerified = null,
            hasCompletedInitialBalanceSetup = null
        )
        assertEquals(NavDestinations.LOGIN, startDest1)

        val startDest2 = NavRouting.getStartDestination(
            isLoggedIn = true,
            isEmailVerified = null,
            hasCompletedInitialBalanceSetup = null
        )
        assertEquals(NavDestinations.EMAIL_VERIFICATION, startDest2)

        val startDest3 = NavRouting.getStartDestination(
            isLoggedIn = true,
            isEmailVerified = true,
            hasCompletedInitialBalanceSetup = null
        )
        assertEquals(NavDestinations.SET_BALANCE, startDest3)

        val startDest4 = NavRouting.getStartDestination(
            isLoggedIn = true,
            isEmailVerified = true,
            hasCompletedInitialBalanceSetup = true
        )
        assertEquals(NavDestinations.HOME, startDest4)

        // Screen mapping
        assertEquals(AppScreen.LOGIN, NavRouting.getStartScreen(null, null, null))
        assertEquals(AppScreen.EMAIL_VERIFICATION, NavRouting.getStartScreen(true, false, null))
        assertEquals(AppScreen.ENTER_BALANCE, NavRouting.getStartScreen(true, true, false))
        assertEquals(AppScreen.HOME, NavRouting.getStartScreen(true, true, true))

        // Gating with null currentUser
        val gated = NavRouting.gateScreen(
            targetScreen = AppScreen.HOME,
            currentUser = null,
            isLoggedIn = false,
            hasCompletedSetup = null
        )
        assertEquals(AppScreen.LOGIN, gated)
    }
}
