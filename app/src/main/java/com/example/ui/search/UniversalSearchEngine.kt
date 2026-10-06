package com.example.ui.search

import com.example.data.local.BudgetEntity
import com.example.data.local.GoalEntity
import com.example.data.local.LoanEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.domain.financial.MoneyUtils
import com.example.ui.viewmodel.WealthWiseUiState
import java.util.Locale

/**
 * Navigation destination targets supported by Universal Search.
 */
sealed class SearchNavigationTarget {
    object Activity : SearchNavigationTarget()
    data class ActivityFiltered(val filter: String) : SearchNavigationTarget()
    object Goals : SearchNavigationTarget()
    object Budgets : SearchNavigationTarget()
    object Investments : SearchNavigationTarget()
    object Loans : SearchNavigationTarget()
    object Subscriptions : SearchNavigationTarget()
    data class Market(val focusedSymbol: String? = null) : SearchNavigationTarget()
    object Settings : SearchNavigationTarget()
    object Profile : SearchNavigationTarget()
}

data class FeatureSearchResult(
    val title: String,
    val subtitle: String,
    val target: SearchNavigationTarget,
    val categoryLabel: String = "FEATURE",
    val keywords: List<String>
)

data class UniversalSearchResults(
    val query: String,
    val features: List<FeatureSearchResult>,
    val transactions: List<TransactionEntity>,
    val goals: List<GoalEntity>,
    val budgets: List<BudgetEntity>,
    val loans: List<LoanEntity>,
    val subscriptions: List<SubscriptionEntity>,
    val directFeatureMatch: SearchNavigationTarget? = null
) {
    val isEmpty: Boolean
        get() = features.isEmpty() &&
                transactions.isEmpty() &&
                goals.isEmpty() &&
                budgets.isEmpty() &&
                loans.isEmpty() &&
                subscriptions.isEmpty()
}

object UniversalSearchEngine {

    val ALL_FEATURES = listOf(
        FeatureSearchResult(
            title = "Activity & Ledger",
            subtitle = "All recorded financial transactions & cashflow",
            target = SearchNavigationTarget.Activity,
            keywords = listOf("transaction", "transactions", "transact", "trans", "txn", "txns", "ledger", "activity", "history", "records")
        ),
        FeatureSearchResult(
            title = "Expenses",
            subtitle = "Ledger filtered strictly for expense outflows",
            target = SearchNavigationTarget.ActivityFiltered("EXPENSE"),
            keywords = listOf("expense", "expenses", "exp", "spending", "spends", "spent", "outflow", "payments")
        ),
        FeatureSearchResult(
            title = "Income & Deposits",
            subtitle = "Ledger filtered strictly for incoming inflows",
            target = SearchNavigationTarget.ActivityFiltered("INCOME"),
            keywords = listOf("income", "incomes", "inc", "earning", "earnings", "salary", "deposit", "deposits", "inflow")
        ),
        FeatureSearchResult(
            title = "Financial Goals",
            subtitle = "Track, save, and fund milestone savings targets",
            target = SearchNavigationTarget.Goals,
            keywords = listOf("goal", "goals", "saving", "savings", "save", "target", "targets", "milestone", "milestones")
        ),
        FeatureSearchResult(
            title = "Monthly Budgets",
            subtitle = "Category limits & real spending discipline",
            target = SearchNavigationTarget.Budgets,
            keywords = listOf("budget", "budgets", "budg", "limit", "limits", "cap", "caps", "allowance", "category budget")
        ),
        FeatureSearchResult(
            title = "Investments & Wealth",
            subtitle = "Portfolio tracking & asset allocation",
            target = SearchNavigationTarget.Investments,
            keywords = listOf("investment", "investments", "invest", "portfolio", "asset", "assets", "wealth", "stock", "stocks", "mutual fund", "mf", "equity")
        ),
        FeatureSearchResult(
            title = "Loans & Debt",
            subtitle = "Track bank loans, tenures, EMIs & borrowed balances",
            target = SearchNavigationTarget.Loans,
            keywords = listOf("loan", "loans", "debt", "debts", "emi", "emis", "borrow", "borrowed", "lender", "liability", "liabilities")
        ),
        FeatureSearchResult(
            title = "Subscriptions",
            subtitle = "Recurring commitments, memberships & renewal schedule",
            target = SearchNavigationTarget.Subscriptions,
            keywords = listOf("subscription", "subscriptions", "sub", "subs", "recurring", "membership", "memberships", "saas", "killer")
        ),
        FeatureSearchResult(
            title = "Market Benchmarks",
            subtitle = "Macro benchmark quotes (NIFTY, SENSEX, Gold, Silver)",
            target = SearchNavigationTarget.Market(null),
            keywords = listOf("market", "markets", "benchmark", "benchmarks", "macro", "indices", "quote", "quotes", "trading")
        ),
        FeatureSearchResult(
            title = "Gold (XAUUSD)",
            subtitle = "Market benchmark • Spot bullion quote & TradingView chart",
            target = SearchNavigationTarget.Market("XAUUSD"),
            keywords = listOf("gold", "xau", "xauusd", "bullion", "precious metal")
        ),
        FeatureSearchResult(
            title = "Silver (XAGUSD)",
            subtitle = "Market benchmark • Spot bullion quote & TradingView chart",
            target = SearchNavigationTarget.Market("XAGUSD"),
            keywords = listOf("silver", "xag", "xagusd")
        ),
        FeatureSearchResult(
            title = "NIFTY 50 (NSE:NIFTY)",
            subtitle = "Market benchmark • National Stock Exchange index",
            target = SearchNavigationTarget.Market("NSE:NIFTY"),
            keywords = listOf("nifty", "nifty 50", "nifty50", "nse", "nse:nifty")
        ),
        FeatureSearchResult(
            title = "SENSEX (BSE:SENSEX)",
            subtitle = "Market benchmark • Bombay Stock Exchange index",
            target = SearchNavigationTarget.Market("BSE:SENSEX"),
            keywords = listOf("sensex", "bse", "bse:sensex")
        ),
        FeatureSearchResult(
            title = "Settings",
            subtitle = "Preferences, profile, and system configuration",
            target = SearchNavigationTarget.Settings,
            keywords = listOf("setting", "settings", "preference", "preferences", "config", "configuration", "more")
        ),
        FeatureSearchResult(
            title = "Profile & Sync",
            subtitle = "Notification sync status, linked accounts & profile details",
            target = SearchNavigationTarget.Profile,
            keywords = listOf("profile", "sync", "account", "accounts", "user", "notification access", "linked")
        )
    )

    fun search(rawQuery: String, state: WealthWiseUiState): UniversalSearchResults {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            return UniversalSearchResults(
                query = "",
                features = ALL_FEATURES.take(7), // show initial top shortcuts
                transactions = emptyList(),
                goals = emptyList(),
                budgets = emptyList(),
                loans = emptyList(),
                subscriptions = emptyList(),
                directFeatureMatch = null
            )
        }

        val qClean = q
            .removePrefix("₹")
            .removePrefix("rs.")
            .removePrefix("rs")
            .trim()

        // 1. Feature Matching (supports stems, singular/plural, partial words)
        val matchedFeatures = ALL_FEATURES.filter { feat ->
            feat.keywords.any { kw ->
                kw == q ||
                kw.startsWith(q) ||
                q.startsWith(kw) ||
                (q.length >= 3 && kw.contains(q))
            } || feat.title.lowercase(Locale.ROOT).contains(q)
        }

        // Direct feature match if query clearly specifies a feature
        val directMatch = matchedFeatures.firstOrNull { feat ->
            feat.keywords.any { kw -> kw == q || (q.length >= 4 && kw.startsWith(q)) }
        }?.target

        // 2. Real User Data Matching ONLY (never mock or invent)
        val matchedTxns = state.transactions.filter { txn ->
            txn.title.contains(q, ignoreCase = true) ||
            txn.merchant.contains(q, ignoreCase = true) ||
            txn.category.contains(q, ignoreCase = true) ||
            txn.paymentMethod.contains(q, ignoreCase = true) ||
            txn.note.contains(q, ignoreCase = true) ||
            (qClean.isNotEmpty() && (
                txn.amount.toInt().toString() == qClean ||
                MoneyUtils.formatInr(MoneyUtils.of(txn.amount)).contains(qClean)
            ))
        }.take(8)

        val matchedGoals = state.goals.filter { g ->
            g.title.contains(q, ignoreCase = true) ||
            g.category.contains(q, ignoreCase = true)
        }.take(4)

        val matchedBudgets = state.budgets.filter { b ->
            b.category.contains(q, ignoreCase = true)
        }.take(4)

        val matchedLoans = state.loans.filter { l ->
            l.lender.contains(q, ignoreCase = true) ||
            l.title.contains(q, ignoreCase = true)
        }.take(4)

        val matchedSubs = state.subscriptions.filter { s ->
            s.name.contains(q, ignoreCase = true) ||
            s.category.contains(q, ignoreCase = true)
        }.take(4)

        return UniversalSearchResults(
            query = rawQuery,
            features = matchedFeatures,
            transactions = matchedTxns,
            goals = matchedGoals,
            budgets = matchedBudgets,
            loans = matchedLoans,
            subscriptions = matchedSubs,
            directFeatureMatch = directMatch
        )
    }
}
