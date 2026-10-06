package com.example

import com.example.ui.auth.AuthViewModel
import com.example.ui.navigation.NavDestinations
import com.example.ui.navigation.NavRouting
import com.example.ui.viewmodel.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationAuthTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. REGISTRATION TESTS
    // =========================================================================

    @Test
    fun `registration with valid email and password triggers registration and verification email`() = runTest(testDispatcher) {
        var registeredEmail = ""
        var sendVerificationCalled = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, email, _ ->
                registeredEmail = email
                Result.success(null)
            },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = {
                sendVerificationCalled = true
                Result.success(Unit)
            },
            reloadUserAction = { Result.success(null) },
            isEmailVerifiedCheck = { false },
            hasActiveSessionCheck = { true }
        )

        viewModel.register("  newuser@test.com  ", "validPassword123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("newuser@test.com", registeredEmail)
        assertTrue("sendVerificationEmailAction should be called", sendVerificationCalled)
        assertTrue("isEmailSent should be true", state.isEmailSent)
        assertTrue("authSuccess should be true", state.authSuccess)
        assertFalse("isLoading should be false", state.isLoading)
        assertNull("errorMessage should be null", state.errorMessage)
        assertEquals("Verification link sent to newuser@test.com.", state.infoMessage)
    }

    @Test
    fun `registration with invalid email format rejects before calling Firebase`() = runTest(testDispatcher) {
        var registerCalled = false
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ ->
                registerCalled = true
                Result.success(null)
            }
        )

        viewModel.register("invalid-email-address", "password123")
        advanceUntilIdle()

        assertFalse("Firebase registration should not be called", registerCalled)
        assertEquals("Please enter a valid email address.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `registration with password shorter than 6 characters rejects before calling Firebase`() = runTest(testDispatcher) {
        var registerCalled = false
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ ->
                registerCalled = true
                Result.success(null)
            }
        )

        viewModel.register("user@test.com", "12345")
        advanceUntilIdle()

        assertFalse("Firebase registration should not be called", registerCalled)
        assertEquals("Password must contain at least 6 characters.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `registration with duplicate email maps to friendly already registered error`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ ->
                Result.failure(Exception("FirebaseAuthUserCollisionException: The email address is already in use by another account."))
            }
        )

        viewModel.register("existing@test.com", "password123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("This email is already registered. Please sign in.", state.errorMessage)
        assertFalse(state.isEmailSent)
        assertFalse(state.authSuccess)
        assertFalse(state.isLoading)
    }

    @Test
    fun `registration with weak password exception maps to 6 characters message`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ ->
                Result.failure(Exception("FirebaseAuthWeakPasswordException: Password should be at least 6 characters, weak"))
            }
        )

        viewModel.register("test@test.com", "123456")
        advanceUntilIdle()

        assertEquals("Password must contain at least 6 characters.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `registration with Firebase failure sets user-friendly error message`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ ->
                Result.failure(Exception("Registration service temporarily unavailable."))
            }
        )

        viewModel.register("user@test.com", "password123")
        advanceUntilIdle()

        assertEquals("Registration service temporarily unavailable.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isEmailSent)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `registration succeeds but verification email fails updates state accordingly`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.failure(Exception("SMTP error")) },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.register("user@test.com", "password123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("isEmailSent should be false", state.isEmailSent)
        assertEquals(
            "Account created, but failed to send verification email. Please try resending.",
            state.errorMessage
        )
        assertFalse(state.isLoading)
    }

    @Test
    fun `registration succeeds but currentUser is null reports session could not be loaded`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.failure(Exception()) },
            hasActiveSessionCheck = { false }
        )

        viewModel.register("user@test.com", "password123")
        advanceUntilIdle()

        assertEquals(
            "Account created, but your session could not be loaded. Please try signing in again.",
            viewModel.uiState.value.errorMessage
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // =========================================================================
    // 2. VERIFICATION STATUS CHECK TESTS
    // =========================================================================

    @Test
    fun `checkEmailVerificationStatus when session is missing sets session expired error`() = runTest(testDispatcher) {
        var onVerifiedCalled = false
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.failure(Exception()) },
            hasActiveSessionCheck = { false }
        )

        viewModel.checkEmailVerificationStatus { onVerifiedCalled = true }
        advanceUntilIdle()

        assertFalse(onVerifiedCalled)
        assertEquals("Session expired. Please log in again.", viewModel.uiState.value.verificationError)
    }

    @Test
    fun `checkEmailVerificationStatus when reload succeeds and isEmailVerified true invokes callback`() = runTest(testDispatcher) {
        var onVerifiedCalled = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            isEmailVerifiedCheck = { true },
            hasActiveSessionCheck = { true }
        )

        viewModel.checkEmailVerificationStatus { onVerifiedCalled = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("onVerified callback must be called", onVerifiedCalled)
        assertTrue("isVerified must be true", state.isVerified)
        assertTrue("authSuccess must be true", state.authSuccess)
        assertNull("verificationError must be null", state.verificationError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `checkEmailVerificationStatus when reload succeeds but isEmailVerified false does not invoke callback`() = runTest(testDispatcher) {
        var onVerifiedCalled = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            isEmailVerifiedCheck = { false },
            hasActiveSessionCheck = { true }
        )

        viewModel.checkEmailVerificationStatus { onVerifiedCalled = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("onVerified callback must NOT be called", onVerifiedCalled)
        assertFalse("isVerified must be false", state.isVerified)
        assertEquals(
            "Email not yet verified. Please tap the link sent to your email.",
            state.verificationError
        )
        assertFalse(state.isLoading)
    }

    @Test
    fun `checkEmailVerificationStatus when reload fails sets network error`() = runTest(testDispatcher) {
        var onVerifiedCalled = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.failure(Exception("Network timeout")) },
            isEmailVerifiedCheck = { false },
            hasActiveSessionCheck = { true }
        )

        viewModel.checkEmailVerificationStatus { onVerifiedCalled = true }
        advanceUntilIdle()

        assertFalse(onVerifiedCalled)
        assertEquals("Network error. Could not check verification status.", viewModel.uiState.value.verificationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // =========================================================================
    // 3. RESEND VERIFICATION TESTS
    // =========================================================================

    @Test
    fun `resendVerificationEmail when session is missing sets session expired`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.failure(Exception()) },
            hasActiveSessionCheck = { false }
        )

        viewModel.resendVerificationEmail()
        advanceUntilIdle()

        assertEquals("Your session has expired. Please log in again.", viewModel.uiState.value.verificationError)
    }

    @Test
    fun `resendVerificationEmail on success sets info message and clears error`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.resendVerificationEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Verification link resent! Check Spam/All Mail.", state.infoMessage)
        assertNull(state.verificationError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `resendVerificationEmail on failure sets failure message`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.failure(Exception("Firebase error")) },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.resendVerificationEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Failed to resend verification email. Please try again.", state.verificationError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `resendVerificationEmail within cooldown enforces wait time`() = runTest(testDispatcher) {
        var sendCallCount = 0

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = {
                sendCallCount++
                Result.success(Unit)
            },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.resendVerificationEmail()
        advanceUntilIdle()
        assertEquals(1, sendCallCount)

        // Attempt second resend immediately
        viewModel.resendVerificationEmail()
        advanceUntilIdle()

        assertEquals("Should not call sendVerificationEmailAction again within cooldown", 1, sendCallCount)
        assertTrue(
            "Expected wait message in verificationError",
            viewModel.uiState.value.verificationError?.contains("Please wait") == true
        )
    }

    // =========================================================================
    // 4. LOGIN SECURITY & ERROR FLOW TESTS
    // =========================================================================

    @Test
    fun `signIn with verified user marks success and does not navigate to verification`() = runTest(testDispatcher) {
        var navigateToVerificationCalled = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            isEmailVerifiedCheck = { true },
            hasActiveSessionCheck = { true }
        )

        viewModel.signIn("user@test.com", "correctPassword", onNavigateToVerification = {
            navigateToVerificationCalled = true
        })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("isSuccess should be true", state.isSuccess)
        assertTrue("isVerified should be true", state.isVerified)
        assertFalse("onNavigateToVerification should NOT be called", navigateToVerificationCalled)
        assertNull(state.errorMessage)
    }

    @Test
    fun `signIn with unverified user blocks app access and triggers verification navigation`() = runTest(testDispatcher) {
        var navigateToVerificationCalled = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            isEmailVerifiedCheck = { false },
            hasActiveSessionCheck = { true }
        )

        viewModel.signIn("unverified@test.com", "correctPassword", onNavigateToVerification = {
            navigateToVerificationCalled = true
        })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("isSuccess must be false for unverified user", state.isSuccess)
        assertFalse("authSuccess must be false for unverified user", state.authSuccess)
        assertFalse("isVerified must be false", state.isVerified)
        assertTrue("onNavigateToVerification MUST be called", navigateToVerificationCalled)
        assertEquals("Please verify your email before continuing.", state.infoMessage)
    }

    @Test
    fun `signIn with invalid credentials maps to friendly error`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ ->
                Result.failure(Exception("FirebaseAuthInvalidCredentialsException: The password is invalid or user-not-found / invalid-credential"))
            }
        )

        viewModel.signIn("user@test.com", "wrongPassword")
        advanceUntilIdle()

        assertEquals("Incorrect password. Please try again.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `signIn with network exception maps to network error`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ ->
                Result.failure(Exception("FirebaseNetworkException: A network error has occurred."))
            }
        )

        viewModel.signIn("user@test.com", "somePassword")
        advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.errorMessage?.contains("network", ignoreCase = true) == true
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // =========================================================================
    // 5. NAVIGATION CONTRACTS TESTS
    // =========================================================================

    @Test
    fun `NavRouting registration contract routes to EMAIL_VERIFICATION`() {
        assertEquals(NavDestinations.EMAIL_VERIFICATION, NavRouting.destinationOnRegistration())
        assertEquals(AppScreen.EMAIL_VERIFICATION, NavRouting.screenOnRegistration())
    }

    @Test
    fun `NavRouting unverified login routes to EMAIL_VERIFICATION regardless of balance setup status`() {
        assertEquals(
            NavDestinations.EMAIL_VERIFICATION,
            NavRouting.destinationOnLogin(isEmailVerified = false, hasCompletedInitialBalanceSetup = false)
        )
        assertEquals(
            NavDestinations.EMAIL_VERIFICATION,
            NavRouting.destinationOnLogin(isEmailVerified = false, hasCompletedInitialBalanceSetup = true)
        )
        assertEquals(
            AppScreen.EMAIL_VERIFICATION,
            NavRouting.screenOnLogin(isEmailVerified = false, hasCompletedInitialBalanceSetup = false)
        )
        assertEquals(
            AppScreen.EMAIL_VERIFICATION,
            NavRouting.screenOnLogin(isEmailVerified = false, hasCompletedInitialBalanceSetup = true)
        )
    }

    @Test
    fun `NavRouting verified login routes to SET_BALANCE or HOME based on balance setup status`() {
        assertEquals(
            NavDestinations.SET_BALANCE,
            NavRouting.destinationOnLogin(isEmailVerified = true, hasCompletedInitialBalanceSetup = false)
        )
        assertEquals(
            NavDestinations.HOME,
            NavRouting.destinationOnLogin(isEmailVerified = true, hasCompletedInitialBalanceSetup = true)
        )
        assertEquals(
            AppScreen.ENTER_BALANCE,
            NavRouting.screenOnLogin(isEmailVerified = true, hasCompletedInitialBalanceSetup = false)
        )
        assertEquals(
            AppScreen.HOME,
            NavRouting.screenOnLogin(isEmailVerified = true, hasCompletedInitialBalanceSetup = true)
        )
    }

    @Test
    fun `NavRouting verification success routes to SET_BALANCE or HOME`() {
        assertEquals(
            NavDestinations.SET_BALANCE,
            NavRouting.destinationOnVerificationSuccess(hasCompletedInitialBalanceSetup = false)
        )
        assertEquals(
            NavDestinations.HOME,
            NavRouting.destinationOnVerificationSuccess(hasCompletedInitialBalanceSetup = true)
        )
        assertEquals(
            AppScreen.ENTER_BALANCE,
            NavRouting.screenOnVerificationSuccess(hasCompletedInitialBalanceSetup = false)
        )
        assertEquals(
            AppScreen.HOME,
            NavRouting.screenOnVerificationSuccess(hasCompletedInitialBalanceSetup = true)
        )
    }

    @Test
    fun `email validation regex works for various inputs`() {
        assertTrue(AuthViewModel.isValidEmail("user@domain.com"))
        assertTrue(AuthViewModel.isValidEmail("user.name+tag@sub.domain.org"))
        assertFalse(AuthViewModel.isValidEmail("user@domain"))
        assertFalse(AuthViewModel.isValidEmail("userdomain.com"))
        assertFalse(AuthViewModel.isValidEmail("@domain.com"))
        assertFalse(AuthViewModel.isValidEmail("   "))
    }

    @Test
    fun `sendVerificationEmail rate limiting maps to 2 minutes message`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = {
                Result.failure(Exception("FirebaseTooManyRequestsException: We have blocked all requests from this device due to unusual activity. Try again later."))
            },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.register("user@test.com", "password123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Too many requests. Please check your Spam folder or wait 2 minutes.", state.errorMessage)
    }

    @Test
    fun `sendVerificationEmail network error maps to connection message`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = {
                Result.failure(java.io.IOException("Network error"))
            },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.register("user@test.com", "password123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Network error. Please check your internet connection.", state.errorMessage)
    }

    @Test
    fun `resendVerificationEmail rate limiting maps to 2 minutes message`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = {
                Result.failure(Exception("FirebaseTooManyRequestsException: blocked requests"))
            },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.resendVerificationEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Too many requests. Please check your Spam folder or wait 2 minutes.", state.verificationError)
    }

    @Test
    fun `resendVerificationEmail network error maps to connection message`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = {
                Result.failure(java.io.IOException("A network error has occurred"))
            },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { true }
        )

        viewModel.resendVerificationEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Network error. Please check your internet connection.", state.verificationError)
    }
}
