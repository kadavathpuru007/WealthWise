/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development Team:
 * - Purushotham
 
 * Module: Consolidated WealthWise Utility & Action Dialogs Suite
 */

package com.example.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.BillEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.GoalEntity
import com.example.data.local.LoanEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.TransactionEntity
import com.example.data.parser.ParserConfidence
import com.example.data.parser.TransactionDirection
import com.example.data.parser.TransactionParser
import com.example.domain.financial.FinancialStatus
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*
import java.util.Locale

// 1. Manual Add Transaction Dialog
@Composable
fun AddTransactionDialog(
    initialType: String = "EXPENSE",
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, type: String, category: String, paymentMethod: String) -> Unit,
    onOpenSimulate: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var category by remember { mutableStateOf(if (type == "INCOME") "Salary" else "Food") }
    var paymentMethod by remember { mutableStateOf("UPI") }

    val expenseCategories = listOf("Food", "Shopping", "Transport", "Personal Transfer", "Subscription", "Bills", "Entertainment", "Other")
    val incomeCategories = listOf("Salary", "Personal Transfer", "Freelance", "Investment", "Gift", "Other")
    val paymentMethods = listOf("UPI", "Bank Transfer", "Credit Card", "Debit Card", "Cash")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Transaction",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                    if (onOpenSimulate != null) {
                        TextButton(
                            onClick = onOpenSimulate,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = IndigoLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simulate", fontSize = 12.sp, color = IndigoLight, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Type Toggle (Income / Expense)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate950)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == "EXPENSE") CrimsonRed else Color.Transparent)
                            .clickable {
                                type = "EXPENSE"
                                category = "Food"
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Expense (-)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (type == "EXPENSE") Color.White else Slate400
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == "INCOME") EmeraldGreen else Color.Transparent)
                            .clickable {
                                type = "INCOME"
                                category = "Salary"
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Income (+)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (type == "INCOME") Color.Black else Slate400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Merchant (e.g. Swiggy, Salary)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    prefix = { Text("₹ ", color = EmeraldLight, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Category", fontSize = 12.sp, color = Slate400, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val currentCategories = if (type == "INCOME") incomeCategories else expenseCategories
                    currentCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldBadgeBg,
                                selectedLabelColor = EmeraldLight,
                                containerColor = Slate850,
                                labelColor = Slate300
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Payment Method", fontSize = 12.sp, color = Slate400, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    paymentMethods.forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoBadgeBg,
                                selectedLabelColor = IndigoLight,
                                containerColor = Slate850,
                                labelColor = Slate300
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
                            if (title.isNotBlank() && amount > 0) {
                                onConfirm(title, amount, type, category, paymentMethod)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Transaction", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 4. Budget Adjust Limits Dialog
@Composable
fun BudgetAdjustDialog(
    budgets: List<BudgetEntity>,
    availableAmount: Double = 0.0,
    onSaveLimit: (category: String, limit: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(budgets.firstOrNull()?.category ?: "Shopping") }
    val currentLimit = remember(selectedCategory, budgets) {
        budgets.firstOrNull { it.category == selectedCategory }?.monthlyLimit?.toInt()?.toString() ?: "0"
    }
    var limitInput by remember(selectedCategory) { mutableStateOf(currentLimit) }
    var showConfirmation by remember { mutableStateOf(false) }
    var pendingCategory by remember { mutableStateOf("") }
    var pendingNewLimit by remember { mutableStateOf(0.0) }
    var pendingOldLimit by remember { mutableStateOf(0.0) }
    var pendingTotalBudget by remember { mutableStateOf(0.0) }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = {
                Text(
                    text = "Are you sure?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You're changing your $pendingCategory budget from ₹${pendingOldLimit.toInt()} to ₹${pendingNewLimit.toInt()}.",
                        fontSize = 13.sp,
                        color = Slate200
                    )
                    Text(
                        text = "Your total planned budget will be ₹${pendingTotalBudget.toInt()}.",
                        fontSize = 13.sp,
                        color = Slate300
                    )
                    Text(
                        text = "Your available amount is ₹${availableAmount.toInt()}.",
                        fontSize = 13.sp,
                        color = Slate300
                    )
                    if (pendingTotalBudget <= availableAmount) {
                        Text(
                            text = "₹${(availableAmount - pendingTotalBudget).toInt()} will remain after planned budgets.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldLight
                        )
                    } else {
                        Text(
                            text = "⚠️ Warning: Your planned budgets exceed available funds by ₹${(pendingTotalBudget - availableAmount).toInt()}.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FintechRedLight
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmation = false
                        onSaveLimit(pendingCategory, pendingNewLimit)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Cancel", color = Slate400)
                }
            },
            containerColor = Slate900,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .imePadding()
                .navigationBarsPadding()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Adjust Spending Caps",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "Configure monthly budget thresholds per category.",
                    fontSize = 12.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Select Category:", fontSize = 12.sp, color = Slate400)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    budgets.forEach { b ->
                        FilterChip(
                            selected = selectedCategory == b.category,
                            onClick = {
                                selectedCategory = b.category
                                limitInput = b.monthlyLimit.toInt().toString()
                            },
                            label = { Text(b.category, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldBadgeBg,
                                selectedLabelColor = EmeraldLight,
                                containerColor = Slate850,
                                labelColor = Slate300
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Monthly Limit for $selectedCategory") },
                    prefix = { Text("₹ ", color = EmeraldLight, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val limit = limitInput.replace(",", "").toDoubleOrNull() ?: 5000.0
                            val old = budgets.firstOrNull { it.category == selectedCategory }?.monthlyLimit ?: 0.0
                            val newTotal = budgets.filter { it.category != selectedCategory }.sumOf { it.monthlyLimit } + limit
                            pendingCategory = selectedCategory
                            pendingNewLimit = limit
                            pendingOldLimit = old
                            pendingTotalBudget = newTotal
                            showConfirmation = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Limit", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 4B. Add Budget Category Dialog
@Composable
fun AddBudgetCategoryDialog(
    availableAmount: Double = 0.0,
    existingBudgets: List<BudgetEntity> = emptyList(),
    onSave: (category: String, limit: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var limitInput by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("Monthly") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .imePadding()
                .navigationBarsPadding()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add Budget Category",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "Create a new planned spending category. This will not deduct money or create an expense.",
                    fontSize = 12.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { 
                        categoryName = it
                        errorMessage = null
                    },
                    label = { Text("Category Name (e.g. Groceries, Entertainment)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { 
                        limitInput = it
                        errorMessage = null
                    },
                    label = { Text("Budget Amount") },
                    prefix = { Text("₹ ", color = EmeraldLight, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = period,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Budget Period") },
                    trailingIcon = {
                        Text("Fixed", fontSize = 11.sp, color = EmeraldLight, modifier = Modifier.padding(end = 12.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = FintechRedLight,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmedName = categoryName.trim()
                            val limit = limitInput.replace(",", "").toDoubleOrNull()
                            if (trimmedName.isBlank()) {
                                errorMessage = "Please enter a category name"
                                return@Button
                            }
                            if (limit == null || limit <= 0) {
                                errorMessage = "Please enter a valid budget amount"
                                return@Button
                            }
                            val isDuplicate = existingBudgets.any {
                                it.category.trim().equals(trimmedName, ignoreCase = true)
                            }
                            if (isDuplicate) {
                                errorMessage = "Category \"$trimmedName\" already exists"
                                return@Button
                            }
                            onSave(trimmedName, limit)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Budget", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 5. Notifications Tray Dialog
@Composable
fun NotificationsDialog(
    notifications: List<NotificationEntity>,
    onMarkAllRead: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = EmeraldLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Notifications",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(1.dp))

                    if (notifications.isNotEmpty()) {
                        Row {
                            TextButton(
                                onClick = onMarkAllRead,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Mark All Read", fontSize = 11.sp, color = IndigoLight)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = onClearAll,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clear All", fontSize = 11.sp, color = Slate400)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = Slate600, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No notifications right now", color = Slate400, fontSize = 13.sp)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        notifications.forEach { notif ->
                            val badgeColor = when (notif.type) {
                                "ALERT" -> CrimsonRed
                                "PARSED" -> EmeraldGreen
                                "BILL" -> AmberWarning
                                else -> IndigoPurple
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Slate950)
                                    .border(1.dp, if (!notif.isRead) IndigoAccent.copy(alpha = 0.5f) else Slate800, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                            .padding(top = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = notif.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate100
                                        )
                                        Text(
                                            text = notif.message,
                                            fontSize = 11.sp,
                                            color = Slate400,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close Tray", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 6. Pay Bill / Google Pay UPI Payment Dialog (REQ-50)
@Composable
fun PayBillDialog(
    bill: BillEntity,
    onConfirmPay: (BillEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val formattedAmount = String.format(Locale.US, "%.2f", bill.amount)
    val upiUriString = remember(bill) {
        "upi://pay?pa=bills@upi&pn=${Uri.encode(bill.billerName)}&am=$formattedAmount&cu=INR&tn=${Uri.encode("Payment for " + bill.billerName)}"
    }
    val upiIntent = remember(upiUriString) {
        Intent(Intent.ACTION_VIEW, Uri.parse(upiUriString)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Google Pay / UPI Theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = "Google Pay",
                                tint = EmeraldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Opening Google Pay...",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate50
                            )
                            Text(
                                text = "UPI Intent Deep-Linking • bills@upi",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Biller Details Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bill.billerName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IndigoBadgeBg)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "VPA: bills@upi",
                                    fontSize = 10.sp,
                                    color = IndigoLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Category: ${bill.category} • Due: ${bill.dueDate}",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text("Amount Due", fontSize = 11.sp, color = Slate400)
                                Text(
                                    text = formatInr(bill.amount),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CrimsonLight
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AmberBadgeBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Pending UPI Debit",
                                    fontSize = 11.sp,
                                    color = AmberWarning,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // UPI Payment String Box
                Text(
                    text = "UPI Payment URI:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate300
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = upiUriString,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyanAccent,
                            modifier = Modifier.weight(1f),
                            maxLines = 3
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(upiUriString))
                                Toast.makeText(context, "UPI URI copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy UPI URI",
                                tint = Slate300,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payment Confirmation Callout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldBadgeBg.copy(alpha = 0.5f))
                        .border(1.dp, EmeraldDark.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = EmeraldLight,
                            modifier = Modifier
                                .size(15.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap below to confirm payment, update current balance, and record the debit to your account.",
                            fontSize = 11.sp,
                            color = Slate300,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Confirm Payment Button
                Button(
                    onClick = { onConfirmPay(bill) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm Payment",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Native Hardware UPI Launch Option
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(upiIntent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                "No native UPI app (e.g. Google Pay) found. Use 'Confirm Payment'.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoLight),
                    border = BorderStroke(1.dp, IndigoDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = IndigoLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch Native Google Pay", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Slate400, fontSize = 12.sp)
                }
            }
        }
    }
}

// 7. Simple Guide & FAQ Dialog
@Composable
fun SimpleGuideDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WealthWise Simple Guide & FAQ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GuideItem(
                        q = "1. How does SMS & UPI parsing work?",
                        a = "WealthWise scans incoming notification text locally on your device. It detects amounts in INR/₹, matches merchants like Zomato, Swiggy, Uber, or Amazon, and classifies them into Food, Shopping, or Bills automatically."
                    )
                    GuideItem(
                        q = "2. What is the Subscription Killer?",
                        a = "It flags recurring services like gym memberships or streaming platforms you haven't used in 30+ days. You can cancel them in one tap to prevent money leaks."
                    )
                    GuideItem(
                        q = "3. How are Budget Limit Alerts triggered?",
                        a = "When your monthly spending in a category (e.g. Shopping) reaches or exceeds the threshold you specified, real-time warning badges and banners are displayed."
                    )
                    GuideItem(
                        q = "4. Is my financial data secure?",
                        a = "Yes! All transactions, accounts, and budgets are persisted inside a secure local SQLite/Room database directly on your device."
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close Guide", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GuideItem(q: String, a: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Slate950)
            .border(1.dp, Slate800, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(q, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            Spacer(modifier = Modifier.height(4.dp))
            Text(a, fontSize = 11.sp, color = Slate400, lineHeight = 16.sp)
        }
    }
}

// 8. Edit Profile Dialog
@Composable
fun EditProfileDialog(
    initialName: String,
    initialEmail: String,
    onSave: (name: String, email: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Edit Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && email.isNotBlank()) {
                                onSave(name, email)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Profile", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 9. Add Goal Dialog
@Composable
fun AddGoalDialog(
    availableFunds: Double = 0.0,
    totalPlannedBudget: Double = 0.0,
    onDismiss: () -> Unit,
    onSave: (com.example.data.local.GoalEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var timelineMonthsText by remember { mutableStateOf("10") }
    var initialSavedText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Savings") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val targetAmount = targetText.replace(",", "").toDoubleOrNull() ?: 0.0
    val savedAmount = initialSavedText.replace(",", "").toDoubleOrNull() ?: 0.0
    val timelineMonths = timelineMonthsText.toIntOrNull() ?: 1
    val remainingTarget = maxOf(0.0, targetAmount - savedAmount)
    val requiredMonthly = if (timelineMonths > 0) remainingTarget / timelineMonths else remainingTarget
    val availableMonthly = maxOf(0.0, availableFunds - totalPlannedBudget)
    val isAffordable = availableMonthly >= requiredMonthly

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "New Financial Goal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "Planning only. This will NOT deduct balance or create an expense.",
                    fontSize = 12.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { 
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Goal Title (e.g. Royal Enfield, Vacation)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { 
                        targetText = it
                        errorMessage = null
                    },
                    label = { Text("Target Amount") },
                    prefix = { Text("₹ ", color = EmeraldLight, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = timelineMonthsText,
                        onValueChange = { timelineMonthsText = it },
                        label = { Text("Timeline (Months)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate100,
                            focusedContainerColor = Slate850,
                            unfocusedContainerColor = Slate850
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = initialSavedText,
                        onValueChange = { initialSavedText = it },
                        label = { Text("Already Saved") },
                        prefix = { Text("₹ ", color = EmeraldLight, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate100,
                            focusedContainerColor = Slate850,
                            unfocusedContainerColor = Slate850
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Dynamic Affordability Assessment Card
                if (targetAmount > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAffordable) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFF7F1D1D).copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isAffordable) EmeraldGreen.copy(alpha = 0.5f) else FintechRedLight.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAffordable) "✓ Contribution Affordable" else "⚠️ Affordability Warning",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAffordable) EmeraldLight else FintechRedLight
                                )
                                Text(
                                    text = "₹${requiredMonthly.toInt()}/mo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100
                                )
                            }
                            Text(
                                text = if (isAffordable) {
                                    "Goal contribution appears affordable based on your current budget. Available after budget: ₹${availableMonthly.toInt()}/month. Remaining buffer: ₹${(availableMonthly - requiredMonthly).toInt()}/month."
                                } else {
                                    "Your current budget leaves ₹${availableMonthly.toInt()}/month. This goal requires ₹${requiredMonthly.toInt()}/month. Shortfall: ₹${(requiredMonthly - availableMonthly).toInt()}/month.\nOptions: Reduce planned budget, increase timeline, or proceed with caution."
                                },
                                fontSize = 11.sp,
                                color = if (isAffordable) Slate200 else Color(0xFFFCA5A5),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg, color = FintechRedLight, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = title.trim()
                            if (trimmed.isBlank()) {
                                errorMessage = "Please enter a goal title"
                                return@Button
                            }
                            if (targetAmount <= 0) {
                                errorMessage = "Please enter a valid target amount"
                                return@Button
                            }
                            onSave(
                                com.example.data.local.GoalEntity(
                                    title = trimmed,
                                    targetAmount = targetAmount,
                                    savedAmount = savedAmount,
                                    monthlyTarget = requiredMonthly,
                                    category = category
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Goal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 9B. Edit Opening Balance Dialog
@Composable
fun EditBalanceDialog(
    currentOpening: Double,
    currentNet: Double,
    totalInflow: Double,
    totalOutflow: Double,
    onSaveBalance: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var newBalanceInput by remember { mutableStateOf(currentOpening.toInt().toString()) }
    var showConfirm by remember { mutableStateOf(false) }
    var pendingNewBalance by remember { mutableStateOf(0.0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                Text(
                    text = "Update balance?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Current balance: ${formatInr(currentNet)}",
                        fontSize = 13.sp,
                        color = Slate200
                    )
                    Text(
                        text = "New opening balance: ${formatInr(pendingNewBalance)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldLight
                    )
                    Text(
                        text = "Calculated new balance: ${formatInr(pendingNewBalance + totalInflow - totalOutflow)}",
                        fontSize = 13.sp,
                        color = Slate300
                    )
                    Text(
                        text = "Your real transactions remain unchanged. This adjusts only your baseline opening balance without creating fake income or expense entries.",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onSaveBalance(pendingNewBalance)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = Slate400)
                }
            },
            containerColor = Slate900,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Edit Opening Balance",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "Set your baseline account opening balance. This does NOT create fake income or expenses.",
                    fontSize = 12.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate850),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Opening Balance:", fontSize = 12.sp, color = Slate400)
                            Text(formatInr(currentOpening), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate100)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Net Balance:", fontSize = 12.sp, color = Slate400)
                            Text(formatInr(currentNet), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newBalanceInput,
                    onValueChange = { 
                        newBalanceInput = it
                        errorMessage = null
                    },
                    label = { Text("New Opening Balance") },
                    prefix = { Text("₹ ", color = EmeraldLight, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(msg, color = FintechRedLight, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = newBalanceInput.replace(",", "").toDoubleOrNull()
                            if (parsed == null || parsed < 0) {
                                errorMessage = "Please enter a valid non-negative amount"
                                return@Button
                            }
                            pendingNewBalance = parsed
                            showConfirm = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 12. Add Loan / Debt Liability Dialog
@Composable
fun AddLoanDialog(
    onDismiss: () -> Unit,
    onSaveLoan: (title: String, lender: String, principal: Double, interestRate: Double, tenureMonths: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var lender by remember { mutableStateOf("HDFC Bank") }
    var principalText by remember { mutableStateOf("250000") }
    var interestRateText by remember { mutableStateOf("9.5") }
    var tenureMonthsText by remember { mutableStateOf("24") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val lenderSuggestions = listOf("HDFC Bank", "SBI", "ICICI Bank", "Axis Bank", "Bajaj Finserv", "Personal Loan")

    val p = principalText.toDoubleOrNull() ?: 0.0
    val r = (interestRateText.toDoubleOrNull() ?: 0.0) / 100.0 / 12.0
    val n = tenureMonthsText.toIntOrNull() ?: 12
    val calculatedEmi = if (p > 0 && r > 0 && n > 0) {
        val factor = Math.pow(1.0 + r, n.toDouble())
        Math.round((p * r * factor) / (factor - 1.0)).toDouble()
    } else if (p > 0 && n > 0) {
        Math.round(p / n).toDouble()
    } else 0.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Add Loan / Debt",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }
                Text(
                    text = "Track interest rate, remaining balance & automated EMIs.",
                    fontSize = 11.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Loan Title (e.g. Car Loan, Education Loan)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberWarning,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Lender / Financial Institution:", fontSize = 11.sp, color = Slate400)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lenderSuggestions.forEach { l ->
                        FilterChip(
                            selected = lender == l,
                            onClick = { lender = l },
                            label = { Text(l, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberWarning.copy(alpha = 0.25f),
                                selectedLabelColor = AmberLight,
                                containerColor = Slate850,
                                labelColor = Slate300
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = principalText,
                        onValueChange = {
                            principalText = it
                            errorMessage = null
                        },
                        label = { Text("Principal (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberWarning,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate100,
                            focusedContainerColor = Slate850,
                            unfocusedContainerColor = Slate850
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = interestRateText,
                        onValueChange = { interestRateText = it },
                        label = { Text("ROI (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberWarning,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate100,
                            focusedContainerColor = Slate850,
                            unfocusedContainerColor = Slate850
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tenureMonthsText,
                    onValueChange = { tenureMonthsText = it },
                    label = { Text("Tenure (Months)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberWarning,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // EMI Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AmberWarning.copy(alpha = 0.15f))
                        .border(1.dp, AmberWarning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Calculated Monthly EMI", fontSize = 11.sp, color = Slate300)
                            Text(
                                text = "₹${calculatedEmi.toInt()}/mo",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberLight
                            )
                        }
                        Text(
                            text = "${tenureMonthsText.ifBlank { "0" }} installments",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(err, color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val principal = principalText.toDoubleOrNull() ?: 0.0
                            val interestRate = interestRateText.toDoubleOrNull() ?: 0.0
                            val tenureMonths = tenureMonthsText.toIntOrNull() ?: 12
                            if (title.isBlank()) {
                                errorMessage = "Please enter a loan title"
                            } else if (principal <= 0) {
                                errorMessage = "Principal must be greater than ₹0"
                            } else {
                                onSaveLoan(title, lender, principal, interestRate, tenureMonths)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Loan", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 13. Fund Goal Dialog
@Composable
fun FundGoalDialog(
    goal: GoalEntity,
    availableBalance: Double = 0.0,
    onDismiss: () -> Unit,
    onFund: (Double) -> Unit
) {
    val remainingAmount = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)
    val monthlyTarget = if (goal.timelineMonths > 0) {
        remainingAmount / goal.timelineMonths
    } else if (goal.monthlyTarget > 0.0) {
        goal.monthlyTarget
    } else {
        0.0
    }
    val roundedTarget = (kotlin.math.ceil(monthlyTarget / 100.0) * 100.0).toInt()

    var amountText by remember { mutableStateOf(if (roundedTarget > 0) roundedTarget.toString() else "") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fund Goal: ${goal.title}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                    IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Saved: ${formatInr(goal.savedAmount)}", fontSize = 12.sp, color = EmeraldLight)
                            Text("Target: ${formatInr(goal.targetAmount)}", fontSize = 12.sp, color = Slate400)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = EmeraldGreen,
                            trackColor = Slate800
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Remaining: ${formatInr(remainingAmount)}",
                                fontSize = 11.sp,
                                color = Slate300
                            )
                            if (availableBalance > 0.0) {
                                Text(
                                    text = "Available: ${formatInr(availableBalance)}",
                                    fontSize = 11.sp,
                                    color = EmeraldLight
                                )
                            }
                        }
                    }
                }

                if (roundedTarget > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldGreen.copy(alpha = 0.12f))
                            .border(1.dp, EmeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Recommended this month",
                                    fontSize = 11.sp,
                                    color = EmeraldLight.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "₹$roundedTarget / month",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldLight
                                )
                            }
                            if (goal.timelineMonths > 0) {
                                Text(
                                    text = "${goal.timelineMonths} mo timeline",
                                    fontSize = 11.sp,
                                    color = Slate400
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Quick Add Contribution:", fontSize = 11.sp, color = Slate400)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (roundedTarget > 0) {
                        Button(
                            onClick = { amountText = roundedTarget.toString() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (amountText == roundedTarget.toString()) IndigoAccent else Slate850
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text("₹$roundedTarget (Target/mo)", fontSize = 10.sp, color = Slate100, maxLines = 1)
                        }
                    }
                    listOf(500, 1000, 2500).forEach { amt ->
                        Button(
                            onClick = { amountText = amt.toString() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (amountText == amt.toString()) IndigoAccent else Slate850
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text("+₹$amt", fontSize = 11.sp, color = Slate100)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text("Contribution Amount (₹)") },
                    prefix = { Text("₹ ", color = EmeraldLight, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate850,
                        unfocusedContainerColor = Slate850
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                errorText?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(err, color = CrimsonRed, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text("Cancel", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = amountText.trim()
                            val amt = trimmed.toDoubleOrNull()
                            if (amt == null || amt <= 0.0 || amt.isNaN() || amt.isInfinite()) {
                                errorText = "Please enter a valid positive contribution amount."
                                return@Button
                            }

                            if (availableBalance > 0.0 && amt > availableBalance) {
                                val shortfall = amt - availableBalance
                                errorText = "Insufficient Available Balance\nAvailable: ${formatInr(availableBalance)}\nRequested: ${formatInr(amt)}\nShortfall: ${formatInr(shortfall)}"
                                return@Button
                            }

                            isSubmitting = true
                            onFund(amt)
                            onDismiss()
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isSubmitting) "Processing..." else "Confirm Funding", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 14. Section 80C Tax Optimizer Dialog
@Composable
fun Tax80CAnalyzerDialog(
    transactions: List<TransactionEntity>,
    totalInflow: Double,
    onDismiss: () -> Unit
) {
    val investmentTotal = transactions
        .filter { it.category.equals("Investment", ignoreCase = true) || it.title.contains("Mutual", ignoreCase = true) || it.title.contains("ELSS", ignoreCase = true) }
        .sumOf { it.amount }

    val insuranceTotal = transactions
        .filter { it.title.contains("Insurance", ignoreCase = true) || it.title.contains("LIC", ignoreCase = true) }
        .sumOf { it.amount }

    val total80CInvested = (investmentTotal + insuranceTotal).coerceAtMost(150000.0)
    val max80CLimit = 150000.0
    val remaining80CGap = (max80CLimit - total80CInvested).coerceAtLeast(0.0)
    val standardDeduction = 75000.0

    val estimatedAnnualIncome = (totalInflow * 12).coerceAtLeast(600000.0)
    val newRegimeTaxable = (estimatedAnnualIncome - standardDeduction).coerceAtLeast(0.0)
    val oldRegimeTaxable = (estimatedAnnualIncome - standardDeduction - total80CInvested).coerceAtLeast(0.0)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Section 80C Tax Analyzer",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }
                Text(
                    text = "FY 2024-26 Income Tax deductions & regime comparison.",
                    fontSize = 11.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("80C Exemption Used", fontSize = 11.sp, color = Slate400)
                            Text("₹${total80CInvested.toInt()} / ₹1,50,000", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val percent = (total80CInvested / max80CLimit).toFloat()
                        LinearProgressIndicator(
                            progress = { percent.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (percent > 0.8f) EmeraldGreen else AmberWarning,
                            trackColor = Slate800
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (remaining80CGap > 0) {
                                "💡 Invest ₹${remaining80CGap.toInt()} more in ELSS, PPF, or Tax-Saver FDs to save up to ₹${(remaining80CGap * 0.312).toInt()} in taxes."
                            } else {
                                "🎉 Maximum ₹1,50,000 Section 80C tax deduction fully utilized!"
                            },
                            fontSize = 11.sp,
                            color = Slate300,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, IndigoAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate850),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "REGIME COMPARISON ESTIMATE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Standard Deduction:", fontSize = 12.sp, color = Slate300)
                            Text("₹75,000 (New & Old)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate100)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("80C Deductions Claimed:", fontSize = 12.sp, color = Slate300)
                            Text("₹${total80CInvested.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EmeraldLight)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recommended Regime:", fontSize = 12.sp, color = Slate300)
                            val recommendation = if (total80CInvested > 120000) "Old Regime (High Deductions)" else "New Tax Regime (Slab Benefits)"
                            Text(recommendation, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberLight)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 15. Spend Anomaly & Guardian Dialog
@Composable
fun SpendAnomalyGuardianDialog(
    transactions: List<TransactionEntity>,
    onDismiss: () -> Unit
) {
    val duplicates = transactions
        .groupBy { "${it.title.trim().lowercase()}_${it.amount}_${it.date}" }
        .filter { it.value.size > 1 }
        .flatMap { it.value }

    val expenseList = transactions.filter { it.type == "EXPENSE" }
    val avgExpense = if (expenseList.isNotEmpty()) expenseList.map { it.amount }.average() else 1000.0
    val highSpikes = expenseList.filter { it.amount > 8000.0 || (it.amount > avgExpense * 2.5 && it.amount > 3000.0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spend Anomaly Guardian",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }
                Text(
                    text = "Automated scanner for duplicate debits and suspicious spending bursts.",
                    fontSize = 11.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (duplicates.isEmpty() && highSpikes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate950)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("All Clear!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate100)
                            Text("No duplicate debits or suspicious charge surges detected in your transactions.", fontSize = 11.sp, color = Slate400, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    if (duplicates.isNotEmpty()) {
                        Text("POTENTIAL DUPLICATE CHARGES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                        Spacer(modifier = Modifier.height(6.dp))
                        duplicates.take(3).forEach { dup ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Slate950)
                                    .border(1.dp, CrimsonRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(dup.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate100)
                                        Text("${dup.date} • ${dup.paymentMethod}", fontSize = 10.sp, color = Slate400)
                                    }
                                    Text("₹${dup.amount.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (highSpikes.isNotEmpty()) {
                        Text("HIGH VALUE OUTFLOWS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberLight)
                        Spacer(modifier = Modifier.height(6.dp))
                        highSpikes.take(3).forEach { spk ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Slate950)
                                    .border(1.dp, AmberWarning.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(spk.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate100)
                                        Text("${spk.date} • ${spk.category}", fontSize = 10.sp, color = Slate400)
                                    }
                                    Text("₹${spk.amount.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AmberLight)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dismiss", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// 15. Simulate Transaction Sheet / Dialog
// =========================================================================
@Composable
fun SimulateTxnSheet(
    onDismiss: () -> Unit,
    onSimulate: (rawNotificationText: String) -> Unit
) {
    var rawText by remember { mutableStateOf("Received ₹2,000 from Ramesh via UPI") }
    val parsed = remember(rawText) { TransactionParser.parse(rawText) }

    val presetTests = listOf(
        "Inflow: Ramesh UPI" to "Received ₹2,000 from Ramesh via UPI",
        "Outflow: Rahul GPay" to "Paid ₹450 to Rahul via Google Pay",
        "Outflow: Groceries" to "₹500 debited for groceries",
        "Inflow: Credited" to "credited with Rs 1,500",
        "Inflow: Deposit" to "₹5,000 deposited in your a/c",
        "Outflow: Sent" to "Sent ₹250 to Rahul",
        "Outflow: Transferred" to "Transferred ₹1,000 to vendor"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(IndigoBadgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = IndigoLight, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Simulate Txn", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate50)
                            Text("Test UPI & SMS notification parser", fontSize = 11.sp, color = Slate400)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "QUICK TEST PRESETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetTests.forEach { (label, sample) ->
                        val isSelected = rawText == sample
                        Surface(
                            onClick = { rawText = sample },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) IndigoPurple else Slate850,
                            border = BorderStroke(1.dp, if (isSelected) IndigoLight else Slate800)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Slate300,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "NOTIFICATION TEXT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("Paste SMS or UPI push notification text...", fontSize = 12.sp, color = Slate500) },
                    trailingIcon = {
                        if (rawText.isNotEmpty()) {
                            IconButton(onClick = { rawText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoLight,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate100,
                        focusedContainerColor = Slate950,
                        unfocusedContainerColor = Slate950
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "PARSED OUTPUT & BALANCE IMPACT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (parsed.confidence == ParserConfidence.REJECTED || parsed.amount <= 0.0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate950)
                            .border(1.dp, CrimsonRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = CrimsonLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ignored / Unmatched",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonLight
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reason: ${parsed.rejectionReason}. This notification is not classified as a valid financial debit or credit transaction.",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                } else {
                    val isCredit = parsed.direction == TransactionDirection.CREDIT || parsed.isInflow
                    val badgeBg = if (isCredit) EmeraldBadgeBg else CrimsonBadgeBg
                    val badgeColor = if (isCredit) EmeraldLight else CrimsonLight

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate950)
                            .border(1.dp, if (isCredit) EmeraldDark else CrimsonDark, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCredit) "+${formatInr(parsed.amount)}" else "-${formatInr(parsed.amount)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCredit) EmeraldLight else CrimsonLight
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (isCredit) "CREDIT (Inflow)" else "DEBIT (Outflow)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }

                            HorizontalDivider(color = Slate800, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isCredit) "Sender" else "Vendor / Merchant",
                                    fontSize = 11.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = parsed.merchant ?: parsed.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate100
                                )
                            }

                            if (parsed.referenceId.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Reference / UPI ID", fontSize = 11.sp, color = Slate400)
                                    Text(parsed.referenceId, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = IndigoLight)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Payment Method", fontSize = 11.sp, color = Slate400)
                                Text("${parsed.paymentMethod} • ${parsed.category}", fontSize = 11.sp, color = Slate200)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val canSimulate = parsed.confidence != ParserConfidence.REJECTED && parsed.amount > 0
                Button(
                    onClick = {
                        onSimulate(rawText)
                        onDismiss()
                    },
                    enabled = canSimulate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        disabledContainerColor = Slate800
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (canSimulate) Color.Black else Slate600, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulate & Record Transaction",
                        color = if (canSimulate) Color.Black else Slate500,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300),
                    border = BorderStroke(1.dp, Slate800),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}