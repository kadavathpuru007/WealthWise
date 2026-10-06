/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development:
 * - Purushotham

 * Module: Macro Market Benchmarks & Price Movement Analytics
 * Strictly separated from user's personal financial portfolio.
 * Real reference benchmark quotes and TradingView charts for:
 * - Gold (XAUUSD)
 * - Silver (XAGUSD)
 * - NIFTY 50 (NSE:NIFTY)
 * - SENSEX (BSE:SENSEX)
 */

package com.example.ui.market

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.financial.MarketBenchmark
import com.example.domain.financial.PageAnalyticsEngine
import com.example.ui.components.charts.WealthWiseChartCard
import com.example.ui.components.charts.WealthWiseLineChart
import com.example.ui.components.charts.WealthWiseTimeRangeSelector
import com.example.ui.theme.*
import java.util.Locale

data class MarketInstrument(
    val id: String,
    val name: String,
    val symbol: String,
    val exchange: String,
    val category: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBg: Color,
    val tradingViewUrl: String,
    val description: String
)

private val marketInstruments = listOf(
    MarketInstrument(
        id = "GOLD",
        name = "Gold",
        symbol = "XAUUSD",
        exchange = "Spot Bullion (Global)",
        category = "Precious Commodity",
        icon = Icons.Default.Diamond,
        iconColor = FintechOrange,
        iconBg = FintechOrangeBg,
        tradingViewUrl = "https://www.tradingview.com/symbols/XAUUSD/",
        description = "Global gold spot price benchmark denominated in US Dollars per troy ounce."
    ),
    MarketInstrument(
        id = "SILVER",
        name = "Silver",
        symbol = "XAGUSD",
        exchange = "Spot Bullion (Global)",
        category = "Precious Commodity",
        icon = Icons.Default.Circle,
        iconColor = Color(0xFF64748B),
        iconBg = Color(0xFFF1F5F9),
        tradingViewUrl = "https://www.tradingview.com/symbols/XAGUSD/",
        description = "International silver benchmark in US Dollars per troy ounce."
    ),
    MarketInstrument(
        id = "NIFTY_50",
        name = "NIFTY 50",
        symbol = "NSE:NIFTY",
        exchange = "National Stock Exchange of India",
        category = "National Equity Index",
        icon = Icons.Default.TrendingUp,
        iconColor = FintechBlue,
        iconBg = FintechBlueBg,
        tradingViewUrl = "https://in.tradingview.com/symbols/NSE-NIFTY/",
        description = "India's benchmark stock market index representing 50 premier companies across sectors."
    ),
    MarketInstrument(
        id = "SENSEX",
        name = "SENSEX",
        symbol = "BSE:SENSEX",
        exchange = "Bombay Stock Exchange",
        category = "National Equity Index",
        icon = Icons.Default.ShowChart,
        iconColor = FintechGreen,
        iconBg = FintechGreenBg,
        tradingViewUrl = "https://in.tradingview.com/symbols/BSE-SENSEX/",
        description = "The oldest benchmark stock index in India, tracking 30 prominent constituent firms."
    )
)

@Composable
fun MarketScreen(
    focusedSymbol: String? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val benchmarks = remember { PageAnalyticsEngine.getMarketBenchmarks() }

    var selectedInstrumentId by remember(focusedSymbol) {
        val matched = if (focusedSymbol != null) {
            benchmarks.find {
                it.symbol.contains(focusedSymbol, ignoreCase = true) ||
                        it.displayName.contains(focusedSymbol, ignoreCase = true)
            }?.id
        } else null
        mutableStateOf(matched ?: "GOLD")
    }

    var selectedTimeRange by remember { mutableStateOf("1M") }
    var isSimulatedOffline by remember { mutableStateOf(false) }

    val activeBenchmark = remember(benchmarks, selectedInstrumentId) {
        benchmarks.find { it.id == selectedInstrumentId } ?: benchmarks.first()
    }

    val chartPoints = remember(activeBenchmark, selectedTimeRange) {
        activeBenchmark.historyPoints[selectedTimeRange]
            ?: activeBenchmark.historyPoints["1M"]
            ?: emptyList()
    }

    val periodHigh = remember(chartPoints) { chartPoints.maxOfOrNull { it.value } ?: activeBenchmark.numericValue }
    val periodLow = remember(chartPoints) { chartPoints.minOfOrNull { it.value } ?: activeBenchmark.numericValue }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Header ---
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
                        text = "Market Benchmarks",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "Macro reference rates & national market indices",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                }
            }
        }

        // --- 2. Portfolio Separation & Disclaimer Notice ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate50),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FintechBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = FintechBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Reference Data Only",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Market benchmark data is strictly separated from your actual financial ledger. WealthWise is not a trading or execution platform and does not manage brokerage holdings.",
                            fontSize = 11.sp,
                            color = TextBlueGrey,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // --- 3. PRIMARY GRAPH: Price Movement ---
        item {
            WealthWiseChartCard(
                title = "Price Movement",
                subtitle = "${activeBenchmark.displayName} (${activeBenchmark.symbol}) • ${activeBenchmark.exchange}",
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeBenchmark.isPositive) FintechGreenBg else FintechRedBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${activeBenchmark.changeAmount} (${activeBenchmark.changePercent})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeBenchmark.isPositive) FintechGreen else FintechRed
                        )
                    }
                }
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(marketInstruments) { inst ->
                        val isSelected = inst.id == selectedInstrumentId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedInstrumentId = inst.id },
                            label = { Text(inst.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FintechNavyCard,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF8FAFC),
                                labelColor = TextNavy
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = activeBenchmark.currentValue,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextNavy
                        )
                        Text(
                            text = "High: ${String.format(Locale.US, "%.2f", periodHigh)} • Low: ${String.format(Locale.US, "%.2f", periodLow)}",
                            fontSize = 11.sp,
                            color = TextBlueGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                WealthWiseTimeRangeSelector(
                    selectedRange = selectedTimeRange,
                    ranges = listOf(
                        "1D" to "1D",
                        "1W" to "1W",
                        "1M" to "1M",
                        "3M" to "3M",
                        "1Y" to "1Y"
                    ),
                    onSelectRange = { selectedTimeRange = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSimulatedOffline) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF8FAFC)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextBlueGrey, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Market data temporarily unavailable",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlueGrey
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { isSimulatedOffline = false },
                            colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Retry", fontSize = 12.sp, color = Color.White)
                        }
                    }
                } else {
                    WealthWiseLineChart(
                        points = chartPoints,
                        lineColor = if (activeBenchmark.isPositive) FintechGreen else FintechRed,
                        emptyMessage = "Market data temporarily unavailable",
                        height = 170.dp
                    )
                }
            }
        }

        // --- 4. Market Instruments List ---
        items(marketInstruments) { instrument ->
            val isFocused = focusedSymbol != null && (
                    instrument.symbol.contains(focusedSymbol, ignoreCase = true) ||
                            instrument.name.contains(focusedSymbol, ignoreCase = true)
                    )
            val benchmark = benchmarks.find { it.id == instrument.id }
            MarketInstrumentCard(
                instrument = instrument,
                benchmark = benchmark,
                isFocused = isFocused,
                onSelect = { selectedInstrumentId = instrument.id },
                onOpenTradingView = {
                    openBrowserUrl(context, instrument.tradingViewUrl)
                }
            )
        }

        // --- 5. Footer Note ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Powered by TradingView Charts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextNavy
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap any benchmark above to view live interactive candlestick charts, volume, and technical indicators on TradingView.",
                        fontSize = 11.sp,
                        color = TextBlueGrey,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun MarketInstrumentCard(
    instrument: MarketInstrument,
    benchmark: MarketBenchmark?,
    isFocused: Boolean = false,
    onSelect: () -> Unit = {},
    onOpenTradingView: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FintechBlue else BorderLight,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) FintechBlueBg.copy(alpha = 0.3f) else CardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(instrument.iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = instrument.icon,
                            contentDescription = instrument.name,
                            tint = instrument.iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = instrument.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextNavy
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(FintechBlueBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = instrument.symbol,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FintechBlue
                                )
                            }
                        }

                        Text(
                            text = instrument.exchange,
                            fontSize = 11.sp,
                            color = TextBlueGrey,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = benchmark?.currentValue ?: "",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextNavy
                    )
                    if (benchmark != null) {
                        Text(
                            text = "${benchmark.changeAmount} (${benchmark.changePercent})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (benchmark.isPositive) FintechGreen else FintechRed,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = instrument.description,
                fontSize = 11.sp,
                color = TextBlueGrey,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenTradingView,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FintechBlue.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FintechBlue)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View ${instrument.symbol} on TradingView",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun openBrowserUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback gracefully if no browser installed
    }
}