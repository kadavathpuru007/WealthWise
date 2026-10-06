package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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

@Composable
fun BudgetLimitReachedBanner(
    shoppingSpent: Double = 0.0,
    shoppingLimit: Double = 0.0,
    onAdjustLimits: () -> Unit
) {
    val overrunPercent = if (shoppingLimit > 0.0) ((shoppingSpent / shoppingLimit) * 100).toInt() else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, CrimsonRed.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CrimsonBadgeBg.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CrimsonBadgeBg)
                            .border(1.dp, CrimsonRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = CrimsonRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CrimsonRed.copy(alpha = 0.2f))
                            .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⚠ BUDGET LIMIT REACHED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonLight
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1 Category Overruns",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate200
                    )
                }

                Button(
                    onClick = onAdjustLimits,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Adjust Limits",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "You have reached or exceeded your allocated monthly budget limits. Review and curb spending to protect your savings!",
                fontSize = 12.sp,
                color = Slate400,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Overrun Item Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate950)
                    .border(1.dp, CrimsonRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Shopping: ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate200
                )
                Text(
                    text = "₹${shoppingSpent.toInt()} / ₹${shoppingLimit.toInt()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonLight
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CrimsonRed)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$overrunPercent%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun InvestmentLimitReachedBanner(
    onViewPortfolio: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, IndigoPurple.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = IndigoBadgeBg.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(IndigoBadgeBg)
                            .border(1.dp, IndigoPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "Investment",
                            tint = IndigoLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(IndigoPurple.copy(alpha = 0.2f))
                            .border(1.dp, IndigoPurple.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "📈 INVESTMENT LIMIT REACHED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoLight
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% Monthly Cap Allocated",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate200
                    )
                }

                Button(
                    onClick = onViewPortfolio,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "View Portfolio",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "You have reached your maximum planned investment limit for this month across active assets.",
                fontSize = 12.sp,
                color = Slate400,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mutual funds pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate950)
                        .border(1.dp, IndigoPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Mutual Funds SIP Limit: ", fontSize = 11.sp, color = Slate400)
                    Text("₹25,000 / ₹25,000", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoLight)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(IndigoBadgeBg)
                            .border(1.dp, IndigoPurple, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("100% Reached", fontSize = 9.sp, color = IndigoLight, fontWeight = FontWeight.Bold)
                    }
                }

                // Sovereign Gold pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate950)
                        .border(1.dp, IndigoPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Sovereign Gold Maximum Cap: ", fontSize = 11.sp, color = Slate400)
                    Text("₹15,000 / ₹15,000", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoLight)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(IndigoBadgeBg)
                            .border(1.dp, IndigoPurple, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("100% Reached", fontSize = 9.sp, color = IndigoLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Floating Dismissible Notification Card (Slide away or tap 'X' in top right)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingDismissibleNotificationCard(
    title: String,
    message: String,
    type: String = "ALERT", // ALERT, TRANSACTION, INFO
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Slate800)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("Dismissed", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.2.dp,
                    color = when (type) {
                        "ALERT" -> CrimsonRed.copy(alpha = 0.8f)
                        "TRANSACTION" -> IndigoAccent.copy(alpha = 0.8f)
                        else -> RoyalBlue.copy(alpha = 0.8f)
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = when (type) {
                    "ALERT" -> CrimsonBadgeBg.copy(alpha = 0.85f)
                    "TRANSACTION" -> IndigoBadgeBg.copy(alpha = 0.85f)
                    else -> Slate900.copy(alpha = 0.9f)
                }
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when (type) {
                                    "ALERT" -> CrimsonBadgeBg
                                    "TRANSACTION" -> IndigoBadgeBg
                                    else -> RoyalBlueBadgeBg
                                }
                            )
                            .border(
                                1.dp,
                                when (type) {
                                    "ALERT" -> CrimsonRed
                                    "TRANSACTION" -> IndigoAccent
                                    else -> RoyalBlue
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (type) {
                                "ALERT" -> Icons.Default.Warning
                                "TRANSACTION" -> Icons.AutoMirrored.Filled.ReceiptLong
                                else -> Icons.Default.Notifications
                            },
                            contentDescription = "Alert",
                            tint = when (type) {
                                "ALERT" -> CrimsonLight
                                "TRANSACTION" -> IndigoLight
                                else -> RoyalBlueLight
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = message,
                            fontSize = 11.sp,
                            color = Slate200,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Slide to dismiss or tap ✕",
                            fontSize = 9.sp,
                            color = Slate400
                        )
                    }
                }

                // 'X' Icon in top right corner to dismiss
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Slate300,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
