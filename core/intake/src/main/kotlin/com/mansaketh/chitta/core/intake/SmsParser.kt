package com.mansaketh.chitta.core.intake

/**
 * Reads a bank / wallet SMS and works out what happened: how much, in or out, who with, a suggested category, a title, and which
 * account / card / wallet it was. A port of the old Paddhu `smsTransactionParser.dart`, which matches Ping and the Python reference
 * that produced the expected answers in the test fixtures. It is rule based and does not learn; the learning part is the titles memory.
 */
data class ParsedSms(
    /** Always positive. */
    val amountPaise: Long,
    /** true = money out, false = money in. */
    val isExpense: Boolean,
    /** Cleaned merchant / person / UPI handle as found in the SMS, "" when the SMS does not say. */
    val merchant: String,
    /** A credit-card bill payment acknowledgement. The bank-side debit is a separate SMS, so these are not added as entries. */
    val cardPayment: Boolean,
    /** Rule-based label ("dining", "groceries" ... "income"), "" when unknown. */
    val category: String,
    /** Suggested title, at most 60 characters. */
    val title: String,
    /** Key like "sbi:account:6919", see [Instrument]. */
    val instrumentKey: String,
) {
    /** "SBI ••6919" style name, or null when the SMS does not say which account it was. */
    val instrumentName: String? get() = Instrument.label(instrumentKey)
}

object SmsParser {
    /** Labels that map to one of the old Paddhu default categories. "health" and "transfer" have none. */
    val LABELS_WITH_CATEGORY: Set<String> =
        setOf("dining", "groceries", "shopping", "transit", "entertainment", "bills_fees", "gifts", "beauty", "work", "travel", "income")

    private val notTxn = Rx.of(
        "requested money|will be debited|is due|due on|not completed|if debited|passbook|has been initiated|e-mandate|mandate|\\botp\\b|one.?time password|verification code|balance enquiry|statement|total amt due|min amt due",
        ignoreCase = true,
    )
    private val amtRe = Rx.of("(?:rs\\.?|inr|\\u20b9)\\s*(\\d[\\d,]*(?:\\.\\d+)?)", ignoreCase = true)
    private val amtByRe = Rx.of("(?:debited|credited)\\s+(?:by|for|with)\\s+(\\d[\\d,]*(?:\\.\\d+)?)", ignoreCase = true)
    private val debitRe = Rx.of(
        "\\bdebit(?:ed)?\\b(?! card)|spent|\\bpaid\\b|withdrawn|\\bsent\\b|purchase|payment of|\\btxn of\\b|transaction number \\d+ for",
        ignoreCase = true,
    )
    private val creditRe = Rx.of("credited|received|refund|deposited", ignoreCase = true)
    private val cardBillRe = Rx.of(
        "payment of .{0,24}(?:has been|is) received (?:on|towards) your .{0,60}credit card|credit ?card payment|payment .{0,30}towards your .{0,40}credit card|thank you for payment .{0,40}credit card",
        ignoreCase = true,
    )
    private val debitThenCreditRe = Rx.of(";\\s*.+? credited\\.", ignoreCase = true)
    private val debitedRe = Rx.of("\\bdebited\\b", ignoreCase = true)

    private val mAxis = Rx.of("UPI/P2[MA]/\\d+/(.+?)\\s+Not you", ignoreCase = true)
    private val mSbi = Rx.of("(?:transfer|trf)\\s+to\\s+(.+?)\\s+Ref\\s?no", ignoreCase = true)
    private val mFrom = Rx.of("\\bfrom\\s+([A-Za-z][A-Za-z .&']+?)\\.\\s*UPI", ignoreCase = true)
    private val mIciciOn = Rx.of("\\bon \\d{1,2}-[A-Za-z]{3}-\\d{2,4} on (.+?)\\s*(?:-\\s*)?\\.", ignoreCase = true)
    private val mAtSign = Rx.of("@\\s*([A-Za-z0-9 .*&\\-]+?)\\s+\\d{2}-\\d{2}-\\d{2,4}", ignoreCase = true)
    private val mAt = Rx.of("\\bat\\s+(.+?)(?:\\s+on\\s+\\d|\\s+on your\\b|\\s*\\.\\s+(?:Avl|Available))", ignoreCase = true)
    private val mIciciSemi = Rx.of(";\\s*(.+?)\\s+credited\\.", ignoreCase = true)
    private val mUpiDash = Rx.of("UPI-\\d+-([A-Za-z0-9 .&]+?)(?:\\.|\\s*To dispute|\\z)", ignoreCase = true)
    private val mUsing = Rx.of("using\\s+([A-Za-z ]+?)\\s+is successful", ignoreCase = true)
    private val mAgainst = Rx.of("against\\s+([A-Za-z ]+?)(?:,|\\.)", ignoreCase = true)
    private val mInfoDash = Rx.of("\\bInfo\\s*-\\s*([A-Za-z][A-Za-z ]+)", ignoreCase = true)
    private val mVpa = Rx.of("to VPA\\s+([A-Za-z0-9._\\-]+)@", ignoreCase = true)
    private val mInfo = Rx.of("Info[:\\-]?\\s*([A-Za-z]+)[*\\-]", ignoreCase = true)
    private val mOrder = Rx.of("\\b(zepto|swiggy|zomato|blinkit|amazon|flipkart|myntra)\\b", ignoreCase = true)
    private val atmRe = Rx.of("withdrawn at .{0,40}\\bATM\\b|\\bATM\\b.{0,30}withdrawn|cash withdrawal", ignoreCase = true)
    private val vpaTailRe = Rx.of("(?:[-.]\\d{4,}.*|\\.(?:payu|rzp|cc)\\b.*)\\z", ignoreCase = true)
    private val vpaDigitsRe = Rx.of("\\d{4,}\\z")
    private val vpaNoiseRe = Rx.of("^(?:[\\d.\\-]+|gpay-\\d+|paytmqr\\d*[a-z0-9]*|q\\d+)\\z", ignoreCase = true)
    private val terminalRe = Rx.of("^(?=.*\\d)[A-Za-z0-9]{6,10}\\z")
    private val spacesRe = Rx.of("\\s+")
    private val creditIncomeRe = Rx.of("salary|interest|dividend|cashback|payout", ignoreCase = true)

    /** First match wins, so the order matters. */
    private val categoryRules: List<Pair<String, Regex>> = listOf(
        "transfer" to Rx.of("bank account|own account|self transfer|\\bidfc first bank\\b", ignoreCase = true),
        "travel" to Rx.of("irctc|redbus|abhibus|makemytrip|goibibo|cleartrip|yatra|indigo|air ?india|vistara|akasa|oyo\\b|lodge|resort|airbnb|trainman|ixigo", ignoreCase = true),
        "transit" to Rx.of("\\buber\\b|\\bola\\b|rapido|metro|fuel|petrol|diesel|hpcl|bpcl|iocl|fastag|tsrtc|apsrtc|parking|\\btoll\\b|namma yatri", ignoreCase = true),
        "entertainment" to Rx.of("netflix|hotstar|spotify|prime video|bookmyshow|\\bpvr\\b|\\binox\\b|youtube|jiocinema|sonyliv|zee5|steam|playstation|play ?store|google play|cinema|movie", ignoreCase = true),
        "bills_fees" to Rx.of("\\bjio\\b|jioinfo|airtel|vodafone|\\bvi\\b|bsnl|tata ?sky|tata ?play|\\bdth\\b|electric|bescom|tsspdcl|tgspdcl|apspdcl|water board|broadband|fibernet|insurance|\\blic\\b|premium|\\bemi\\b|\\bfees?\\b|charges|bil\\*|billdesk|\\bbill\\b|recharge|\\bloan\\b|\\brent\\b", ignoreCase = true),
        "beauty" to Rx.of("nykaa|salon|parlou?r|\\bspa\\b|beauty|cosmetic|barber|grooming|lakme|urban company", ignoreCase = true),
        "gifts" to Rx.of("\\bgifts?\\b|florist|flowers|archies", ignoreCase = true),
        "work" to Rx.of("github|\\baws\\b|amazon web|google cloud|digitalocean|notion|linkedin|openai|anthropic|godaddy|hosting|coworking|\\bzoom\\b", ignoreCase = true),
        "groceries" to Rx.of("zepto|blinkit|instamart|bigbasket|d-?mart|reliance smart|smart bazaar|supermarket|fruits?|vegetable|kirana|provision|grocer|ksheera|dairy|\\bmilk\\b|ratnadeep|spencer|more retail|\\bsmart \\d+", ignoreCase = true),
        "dining" to Rx.of("swiggy|zomato|food|restaurant|cafe|chai|\\btea\\b|bakery|biryani|pizza|\\bkfc\\b|mcdonald|domino|burger|momo|frankie|sweets|juice|ice ?cream|kitchen|dhaba|tiffin|canteen|\\bmess\\b|coffee|\\bccd\\b|starbucks|eatery|brew|hotel", ignoreCase = true),
        "shopping" to Rx.of("amazon|flipkart|myntra|ajio|meesho|tanishq|fancy|bangles|\\bstores?\\b|shoe|mobiles?|poorvika|ikea|lenskart|croma|decathlon|jewel|textile|clothing|fashion|boutique|saravana|pothys|trends|ecommerce|fsn|\\bshop\\b", ignoreCase = true),
        "health" to Rx.of("pharmacy|medplus|apollo|medical|hospital|clinic|diagnostic|pharma|1mg|netmeds|dental", ignoreCase = true),
    )

    /** Same as Python's s.strip(chars). */
    private fun stripChars(s: String, chars: String): String {
        var start = 0
        var end = s.length
        while (start < end && chars.indexOf(s[start]) >= 0) start++
        while (end > start && chars.indexOf(s[end - 1]) >= 0) end--
        return s.substring(start, end)
    }

    private fun clean(s: String) = stripChars(s.replace(spacesRe, " "), " .-*")

    private fun merchantOf(body: String): String {
        if (atmRe.containsMatchIn(body)) return "ATM withdrawal"
        val first = listOf(mAxis, mSbi, mFrom, mIciciOn, mAtSign, mAt, mIciciSemi, mUpiDash, mUsing, mAgainst, mInfoDash)
        for (rx in first) {
            val m = rx.find(body)
            if (m != null) {
                val v = clean(m.groupValues[1])
                if (v.isNotEmpty() && !terminalRe.containsMatchIn(v)) return v
            }
        }
        var m = mVpa.find(body)
        if (m != null) {
            val h = m.groupValues[1]
            if (vpaNoiseRe.containsMatchIn(h)) return ""
            return clean(h.replaceFirst(vpaTailRe, "").replaceFirst(vpaDigitsRe, ""))
        }
        m = mInfo.find(body)
        if (m != null) {
            val v = clean(m.groupValues[1])
            return if (v.uppercase() == "BIL") "BillDesk" else v
        }
        m = mOrder.find(body)
        if (m != null) return clean(m.groupValues[1])
        return ""
    }

    private fun categorize(isExpense: Boolean, merchant: String, body: String): String {
        if (!isExpense) return if (creditIncomeRe.containsMatchIn(body)) "income" else ""
        for ((label, rx) in categoryRules) if (rx.containsMatchIn(merchant)) return label
        return ""
    }

    /** The category label the keyword rules suggest for a merchant name ("" when none). Used for payments that do not come as SMS (UPI apps). */
    fun suggestCategoryLabel(isExpense: Boolean, merchant: String): String = categorize(isExpense, merchant, merchant)

    private fun titleCase(s: String): String {
        if (s == s.uppercase() || s == s.lowercase()) {
            return s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.substring(0, 1).uppercase() + it.substring(1).lowercase() }
        }
        return s
    }

    private val senderIdRe = Rx.of("^[A-Za-z]{2}-[A-Za-z0-9]{3,12}(?:-[A-Za-z])?\\z")
    private val bareSenderRe = Rx.of("^[A-Za-z][A-Za-z0-9]{4,11}\\z")

    /** True for sender IDs the way Android shows them for bank SMS ("AX-AXISBK-S", "VM-SBIUPI", "JAJIOPAY"), false for phone numbers and contact names with spaces. */
    fun looksLikeSenderId(sender: String): Boolean {
        val s = sender.trim()
        return senderIdRe.containsMatchIn(s) || bareSenderRe.containsMatchIn(s)
    }

    /** Null when the SMS is not a completed money movement (OTPs, requests, reminders, statements, marketing...). */
    fun parse(sender: String, body: String): ParsedSms? {
        val instrument = Instrument.key(Instrument.parse(sender, body))

        if (cardBillRe.containsMatchIn(body)) {
            val a0 = amtRe.find(body)
            return ParsedSms(a0?.let { Rupees.toPaise(it.groupValues[1]) } ?: 0L, true, "", true, "", "Card bill payment", instrument)
        }
        if (notTxn.containsMatchIn(body)) return null

        val am = amtByRe.find(body) ?: amtRe.find(body) ?: return null
        val amount = Rupees.toPaise(am.groupValues[1]) ?: return null

        val d = debitRe.find(body)
        val c = creditRe.find(body)
        if (d == null && c == null) return null

        var isExpense = if (d != null && c != null) d.range.first < c.range.first else d != null
        // "... debited for Rs 25.00 ...; Hyderabad Metro credited." is still a debit
        if (debitedRe.containsMatchIn(body) && debitThenCreditRe.containsMatchIn(body)) isExpense = true

        val merchant = merchantOf(body)
        val category = categorize(isExpense, merchant, body)

        var title = if (merchant.isNotEmpty()) titleCase(merchant) else if (isExpense) "Payment" else "Money received"
        if (title.length > 60) title = title.substring(0, 60)
        title = title.trim()

        return ParsedSms(amount, isExpense, merchant, false, category, title, instrument)
    }
}
