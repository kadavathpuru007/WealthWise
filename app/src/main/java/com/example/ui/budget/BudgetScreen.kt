package com.example.ui.budget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*
import com.example.ui.viewmodel.BudgetCategoryUiModel
import com.example.ui.viewmodel.BudgetsUiState
import com.example.ui.viewmodel.BudgetsViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.SuggestedCategoryUiModel
import java.util.Calendar

/**
 * BudgetScreen is the authoritative, complete Budget Manager for WealthWise.
 *
 * Displays:
 * - Total Monthly Budget Cap, Current Spending, Remaining, Safe to Spend
 * - All budget categories with limits, spending, remaining, percent, overage banners
 * - Add / Edit / Delete budget actions
 * - "Suggested for You" recommendations from real last-30-days spending
 * - Responsive Add/Edit dialog with IME padding and duplicate prevention
 */
@Composable
fun BudgetScreen(
    budgets: List<BudgetEntity> = emptyList(),
    transactions: List<TransactionEntity> = emptyList(),
    availableFunds: Double = 0.0,
    safeToSpend: Double = 0.0,
    budgetsViewModel: BudgetsViewModel = viewModel(),
    onAdjustBudget: () -> Unit = {},
    onAddBudgetCategory: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by budgetsViewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<BudgetCategoryUiModel?>(null) }
    var categoryToDelete by remember { mutableStateOf<BudgetCategoryUiModel?>(null) }

    // Calendar bounds for accurate current-month fallback calculation
    val currentMonthTxns = remember(transactions) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val endCal = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val end = endCal.timeInMillis
        transactions.filter { it.timestamp in start..end && HomeViewModel.isCompletedTransaction(it) }
    }

    // Authoritative metrics from reactive BudgetsViewModel with graceful fallback
    val displayCategories = if (uiState.categories.isNotEmpty()) {
        uiState.categories
    } else {
        budgets.mapIndexed { index, b ->
            val spent = currentMonthTxns
                .filter {
                    !it.isTransfer && !it.isInflow &&
                    (it.type.equals("EXPENSE", ignoreCase = true) || it.type.equals("debit", ignoreCase = true)) &&
                    !it.category.equals("Savings Goal", ignoreCase = true) &&
                    !it.category.equals("Goal Allocation", ignoreCase = true) &&
                    it.category.trim().equals(b.category.trim(), ignoreCase = true)
                }
                .sumOf { kotlin.math.abs(it.amount) }
            val remaining = (b.monthlyLimit - spent).coerceAtLeast(0.0)
            val progress = if (b.monthlyLimit > 0) (spent / b.monthlyLimit).toFloat().coerceIn(0f, 1f) else 0f
            val percent = if (b.monthlyLimit > 0) ((spent / b.monthlyLimit) * 100).toInt().coerceAtLeast(0) else 0
            val isExceeded = b.monthlyLimit > 0 && spent > b.monthlyLimit
            val overrun = (spent - b.monthlyLimit).coerceAtLeast(0.0)

            BudgetCategoryUiModel(
                id = (index + 1).toLong(),
                name = b.category,
                budgetLimit = b.monthlyLimit,
                spent = spent,
                remaining = remaining,
                progress = progress,
                percentUsed = percent,
                isExceeded = isExceeded,
                overrun = overrun,
                colorHex = b.colorHex
            )
        }
    }

    val totalBudget = if (uiState.totalMonthlyBudgetCap > 0.0) uiState.totalMonthlyBudgetCap else budgets.sumOf { it.monthlyLimit }
    val totalSpent = if (uiState.currentMonthBudgetedExpenses > 0.0) {
        uiState.currentMonthBudgetedExpenses
    } else {
        currentMonthTxns.filter {
            !it.isTransfer && !it.isInflow &&
            (it.type.equals("EXPENSE", ignoreCase = true) || it.type.equals("debit", ignoreCase = true)) &&
            !it.category.equals("Savings Goal", ignoreCase = true) &&
            !it.category.equals("Goal Allocation", ignoreCase = true)
        }.sumOf { kotlin.math.abs(it.amount) }
    }
    val remaining = (totalBudget - totalSpent).coerceAtLeast(0.0)
    val overBudget = if (totalSpent > totalBudget) totalSpent - totalBudget else 0.0
    val overallPercent = if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt().coerceAtLeast(0) else 0
    val progressRatio = if (totalBudget > 0) (totalSpent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f

    val effectiveSafeToSpend = if (uiState.safeToSpend > 0.0) uiState.safeToSpend else safeToSpend
    val isOverBudgetFunds = availableFunds > 0 && totalBudget > availableFunds
    val shortfall = totalBudget - availableFunds

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = FintechNavyCard,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 80.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget Category", tint = Color.White)
            }
        },
        containerColor = Color.White
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextNavy)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Monthly Budgets",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextNavy
                            )
                            Text(
                                text = "Complete Category & Budget Manager",
                                fontSize = 11.sp,
                                color = TextBlueGrey
                            )
                        }
                    }


                }
            }

            // Error Banner if present
            if (uiState.errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = FintechRedBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = FintechRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                fontSize = 12.sp,
                                color = FintechRed,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { budgetsViewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = FintechRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Available Funds & Shortfall Warning
            if (isOverBudgetFunds) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FintechRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = FintechRedBg.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = FintechRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Budget Exceeds Available Funds",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FintechRed
                                )
                                Text(
                                    text = "Your planned budgets (${formatInr(totalBudget)}) exceed available funds (${formatInr(availableFunds)}). Please review your limits.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }
                }
            }

            // Summary Card
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL MONTHLY CAP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechBlueLight,
                                letterSpacing = 1.sp
                            )
                            if (availableFunds > 0) {
                                Text(
                                    text = "Available: ${formatInr(availableFunds)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate300
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${formatInr(totalSpent)} / ${formatInr(totalBudget)}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Contextual Metrics: Remaining & Safe to Spend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Remaining: ${formatInr(remaining)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (remaining > 0) EmeraldLight else FintechRedLight
                            )
                            Text(
                                text = "Safe to Spend: ${formatInr(effectiveSafeToSpend)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FintechBlueLight
                            )
                        }

                        if (overBudget > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Exceeded by ${formatInr(overBudget)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechRedLight
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (totalSpent > totalBudget) FintechRedLight else FintechBlueLight,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$overallPercent% of budget consumed",
                                fontSize = 11.sp,
                                color = if (totalSpent > totalBudget) FintechRedLight else Slate300
                            )
                            if (availableFunds > 0 && !isOverBudgetFunds) {
                                Text(
                                    text = "${formatInr((availableFunds - totalBudget).coerceAtLeast(0.0))} unallocated",
                                    fontSize = 11.sp,
                                    color = EmeraldLight
                                )
                            }
                        }
                    }
                }
            }

            // "Suggested for You" Recommendations Section
            if (uiState.suggestedCategories.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Suggested for You",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextNavy
                            )
                            Text(
                                text = "Based on your spending in the last 30 days",
                                fontSize = 11.sp,
                                color = TextBlueGrey
                            )
                        }

                        uiState.suggestedCategories.filter { !it.name.equals("Savings Goal", ignoreCase = true) }.forEach { suggested ->
                            SuggestedCategoryCard(
                                suggested = suggested,
                                onAddClick = {
                                    budgetsViewModel.quickAddSuggestedCategory(suggested)
                                }
                            )
                        }
                    }
                }
            }

            // Category List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "All Budget Categories",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "${displayCategories.size} Categories",
                        fontSize = 12.sp,
                        color = TextBlueGrey
                    )
                }
            }

            // Budgets by category
            if (displayCategories.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No budget categories found.", fontSize = 13.sp, color = TextBlueGrey)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard)
                            ) {
                                Text("+ Add Your First Budget", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                items(displayCategories, key = { it.id }) { cat ->
                    BudgetCategoryItemCard(
                        cat = cat,
                        onEdit = { editingCategory = cat },
                        onDelete = { categoryToDelete = cat }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Add Category Dialog
    if (showAddDialog) {
        AddEditCategoryDialog(
            title = "Add Budget Category",
            initialName = "",
            initialLimit = 5000.0,
            existingCategories = displayCategories,
            currentCategoryId = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, limit ->
                budgetsViewModel.addCategory(name, limit)
                showAddDialog = false
            }
        )
    }

    // Edit Category Dialog
    editingCategory?.let { cat ->
        AddEditCategoryDialog(
            title = "Edit Budget Category",
            initialName = cat.name,
            initialLimit = cat.budgetLimit,
            existingCategories = displayCategories,
            currentCategoryId = cat.id,
            onDismiss = { editingCategory = null },
            onConfirm = { name, limit ->
                budgetsViewModel.updateCategory(cat.id, name, limit)
                editingCategory = null
            }
        )
    }

    // Delete Confirmation Dialog
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = {
                Text(text = "Delete Budget Category?", fontWeight = FontWeight.Bold, color = TextNavy)
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${cat.name}'? Existing transactions will remain untouched.",
                    fontSize = 13.sp,
                    color = TextBlueGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        budgetsViewModel.deleteCategory(cat.id)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FintechRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel", color = TextBlueGrey)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun BudgetCategoryItemCard(
    cat: BudgetCategoryUiModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (cat.isExceeded) FintechRed.copy(alpha = 0.4f) else BorderLight,
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = if (cat.isExceeded) FintechRedBg.copy(alpha = 0.5f) else CardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = cat.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Category",
                            tint = TextBlueGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Category",
                            tint = FintechRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatInr(cat.spent)} / ${formatInr(cat.budgetLimit)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (cat.isExceeded) FintechRed else TextNavy
                )

                Text(
                    text = if (cat.isExceeded) {
                        "Exceeded by ${formatInr(cat.overrun)}"
                    } else {
                        "Remaining: ${formatInr(cat.remaining)} · ${cat.percentUsed}% used"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (cat.isExceeded) FintechRed else FintechGreen
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { cat.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (cat.isExceeded) FintechRed else FintechBlue,
                trackColor = BorderLight
            )
        }
    }
}

@Composable
private fun SuggestedCategoryCard(
    suggested: SuggestedCategoryUiModel,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FintechTealBg),
        border = BorderStroke(1.dp, FintechTeal.copy(alpha = 0.3f))
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
                    text = suggested.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
                Text(
                    text = "Avg: ${formatInr(suggested.averageMonthlySpend)}/mo · Suggested: ${formatInr(suggested.suggestedLimit)}",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FintechTeal),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("+ Add Budget", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun AddEditCategoryDialog(
    title: String,
    initialName: String,
    initialLimit: Double,
    existingCategories: List<BudgetCategoryUiModel>,
    currentCategoryId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, limit: Double) -> Unit
) {
    var nameInput by remember { mutableStateOf(initialName) }
    var limitInput by remember { mutableStateOf(if (initialLimit > 0) initialLimit.toInt().toString() else "") }
    var localError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Text(
                    text = "Configure category name and monthly spending threshold.",
                    fontSize = 12.sp,
                    color = TextBlueGrey
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        localError = null
                    },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Groceries, Subscriptions") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = limitInput,
                    onValueChange = {
                        limitInput = it
                        localError = null
                    },
                    label = { Text("Monthly Budget Limit") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = FintechGreen) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Quick Preset Limit Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("2000", "5000", "10000", "20000").forEach { preset ->
                        FilterChip(
                            selected = limitInput == preset,
                            onClick = { limitInput = preset },
                            label = { Text("₹$preset", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FintechBlueBg,
                                selectedLabelColor = FintechBlue,
                                containerColor = LightBg,
                                labelColor = TextNavy
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = limitInput == preset,
                                borderColor = BorderLight,
                                selectedBorderColor = FintechBlue
                            )
                        )
                    }
                }

                localError?.let { err ->
                    Text(
                        text = err,
                        color = FintechRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextBlueGrey)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val trimmedName = nameInput.trim()
                            if (trimmedName.isBlank()) {
                                localError = "Category name cannot be empty"
                                return@Button
                            }

                            val parsedLimit = limitInput.replace("₹", "").replace(",", "").trim().toDoubleOrNull()
                            if (parsedLimit == null || parsedLimit <= 0) {
                                localError = "Please enter a valid positive budget amount"
                                return@Button
                            }

                            // Duplicate check case-insensitively, ignoring current category when editing
                            val isDuplicate = existingCategories.any {
                                (currentCategoryId == null || it.id != currentCategoryId) &&
                                        it.name.trim().equals(trimmedName, ignoreCase = true)
                            }
                            if (isDuplicate) {
                                localError = "Category \"$trimmedName\" already exists"
                                return@Button
                            }

                            onConfirm(trimmedName, parsedLimit)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard)
                    ) {
                        Text("Save Budget", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}