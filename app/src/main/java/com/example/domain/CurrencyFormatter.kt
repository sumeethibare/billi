package com.example.domain

import com.example.data.model.CURRENCY_SYMBOL_INR
import com.example.data.model.DEFAULT_CURRENCY_CODE
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * INR-exclusive currency formatter using Indian Rupee grouping (₹1,24,500.00)
 * and exact integer paise (minor unit = 2 decimals) conversions.
 */
object CurrencyFormatter {

    private val indianLocale: Locale = Locale.Builder()
        .setLanguage("en")
        .setRegion("IN")
        .build()

    fun format(
        amountCents: Long,
        @Suppress("UNUSED_PARAMETER") currencyCode: String = DEFAULT_CURRENCY_CODE,
        showPlusSign: Boolean = false
    ): String {
        val sign = when {
            amountCents < 0L -> "-"
            amountCents > 0L && showPlusSign -> "+"
            else -> ""
        }
        val absRupees = abs(amountCents) / 100.0
        val nf = NumberFormat.getNumberInstance(indianLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$sign$CURRENCY_SYMBOL_INR${nf.format(absRupees)}"
    }

    fun formatCompact(amountCents: Long): String {
        val rupees = abs(amountCents) / 100.0
        val sign = if (amountCents < 0L) "-" else ""
        return when {
            rupees >= 100_000.0 -> String.format(Locale.US, "%s₹%.1fL", sign, rupees / 100_000.0)
            rupees >= 1_000.0 -> String.format(Locale.US, "%s₹%.1fK", sign, rupees / 1_000.0)
            else -> String.format(Locale.US, "%s₹%.0f", sign, rupees)
        }
    }

    fun formatInput(
        amountCents: Long,
        @Suppress("UNUSED_PARAMETER") currencyCode: String = DEFAULT_CURRENCY_CODE
    ): String {
        if (amountCents <= 0L) return ""
        val major = amountCents / 100
        val minor = amountCents % 100
        return if (minor == 0L) {
            major.toString()
        } else {
            "$major.${minor.toString().padStart(2, '0')}"
        }
    }

    fun parseToMinorUnits(
        input: String,
        @Suppress("UNUSED_PARAMETER") currencyCode: String = DEFAULT_CURRENCY_CODE
    ): Long? {
        val cleaned = input.trim().replace(",", "").replace(CURRENCY_SYMBOL_INR, "")
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (parsed < 0.0 || parsed.isNaN() || parsed.isInfinite()) return null
        return (parsed * 100.0).roundToLong()
    }

    /** Formats basis points (e.g., 3333 -> "33.33", 5000 -> "50") for percentage inputs. */
    fun formatPercentInput(bps: Long): String {
        if (bps <= 0L) return ""
        val whole = bps / 100L
        val frac = bps % 100L
        return if (frac == 0L) {
            whole.toString()
        } else {
            val trimmedFrac = frac.toString().padStart(2, '0').trimEnd('0')
            "$whole.$trimmedFrac"
        }
    }

    /** Parses a percentage text like "33.33" or "50" into basis points (0..10000). */
    fun parsePercentToBps(input: String): Long? {
        val cleaned = input.trim().replace("%", "")
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (parsed < 0.0 || parsed > 100.0 || parsed.isNaN() || parsed.isInfinite()) return null
        return (parsed * 100.0).roundToLong()
    }
}
