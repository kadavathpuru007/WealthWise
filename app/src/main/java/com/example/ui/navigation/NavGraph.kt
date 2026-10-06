package com.example.ui.navigation

import com.example.ui.viewmodel.AppScreen

/**
 * Navigation routes and destinations for WealthWise.
 */
object NavDestinations {
    const val LOGIN = "LoginScreen"
    const val REGISTER = "RegisterScreen"
    const val EMAIL_VERIFICATION = "EmailVerificationScreen"
    const val ROUTE_EMAIL_VERIFICATION = "email_verification"
    const val SET_BALANCE = "SetBalanceScreen"
    const val ENTER_BALANCE = "EnterBalanceScreen"
    const val HOME = "HomeScreen"
}

/**
 * Authoritative navigation routing logic for WealthWise onboarding and authentication flows.
 * Provides defensive null-safety with default false fallbacks to prevent crash loops.
 */
object NavRouting {

    /**
     * On Registration: Route user to the Email Verification screen after verification email is sent.
     */
    fun destinationOnRegistration(): String = NavDestinations.EMAIL_VERIFICATION

    fun screenOnRegistration(): AppScreen = AppScreen.EMAIL_VERIFICATION

    /**
     * On Verification Success: Continue to Set Balance screen if initial balance is not yet set;
     * otherwise go directly to Home.
     */
    fun destinationOnVerificationSuccess(hasCompletedInitialBalanceSetup: Boolean?): String {
        return if (hasCompletedInitialBalanceSetup == true) {
            NavDestinations.HOME
        } else {
            NavDestinations.SET_BALANCE
        }
    }

    fun screenOnVerificationSuccess(hasCompletedInitialBalanceSetup: Boolean?): AppScreen {
        return if (hasCompletedInitialBalanceSetup == true) {
            AppScreen.HOME
        } else {
            AppScreen.ENTER_BALANCE
        }
    }

    /**
     * On Initial Balance Submission: Balance is persisted, onboarding completed, route to HomeScreen.
     */
    fun destinationOnInitialBalanceSubmission(): String = NavDestinations.HOME

    fun screenOnInitialBalanceSubmission(): AppScreen = AppScreen.HOME

    /**
     * Determines start destination safely with default false fallbacks.
     */
    fun getStartDestination(
        isLoggedIn: Boolean?,
        isEmailVerified: Boolean?,
        hasCompletedInitialBalanceSetup: Boolean?
    ): String {
        val loggedIn = isLoggedIn ?: false
        if (!loggedIn) return NavDestinations.LOGIN
        val verified = isEmailVerified ?: false
        if (!verified) return NavDestinations.EMAIL_VERIFICATION
        val setupDone = hasCompletedInitialBalanceSetup ?: false
        if (!setupDone) return NavDestinations.SET_BALANCE
        return NavDestinations.HOME
    }

    fun getStartScreen(
        isLoggedIn: Boolean?,
        isEmailVerified: Boolean?,
        hasCompletedInitialBalanceSetup: Boolean?
    ): AppScreen {
        val loggedIn = isLoggedIn ?: false
        if (!loggedIn) return AppScreen.LOGIN
        val verified = isEmailVerified ?: false
        if (!verified) return AppScreen.EMAIL_VERIFICATION
        val setupDone = hasCompletedInitialBalanceSetup ?: false
        if (!setupDone) return AppScreen.ENTER_BALANCE
        return AppScreen.HOME
    }

    /**
     * On Login: Check email verification first. If not verified, stay on or navigate to EMAIL_VERIFICATION.
     * If verified, check has_completed_initial_balance_setup.
     * If true: do NOT show balance setup screen; route directly to HomeScreen.
     * If false: route to SetBalanceScreen so the user can complete setup.
     */
    fun destinationOnLogin(
        isEmailVerified: Boolean?,
        hasCompletedInitialBalanceSetup: Boolean?
    ): String {
        val verified = isEmailVerified ?: false
        val setupDone = hasCompletedInitialBalanceSetup ?: false
        if (!verified) return NavDestinations.EMAIL_VERIFICATION
        return if (setupDone) {
            NavDestinations.HOME
        } else {
            NavDestinations.SET_BALANCE
        }
    }

    fun screenOnLogin(
        isEmailVerified: Boolean?,
        hasCompletedInitialBalanceSetup: Boolean?
    ): AppScreen {
        val verified = isEmailVerified ?: false
        val setupDone = hasCompletedInitialBalanceSetup ?: false
        if (!verified) return AppScreen.EMAIL_VERIFICATION
        return if (setupDone) {
            AppScreen.HOME
        } else {
            AppScreen.ENTER_BALANCE
        }
    }

    // Overloads for backward compatibility
    fun destinationOnLogin(hasCompletedInitialBalanceSetup: Boolean): String =
        destinationOnLogin(isEmailVerified = true, hasCompletedInitialBalanceSetup = hasCompletedInitialBalanceSetup)

    fun screenOnLogin(hasCompletedInitialBalanceSetup: Boolean): AppScreen =
        screenOnLogin(isEmailVerified = true, hasCompletedInitialBalanceSetup = hasCompletedInitialBalanceSetup)

    /**
     * Authoritative Navigation Gating:
     * Unauthenticated user: -> Login/Register
     * Authenticated + email unverified: -> Email Verification
     * Authenticated + verified + setup incomplete: -> Set Balance (ENTER_BALANCE)
     * Authenticated + verified + setup complete: -> Home / Authenticated destinations (cannot re-access ENTER_BALANCE)
     */
    fun gateScreen(
        targetScreen: AppScreen,
        currentUser: com.google.firebase.auth.FirebaseUser?,
        isLoggedIn: Boolean?,
        hasCompletedSetup: Boolean?
    ): AppScreen {
        val loggedIn = isLoggedIn ?: false
        val setupDone = hasCompletedSetup ?: false
        val isAuthScreen = targetScreen == AppScreen.LOGIN || targetScreen == AppScreen.REGISTER
        if (!loggedIn) {
            return if (isAuthScreen) targetScreen else AppScreen.LOGIN
        }
        val isEmailVerified = currentUser?.isEmailVerified ?: (hasCompletedSetup == true)
        if (!isEmailVerified) {
            return if (isAuthScreen || targetScreen == AppScreen.EMAIL_VERIFICATION) targetScreen else AppScreen.EMAIL_VERIFICATION
        }
        if (!setupDone) {
            return if (isAuthScreen || targetScreen == AppScreen.ENTER_BALANCE) targetScreen else AppScreen.ENTER_BALANCE
        }
        if (targetScreen == AppScreen.ENTER_BALANCE) {
            return AppScreen.HOME
        }
        return targetScreen
    }

    fun gateDestination(
        targetDestination: String,
        currentUser: com.google.firebase.auth.FirebaseUser?,
        isLoggedIn: Boolean?,
        hasCompletedSetup: Boolean?
    ): String {
        val loggedIn = isLoggedIn ?: false
        val setupDone = hasCompletedSetup ?: false
        val isAuth = targetDestination == NavDestinations.LOGIN || targetDestination == NavDestinations.REGISTER
        if (!loggedIn) {
            return if (isAuth) targetDestination else NavDestinations.LOGIN
        }
        val isEmailVerified = currentUser?.isEmailVerified ?: (hasCompletedSetup == true)
        if (!isEmailVerified) {
            return if (isAuth || targetDestination == NavDestinations.EMAIL_VERIFICATION || targetDestination == NavDestinations.ROUTE_EMAIL_VERIFICATION) {
                targetDestination
            } else {
                NavDestinations.EMAIL_VERIFICATION
            }
        }
        if (!setupDone) {
            return if (isAuth || targetDestination == NavDestinations.SET_BALANCE || targetDestination == NavDestinations.ENTER_BALANCE) {
                targetDestination
            } else {
                NavDestinations.SET_BALANCE
            }
        }
        if (targetDestination == NavDestinations.SET_BALANCE || targetDestination == NavDestinations.ENTER_BALANCE) {
            return NavDestinations.HOME
        }
        return targetDestination
    }
}
