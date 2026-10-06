/**
 * WealthWise - Smart Personal Finance Application
 *
 * Navigation Drawer: Clean, production fintech structure
 * Header: WEALTHWISE / Personal Finance
 * Sections: FINANCES & SETTINGS
 */

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppScreen

@Composable
fun WealthWiseDrawerContent(
    currentScreen: AppScreen,
    userName: String = "User",
    userEmail: String = "",
    onNavigate: (AppScreen) -> Unit,
    onNavigateWithFilter: (AppScreen, String) -> Unit = { screen, _ -> onNavigate(screen) },
    onOpenBudget: () -> Unit = {},
    onCloseDrawer: () -> Unit
) {
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0D1527),
        drawerContentColor = Color.White,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Header: WEALTHWISE / Personal Finance
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "WealthWise",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "WEALTHWISE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Personal Finance",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 1: FINANCES
            Text(
                text = "FINANCES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // 1. Dashboard
                DrawerFinanceItem(
                    icon = Icons.Default.GridView,
                    label = "Dashboard",
                    isSelected = currentScreen == AppScreen.HOME,
                    onClick = {
                        onNavigate(AppScreen.HOME)
                        onCloseDrawer()
                    }
                )

                // 2. Investments
                DrawerFinanceItem(
                    icon = Icons.Default.TrendingUp,
                    label = "Investments",
                    isSelected = currentScreen == AppScreen.INVESTMENTS,
                    onClick = {
                        onNavigate(AppScreen.INVESTMENTS)
                        onCloseDrawer()
                    }
                )

                // 6. Subscriptions
                DrawerFinanceItem(
                    icon = Icons.Default.Subscriptions,
                    label = "Subscriptions",
                    isSelected = currentScreen == AppScreen.SUBSCRIPTION_KILLER,
                    onClick = {
                        onNavigate(AppScreen.SUBSCRIPTION_KILLER)
                        onCloseDrawer()
                    }
                )


                // 8. Loans & Debt
                DrawerFinanceItem(
                    icon = Icons.Default.AccountBalance,
                    label = "Loans & Debt",
                    isSelected = currentScreen == AppScreen.LOANS_DEBTS,
                    onClick = {
                        onNavigate(AppScreen.LOANS_DEBTS)
                        onCloseDrawer()
                    }
                )

                // 9. Financial Goals
                DrawerFinanceItem(
                    icon = Icons.Default.Flag,
                    label = "Financial Goals",
                    isSelected = currentScreen == AppScreen.GOALS,
                    onClick = {
                        onNavigate(AppScreen.GOALS)
                        onCloseDrawer()
                    }
                )

                // 10. Sync
                DrawerFinanceItem(
                    icon = Icons.Default.Sync,
                    label = "Sync",
                    isSelected = false,
                    badgeText = "Active",
                    onClick = {
                        onNavigate(AppScreen.SETTINGS)
                        onCloseDrawer()
                    }
                )

            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: SETTINGS
            Text(
                text = "SETTINGS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DrawerFinanceItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    isSelected = currentScreen == AppScreen.SETTINGS || currentScreen == AppScreen.MORE,
                    onClick = {
                        onNavigate(AppScreen.SETTINGS)
                        onCloseDrawer()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerFinanceItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                modifier = Modifier.weight(1f)
            )
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10B981))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
