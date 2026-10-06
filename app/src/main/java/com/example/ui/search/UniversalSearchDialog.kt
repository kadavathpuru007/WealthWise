package com.example.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.BudgetEntity
import com.example.data.local.GoalEntity
import com.example.data.local.LoanEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*
import com.example.ui.viewmodel.WealthWiseUiState

@Composable
fun UniversalSearchDialog(
    state: WealthWiseUiState,
    initialQuery: String = "",
    onDismiss: () -> Unit,
    onNavigateTarget: (SearchNavigationTarget) -> Unit,
    onSelectTransaction: (TransactionEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf(initialQuery) }

    val results = remember(searchQuery, state) {
        UniversalSearchEngine.search(searchQuery, state)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // Top Search Input Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Search",
                            tint = TextNavy
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        placeholder = {
                            Text(
                                text = "Search transactions, goals, budgets, loans...",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = FintechNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = TextBlueGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FintechBlue,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            cursorColor = FintechBlue
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (results.directFeatureMatch != null) {
                                    onNavigateTarget(results.directFeatureMatch)
                                    onDismiss()
                                } else if (results.features.isNotEmpty()) {
                                    onNavigateTarget(results.features.first().target)
                                    onDismiss()
                                }
                            }
                        )
                    )
                }

                HorizontalDivider(color = BorderLight, thickness = 1.dp)

                // Results Body
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Empty state when search query yielded no results
                    if (searchQuery.trim().isNotEmpty() && results.isEmpty) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(FintechBlueBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        tint = TextBlueGrey,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No results found for \"${searchQuery.trim()}\"",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextNavy
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Search uses only your real data. Try searching for features like 'Goals', 'Market', 'Loans', or real merchant names.",
                                    fontSize = 12.sp,
                                    color = TextBlueGrey,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }

                    // --- 1. FEATURES SECTION ---
                    if (results.features.isNotEmpty()) {
                        item {
                            Text(
                                text = if (searchQuery.isBlank()) "QUICK NAVIGATION" else "MATCHING FEATURES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextBlueGrey,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }

                        items(results.features) { feat ->
                            FeatureItemRow(
                                feature = feat,
                                onClick = {
                                    onNavigateTarget(feat.target)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // --- 2. REAL TRANSACTIONS SECTION ---
                    if (results.transactions.isNotEmpty()) {
                        item {
                            Text(
                                text = "TRANSACTIONS (${results.transactions.size} REAL MATCHES)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextBlueGrey,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }

                        items(results.transactions) { txn ->
                            SearchTransactionRow(
                                txn = txn,
                                onClick = {
                                    onSelectTransaction(txn)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // --- 3. REAL GOALS SECTION ---
                    if (results.goals.isNotEmpty()) {
                        item {
                            Text(
                                text = "GOALS (${results.goals.size} MATCHES)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextBlueGrey,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }

                        items(results.goals) { goal ->
                            SearchGoalRow(
                                goal = goal,
                                onClick = {
                                    onNavigateTarget(SearchNavigationTarget.Goals)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // --- 4. REAL BUDGETS SECTION ---
                    if (results.budgets.isNotEmpty()) {
                        item {
                            Text(
                                text = "BUDGETS (${results.budgets.size} MATCHES)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextBlueGrey,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }

                        items(results.budgets) { budget ->
                            SearchBudgetRow(
                                budget = budget,
                                onClick = {
                                    onNavigateTarget(SearchNavigationTarget.Budgets)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // --- 5. REAL LOANS SECTION ---
                    if (results.loans.isNotEmpty()) {
                        item {
                            Text(
                                text = "LOANS (${results.loans.size} MATCHES)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextBlueGrey,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }

                        items(results.loans) { loan ->
                            SearchLoanRow(
                                loan = loan,
                                onClick = {
                                    onNavigateTarget(SearchNavigationTarget.Loans)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    // --- 6. REAL SUBSCRIPTIONS SECTION ---
                    if (results.subscriptions.isNotEmpty()) {
                        item {
                            Text(
                                text = "SUBSCRIPTIONS (${results.subscriptions.size} MATCHES)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextBlueGrey,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }

                        items(results.subscriptions) { sub ->
                            SearchSubscriptionRow(
                                sub = sub,
                                onClick = {
                                    onNavigateTarget(SearchNavigationTarget.Subscriptions)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureItemRow(
    feature: FeatureSearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FintechBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFeatureIcon(feature.target),
                        contentDescription = null,
                        tint = FintechBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = feature.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = feature.subtitle,
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Go",
                tint = TextBlueGrey,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SearchTransactionRow(
    txn: TransactionEntity,
    onClick: () -> Unit
) {
    val isCredit = txn.isInflow || txn.type.equals("INCOME", ignoreCase = true)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCredit) FintechGreenBg else FintechRedBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (isCredit) FintechGreen else FintechRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (txn.merchant.isNotBlank()) txn.merchant else txn.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextNavy
                    )
                    Text(
                        text = "${txn.category} • ${txn.date}",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                }
            }

            Text(
                text = "${if (isCredit) "+" else "-"}${formatInr(kotlin.math.abs(txn.amount))}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) FintechGreen else TextNavy
            )
        }
    }
}

@Composable
private fun SearchGoalRow(
    goal: GoalEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextNavy
                )
                Text(
                    text = "Saved: ${formatInr(goal.savedAmount)} of ${formatInr(goal.targetAmount)}",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }
            Text(
                text = "${((goal.savedAmount / goal.targetAmount.coerceAtLeast(1.0)) * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = FintechTeal
            )
        }
    }
}

@Composable
private fun SearchBudgetRow(
    budget: BudgetEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = budget.category,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextNavy
                )
                Text(
                    text = "Monthly Limit: ${formatInr(budget.monthlyLimit)}",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = TextBlueGrey,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SearchLoanRow(
    loan: LoanEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (loan.lender.isNotBlank()) "${loan.lender} • ${loan.title}" else loan.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextNavy
                )
                Text(
                    text = "Remaining: ${formatInr(loan.remaining)} • EMI: ${formatInr(loan.monthlyEmi)}",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }
            Text(
                text = "${loan.tenureMonthsLeft}m left",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FintechOrange
            )
        }
    }
}

@Composable
private fun SearchSubscriptionRow(
    sub: SubscriptionEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextNavy
                )
                Text(
                    text = "${sub.billingPeriod}${if (sub.nextExpectedPaymentDate.isNotBlank()) " • Next: ${sub.nextExpectedPaymentDate}" else ""}",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }
            Text(
                text = formatInr(if (sub.amount > 0.0) sub.amount else sub.monthlyCost),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
        }
    }
}

private fun getFeatureIcon(target: SearchNavigationTarget): ImageVector {
    return when (target) {
        is SearchNavigationTarget.Activity,
        is SearchNavigationTarget.ActivityFiltered -> Icons.Default.ReceiptLong
        is SearchNavigationTarget.Goals -> Icons.Default.TrackChanges
        is SearchNavigationTarget.Budgets -> Icons.Default.AccountBalanceWallet
        is SearchNavigationTarget.Investments -> Icons.Default.TrendingUp
        is SearchNavigationTarget.Loans -> Icons.Default.AccountBalance
        is SearchNavigationTarget.Subscriptions -> Icons.Default.Subscriptions
        is SearchNavigationTarget.Market -> Icons.Default.ShowChart
        is SearchNavigationTarget.Settings -> Icons.Default.Settings
        is SearchNavigationTarget.Profile -> Icons.Default.Person
    }
}
