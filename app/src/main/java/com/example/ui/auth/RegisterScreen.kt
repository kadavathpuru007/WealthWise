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
fun RegisterScreen(
    onRegister: (String, String, String) -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToVerification: () -> Unit = {},
    authViewModel: AuthViewModel? = null,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    onClearError: () -> Unit = {}
) {
    val authUiState = authViewModel?.uiState?.collectAsState()?.value
    val displayError = errorMessage ?: authUiState?.errorMessage
    val isRegistering = isLoading || (authUiState?.isLoading == true)

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    fun submitRegistration() {
        focusManager.clearFocus()
        authViewModel?.clearError()
        onClearError()
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        if (authViewModel != null) {
            authViewModel.register(trimmedName, trimmedEmail, trimmedPassword)
        }
        onRegister(trimmedName, trimmedEmail, trimmedPassword)
    }

    LaunchedEffect(displayError) {
        displayError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Navigate to email verification screen once verification email is triggered
    LaunchedEffect(authUiState?.isEmailSent) {
        if (authUiState?.isEmailSent == true) {
            onNavigateToVerification()
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
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(FintechBlueBg)
                            .border(1.5.dp, FintechBlue.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = FintechBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Create WealthWise Account",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "Track expenses, budget limits, & UPI parsing",
                        fontSize = 13.sp,
                        color = TextBlueGrey,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Firebase Project Badge
                    Surface(
                        color = FintechBlueBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, FintechBlue.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(FintechGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = FintechBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Firebase: wealthwise-869dd",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = FintechBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Full Name Input
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            authViewModel?.clearError()
                            onClearError()
                        },
                        label = { Text("Full Name") },
                        leadingIcon = { 
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextBlueGrey) 
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
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
                        label = { Text("Password (min. 6 characters)") },
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
                            onDone = { submitRegistration() }
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
                        onClick = { submitRegistration() },
                        enabled = !isRegistering,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isRegistering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Create Account & Verify",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Already have an account? ", color = TextBlueGrey, fontSize = 14.sp)
                        Text(
                            text = "Sign In",
                            color = FintechBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToLogin() }
                        )
                    }
                }
            }
        }
    }
}