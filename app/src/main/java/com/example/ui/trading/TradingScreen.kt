/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development Team:
 * - Purushotham
 */

package com.example.ui.trading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TradeEntity
import com.example.domain.trading.BrokerSecurityState
import com.example.domain.trading.TradingMoneyFormatter
import com.example.ui.theme.*
import com.example.ui.viewmodel.TradeValidationResult
import com.example.ui.viewmodel.TradingUiState
import com.example.ui.viewmodel.TradingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingScreen(
    viewModel: TradingViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showLogTradeSheet by remember { mutableStateOf(false) }
    var tradeToDelete by remember { mutableStateOf<TradeEntity?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTrades = remember(uiState.tradesList, selectedFilter, searchQuery) {
        uiState.tradesList.filter { trade ->
            val matchesFilter = when (selectedFilter) {
                "Equities" -> trade.tradeType.equals(TradeEntity.TYPE_EQUITY, ignoreCase = true)
                "F&O" -> trade.tradeType.equals(TradeEntity.TYPE_FNO, ignoreCase = true)
                "Crypto" -> trade.tradeType.equals(TradeEntity.TYPE_CRYPTO, ignoreCase = true)
                "Commodity" -> trade.tradeType.equals(TradeEntity.TYPE_COMMODITY, ignoreCase = true)
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                trade.symbol.lowercase(Locale.ROOT).contains(q) ||
                        trade.brokerName.lowercase(Locale.ROOT).contains(q) ||
                        trade.tradeType.lowercase(Locale.ROOT).contains(q)
            }
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trading & Investments",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = "Portfolio ledger & trade journal",
                            fontSize = 12.sp,
                            color = TextBlueGrey
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = TextNavy
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { showLogTradeSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = FintechBlue),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Trade", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBg)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Top Portfolio Card (Dark Navy Financial Card)
            item {
                TradingPortfolioCard(uiState = uiState)
            }

            // 2. Capital Flow Tracker Visual
            item {
                CapitalFlowTrackerCard(uiState = uiState)
            }

            // 3. Broker Trust & Regulatory Card
            item {
                BrokerSecurityStatusCard(securityState = uiState.brokerTrustStatus)
            }

            // 4. Trade Journal Section Header with Filter Chips & Search
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRADE JOURNAL (${filteredTrades.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextBlueGrey,
                            letterSpacing = 1.sp
                        )
                    }

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by symbol, broker, or type...", fontSize = 13.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextBlueGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = TextBlueGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            focusedBorderColor = FintechBlue,
                            unfocusedBorderColor = BorderLight
                        )
                    )

                    // Filter Chips: All, Equities, F&O, Crypto, Commodity
                    val filters = listOf("All", "Equities", "F&O", "Crypto", "Commodity")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filters.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FintechBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = CardWhite,
                                    labelColor = TextNavy
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) FintechBlue else BorderLight
                                )
                            )
                        }
                    }
                }
            }

            // 5. Trades List or Empty State
            if (filteredTrades.isEmpty()) {
                item {
                    EmptyTradeJournalState()
                }
            } else {
                items(filteredTrades, key = { it.id }) { trade ->
                    TradeJournalItemCard(
                        trade = trade,
                        onDeleteClick = { tradeToDelete = trade }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet: Log Trade
    if (showLogTradeSheet) {
        LogTradeBottomSheet(
            onDismiss = {
                showLogTradeSheet = false
                viewModel.clearError()
            },
            onSaveTrade = { symbol, type, buyPrice, sellPrice, quantity, broker, status ->
                viewModel.logTrade(
                    symbol = symbol,
                    type = type,
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    quantity = quantity,
                    broker = broker,
                    status = status,
                    onResult = { result ->
                        if (result is TradeValidationResult.Success) {
                            showLogTradeSheet = false
                        }
                    }
                )
            },
            errorMessage = uiState.errorMessage
        )
    }

    // Safe Delete Confirmation Dialog
    tradeToDelete?.let { trade ->
        val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }
        AlertDialog(
            onDismissRequest = { tradeToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = FintechRed
                )
            },
            title = {
                Text(
                    text = "Delete this trade?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Are you sure you want to remove this trade entry from your journal?",
                        fontSize = 13.sp,
                        color = TextBlueGrey
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = LightBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "Symbol: ${trade.symbol}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextNavy)
                            Text(
                                text = "Realized P&L: ${TradingMoneyFormatter.formatPnl(trade.realizedPnl)}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (trade.realizedPnl >= 0) FintechGreen else FintechRed
                            )
                            Text(text = "Date: ${dateFormat.format(Date(trade.tradeDate))}", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: Deleting a trade journal entry does NOT alter your central financial transactions or bank ledger.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrade(trade)
                        tradeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FintechRed)
                ) {
                    Text("Delete Trade", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { tradeToDelete = null }) {
                    Text("Cancel", color = TextNavy)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Top Portfolio Card - Dark Navy Surface displaying realized P&L and capital stats.
 */
@Composable
fun TradingPortfolioCard(uiState: TradingUiState) {
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
                    text = "TOTAL REALIZED P&L",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FintechBlueLight,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Realized Gross P&L",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate300
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val pnl = uiState.totalRealizedPnl
            val pnlColor = when {
                pnl > 0.0 -> FintechGreenLight
                pnl < 0.0 -> FintechRedLight
                else -> Color.White
            }

            Text(
                text = TradingMoneyFormatter.formatPnl(pnl, includeDecimals = false),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = pnlColor
            )

            Text(
                text = "Gross price-difference P&L (exchange charges & brokerage not included)",
                fontSize = 10.sp,
                color = Slate400,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Net Deployed", fontSize = 11.sp, color = Slate400)
                    Text(
                        text = TradingMoneyFormatter.format(uiState.totalDeployedCapital),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net In Market", fontSize = 11.sp, color = Slate400)
                    Text(
                        text = TradingMoneyFormatter.format(uiState.netCapitalInMarket),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechBlueLight
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Withdrawn", fontSize = 11.sp, color = Slate400)
                    Text(
                        text = TradingMoneyFormatter.format(uiState.totalPayoutsWithdrawn),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Win Rate", fontSize = 11.sp, color = Slate400)
                    Text(
                        text = "${uiState.winRatePercentage}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.winRatePercentage >= 50) FintechGreenLight else Slate300
                    )
                }
            }
        }
    }
}

/**
 * Capital Flow Tracker Visual - Compact visualization of money movement.
 */
@Composable
fun CapitalFlowTrackerCard(uiState: TradingUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AUTOMATED BROKER CAPITAL FLOW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextBlueGrey,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Ledger Synced",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = FintechGreen
                )
            }

            // Visual flow: Deployed -> Broker / Investment Account -> Payouts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Deployed Box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FintechBlueBg)
                        .padding(10.dp)
                ) {
                    Text("Capital Deployed", fontSize = 10.sp, color = TextBlueGrey, fontWeight = FontWeight.Medium)
                    Text(
                        text = TradingMoneyFormatter.format(uiState.totalDeployedCapital),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechBlue
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(16.dp)
                )

                // Broker Accounts Box
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(10.dp)
                ) {
                    Text("Net In Market", fontSize = 10.sp, color = TextBlueGrey, fontWeight = FontWeight.Medium)
                    Text(
                        text = TradingMoneyFormatter.format(uiState.netCapitalInMarket),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(16.dp)
                )

                // Payouts Box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FintechGreenBg)
                        .padding(10.dp)
                ) {
                    Text("Payouts Received", fontSize = 10.sp, color = TextBlueGrey, fontWeight = FontWeight.Medium)
                    Text(
                        text = TradingMoneyFormatter.format(uiState.totalPayoutsWithdrawn),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechGreen
                    )
                }
            }

            Text(
                text = "Tracked directly from your central completed financial debits/credits to verified investment platforms. Net Capital in Market reflects cash transfer movement, not fluctuating market valuations.",
                fontSize = 11.sp,
                color = TextBlueGrey,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Broker Trust & Regulatory Verification Card.
 */
@Composable
fun BrokerSecurityStatusCard(securityState: BrokerSecurityState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (securityState) {
                is BrokerSecurityState.Verified -> {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(FintechGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Verified broker",
                            tint = FintechGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Broker Verification",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextNavy
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(FintechGreenBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Verified",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FintechGreen
                                )
                            }
                        }
                        Text(
                            text = "${securityState.brokerName} • Regulatory status verified from authoritative SEBI directory",
                            fontSize = 12.sp,
                            color = TextBlueGrey,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        securityState.lastVerified?.let {
                            val dt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(it))
                            Text(
                                text = "Dataset verified: $dt",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
                is BrokerSecurityState.Alert -> {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(FintechRedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Regulatory alert",
                            tint = FintechRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Regulatory Alert",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = FintechRed
                        )
                        Text(
                            text = "${securityState.platformName} appears on the configured RBI Alert List of unauthorized platforms. Verify regulatory authorization before using this service.",
                            fontSize = 12.sp,
                            color = TextBlueGrey,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                is BrokerSecurityState.DetectedUnverified -> {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(FintechBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Broker detected",
                            tint = FintechBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Broker App Detected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = "${securityState.brokerName} detected on device. Regulatory status could not be verified automatically.",
                            fontSize = 12.sp,
                            color = TextBlueGrey,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                BrokerSecurityState.Neutral -> {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Broker status",
                            tint = TextBlueGrey,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Broker Verification",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = "No supported broker app detected on device. No known regulatory alert detected.",
                            fontSize = 12.sp,
                            color = TextBlueGrey,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = "Note: Absence from the RBI Alert List is not conclusive proof of authorization.",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
                BrokerSecurityState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = FintechBlue)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Checking broker security signatures...", fontSize = 12.sp, color = TextBlueGrey)
                }
            }
        }
    }
}

/**
 * Trade Card in the Trade Journal.
 */
@Composable
fun TradeJournalItemCard(
    trade: TradeEntity,
    onDeleteClick: () -> Unit
) {
    val isClosed = trade.status.equals(TradeEntity.STATUS_CLOSED, ignoreCase = true)
    val isPositive = trade.realizedPnl > 0.0
    val isNegative = trade.realizedPnl < 0.0
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = trade.symbol,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(FintechBlueBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = trade.tradeType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FintechBlue
                        )
                    }
                    if (!isClosed) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OPEN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }

                // P&L badge
                if (isClosed) {
                    val pnlBg = if (isPositive) FintechGreenBg else if (isNegative) FintechRedBg else Color(0xFFF1F5F9)
                    val pnlText = if (isPositive) FintechGreen else if (isNegative) FintechRed else TextNavy
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pnlBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = TradingMoneyFormatter.formatPnl(trade.realizedPnl),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = pnlText
                        )
                    }
                } else {
                    Text(
                        text = "Realized: ₹0",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Price Details: Buy -> Sell | Qty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${TradingMoneyFormatter.format(trade.buyPrice, includeDecimals = true)} → ${TradingMoneyFormatter.format(trade.sellPrice, includeDecimals = true)}  •  Qty: ${trade.quantity}",
                    fontSize = 12.sp,
                    color = TextNavy,
                    fontWeight = FontWeight.Medium
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete trade ${trade.symbol}",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Footer: Date and Broker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = dateFormat.format(Date(trade.tradeDate)), fontSize = 11.sp, color = TextMuted)
                Text(text = "Broker: ${trade.brokerName}", fontSize = 11.sp, color = TextBlueGrey, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Empty state displayed when no trades match the current filters.
 */
@Composable
fun EmptyTradeJournalState() {
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(FintechBlueBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoGraph,
                    contentDescription = null,
                    tint = FintechBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "No trades recorded yet",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
            Text(
                text = "Log your completed trades to build your trade journal.",
                fontSize = 12.sp,
                color = TextBlueGrey,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "Broker fund transfers can be tracked automatically from your WealthWise transaction ledger.",
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Modal Bottom Sheet for logging a manual trade into the trade journal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTradeBottomSheet(
    onDismiss: () -> Unit,
    onSaveTrade: (
        symbol: String,
        type: String,
        buyPrice: Double,
        sellPrice: Double,
        quantity: Int,
        broker: String,
        status: String
    ) -> Unit,
    errorMessage: String? = null
) {
    val focusManager = LocalFocusManager.current

    var symbol by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TradeEntity.TYPE_EQUITY) }
    var selectedStatus by remember { mutableStateOf(TradeEntity.STATUS_CLOSED) }
    var brokerName by remember { mutableStateOf("") }
    var buyPriceText by remember { mutableStateOf("") }
    var sellPriceText by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }

    val buyPrice = buyPriceText.toDoubleOrNull() ?: 0.0
    val sellPrice = sellPriceText.toDoubleOrNull() ?: 0.0
    val quantity = quantityText.toIntOrNull() ?: 0

    // Real-time P&L preview
    val projectedPnl = remember(selectedStatus, buyPrice, sellPrice, quantity) {
        TradeEntity.calculateRealizedPnl(selectedStatus, buyPrice, sellPrice, quantity)
    }

    val symbolSuggestions = listOf("NIFTY", "BANKNIFTY", "RELIANCE", "TCS", "INFY", "HDFCBANK", "TATAMOTORS")
    val brokerSuggestions = listOf("Zerodha", "Groww", "Angel One", "Upstox", "Dhan", "INDmoney", "Paytm Money", "ICICI Direct")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Log Trade Entry",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )

            errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = FintechRedBg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = FintechRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = err, fontSize = 12.sp, color = FintechRed, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Symbol with Suggestions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Symbol / Instrument", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase(Locale.ROOT) },
                    placeholder = { Text("e.g. RELIANCE, NIFTY", fontSize = 13.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                // Suggestion chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    symbolSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { symbol = suggestion },
                            label = { Text(suggestion, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Trade Type & Status Selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Trade Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                    var typeExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { typeExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(selectedType, fontSize = 12.sp, color = TextNavy)
                        }
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            TradeEntity.SUPPORTED_TYPES.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t) },
                                    onClick = {
                                        selectedType = t
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Position Status", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                    var statusExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { statusExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(selectedStatus, fontSize = 12.sp, color = TextNavy)
                        }
                        DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            listOf(TradeEntity.STATUS_CLOSED, TradeEntity.STATUS_OPEN).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = {
                                        selectedStatus = s
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Broker with Suggestions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Broker / Trading Platform", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                OutlinedTextField(
                    value = brokerName,
                    onValueChange = { brokerName = it },
                    placeholder = { Text("e.g. Zerodha, Groww", fontSize = 13.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    brokerSuggestions.forEach { b ->
                        SuggestionChip(
                            onClick = { brokerName = b },
                            label = { Text(b, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Buy Price, Sell Price, Quantity
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Buy Price", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                    OutlinedTextField(
                        value = buyPriceText,
                        onValueChange = { buyPriceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Right) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Sell Price", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                    OutlinedTextField(
                        value = sellPriceText,
                        onValueChange = { sellPriceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Right) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Quantity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            }

            // Real-Time P&L Preview Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = LightBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedStatus == TradeEntity.STATUS_CLOSED) "Projected Realized P&L:" else "Position Status:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextBlueGrey
                    )
                    Text(
                        text = if (selectedStatus == TradeEntity.STATUS_CLOSED) {
                            TradingMoneyFormatter.formatPnl(projectedPnl)
                        } else {
                            "OPEN (0 realized P&L)"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedStatus == TradeEntity.STATUS_CLOSED) {
                            if (projectedPnl >= 0) FintechGreen else FintechRed
                        } else {
                            TextNavy
                        }
                    )
                }
            }

            // Action Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSaveTrade(
                        symbol,
                        selectedType,
                        buyPrice,
                        sellPrice,
                        quantity,
                        brokerName,
                        selectedStatus
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FintechBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Trade Entry", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}