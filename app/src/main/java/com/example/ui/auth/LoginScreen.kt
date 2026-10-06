package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToVerification: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToSetBalance: () -> Unit = {},
    authViewModel: AuthViewModel? = null,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    onClearError: () -> Unit = {}
) {
    val authUiState = authViewModel?.uiState?.collectAsState()?.value
    val displayError = errorMessage ?: authUiState?.errorMessage
    val isLoggingIn = isLoading || (authUiState?.isLoading == true)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    fun submitLogin(e: String, p: String) {
        focusManager.clearFocus()
        authViewModel?.clearError()
        onClearError()
        if (authViewModel != null) {
            authViewModel.signIn(e, p, onNavigateToVerification)
        }
        onLogin(e, p)
    }

    LaunchedEffect(displayError) {
        displayError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(authUiState?.authSuccess, authUiState?.hasCompletedSetup, authUiState?.isVerified) {
        if (authUiState?.authSuccess == true) {
            if (authUiState.hasCompletedSetup) {
                onNavigateToHome()
            } else {
                onNavigateToSetBalance()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .imePadding()
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
                    // Logo Badge
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(FintechGreenBg)
                            .border(1.5.dp, FintechGreen, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "WealthWise Logo",
                            tint = FintechGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "WealthWise",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "Smart Personal Finance & Expense Tracker",
                        fontSize = 13.sp,
                        color = TextBlueGrey,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tagline Added Here
                    Text(
                        text = "Be Smart Spend Great",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FintechGreen
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            authViewModel?.clearError()
                            onClearError()
                        },
                        label = { Text("Email Address") },
                        leadingIcon = { 
                            Icon(Icons.Default.Email, contentDescription = null, tint = TextBlueGrey) 
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FintechBlue,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextNavy,
                            unfocusedTextColor = TextNavy,
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Input with Visibility Toggle
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            authViewModel?.clearError()
                            onClearError()
                        },
                        label = { Text("Password") },
                        leadingIcon = { 
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextBlueGrey) 
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = TextBlueGrey
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (email.isNotBlank() && password.isNotBlank()) {
                                    submitLogin(email, password)
                                }
                            }
                        ),
                        singleLine = true,
                        isError = displayError != null,
                        supportingText = if (displayError != null) {
                            {
                                Text(
                                    text = displayError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (displayError != null) MaterialTheme.colorScheme.error else FintechBlue,
                            unfocusedBorderColor = if (displayError != null) MaterialTheme.colorScheme.error else BorderLight,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            focusedTextColor = TextNavy,
                            unfocusedTextColor = TextNavy,
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit Button
                    Button(
                        onClick = { submitLogin(email, password) },
                        enabled = !isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Register Link
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Don't have an account? ", 
                            color = TextBlueGrey, 
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Register",
                            color = FintechBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToRegister() }
                        )
                    }
                }
            }
        }
    }
}