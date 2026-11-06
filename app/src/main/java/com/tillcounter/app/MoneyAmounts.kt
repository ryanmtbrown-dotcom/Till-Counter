package com.tillcounter.app

/** Signed entries for non-cash tender categories. The base till remains non-negative. */
internal object MoneyAmounts {
    fun display(raw: String): String {
        val negative = raw.startsWith("-")
        val number = raw.removePrefix("-").ifBlank { "0.00" }
        return (if (negative) "-$" else "$") + number
    }

    fun parseCents(raw: String): Long? {
        if (raw.isBlank() || raw == "-") return null
        val negative = raw.startsWith("-")
        val number = raw.removePrefix("-")
        if (!number.matches(Regex("""\d+(\.\d{0,2})?"""))) return null
        val parts = number.split('.', limit = 2)
        val dollars = parts[0].toLongOrNull() ?: return null
        val fraction = if (parts.size == 1) 0L else when (parts[1].length) {
            0 -> 0L
            1 -> parts[1].toLong() * 10
            else -> parts[1].toLong()
        }
        if (dollars > (Long.MAX_VALUE - fraction) / 100L) return null
        val cents = dollars * 100L + fraction
        return if (negative) -cents else cents
    }
}
