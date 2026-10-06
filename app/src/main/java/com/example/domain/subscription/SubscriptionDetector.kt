package com.example.domain.subscription

import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.domain.financial.FinancialStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic, privacy-first detector for subscriptions and recurring payments.
 * Consumes normalized Room TransactionEntity records. Never inspects raw SMS directly.
 */
object SubscriptionDetector {

    /**
     * Deterministic dictionary mapping recognized subscription service aliases to Android package names.
     */
    val KNOWN_SUBSCRIPTION_DICTIONARY: Map<String, String> = mapOf(
        "NETFLIX" to "com.netflix.mediaclient",
        "SPOTIFY" to "com.spotify.music",
        "PRIME" to "com.amazon.avod.thirdpartyclient",
        "AMAZON PRIME" to "com.amazon.avod.thirdpartyclient",
        "HOTSTAR" to "in.startv.hotstar",
        "DISNEY" to "in.startv.hotstar",
        "DISNEY+ HOTSTAR" to "in.startv.hotstar",
        "YOUTUBE" to "com.google.android.youtube",
        "CHATGPT" to "com.openai.chatgpt",
        "OPENAI" to "com.openai.chatgpt",
        "ZEE5" to "com.graymatrix.did",
        "SONYLIV" to "com.sonyliv",
        "SWIGGY" to "in.swiggy.android",
        "ZOMATO" to "com.application.zomato",
        "CULT.FIT" to "in.cure.fit",
        "GOOGLE ONE" to "com.google.android.apps.subscriptions.red",
        "MICROSOFT 365" to "com.microsoft.office.officehubrow",
        "APPLE MUSIC" to "com.apple.android.music",
        "AUDIBLE" to "com.audible.application"
    )

    private val RECURRING_KEYWORDS = listOf(
        "mandate",
        "autopay",
        "standing instruction",
        "recurring",
        "subscription",
        "membership"
    )

    private val ANNUAL_KEYWORDS = listOf(
        "annual",
        "yearly",
        "12 months",
        "one year"
    )

    // Standalone SI (Standing Instruction) regex token
    private val STANDALONE_SI_REGEX = Regex("""(?i)\b(si|s\.i\.)\b""")

    /**
     * Detects potential recurring subscriptions from completed debit transactions.
     * Idempotent: Skips services that are already tracked in [existingSubscriptions] or [rejectedMerchants].
     */
    fun detectPotentialSubscriptions(
        transactions: List<TransactionEntity>,
        existingSubscriptions: List<SubscriptionEntity>,
        rejectedMerchants: Set<String> = emptySet()
    ): List<PotentialSubscription> {
        // Only evaluate completed debit/expense transactions with positive amounts
        val debitExpenses = transactions.filter { txn ->
            val isCompleted = txn.status.isBlank() || txn.status.equals(FinancialStatus.COMPLETED.name, ignoreCase = true)
            val isDebit = txn.isOutflow || txn.type.equals("EXPENSE", ignoreCase = true) || txn.eventType.equals("SUBSCRIPTION_PAYMENT", ignoreCase = true)
            isCompleted && isDebit && txn.amount > 0.0
        }

        if (debitExpenses.isEmpty()) return emptyList()

        val candidates = mutableListOf<PotentialSubscription>()
        val groupedByMerchant = debitExpenses.groupBy { normalizeMerchantIdentity(it) }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for ((normalizedIdentity, txns) in groupedByMerchant) {
            val sampleTxn = txns.first()
            val rawName = sampleTxn.merchant.ifBlank { sampleTxn.title }.trim()
            val displayMerchant = cleanMerchantDisplayName(rawName)

            if (isAlreadyTrackedOrRejected(displayMerchant, normalizedIdentity, existingSubscriptions, rejectedMerchants)) {
                continue
            }

            val sortedTxns = txns.sortedBy { it.timestamp }
            val isAnnual = sortedTxns.any { hasAnnualEvidence(it) }

            // Check if multiple transactions exhibit recurring monthly pattern (~27-32 days apart, ±5% amount)
            val recurringMonthlyMatch = findRecurringIntervalPattern(sortedTxns, dateFormat)

            if (recurringMonthlyMatch != null) {
                val suggestedPkg = resolvePackageForMerchant(displayMerchant)
                val billingCycle = if (isAnnual) "ANNUAL" else "MONTHLY"
                candidates.add(
                    PotentialSubscription(
                        merchant = displayMerchant,
                        amount = recurringMonthlyMatch.amount,
                        billingPeriod = billingCycle,
                        intervalDays = recurringMonthlyMatch.intervalDays,
                        occurrences = recurringMonthlyMatch.occurrences,
                        lastTransactionDate = sortedTxns.last().date.ifBlank {
                            dateFormat.format(Date(sortedTxns.last().timestamp))
                        },
                        confidence = 0.90,
                        reason = "Recurring monthly payments detected (~${recurringMonthlyMatch.intervalDays} days apart • ±5% amount)",
                        suggestedPackage = suggestedPkg
                    )
                )
                continue
            }

            // Check Rule #1 (Category or subscription evidence keyword) or Rule #2 (Known Subscription Alias)
            val matchesRule1 = sortedTxns.any { hasSubscriptionEvidence(it) }
            val matchesRule2 = matchesKnownSubscriptionAlias(sampleTxn)

            if (matchesRule1 || matchesRule2 || isAnnual) {
                val latest = sortedTxns.last()
                val suggestedPkg = resolvePackageForMerchant(displayMerchant)
                val billingCycle = if (isAnnual) "ANNUAL" else "MONTHLY"
                val reasonText = when {
                    isAnnual -> "Annual recurring plan detected ($displayMerchant)"
                    matchesRule2 -> "Recognized subscription service ($displayMerchant)"
                    else -> "Subscription payment evidence detected ($displayMerchant)"
                }

                candidates.add(
                    PotentialSubscription(
                        merchant = displayMerchant,
                        amount = latest.amount,
                        billingPeriod = billingCycle,
                        intervalDays = if (isAnnual) 365 else 30,
                        occurrences = sortedTxns.size,
                        lastTransactionDate = latest.date.ifBlank {
                            dateFormat.format(Date(latest.timestamp))
                        },
                        confidence = if (matchesRule2) 0.85 else 0.75,
                        reason = reasonText,
                        suggestedPackage = suggestedPkg
                    )
                )
            }
        }

        return candidates
    }

    /**
     * Rule #1: category == "Subscription" OR contains explicit recurring/mandate/autopay evidence.
     * Prevents false positives from words containing "si".
     */
    fun hasSubscriptionEvidence(txn: TransactionEntity): Boolean {
        if (txn.category.equals("Subscription", ignoreCase = true)) return true

        val textToSearch = "${txn.title} ${txn.merchant} ${txn.note}".lowercase(Locale.ROOT)

        if (RECURRING_KEYWORDS.any { textToSearch.contains(it) }) return true

        // Strict Standing Instruction (SI) token check:
        // Must appear as a standalone word/token AND in banking/payment context
        if (STANDALONE_SI_REGEX.containsMatchIn(txn.title) || STANDALONE_SI_REGEX.containsMatchIn(txn.note)) {
            val paymentContext = textToSearch.contains("debit") ||
                    textToSearch.contains("nach") ||
                    textToSearch.contains("mandate") ||
                    textToSearch.contains("bank") ||
                    textToSearch.contains("standing")
            if (paymentContext) return true
        }

        return false
    }

    /**
     * Rule #2: Matches known subscription aliases safely without converting
     * general shopping/food transactions into subscriptions.
     */
    fun matchesKnownSubscriptionAlias(txn: TransactionEntity): Boolean {
        val titleUpper = txn.title.uppercase(Locale.ROOT)
        val merchantUpper = txn.merchant.uppercase(Locale.ROOT)
        val combined = "$titleUpper $merchantUpper"

        // Netflix / Spotify / Hotstar / Disney / YouTube / ChatGPT / Zee5 / SonyLiv
        if (combined.contains("NETFLIX")) return true
        if (combined.contains("SPOTIFY")) return true
        if (combined.contains("HOTSTAR") || combined.contains("DISNEY")) return true
        if (combined.contains("CHATGPT") || combined.contains("OPENAI")) return true
        if (combined.contains("ZEE5")) return true
        if (combined.contains("SONYLIV") || combined.contains("SONY LIV")) return true

        // YouTube: only when recurring/membership/premium/subscription is mentioned
        if (combined.contains("YOUTUBE")) {
            val isPremium = combined.contains("PREMIUM") || combined.contains("MEMBERSHIP") ||
                    combined.contains("SUB") || hasSubscriptionEvidence(txn)
            if (isPremium) return true
        }

        // Amazon: Amazon Prime only, NOT generic Amazon groceries/shopping
        if (combined.contains("AMAZON")) {
            val isPrime = combined.contains("PRIME") || combined.contains("PRIME VIDEO") || combined.contains("SUBSCRIPTION")
            if (isPrime) return true
        } else if (combined.contains("PRIME VIDEO") || combined.contains("PRIME MEMBERSHIP")) {
            return true
        }

        // Swiggy: Swiggy One/Super/Membership only, NOT generic food delivery
        if (combined.contains("SWIGGY")) {
            val isSwiggyOne = combined.contains("ONE") || combined.contains("SUPER") ||
                    combined.contains("MEMBERSHIP") || hasSubscriptionEvidence(txn)
            if (isSwiggyOne) return true
        }

        // Zomato: Zomato Gold/Pro only, NOT generic food delivery
        if (combined.contains("ZOMATO")) {
            val isZomatoGold = combined.contains("GOLD") || combined.contains("PRO") ||
                    combined.contains("MEMBERSHIP") || hasSubscriptionEvidence(txn)
            if (isZomatoGold) return true
        }

        return false
    }

    /**
     * Checks if transaction context contains annual keywords.
     */
    fun hasAnnualEvidence(txn: TransactionEntity): Boolean {
        val text = "${txn.title} ${txn.note}".lowercase(Locale.ROOT)
        return ANNUAL_KEYWORDS.any { text.contains(it) }
    }

    /**
     * Recurring interval detection (Rule #13):
     * Finds 2 or more transactions with:
     * - Approximately same amount (tolerance ±5%)
     * - Spacing: 27–32 days between consecutive charges.
     */
    private fun findRecurringIntervalPattern(
        txns: List<TransactionEntity>,
        dateFormat: SimpleDateFormat
    ): RecurringPatternResult? {
        if (txns.size < 2) return null

        for (i in 0 until txns.size - 1) {
            val t1 = txns[i]
            val t2 = txns[i + 1]

            // Check amount tolerance: abs(a - b) / max(a, b) <= 0.05
            val maxAmount = max(t1.amount, t2.amount)
            if (maxAmount <= 0.0) continue
            val amountDiff = abs(t1.amount - t2.amount) / maxAmount
            if (amountDiff > 0.05) continue

            // Spacing check: 27–32 days
            val daysDiff = calculateDaysBetween(t1, t2, dateFormat)
            if (daysDiff in 27..32) {
                return RecurringPatternResult(
                    amount = t2.amount,
                    intervalDays = daysDiff.toInt(),
                    occurrences = txns.size
                )
            }
        }

        return null
    }

    private fun calculateDaysBetween(
        t1: TransactionEntity,
        t2: TransactionEntity,
        dateFormat: SimpleDateFormat
    ): Long {
        if (t1.timestamp > 0L && t2.timestamp > 0L) {
            val diffMs = abs(t2.timestamp - t1.timestamp)
            return TimeUnit.MILLISECONDS.toDays(diffMs)
        }

        return try {
            val d1 = dateFormat.parse(t1.date)
            val d2 = dateFormat.parse(t2.date)
            if (d1 != null && d2 != null) {
                val diffMs = abs(d2.time - d1.time)
                TimeUnit.MILLISECONDS.toDays(diffMs)
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Resolves a package name for a recognized merchant from the deterministic dictionary.
     */
    fun resolvePackageForMerchant(merchant: String): String? {
        val upper = merchant.uppercase(Locale.ROOT)
        for ((alias, pkg) in KNOWN_SUBSCRIPTION_DICTIONARY) {
            if (upper.contains(alias) || alias.contains(upper)) {
                return pkg
            }
        }
        return null
    }

    /**
     * Normalizes merchant names to a consistent grouping key.
     */
    fun normalizeMerchantIdentity(txn: TransactionEntity): String {
        val raw = txn.merchant.ifBlank { txn.title }.trim().uppercase(Locale.ROOT)
        return when {
            raw.contains("NETFLIX") -> "NETFLIX"
            raw.contains("SPOTIFY") -> "SPOTIFY"
            raw.contains("AMAZON") && (raw.contains("PRIME") || raw.contains("VIDEO")) -> "AMAZON_PRIME"
            raw.contains("PRIME VIDEO") -> "AMAZON_PRIME"
            raw.contains("HOTSTAR") || raw.contains("DISNEY") -> "DISNEY_HOTSTAR"
            raw.contains("YOUTUBE") -> "YOUTUBE_PREMIUM"
            raw.contains("CHATGPT") || raw.contains("OPENAI") -> "CHATGPT"
            raw.contains("ZEE5") -> "ZEE5"
            raw.contains("SONYLIV") || raw.contains("SONY LIV") -> "SONYLIV"
            raw.contains("SWIGGY") && (raw.contains("ONE") || raw.contains("SUPER")) -> "SWIGGY_ONE"
            raw.contains("ZOMATO") && (raw.contains("GOLD") || raw.contains("PRO")) -> "ZOMATO_GOLD"
            raw.contains("CULT.FIT") || raw.contains("CULTFIT") -> "CULT_FIT"
            raw.contains("GOOGLE ONE") -> "GOOGLE_ONE"
            raw.contains("MICROSOFT") -> "MICROSOFT_365"
            raw.contains("APPLE") && (raw.contains("MUSIC") || raw.contains("BILL") || raw.contains("ICLOUD")) -> "APPLE_SERVICES"
            else -> raw.replace(Regex("""[^A-Z0-9]"""), "")
        }
    }

    fun cleanMerchantDisplayName(raw: String): String {
        val upper = raw.uppercase(Locale.ROOT)
        return when {
            upper.contains("NETFLIX") -> "Netflix"
            upper.contains("SPOTIFY") -> "Spotify"
            upper.contains("PRIME") || upper.contains("AMAZON") -> "Amazon Prime"
            upper.contains("HOTSTAR") || upper.contains("DISNEY") -> "Disney+ Hotstar"
            upper.contains("YOUTUBE") -> "YouTube Premium"
            upper.contains("CHATGPT") || upper.contains("OPENAI") -> "ChatGPT Plus"
            upper.contains("ZEE5") -> "Zee5"
            upper.contains("SONYLIV") || upper.contains("SONY LIV") -> "SonyLIV"
            upper.contains("SWIGGY") -> "Swiggy One"
            upper.contains("ZOMATO") -> "Zomato Gold"
            upper.contains("CULT.FIT") || upper.contains("CULTFIT") -> "Cult.fit"
            upper.contains("GOOGLE ONE") -> "Google One"
            upper.contains("MICROSOFT") -> "Microsoft 365"
            upper.contains("APPLE") -> "Apple Services"
            else -> raw.split(" ").take(3).joinToString(" ").replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Prevents duplicate candidate suggestions if service is already tracked or rejected.
     */
    fun isAlreadyTrackedOrRejected(
        displayMerchant: String,
        normalizedIdentity: String,
        existingSubscriptions: List<SubscriptionEntity>,
        rejectedMerchants: Set<String>
    ): Boolean {
        val dispUpper = displayMerchant.uppercase(Locale.ROOT)
        val normUpper = normalizedIdentity.uppercase(Locale.ROOT)

        val isRejected = rejectedMerchants.any { rejected ->
            val rejUpper = rejected.uppercase(Locale.ROOT)
            rejUpper == dispUpper || rejUpper == normUpper || dispUpper.contains(rejUpper) || rejUpper.contains(dispUpper)
        }
        if (isRejected) return true

        return existingSubscriptions.any { sub ->
            val subName = sub.name.uppercase(Locale.ROOT)
            val subTitle = sub.title.uppercase(Locale.ROOT)
            val subMerchant = sub.merchantName.uppercase(Locale.ROOT)
            subName.contains(dispUpper) || dispUpper.contains(subName) ||
                    subTitle.contains(dispUpper) || dispUpper.contains(subTitle) ||
                    subMerchant.contains(dispUpper) || dispUpper.contains(subMerchant) ||
                    subName.contains(normUpper) || normUpper.contains(subName)
        }
    }

    /**
     * Detects price changes between historical payment transactions and current subscription amount.
     */
    fun detectPriceChanges(
        subscriptions: List<SubscriptionEntity>,
        transactions: List<TransactionEntity>
    ): List<SubscriptionPriceChange> {
        val priceChanges = mutableListOf<SubscriptionPriceChange>()

        for (sub in subscriptions) {
            if (sub.amount <= 0.0) continue
            val matchingTxns = transactions.filter { txn ->
                val name = (txn.merchant.ifBlank { txn.title }).uppercase(Locale.ROOT)
                val subName = sub.name.uppercase(Locale.ROOT)
                (name.contains(subName) || subName.contains(name)) && txn.amount > 0.0
            }.sortedByDescending { it.timestamp }

            if (matchingTxns.isNotEmpty()) {
                val latestTxn = matchingTxns.first()
                if (abs(latestTxn.amount - sub.amount) >= 1.0) {
                    priceChanges.add(
                        SubscriptionPriceChange(
                            subscriptionId = sub.id,
                            name = sub.name,
                            oldAmount = sub.amount,
                            newAmount = latestTxn.amount,
                            difference = latestTxn.amount - sub.amount,
                            detectedDate = latestTxn.date
                        )
                    )
                }
            }
        }

        return priceChanges
    }

    private data class RecurringPatternResult(
        val amount: Double,
        val intervalDays: Int,
        val occurrences: Int
    )
}
