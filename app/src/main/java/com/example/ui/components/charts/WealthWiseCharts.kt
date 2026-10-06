package com.example.ui.components.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.financial.BudgetComparisonItem
import com.example.domain.financial.ChartPoint
import com.example.domain.financial.IncomeExpensePoint
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*

/**
 * Clean, consistent pill-style period selector for WealthWise charts.
 */
@Composable
fun WealthWiseTimeRangeSelector(
    selectedRange: String,
    ranges: List<Pair<String, String>>, // key to label, e.g. "1 DAY" to "1 DAY"
    onSelectRange: (String) -> Unit,
    modifier: Modifier = Modifier,
    equalWeight: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ranges.forEach { (key, label) ->
            val isSelected = selectedRange == key
            val boxModifier = if (equalWeight) {
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) FintechNavyCard else Color(0xFFF1F5F9))
                    .clickable { onSelectRange(key) }
                    .padding(vertical = 7.dp)
            } else {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) FintechNavyCard else Color(0xFFF1F5F9))
                    .clickable { onSelectRange(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            }
            Box(
                modifier = boxModifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextBlueGrey
                )
            }
        }
    }
}

/**
 * Standard container card for WealthWise charts.
 */
@Composable
fun WealthWiseChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = TextBlueGrey
                        )
                    }
                }
                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingContent()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

/**
 * Minimalist, professional empty state for charts when real data is not available.
 */
@Composable
fun WealthWiseChartEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Default.ShowChart
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextBlueGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextNavy,
                textAlign = TextAlign.Center
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Native Jetpack Compose Canvas Line Chart.
 * Features:
 * - Smooth polyline / curve
 * - Subtle gradient fill under curve
 * - Reference guide lines
 * - Interactive tap/drag scrubber tooltip displaying date and amount
 */
@Composable
fun WealthWiseLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = FintechBlue,
    fillGradient: Brush = Brush.verticalGradient(
        colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)
    ),
    height: Dp = 160.dp,
    formatAsCurrency: Boolean = true,
    emptyMessage: String = "No spending data yet"
) {
    if (points.isEmpty() || points.all { it.value == 0.0 }) {
        WealthWiseChartEmptyState(
            message = emptyMessage,
            subtitle = "Recorded financial activity will automatically appear here."
        )
        return
    }

    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val maxVal = remember(points) {
        points.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
    }
    val minVal = remember(points) {
        points.minOfOrNull { it.value }?.coerceAtLeast(0.0) ?: 0.0
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Scrubber / Tooltip info banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
            if (activePoint != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = activePoint.tooltipDate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextBlueGrey
                    )
                    Text(
                        text = if (formatAsCurrency) formatInr(activePoint.value) else "${activePoint.value}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = lineColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures(
                            onPress = { offset ->
                                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                val idx = ((offset.x / stepX) + 0.5f).toInt().coerceIn(0, points.size - 1)
                                selectedIndex = idx
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                            val idx = ((change.position.x / stepX) + 0.5f).toInt().coerceIn(0, points.size - 1)
                            selectedIndex = idx
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val padBottom = 16.dp.toPx()
                val drawHeight = h - padBottom

                // Grid reference lines (top, middle, baseline)
                val gridColor = Color(0xFFF1F5F9)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, 0f),
                    end = Offset(w, 0f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, drawHeight / 2f),
                    end = Offset(w, drawHeight / 2f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, drawHeight),
                    end = Offset(w, drawHeight),
                    strokeWidth = 1.dp.toPx()
                )

                if (points.size < 2) {
                    val y = drawHeight - ((points[0].value / maxVal).toFloat() * drawHeight)
                    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(w / 2, y))
                    return@Canvas
                }

                val stepX = w / (points.size - 1)
                val strokePath = Path()
                val fillPath = Path()

                points.forEachIndexed { i, pt ->
                    val x = i * stepX
                    val normalized = ((pt.value - minVal) / (maxVal - minVal).coerceAtLeast(1.0)).toFloat()
                    val y = drawHeight - (normalized * (drawHeight - 12.dp.toPx())) - 6.dp.toPx()

                    if (i == 0) {
                        strokePath.moveTo(x, y)
                        fillPath.moveTo(x, drawHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(w, drawHeight)
                fillPath.close()

                // Draw gradient under curve
                drawPath(path = fillPath, brush = fillGradient)

                // Draw main curve stroke
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Highlight selected or latest point
                val activeIdx = selectedIndex ?: (points.size - 1)
                if (activeIdx in points.indices) {
                    val selX = activeIdx * stepX
                    val pt = points[activeIdx]
                    val normalized = ((pt.value - minVal) / (maxVal - minVal).coerceAtLeast(1.0)).toFloat()
                    val selY = drawHeight - (normalized * (drawHeight - 12.dp.toPx())) - 6.dp.toPx()

                    // Vertical scrubber guideline
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(selX, 0f),
                        end = Offset(selX, drawHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Outer halo
                    drawCircle(
                        color = lineColor.copy(alpha = 0.2f),
                        radius = 8.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                    // Inner dot
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(selX, selY)
                    )
                }
            }
        }

        // X-axis evenly distributed date labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelCount = points.size.coerceAtMost(5)
            val step = if (points.size <= 5) 1 else (points.size - 1) / (labelCount - 1)

            for (i in 0 until points.size step step) {
                Text(
                    text = points[i].label,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            if (points.size > 5 && (points.size - 1) % step != 0) {
                Text(
                    text = points.last().label,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Paired Bar Chart for Home Income vs Expenses.
 */
@Composable
fun WealthWiseIncomeExpenseBarChart(
    points: List<IncomeExpensePoint>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No income or expense data yet"
) {
    if (points.isEmpty() || points.all { it.income == 0.0 && it.expense == 0.0 }) {
        WealthWiseChartEmptyState(
            message = emptyMessage,
            subtitle = "Completed income and expense transactions will automatically appear here."
        )
        return
    }

    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val maxVal = remember(points) {
        points.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1.0) ?: 1.0
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Scrubber / Tooltip banner
        val active = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
        if (active != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = active.tooltipDate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextBlueGrey
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "+${formatInr(active.income)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechGreen
                    )
                    Text(
                        text = "-${formatInr(active.expense)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechRed
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FintechGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Income", fontSize = 10.sp, color = TextBlueGrey)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FintechRed)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Expenses", fontSize = 10.sp, color = TextBlueGrey)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEachIndexed { idx, pt ->
                val isSelected = selectedIndex == idx
                val incHeightRatio = (pt.income / maxVal).toFloat().coerceIn(0.02f, 1f)
                val expHeightRatio = (pt.expense / maxVal).toFloat().coerceIn(0.02f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedIndex = idx }
                        .padding(horizontal = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                    ) {
                        // Income Bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(incHeightRatio)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isSelected) FintechGreen else FintechGreen.copy(alpha = 0.85f))
                        )
                        // Expense Bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(expHeightRatio)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isSelected) FintechRed else FintechRed.copy(alpha = 0.85f))
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = pt.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) TextNavy else TextMuted
                    )
                }
            }
        }
    }
}

/**
 * Budget vs Actual Bar Chart for Budget Page.
 * For each category shows:
 * Planned budget limit vs Actual spent, clearly highlighting when actual exceeds budget.
 */
@Composable
fun WealthWiseBudgetVsActualChart(
    items: List<BudgetComparisonItem>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No budget data yet"
) {
    if (items.isEmpty()) {
        WealthWiseChartEmptyState(
            message = emptyMessage,
            subtitle = "Set monthly budget categories to visualize planned vs actual spending."
        )
        return
    }

    var selectedItem by remember(items) { mutableStateOf<BudgetComparisonItem?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Selection Banner
        selectedItem?.let { sel ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (sel.isOverBudget) FintechRedBg else FintechBlueBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = sel.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel.isOverBudget) FintechRed else FintechBlue
                        )
                        Text(
                            text = if (sel.isOverBudget) "Exceeded by ₹${sel.exceededBy.toInt()}"
                            else "Remaining: ₹${sel.remaining.toInt()} (${100 - sel.percentUsed}% left)",
                            fontSize = 11.sp,
                            color = TextNavy
                        )
                    }
                    Text(
                        text = "${sel.percentUsed}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if (sel.isOverBudget) FintechRed else FintechBlue
                    )
                }
            }
        }

        // Category comparison rows
        items.forEach { item ->
            val isOver = item.isOverBudget
            val spentRatio = if (item.budgetAmount > 0) {
                (item.actualSpent / item.budgetAmount).toFloat().coerceIn(0f, 1f)
            } else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedItem = item }
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOver) FintechRed else FintechBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextNavy
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Spent: ${formatInr(item.actualSpent)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOver) FintechRed else TextNavy
                        )
                        Text(
                            text = " / ${formatInr(item.budgetAmount)}",
                            fontSize = 11.sp,
                            color = TextBlueGrey
                        )
                        if (isOver) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(FintechRedBg)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Over",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FintechRed
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Progress track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isOver) FintechRedBg else Color(0xFFF1F5F9))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(spentRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isOver) FintechRed
                                else if (spentRatio >= 0.85f) FintechOrange
                                else FintechBlue
                            )
                    )
                }
            }
        }
    }
}

/**
 * Subscriptions Monthly Cost Bar Chart.
 */
@Composable
fun WealthWiseSubscriptionCostChart(
    items: List<ChartPoint>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No subscriptions detected"
) {
    if (items.isEmpty()) {
        WealthWiseChartEmptyState(
            message = emptyMessage,
            subtitle = "Active subscriptions with recurring charges will be analyzed here."
        )
        return
    }

    var selectedItem by remember(items) { mutableStateOf<ChartPoint?>(null) }
    val maxCost = remember(items) { items.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0 }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        selectedItem?.let { sel ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FintechBlueBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sel.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechBlue
                    )
                    Text(
                        text = sel.tooltipDetails ?: formatInr(sel.value),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextNavy
                    )
                }
            }
        }

        items.take(6).forEach { item ->
            val ratio = (item.value / maxCost).toFloat().coerceIn(0.05f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedItem = item }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatInr(item.value)}/mo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechBlue
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFF1F5F9))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(FintechBlue)
                    )
                }
            }
        }
    }
}

/**
 * Dual-line chart for Income vs Expenses on the Home Dashboard.
 * Draws two smooth bezier curves — green for income, red for expenses.
 * Tap or drag to show a crosshair scrubber with exact values.
 */
@Composable
fun WealthWiseIncomeExpenseLineChart(
    points: List<IncomeExpensePoint>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    emptyMessage: String = "No income or expense data yet"
) {
    if (points.isEmpty() || points.all { it.income == 0.0 && it.expense == 0.0 }) {
        WealthWiseChartEmptyState(
            message = emptyMessage,
            subtitle = "Completed income and expense transactions will automatically appear here."
        )
        return
    }

    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val maxVal = remember(points) {
        points.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1.0) ?: 1.0
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Scrubber / Tooltip banner
        val active = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
        if (active != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = active.tooltipDate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextBlueGrey
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "+${formatInr(active.income)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechGreen
                    )
                    Text(
                        text = "-${formatInr(active.expense)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechRed
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FintechGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Income", fontSize = 10.sp, color = TextBlueGrey)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FintechRed)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Expenses", fontSize = 10.sp, color = TextBlueGrey)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size > 1) {
                            val step = size.width.toFloat() / (points.size - 1)
                            val idx = ((offset.x / step).toInt()).coerceIn(0, points.size - 1)
                            selectedIndex = idx
                        }
                    }
                }
                .pointerInput(points) {
                    detectDragGestures { change, _ ->
                        if (points.size > 1) {
                            val step = size.width.toFloat() / (points.size - 1)
                            val idx = ((change.position.x / step).toInt()).coerceIn(0, points.size - 1)
                            selectedIndex = idx
                        }
                    }
                }
        ) {
            if (points.size < 2) return@Canvas
            val w = size.width
            val h = size.height
            val step = w / (points.size - 1)
            val yPad = 8.dp.toPx()

            fun xOf(i: Int) = i * step
            fun yOf(v: Double) = h - yPad - ((v / maxVal) * (h - 2 * yPad)).toFloat()

            // Helper: build smooth bezier path through points
            fun buildPath(values: List<Double>): Path {
                val path = Path()
                path.moveTo(xOf(0), yOf(values[0]))
                for (i in 0 until values.size - 1) {
                    val cx = (xOf(i) + xOf(i + 1)) / 2f
                    path.cubicTo(
                        cx, yOf(values[i]),
                        cx, yOf(values[i + 1]),
                        xOf(i + 1), yOf(values[i + 1])
                    )
                }
                return path
            }

            val incomeValues = points.map { it.income }
            val expenseValues = points.map { it.expense }

            // Draw income line (green)
            drawPath(
                path = buildPath(incomeValues),
                color = FintechGreen,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw expense line (red)
            drawPath(
                path = buildPath(expenseValues),
                color = FintechRed,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw crosshair and dots at selected index
            val selIdx = selectedIndex ?: (points.size - 1)
            val selX = xOf(selIdx)
            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(selX, 0f),
                end = Offset(selX, h),
                strokeWidth = 1.dp.toPx()
            )
            // Income dot
            drawCircle(
                color = FintechGreen,
                radius = 4.dp.toPx(),
                center = Offset(selX, yOf(incomeValues[selIdx]))
            )
            // Expense dot
            drawCircle(
                color = FintechRed,
                radius = 4.dp.toPx(),
                center = Offset(selX, yOf(expenseValues[selIdx]))
            )
        }

        // X-axis labels (show first, middle, last)
        if (points.size >= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().label, fontSize = 9.sp, color = TextMuted)
                if (points.size > 2) {
                    Text(points[points.size / 2].label, fontSize = 9.sp, color = TextMuted)
                }
                Text(points.last().label, fontSize = 9.sp, color = TextMuted)
            }
        }
    }
}
