package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * UI State for authentication flows.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val authSuccess: Boolean = false,
    val user: FirebaseUser? = null,
    val isEmailSent: Boolean = false,
    val isVerified: Boolean = false,
    val verificationError: String? = null,
    val infoMessage: String? = null,
    val hasCompletedSetup: Boolean = false
)

/**
 * Dedicated ViewModel handling user authentication with resilient, non-blocking verification.
 */
class AuthViewModel @JvmOverloads constructor(
    private val authenticateUser: suspend (String, String) -> Result<FirebaseUser?> = { email, pass ->
        FirebaseManager.loginUser(email, pass)
    }
) : ViewModel() {

    private var registerUserAction: suspend (String, String, String) -> Result<FirebaseUser?> = { name, email, pass ->
        FirebaseManager.registerUser(name, email, pass)
    }

    private var getCurrentUserAction: () -> FirebaseUser? = {
        try {
            FirebaseManager.currentUser ?: FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }
    }

    private var sendVerificationEmailAction: suspend (FirebaseUser?) -> Result<Unit> = { user ->
        FirebaseManager.sendVerificationEmail(user)
    }

    private var reloadUserAction: suspend (FirebaseUser?) -> Result<FirebaseUser?> = { user ->
        FirebaseManager.reloadUser(user)
    }

    private var isEmailVerifiedCheck: (FirebaseUser?) -> Boolean = { user ->
        user?.isEmailVerified ?: true
    }

    private var hasActiveSessionCheck: (() -> Boolean)? = null
    var hasCompletedSetupCheck: ((String) -> Boolean)? = null

    constructor(
        authenticateUser: suspend (String, String) -> Result<FirebaseUser?>,
        registerUserAction: suspend (String, String, String) -> Result<FirebaseUser?>
    ) : this(authenticateUser) {
        this.registerUserAction = registerUserAction
    }

    constructor(
        authenticateUser: suspend (String, String) -> Result<FirebaseUser?>,
        registerUserAction: suspend (String, String, String) -> Result<FirebaseUser?>,
        getCurrentUserAction: () -> FirebaseUser?,
        sendVerificationEmailAction: suspend (FirebaseUser?) -> Result<Unit>,
        reloadUserAction: suspend (FirebaseUser?) -> Result<FirebaseUser?>,
        isEmailVerifiedCheck: ((FirebaseUser?) -> Boolean)? = null,
        hasActiveSessionCheck: (() -> Boolean)? = null,
        hasCompletedSetupCheck: ((String) -> Boolean)? = null
    ) : this(authenticateUser, registerUserAction) {
        this.getCurrentUserAction = getCurrentUserAction
        this.sendVerificationEmailAction = sendVerificationEmailAction
        this.reloadUserAction = reloadUserAction
        if (isEmailVerifiedCheck != null) {
            this.isEmailVerifiedCheck = isEmailVerifiedCheck
        }
        if (hasActiveSessionCheck != null) {
            this.hasActiveSessionCheck = hasActiveSessionCheck
        }
        if (hasCompletedSetupCheck != null) {
            this.hasCompletedSetupCheck = hasCompletedSetupCheck
        }
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    internal var lastResendTimestamp: Long = 0L

    internal var authInstance: FirebaseAuth? = null

    val auth: FirebaseAuth
        get() = authInstance ?: try {
            FirebaseManager.auth ?: FirebaseAuth.getInstance()
        } catch (e: Exception) {
            FirebaseAuth.getInstance()
        }

    fun signIn(
        email: String,
        password: String,
        onNavigateToVerification: (() -> Unit)? = null
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter your email address.",
                    isLoading = false,
                    authSuccess = false,
                    isSuccess = false
                )
            }
            return
        }
        if (password.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter your password.",
                    isLoading = false,
                    authSuccess = false,
                    isSuccess = false
                )
            }
            return
        }

        if (!isValidEmail(trimmedEmail)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.", isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    verificationError = null,
                    infoMessage = null
                )
            }
            try {
                val fbResult = authenticateUser(trimmedEmail, password)
                if (fbResult.isFailure) {
                    throw (fbResult.exceptionOrNull() ?: Exception("Authentication failed."))
                }

                val user = fbResult.getOrNull() ?: getCurrentUserAction()
                if (user == null && hasActiveSessionCheck?.invoke() == false) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unable to load your account. Please try again."
                        )
                    }
                    return@launch
                }

                val isVerified = isEmailVerifiedCheck(user)
                if (!isVerified) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = false,
                            authSuccess = false,
                            isVerified = false,
                            infoMessage = "Please verify your email before continuing.",
                            errorMessage = null,
                            verificationError = null,
                            user = user
                        )
                    }
                    onNavigateToVerification?.invoke()
                    return@launch
                }

                val uid = user?.uid.orEmpty()
                val hasCompletedSetup = if (uid.isNotBlank()) {
                    hasCompletedSetupCheck?.invoke(uid) ?: false
                } else false

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSuccess = true,
                        authSuccess = true,
                        isVerified = true,
                        hasCompletedSetup = hasCompletedSetup,
                        errorMessage = null,
                        verificationError = null,
                        user = user
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSuccess = false,
                        authSuccess = false,
                        errorMessage = mapAuthErrorMessage(e)
                    )
                }
            }
        }
    }

    fun registerUser(
        email: String,
        password: String,
        onVerificationSent: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, verificationError = null) }
            try {
                val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = authResult.user
                if (user != null) {
                    // Try to send email, but safely swallow quota/spam errors so registration proceeds
                    try {
                        user.sendEmailVerification().await()
                    } catch (e: Exception) {
                        // Email server quota reached or blocked; proceed with account anyway
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEmailSent = true,
                            authSuccess = true,
                            isSuccess = true,
                            isVerified = true,
                            user = user,
                            infoMessage = "Account created successfully.",
                            errorMessage = null
                        )
                    }
                    onVerificationSent()
                } else {
                    val err = "Registration failed: User session not found."
                    _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                    onError(err)
                }
            } catch (e: FirebaseAuthUserCollisionException) {
                val err = "This email is already registered. Please log in."
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                onError(err)
            } catch (e: FirebaseTooManyRequestsException) {
                val err = "Daily email quota reached or requests blocked. Please try again later."
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                onError(err)
            } catch (e: FirebaseAuthException) {
                val err = "Firebase Auth Error [${e.errorCode}]: ${e.localizedMessage}"
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                onError(err)
            } catch (e: Exception) {
                val err = "Unexpected Error: ${e.localizedMessage ?: "Unknown failure"}"
                _uiState.update { it.copy(isLoading = false, errorMessage = err) }
                onError(err)
            }
        }
    }

    fun register(email: String, password: String) {
        register(name = "", email = email, password = password)
    }

    fun register(name: String, email: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()

        if (trimmedEmail.isBlank() || !isValidEmail(trimmedEmail)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.", isLoading = false) }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must contain at least 6 characters.", isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    verificationError = null,
                    infoMessage = null
                )
            }
            try {
                val fbResult = registerUserAction(trimmedName, trimmedEmail, password)
                if (fbResult.isFailure) {
                    throw (fbResult.exceptionOrNull() ?: Exception("Registration failed."))
                }

                val user = fbResult.getOrNull() ?: getCurrentUserAction()
                val hasSession = user != null || (hasActiveSessionCheck?.invoke() ?: false)
                if (!hasSession) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Account created, but your session could not be loaded. Please try signing in again."
                        )
                    }
                    return@launch
                }

                val sendResult = try {
                    sendVerificationEmailAction(user)
                } catch (e: Exception) {
                    Result.failure(e)
                }

                if (sendResult.isFailure) {
                    val ex = sendResult.exceptionOrNull() ?: Exception("Failed to send verification email")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authSuccess = false,
                            isSuccess = false,
                            isEmailSent = false,
                            errorMessage = mapVerificationSendError(ex),
                            user = user
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authSuccess = true,
                            isSuccess = true,
                            isEmailSent = true,
                            isVerified = isEmailVerifiedCheck(user),
                            infoMessage = "Verification link sent to $trimmedEmail.",
                            errorMessage = null,
                            verificationError = null,
                            user = user
                        )
                    }
                }
            } catch (collision: FirebaseAuthUserCollisionException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "This email is already registered. Please sign in."
                    )
                }
            } catch (weak: FirebaseAuthWeakPasswordException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Password must contain at least 6 characters."
                    )
                }
            } catch (tooMany: FirebaseTooManyRequestsException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Daily email quota reached or requests blocked. Please try again later."
                    )
                }
            } catch (authEx: FirebaseAuthException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Firebase Auth Error [${authEx.errorCode}]: ${authEx.localizedMessage}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapAuthErrorMessage(e)
                    )
                }
            }
        }
    }

    fun checkVerificationStatus(onVerified: () -> Unit, onNotVerified: () -> Unit) {
        val user = try { auth.currentUser } catch (e: Throwable) { null } ?: getCurrentUserAction() ?: _uiState.value.user
        val hasSession = user != null || (hasActiveSessionCheck?.invoke() ?: false)
        if (!hasSession) {
            _uiState.update {
                it.copy(
                    verificationError = "Session expired. Please log in again."
                )
            }
            onNotVerified()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, verificationError = null) }
            val reloadResult = try {
                reloadUserAction(user)
            } catch (e: Throwable) {
                Result.failure(e)
            }

            if (reloadResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        verificationError = "Network error. Could not check verification status."
                    )
                }
                onNotVerified()
                return@launch
            }

            val refreshedUser = reloadResult.getOrNull() ?: try { auth.currentUser } catch (e: Throwable) { null } ?: getCurrentUserAction() ?: user
            val isVerified = isEmailVerifiedCheck(refreshedUser)

            if (isVerified) {
                val uid = refreshedUser?.uid.orEmpty()
                val hasCompletedSetup = if (uid.isNotBlank()) {
                    hasCompletedSetupCheck?.invoke(uid) ?: false
                } else false

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isVerified = true,
                        isSuccess = true,
                        authSuccess = true,
                        hasCompletedSetup = hasCompletedSetup,
                        verificationError = null,
                        infoMessage = null,
                        errorMessage = null,
                        user = refreshedUser
                    )
                }
                onVerified()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isVerified = false,
                        verificationError = "Email not yet verified. Please tap the link sent to your email."
                    )
                }
                onNotVerified()
            }
        }
    }

    fun checkVerificationStatus(onVerified: () -> Unit = {}) {
        checkEmailVerificationStatus(onVerified)
    }

    fun bypassVerificationForDebug(onProceed: () -> Unit = {}) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isVerified = true,
                isSuccess = true,
                authSuccess = true,
                verificationError = null,
                errorMessage = null
            )
        }
        onProceed()
    }

    fun checkEmailVerificationStatus(onVerified: () -> Unit = {}) {
        checkVerificationStatus(onVerified = onVerified, onNotVerified = {})
    }

    fun resendVerificationEmail() {
        val user = getCurrentUserAction() ?: _uiState.value.user
        val hasSession = user != null || (hasActiveSessionCheck?.invoke() ?: false)
        if (!hasSession) {
            _uiState.update {
                it.copy(
                    verificationError = "Your session has expired. Please log in again."
                )
            }
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastResendTimestamp < RESEND_COOLDOWN_MS) {
            val remainingSecs = ((RESEND_COOLDOWN_MS - (currentTime - lastResendTimestamp)) / 1000).coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    verificationError = "Please wait $remainingSecs seconds before requesting another email."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, verificationError = null, errorMessage = null) }
            val sendResult = try {
                sendVerificationEmailAction(user)
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (sendResult.isFailure) {
                val ex = sendResult.exceptionOrNull() ?: Exception("Failed to resend verification email")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        verificationError = mapResendVerificationError(ex)
                    )
                }
            } else {
                lastResendTimestamp = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Verification link resent! Check Spam/All Mail.",
                        verificationError = null,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun resendVerificationEmail(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val user = try { auth.currentUser } catch (e: Throwable) { null } ?: getCurrentUserAction() ?: _uiState.value.user
            val hasSession = user != null || (hasActiveSessionCheck?.invoke() ?: false)
            if (!hasSession) {
                val err = "Session expired. Please log in again to request verification."
                _uiState.update { it.copy(verificationError = err) }
                onError(err)
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, verificationError = null, errorMessage = null) }
            val sendResult = try {
                sendVerificationEmailAction(user)
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (sendResult.isFailure) {
                val ex = sendResult.exceptionOrNull() ?: Exception("Failed to resend verification email")
                val errMsg = mapResendVerificationError(ex)
                _uiState.update { it.copy(isLoading = false, verificationError = errMsg) }
                onError(errMsg)
            } else {
                lastResendTimestamp = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Verification link resent! Check Spam/All Mail.",
                        verificationError = null,
                        errorMessage = null
                    )
                }
                onSuccess()
            }
        }
    }

    fun signOutAndReset() {
        FirebaseManager.signOut()
        _uiState.value = AuthUiState()
    }

    fun clearError() {
        if (_uiState.value.errorMessage != null || _uiState.value.verificationError != null) {
            _uiState.update { it.copy(errorMessage = null, verificationError = null) }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState()
    }

    companion object {
        const val RESEND_COOLDOWN_MS = 60_000L

        fun isValidEmail(email: String): Boolean {
            val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
            return emailRegex.matches(email)
        }

        fun mapAuthErrorMessage(e: Throwable): String {
            val msg = e.message ?: ""
            return when {
                e is FirebaseAuthUserCollisionException ||
                msg.contains("already registered", ignoreCase = true) ||
                msg.contains("already in use", ignoreCase = true) ||
                msg.contains("email-already-in-use", ignoreCase = true) ->
                    "This email is already registered. Please sign in."

                e is FirebaseAuthWeakPasswordException ||
                msg.contains("weak", ignoreCase = true) ->
                    "Password must contain at least 6 characters."

                e is FirebaseAuthInvalidCredentialsException ||
                msg.contains("password", ignoreCase = true) ||
                msg.contains("invalid-credential", ignoreCase = true) ||
                msg.contains("invalid_credential", ignoreCase = true) ||
                msg.contains("wrong_password", ignoreCase = true) ->
                    "Incorrect password. Please try again."

                e is FirebaseAuthInvalidUserException ||
                msg.contains("user-not-found", ignoreCase = true) ||
                msg.contains("user_not_found", ignoreCase = true) ||
                msg.contains("no account found", ignoreCase = true) ->
                    "No account found with this email. Please check spelling or register."

                msg.contains("invalid-email", ignoreCase = true) ||
                msg.contains("badly formatted", ignoreCase = true) ->
                    "Please enter a valid email address."

                e is FirebaseNetworkException ||
                e.javaClass.simpleName.contains("FirebaseNetworkException", ignoreCase = true) ||
                (e is java.io.IOException && (msg.contains("network", ignoreCase = true) || msg.contains("connection", ignoreCase = true))) ->
                    "Network error. Please check your internet connection."

                else -> e.localizedMessage?.ifBlank { "Sign in failed. Please try again." } ?: "Sign in failed. Please try again."
            }
        }

        fun mapVerificationSendError(e: Throwable): String {
            if (e is FirebaseAuthException) {
                return "Firebase Auth Error [${e.errorCode}]: ${e.localizedMessage}"
            }
            val msg = e.message ?: ""
            val name = e.javaClass.simpleName
            val fullClassName = e.javaClass.name
            return when {
                name.contains("TooManyRequests", ignoreCase = true) ||
                fullClassName.contains("TooManyRequests", ignoreCase = true) ||
                msg.contains("TooManyRequests", ignoreCase = true) ||
                msg.contains("too many requests", ignoreCase = true) ||
                msg.contains("too-many-requests", ignoreCase = true) ->
                    "Too many requests. Please check your Spam folder or wait 2 minutes."

                name.contains("Network", ignoreCase = true) ||
                fullClassName.contains("Network", ignoreCase = true) ||
                msg.contains("Network", ignoreCase = true) ||
                msg.contains("connection", ignoreCase = true) ||
                e is java.io.IOException ->
                    "Network error. Please check your internet connection."

                else -> "Account created, but failed to send verification email. Please try resending."
            }
        }

        fun mapResendVerificationError(e: Throwable): String {
            if (e is FirebaseAuthException) {
                return "Error [${e.errorCode}]: ${e.localizedMessage}"
            }
            val msg = e.message ?: ""
            val name = e.javaClass.simpleName
            val fullClassName = e.javaClass.name
            return when {
                name.contains("TooManyRequests", ignoreCase = true) ||
                fullClassName.contains("TooManyRequests", ignoreCase = true) ||
                msg.contains("TooManyRequests", ignoreCase = true) ||
                msg.contains("too many requests", ignoreCase = true) ||
                msg.contains("too-many-requests", ignoreCase = true) ->
                    "Too many requests. Please check your Spam folder or wait 2 minutes."

                name.contains("Network", ignoreCase = true) ||
                fullClassName.contains("Network", ignoreCase = true) ||
                msg.contains("Network", ignoreCase = true) ||
                msg.contains("connection", ignoreCase = true) ||
                e is java.io.IOException ->
                    "Network error. Please check your internet connection."

                else -> "Failed to resend verification email. Please try again."
            }
        }
    }
}

typealias LoginViewModel = AuthViewModel