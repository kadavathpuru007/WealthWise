package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetBalanceUiState(
    val balanceInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SetBalanceViewModel @JvmOverloads constructor(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SetBalanceUiState())
    val uiState: StateFlow<SetBalanceUiState> = _uiState.asStateFlow()

    fun onBalanceInputChange(input: String) {
        _uiState.value = _uiState.value.copy(balanceInput = input, errorMessage = null)
    }

    fun sanitizeBalance(inputString: String): Double {
        val sanitized = inputString.replace("₹", "").replace(",", "").trim()
        return sanitized.toDoubleOrNull() ?: 0.0
    }

    fun submitInitialBalance(
        inputString: String = _uiState.value.balanceInput,
        onSuccessNavigateToHome: () -> Unit
    ) {
        val sanitized = inputString.replace("₹", "").replace(",", "").trim()
        val initialBalance = sanitized.toDoubleOrNull() ?: 0.0

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                userPreferencesRepository.setInitialBalance(initialBalance)
                userPreferencesRepository.setOnboardingCompleted(true)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccessNavigateToHome()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to save initial balance"
                )
            }
        }
    }
}
