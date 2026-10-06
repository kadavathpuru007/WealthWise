package com.example.data.parser

import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialStatus
import java.util.Locale
import java.util.regex.Pattern

enum class ParserConfidence {
    HIGH_CONFIDENCE,
    MEDIUM_CONFIDENCE,
    REJECTED
}

enum class TransactionDirection {
    DEBIT,
    CREDIT,
    INFLOW,
    OUTFLOW,
    TRANSFER,
    NEITHER;

    val isInflow: Boolean
        get() = this == CREDIT || this == INFLOW

    val isOutflow: Boolean
        get() = this == DEBIT || this == OUTFLOW
}

enum class RejectionReason {
    NONE,
    PROMOTIONAL,
    BALANCE_ONLY,
    LOAN_OFFER,
    CREDIT_LIMIT,
    DUE_REMINDER,
    PAYMENT_INITIATION,
    FAILED_TRANSACTION,
    INSUFFICIENT_TRANSACTION_EVIDENCE,
    UNRELATED_AMOUNT,
    INVALID_AMOUNT,
    NO_AUTHENTICATED_USER,
    DUPLICATE,
    UNKNOWN
}

data class ParsedTransactionResult(
    val title: String,
    val merchant: String? = null,
    val amount: Double,
    val category: String,
    val paymentMethod: String,
    val isAutoDetected: Boolean,
    val rawText: String,
    val confidence: ParserConfidence = ParserConfidence.MEDIUM_CONFIDENCE,
    val referenceId: String = "",
    val direction: TransactionDirection = TransactionDirection.NEITHER,
    val transactionType: FinancialEventType = FinancialEventType.EXPENSE,
    val status: FinancialStatus = FinancialStatus.COMPLETED,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String = "",
    val accountHint: String = "",
    val balanceAfter: Double? = null,
    val rejectionReason: RejectionReason = RejectionReason.NONE,
    val source: String = "SMS",
    val sender: String = "",
    val fingerprint: String = ""
) {
    val isInflow: Boolean
        get() = direction.isInflow || transactionType == FinancialEventType.INCOME || transactionType == FinancialEventType.REFUND || transactionType == FinancialEventType.REVERSAL

    val isOutflow: Boolean
        get() = direction.isOutflow || transactionType == FinancialEventType.EXPENSE || transactionType == FinancialEventType.SUBSCRIPTION_PAYMENT || transactionType == FinancialEventType.LOAN_PAYMENT

    val type: String
        get() = if (direction == TransactionDirection.CREDIT || isInflow) "credit" else "debit"

    val transactionTypeString: String
        get() = type

    val ledgerAmount: Double
        get() = when {
            direction == TransactionDirection.TRANSFER || transactionType == FinancialEventType.TRANSFER -> 0.0
            isInflow -> kotlin.math.abs(amount)
            isOutflow -> -kotlin.math.abs(amount)
            else -> kotlin.math.abs(amount)
        }

    val signedAmount: Double
        get() = ledgerAmount
}

typealias ParsedTransaction = ParsedTransactionResult

data class NotificationTransactionSource(
    val packageName: String,
    val title: String,
    val body: String,
    val subText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val key: String? = null
)

data class SmsTransactionSource(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

object TransactionParser {

    private val merchantCategoryMap = mapOf(
        // Food & Dining
        "swiggy instamart" to Pair("Swiggy Instamart", "Groceries"),
        "swiggy" to Pair("Swiggy", "Food"),
        "zomato" to Pair("Zomato", "Food"),
        "mcdonald" to Pair("McDonald's", "Food"),
        "starbucks" to Pair("Starbucks", "Food"),
        "domino" to Pair("Domino's Pizza", "Food"),
        "kfc" to Pair("KFC", "Food"),
        "cafe coffee day" to Pair("Cafe Coffee Day", "Food"),

        // Groceries & Retail
        "blinkit" to Pair("Blinkit", "Groceries"),
        "zepto" to Pair("Zepto", "Groceries"),
        "bigbasket" to Pair("BigBasket", "Groceries"),
        "dmart" to Pair("DMart", "Groceries"),
        "bbdaily" to Pair("BBDaily", "Groceries"),
        "reliance retail" to Pair("RELIANCE RETAIL", "Shopping"),
        "apollo pharmacy" to Pair("APOLLO PHARMACY", "Health"),
        "apollo" to Pair("Apollo Pharmacy", "Health"),

        // Transport
        "uber" to Pair("Uber", "Transport"),
        "ola cabs" to Pair("Ola Cabs", "Transport"),
        "ola" to Pair("Ola Cabs", "Transport"),
        "rapido" to Pair("Rapido", "Transport"),
        "namma yatri" to Pair("Namma Yatri", "Transport"),
        "irctc" to Pair("IRCTC Railway", "Transport"),
        "indigo" to Pair("IndiGo Airlines", "Transport"),
        "metro" to Pair("Metro Rail", "Transport"),
        "petrol" to Pair("HP / IndianOil Fuel", "Transport"),
        "fuel" to Pair("Fuel Station", "Transport"),

        // Shopping
        "amazon india" to Pair("Amazon India", "Shopping"),
        "amazon" to Pair("Amazon India", "Shopping"),
        "flipkart internet" to Pair("Flipkart Internet Pvt Ltd", "Shopping"),
        "flipkart" to Pair("Flipkart", "Shopping"),
        "myntra" to Pair("Myntra", "Shopping"),
        "ajio" to Pair("Ajio Fashion", "Shopping"),
        "croma" to Pair("Croma Retail", "Shopping"),
        "zara" to Pair("Zara Retail", "Shopping"),
        "ikea" to Pair("IKEA India", "Shopping"),

        // Subscriptions
        "netflix" to Pair("Netflix", "Subscription"),
        "spotify" to Pair("Spotify Premium", "Subscription"),
        "prime" to Pair("Amazon Prime", "Subscription"),
        "youtube" to Pair("YouTube Premium", "Subscription"),
        "cult.fit" to Pair("Cult.fit Gym & Fitness", "Subscription"),
        "hotstar" to Pair("Disney+ Hotstar", "Subscription"),
        "apple.com" to Pair("Apple Services", "Subscription"),

        // Bills & Utilities
        "bescom" to Pair("BESCOM", "Bills"),
        "electricity" to Pair("Electricity Board", "Bills"),
        "jio" to Pair("Jio Fiber Gigafiber", "Bills"),
        "airtel broadband" to Pair("Airtel Broadband", "Bills"),
        "airtel" to Pair("Airtel", "Bills"),
        "broadband" to Pair("Broadband Internet", "Bills"),
        "hdfc credit card" to Pair("HDFC Millennia Credit Card", "Bills"),
        "credit card" to Pair("Credit Card Payment", "Bills"),
        "water" to Pair("Water Supply Utility", "Bills"),
        "gas" to Pair("Indane Gas Utility", "Bills")
    )

    private val ignoredMerchantWords = setOf(
        "your", "your account", "your a/c", "account", "a/c", "acct", "bank", "payment",
        "upi", "card", "credit card", "debit card", "customer", "user", "phonepe", "gpay",
        "paytm", "rs", "inr", "self", "own account", "savings account", "current account"
    )

    private const val AMOUNT_REGEX = """([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""

    fun parse(
        text: String,
        title: String = "",
        sourcePackage: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): ParsedTransactionResult {
        return try {
            val result = parseInternal(text, title, sourcePackage, timestamp)
            if (result.confidence == ParserConfidence.REJECTED || result.amount <= 0.0) {
                try {
                    android.util.Log.i("TransactionParser", "Unmatched notification string: '$text' (reason: ${result.rejectionReason})")
                } catch (_: Throwable) {}
            }
            result
        } catch (e: Throwable) {
            try {
                android.util.Log.e("TransactionParser", "Error parsing notification string: '$text'", e)
            } catch (_: Throwable) {}
            rejectedResult(
                rawText = text,
                reason = RejectionReason.UNKNOWN,
                title = "Parsing Error",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }
    }

    private fun parseInternal(
        text: String,
        title: String = "",
        sourcePackage: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): ParsedTransactionResult {
        val combinedText = if (title.isNotBlank()) "$title $text".trim() else text.trim()
        val normalized = combinedText.lowercase(Locale.ROOT)
            .replace("\u00A0", " ")
            .replace("\u200B", "")
            .replace(Regex("\\s+"), " ")

        if (normalized.isBlank()) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.UNKNOWN,
                title = "No Content",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }

        // -------------------------------------------------------------
        // PRE-EXTRACTION: EXTRACT BALANCE AFTER (Informational only)
        // -------------------------------------------------------------
        val balancePatterns = listOf(
            Pattern.compile("""(?:available\s+account\s+balance|available\s+balance|account\s+balance|effective\s+balance|bal\s+in\s+a/c)[^:]*?(?:is|as\s+of\s+[^:]*is|as\s+on\s+[^:]*is)?\s*(?:Rs\.?|INR|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:avl\s+bal(?:ance)?|avail\s+bal(?:ance)?|bal(?:\s*[:=])|bal\s+rs\.?|updated\s+bal(?:ance)?|current\s+balance|closing\s+balance)\s*[:=]?\s*(?:Rs\.?|INR|₹)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:is\s+your\s+available\s+balance)""", Pattern.CASE_INSENSITIVE)
        )
        var detectedBalance: Double? = null
        var balRange: IntRange? = null
        for (bPat in balancePatterns) {
            val bMatcher = bPat.matcher(combinedText)
            if (bMatcher.find()) {
                val bStr = bMatcher.group(1)?.replace(",", "") ?: ""
                detectedBalance = bStr.toDoubleOrNull()
                balRange = bMatcher.start()..bMatcher.end()
                break
            }
        }

        // -------------------------------------------------------------
        // 1. HARD REJECTION CHECK: OTP / Security / Auth messages
        // -------------------------------------------------------------
        val isOtp = Regex("""\b(?:otp|one\s*time\s*password|verification\s*code|secret\s*code|login\s*code)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val hasConfirmedDebitedOrCredited = Regex("""\b(?:debited\s+by|debited\s+with|debited\s+for|credited\s+with|credited\s+by|payment\s+of\s+.*successful|payment\s+successful)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (isOtp && !hasConfirmedDebitedOrCredited) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.PAYMENT_INITIATION,
                title = "Authentication OTP (Ignored)",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }

        // Collect request / Payment request approval
        val isCollectRequest = Regex("""\b(?:collect\s+request|requested\s+payment|approve\s+collect)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (isCollectRequest) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.PAYMENT_INITIATION,
                title = "Collect Request (Ignored)",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }

        // -------------------------------------------------------------
        // 2. STATUS DETECTION: Failed / Declined / Pending / Reversed / Refunded
        // -------------------------------------------------------------
        val isFailed = Regex("""\b(?:failed|declined|unsuccessful|could\s+not\s+be\s+processed|recharge\s+failed|payment\s+failed|transaction\s+failed)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val isPending = Regex("""\b(?:is\s+pending|pending\s+approval|payment\s+initiated|txn\s+initiated|in\s+progress|processing)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val isReversed = Regex("""\b(?:reversed|reversal|transaction\s+reversed|payment\s+reversed|has\s+been\s+reversed)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val isRefund = Regex("""\b(?:refund\s+credited|refund\s+of|refunded|refund\s+received)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)

        if (isFailed) {
            return ParsedTransactionResult(
                title = "Failed Transaction",
                merchant = null,
                amount = 0.0,
                category = "Other",
                paymentMethod = detectPaymentMethod(normalized),
                isAutoDetected = false,
                rawText = text,
                confidence = ParserConfidence.REJECTED,
                referenceId = extractReferenceId(combinedText),
                direction = TransactionDirection.NEITHER,
                transactionType = FinancialEventType.EXPENSE,
                status = FinancialStatus.FAILED,
                timestamp = timestamp,
                sourceApp = sourcePackage,
                rejectionReason = RejectionReason.FAILED_TRANSACTION
            )
        }

        // -------------------------------------------------------------
        // 3. HARD REJECTION: Loan Eligibility / Offers (e.g. ₹5,00,000 eligible)
        // -------------------------------------------------------------
        val isLoanOffer = Regex("""\b(?:eligible\s+for\s+(?:a\s+)?loan|eligible\s+loan\s+amount|pre-?approved\s+loan|personal\s+loan|loan\s+offer|loan\s+up\s+to|loan\s+of\s+up\s+to|instant\s+loan|apply\s+for\s+loan|maximum\s+loan\s+amount)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val isLoanDisbursed = Regex("""\b(?:loan\s+(?:amount\s+)?.*credited|credited\s+.*loan|loan\s+disbursed)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val isEmiDebit = Regex("""\b(?:debited|deducted|paid|successful).*?\b(?:emi|nach|ecs|ach|loan\s+installment|loan\s+repayment|towards\s+.*loan)\b|\b(?:emi|nach|ecs|ach|loan\s+installment|loan\s+repayment).*?\b(?:debited|deducted|paid|successful)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (isLoanOffer && !isLoanDisbursed && !isEmiDebit) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.LOAN_OFFER,
                title = "Loan Offer (Ignored)",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }

        // -------------------------------------------------------------
        // 4. HARD REJECTION: Credit Limit / Limit Increase / ATM Limit
        // -------------------------------------------------------------
        val isLimitMessage = Regex("""\b(?:credit\s+limit|limit\s+increased|card\s+limit|available\s+credit\s+limit|remaining\s+credit\s+card\s+limit|remaining\s+credit\s+limit|spending\s+limit|cash\s+limit|atm\s+withdrawal\s+limit|withdrawal\s+limit)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val hasActualDebitCredit = Regex("""\b(?:debited|credited|spent|used\s+for|cash\s+withdrawal\s+of)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (isLimitMessage && !hasActualDebitCredit) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.CREDIT_LIMIT,
                title = "Limit Notice (Ignored)",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }

        // -------------------------------------------------------------
        // 5. HARD REJECTION: Promotional / Advertisement Messages (Checked before Due/Reminder)
        // -------------------------------------------------------------
        val isPromotionalKeyword = Regex("""\b(?:special\s+offer|offer|offers|discount|cashback\s+offer|up\s+to\s+(?:rs\.?|inr|₹)?\s*\d+|get\s+(?:upto|up\s+to|flat|a)?\s*(?:rs\.?|inr|₹)?\s*\d+|recharge\s+for\s+(?:only\s+)?(?:rs\.?|inr|₹)?\s*\d+|recharge\s+(?:now\s+)?for\s+(?:only\s+)?(?:rs\.?|inr|₹)?\s*\d+|win\b|reward|coupon|voucher|promo(?:tion)?|sale\b|products\s+starting\s+at|starting\s+at\s+(?:rs\.?|inr|₹)?\s*\d+|avail\s+(?:now|this|offer)|hurry|limited\s+time|limited\s+period|shop\s+now|apply\s+now|claim\s+(?:now|your)?|exclusive\s+offer|pay\s+(?:rs\.?|inr|₹)?\s*\d+\s+and\s+(?:get|receive|win)|pre-?approved|bonus|scratch\s+card\s+reward|reward\s+points|plan\s+available|special\s+price)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val hasCompletedPaymentProof = Regex("""\b(?:payment\s+of\s+.*successful|payment\s+successful|paid\s+.*successfully|paid\s+(?:rs\.?|inr|₹)?\s*\d+|debited\s+from|debited\s+by|debited\s+on|spent\s+on|recharge\s+of\s+.*successful|renewed\s+for\s+.*successfully|refund\s+credited|cashback\s+of\s+(?:rs\.?|inr|₹)?\s*\d+\s+credited|cashback\s+credited|loan\s+amount\s+credited)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (isPromotionalKeyword && !hasCompletedPaymentProof) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.PROMOTIONAL,
                title = "Promotional Offer (Ignored)",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }

        // -------------------------------------------------------------
        // 6. HARD REJECTION: Due / Upcoming Bill / Payment Reminders
        // -------------------------------------------------------------
        val isDueOrReminder = Regex("""\b(?:payment\s+due|amount\s+due|bill\s+due|is\s+due|due\s+tomorrow|due\s+on|due\s+by|due\s+date|payment\s+reminder|reminder\s*:|upcoming\s+payment|bill\s+generated|bill\s+amount|minimum\s+due|total\s+amount\s+due|amount\s+payable|pay\s+before|pay\s+now|renew\s+now|will\s+renew|renewal\s+tomorrow)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val hasExplicitPastSuccess = Regex("""\b(?:paid\s+successfully|payment\s+successful|debited\s+successfully|successfully\s+debited|has\s+been\s+paid|renewed\s+for\s+.*successfully|recharge\s+of\s+.*successful)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (isDueOrReminder && !hasExplicitPastSuccess) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.DUE_REMINDER,
                title = "Upcoming Bill Reminder (Ignored)",
                timestamp = timestamp,
                sourcePackage = sourcePackage
            )
        }

        // -------------------------------------------------------------
        // 7. HARD REJECTION: Balance-Only Messages (e.g. "Your account balance is ₹12,450")
        // -------------------------------------------------------------
        val hasBalanceKeywords = Regex("""\b(?:available\s+account\s+balance|available\s+balance|account\s+balance|current\s+balance|closing\s+balance|ledger\s+balance|outstanding\s+balance|remaining\s+balance|effective\s+balance|available\s+credit|avl\s+bal|bal\s*[:=]|balance\s+is|bal\s+in\s+a/c)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val hasMoneyMovementVerbs = Regex("""\b(?:debited|debit|deducted|credited|credit|paid|spent|sent|received|transferred|withdrawn|deposit(?:ed)?|refund(?:ed)?|cashback\s+credited|cashback\s+of|salary|loan\s+amount\s+credited|payment\s+successful|txn\s+successful|bill\s+payment|purchase\s+at)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (hasBalanceKeywords && !hasMoneyMovementVerbs) {
            return rejectedResult(
                rawText = text,
                reason = RejectionReason.BALANCE_ONLY,
                title = "Balance Inquiry (Ignored)",
                timestamp = timestamp,
                sourcePackage = sourcePackage,
                balanceAfter = detectedBalance
            )
        }

        // -------------------------------------------------------------
        // 8. DIRECTION DETERMINATION FIRST & AMOUNT EXTRACTION
        // -------------------------------------------------------------
        var detectedAmount = 0.0
        var detectedDirection = TransactionDirection.NEITHER

        val isAtm = Regex("""\b(?:atm|cash\s+wdl|atm\s+wdl|atm\s+cash|self\s+withdrawal)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)

        // Check explicit self-transfer
        val isSelfTransfer = Regex("""\b(?:transferred\s+from\s+.*to\s+your\s+own|self\s+transfer|transferred\s+to\s+your\s+own)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)

        // Check if user's account is explicitly credited or debited
        val isUserAccountDebited = Regex(
            """\b(?:(?:your\s+)?(?:a/c|account|acct)\s+(?:no\.?\s*)?(?:[xX*]*\d+)?\s*(?:is|has\s+been|was)?\s*debited|debited\s+(?:from|to)\s+(?:your\s+)?(?:a/c|account|acct)|amount\s+debited|debited\s+amount)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(normalized)

        val isUserAccountCredited = Regex(
            """\b(?:(?:your\s+)?(?:a/c|account|acct)\s+(?:no\.?\s*)?(?:[xX*]*\d+)?\s*(?:is|has\s+been|was)?\s*credited|credited\s+(?:to|in|into)\s+(?:your\s+)?(?:a/c|account|acct)|amount\s+credited|credited\s+amount)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(normalized)
        
        val creditedAndAccountNear = Regex("""(?:credited.{0,30}account|account.{0,30}credited)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val debitedAndAccountNear = Regex("""(?:debited.{0,30}account|account.{0,30}debited)""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)

        // Semantic INFLOW indicators
        // Matchers: "credited", "received", "deposited", "added to", "refund", etc.
        val hasInflowKeywords = Regex(
            """\b(?:money\s+received|payment\s+received|amount\s+received|received\s+payment|received\s+from|received|credited\s+with|credited\s+by|credited|credit\s+of|amount\s+credited|deposited\s+in|deposited|cash\s+deposit|added\s+to|refund(?:ed|\s+credited|\s+received|\s+of)?|cashback|salary|sent\s+you|transferred\s+to\s+you|transferred\s+to\s+your\s+a(?:ccoun)?t)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(normalized)

        // Semantic OUTFLOW indicators
        // Matchers: "paid", "debited", "sent", "transferred to", "spent", "withdrawn", "emi", "nach", etc.
        val hasOutflowKeywords = Regex(
            """\b(?:debited\s+for|debited\s+by|debited|debit\s+of|amount\s+debited|amount\s+deducted|deducted\s+from|deducted|paid\s+to|paid\s+for|paid|you\s+paid|payment\s+to|payment\s+made|sent\s+to|sent|you\s+sent|transferred\s+to|transferred|transfer|spent\s+on|spent|withdrawn|cash\s+withdrawal|purchase\s+at|purchase\s+successful|bill\s+payment|recharge\s+of|recharge\s+successful|emi\s+of|loan\s+emi|nach\s+debit|ecs\s+debit|ach\s+debit|auto\s+debit|mandate|loan\s+installment|loan\s+repayment|towards\s+.*loan|towards\s+emi)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(normalized)

        val strictOutflow = Regex("""\b(?:debited|spent|withdrawn|sent\s+to|paid\s+to|deducted|transferred\s+to)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val strictInflow = Regex("""\b(?:credited|received|added|deposited|refunded)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)

        // Determine transaction direction FIRST based on semantic domain rules
        detectedDirection = when {
            isSelfTransfer -> TransactionDirection.TRANSFER
            isReversed || isRefund -> TransactionDirection.CREDIT
            isAtm -> TransactionDirection.DEBIT
            isUserAccountDebited && !isUserAccountCredited -> TransactionDirection.DEBIT
            isUserAccountCredited && !isUserAccountDebited -> TransactionDirection.CREDIT
            debitedAndAccountNear && !creditedAndAccountNear -> TransactionDirection.DEBIT
            creditedAndAccountNear && !debitedAndAccountNear -> TransactionDirection.CREDIT
            strictOutflow && !strictInflow -> TransactionDirection.DEBIT
            strictInflow && !strictOutflow -> TransactionDirection.CREDIT
            strictOutflow && strictInflow -> {
                if (Regex("""\b(?:debited|spent|withdrawn|sent\s+to|paid\s+to|deducted)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) {
                    TransactionDirection.DEBIT
                } else {
                    TransactionDirection.CREDIT
                }
            }
            Regex("""\b(?:payment\s+received|received\s+payment|money\s+received|sent\s+you|transferred\s+to\s+you)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> TransactionDirection.CREDIT
            hasInflowKeywords && !hasOutflowKeywords -> TransactionDirection.CREDIT
            hasOutflowKeywords && !hasInflowKeywords -> TransactionDirection.DEBIT
            hasInflowKeywords && hasOutflowKeywords -> {
                if (Regex("""\b(?:received|credited|deposit|cashback|refund|salary)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) &&
                    !Regex("""\b(?:paid\s+to|sent\s+to|debited\s+from|spent\s+on)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) {
                    TransactionDirection.CREDIT
                } else if (Regex("""\b(?:paid\s+to|sent\s+to|debited\s+from|spent\s+on|debited|spent|withdrawn)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) {
                    TransactionDirection.DEBIT
                } else {
                    TransactionDirection.DEBIT
                }
            }
            Regex("""\b(?:payment\s+successful|txn\s+successful|upi\s+payment\s+successful)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> TransactionDirection.DEBIT
            else -> TransactionDirection.NEITHER
        }

        // Action-bound credit amount patterns (Inflow)
        // Explicit support for:
        // - "Received ₹[amount] from [sender]"
        // - "credited with Rs [amount]"
        // - "₹[amount] deposited in your a/c"
        // - "added to"
        val creditAmountPatterns = listOf(
            Pattern.compile("""(?:credited(?:\s+(?:by|with|for|to))?|credit\s+of)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:credited|received|deposited|added).*?(?:rs\.?|inr|₹)\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:rs\.?|inr|₹)\s*$AMOUNT_REGEX.*?(?:credited|deposited)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:received(?:\s+from)?|deposited)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:received)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX\s+from""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:added\s+to(?:\s+your\s+(?:a/c|account|wallet))?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:refund(?:\s+of)?|refunded)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:cashback(?:\s+of)?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX\s*(?:credited)?""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:loan(?:\s+amount)?(?:\s+of)?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX\s*(?:credited|disbursed)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:salary(?:\s+of)?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:reversed(?:\s+to)?|reversal\s+of)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:sent\s+you)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+(?:credited|deposited|refunded|received)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+deposited(?:\s+in\s+your\s+(?:a/c|account))?""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+added\s+to""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+refund\s+credited""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+cashback\s+credited""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+has\s+been\s+reversed""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+received\s+from""", Pattern.CASE_INSENSITIVE)
        )

        // Action-bound debit amount patterns (Outflow)
        // Explicit support for:
        // - "Paid ₹[amount] to [vendor]" / "You paid ₹[amount] to [vendor]"
        // - "debited for Rs [amount]" / "debited by Rs [amount]"
        // - "Sent ₹[amount] to [vendor]"
        // - "Transferred ₹[amount] to [vendor]"
        val debitAmountPatterns = listOf(
            Pattern.compile("""(?:debited(?:\s+(?:by|with|for|from|on))?|debit\s+of)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:debited|spent|paid|withdrawn|transferred|deducted).*?(?:rs\.?|inr|₹)\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:rs\.?|inr|₹)\s*$AMOUNT_REGEX.*?(?:debited|spent|withdrawn|at\s+atm|cash\s+wdl)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:(?:you\s+)?paid(?:\s+to|\s+for)?|payment\s+of)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:spent(?:\s+on)?|txn\s+of|transaction\s+of|used\s+for)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:transfer(?:red)?(?:\s+(?:of|to))?|sent(?:\s+to)?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:cash\s+withdrawal\s+of|withdrawn)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:(?:rs\.?|inr|₹)\s*$AMOUNT_REGEX.*?debited.*?(?:towards\s+)?(?:emi|nach|ecs|ach|loan))""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:debited.*?(?:towards\s+)?(?:emi|nach|ecs|ach|loan).*?(?:rs\.?|inr|₹)\s*$AMOUNT_REGEX)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:(?:emi|nach|ecs|ach|loan\s+installment|loan\s+repayment).*?(?:rs\.?|inr|₹)\s*$AMOUNT_REGEX)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:emi(?:\s+of)?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:recharge(?:\s+of)?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:renewed(?:\s+for)?)\s*(?:Rs\.?|INR|₹)?\s*$AMOUNT_REGEX""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+(?:debited|deducted|spent|transferred|withdrawn|sent\s+to|used\s+for|paid)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+(?:you\s+)?paid\s+to""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX\s+(?:bill\s+payment|payment|upi\s+payment)\s*successful""", Pattern.CASE_INSENSITIVE)
        )

        // Try the patterns corresponding to the detected direction FIRST
        val primaryPatterns = if (detectedDirection == TransactionDirection.CREDIT) {
            creditAmountPatterns
        } else {
            debitAmountPatterns
        }

        for (pattern in primaryPatterns) {
            val matcher = pattern.matcher(combinedText)
            while (matcher.find()) {
                val matchRange = matcher.start()..matcher.end()
                if (balRange != null && (matchRange.first in balRange || matchRange.last in balRange)) {
                    continue // Skip if this amount is part of balance clause
                }
                val matchStr = matcher.group(1)?.replace(",", "") ?: "0"
                val amt = matchStr.toDoubleOrNull() ?: 0.0
                if (amt > 0.0) {
                    detectedAmount = amt
                    if (detectedDirection == TransactionDirection.NEITHER) {
                        detectedDirection = if (primaryPatterns === creditAmountPatterns) {
                            TransactionDirection.CREDIT
                        } else {
                            TransactionDirection.DEBIT
                        }
                    }
                    break
                }
            }
            if (detectedAmount > 0.0) break
        }

        // If no amount found with primary patterns, try opposite direction patterns as fallback
        if (detectedAmount <= 0.0) {
            val secondaryPatterns = if (detectedDirection == TransactionDirection.CREDIT) debitAmountPatterns else creditAmountPatterns
            for (pattern in secondaryPatterns) {
                val matcher = pattern.matcher(combinedText)
                while (matcher.find()) {
                    val matchRange = matcher.start()..matcher.end()
                    if (balRange != null && (matchRange.first in balRange || matchRange.last in balRange)) {
                        continue
                    }
                    val matchStr = matcher.group(1)?.replace(",", "") ?: "0"
                    val amt = matchStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        detectedAmount = amt
                        // If secondary pattern matched, re-verify direction
                        detectedDirection = if (secondaryPatterns === creditAmountPatterns) {
                            TransactionDirection.CREDIT
                        } else {
                            TransactionDirection.DEBIT
                        }
                        break
                    }
                }
                if (detectedAmount > 0.0) break
            }
        }

        // If still no amount found with action-bound verbs, check all currency amounts excluding balance clause
        if (detectedAmount <= 0.0) {
            val genericCurrencyPattern = Pattern.compile(
                """(?:Rs\.?|INR|₹)\s*$AMOUNT_REGEX""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = genericCurrencyPattern.matcher(combinedText)
            while (matcher.find()) {
                val matchRange = matcher.start()..matcher.end()
                if (balRange != null && (matchRange.first in balRange || matchRange.last in balRange)) {
                    continue
                }
                val matchStr = matcher.group(1)?.replace(",", "") ?: "0"
                val amt = matchStr.toDoubleOrNull() ?: 0.0
                if (amt > 0.0) {
                    detectedAmount = amt
                    break
                }
            }

            if (detectedAmount <= 0.0) {
                return rejectedResult(
                    rawText = text,
                    reason = RejectionReason.INSUFFICIENT_TRANSACTION_EVIDENCE,
                    title = "No Transaction Detected",
                    timestamp = timestamp,
                    sourcePackage = sourcePackage
                )
            }

            // An amount was found without action-bound verbs: only allow if direction was determined
            if (detectedDirection == TransactionDirection.NEITHER) {
                // To ensure 100% of parsed transactions are visible, default to DEBIT instead of rejecting
                detectedDirection = TransactionDirection.DEBIT
            }
        }

        // Amount MUST always be strictly positive (absolute monetary value)
        detectedAmount = kotlin.math.abs(detectedAmount)

        // If reversed, direction is always CREDIT and status is REVERSED
        if (isReversed) {
            detectedDirection = TransactionDirection.CREDIT
        }

        // -------------------------------------------------------------
        // 9. RESOLVE TRANSACTION EVENT TYPE
        // -------------------------------------------------------------
        val isEmiDebitSemantic = Regex("""\b(?:emi|nach|ecs|ach|loan\s+installment|loan\s+repayment)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        val eventType = when {
            isReversed -> FinancialEventType.REVERSAL
            isRefund -> FinancialEventType.REFUND
            isSelfTransfer -> FinancialEventType.TRANSFER
            isLoanDisbursed -> FinancialEventType.LOAN_DISBURSEMENT
            isEmiDebitSemantic && detectedDirection == TransactionDirection.DEBIT -> FinancialEventType.LOAN_PAYMENT
            Regex("""\bemi\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) && detectedDirection == TransactionDirection.DEBIT -> FinancialEventType.LOAN_PAYMENT
            Regex("""\b(?:subscription|netflix|spotify|youtube\s+premium|hotstar)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> FinancialEventType.SUBSCRIPTION_PAYMENT
            detectedDirection == TransactionDirection.CREDIT -> FinancialEventType.INCOME
            else -> FinancialEventType.EXPENSE
        }

        // -------------------------------------------------------------
        // 10. EXTRACT MERCHANT & CATEGORY
        // -------------------------------------------------------------
        var detectedMerchant: String? = null
        var detectedCategory = "Other"

        // Dictionary lookup
        for ((keyword, mapping) in merchantCategoryMap) {
            if (normalized.contains(keyword)) {
                detectedMerchant = mapping.first
                detectedCategory = mapping.second
                break
            }
        }

        // Contextual merchant patterns if dictionary didn't find one
        if (detectedMerchant == null) {
            val merchantPatterns = if (detectedDirection == TransactionDirection.CREDIT) {
                listOf(
                    Pattern.compile("""\b(?:(?:received|credited|deposited)(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*from|received\s+from|from)\s+([A-Za-z0-9\s&'-]{2,25}?)(?:\s+(?:via|using|ref|upi|avl|bal|to|on|txn|\.)|[\s.]*$)""", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("""\b(?:(?:you\s+)?paid(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*to|sent(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*to|transfer(?:red)?(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*to|to|sent\s+to|at|vpa)\s+([A-Za-z0-9\s&'-]{2,25}?)(?:\s+(?:via|using|ref|upi|avl|bal|from|txn|\.)|[\s.]*$)""", Pattern.CASE_INSENSITIVE)
                )
            } else {
                listOf(
                    Pattern.compile("""\b(?:(?:you\s+)?paid(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*to|sent(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*to|transfer(?:red)?(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*to|to|sent\s+to|at|vpa)\s+([A-Za-z0-9\s&'-]{2,25}?)(?:\s+(?:via|using|ref|upi|avl|bal|from|txn|\.)|[\s.]*$)""", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("""\b(?:(?:received|credited|deposited)(?:\s+(?:Rs\.?|INR|₹))?\s*[0-9,.]*\s*from|received\s+from|from)\s+([A-Za-z0-9\s&'-]{2,25}?)(?:\s+(?:via|using|ref|upi|avl|bal|to|on|txn|\.)|[\s.]*$)""", Pattern.CASE_INSENSITIVE)
                )
            }

            for (mPat in merchantPatterns) {
                val mMatcher = mPat.matcher(combinedText)
                if (mMatcher.find()) {
                    val candidate = mMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':') ?: ""
                    val candidateLower = candidate.lowercase(Locale.ROOT)
                    if (candidate.length in 2..30 && !ignoredMerchantWords.contains(candidateLower)) {
                        detectedMerchant = candidate
                        break
                    }
                }
            }
        }

        // Specific category overrides
        when {
            eventType == FinancialEventType.REFUND -> detectedCategory = "Refund"
            eventType == FinancialEventType.LOAN_PAYMENT -> detectedCategory = "Loans & Debt"
            normalized.contains("atm") || normalized.contains("cash withdrawal") || normalized.contains("cash wdl") || normalized.contains("atm wdl") || normalized.contains("atm cash") || normalized.contains("self withdrawal") -> detectedCategory = "ATM Withdrawal"
            eventType == FinancialEventType.INCOME && normalized.contains("cashback") -> detectedCategory = "Rewards"
            eventType == FinancialEventType.INCOME && normalized.contains("salary") -> detectedCategory = "Salary"
            detectedCategory == "Other" -> {
                detectedCategory = when {
                    Regex("""\bsalary\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Salary"
                    Regex("""\b(?:bescom|electricity|power|broadband|water|gas|utility)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Bills"
                    Regex("""\b(?:uber|ola|rapido|fuel|petrol|metro|flight|train|irctc)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Transport"
                    Regex("""\b(?:swiggy|zomato|restaurant|cafe|food|dining|pizza)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Food"
                    Regex("""\b(?:amazon|flipkart|myntra|shopping|retail|store)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Shopping"
                    Regex("""\b(?:netflix|spotify|hotstar|prime|subscription)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Subscription"
                    // Person-to-person transfer: sent to or received from a person via UPI/bank when not matching any commercial category
                    detectedMerchant != null && (detectedDirection == TransactionDirection.CREDIT || detectedDirection == TransactionDirection.DEBIT) -> "Personal Transfer"
                    Regex("""\b(?:sent|paid|transfer(?:red)?)\b.*?\bto\s+[a-zA-Z]""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Personal Transfer"
                    Regex("""\b(?:received|credited|deposit(?:ed)?)\b.*?\bfrom\s+[a-zA-Z]""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Personal Transfer"
                    Regex("""\b(?:sent\s+to|paid\s+to|transfer(?:red)?\s+to|received\s+from|from)\s+[a-zA-Z]""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Personal Transfer"
                    else -> "Other"
                }
            }
        }

        if (eventType == FinancialEventType.LOAN_PAYMENT && detectedMerchant == null) {
            val lender = when {
                normalized.contains("hdfc") -> "HDFC Bank"
                normalized.contains("sbi") -> "State Bank of India"
                normalized.contains("icici") -> "ICICI Bank"
                normalized.contains("axis") -> "Axis Bank"
                normalized.contains("kotak") -> "Kotak Mahindra Bank"
                normalized.contains("bajaj") -> "Bajaj Finance"
                normalized.contains("tata") -> "Tata Capital"
                normalized.contains("pnb") -> "Punjab National Bank"
                normalized.contains("canara") -> "Canara Bank"
                normalized.contains("idfc") -> "IDFC FIRST Bank"
                else -> null
            }
            if (lender != null) {
                detectedMerchant = "$lender Loan EMI"
            }
        }

        // Resolved Title
        val resolvedTitle = detectedMerchant ?: when (eventType) {
            FinancialEventType.INCOME -> if (detectedCategory == "Salary") "Salary Credit" else "Income Credit"
            FinancialEventType.EXPENSE -> if (normalized.contains("atm") || normalized.contains("cash withdrawal") || normalized.contains("cash wdl") || normalized.contains("atm wdl") || normalized.contains("atm cash") || normalized.contains("self withdrawal")) "ATM Cash Withdrawal" else if (normalized.contains("recharge")) "Mobile Recharge" else "Expense"
            FinancialEventType.TRANSFER -> "Account Transfer"
            FinancialEventType.LOAN_PAYMENT -> "Loan EMI Payment"
            FinancialEventType.LOAN_DISBURSEMENT -> "Loan Disbursement"
            FinancialEventType.SUBSCRIPTION_PAYMENT -> "Subscription Payment"
            FinancialEventType.REFUND -> "Refund Credit"
            FinancialEventType.REVERSAL -> "Transaction Reversal"
            else -> "Financial Transaction"
        }

        // Reference ID & Account Hint
        val refId = extractReferenceId(combinedText)
        val acctHint = extractAccountHint(combinedText)
        val paymentMethod = detectPaymentMethod(normalized)

        // -------------------------------------------------------------
        // 11. CONFIDENCE ASSIGNMENT
        // -------------------------------------------------------------
        val finalStatus = when {
            isPending -> FinancialStatus.PENDING
            isReversed -> FinancialStatus.REVERSED
            isRefund -> FinancialStatus.REFUNDED
            else -> FinancialStatus.COMPLETED
        }

        val confidence = when {
            finalStatus == FinancialStatus.PENDING -> ParserConfidence.MEDIUM_CONFIDENCE
            detectedAmount > 0.0 && detectedDirection != TransactionDirection.NEITHER -> ParserConfidence.HIGH_CONFIDENCE
            else -> ParserConfidence.MEDIUM_CONFIDENCE
        }

        val fingerprint = generateFingerprint(
            amount = detectedAmount,
            type = if (detectedDirection.isInflow) "INCOME" else "EXPENSE",
            timestamp = timestamp,
            merchant = detectedMerchant ?: resolvedTitle,
            referenceId = refId
        )

        return ParsedTransactionResult(
            title = resolvedTitle,
            merchant = detectedMerchant,
            amount = detectedAmount,
            category = detectedCategory,
            paymentMethod = paymentMethod,
            isAutoDetected = detectedMerchant != null || detectedCategory != "Other",
            rawText = text,
            confidence = confidence,
            referenceId = refId,
            direction = detectedDirection,
            transactionType = eventType,
            status = finalStatus,
            timestamp = timestamp,
            sourceApp = sourcePackage,
            accountHint = acctHint,
            balanceAfter = detectedBalance,
            rejectionReason = if (isPending) RejectionReason.PAYMENT_INITIATION else RejectionReason.NONE,
            source = if (sourcePackage.equals("sms", ignoreCase = true)) "SMS" else "NOTIFICATION",
            sender = if (sourcePackage.equals("sms", ignoreCase = true)) title else "",
            fingerprint = fingerprint
        )
    }

    fun normalizeMerchantForFingerprint(raw: String): String {
        var m = raw.trim().lowercase(Locale.ROOT)
        if (m.isBlank()) return "unknown"
        // Strip common notification/SMS prefixes
        m = m.replace(Regex("""^(?:paid\s+to|sent\s+to|payment\s+to|transfer\s+to|to\s+|upi[-/_]|vpa\s+)""", RegexOption.IGNORE_CASE), "").trim()
        // Strip VPA handles (e.g. swiggy@icici -> swiggy)
        if (m.contains("@")) {
            val prefix = m.substringBefore("@").trim()
            if (prefix.isNotBlank()) m = prefix
        }
        // Normalize punctuation / whitespace to alphanumeric
        val cleaned = m.replace(Regex("""[^a-z0-9]"""), "")
        return if (cleaned.isNotBlank()) cleaned else m
    }

    fun generateFingerprint(
        amount: Double,
        type: String = "EXPENSE",
        timestamp: Long = System.currentTimeMillis(),
        merchant: String = "",
        referenceId: String = ""
    ): String {
        val normAmount = String.format(Locale.ROOT, "%.2f", kotlin.math.abs(amount))
        val normType = if (type.contains("credit", ignoreCase = true) || type.contains("inflow", ignoreCase = true) || type.equals("INCOME", ignoreCase = true)) "INFLOW" else "OUTFLOW"
        // 5-minute window bucket (5 * 60 * 1000 = 300,000 ms)
        val timeBucket = timestamp / 300_000L
        val normMerchant = normalizeMerchantForFingerprint(merchant)
        val raw = "${normAmount}_${normType}_${timeBucket}_${normMerchant}"
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            raw.hashCode().toString()
        }
    }

    private fun extractReferenceId(text: String): String {
        val refPatterns = listOf(
            Pattern.compile("""(?:upi\s+ref(?:\s+no)?|upi\s+txn(?:\s+id)?|upi\s+transaction\s+id)\s*[:#\-]?\s*([0-9A-Za-z\-_]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:ref(?:\s+no)?|txn(?:\s+id)?|transaction\s+id|utr|rrn)\s*[:#\-]?\s*([A-Za-z0-9\-_]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(?:vpa|upi\s+id)\s*[:#\-]?\s*([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)""", Pattern.CASE_INSENSITIVE)
        )
        for (refPat in refPatterns) {
            val matcher = refPat.matcher(text)
            if (matcher.find()) {
                val ref = matcher.group(1)?.trim() ?: ""
                if (ref.isNotEmpty()) return ref
            }
        }
        return ""
    }

    private fun extractAccountHint(text: String): String {
        val acctPatterns = listOf(
            Pattern.compile("""(?:a/c|acct|account)\s+(?:no\.?\s*)?(?:ending\s+)?([xX*]*[0-9]{3,4})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:card\s+ending)\s+([0-9]{4})""", Pattern.CASE_INSENSITIVE)
        )
        for (pat in acctPatterns) {
            val matcher = pat.matcher(text)
            if (matcher.find()) {
                val acct = matcher.group(1)?.trim() ?: ""
                if (acct.isNotEmpty()) return acct
            }
        }
        return ""
    }

    private fun detectPaymentMethod(normalized: String): String {
        return when {
            normalized.contains("upi") || normalized.contains("vpa") || normalized.contains("phonepe") || normalized.contains("gpay") || normalized.contains("paytm") -> "UPI"
            normalized.contains("credit card") || normalized.contains("card ending") -> "Credit Card"
            normalized.contains("debit card") -> "Debit Card"
            normalized.contains("netbanking") -> "NetBanking"
            normalized.contains("neft") || normalized.contains("imps") || normalized.contains("rtgs") -> "Bank Transfer"
            normalized.contains("cash withdrawal") || normalized.contains("atm") -> "Cash"
            else -> "UPI"
        }
    }

    private fun rejectedResult(
        rawText: String,
        reason: RejectionReason,
        title: String,
        timestamp: Long,
        sourcePackage: String,
        balanceAfter: Double? = null
    ): ParsedTransactionResult {
        return ParsedTransactionResult(
            title = title,
            merchant = null,
            amount = 0.0,
            category = "Other",
            paymentMethod = "Other",
            isAutoDetected = false,
            rawText = rawText,
            confidence = ParserConfidence.REJECTED,
            referenceId = "",
            direction = TransactionDirection.NEITHER,
            transactionType = FinancialEventType.EXPENSE,
            status = FinancialStatus.COMPLETED,
            timestamp = timestamp,
            sourceApp = sourcePackage,
            accountHint = "",
            balanceAfter = balanceAfter,
            rejectionReason = reason
        )
    }
}

