/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development Team:
 * - Purushotham
 */

package com.example.ui.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.local.GoalEntity
import com.example.data.local.TransactionEntity
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*

@Composable
fun GoalsScreen(
    goals: List<GoalEntity>,
    transactions: List<TransactionEntity> = emptyList(),
    safeToSpend: Double = 0.0,
    onFundGoal: (GoalEntity, Double) -> Unit,
    onOpenFundDialog: (GoalEntity) -> Unit = {},
    onAddGoal: () -> Unit,
    onBack: () -> Unit = {}
) {
    val totalSaved = goals.sumOf { it.savedAmount }
    val totalTarget = goals.sumOf { it.targetAmount }
    val overallProgress = if (totalTarget > 0) ((totalSaved / totalTarget) * 100).toInt() else 0

    Scaffold(
        containerColor = Color.White,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGoal,
                containerColor = FintechNavyCard,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 80.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Goal", tint = Color.White)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Row (Title and Subtitle only)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextNavy
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Financial Goals",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = "Track and fund your targeted savings milestones",
                            fontSize = 11.sp,
                            color = TextBlueGrey
                        )
                    }
                }
            }

            // 2. Overall Progress Hero Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderLight, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = FintechNavyCard),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TOTAL GOAL PORTFOLIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechBlueLight,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$overallProgress% Achieved",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechGreenLight
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = formatInr(totalSaved),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        Text(
                            text = "Targeted: ${formatInr(totalTarget)}",
                            fontSize = 12.sp,
                            color = Slate300,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { (overallProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = FintechTealLight,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            // 3. Goal Cards List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active Savings Goals",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "${goals.size} Goals",
                        fontSize = 12.sp,
                        color = TextBlueGrey
                    )
                }
            }

            // Goal Cards
            if (goals.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(FintechTealBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrackChanges,
                                    contentDescription = null,
                                    tint = FintechTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No active savings goals",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextNavy
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a goal for your next vehicle, vacation, or emergency fund.",
                                fontSize = 12.sp,
                                color = TextBlueGrey,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onAddGoal,
                                colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Add Your First Goal", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(goals, key = { it.id }) { goal ->
                    val progressPercent = if (goal.targetAmount > 0) {
                        ((goal.savedAmount / goal.targetAmount) * 100).toInt().coerceIn(0, 100)
                    } else 0
                    val isCompleted = progressPercent >= 100
                    val remainingGoal = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isCompleted) FintechGreen.copy(alpha = 0.5f) else BorderLight, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (isCompleted) FintechGreenBg.copy(alpha = 0.3f) else CardWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isCompleted) FintechGreenBg else FintechTealBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isCompleted -> Icons.Default.CheckCircle
                                                goal.title.contains("Car", ignoreCase = true) -> Icons.Default.DirectionsCar
                                                goal.title.contains("Emergency", ignoreCase = true) -> Icons.Default.Shield
                                                goal.title.contains("Gold", ignoreCase = true) -> Icons.Default.Diamond
                                                else -> Icons.Default.Flag
                                            },
                                            contentDescription = null,
                                            tint = if (isCompleted) FintechGreen else FintechTeal,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = goal.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextNavy
                                        )
                                        Text(
                                            text = "Category: ${goal.category}",
                                            fontSize = 11.sp,
                                            color = TextBlueGrey
                                        )
                                    }
                                }

                                if (isCompleted) {
                                    Surface(
                                        color = FintechGreenBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, FintechGreen.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "Completed 🎉",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FintechGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { onOpenFundDialog(goal) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = FintechBlueBg),
                                        border = BorderStroke(1.dp, FintechBlue.copy(alpha = 0.3f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = FintechBlue, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fund Goal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FintechBlue)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Saved: ${formatInr(goal.savedAmount)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextBlueGrey
                                )
                                Text(
                                    text = "Goal: ${formatInr(goal.targetAmount)} ($progressPercent%)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) FintechGreen else FintechBlue
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isCompleted) "Target Achieved!" else "Remaining: ${formatInr(remainingGoal)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCompleted) FintechGreen else TextBlueGrey
                                )
                                if (goal.monthlyTarget > 0.0 && !isCompleted) {
                                    Text(
                                        text = "Req: ${formatInr(goal.monthlyTarget)}/mo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FintechTeal
                                    )
                                }
                            }

                            val estMonths = if (goal.monthlyTarget > 0.0 && remainingGoal > 0.0) {
                                (kotlin.math.ceil(remainingGoal / goal.monthlyTarget)).toInt()
                            } else null

                            if (estMonths != null && estMonths > 0 && !isCompleted) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Est. completion: ~$estMonths months at target contribution",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isCompleted) FintechGreen else FintechTeal,
                                trackColor = BorderLight
                            )

                            if (!isCompleted) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(500.0, 1000.0, 2500.0).forEach { amount ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(FintechBlueBg)
                                                .clickable { onFundGoal(goal, amount) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "+₹${amount.toInt()}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FintechBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Automated Goal Boosters Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, FintechGreen.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = FintechGreenBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Celebration,
                                        contentDescription = null,
                                        tint = FintechGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Automated Goal Boosters",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextNavy
                                    )
                                    Text(
                                        text = "Round-up spare change from UPI spends",
                                        fontSize = 11.sp,
                                        color = TextBlueGrey
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CardWhite)
                                    .border(1.dp, FintechGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FintechGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Every transaction rounds up to the nearest ₹10. Saved spare change is automatically allocated toward your highest-priority savings goal.",
                            fontSize = 12.sp,
                            color = TextBlueGrey,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}