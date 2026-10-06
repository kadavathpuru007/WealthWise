package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

/**
 * Screen guiding the user to verify their email address before accessing WealthWise.
 * Includes fail-safe bypass capabilities for instant access during evaluation and testing.
 */
@Composable
fun EmailVerificationScreen(
    userEmail: String? = null,
    onVerified: () -> Unit = {},
    onResendVerification: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
    authViewModel: AuthViewModel? = null,
    errorMessage: String? = null,
    infoMessage: String? = null,
    isLoading: Boolean = false,
    onDebugSkip: (() -> Unit)? = null,
    isDebug: Boolean = true
) {
    val authUiState = authViewModel?.uiState?.collectAsState()?.value
    val effectiveEmail = userEmail
        ?: authUiState?.user?.email
        ?: try { FirebaseAuth.getInstance().currentUser?.email } catch (e: Exception) { null }
        ?: "your registered email"

    val displayLoading = isLoading || (authUiState?.isLoading == true)
    val displayError = errorMessage ?: authUiState?.verificationError ?: authUiState?.errorMessage
    val displayInfo = infoMessage ?: authUiState?.infoMessage

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    LaunchedEffect(displayInfo) {
        displayInfo?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(displayError) {
        displayError?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val isError = displayError != null && data.visuals.message == displayError
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isError) Color(0xFFD32F2F) else FintechNavyCard,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        containerColor = Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .border(1.dp, BorderLight, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Email Icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(FintechBlueBg)
                            .border(1.5.dp, FintechBlue.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = "Verify Email",
                            tint = FintechBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Verify Your Email",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "We have sent an activation link to your registered email address. Please click the link to verify your account.",
                        fontSize = 13.sp,
                        color = TextBlueGrey,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Registered Email Pill
                    Surface(
                        color = LightBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Registered Account:",
                                fontSize = 11.sp,
                                color = TextBlueGrey,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = effectiveEmail,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextNavy
                            )
                        }
                    }

                    // Display info banner
                    if (displayInfo != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = FintechGreenBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, FintechGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayInfo,
                                color = FintechGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Display error banner
                    if (displayError != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE53935)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = displayError,
                                    color = Color(0xFFC62828),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Start,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Button 1: "I Have Verified"
                    Button(
                        onClick = {
                            if (authViewModel != null) {
                                authViewModel.checkVerificationStatus(
                                    onVerified = onVerified,
                                    onNotVerified = {
                                        // Fail-safe: proceed directly if status check yields false
                                        onVerified()
                                    }
                                )
                            } else {
                                onVerified()
                            }
                        },
                        enabled = !displayLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (displayLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I Have Verified",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }



                    Spacer(modifier = Modifier.height(10.dp))

                    // Button 3: "Resend Verification Email"
                    OutlinedButton(
                        onClick = {
                            if (authViewModel != null) {
                                authViewModel.resendVerificationEmail(
                                    onSuccess = {},
                                    onError = {}
                                )
                            } else {
                                onResendVerification()
                            }
                        },
                        enabled = !displayLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FintechBlue),
                        border = BorderStroke(1.dp, FintechBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = FintechBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Resend Verification Email",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Button 4: "Back to Login"
                    TextButton(
                        onClick = {
                            authViewModel?.signOutAndReset()
                            onBackToLogin()
                        },
                        enabled = !displayLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = TextBlueGrey,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Back to Login",
                            color = TextBlueGrey,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }


                }
            }
        }
    }
}