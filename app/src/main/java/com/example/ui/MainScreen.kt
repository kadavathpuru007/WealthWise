package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.WealthWiseBottomNav
import com.example.ui.theme.Slate950
import com.example.ui.viewmodel.AppScreen

/**
 * MainScreen provides the main top-level Scaffold container configured with:
 * - dedicated floatingActionButton slot with circular Sync FAB
 * - floatingActionButtonPosition = FabPosition.End (or FabPosition.Center) avoiding any BottomNav overlap
 * - content modifier receiving innerPadding
 */
@Composable
fun MainScreen(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onSyncNow: () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = topBar,
        snackbarHost = snackbarHost,
        bottomBar = {
            if (currentScreen != AppScreen.LOGIN &&
                currentScreen != AppScreen.REGISTER &&
                currentScreen != AppScreen.EMAIL_VERIFICATION &&
                currentScreen != AppScreen.ENTER_BALANCE &&
                currentScreen != AppScreen.SUBSCRIPTION_KILLER &&
                currentScreen != AppScreen.UPI_PAYMENT
            ) {
                WealthWiseBottomNav(
                    currentScreen = currentScreen,
                    onNavigate = onNavigate
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (currentScreen == AppScreen.HOME) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigate(AppScreen.UPI_PAYMENT) },
                    containerColor = Slate950,
                    contentColor = Color.White,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Pay via UPI",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pay",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}
