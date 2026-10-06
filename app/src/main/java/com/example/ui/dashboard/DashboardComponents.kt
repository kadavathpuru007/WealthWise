package com.example.ui.dashboard

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BillEntity
import com.example.data.local.GoalEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

fun formatInr(amount: Double): String {
    if (amount.isNaN() || amount.isInfinite()) return "₹0"
    val sign = if (amount < 0) "-₹" else "₹"
    return "$sign${com.example.domain.financial.MoneyUtils.formatInr(kotlin.math.abs(amount))}"
}

@Composable
fun NetCashflowCard(
    netBalance: Double,
    totalInflow: Double,
    totalOutflow: Double,
    savingsRate: Int,
    bankSavings: Double,
    investments: Double,
    loansRemaining: Double = 425000.0,
    onViewPortfolio: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, IndigoAccent.copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            // Header Row: Title & Savings Rate Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(IndigoBadgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = IndigoLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CURRENT BALANCE",
                        fontSize = 12.sp,
                        color = IndigoLight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(IndigoBadgeBg)
                        .border(1.dp, IndigoDark, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Savings Rate: $savingsRate%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // The "Current Balance" is the largest, most striking text on the screen
            Text(
                text = formatInr(netBalance),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = Slate50,
                lineHeight = 48.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Below the balance, clean row/grid showing: Total Income, Total Expenses, and Loans Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Total Income
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Income",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "+${formatInr(totalInflow)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoLight
                        )
                    }
                }

                // 2. Total Expenses
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Expenses",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "-${formatInr(totalOutflow)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonLight
                        )
                    }
                }

                // 3. Loans Remaining
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Loans Remaining",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = formatInr(loansRemaining),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Slate800, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Footer row: Bank Savings & Investments / View Portfolio
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Savings: ${formatInr(bankSavings)}",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                    Text(
                        text = "Investments: ${formatInr(investments)}",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onViewPortfolio() }
                ) {
                    Text(
                        text = "Portfolio",
                        fontSize = 11.sp,
                        color = EmeraldLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = EmeraldLight,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// Visual Analytics: Bar Graph comparing Income vs. Expenses
@Composable
fun IncomeVsExpensesBarGraphCard(
    modifier: Modifier = Modifier
) {
    // 6-month historical financial tracking data
    val monthlyData = listOf(
        Triple("Apr", 85000f, 24000f),
        Triple("May", 88000f, 27500f),
        Triple("Jun", 92000f, 22000f),
        Triple("Jul", 95000f, 28000f),
        Triple("Aug", 100000f, 21500f),
        Triple("Sep", 103000f, 20811f)
    )
    val maxAmount = 110000f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header Row: Title & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(IndigoBadgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = IndigoLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "VISUAL ANALYTICS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoLight,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Income vs. Expenses",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                    }
                }

                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(IndigoAccent)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Income", fontSize = 10.sp, color = Slate300)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Expense", fontSize = 10.sp, color = Slate300)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bar Chart Canvas / Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyData.forEach { (month, income, expense) ->
                    val incomeHeightRatio = (income / maxAmount).coerceIn(0.1f, 1f)
                    val expenseHeightRatio = (expense / maxAmount).coerceIn(0.1f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Paired Bars
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.height(110.dp)
                        ) {
                            // Income Bar (Royal Blue / Indigo)
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(incomeHeightRatio)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(IndigoAccent)
                            )
                            // Expense Bar (Crimson Red)
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(expenseHeightRatio)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(CrimsonRed)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Month Label
                        Text(
                            text = month,
                            fontSize = 11.sp,
                            fontWeight = if (month == "Sep") FontWeight.Bold else FontWeight.Normal,
                            color = if (month == "Sep") IndigoLight else Slate400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Slate800, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Insight Footnote
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "September Surplus: +₹82,189",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLight
                )
                Text(
                    text = "Consistent 78-80% Savings",
                    fontSize = 11.sp,
                    color = Slate400
                )
            }
        }
    }
}


@Composable
fun HomeAlertsAndSubscriptionsRow(
    shoppingSpent: Double,
    shoppingLimit: Double,
    subscription: SubscriptionEntity?,
    onManageBudgets: () -> Unit,
    onReviewSubscriptions: () -> Unit,
    onCancelSubscription: (SubscriptionEntity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Budget Limit Alerts card (REQ-26)
        Card(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BUDGET LIMIT ALERTS (REQ-26)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                    }
                    Text(
                        text = "Manage",
                        fontSize = 11.sp,
                        color = EmeraldLight,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onManageBudgets() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CrimsonBadgeBg.copy(alpha = 0.5f))
                        .border(1.dp, CrimsonRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Shopping Budget Exceeded!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonLight
                            )
                            Text(
                                text = "Spent ₹${shoppingSpent.toInt()} / Limit ₹${shoppingLimit.toInt()}",
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }

                        val percent = if (shoppingLimit > 0) ((shoppingSpent / shoppingLimit) * 100).toInt() else 100
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CrimsonRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$percent%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Subscription Killer card (REQ-30)
        Card(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SyncProblem,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SUBSCRIPTION KILLER (REQ-30)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                    }
                    Text(
                        text = "Review",
                        fontSize = 11.sp,
                        color = EmeraldLight,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onReviewSubscriptions() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (subscription != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AmberBadgeBg.copy(alpha = 0.5f))
                            .border(1.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = subscription.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate100
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AmberWarning.copy(alpha = 0.3f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${subscription.daysUnused}d unused",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberLight
                                        )
                                    }
                                }
                                Text(
                                    text = "Draining ₹${subscription.monthlyCost.toInt()}/month (₹${(subscription.monthlyCost * 12).toInt()}/yr)",
                                    fontSize = 10.sp,
                                    color = Slate400,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Button(
                                onClick = { onCancelSubscription(subscription) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (subscription.status == "CANCELLED") "Cancelled" else "Cancel",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpcomingBillsSection(
    bills: List<BillEntity>,
    onPayBill: (BillEntity) -> Unit,
    onViewAllBills: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UPCOMING BILLS (REQ-50)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                }

                Text(
                    text = "All Bills",
                    fontSize = 11.sp,
                    color = EmeraldLight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onViewAllBills() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bills.take(3).forEach { bill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate950)
                            .border(1.dp, Slate800, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = bill.billerName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )
                            Text(
                                text = "Due: ${bill.dueDate} • ${bill.category}",
                                fontSize = 11.sp,
                                color = Slate400,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatInr(bill.amount),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate50
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            if (bill.isPaid) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(EmeraldBadgeBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Paid ✓", fontSize = 11.sp, color = EmeraldLight, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val formattedAmount = String.format(Locale.US, "%.2f", bill.amount)
                                        val upiUriString = "upi://pay?pa=bills@upi&pn=${Uri.encode(bill.billerName)}&am=$formattedAmount&cu=INR&tn=${Uri.encode("Payment for " + bill.billerName)}"
                                        val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUriString)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        val canLaunch = try {
                                            context.packageManager.queryIntentActivities(upiIntent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
                                        } catch (e: Exception) {
                                            false
                                        }
                                        if (canLaunch) {
                                            try {
                                                context.startActivity(upiIntent)
                                            } catch (e: Exception) {
                                                onPayBill(bill)
                                            }
                                        } else {
                                            onPayBill(bill)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payment,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pay Now", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoalsSection(
    goals: List<GoalEntity>,
    onFundGoal: (GoalEntity) -> Unit,
    onAddGoal: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = EmeraldLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FINANCIAL GOALS (REQ-32)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                }

                Text(
                    text = "+ Add Goal",
                    fontSize = 11.sp,
                    color = EmeraldLight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAddGoal() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                goals.take(2).forEach { goal ->
                    val progress = (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                    val percentage = (progress * 100).toInt()

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate950)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .clickable { onFundGoal(goal) },
                        colors = CardDefaults.cardColors(containerColor = Slate950)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = goal.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$percentage%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldLight
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = EmeraldGreen,
                                trackColor = Slate800
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Saved: ${formatInr(goal.savedAmount)}",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = "Target: ${formatInr(goal.targetAmount)}",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTransactionsSection(
    transactions: List<TransactionEntity>,
    onViewFullLedger: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Slate200,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECENT TRANSACTIONS (REQ-20)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                }

                Text(
                    text = "View Full Ledger",
                    fontSize = 11.sp,
                    color = EmeraldLight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onViewFullLedger() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                transactions.take(5).forEach { txn ->
                    TransactionItemRow(txn = txn)
                }
            }
        }
    }
}

@Composable
fun TransactionItemRow(
    txn: TransactionEntity,
    onDelete: (() -> Unit)? = null
) {
    val isCredit = txn.isInflow || txn.type.equals("credit", ignoreCase = true) || txn.type.equals("INCOME", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardWhite)
            .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCredit) FintechGreenBg else FintechRedBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.Add else Icons.Default.Remove,
                    contentDescription = null,
                    tint = if (isCredit) FintechGreen else FintechRed,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = txn.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    if (txn.isParsed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(FintechBlueBg)
                                .border(1.dp, FintechBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Parsed",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechBlue
                            )
                        }
                    }
                }

                Text(
                    text = "${txn.date} • ${txn.category} • ${txn.paymentMethod}",
                    fontSize = 11.sp,
                    color = TextBlueGrey,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isCredit) "+${formatInr(kotlin.math.abs(txn.amount))}" else "-${formatInr(kotlin.math.abs(txn.amount))}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) FintechGreen else FintechRed
            )
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete transaction",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
