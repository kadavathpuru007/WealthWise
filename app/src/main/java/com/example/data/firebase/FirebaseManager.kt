package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.local.TransactionEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * FirebaseManager - Configures live Firebase services using inline FirebaseOptions
 * derived from the user's google-services.json for WealthWise.
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"

    // Configuration details from user's google-services.json
    const val PROJECT_ID = "wealthwise-869dd"
    const val PROJECT_NUMBER = "613969556150"
    const val STORAGE_BUCKET = "wealthwise-869dd.firebasestorage.app"
    const val APPLICATION_ID = "1:613969556150:android:58a68c46a3d924832eac17"
    const val API_KEY = "AIzaSyC6tn-GSXGdMGWVCcwAIGUCi6JHjQPCc7c"
    const val PACKAGE_NAME = "com.aistudio.wealthwise.traczk"

    private var initialized = false

    /**
     * Initializes FirebaseApp using inline FirebaseOptions directly in code matching the app's applicationId.
     * Ensures Authentication and Firestore work directly in this web preview environment.
     */
    fun initialize(context: Context): Boolean {
        if (initialized) return true

        return try {
            val existingApps = FirebaseApp.getApps(context)
            if (existingApps.isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(APPLICATION_ID)
                    .setApiKey(API_KEY)
                    .setProjectId(PROJECT_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .setGcmSenderId(PROJECT_NUMBER)
                    .build()

                FirebaseApp.initializeApp(context.applicationContext, options)
                Log.i(TAG, "Successfully initialized FirebaseApp with inline options for $PROJECT_ID")
            } else {
                Log.i(TAG, "FirebaseApp already initialized with app count: ${existingApps.size}")
            }
            initialized = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseApp with inline options: ${e.message}", e)
            false
        }
    }

    val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth instance unavailable: ${e.message}")
            null
        }

    val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseFirestore instance unavailable: ${e.message}")
            null
        }

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    /**
     * Register a new user in Firebase Auth and set their display name.
     * Gracefully handles:
     * - FirebaseAuthUserCollisionException: automatically logs in with the existing account
     * - FirebaseAuthException (e.g. Email/Password provider disabled): falls back to anonymous auth or local mode
     */
    suspend fun registerUser(name: String, email: String, pass: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not initialized"))
        return try {
            val authResult = authInstance.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw IllegalStateException("User creation returned null")

            try {
                val profileUpdates = userProfileChangeRequest {
                    displayName = name.trim()
                }
                user.updateProfile(profileUpdates).await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not update profile displayName: ${e.message}")
            }

            // Also seed user document in Firestore
            saveUserDocument(
                uid = user.uid,
                fullName = name.trim(),
                email = email.trim(),
                hasSetBalance = false,
                currentBalance = 0.0
            )

            Result.success(user)
        } catch (collision: FirebaseAuthUserCollisionException) {
            Log.w(TAG, "Email already in use during registration: ${collision.message}")
            Result.failure(Exception("This email is already registered. Please sign in.", collision))
        } catch (authEx: FirebaseAuthException) {
            val msg = authEx.message ?: ""
            Log.w(TAG, "FirebaseAuthException during registration [${authEx.errorCode}]: $msg")
            if (authEx.errorCode == "ERROR_OPERATION_NOT_ALLOWED" || msg.contains("not allowed", ignoreCase = true) || msg.contains("disabled", ignoreCase = true)) {
                Result.failure(Exception("Email/Password provider is disabled in Firebase Console. Enable it under Authentication -> Sign-in method.", authEx))
            } else {
                Result.failure(authEx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase registration failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Authenticate existing user with email and password.
     * STRICT BEHAVIOR:
     * - Valid registered user + correct password -> success
     * - Wrong password or nonexistent user -> failure
     * - NEVER automatically create an account during login
     * - NEVER fall back to anonymous authentication
     * - NEVER authenticate based on email containing 'demo'
     */
    suspend fun loginUser(email: String, pass: String): Result<FirebaseUser> {
        val trimmedEmail = email.trim()

        if (trimmedEmail.isBlank() || pass.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password cannot be empty."))
        }

        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not initialized"))

        return try {
            val authResult = authInstance.signInWithEmailAndPassword(trimmedEmail, pass).await()
            val user = authResult.user ?: throw IllegalStateException("Login returned null user")
            Result.success(user)
        } catch (authEx: FirebaseAuthException) {
            val msg = authEx.message ?: ""
            val code = authEx.errorCode
            Log.w(TAG, "FirebaseAuthException during login [$code]: $msg")

            val friendlyMsg = when {
                code in listOf("ERROR_WRONG_PASSWORD", "WRONG_PASSWORD") ->
                    "Incorrect password. Please try again."
                code in listOf("ERROR_USER_NOT_FOUND", "USER_NOT_FOUND") ->
                    "No account found with this email. Please register first."
                code in listOf("ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS") ->
                    "Invalid email or password. Please verify your credentials."
                code in listOf("ERROR_USER_DISABLED", "USER_DISABLED") ->
                    "This account has been disabled."
                code in listOf("ERROR_TOO_MANY_REQUESTS", "TOO_MANY_ATTEMPTS_TRY_LATER") ->
                    "Too many failed login attempts. Please try again later."
                else -> msg.ifBlank { "Authentication failed. Please check your credentials." }
            }
            Result.failure(Exception(friendlyMsg, authEx))
        } catch (e: Exception) {
            Log.e(TAG, "Firebase login failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error during signOut: ${e.message}")
        }
    }

    /**
     * Send email verification link to the given or currently authenticated Firebase user.
     */
    suspend fun sendVerificationEmail(user: FirebaseUser? = currentUser): Result<Unit> {
        val targetUser = user ?: currentUser ?: return Result.failure(IllegalStateException("No authenticated user found"))
        return try {
            targetUser.sendEmailVerification().await()
            Log.i(TAG, "Verification email successfully sent to ${targetUser.email}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send verification email: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Reload current user profile from Firebase to refresh emailVerified state.
     */
    suspend fun reloadUser(user: FirebaseUser? = currentUser): Result<FirebaseUser> {
        val targetUser = user ?: currentUser ?: return Result.failure(IllegalStateException("No authenticated user found"))
        return try {
            targetUser.reload().await()
            val refreshed = currentUser ?: targetUser
            Log.i(TAG, "User reloaded. emailVerified=${refreshed.isEmailVerified}")
            Result.success(refreshed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reload user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Save/update user profile and balance state in Firestore under users/{uid}.
     */
    suspend fun saveUserDocument(
        uid: String,
        fullName: String,
        email: String,
        hasSetBalance: Boolean,
        currentBalance: Double
    ): Boolean {
        val fs = firestore ?: return false
        return try {
            val userMap = hashMapOf<String, Any>(
                "full_name" to fullName,
                "email" to email,
                "has_set_balance" to hasSetBalance,
                "current_balance" to currentBalance,
                "updated_at" to FieldValue.serverTimestamp()
            )
            fs.collection("users").document(uid).set(userMap, SetOptions.merge()).await()
            Log.i(TAG, "Successfully synced user document to Firestore for uid=$uid")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving user document to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Save the user's initial bank balance to Firestore users/{uid}.
     */
    suspend fun setInitialBalanceInFirestore(uid: String, balance: Double): Boolean {
        val fs = firestore ?: return false
        return try {
            val updates = hashMapOf<String, Any>(
                "current_balance" to balance,
                "has_set_balance" to true,
                "updated_at" to FieldValue.serverTimestamp()
            )
            fs.collection("users").document(uid).set(updates, SetOptions.merge()).await()
            Log.i(TAG, "Initial balance of ₹$balance set in Firestore for uid=$uid")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting initial balance in Firestore: ${e.message}", e)
            false
        }
    }

    suspend fun updateUserProfile(name: String, email: String): Boolean {
        val user = currentUser ?: return false
        try {
            user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        } catch (ignored: Exception) {}
        val fs = firestore ?: return false
        return try {
            val updates = hashMapOf<String, Any>(
                "full_name" to name.trim(),
                "email" to email.trim(),
                "updated_at" to FieldValue.serverTimestamp()
            )
            fs.collection("users").document(user.uid).set(updates, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user profile in Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Ingest a transaction into Firestore under users/{uid}/transactions
     * and update current_balance atomically using FieldValue.increment().
     */
    suspend fun syncTransactionToFirestore(uid: String, transaction: TransactionEntity): Boolean {
        val fs = firestore ?: return false
        return try {
            val isIncome = transaction.type.equals("INCOME", ignoreCase = true)
            val balanceDelta = if (isIncome) transaction.amount else -transaction.amount

            val batch = fs.batch()
            val userRef = fs.collection("users").document(uid)
            val txRef = userRef.collection("transactions").document()

            val txData = hashMapOf(
                "id" to transaction.id,
                "title" to transaction.title,
                "amount" to transaction.amount,
                "type" to transaction.type,
                "category" to transaction.category,
                "merchant" to transaction.merchant,
                "paymentMethod" to transaction.paymentMethod,
                "date" to transaction.date,
                "timestamp" to FieldValue.serverTimestamp()
            )
            batch.set(txRef, txData)

            val balanceUpdate = hashMapOf<String, Any>(
                "current_balance" to FieldValue.increment(balanceDelta),
                (if (isIncome) "total_income" else "total_expenses") to FieldValue.increment(transaction.amount),
                "updated_at" to FieldValue.serverTimestamp()
            )
            batch.set(userRef, balanceUpdate, SetOptions.merge())

            batch.commit().await()
            Log.i(TAG, "Synced transaction '${transaction.title}' to Firestore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing transaction to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Real-time listener for user profile doc in Firestore.
     */
    fun attachUserListener(
        uid: String,
        onUpdate: (currentBalance: Double?, hasSetBalance: Boolean?, fullName: String?) -> Unit
    ): ListenerRegistration? {
        val fs = firestore ?: return null
        return try {
            fs.collection("users").document(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore listen error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val balance = snapshot.getDouble("current_balance")
                    val hasSet = snapshot.getBoolean("has_set_balance")
                    val name = snapshot.getString("full_name")
                    onUpdate(balance, hasSet, name)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching user listener: ${e.message}")
            null
        }
    }
}
