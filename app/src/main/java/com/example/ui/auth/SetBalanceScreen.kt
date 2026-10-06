package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyRupee
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SetBalanceScreen(
    onSubmitBalance: (Double) -> Unit
) {
    var balanceInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    fun handleSubmission() {
        if (isSubmitting) return
        isSubmitting = true
        focusManager.clearFocus()

        // Robust cleanup: remove ₹, spaces, commas, and negative signs
        val sanitized = balanceInput
            .replace("₹", "")
            .replace(",", "")
            .trim()

        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        val finalBalance = parsed.coerceAtLeast(0.0)

        onSubmitBalance(finalBalance)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
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
                // Currency Icon Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(FintechGreenBg)
                        .border(2.dp, FintechGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CurrencyRupee,
                        contentDescription = null,
                        tint = FintechGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Enter Starting Balance",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Text(
                    text = "Welcome to WealthWise! Please enter your current bank balance to initialize your financial pulse.",
                    fontSize = 14.sp,
                    color = TextBlueGrey,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { input ->
                        // Allow digits and at most one decimal point
                        if (input.all { it.isDigit() || it == '.' || it == ',' } && input.count { it == '.' } <= 1) {
                            balanceInput = input
                        }
                    },
                    label = { Text("Starting Bank Balance (INR)") },
                    placeholder = { Text("0") },
                    prefix = { 
                        Text(
                            text = "₹ ", 
                            color = FintechGreen, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 18.sp
                        ) 
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { handleSubmission() }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FintechGreen,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextNavy,
                        unfocusedTextColor = TextNavy,
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { handleSubmission() },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Complete Setup & Launch Home",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for SetBalanceScreen.
 */
@Composable
fun EnterCurrentBalanceScreen(
    onSubmitBalance: (Double) -> Unit
) {
    SetBalanceScreen(onSubmitBalance = onSubmitBalance)
}