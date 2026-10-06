package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * UserPreferencesRepository manages persistent user configuration flags and onboarding states.
 * Scoped by Firebase UID so multiple users on the same device each have their own onboarding states.
 */
class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wealthwise_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_HAS_COMPLETED_INITIAL_BALANCE_SETUP = "has_completed_initial_balance_setup"
        const val KEY_HAS_COMPLETED_SETUP = "hasCompletedSetup"
        const val KEY_IS_LOGGED_IN = "isLoggedIn"
        const val KEY_USER_NAME = "userName"
        const val KEY_USER_EMAIL = "userEmail"
        const val KEY_STARTING_BANK_BALANCE = "startingBankBalance"
    }

    private val currentUid: String
        get() = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        } catch (_: Throwable) {
            ""
        }

    fun hasCompletedInitialBalanceSetupForUser(uid: String = currentUid): Boolean {
        val key = if (uid.isNotBlank()) "has_completed_initial_balance_setup_$uid" else KEY_HAS_COMPLETED_INITIAL_BALANCE_SETUP
        return prefs.getBoolean(key, false)
    }

    fun setCompletedInitialBalanceSetupForUser(completed: Boolean, uid: String = currentUid) {
        val key = if (uid.isNotBlank()) "has_completed_initial_balance_setup_$uid" else KEY_HAS_COMPLETED_INITIAL_BALANCE_SETUP
        prefs.edit().putBoolean(key, completed).apply()
    }

    var has_completed_initial_balance_setup: Boolean
        get() = hasCompletedInitialBalanceSetupForUser()
        set(value) = setCompletedInitialBalanceSetupForUser(value)

    var hasCompletedInitialBalanceSetup: Boolean
        get() = has_completed_initial_balance_setup
        set(value) {
            has_completed_initial_balance_setup = value
        }

    var hasCompletedSetup: Boolean
        get() = has_completed_initial_balance_setup
        set(value) {
            has_completed_initial_balance_setup = value
        }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var startingBankBalance: Double
        get() = getInitialBalance(currentUid) ?: 0.0
        set(value) = setInitialBalance(value, currentUid)

    fun getInitialBalance(userId: String = currentUid): Double? {
        val key = if (userId.isNotBlank()) "startingBankBalance_$userId" else KEY_STARTING_BANK_BALANCE
        return if (prefs.contains(key)) {
            prefs.getFloat(key, 0f).toDouble()
        } else {
            null
        }
    }

    fun getInitialBalanceFlow(userId: String = currentUid): Flow<Double> = callbackFlow {
        val balKey = if (userId.isNotBlank()) "startingBankBalance_$userId" else KEY_STARTING_BANK_BALANCE
        trySend(getInitialBalance(userId) ?: 0.0)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == balKey) {
                trySend(getInitialBalance(userId) ?: 0.0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val userPreferencesFlow: Flow<UserPreferences> = callbackFlow {
        fun emitPrefs() {
            trySend(
                UserPreferences(
                    initialBalance = getInitialBalance(currentUid) ?: 0.0,
                    isOnboardingCompleted = hasCompletedInitialBalanceSetupForUser(currentUid)
                )
            )
        }
        emitPrefs()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            emitPrefs()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun setInitialBalance(balance: Double, userId: String = currentUid) {
        val balKey = if (userId.isNotBlank()) "startingBankBalance_$userId" else KEY_STARTING_BANK_BALANCE
        val setupKey = if (userId.isNotBlank()) "has_completed_initial_balance_setup_$userId" else KEY_HAS_COMPLETED_INITIAL_BALANCE_SETUP
        prefs.edit()
            .putFloat(balKey, balance.toFloat())
            .putBoolean(setupKey, true)
            .apply()
    }

    fun setOnboardingCompleted(completed: Boolean, userId: String = currentUid) {
        setCompletedInitialBalanceSetupForUser(completed, userId)
    }

    fun clear(uid: String = currentUid) {
        val editor = prefs.edit()
        if (uid.isNotBlank()) {
            editor.remove("has_completed_initial_balance_setup_$uid")
                .remove("startingBankBalance_$uid")
        } else {
            editor.remove(KEY_HAS_COMPLETED_INITIAL_BALANCE_SETUP)
                .remove(KEY_STARTING_BANK_BALANCE)
        }
        editor.putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }
}

data class UserPreferences(
    val initialBalance: Double? = 0.0,
    val isOnboardingCompleted: Boolean = true
)

