package com.mansaketh.chitta.data

import android.content.Context
import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.domain.Sip
import com.mansaketh.chitta.core.intake.AmfiNav
import com.mansaketh.chitta.core.intake.AmfiReading
import com.mansaketh.chitta.core.intake.AmfiScheme
import com.mansaketh.chitta.core.intake.FundMatchIndex
import com.mansaketh.chitta.core.intake.FundMatchResult
import com.mansaketh.chitta.core.intake.FundPrices
import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.di.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches AMFI's public NAV file and puts the prices on the funds you hold (Phase 8d, master section 8).
 *
 * The only thing sent is a plain GET for the file: no holdings, name or account number leave the phone. A fetch that fails, or a file that does not
 * read, changes nothing: the old prices stay, with their dates. The last good file is kept on the phone (not in the book, not in backups) so the
 * "choose the matching scheme" list works offline.
 */
@Singleton
class FundPriceService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assets: AssetsRepository,
    private val prefs: UiPrefs,
    private val clock: Clock,
) {
    private val lock = Mutex()
    private val cacheFile get() = File(context.filesDir, CACHE_NAME)

    /** What a refresh did, in numbers the screen can say in words. */
    data class Outcome(val ok: Boolean, val note: String, val priced: Int = 0, val unlinked: Int = 0)

    /** On app open: only when the switch is on, there are funds, the last good fetch is over 12 hours old, and the last try was over 30 minutes ago. */
    suspend fun refreshIfDue() {
        if (!prefs.fundPricesAuto.value) return
        val s = prefs.fundPriceStatus.value
        val now = clock.nowMs()
        if (now - s.okMs < DUE_AFTER_MS || now - s.tryMs < RETRY_AFTER_MS) return
        refresh()
    }

    /** Fetches, matches and applies. Never throws: a failure becomes a note and the old prices stay. */
    suspend fun refresh(): Outcome = lock.withLock {
        val now = clock.nowMs()
        val funds = assets.funds().filter { !it.isNps }
        if (funds.isEmpty()) return@withLock Outcome(true, "No funds to price.")
        val outcome = try {
            val text = withContext(Dispatchers.IO) { download() }
            when (val reading = withContext(Dispatchers.Default) { AmfiNav.read(text) }) {
                is AmfiReading.Refused -> Outcome(false, reading.reason)
                is AmfiReading.Ready -> {
                    withContext(Dispatchers.IO) { saveCache(text) }
                    val plan = withContext(Dispatchers.Default) { FundPrices.plan(funds, reading) }
                    plan.updates.forEach { applyScheme(it.fund, it.scheme, it.applyPrice) }
                    val unlinked = plan.needPick.size + plan.noMatch.size
                    val updated = plan.updates.count { it.applyPrice }
                    Outcome(
                        true, "Prices as of ${display(reading.newestDate)}. $updated ${if (updated == 1) "fund" else "funds"} priced" +
                            (if (unlinked > 0) ", $unlinked ${if (unlinked == 1) "needs" else "need"} a matching scheme chosen." else "."),
                        updated, unlinked,
                    )
                }
            }
        } catch (e: Exception) {
            Outcome(false, "Could not reach the price file (${e.javaClass.simpleName}). Old prices are kept.")
        }
        val old = prefs.fundPriceStatus.value
        prefs.setFundPriceStatus(FundPriceStatus(okMs = if (outcome.ok) now else old.okMs, tryMs = now, note = outcome.note))
        outcome
    }

    /** What to offer for [fund]: one sure match, a pick list, or nothing. Reads the saved file, so it works offline. Empty before any fetch. */
    suspend fun candidates(fund: MutualFund): List<AmfiScheme> = withContext(Dispatchers.Default) {
        val text = readCache() ?: return@withContext emptyList()
        val reading = AmfiNav.read(text) as? AmfiReading.Ready ?: return@withContext emptyList()
        when (val m = FundMatchIndex(reading.schemes).match(fund)) {
            is FundMatchResult.Sure -> listOf(m.scheme)
            is FundMatchResult.Pick -> m.candidates
            FundMatchResult.None -> emptyList()
        }
    }

    /** Saves the person's choice on the fund (its scheme code, and the ISIN if it had none), then prices it from that scheme. */
    suspend fun link(fund: MutualFund, scheme: AmfiScheme) = lock.withLock {
        applyScheme(fund, scheme, FundPrices.isNewer(scheme, fund))
    }

    /** Writes the match, then the price when it is not older than the one the fund has, then credits any SIP instalments the new price date covers. */
    private suspend fun applyScheme(fund: MutualFund, scheme: AmfiScheme, applyPrice: Boolean) {
        val isin = FundPrices.isinFor(scheme)
        if (!applyPrice) {
            // Only the match is kept; the fund's own newer price and date stay as they are.
            assets.setFundPrice(fund.id, fund.navText ?: scheme.nav.toPlainString(), fund.navDate ?: scheme.date.toString(), scheme.code, isin)
            return
        }
        assets.setFundPrice(fund.id, scheme.nav.toPlainString(), scheme.date.toString(), scheme.code, isin)
        val priced = fund.copy(navText = scheme.nav.toPlainString(), navDate = scheme.date.toString())
        val credited = Sip.credit(priced, scheme.nav, scheme.date)
        if (credited.sipThrough != priced.sipThrough || credited.sipCount != priced.sipCount) {
            assets.setFundSipEstimate(fund.id, credited.sipUnits, credited.sipInvestedPaise, credited.sipCount, credited.sipThrough)
        }
    }

    private fun download(): String {
        var last: Exception? = null
        for (url in URLS) {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 60_000
                c.setRequestProperty("User-Agent", "Chitta")
                try {
                    if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                    // Read with a size limit by hand: InputStream.readNBytes needs Android 13, and the app supports Android 8.
                    val out = java.io.ByteArrayOutputStream()
                    c.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) error("the file was larger than expected")
                        }
                    }
                    return out.toString(Charsets.UTF_8.name())
                } finally { c.disconnect() }
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("no address")
    }

    private fun saveCache(text: String) {
        val tmp = File(context.filesDir, "$CACHE_NAME.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); tmp.renameTo(cacheFile) }
    }

    private fun readCache(): String? = cacheFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)

    companion object {
        /** AMFI's own address moved from www.amfiindia.com to portal.amfiindia.com (read 14 Nov 2026); the old one still redirects, so it is the fallback. */
        val URLS = listOf("https://portal.amfiindia.com/spages/NAVAll.txt", "https://www.amfiindia.com/spages/NAVAll.txt")
        private const val CACHE_NAME = "amfi_navall.txt"
        private const val MAX_BYTES = 20 * 1024 * 1024
        const val DUE_AFTER_MS = 12 * 3_600_000L
        const val RETRY_AFTER_MS = 30 * 60_000L
        private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private fun display(d: LocalDate) = d.format(fmt)
    }
}
