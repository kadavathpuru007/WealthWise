/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development Team:
 * - Purushotham
 * - Surya
 * - Srinivas
 * - Karthik
 *
 * Module: Profile & System Permissions Settings
 */

package com.example.ui.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.notification.NotificationAccessUtils
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*
import com.example.ui.viewmodel.WealthWiseUiState

@Composable
fun ProfileScreen(
    state: WealthWiseUiState,
    onEditProfile: () -> Unit,
    onEditBalance: () -> Unit = {},
    onOpenBillReminders: () -> Unit,
    onOpenGuide: () -> Unit,
    onTogglePushNotifications: () -> Unit,
    onToggleBudgetAlerts: () -> Unit = {},
    onToggleBillEmiAlerts: () -> Unit = {},
    onToggleGoalAlerts: () -> Unit = {},
    onToggleSyncAlerts: () -> Unit = {},
    onTriggerTestNotification: () -> Unit = {},
    onEvaluateAlertsNow: () -> Unit = {},
    onToggleAccountAggregator: () -> Unit,
    onRequestNotificationAccess: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onLogout: () -> Unit,
    onToggleMobileView: () -> Unit = {},
    onResetData: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. USER PROFILE HEADER SECTION (Horizontal Full-Width Card) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading (Left): Circular Avatar (56dp) with edit camera badge
                    Box(
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(TextNavy)
                                .border(1.5.dp, BorderLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.userName.firstOrNull()?.uppercase() ?: "P",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Small edit camera badge positioned at bottom-right
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(FintechBlue)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Edit photo",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Middle (Expanded Column): User Name, Email, Status Badge
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = state.userName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = state.userEmail,
                            fontSize = 13.sp,
                            color = TextBlueGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Status Badge: Soft pill tag
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(FintechGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Personal Account • Verified",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF334155)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Trailing (Right): Compact "Edit" pill button
                    Surface(
                        onClick = onEditProfile,
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = FintechBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Edit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextNavy
                            )
                        }
                    }
                }

                // Quick-Stats Addition within Card
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val hasListenerAccess = NotificationAccessUtils.isNotificationListenerAccessGranted(context)
                        Text(
                            text = "Linked Accounts",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlueGrey
                        )
                        Text(
                            text = if (hasListenerAccess) "1 Sync Active" else "0 Active",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasListenerAccess) FintechGreen else TextNavy,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(BorderLight)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sync Status",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlueGrey
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(FintechGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechGreen
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(BorderLight)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Plan",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlueGrey
                        )
                        Text(
                            text = "Free Tier",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FintechBlue,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // --- 2. BALANCE & LEDGER MANAGEMENT SECTION ---
        Text(
            text = "BALANCE & LEDGER MANAGEMENT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextBlueGrey,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Current Calculated Balance",
                            fontSize = 12.sp,
                            color = TextBlueGrey
                        )
                        val calculatedBalance = state.startingBalance + state.totalInflow - state.totalOutflow
                        Text(
                            text = formatInr(calculatedBalance),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextNavy
                        )
                    }

                    Button(
                        onClick = onEditBalance,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Balance", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Opening Balance Baseline", fontSize = 12.sp, color = TextBlueGrey)
                    Text(formatInr(state.startingBalance), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Actual Inflow (+)", fontSize = 12.sp, color = TextBlueGrey)
                    Text("+${formatInr(state.totalInflow)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FintechGreen)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Actual Outflow (-)", fontSize = 12.sp, color = TextBlueGrey)
                    Text("-${formatInr(state.totalOutflow)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FintechRed)
                }
            }
        }

        // --- 3. LIVE FIREBASE CLOUD BACKEND CARD ---
        Text(
            text = "LIVE FIREBASE CLOUD BACKEND",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextBlueGrey,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(FintechBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Firebase Cloud",
                                tint = FintechBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Firebase Cloud Services",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "wealthwise-869dd",
                                fontSize = 12.sp,
                                color = TextBlueGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        color = FintechGreenBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(FintechGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Connected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FintechGreen,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auth & Security", fontSize = 12.sp, color = TextBlueGrey)
                    Text("Firebase Auth Active", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextNavy)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cloud Database", fontSize = 12.sp, color = TextBlueGrey)
                    Text("Firestore Real-time Sync", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextNavy)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Storage Bucket", fontSize = 12.sp, color = TextBlueGrey)
                    Text("wealthwise-869dd.firebasestorage.app", fontSize = 11.sp, color = TextNavy)
                }
            }
        }

        // --- 4. PRIVACY & SYSTEM ACCESS ---
        Text(
            text = "PRIVACY & SYSTEM ACCESS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextBlueGrey,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // 1. Notification Reading (Special Access)
                val isListenerGranted = state.notificationAccessStatus == com.example.permission.AccessStatus.CONNECTED
                PrivacyAccessItem(
                    icon = Icons.Default.Sync,
                    iconTint = FintechOrange,
                    title = "Notification Reading",
                    subtitle = "Used for transaction synchronization",
                    statusText = if (isListenerGranted) "Connected" else "Not Connected",
                    isGranted = isListenerGranted,
                    actionButtonText = if (isListenerGranted) "Manage" else "Connect",
                    onActionClick = {
                        if (isListenerGranted) {
                            try {
                                context.startActivity(com.example.notification.NotificationAccessUtils.getNotificationListenerSettingsIntent())
                            } catch (_: Exception) {}
                        } else {
                            onRequestNotificationAccess()
                        }
                    }
                )

                HorizontalDivider(color = BorderLight, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // 2. WealthWise Notifications (Runtime Permission)
                val isPostNotifGranted = state.postNotificationStatus.isGranted
                PrivacyAccessItem(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = FintechBlue,
                    title = "WealthWise Notifications",
                    subtitle = "Used for financial alerts",
                    statusText = if (isPostNotifGranted) "Allowed" else "Denied",
                    isGranted = isPostNotifGranted,
                    actionButtonText = if (isPostNotifGranted) "Manage" else "Allow",
                    onActionClick = {
                        if (isPostNotifGranted) {
                            try {
                                context.startActivity(com.example.notification.NotificationAccessUtils.getAppNotificationSettingsIntent(context))
                            } catch (_: Exception) {}
                        } else {
                            onRequestNotificationPermission()
                        }
                    }
                )

                HorizontalDivider(color = BorderLight, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // 3. App Usage Access (Special Access)
                val isUsageGranted = state.usageAccessStatus == com.example.permission.AccessStatus.CONNECTED
                PrivacyAccessItem(
                    icon = Icons.Default.QueryStats,
                    iconTint = FintechBlue,
                    title = "App Usage",
                    subtitle = "Used for subscription usage insights",
                    statusText = if (isUsageGranted) "Connected" else "Not Connected",
                    isGranted = isUsageGranted,
                    actionButtonText = if (isUsageGranted) "Manage" else "Connect",
                    onActionClick = {
                        if (isUsageGranted) {
                            try {
                                com.example.permission.PermissionManager.openUsageAccessSettings(context)
                            } catch (_: Exception) {}
                        } else {
                            onRequestUsageAccess()
                        }
                    }
                )

                HorizontalDivider(color = BorderLight, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // 4. Internet Access (Normal Permission)
                val isInternetGranted = state.internetStatus == com.example.permission.AccessStatus.CONNECTED
                PrivacyAccessItem(
                    icon = Icons.Default.Language,
                    iconTint = FintechGreen,
                    title = "Internet",
                    subtitle = "Available for online database sync",
                    statusText = if (isInternetGranted) "Available" else "Offline",
                    isGranted = isInternetGranted,
                    actionButtonText = "Active",
                    onActionClick = { }
                )
            }
        }

        // --- 5. FINANCIAL ALERTS & SYNC PREFERENCES ---
        Text(
            text = "FINANCIAL ALERTS & SYNC PREFERENCES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextBlueGrey,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Currency Preference Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(FintechBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = FintechBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Primary Currency", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextNavy)
                            Text("Indian Rupee (₹ INR)", fontSize = 12.sp, color = TextBlueGrey)
                        }
                    }
                    Text("Default", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FintechGreen)
                }

                HorizontalDivider(color = BorderLight, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // Account Aggregator / Notification Sync Toggle
                PermissionToggleRow(
                    icon = Icons.Default.Sync,
                    iconTint = FintechOrange,
                    title = "Financial Notification Sync",
                    subtitle = "Capture bank & UPI transactions automatically",
                    isChecked = state.accountAggregatorSyncEnabled,
                    onCheckedChange = { onToggleAccountAggregator() }
                )

                HorizontalDivider(color = BorderLight, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // Push Notifications Toggle
                PermissionToggleRow(
                    icon = Icons.Default.Notifications,
                    iconTint = FintechBlue,
                    title = "Device Financial Alerts",
                    subtitle = "Real Android status bar notifications for key events",
                    isChecked = state.pushNotificationsEnabled,
                    onCheckedChange = { onTogglePushNotifications() }
                )

                if (state.pushNotificationsEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sub-toggle: Budget alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Budget Limits (90% & Exceeded)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                                Text("Warns when category budget reaches 90% or overflows", fontSize = 11.sp, color = TextBlueGrey)
                            }
                            Switch(
                                checked = state.budgetAlertsEnabled,
                                onCheckedChange = { onToggleBudgetAlerts() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = FintechBlue
                                )
                            )
                        }

                        // Sub-toggle: Bill/EMI alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Upcoming Bills & EMIs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                                Text("Alerts 1 day before due date (if obligations exist)", fontSize = 11.sp, color = TextBlueGrey)
                            }
                            Switch(
                                checked = state.billEmiAlertsEnabled,
                                onCheckedChange = { onToggleBillEmiAlerts() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = FintechBlue
                                )
                            )
                        }

                        // Sub-toggle: Goal alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Goal Pace & Affordability", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                                Text("Alerts when falling behind or when budget is tight", fontSize = 11.sp, color = TextBlueGrey)
                            }
                            Switch(
                                checked = state.goalAlertsEnabled,
                                onCheckedChange = { onToggleGoalAlerts() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = FintechBlue
                                )
                            )
                        }

                        // Sub-toggle: Sync alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sync Attention Alerts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextNavy)
                                Text("Alerts if notification sync access is disabled", fontSize = 11.sp, color = TextBlueGrey)
                            }
                            Switch(
                                checked = state.syncAlertsEnabled,
                                onCheckedChange = { onToggleSyncAlerts() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = FintechBlue
                                )
                            )
                        }

                        // Action Buttons: Test Notification & Evaluate Now
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onTriggerTestNotification,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, FintechBlue)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = FintechBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Alert", fontSize = 12.sp, color = FintechBlue, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onEvaluateAlertsNow,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FintechBlue)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Alerts", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // --- 5. GENERAL PREFERENCES & UTILITIES ---
        Text(
            text = "PREFERENCES & UTILITIES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextBlueGrey,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                ProfileMenuItem(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    iconTint = FintechGreen,
                    title = "Simple Guide & FAQ",
                    subtitle = "1-minute tutorial on budgeting & goals",
                    onClick = onOpenGuide
                )

                HorizontalDivider(color = BorderLight, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))

                ProfileMenuItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = FintechRed,
                    title = "Log Out",
                    subtitle = "Safely sign out of WealthWise",
                    onClick = onLogout,
                    isDestructive = true
                )
            }
        }

        // Team Credits Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WealthWise Personal Finance",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
                Text(
                    text = "Encrypted Local Ledger • Real-time Cloud Synchronization",
                    fontSize = 11.sp,
                    color = TextBlueGrey,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Production Release • Version 1.0.0",
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Developed by Babu Singh",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FintechBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun PermissionToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BorderLight.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextNavy
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextBlueGrey,
                    lineHeight = 14.sp
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FintechBlue,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BorderLight
            )
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BorderLight.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextNavy
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PrivacyAccessItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    statusText: String,
    isGranted: Boolean,
    actionButtonText: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isGranted) FintechGreenBg else Color(0xFFF1F5F9))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isGranted) "✓ $statusText" else "• $statusText",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGranted) FintechGreen else TextBlueGrey,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Text(subtitle, fontSize = 11.sp, color = TextBlueGrey, lineHeight = 15.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onActionClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(30.dp),
            enabled = actionButtonText != "Active"
        ) {
            Text(
                text = actionButtonText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) TextNavy else FintechBlue
            )
        }
    }
}