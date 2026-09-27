package com.tillcounter.app

import android.content.Context

class TillSettings(context: Context) {
    private val prefs = context.getSharedPreferences("till_settings", Context.MODE_PRIVATE)

    val categories = listOf("Store Charges", "Gift Certificates", "Vendor Coupons", "Checks", "Loans")

    fun isEnabled(name: String): Boolean = prefs.getBoolean("enabled_" + key(name), true)

    fun setEnabled(name: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_" + key(name), enabled).apply()
    }

    fun baseTillCents(): Long = prefs.getLong("base_till_cents", 0L).coerceAtLeast(0L)

    fun setBaseTillCents(cents: Long) {
        prefs.edit().putLong("base_till_cents", cents.coerceAtLeast(0L)).apply()
    }

    private fun key(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
