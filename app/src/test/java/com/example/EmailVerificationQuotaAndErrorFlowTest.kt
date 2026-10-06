package com.example

import com.example.ui.auth.AuthViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationQuotaAndErrorFlowTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkVerificationStatus when user is email verified invokes onVerified callback`() = runTest(testDispatcher) {
        var verifiedInvoked = false
        var notVerifiedInvoked = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            isEmailVerifiedCheck = { true },
            hasActiveSessionCheck = { true }
        )

        viewModel.checkVerificationStatus(
            onVerified = { verifiedInvoked = true },
            onNotVerified = { notVerifiedInvoked = true }
        )
        advanceUntilIdle()

        assertTrue("onVerified must be invoked when user is verified", verifiedInvoked)
        assertFalse("onNotVerified must NOT be invoked when user is verified", notVerifiedInvoked)
        assertTrue(viewModel.uiState.value.isVerified)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `checkVerificationStatus when user is not verified invokes onNotVerified callback`() = runTest(testDispatcher) {
        var verifiedInvoked = false
        var notVerifiedInvoked = false

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            isEmailVerifiedCheck = { false },
            hasActiveSessionCheck = { true }
        )

        viewModel.checkVerificationStatus(
            onVerified = { verifiedInvoked = true },
            onNotVerified = { notVerifiedInvoked = true }
        )
        advanceUntilIdle()

        assertFalse("onVerified must NOT be invoked when user is not verified", verifiedInvoked)
        assertTrue("onNotVerified must be invoked when user is not verified", notVerifiedInvoked)
        assertFalse(viewModel.uiState.value.isVerified)
        assertEquals("Email not yet verified. Please tap the link sent to your email.", viewModel.uiState.value.verificationError)
    }

    @Test
    fun `resendVerificationEmail callback version when session is missing invokes onError`() = runTest(testDispatcher) {
        var successInvoked = false
        var capturedError = ""

        val viewModel = AuthViewModel(
            authenticateUser = { _, _ -> Result.success(null) },
            registerUserAction = { _, _, _ -> Result.success(null) },
            getCurrentUserAction = { null },
            sendVerificationEmailAction = { Result.success(Unit) },
            reloadUserAction = { Result.success(null) },
            hasActiveSessionCheck = { false }
        )

        viewModel.resendVerificationEmail(
            onSuccess = { successInvoked = true },
            onError = { capturedError = it }
        )
        advanceUntilIdle()

        assertFalse(successInvoked)
        assertEquals("Session expired. Please log in again to request verification.", capturedError)
        assertEquals("Session expired. Please log in again to request verification.", viewModel.uiState.value.verificationError)
    }

    @Test
    fun `bypassVerificationForDebug sets verification state and calls onProceed`() {
        var proceedCalled = false
        val viewModel = AuthViewModel()

        viewModel.bypassVerificationForDebug {
            proceedCalled = true
        }

        assertTrue("onProceed must be called", proceedCalled)
        assertTrue(viewModel.uiState.value.isVerified)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertTrue(viewModel.uiState.value.authSuccess)
    }

    @Test
    fun `registerUser without Firebase runtime context triggers onError with unexpected error`() = runTest(testDispatcher) {
        var verificationSent = false
        var capturedError = ""

        val viewModel = AuthViewModel()

        viewModel.registerUser(
            email = "test@example.com",
            password = "password123",
            onVerificationSent = { verificationSent = true },
            onError = { capturedError = it }
        )
        advanceUntilIdle()

        assertFalse("onVerificationSent must not be called", verificationSent)
        assertTrue("Expected captured error to be non-empty", capturedError.isNotBlank())
        assertTrue("Expected captured error to indicate failure", capturedError.startsWith("Unexpected Error:"))
    }
}
