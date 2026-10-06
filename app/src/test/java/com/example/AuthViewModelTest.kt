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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

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
    fun `signIn with blank password sets password cannot be empty error`() {
        val viewModel = AuthViewModel { _, _ -> Result.success(null) }
        viewModel.signIn("user@test.com", "   ")

        assertEquals("Please enter your password.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `signIn with blank email sets email cannot be empty error`() {
        val viewModel = AuthViewModel { _, _ -> Result.success(null) }
        viewModel.signIn("", "validPassword123")

        assertEquals("Please enter your email address.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `signIn with invalid password credential maps to friendly error`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel { _, _ ->
            Result.failure(Exception("FirebaseAuthException: The password is invalid or user-not-found / invalid-credential"))
        }

        viewModel.signIn("user@test.com", "wrongPass")
        advanceUntilIdle()

        assertEquals("Incorrect password. Please try again.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `signIn with user-not-found maps to account not found error`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel { _, _ ->
            Result.failure(Exception("user-not-found: There is no user record corresponding to this identifier."))
        }

        viewModel.signIn("missing@test.com", "somePassword")
        advanceUntilIdle()

        assertEquals("No account found with this email. Please check spelling or register.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `signIn with generic error maps to exception localizedMessage`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel { _, _ ->
            Result.failure(Exception("Network connection timeout."))
        }

        viewModel.signIn("user@test.com", "somePassword")
        advanceUntilIdle()

        assertEquals("Network connection timeout.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `signIn success updates isSuccess and clears error`() = runTest(testDispatcher) {
        val viewModel = AuthViewModel { _, _ ->
            Result.success(null)
        }

        viewModel.signIn("user@test.com", "correctPassword")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `resetState resets state to default`() {
        val viewModel = AuthViewModel()
        viewModel.signIn("user@test.com", "")
        assertEquals("Please enter your password.", viewModel.uiState.value.errorMessage)

        viewModel.resetState()
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }
}
