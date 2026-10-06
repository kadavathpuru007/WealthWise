/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development Team:
 * - Purushotham
 * - Surya
 * - Srinivas
 * - Karthik
 */

package com.example.ui.aitools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.WealthWiseUiState

@Composable
fun AIToolsScreen(
    state: WealthWiseUiState,
    onOpenSubscriptionKiller: () -> Unit,
    onOpenBudgetLimits: () -> Unit,
    onOpenTaxAnalyzer: () -> Unit = {},
    onOpenAnomalyGuardian: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FintechBlueBg)
                    .border(1.2.dp, FintechBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = FintechBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Smart Financial Assistant",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextNavy
                )
                Text(
                    text = "Automated intelligence tools designed to save you time and stop money leaks.",
                    fontSize = 12.sp,
                    color = TextBlueGrey,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tool 1: Subscription Killer
        val unusedCount = state.subscriptions.count { it.isUnusedFlagged && it.status == "ACTIVE" }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, BorderLight, RoundedCornerShape(18.dp))
                .clickable { onOpenSubscriptionKiller() },
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FintechOrangeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SyncProblem,
                            contentDescription = null,
                            tint = FintechOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(FintechOrangeBg)
                            .border(1.dp, FintechOrange.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🔥 $unusedCount Unused Subs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FintechOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Subscription Killer",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Flags recurring subscriptions (gym, streaming, apps) you haven't opened in over 30 days. Cancel them in one tap to stop wasting money.",
                    fontSize = 13.sp,
                    color = TextBlueGrey,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderLight, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Saves average users ₹12,000 to ₹30,000 every year",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = FintechOrange
                )
            }
        }

        // Card 3: Budget Spending Limits
        val overBudgetCount = state.budgets.count { budget ->
            val spent = state.transactions
                .filter { it.type == "EXPENSE" && it.category == budget.category }
                .sumOf { it.amount }
            spent > budget.monthlyLimit
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, BorderLight, RoundedCornerShape(18.dp))
                .clickable { onOpenBudgetLimits() },
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FintechRedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = FintechRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(FintechRedBg)
                            .border(1.dp, FintechRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚠ $overBudgetCount Over Budget",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FintechRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Budget Spending Limits",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Set simple monthly limits for Dining, Shopping, Travel, and Bills. Get instant warning notifications if you spend too fast.",
                    fontSize = 13.sp,
                    color = TextBlueGrey,
                    lineHeight = 18.sp
                )
            }
        }

        // Card 5: Smart Tax & 80C Deduction Analyzer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, BorderLight, RoundedCornerShape(18.dp))
                .clickable { onOpenTaxAnalyzer() },
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FintechGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = FintechGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FintechGreenBg)
                                .border(1.dp, FintechGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Analyze 80C",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechGreen
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = FintechGreen, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Tax & 80C Deduction Analyzer",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Scans mutual fund ELSS investments, health insurance premiums, and PPF deposits to maximize your tax deductions under Section 80C and Old vs New regime.",
                    fontSize = 13.sp,
                    color = TextBlueGrey,
                    lineHeight = 18.sp
                )
            }
        }

        // Card 6: AI Spending Anomaly Detector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, BorderLight, RoundedCornerShape(18.dp))
                .clickable { onOpenAnomalyGuardian() },
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FintechBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = FintechBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FintechGreenBg)
                                .border(1.dp, FintechGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Run Scanner",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechGreen
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextBlueGrey, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Real-time Spend Anomaly Guardian",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Monitors sudden double charges, atypical midnight merchant spikes, or unauthorized recurring trial payments before they renew.",
                    fontSize = 13.sp,
                    color = TextBlueGrey,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}
