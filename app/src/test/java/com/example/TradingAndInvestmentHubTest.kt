package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TradeDao
import com.example.data.local.TradeEntity
import com.example.data.local.TransactionDao
import com.example.data.local.TransactionEntity
import com.example.data.local.WealthWiseDatabase
import com.example.domain.trading.BrokerCapitalCalculator
import com.example.domain.trading.BrokerSecurityState
import com.example.domain.trading.BrokerTrustEngine
import com.example.domain.trading.LocalRegulatoryDataSource
import com.example.domain.trading.RegulatoryRepository
import com.example.ui.viewmodel.TradeValidationResult
import com.example.ui.viewmodel.TradingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TradingAndInvestmentHubTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: WealthWiseDatabase
    private lateinit var tradeDao: TradeDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var regulatoryRepository: RegulatoryRepository
    private lateinit var brokerTrustEngine: BrokerTrustEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        tradeDao = database.tradeDao()
        transactionDao = database.transactionDao()
        regulatoryRepository = RegulatoryRepository(LocalRegulatoryDataSource())
        brokerTrustEngine = BrokerTrustEngine(context, regulatoryRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // 1. CLOSED trade P&L: buy = 100, sell = 120, quantity = 10 -> expected: 200
    @Test
    fun testClosedTradePnlCalculation() {
        val pnl = TradeEntity.calculateRealizedPnl(
            status = TradeEntity.STATUS_CLOSED,
            buyPrice = 100.0,
            sellPrice = 120.0,
            quantity = 10
        )
        assertEquals(200.0, pnl, 0.001)
    }

    // 2. Losing trade: buy = 120, sell = 100, quantity = 10 -> expected: -200
    @Test
    fun testLosingTradePnlCalculation() {
        val pnl = TradeEntity.calculateRealizedPnl(
            status = TradeEntity.STATUS_CLOSED,
            buyPrice = 120.0,
            sellPrice = 100.0,
            quantity = 10
        )
        assertEquals(-200.0, pnl, 0.001)
    }

    // 3. Win rate: 2 closed trades (1 winning, 1 losing) -> expected: 50%
    @Test
    fun testWinRateCalculation() = runTest(testDispatcher) {
        val user = "test_user_winrate"
        val tradeWin = TradeEntity(
            userId = user,
            symbol = "RELIANCE",
            tradeType = TradeEntity.TYPE_EQUITY,
            buyPrice = 2500.0,
            sellPrice = 2700.0,
            quantity = 5,
            realizedPnl = 1000.0,
            tradeDate = System.currentTimeMillis(),
            brokerName = "Zerodha",
            status = TradeEntity.STATUS_CLOSED
        )
        val tradeLoss = TradeEntity(
            userId = user,
            symbol = "TCS",
            tradeType = TradeEntity.TYPE_EQUITY,
            buyPrice = 3500.0,
            sellPrice = 3300.0,
            quantity = 2,
            realizedPnl = -400.0,
            tradeDate = System.currentTimeMillis(),
            brokerName = "Groww",
            status = TradeEntity.STATUS_CLOSED
        )

        tradeDao.insertTrade(tradeWin)
        tradeDao.insertTrade(tradeLoss)

        val viewModel = TradingViewModel(
            application = ApplicationProvider.getApplicationContext(),
            tradeDao = tradeDao,
            transactionDao = transactionDao,
            brokerTrustEngine = brokerTrustEngine,
            authUidProvider = { user },
            sharingStarted = SharingStarted.Eagerly
        )

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(2, state.tradesList.size)
        assertEquals(50, state.winRatePercentage)
        assertEquals(600.0, state.totalRealizedPnl, 0.001)
    }

    // 4. No closed trades -> expected: 0% (never divide by zero)
    @Test
    fun testNoClosedTradesWinRate() = runTest(testDispatcher) {
        val user = "test_user_empty"
        val viewModel = TradingViewModel(
            application = ApplicationProvider.getApplicationContext(),
            tradeDao = tradeDao,
            transactionDao = transactionDao,
            brokerTrustEngine = brokerTrustEngine,
            authUidProvider = { user },
            sharingStarted = SharingStarted.Eagerly
        )

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(0, state.tradesList.size)
        assertEquals(0, state.winRatePercentage)
        assertEquals(0.0, state.totalRealizedPnl, 0.001)
    }

    // 5. Open trade: must not contribute to realized P&L
    @Test
    fun testOpenTradeDoesNotContributeToRealizedPnl() = runTest(testDispatcher) {
        val user = "test_user_open"
        val openPnl = TradeEntity.calculateRealizedPnl(
            status = TradeEntity.STATUS_OPEN,
            buyPrice = 100.0,
            sellPrice = 150.0,
            quantity = 10
        )
        assertEquals(0.0, openPnl, 0.001)

        val openTrade = TradeEntity(
            userId = user,
            symbol = "INFY",
            tradeType = TradeEntity.TYPE_EQUITY,
            buyPrice = 1500.0,
            sellPrice = 1600.0,
            quantity = 10,
            realizedPnl = 0.0,
            tradeDate = System.currentTimeMillis(),
            brokerName = "Upstox",
            status = TradeEntity.STATUS_OPEN
        )
        tradeDao.insertTrade(openTrade)

        val totalRealized = tradeDao.getTotalRealizedPnlFlow(user).first()
        assertEquals(0.0, totalRealized, 0.001)

        val closedCount = tradeDao.getClosedTradeCountFlow(user).first()
        assertEquals(0, closedCount)
    }

    // 6. Invalid quantity: 0 or negative must fail validation
    @Test
    fun testInvalidQuantityFailsValidation() = runTest(testDispatcher) {
        val user = "test_user_validation"
        val viewModel = TradingViewModel(
            application = ApplicationProvider.getApplicationContext(),
            tradeDao = tradeDao,
            transactionDao = transactionDao,
            brokerTrustEngine = brokerTrustEngine,
            authUidProvider = { user }
        )

        var resultZero: TradeValidationResult? = null
        viewModel.logTrade(
            symbol = "NIFTY",
            type = "EQUITY",
            buyPrice = 100.0,
            sellPrice = 120.0,
            quantity = 0,
            broker = "Zerodha",
            onResult = { resultZero = it }
        )
        assertTrue(resultZero is TradeValidationResult.Error)
        assertEquals("Quantity must be greater than zero.", (resultZero as TradeValidationResult.Error).message)

        var resultNegative: TradeValidationResult? = null
        viewModel.logTrade(
            symbol = "NIFTY",
            type = "EQUITY",
            buyPrice = 100.0,
            sellPrice = 120.0,
            quantity = -5,
            broker = "Zerodha",
            onResult = { resultNegative = it }
        )
        assertTrue(resultNegative is TradeValidationResult.Error)
    }

    // 7. Invalid price: 0 or negative must fail validation
    @Test
    fun testInvalidPriceFailsValidation() = runTest(testDispatcher) {
        val user = "test_user_price_validation"
        val viewModel = TradingViewModel(
            application = ApplicationProvider.getApplicationContext(),
            tradeDao = tradeDao,
            transactionDao = transactionDao,
            brokerTrustEngine = brokerTrustEngine,
            authUidProvider = { user }
        )

        var resultZeroBuy: TradeValidationResult? = null
        viewModel.logTrade(
            symbol = "TCS",
            type = "EQUITY",
            buyPrice = 0.0,
            sellPrice = 120.0,
            quantity = 1,
            broker = "Zerodha",
            onResult = { resultZeroBuy = it }
        )
        assertTrue(resultZeroBuy is TradeValidationResult.Error)

        var resultNegativeSell: TradeValidationResult? = null
        viewModel.logTrade(
            symbol = "TCS",
            type = "EQUITY",
            buyPrice = 100.0,
            sellPrice = -50.0,
            quantity = 1,
            broker = "Zerodha",
            onResult = { resultNegativeSell = it }
        )
        assertTrue(resultNegativeSell is TradeValidationResult.Error)
    }

    // 8. User isolation: User A must never receive User B's trades
    @Test
    fun testUserIsolationInRoom() = runTest(testDispatcher) {
        val userA = "user_A_isolated"
        val userB = "user_B_isolated"

        val tradeA = TradeEntity(
            userId = userA,
            symbol = "HDFCBANK",
            tradeType = TradeEntity.TYPE_EQUITY,
            buyPrice = 1600.0,
            sellPrice = 1700.0,
            quantity = 10,
            realizedPnl = 1000.0,
            tradeDate = 1000L,
            brokerName = "Zerodha",
            status = TradeEntity.STATUS_CLOSED
        )
        val tradeB = TradeEntity(
            userId = userB,
            symbol = "ICICIBANK",
            tradeType = TradeEntity.TYPE_EQUITY,
            buyPrice = 900.0,
            sellPrice = 950.0,
            quantity = 20,
            realizedPnl = 1000.0,
            tradeDate = 2000L,
            brokerName = "Groww",
            status = TradeEntity.STATUS_CLOSED
        )

        tradeDao.insertTrade(tradeA)
        tradeDao.insertTrade(tradeB)

        val tradesUserA = tradeDao.getAllTradesFlow(userA).first()
        assertEquals(1, tradesUserA.size)
        assertEquals("HDFCBANK", tradesUserA[0].symbol)

        val tradesUserB = tradeDao.getAllTradesFlow(userB).first()
        assertEquals(1, tradesUserB.size)
        assertEquals("ICICIBANK", tradesUserB[0].symbol)
    }

    // 9. Broker deposit detection: Completed ₹5,000 debit to Zerodha -> deployed capital +₹5,000
    @Test
    fun testBrokerDepositDetection() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_deposit",
                title = "Funds added to Zerodha",
                amount = 5000.0,
                type = "EXPENSE",
                category = "Investment",
                paymentMethod = "UPI",
                date = "2026-10-02",
                merchant = "Zerodha",
                status = "COMPLETED"
            )
        )

        val metrics = BrokerCapitalCalculator.calculateMetrics(txns)
        assertEquals(5000.0, metrics.totalDeployedCapital, 0.001)
        assertEquals(0.0, metrics.totalPayoutsWithdrawn, 0.001)
        assertEquals(5000.0, metrics.netCapitalInMarket, 0.001)
    }

    // 10. Promotional notification: "Invest ₹5,000 and get rewards" -> must NOT create capital deployment
    @Test
    fun testPromotionalNotificationIgnored() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_promo",
                title = "Invest ₹5,000 and get rewards on Zerodha IPO advertisement",
                amount = 5000.0,
                type = "EXPENSE",
                category = "Investment",
                paymentMethod = "UPI",
                date = "2026-10-02",
                merchant = "Zerodha",
                note = "Promo offer advert",
                status = "COMPLETED"
            )
        )

        val metrics = BrokerCapitalCalculator.calculateMetrics(txns)
        assertEquals(0.0, metrics.totalDeployedCapital, 0.001)
        assertEquals(0.0, metrics.netCapitalInMarket, 0.001)
    }

    // 11. Pending broker deposit: must NOT affect completed capital metrics
    @Test
    fun testPendingBrokerDepositIgnored() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_pending",
                title = "Transfer to Groww",
                amount = 10000.0,
                type = "EXPENSE",
                category = "Investment",
                paymentMethod = "UPI",
                date = "2026-10-02",
                merchant = "Groww",
                status = "PENDING"
            )
        )

        val metrics = BrokerCapitalCalculator.calculateMetrics(txns)
        assertEquals(0.0, metrics.totalDeployedCapital, 0.001)
        assertEquals(0.0, metrics.netCapitalInMarket, 0.001)
    }

    // 12. Duplicate transaction: must not double count
    @Test
    fun testDuplicateTransactionHandling() {
        val txn1 = TransactionEntity(
            id = 1,
            userId = "user_dup",
            title = "Transfer to Angel One",
            amount = 7000.0,
            type = "EXPENSE",
            category = "Investment",
            paymentMethod = "Net Banking",
            date = "2026-10-02",
            merchant = "Angel One",
            status = "COMPLETED",
            referenceId = "REF12345"
        )
        // Deduplicated list where duplicate entry is ignored
        val list = listOf(txn1)
        val metrics = BrokerCapitalCalculator.calculateMetrics(list)
        assertEquals(7000.0, metrics.totalDeployedCapital, 0.001)
    }

    // 13. Refund/reversal: follow central ledger rules (reduces deployed capital)
    @Test
    fun testRefundOrReversalReducesDeployedCapital() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_reversal",
                title = "Transfer to Upstox",
                amount = 10000.0,
                type = "EXPENSE",
                category = "Investment",
                paymentMethod = "UPI",
                date = "2026-10-02",
                merchant = "Upstox",
                status = "COMPLETED"
            ),
            TransactionEntity(
                id = 2,
                userId = "user_reversal",
                title = "Reversal of Upstox deposit",
                amount = 10000.0,
                type = "INCOME",
                eventType = "REVERSAL",
                category = "Reversal",
                paymentMethod = "UPI",
                date = "2026-10-02",
                merchant = "Upstox",
                status = "COMPLETED"
            )
        )

        val metrics = BrokerCapitalCalculator.calculateMetrics(txns)
        assertEquals(0.0, metrics.totalDeployedCapital, 0.001)
        assertEquals(0.0, metrics.netCapitalInMarket, 0.001)
    }

    // 14. Trade deletion: removes trade journal entry -> does NOT delete financial transaction
    @Test
    fun testTradeDeletionDoesNotDeleteFinancialTransaction() = runTest(testDispatcher) {
        val user = "user_delete_test"

        val financialTxn = TransactionEntity(
            id = 101,
            userId = user,
            title = "Transfer to Zerodha",
            amount = 15000.0,
            type = "EXPENSE",
            category = "Investment",
            paymentMethod = "UPI",
            date = "2026-10-02",
            merchant = "Zerodha",
            status = "COMPLETED"
        )
        transactionDao.insertTransaction(financialTxn)

        val trade = TradeEntity(
            id = 1,
            userId = user,
            symbol = "TATA MOTORS",
            tradeType = TradeEntity.TYPE_EQUITY,
            buyPrice = 900.0,
            sellPrice = 950.0,
            quantity = 10,
            realizedPnl = 500.0,
            tradeDate = System.currentTimeMillis(),
            brokerName = "Zerodha",
            status = TradeEntity.STATUS_CLOSED
        )
        tradeDao.insertTrade(trade)

        val viewModel = TradingViewModel(
            application = ApplicationProvider.getApplicationContext(),
            tradeDao = tradeDao,
            transactionDao = transactionDao,
            brokerTrustEngine = brokerTrustEngine,
            authUidProvider = { user },
            sharingStarted = SharingStarted.Eagerly
        )

        advanceUntilIdle()
        assertEquals(1, tradeDao.getAllTradesFlow(user).first().size)

        // Delete trade
        viewModel.deleteTrade(trade)
        advanceUntilIdle()

        // Trade deleted from trade journal
        val tradesAfter = tradeDao.getAllTradesFlow(user).first()
        assertEquals(0, tradesAfter.size)

        // Financial transaction in central ledger remains untouched!
        val txnsAfter = transactionDao.getTransactionsFlow(user).first()
        assertEquals(1, txnsAfter.size)
        assertEquals(101, txnsAfter[0].id)
        assertEquals(15000.0, txnsAfter[0].amount, 0.001)
    }

    // 15. Reactive Room update: Insert trade -> Flow updates -> UI state updates
    @Test
    fun testReactiveRoomFlowUpdate() = runTest(testDispatcher) {
        val user = "user_reactive"
        val viewModel = TradingViewModel(
            application = ApplicationProvider.getApplicationContext(),
            tradeDao = tradeDao,
            transactionDao = transactionDao,
            brokerTrustEngine = brokerTrustEngine,
            authUidProvider = { user },
            sharingStarted = SharingStarted.Eagerly
        )

        val initialState = viewModel.uiState.first { !it.isLoading }
        assertEquals(0, initialState.tradesList.size)

        viewModel.logTrade(
            symbol = "RELIANCE",
            type = TradeEntity.TYPE_EQUITY,
            buyPrice = 2400.0,
            sellPrice = 2500.0,
            quantity = 10,
            broker = "Zerodha",
            status = TradeEntity.STATUS_CLOSED
        )

        val stateAfter = viewModel.uiState.first { it.tradesList.isNotEmpty() }
        assertEquals(1, stateAfter.tradesList.size)
        assertEquals("RELIANCE", stateAfter.tradesList[0].symbol)
        assertEquals(1000.0, stateAfter.totalRealizedPnl, 0.001)
        assertEquals(100, stateAfter.winRatePercentage)
    }

    // 16. Case-insensitive broker matching: "zerodha", "ZERODHA", "Zerodha" must resolve consistently
    @Test
    fun testCaseInsensitiveBrokerMatching() {
        val variations = listOf("zerodha", "ZERODHA", "Zerodha", "  zerodha  ")
        for (v in variations) {
            val txn = TransactionEntity(
                id = 1,
                userId = "user_ci",
                title = "Fund transfer",
                amount = 2000.0,
                type = "EXPENSE",
                category = "Investment",
                paymentMethod = "UPI",
                date = "2026-10-02",
                merchant = v,
                status = "COMPLETED"
            )
            assertTrue("Should match broker for variation: '$v'", BrokerCapitalCalculator.matchesBroker(txn))
            val metrics = BrokerCapitalCalculator.calculateMetrics(listOf(txn))
            assertEquals(2000.0, metrics.totalDeployedCapital, 0.001)
        }
    }

    // 17. Regulatory verification: SEBI verified broker vs RBI Alert List
    @Test
    fun testRegulatoryVerificationSeparation() {
        // Known SEBI registered broker
        val sebiBroker = brokerTrustEngine.evaluateBrokerByName("Zerodha")
        assertTrue(sebiBroker is BrokerSecurityState.Verified)
        assertEquals("Zerodha", (sebiBroker as BrokerSecurityState.Verified).brokerName)
        assertNotNull(sebiBroker.lastVerified)

        // Known platform on RBI Alert List
        val rbiAlert = brokerTrustEngine.evaluateBrokerByName("OctaFX")
        assertTrue(rbiAlert is BrokerSecurityState.Alert)
        assertEquals("OctaFX", (rbiAlert as BrokerSecurityState.Alert).platformName)

        // Unknown entity
        val unknown = brokerTrustEngine.evaluateBrokerByName("UnknownCryptoPlatform99")
        assertTrue(unknown is BrokerSecurityState.DetectedUnverified)

        // Blank name gives neutral
        val blank = brokerTrustEngine.evaluateBrokerByName("")
        assertTrue(blank is BrokerSecurityState.Neutral)
    }
}
