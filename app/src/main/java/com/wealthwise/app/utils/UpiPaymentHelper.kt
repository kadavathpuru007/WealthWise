package com.wealthwise.app.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

/**
 * Data extracted from a scanned or parsed UPI URI.
 */
data class ParsedUpiData(
    val vpa: String,
    val name: String,
    val amount: Double?,
    val note: String? = null,
    val transactionReference: String? = null
)

/**
 * In-memory representation of an intended UPI payment request prior to intent launch.
 * Never inserted into the completed ledger until real confirmation.
 */
data class UpiPaymentRequest(
    val vpa: String,
    val merchantName: String,
    val amount: Double,
    val categoryId: Long?,
    val categoryName: String?,
    val note: String,
    val source: String = "UPI"
)

/**
 * State machine for the UPI Payment flow.
 */
sealed interface UpiPaymentState {
    data object Idle : UpiPaymentState
    data object Scanning : UpiPaymentState
    data class Scanned(val data: ParsedUpiData) : UpiPaymentState
    data object ValidatingBudget : UpiPaymentState
    data object BudgetApproved : UpiPaymentState
    data class BudgetWarning(val message: String) : UpiPaymentState
    data class BudgetBlocked(val message: String) : UpiPaymentState
    data object LaunchingUpi : UpiPaymentState
    data object AwaitingConfirmation : UpiPaymentState
    data object Completed : UpiPaymentState
    data object Cancelled : UpiPaymentState
    data class Failed(val message: String) : UpiPaymentState
}

/**
 * Result status for pre-transaction budget & spendable balance enforcement.
 */
enum class BudgetValidationStatus {
    VALID,
    WARNING,
    BLOCKED
}

/**
 * Detailed outcome of pre-transaction budget evaluation.
 */
data class UpiBudgetValidation(
    val status: BudgetValidationStatus,
    val budgetLimit: Double,
    val currentSpent: Double,
    val paymentAmount: Double,
    val projectedSpent: Double,
    val remainingBeforePayment: Double,
    val remainingAfterPayment: Double,
    val percentageAfterPayment: Int,
    val message: String
)

/**
 * Targeted external UPI applications.
 */
enum class UpiTargetApp(val title: String, val packageName: String) {
    GOOGLE_PAY("Google Pay", "com.google.android.apps.nbu.paisa.user"),
    PHONEPE("PhonePe", "com.phonepe.app"),
    PAYTM("Paytm", "net.one97.paytm"),
    GENERIC_UPI("Any UPI App", "")
}

/**
 * Parsed callback details from an external UPI payment return.
 */
data class UpiCallbackResult(
    val isSuccess: Boolean,
    val status: String, // "SUCCESS", "SUBMITTED", "FAILED", "CANCELLED"
    val txnId: String?,
    val responseCode: String?,
    val approvalRefNo: String?,
    val txnRef: String?,
    val rawResponse: String?
)

/**
 * Core UPI payment utility for WealthWise.
 * Handles QR scanning via Google Play Services Code Scanner, URI parsing, VPA validation,
 * pre-transaction budget enforcement, and safe UPI intent creation.
 */
object UpiPaymentHelper {

    private const val DEFAULT_WARNING_THRESHOLD_PERCENT = 90

    /**
     * Starts Google Play Services Code Scanner to scan a QR code.
     * Does NOT require camera permission in manifest.
     */
    fun startQrScan(
        context: Context,
        onResult: (ParsedUpiData?) -> Unit,
        onCanceled: () -> Unit = {}
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue
                    if (rawValue.isNullOrBlank()) {
                        onResult(null)
                        return@addOnSuccessListener
                    }
                    val parsed = parseUpiString(rawValue)
                    onResult(parsed)
                }
                .addOnFailureListener {
                    onResult(null)
                }
                .addOnCanceledListener {
                    onCanceled()
                }
        } catch (_: Throwable) {
            onResult(null)
        }
    }

    /**
     * Checks if the given input is a valid 10-digit Indian mobile number (optionally prefixed with +91 or 0).
     */
    fun isMobileNumber(input: String): Boolean {
        val trimmed = cleanMobileNumber(input)
        return trimmed.matches(Regex("^[6-9]\\d{9}$"))
    }

    /**
     * Strips whitespace, +91, and leading 0 from a mobile number.
     */
    fun cleanMobileNumber(input: String): String {
        return input.trim()
            .replace(" ", "")
            .replace("-", "")
            .removePrefix("+91")
            .removePrefix("0")
            .trim()
    }

    /**
     * Syntax validation for UPI Virtual Payment Address (VPA) or 10-digit mobile number.
     * Validates syntax without claiming existence of the VPA.
     */
    fun isValidVpa(vpa: String): Boolean {
        val trimmed = vpa.trim()
        if (trimmed.isEmpty() || trimmed.length > 100 || trimmed.contains(" ")) return false

        // Support direct 10-digit mobile number entry (PhonePe / Navi style)
        if (isMobileNumber(trimmed)) return true

        val parts = trimmed.split("@")
        if (parts.size != 2) return false

        val username = parts[0]
        val handle = parts[1]

        if (username.isEmpty() || handle.isEmpty() || handle.length < 2) return false

        // Username: alphanumeric, dots, dashes, underscores
        val usernamePattern = Regex("^[a-zA-Z0-9.\\-_]+$")
        // Handle: alphanumeric and dots, must not start/end with dot
        val handlePattern = Regex("^[a-zA-Z0-9]+([.][a-zA-Z0-9]+)*$")

        return usernamePattern.matches(username) && handlePattern.matches(handle)
    }

    /**
     * Parses a raw UPI string or QR content.
     * Expects scheme="upi" and authority="pay".
     */
    fun parseUpiString(rawString: String): ParsedUpiData? {
        val trimmed = rawString.trim()
        if (trimmed.isEmpty()) return null

        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Throwable) {
            return null
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "upi") return null

        val authority = uri.authority?.lowercase(Locale.ROOT)
        if (authority != "pay") return null

        val pa = uri.getQueryParameter("pa")?.trim() ?: return null
        if (!isValidVpa(pa)) return null

        val rawName = uri.getQueryParameter("pn")?.trim()
        val name = if (rawName.isNullOrBlank()) "Merchant" else rawName

        val rawAmount = uri.getQueryParameter("am")?.trim()
        val amount: Double? = if (!rawAmount.isNullOrBlank()) {
            try {
                val parsed = rawAmount.toDouble()
                if (parsed.isNaN() || parsed.isInfinite() || parsed <= 0.0) {
                    return null // Invalid/non-positive amount
                }
                parsed
            } catch (_: Throwable) {
                return null
            }
        } else {
            null
        }

        val note = uri.getQueryParameter("tn")?.trim()
        val tr = uri.getQueryParameter("tr")?.trim()

        return ParsedUpiData(
            vpa = pa,
            name = name,
            amount = amount,
            note = note,
            transactionReference = tr
        )
    }

    /**
     * Pre-transaction budget and spendable balance validator.
     * Enforces spendable cash balance and category budgets using real figures.
     * If available cash calculation evaluates to 0 due to an unset starting balance,
     * it gracefully falls back to validating against the active monthly category budget limit.
     */
    fun evaluateUpiBudgetAndBalance(
        paymentAmount: Double,
        categoryName: String,
        availableCashBalance: Double,
        budgetLimit: Double,
        currentSpent: Double,
        warningThresholdPercent: Int = DEFAULT_WARNING_THRESHOLD_PERCENT,
        isStartingBalanceUnset: Boolean = false
    ): UpiBudgetValidation {
        // 1. Validate payment amount
        if (paymentAmount <= 0.0 || paymentAmount.isNaN() || paymentAmount.isInfinite()) {
            return UpiBudgetValidation(
                status = BudgetValidationStatus.BLOCKED,
                budgetLimit = budgetLimit,
                currentSpent = currentSpent,
                paymentAmount = paymentAmount,
                projectedSpent = currentSpent,
                remainingBeforePayment = availableCashBalance,
                remainingAfterPayment = availableCashBalance,
                percentageAfterPayment = 0,
                message = "Payment amount must be strictly greater than ₹0.00."
            )
        }

        // 2. Spendable balance check
        // If starting balance is set and balance is insufficient, hard-block the transaction.
        // If available cash calculation evaluates to 0 due to an unset starting balance, gracefully fall back
        // to validating against the active monthly category budget limit rather than hard-blocking valid spending.
        if (!isStartingBalanceUnset) {
            if (availableCashBalance > 0.0 && paymentAmount > availableCashBalance) {
                val shortfall = paymentAmount - availableCashBalance
                return UpiBudgetValidation(
                    status = BudgetValidationStatus.BLOCKED,
                    budgetLimit = budgetLimit,
                    currentSpent = currentSpent,
                    paymentAmount = paymentAmount,
                    projectedSpent = currentSpent + paymentAmount,
                    remainingBeforePayment = availableCashBalance,
                    remainingAfterPayment = -shortfall,
                    percentageAfterPayment = 100,
                    message = "Insufficient available balance. You have ₹${formatInr(availableCashBalance)} available, but payment requires ₹${formatInr(paymentAmount)} (shortfall of ₹${formatInr(shortfall)})."
                )
            } else if (availableCashBalance <= 0.0) {
                val shortfall = paymentAmount
                return UpiBudgetValidation(
                    status = BudgetValidationStatus.BLOCKED,
                    budgetLimit = budgetLimit,
                    currentSpent = currentSpent,
                    paymentAmount = paymentAmount,
                    projectedSpent = currentSpent + paymentAmount,
                    remainingBeforePayment = 0.0,
                    remainingAfterPayment = -shortfall,
                    percentageAfterPayment = 100,
                    message = "Insufficient available balance. You have ₹0 available, but payment requires ₹${formatInr(paymentAmount)}."
                )
            }
        }

        // 3. Category budget check (Graceful fallback when starting balance is unset OR cash is sufficient)
        val projectedSpent = currentSpent + paymentAmount
        val remainingBefore = (budgetLimit - currentSpent).coerceAtLeast(0.0)
        val remainingAfter = budgetLimit - projectedSpent
        val percentageAfter = if (budgetLimit > 0.0) {
            ((projectedSpent / budgetLimit) * 100).toInt()
        } else {
            0
        }

        if (budgetLimit <= 0.0) {
            // No budget limit set for this category
            val msg = if (isStartingBalanceUnset) {
                "No budget limit set for $categoryName. Payment allowed (opening balance unset)."
            } else {
                "No budget configured for $categoryName. Spendable cash is sufficient."
            }
            return UpiBudgetValidation(
                status = BudgetValidationStatus.VALID,
                budgetLimit = 0.0,
                currentSpent = currentSpent,
                paymentAmount = paymentAmount,
                projectedSpent = projectedSpent,
                remainingBeforePayment = availableCashBalance,
                remainingAfterPayment = if (isStartingBalanceUnset) 0.0 else availableCashBalance - paymentAmount,
                percentageAfterPayment = 0,
                message = msg
            )
        }

        if (projectedSpent > budgetLimit) {
            val overrun = projectedSpent - budgetLimit
            return UpiBudgetValidation(
                status = BudgetValidationStatus.BLOCKED,
                budgetLimit = budgetLimit,
                currentSpent = currentSpent,
                paymentAmount = paymentAmount,
                projectedSpent = projectedSpent,
                remainingBeforePayment = remainingBefore,
                remainingAfterPayment = remainingAfter,
                percentageAfterPayment = percentageAfter,
                message = "Your budget for $categoryName is exceeded, you cannot make this transaction for ${categoryName.lowercase(Locale.ROOT)}"
            )
        }

        if (percentageAfter >= warningThresholdPercent) {
            return UpiBudgetValidation(
                status = BudgetValidationStatus.WARNING,
                budgetLimit = budgetLimit,
                currentSpent = currentSpent,
                paymentAmount = paymentAmount,
                projectedSpent = projectedSpent,
                remainingBeforePayment = remainingBefore,
                remainingAfterPayment = remainingAfter,
                percentageAfterPayment = percentageAfter,
                message = "Budget almost exhausted ($percentageAfter% used). ₹${formatInr(remainingAfter)} remaining after this payment."
            )
        }

        return UpiBudgetValidation(
            status = BudgetValidationStatus.VALID,
            budgetLimit = budgetLimit,
            currentSpent = currentSpent,
            paymentAmount = paymentAmount,
            projectedSpent = projectedSpent,
            remainingBeforePayment = remainingBefore,
            remainingAfterPayment = remainingAfter,
            percentageAfterPayment = percentageAfter,
            message = "₹${formatInr(remainingAfter)} remaining after this payment ($percentageAfter% used)."
        )
    }

    /**
     * Resolves the effective VPA for payment routing.
     * If the recipient input is an explicit VPA (containing '@'), it is preserved as entered.
     * If the recipient input is a 10-digit mobile number, formats and appends standard UPI handle mapping safely:
     * - PhonePe: @ybl
     * - Paytm: @paytm
     * - Google Pay: @okaxis
     * - Generic UPI / Other: @upi
     */
    fun resolveEffectiveVpa(vpa: String, targetApp: UpiTargetApp = UpiTargetApp.GENERIC_UPI): String {
        val trimmed = vpa.trim()
        if (trimmed.contains("@")) {
            return trimmed
        }
        if (isMobileNumber(trimmed)) {
            val clean = cleanMobileNumber(trimmed)
            return when (targetApp) {
                UpiTargetApp.PHONEPE -> "$clean@ybl"
                UpiTargetApp.PAYTM -> "$clean@paytm"
                UpiTargetApp.GOOGLE_PAY -> "$clean@okaxis"
                UpiTargetApp.GENERIC_UPI -> "$clean@upi"
            }
        }
        return trimmed
    }

    /**
     * Cryptographic fingerprint hash based on Timestamp + Amount + MerchantName.
     * Prevents duplicate payments and duplicate recording in Room & Firestore.
     */
    fun generateFingerprintHash(timestamp: Long, amount: Double, merchantName: String): String {
        val input = "${timestamp}_${String.format(Locale.US, "%.2f", amount)}_${merchantName.trim().lowercase(Locale.ROOT)}"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Resolves the recipient or merchant name from the VPA, phone number, or entered name.
     * Gives users visual clarity on the exact beneficiary before sending money.
     */
    fun resolveRecipientName(vpa: String, enteredName: String? = null): String {
        val trimmedEntered = enteredName?.trim().orEmpty()
        if (trimmedEntered.isNotBlank() && !trimmedEntered.equals("Merchant", ignoreCase = true)) {
            return trimmedEntered
        }
        val trimmedVpa = vpa.trim()
        if (isMobileNumber(trimmedVpa)) {
            val clean = cleanMobileNumber(trimmedVpa)
            return "+91 ${clean.take(5)} ${clean.drop(5)}"
        }
        val lower = trimmedVpa.lowercase(Locale.ROOT)
        when {
            lower.contains("swiggy") -> return "Swiggy"
            lower.contains("zomato") -> return "Zomato"
            lower.contains("amazon") -> return "Amazon Pay Merchant"
            lower.contains("flipkart") -> return "Flipkart"
            lower.contains("blinkit") -> return "Blinkit"
            lower.contains("zepto") -> return "Zepto"
            lower.contains("instamart") -> return "Instamart"
            lower.contains("uber") -> return "Uber India"
            lower.contains("ola") -> return "Ola Cabs"
            lower.contains("rapido") -> return "Rapido"
            lower.contains("irctc") -> return "IRCTC"
            lower.contains("airtel") -> return "Airtel Payments"
            lower.contains("jio") -> return "Reliance Jio"
            lower.contains("tataplay") || lower.contains("tatasky") -> return "Tata Play"
            lower.contains("bescom") -> return "BESCOM Electricity"
            lower.contains("netflix") -> return "Netflix"
            lower.contains("spotify") -> return "Spotify"
            lower.contains("zerodha") -> return "Zerodha Broking"
            lower.contains("groww") -> return "Groww Invest"
        }
        if (trimmedVpa.contains("@")) {
            val username = trimmedVpa.substringBefore("@")
            val cleanWords = username.replace(".", " ")
                .replace("_", " ")
                .replace("-", " ")
                .split(" ")
                .filter { it.isNotBlank() && !it.all { ch -> ch.isDigit() } }
            if (cleanWords.isNotEmpty()) {
                return cleanWords.joinToString(" ") { word ->
                    word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
            }
        }
        return if (trimmedEntered.isNotBlank()) trimmedEntered else "Verified Recipient"
    }

    /**
     * Parses the response string returned by external UPI applications via Android ActivityResult.
     * Complies with the standard NPCI UPI response format:
     * txnId=...&responseCode=...&ApprovalRefNo=...&Status=SUCCESS&txnRef=...
     */
    fun parseUpiResponse(rawResponse: String?): UpiCallbackResult {
        if (rawResponse.isNullOrBlank()) {
            return UpiCallbackResult(
                isSuccess = false,
                status = "CANCELLED",
                txnId = null,
                responseCode = null,
                approvalRefNo = null,
                txnRef = null,
                rawResponse = null
            )
        }
        val params = mutableMapOf<String, String>()
        val tokens = rawResponse.split("&")
        for (token in tokens) {
            val kv = token.split("=")
            if (kv.size == 2) {
                params[kv[0].trim().lowercase(Locale.ROOT)] = kv[1].trim()
            }
        }
        val statusVal = (params["status"] ?: params["responsecode"] ?: "").uppercase(Locale.ROOT)
        val isSuccess = statusVal == "SUCCESS" || statusVal == "00" || statusVal == "0"
        val isSubmitted = statusVal == "SUBMITTED" || statusVal == "PENDING"
        val status = when {
            isSuccess -> "SUCCESS"
            isSubmitted -> "SUBMITTED"
            statusVal == "USER_DROPPED" || statusVal == "CANCELLED" -> "CANCELLED"
            else -> "FAILED"
        }
        return UpiCallbackResult(
            isSuccess = isSuccess,
            status = status,
            txnId = params["txnid"] ?: params["approvalrefno"] ?: params["txnref"],
            responseCode = params["responsecode"],
            approvalRefNo = params["approvalrefno"],
            txnRef = params["txnref"],
            rawResponse = rawResponse
        )
    }

    /**
     * Smart auto-prediction function that inspects the entered merchant name, UPI handle,
     * and note / description (e.g. "Lunch at restaurant" -> Food, "electricity bill" -> Bills)
     * and auto-suggests the transaction category.
     */
    fun suggestCategory(merchantName: String, vpa: String, note: String = ""): String? {
        val combined = "$merchantName $vpa $note".lowercase(Locale.ROOT)
        return when {
            // Food & Dining
            listOf(
                "swiggy", "zomato", "eat", "cafe", "coffee", "tea", "chai", "restaurant", "restro",
                "food", "dine", "dining", "kitchen", "bakery", "bake", "mcdonald", "domino", "starbuck",
                "pizza", "burger", "biryani", "canteen", "sweet", "hotel", "dhaba", "kfc", "subway",
                "haldiram", "blinkit", "instamart", "zepto", "bigbasket", "bbdaily", "grofers",
                "lunch", "dinner", "breakfast", "snack", "snacks", "meal", "meals", "groceries", "grocery"
            ).any { combined.contains(it) } -> "Food"

            // Bills & Utilities
            listOf(
                "bill", "electricity", "recharge", "broadband", "bescom", "airtel", "jio", "vi",
                "vodafone", "water", "gas", "dth", "tata play", "utility", "cesc", "tneb",
                "mahavitaran", "bsnl", "adani", "piped gas", "cylinder", "indane", "bharat gas",
                "hp gas", "postpaid", "prepaid", "wifi", "power"
            ).any { combined.contains(it) } -> "Bills"

            // Shopping & Retail
            listOf(
                "amazon", "flipkart", "myntra", "meesho", "store", "retail", "mart", "bazaar",
                "shop", "mall", "dmart", "reliance", "ajio", "zara", "h&m", "nykaa", "decathlon",
                "super market", "clothing", "fashion", "electronics", "croma", "vijay sales",
                "clothes", "shoes", "dress", "purchase", "shopping"
            ).any { combined.contains(it) } -> "Shopping"

            // Travel & Transport
            listOf(
                "uber", "ola", "rapido", "metro", "irctc", "rail", "transit", "petrol", "fuel",
                "hpcl", "bpcl", "iocl", "shell", "auto", "taxi", "bus", "toll", "fastag",
                "indian oil", "indigo", "air india", "flight", "redbus", "makemytrip",
                "travel", "trip", "train", "cab"
            ).any { combined.contains(it) } -> "Transport"

            // Entertainment & OTT
            listOf(
                "netflix", "spotify", "prime", "hotstar", "youtube", "cinema", "movie", "pvr",
                "inox", "bookmyshow", "game", "gaming", "playstation", "steam", "disney", "gaana",
                "wynk", "theatre", "show", "tickets"
            ).any { combined.contains(it) } -> "Entertainment"

            // Health & Medical
            listOf(
                "hospital", "pharma", "pharmacy", "apollo", "medplus", "clinic", "health", "1mg",
                "practo", "doctor", "lab", "dental", "netmeds", "diagnostic", "chemist", "pathology",
                "medicine", "meds", "prescription"
            ).any { combined.contains(it) } -> "Health"

            // Investments & Finance
            listOf(
                "zerodha", "groww", "upstox", "angel", "mutual fund", "sip", "kite", "coin",
                "sharekhan", "nippon", "motilal", "invest", "securities", "broker", "stocks", "shares"
            ).any { combined.contains(it) } -> "Investment"

            // Personal Transfer
            listOf(
                "transfer", "family", "friend", "rent", "flat", "deposit", "self", "room", "borrow", "lend"
            ).any { combined.contains(it) } -> "Personal Transfer"

            else -> null
        }
    }

    private fun formatInr(amount: Double): String {
        val isNegative = amount < 0
        val absVal = kotlin.math.abs(amount)
        val integerPart = absVal.toLong()
        val decimalPart = kotlin.math.round((absVal - integerPart) * 100).toInt()

        val str = integerPart.toString()
        val formattedInt = if (str.length <= 3) {
            str
        } else {
            val lastThree = str.substring(str.length - 3)
            val rest = str.substring(0, str.length - 3)
            val sb = StringBuilder()
            var count = 0
            for (i in rest.length - 1 downTo 0) {
                sb.append(rest[i])
                count++
                if (count % 2 == 0 && i != 0) {
                    sb.append(',')
                }
            }
            sb.reverse().append(',').append(lastThree).toString()
        }

        val sign = if (isNegative) "-₹" else "₹"
        return if (decimalPart > 0) {
            val decStr = String.format(Locale.US, "%02d", decimalPart)
            "$sign$formattedInt.$decStr"
        } else {
            "$sign$formattedInt"
        }
    }
}
