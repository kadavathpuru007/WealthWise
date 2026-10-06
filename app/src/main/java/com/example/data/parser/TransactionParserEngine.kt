package com.example.data.parser

/**
 * Single centralized transaction parser engine.
 * Both SMS and Notification ingestion routes converge through this engine.
 */
object TransactionParserEngine {

    fun parse(
        text: String,
        title: String = "",
        sourcePackage: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): ParsedTransaction {
        return TransactionParser.parse(text, title, sourcePackage, timestamp)
    }

    fun parseNotification(source: NotificationTransactionSource): ParsedTransaction {
        val combinedText = listOf(source.title, source.body, source.subText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

        return TransactionParser.parse(
            text = combinedText,
            title = source.title,
            sourcePackage = source.packageName,
            timestamp = source.timestamp
        )
    }

    fun parseSms(source: SmsTransactionSource): ParsedTransaction {
        return TransactionParser.parse(
            text = source.body,
            title = source.sender,
            sourcePackage = "sms",
            timestamp = source.timestamp
        )
    }

    fun generateFingerprint(
        amount: Double,
        type: String,
        timestamp: Long,
        merchant: String,
        referenceId: String = ""
    ): String {
        return TransactionParser.generateFingerprint(amount, type, timestamp, merchant, referenceId)
    }
}
