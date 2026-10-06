package com.example

import com.example.data.parser.ParserConfidence
import com.example.data.parser.RejectionReason
import com.example.data.parser.TransactionDirection
import com.example.data.parser.TransactionParser
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialStatus
import org.junit.Assert.*
import org.junit.Test

class TransactionParserTest {

    @Test
    fun `test recharge promotional offer rs399 is rejected`() {
        val msg = "Special offer! Get unlimited calls + 1.5GB/day recharge for only Rs 399. Click to recharge now: bit.ly/recharge"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PROMOTIONAL, result.rejectionReason)
        assertEquals(0.0, result.amount, 0.001)
    }

    @Test
    fun `test flat discount shopping offer is rejected`() {
        val msg = "Flat Rs 500 off on your next purchase at Myntra on orders above Rs 1999! Use coupon FESTIVE500."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PROMOTIONAL, result.rejectionReason)
    }

    @Test
    fun `test flight booking discount offer is rejected`() {
        val msg = "Save up to Rs 1500 on domestic flights booked today on MakeMyTrip! Promo code FLYMORE."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PROMOTIONAL, result.rejectionReason)
    }

    @Test
    fun `test cashback promotion is rejected`() {
        val msg = "Win up to Rs 100 cashback on paying your electricity bill before Friday on CRED."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PROMOTIONAL, result.rejectionReason)
    }

    @Test
    fun `test pizza discount promo is rejected`() {
        val msg = "Hungry? Order large pizza starting at Rs. 299 only! Use code CHEESE50 on Domino's app."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PROMOTIONAL, result.rejectionReason)
    }

    // =========================================================================
    // SECTION 2: LOAN OFFERS & CREDIT LIMITS (NEVER LOG AS EXPENSE/INCOME)
    // =========================================================================

    @Test
    fun `test pre-approved loan offer is rejected`() {
        val msg = "Congratulations! You have a pre-approved personal loan of Rs 5,00,000 at low interest. Avail now in 2 mins."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.LOAN_OFFER, result.rejectionReason)
    }

    @Test
    fun `test instant loan offer is rejected`() {
        val msg = "Dear Customer, instant cash loan up to Rs 2,50,000 is ready for disbursal to your A/c. Apply today."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.LOAN_OFFER, result.rejectionReason)
    }

    @Test
    fun `test credit limit enhancement is rejected`() {
        val msg = "Great news! Your ICICI Bank credit card limit has been increased to Rs 1,50,000. No extra charges."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.CREDIT_LIMIT, result.rejectionReason)
    }

    @Test
    fun `test available credit limit notification is rejected`() {
        val msg = "Dear Customer, your remaining credit card limit on Card ending 4402 is Rs. 42,000.00."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertTrue(result.rejectionReason == RejectionReason.CREDIT_LIMIT || result.rejectionReason == RejectionReason.BALANCE_ONLY)
    }

    // =========================================================================
    // SECTION 3: BALANCE INQUIRY NOTIFICATIONS (NEVER LOG AS EXPENSE)
    // =========================================================================

    @Test
    fun `test account balance check SMS is rejected`() {
        val msg = "Dear SBI User, your available account balance for A/c *8291 as of 15-Sep is Rs 45,210.50."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.BALANCE_ONLY, result.rejectionReason)
        assertEquals(45210.50, result.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `test HDFC clear balance inquiry is rejected`() {
        val msg = "Bal in A/c XX4029 as on 10-Oct is INR 12,450.00. Thank you for banking with HDFC Bank."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.BALANCE_ONLY, result.rejectionReason)
    }

    @Test
    fun `test mini statement balance is rejected`() {
        val msg = "Your effective balance in A/c 99201 is INR 8,300.00."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.BALANCE_ONLY, result.rejectionReason)
    }

    // =========================================================================
    // SECTION 4: BILL DUE & PAYMENT REMINDERS (NO MONEY HAS MOVED YET)
    // =========================================================================

    @Test
    fun `test electricity bill due reminder is rejected`() {
        val msg = "Reminder: Your BESCOM electricity bill of Rs 1,450 is due on 22-Oct. Pay now to avoid late fee."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.DUE_REMINDER, result.rejectionReason)
    }

    @Test
    fun `test credit card payment due notification is rejected`() {
        val msg = "Dear Customer, Total Amount Due on your HDFC Credit Card 9012 is Rs 18,290.00 due date 18-Nov-26."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.DUE_REMINDER, result.rejectionReason)
    }

    @Test
    fun `test broadband bill unpaid reminder is rejected`() {
        val msg = "Airtel Xstream bill of Rs. 1,179 is generated and due by 28-Sep. Click to pay bill."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.DUE_REMINDER, result.rejectionReason)
    }

    // =========================================================================
    // SECTION 5: OTP & AUTHORIZATION CODES (SECURITY, NOT A COMPLETED TX)
    // =========================================================================

    @Test
    fun `test bank OTP message is rejected`() {
        val msg = "OTP for transaction of Rs 850.00 at Swiggy is 948201. Do not share this OTP with anyone. Valid for 10 minutes."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PAYMENT_INITIATION, result.rejectionReason)
    }

    @Test
    fun `test NetBanking verification code is rejected`() {
        val msg = "402910 is your secret verification code for transferring INR 5,000.00. Never disclose to bank staff."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PAYMENT_INITIATION, result.rejectionReason)
    }

    @Test
    fun `test collect request pending is rejected`() {
        val msg = "Merchant requested payment of Rs 350.00 on PhonePe. Open app to approve collect request."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
    }

    // =========================================================================
    // SECTION 6: FAILED OR DECLINED TRANSACTIONS (DO NOT LOG EXPENSE)
    // =========================================================================

    @Test
    fun `test failed UPI payment status is failed`() {
        val msg = "UPI payment of Rs. 450 to Domino's Pizza failed due to incorrect UPI PIN. Amount has not been debited."
        val result = TransactionParser.parse(msg)
        assertEquals(FinancialStatus.FAILED, result.status)
        assertEquals(RejectionReason.FAILED_TRANSACTION, result.rejectionReason)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
    }

    @Test
    fun `test declined card transaction is rejected`() {
        val msg = "Transaction of INR 2,100 at Croma was declined by issuing bank due to insufficient funds."
        val result = TransactionParser.parse(msg)
        assertEquals(FinancialStatus.FAILED, result.status)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
    }

    // =========================================================================
    // SECTION 7: VALID REAL-WORLD UPI & BANK DEBIT EXPENSES (HIGH CONFIDENCE)
    // =========================================================================

    @Test
    fun `test zomato upi debit parsed correctly`() {
        val msg = "Paid Rs.450 to Zomato via UPI Ref 92837492 on 14-Sep-2026. Avl Bal: Rs.82,189."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(FinancialStatus.COMPLETED, result.status)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(450.0, result.amount, 0.01)
        assertEquals("Food", result.category)
        assertEquals("UPI", result.paymentMethod)
        assertEquals("Zomato", result.merchant)
        assertEquals("92837492", result.referenceId)
        assertEquals(82189.0, result.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `test uber ride upi debit parsed correctly`() {
        val msg = "Your A/c debited by Rs.285.00 on 13-Sep-26 for Uber ride via UPI txn 8382910."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(FinancialStatus.COMPLETED, result.status)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(285.0, result.amount, 0.01)
        assertEquals("Transport", result.category)
        assertEquals("Uber", result.merchant)
        assertEquals("8382910", result.referenceId)
    }

    @Test
    fun `test swiggy instamart debit parsed correctly`() {
        val msg = "Rs 620 debited from A/c XX8210 for Swiggy Instamart on 12-Sep-26. UPI Ref 38291048."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(620.0, result.amount, 0.01)
        assertEquals("Groceries", result.category)
        assertEquals("Swiggy Instamart", result.merchant)
        assertEquals("XX8210", result.accountHint)
    }

    @Test
    fun `test blinkit grocery order debit parsed correctly`() {
        val msg = "Sent Rs. 385.00 to Blinkit via PhonePe UPI. Txn ID: 948201948. Avl Bal Rs 15,200."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(385.0, result.amount, 0.01)
        assertEquals("Groceries", result.category)
        assertEquals("Blinkit", result.merchant)
        assertEquals(15200.0, result.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `test zepto delivery debit parsed correctly`() {
        val msg = "Paid Rs 240 to Zepto via Google Pay UPI. Ref No 8492018."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(240.0, result.amount, 0.01)
        assertEquals("Groceries", result.category)
        assertEquals("Zepto", result.merchant)
    }

    @Test
    fun `test amazon shopping netbanking debit parsed correctly`() {
        val msg = "Dear Customer, Rs.2,499.00 debited from A/c **4029 at Amazon India via NetBanking. Avl Bal: Rs 48,100."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(2499.0, result.amount, 0.01)
        assertEquals("Shopping", result.category)
        assertEquals("Amazon India", result.merchant)
        assertEquals("NetBanking", result.paymentMethod)
        assertEquals(48100.0, result.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `test flipkart shopping debit parsed correctly`() {
        val msg = "Rs 1,299 debited on Flipkart Internet Pvt Ltd using HDFC Card ending 1234."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(1299.0, result.amount, 0.01)
        assertEquals("Shopping", result.category)
        assertEquals("Flipkart Internet Pvt Ltd", result.merchant)
        assertEquals("Credit Card", result.paymentMethod)
    }

    @Test
    fun `test bescom electricity utility bill paid parsed correctly`() {
        val msg = "BESCOM bill payment of Rs.1,850 successful via Google Pay UPI. Ref: 8492019."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(1850.0, result.amount, 0.01)
        assertEquals("Bills", result.category)
        assertEquals("BESCOM", result.merchant)
    }

    @Test
    fun `test airtel broadband bill paid parsed correctly`() {
        val msg = "Paid Rs 1,179 to Airtel Broadband bill payment via Paytm UPI. UTR 39201849."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(1179.0, result.amount, 0.01)
        assertEquals("Bills", result.category)
        assertEquals("Airtel Broadband", result.merchant)
    }

    @Test
    fun `test hdfc bank debit alert with balance parsed correctly`() {
        val msg = "Rs 3,250.00 debited from HDFC Bank A/c 5492 on 18-Sep-26 to RELIANCE RETAIL. Avail bal: Rs 34,120.50."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(3250.0, result.amount, 0.01)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals("RELIANCE RETAIL", result.merchant)
        assertEquals(34120.50, result.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `test sbi pos debit alert parsed correctly`() {
        val msg = "Your SBI A/c 8492 debited by Rs.1,200.00 on 12Sep26 through POS/ECOM at APOLLO PHARMACY. Bal: Rs 18,900."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(1200.0, result.amount, 0.01)
        assertEquals("Health", result.category)
        assertEquals("APOLLO PHARMACY", result.merchant)
        assertEquals(18900.0, result.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `test atm cash withdrawal parsed correctly`() {
        val msg = "Cash withdrawal of Rs.5,000.00 from ATM using Card 4029. Available Bal: Rs.14,200.00."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(5000.0, result.amount, 0.01)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals("ATM Withdrawal", result.category)
    }

    @Test
    fun `test ola cabs ride debit parsed correctly`() {
        val msg = "Paid Rs 195 to Ola Cabs via UPI. Txn ref 9382019."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(195.0, result.amount, 0.01)
        assertEquals("Transport", result.category)
        assertEquals("Ola Cabs", result.merchant)
    }

    // =========================================================================
    // SECTION 8: VALID BANK & UPI CREDITS / INCOME (HIGH CONFIDENCE)
    // =========================================================================

    @Test
    fun `test salary credit parsed correctly as income`() {
        val msg = "Your A/c *8821 is credited by Rs 75,000.00 on 30-Sep towards September Salary from TechCorp. Bal: Rs 92,400."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(FinancialStatus.COMPLETED, result.status)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(FinancialEventType.INCOME, result.transactionType)
        assertEquals(75000.0, result.amount, 0.01)
        assertEquals("Salary", result.category)
        assertEquals(92400.0, result.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `test upi money received parsed correctly as income`() {
        val msg = "Received Rs 1,500.00 from Rahul Sharma via UPI on PhonePe. UTR 492019482. Avl Bal: Rs 8,300."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(FinancialEventType.INCOME, result.transactionType)
        assertEquals(1500.0, result.amount, 0.01)
        assertEquals("Rahul Sharma", result.merchant)
        assertEquals("492019482", result.referenceId)
    }

    @Test
    fun `test merchant refund credited parsed correctly`() {
        val msg = "Refund of Rs 499.00 from Swiggy credited to your A/c XX4029. Updated Bal: Rs 15,400."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(FinancialEventType.REFUND, result.transactionType)
        assertEquals(499.0, result.amount, 0.01)
        assertEquals("Refund", result.category)
    }

    @Test
    fun `test cashback credited parsed correctly`() {
        val msg = "Cashback of Rs 50 credited to your Google Pay wallet for scratch card reward."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(FinancialEventType.INCOME, result.transactionType)
        assertEquals(50.0, result.amount, 0.01)
        assertEquals("Rewards", result.category)
    }

    // =========================================================================
    // SECTION 9: REVERSED TRANSACTIONS
    // =========================================================================

    @Test
    fun `test reversed transaction handling`() {
        val msg = "Transaction of Rs. 350 at Cafe Coffee Day has been reversed to your account. Bal: Rs 4,500."
        val result = TransactionParser.parse(msg)
        assertEquals(FinancialStatus.REVERSED, result.status)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(350.0, result.amount, 0.01)
    }

    // =========================================================================
    // SECTION 10: EDGE CASES & NOISE REJECTION
    // =========================================================================

    @Test
    fun `test empty message returns rejected`() {
        val result = TransactionParser.parse("")
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(0.0, result.amount, 0.001)
    }

    @Test
    fun `test random conversation with no amount returns rejected`() {
        val msg = "Hey bro, let's meet tomorrow evening at the cafeteria after office."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.INSUFFICIENT_TRANSACTION_EVIDENCE, result.rejectionReason)
    }

    @Test
    fun `test tracking number or pin is not mistaken for currency amount`() {
        val msg = "Your order has shipped. Tracking number: 9840291. Expected delivery in 3 days."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
    }

    @Test
    fun `test phone number is not parsed as currency`() {
        val msg = "Call our customer support at +91 9845012345 for assistance with your account."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
    }

    // =========================================================================
    // SECTION 11: FINANCIAL SIGN RULES & INFLOW VS OUTFLOW DISCRIMINATION
    // =========================================================================

    @Test
    fun `case 1 - 1 rupee received parsed as INFLOW and plus 1`() {
        val msg = "₹1 received"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue("Direction must be INFLOW/CREDIT", result.direction.isInflow)
        assertTrue(result.isInflow)
        assertFalse(result.isOutflow)
        assertEquals(1.0, result.amount, 0.001)
        assertEquals(1.0, result.ledgerAmount, 0.001)
        assertEquals(1.0, result.signedAmount, 0.001)
    }

    @Test
    fun `case 2 - 1 rupee credited parsed as INFLOW and plus 1`() {
        val msg = "₹1 credited"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue("Direction must be INFLOW/CREDIT", result.direction.isInflow)
        assertTrue(result.isInflow)
        assertFalse(result.isOutflow)
        assertEquals(1.0, result.amount, 0.001)
        assertEquals(1.0, result.ledgerAmount, 0.001)
        assertEquals(1.0, result.signedAmount, 0.001)
    }

    @Test
    fun `case 3 - 500 credited to account parsed as INFLOW and plus 500`() {
        val msg = "₹500 credited to account"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isInflow)
        assertTrue(result.isInflow)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals(500.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `case 4 - Received 1000 from Rahul parsed as INFLOW and plus 1000`() {
        val msg = "Received ₹1000 from Rahul"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isInflow)
        assertTrue(result.isInflow)
        assertEquals(1000.0, result.amount, 0.001)
        assertEquals(1000.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `case 5 - Money received from Rahul 250 parsed as INFLOW and plus 250`() {
        val msg = "Money received from Rahul ₹250"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isInflow)
        assertTrue(result.isInflow)
        assertEquals(250.0, result.amount, 0.001)
        assertEquals(250.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `case 6 - 1 rupee debited parsed as OUTFLOW and minus 1`() {
        val msg = "₹1 debited"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue("Direction must be OUTFLOW/DEBIT", result.direction.isOutflow)
        assertTrue(result.isOutflow)
        assertFalse(result.isInflow)
        assertEquals(1.0, result.amount, 0.001)
        assertEquals(-1.0, result.ledgerAmount, 0.001)
        assertEquals(-1.0, result.signedAmount, 0.001)
    }

    @Test
    fun `case 7 - 500 paid for groceries parsed as OUTFLOW and minus 500`() {
        val msg = "₹500 paid for groceries"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isOutflow)
        assertTrue(result.isOutflow)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals(-500.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `case 8 - Sent 250 to Rahul parsed as OUTFLOW and minus 250`() {
        val msg = "Sent ₹250 to Rahul"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isOutflow)
        assertTrue(result.isOutflow)
        assertEquals(250.0, result.amount, 0.001)
        assertEquals(-250.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `case 9 - 500 debited with available balance 4500 ignores balance and parses minus 500`() {
        val msg = "₹500 debited for purchase. Available balance ₹4,500."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isOutflow)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals(-500.0, result.ledgerAmount, 0.001)
        assertEquals(4500.0, result.balanceAfter ?: 0.0, 0.001)
    }

    @Test
    fun `case 10 - 500 credited with available balance 5500 ignores balance and parses plus 500`() {
        val msg = "₹500 credited. Available balance ₹5,500."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isInflow)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals(500.0, result.ledgerAmount, 0.001)
        assertEquals(5500.0, result.balanceAfter ?: 0.0, 0.001)
    }

    @Test
    fun `case 11 - 500 refunded parsed as INFLOW and plus 500`() {
        val msg = "₹500 refunded to your account."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.direction.isInflow)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals(500.0, result.ledgerAmount, 0.001)
        assertEquals(FinancialEventType.REFUND, result.transactionType)
    }

    @Test
    fun `case 12 - Promotional message recharge for only 399 is rejected`() {
        val msg = "Recharge for only ₹399 and get unlimited calling!"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.PROMOTIONAL, result.rejectionReason)
    }

    @Test
    fun `case 13 - Balance inquiry your account balance is 12450 is rejected`() {
        val msg = "Your account balance is ₹12,450"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.BALANCE_ONLY, result.rejectionReason)
    }

    @Test
    fun `case 14 - Loan offer eligible loan amount 100000 is rejected`() {
        val msg = "Eligible loan amount ₹1,00,000. Apply now on the app."
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.REJECTED, result.confidence)
        assertEquals(RejectionReason.LOAN_OFFER, result.rejectionReason)
    }

    @Test
    fun `case 15 - Absolute amount storage verification and ledger sign derivation`() {
        val parsedInflow = TransactionParser.parse("Money received from Rahul ₹250")
        assertEquals(250.0, parsedInflow.amount, 0.001)
        assertTrue("Stored amount must be positive", parsedInflow.amount >= 0.0)

        val entityInflow = com.example.data.local.TransactionEntity(
            title = parsedInflow.title,
            amount = parsedInflow.amount,
            type = if (parsedInflow.direction.isInflow) "INCOME" else "EXPENSE",
            category = parsedInflow.category,
            paymentMethod = parsedInflow.paymentMethod,
            date = "2026-09-24",
            eventType = parsedInflow.transactionType.name
        )
        assertEquals(250.0, entityInflow.amount, 0.001)
        assertTrue(entityInflow.isInflow)
        assertEquals(250.0, entityInflow.ledgerAmount, 0.001)

        val parsedOutflow = TransactionParser.parse("₹500 paid for groceries")
        assertEquals(500.0, parsedOutflow.amount, 0.001)
        assertTrue("Stored amount must be positive", parsedOutflow.amount >= 0.0)

        val entityOutflow = com.example.data.local.TransactionEntity(
            title = parsedOutflow.title,
            amount = parsedOutflow.amount,
            type = if (parsedOutflow.direction.isInflow) "INCOME" else "EXPENSE",
            category = parsedOutflow.category,
            paymentMethod = parsedOutflow.paymentMethod,
            date = "2026-09-24",
            eventType = parsedOutflow.transactionType.name
        )
        assertEquals(500.0, entityOutflow.amount, 0.001)
        assertTrue(entityOutflow.isOutflow)
        assertEquals(-500.0, entityOutflow.ledgerAmount, 0.001)
    }

    // =========================================================================
    // SECTION: UNIVERSAL UPI & SMS PARSER TESTS (USER REQ SPECIFIC)
    // =========================================================================

    @Test
    fun `test explicit inflow - Received 2000 from Ramesh via UPI`() {
        val msg = "Received ₹2,000 from Ramesh via UPI"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("credit", result.type)
        assertTrue(result.isInflow)
        assertFalse(result.isOutflow)
        assertEquals(2000.0, result.amount, 0.001)
        assertEquals(2000.0, result.ledgerAmount, 0.001)
        assertEquals("Ramesh", result.merchant)
        assertEquals("UPI", result.paymentMethod)
    }

    @Test
    fun `test explicit outflow - Paid 450 to Rahul via Google Pay`() {
        val msg = "Paid ₹450 to Rahul via Google Pay"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("debit", result.type)
        assertTrue(result.isOutflow)
        assertFalse(result.isInflow)
        assertEquals(450.0, result.amount, 0.001)
        assertEquals(-450.0, result.ledgerAmount, 0.001)
        assertEquals("Rahul", result.merchant)
        assertEquals("UPI", result.paymentMethod)
    }

    @Test
    fun `test explicit outflow - You paid 450 to Rahul via Google Pay`() {
        val msg = "You paid ₹450 to Rahul via Google Pay"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("debit", result.type)
        assertTrue(result.isOutflow)
        assertEquals(450.0, result.amount, 0.001)
        assertEquals("Rahul", result.merchant)
    }

    @Test
    fun `test credit matcher - credited with Rs 1500`() {
        val msg = "Your a/c is credited with Rs 1500 on 24-Sep-26"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("credit", result.type)
        assertTrue(result.isInflow)
        assertEquals(1500.0, result.amount, 0.001)
        assertEquals(1500.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `test credit matcher - 5000 deposited in your ac`() {
        val msg = "₹5,000 deposited in your a/c on 24-Sep-26"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("credit", result.type)
        assertTrue(result.isInflow)
        assertEquals(5000.0, result.amount, 0.001)
        assertEquals(5000.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `test debit matcher - debited for Rs 750`() {
        val msg = "A/c XX1234 debited for Rs 750 at Grocery Mart"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("debit", result.type)
        assertTrue(result.isOutflow)
        assertEquals(750.0, result.amount, 0.001)
        assertEquals(-750.0, result.ledgerAmount, 0.001)
    }

    @Test
    fun `test debit matcher - debited by Rs 1200`() {
        val msg = "Your a/c is debited by Rs 1,200 via UPI"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("debit", result.type)
        assertTrue(result.isOutflow)
        assertEquals(1200.0, result.amount, 0.001)
    }

    @Test
    fun `test debit matcher - Transferred 1000 to vendor`() {
        val msg = "Transferred ₹1,000 to vendor via UPI"
        val result = TransactionParser.parse(msg)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertEquals("debit", result.type)
        assertTrue(result.isOutflow)
        assertEquals(1000.0, result.amount, 0.001)
        assertEquals(-1000.0, result.ledgerAmount, 0.001)
    }

    // =========================================================================
    // SECTION: PERSONAL TRANSFER SPECIFIC TEST SUITE
    // =========================================================================

    @Test
    fun `test 1 - person sent transfer 750 sent to Rahul via UPI`() {
        val input = "₹750 sent to Rahul via UPI"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue("Direction must be outflow", result.isOutflow)
        assertFalse("Direction must not be inflow", result.isInflow)
        assertEquals("Personal Transfer", result.category)
        assertEquals(750.0, result.amount, 0.001)
        assertEquals(-750.0, result.ledgerAmount, 0.001)
        assertEquals("Rahul", result.merchant)
    }

    @Test
    fun `test 2 - person received transfer 1500 received from Rahul via UPI`() {
        val input = "₹1500 received from Rahul via UPI"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue("Direction must be inflow", result.isInflow)
        assertFalse("Direction must not be outflow", result.isOutflow)
        assertEquals("Personal Transfer", result.category)
        assertEquals(1500.0, result.amount, 0.001)
        assertEquals(1500.0, result.ledgerAmount, 0.001)
        assertEquals("Rahul", result.merchant)
    }

    @Test
    fun `test 3 - merchant payment remains Food Paid 450 to Swiggy`() {
        val input = "Paid ₹450 to Swiggy"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Food", result.category)
        assertNotEquals("Personal Transfer", result.category)
        assertEquals(450.0, result.amount, 0.001)
        assertEquals("Swiggy", result.merchant)
    }

    @Test
    fun `test 4 - merchant payment remains Shopping Paid 1200 to Amazon`() {
        val input = "Paid ₹1200 to Amazon"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Shopping", result.category)
        assertNotEquals("Personal Transfer", result.category)
        assertEquals(1200.0, result.amount, 0.001)
        assertEquals("Amazon India", result.merchant)
    }

    @Test
    fun `test 5 - subscription remains Subscription Paid 699 to Netflix`() {
        val input = "Paid ₹699 to Netflix"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Subscription", result.category)
        assertNotEquals("Personal Transfer", result.category)
        assertEquals(699.0, result.amount, 0.001)
        assertEquals("Netflix", result.merchant)
    }

    @Test
    fun `test 6 - bill payment remains Bills Paid 1500 to Airtel`() {
        val input = "Paid ₹1500 to Airtel"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Bills", result.category)
        assertNotEquals("Personal Transfer", result.category)
        assertEquals(1500.0, result.amount, 0.001)
        assertEquals("Airtel", result.merchant)
    }

    @Test
    fun `test 6b - bill payment utility Paid 2000 electricity bill`() {
        val input = "Paid ₹2000 electricity bill"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Bills", result.category)
        assertNotEquals("Personal Transfer", result.category)
        assertEquals(2000.0, result.amount, 0.001)
    }

    @Test
    fun `test 7 - received from person Received 5000 from Rahul`() {
        val input = "Received ₹5000 from Rahul"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isInflow)
        assertEquals("Personal Transfer", result.category)
        assertNotEquals("Salary", result.category)
        assertNotEquals("Refund", result.category)
        assertEquals(5000.0, result.amount, 0.001)
        assertEquals(5000.0, result.ledgerAmount, 0.001)
        assertEquals("Rahul", result.merchant)
    }

    @Test
    fun `test 8 - person sent transfer Paid 500 to Rahul`() {
        val input = "Paid ₹500 to Rahul"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Personal Transfer", result.category)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals("Rahul", result.merchant)
    }

    @Test
    fun `test 9 - person sent transfer Sent 500 to Rahul`() {
        val input = "Sent ₹500 to Rahul"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Personal Transfer", result.category)
        assertEquals(500.0, result.amount, 0.001)
        assertEquals("Rahul", result.merchant)
    }

    @Test
    fun `test 10 - person sent transfer Paid 1000 to Anu`() {
        val input = "Paid ₹1,000 to Anu"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Personal Transfer", result.category)
        assertEquals(1000.0, result.amount, 0.001)
        assertEquals("Anu", result.merchant)
    }

    @Test
    fun `test 11 - person sent transfer Transferred 2000 to John`() {
        val input = "Transferred ₹2000 to John"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isOutflow)
        assertEquals("Personal Transfer", result.category)
        assertEquals(2000.0, result.amount, 0.001)
        assertEquals("John", result.merchant)
    }

    @Test
    fun `test 12 - person received transfer UPI transfer from Arjun`() {
        val input = "UPI transfer ₹3000 received from Arjun"
        val result = TransactionParser.parse(input)
        assertEquals(ParserConfidence.HIGH_CONFIDENCE, result.confidence)
        assertTrue(result.isInflow)
        assertEquals("Personal Transfer", result.category)
        assertEquals(3000.0, result.amount, 0.001)
        assertEquals("Arjun", result.merchant)
    }

    @Test
    fun `test 13 - case insensitive variations`() {
        val lowerSent = TransactionParser.parse("sent ₹500 to Rahul")
        assertEquals("Personal Transfer", lowerSent.category)
        assertTrue(lowerSent.isOutflow)
        assertEquals(500.0, lowerSent.amount, 0.001)
        assertEquals("Rahul", lowerSent.merchant)

        val upperSent = TransactionParser.parse("SENT ₹500 TO RAHUL")
        assertEquals("Personal Transfer", upperSent.category)
        assertTrue(upperSent.isOutflow)
        assertEquals(500.0, upperSent.amount, 0.001)
        assertTrue(upperSent.merchant.equals("RAHUL", ignoreCase = true))

        val mixedReceived = TransactionParser.parse("Received ₹1000 from Priya")
        assertEquals("Personal Transfer", mixedReceived.category)
        assertTrue(mixedReceived.isInflow)
        assertEquals(1000.0, mixedReceived.amount, 0.001)
        assertEquals("Priya", mixedReceived.merchant)

        val caseReceived = TransactionParser.parse("received ₹1000 FROM priya")
        assertEquals("Personal Transfer", caseReceived.category)
        assertTrue(caseReceived.isInflow)
        assertEquals(1000.0, caseReceived.amount, 0.001)
        assertTrue(caseReceived.merchant.equals("priya", ignoreCase = true))
    }
}
