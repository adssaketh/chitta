package com.mansaketh.chitta.core.intake

/**
 * Works out which bank account, credit card, debit card or wallet a transaction SMS is about, so the same money source always gets
 * the same key ("icici:account:2090", "axis:credit_card:1896"). A port of the old Paddhu `instrumentParser.dart`, which is a port of
 * Ping's `instrument.ts`; the three agree on keys. The import reads each message on its own ([parse]); [resolver] and [indexAll] tidy
 * a whole list together ("XX555" and "XX1555" are one account) and are kept so the fixtures can check the port end to end.
 */
enum class InstrumentKind(val wire: String) {
    Account("account"), CreditCard("credit_card"), DebitCard("debit_card"), Card("card"), Wallet("wallet"), Unknown("unknown"),
}

data class InstrumentRef(val issuer: String, val kind: InstrumentKind, val last: String)

object Instrument {
    private val issuerBySender: List<Pair<Regex, String>> = listOf(
        Rx.of("^(SBIUPI|SBIINB|SBIPSG|SBMSG|SBIBNK)\\z") to "sbi",
        Rx.of("^(SBICRD|SBICARD)\\z") to "sbi_card",
        Rx.of("^ICICI") to "icici",
        Rx.of("^AXIS") to "axis",
        Rx.of("^(FEDBNK|FEDERL|FEDRAL)\\z") to "federal",
        Rx.of("^HDFC") to "hdfc",
        Rx.of("^IDFCFB") to "idfcfb",
        Rx.of("^AUBANK\\z") to "au",
        Rx.of("^YESBNK\\z") to "yes",
        Rx.of("^(JIOPAY|JIOINF|JIOFIN)\\z") to "jiopay",
        Rx.of("^ZEPTON\\z") to "zepto",
        Rx.of("^(PAYTM|PYTM)") to "paytm",
        Rx.of("^(PHONEPE|PHONPE)\\z") to "phonepe",
    )

    private val issuerByBody: List<Pair<Regex, String>> = listOf(
        Rx.of("\\bICICI\\b", ignoreCase = true) to "icici",
        Rx.of("\\bAxis\\b", ignoreCase = true) to "axis",
        Rx.of("\\bFederal Bank\\b", ignoreCase = true) to "federal",
        Rx.of("\\bHDFC\\b", ignoreCase = true) to "hdfc",
        Rx.of("\\bAU Small|AU Bank\\b", ignoreCase = true) to "au",
        Rx.of("\\bYes Bank\\b", ignoreCase = true) to "yes",
        Rx.of("-\\s?SBI\\b|\\bSBI\\b") to "sbi",
    )

    private val walletRe = Rx.of(
        "(zepto cash|amazon pay(?: balance)?|paytm wallet|phonepe wallet|mobikwik|freecharge|jio ?pay wallet|airtel money|mi pay|simpl|lazypay|slice)",
        ignoreCase = true,
    )
    private val accountRe = Rx.of(
        "(?:a\\/?c(?:count)?|acct)\\.?\\s*(?:no\\.?)?\\s*(?:ending|ending with)?\\s*[:\\-]?\\s*(?:[Xx*]+|\\.{2,})?\\s*(\\d{3,})",
        ignoreCase = true,
    )
    private val cardRe = Rx.of(
        "(?:card|\\bcc\\b)\\s*(?:no\\.?)?\\s*(?:ending(?: with)?|xx+|x+|\\*+)?\\s*[:\\-]?\\s*(?:[Xx*]+)?\\s*(\\d{4})\\b",
        ignoreCase = true,
    )
    private val maskRe = Rx.of("[Xx*]{2,}(\\d{3,4})\\b")
    private val creditRe = Rx.of("credit\\s*card|\\bcc\\b\\s*(?:no|ending|xx|x+|\\*+|\\d)|sbi card", ignoreCase = true)
    private val debitRe = Rx.of("debit\\s*card", ignoreCase = true)
    private val cardWordRe = Rx.of("\\bcard\\b|credit\\s*card|debit\\s*card", ignoreCase = true)

    private val phoneRe = Rx.of("^\\+?[\\d\\s\\-()]{7,}\\z")
    private val businessRe = Rx.of("^[a-z0-9]{2}-([a-z0-9]{2,12})(?:-[a-z])?\\z", ignoreCase = true)

    private fun isPhoneNumber(value: String) = phoneRe.containsMatchIn(value.trim())

    /** "AX-AXISBK-S", "VM-AXISBK-S" and "JD-AXISBK-T" are all "AXISBK". Phone numbers have none. */
    private fun businessKey(sender: String): String? {
        val raw = sender.trim()
        if (raw.isEmpty() || isPhoneNumber(raw)) return null
        val m = businessRe.find(raw)
        return (m?.groupValues?.get(1) ?: raw).uppercase()
    }

    private fun knownSender(name: String) = issuerBySender.any { it.first.containsMatchIn(name) }

    /** "AX-AXISBK-S" -> "AXISBK"; also "JAJIOPAY" (a prefix without a dash) -> "JIOPAY" when the rest is a known sender. */
    private fun senderName(sender: String): String {
        val raw = sender.trim().uppercase()
        val dashed = (businessKey(raw) ?: raw).uppercase()
        if (dashed != raw || raw.contains('-')) return dashed
        if (!knownSender(raw) && raw.length > 5 && knownSender(raw.substring(2))) return raw.substring(2)
        return raw
    }

    private fun issuerOf(sender: String, body: String): String {
        val biz = senderName(sender)
        for ((rx, issuer) in issuerBySender) if (rx.containsMatchIn(biz)) return issuer
        for ((rx, issuer) in issuerByBody) if (rx.containsMatchIn(body)) return issuer
        return biz.lowercase()
    }

    private fun tail(digits: String) = if (digits.length > 4) digits.substring(digits.length - 4) else digits

    private val scapiaSenderRe = Rx.of("^FEDSCP")
    private val scapiaRe = Rx.of("\\bscapia\\b", ignoreCase = true)
    private val visaRe = Rx.of("\\bvisa\\b", ignoreCase = true)
    private val rupayRe = Rx.of("\\brupay\\b", ignoreCase = true)

    fun parse(sender: String, body: String): InstrumentRef {
        val issuer = issuerOf(sender, body)
        val text = body

        // Scapia (Federal Bank) SMS never print the card digits, the card network is the only clue.
        if (scapiaSenderRe.containsMatchIn(senderName(sender)) || scapiaRe.containsMatchIn(text)) {
            return InstrumentRef("scapia", InstrumentKind.CreditCard, if (visaRe.containsMatchIn(text)) "visa" else if (rupayRe.containsMatchIn(text)) "rupay" else "")
        }

        val isCredit = creditRe.containsMatchIn(text) || issuer == "sbi_card"
        val isDebit = debitRe.containsMatchIn(text)
        val wallet = walletRe.find(text)

        val acct = accountRe.find(text)
        val cardWord = cardWordRe.find(text)
        // "Axis Bank A/c XX6372 ... CreditCard Payment": the money left the account, so whichever comes first wins.
        val accountFirst = acct != null && (cardWord == null || acct.range.first < cardWord.range.first)
        if (!accountFirst && (isCredit || isDebit || cardWord != null)) {
            val m = cardRe.find(text) ?: (if (isCredit || isDebit) maskRe.find(text) else null)
            if (m != null) {
                val kind = if (isCredit) InstrumentKind.CreditCard else if (isDebit) InstrumentKind.DebitCard else InstrumentKind.Card
                return InstrumentRef(issuer, kind, tail(m.groupValues[1]))
            }
        }
        if (wallet != null && !accountRe.containsMatchIn(text)) {
            return InstrumentRef(wallet.groupValues[1].lowercase().replace(Rx.of("\\s+"), "_"), InstrumentKind.Wallet, "")
        }
        val a = acct ?: maskRe.find(text)
        if (a != null) return InstrumentRef(issuer, InstrumentKind.Account, tail(a.groupValues[1]))
        if (isCredit) return InstrumentRef(issuer, InstrumentKind.CreditCard, "")
        return InstrumentRef(issuer, InstrumentKind.Unknown, "")
    }

    fun key(r: InstrumentRef) = "${r.issuer}:${r.kind.wire}:${r.last}"

    private val issuerLabel = mapOf(
        "sbi" to "SBI", "sbi_card" to "SBI Card", "icici" to "ICICI", "axis" to "Axis", "federal" to "Federal", "hdfc" to "HDFC", "au" to "AU",
        "yes" to "Yes Bank", "idfc" to "IDFC First", "idfcfb" to "IDFC First", "scapia" to "Scapia", "jiopay" to "Jio Pay", "zepto" to "Zepto",
        "zepto_cash" to "Zepto Cash", "phonepe" to "PhonePe", "phonepe_wallet" to "PhonePe wallet", "mi_pay" to "Mi Pay",
        "amazon_pay_balance" to "Amazon Pay", "paytm" to "Paytm",
    )

    /** Short name shown for an instrument: "SBI ••6919", "ICICI card ••1000", "Zepto Cash". Null when the SMS does not say which one. */
    fun label(key: String): String? {
        val parts = key.split(':')
        val issuer = parts[0]
        val kind = parts.getOrElse(1) { "" }
        val last = parts.getOrElse(2) { "" }
        val name = issuerLabel[issuer] ?: issuer.uppercase()
        if (kind == "wallet") return name
        if (last.isEmpty()) return null
        val tag = when (kind) { "credit_card", "card" -> " card"; "debit_card" -> " debit"; else -> "" }
        return "$name$tag ••$last"
    }

    /** Issuer part of an instrument key as shown to people, or null when it is not one of the banks and wallets listed here. */
    fun knownIssuerLabel(key: String): String? = issuerLabel[key.substringBefore(':')]

    private val banks = setOf("sbi", "icici", "axis", "federal", "au", "yes", "hdfc", "idfc", "idfcfb")

    /**
     * Looks at every parsed SMS together and tidies the result: "XX090" and "XX2090" are the same account (3 vs 4 printed digits);
     * "Card 1000" and "Credit Card 1000" from the same bank are the same credit card; an SMS with no digits goes to that bank's only
     * account, if there is exactly one. Returns a function that gives the final key for each parsed SMS.
     */
    fun resolver(refs: List<InstrumentRef>): (InstrumentRef) -> String {
        val withDigits = refs.filter { it.last.isNotEmpty() }

        fun canon(r: InstrumentRef): InstrumentRef {
            if (r.last.isEmpty()) return r
            var out = r
            if (r.kind == InstrumentKind.Card) {
                val cc = withDigits.firstOrNull { it.issuer == r.issuer && it.kind == InstrumentKind.CreditCard && it.last.endsWith(r.last.takeLast(3)) }
                if (cc != null) out = out.copy(kind = InstrumentKind.CreditCard, last = if (cc.last.length >= out.last.length) cc.last else out.last)
            }
            // third-party senders (EPFO, a shop refund...) that name one of your accounts by its last digits belong to that bank
            if (out.kind == InstrumentKind.Account && out.issuer !in banks) {
                val cur = out
                val owner = withDigits.filter { it.kind == InstrumentKind.Account && it.issuer in banks && it.last == cur.last }.map { it.issuer }.toSet().toList()
                if (owner.size == 1) out = out.copy(issuer = owner[0])
            }
            val current = out
            val longer = withDigits.firstOrNull { it.issuer == current.issuer && it.kind == current.kind && it.last.length > current.last.length && it.last.endsWith(current.last) }
            return if (longer != null) out.copy(last = longer.last) else out
        }

        val known = LinkedHashMap<String, InstrumentRef>()
        for (r in withDigits) {
            val c = canon(r)
            known["${c.issuer}:${c.kind.wire}:${c.last}"] = c
        }
        fun accountsOf(issuer: String) = known.values.filter { it.issuer == issuer && it.kind == InstrumentKind.Account }

        return { r ->
            if (r.last.isNotEmpty()) key(canon(r))
            else if (r.kind == InstrumentKind.Unknown && accountsOf(r.issuer).size == 1) key(accountsOf(r.issuer)[0])
            else key(r)
        }
    }

    private val txnRe = Rx.of("debited|credited|spent|paid|payment|received|withdrawn|refund|transfer|balance", ignoreCase = true)
    private val otpRe = Rx.of("\\botp\\b|one.?time password|verification code", ignoreCase = true)

    /** Message id -> instrument key for every transaction-looking message in the list. OTPs and other messages are left out. */
    fun indexAll(messages: List<Triple<String, String, String>>): Map<String, String> {
        val parsed = messages.filter { (_, _, text) -> !otpRe.containsMatchIn(text) && txnRe.containsMatchIn(text) }
            .map { (id, sender, text) -> id to parse(sender, text) }
        val resolve = resolver(parsed.map { it.second })
        return parsed.associate { (id, ref) -> id to resolve(ref) }
    }
}
