package com.tillcounter.app

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val stages = listOf("Store Charges", "Gift Certificates", "Vendor Coupons", "Checks", "Loans")
    private val entries = Array(5) { mutableListOf<Long>() }
    private val cashLabels = listOf("$100 bills", "$50 bills", "$20 bills", "$10 bills", "$5 bills", "$2 bills", "$1 bills", "$1 coins", "Half dollars", "Quarters", "Dimes", "Nickels", "Pennies")
    private val cashCents = longArrayOf(10000, 5000, 2000, 1000, 500, 200, 100, 100, 50, 25, 10, 5, 1)
    private val cashCounts = IntArray(cashLabels.size)
    private val currency = NumberFormat.getCurrencyInstance(Locale.US)
    private val handler = Handler(Looper.getMainLooper())
    private var stage = 0
    private var cashIndex = 0
    private var input = ""
    private var splashDone = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        restore(state)
        if (state == null || !splashDone) showSplash() else render()
    }

    private fun root(): FrameLayout = FrameLayout(this).apply {
        setBackgroundColor(Color.rgb(7, 26, 20))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun showSplash() {
        val root = root()
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.splash)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Till Counter splash"
        }, FrameLayout.LayoutParams(-1, -1))
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(214, 183, 107))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(110, 255, 255, 255))
        }
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(6), Gravity.BOTTOM).apply {
            setMargins(dp(28), 0, dp(28), dp(42))
        })
        setContentView(root)
        ObjectAnimator.ofInt(progress, "progress", 0, 1000).apply { duration = 6000; start() }
        handler.postDelayed({ splashDone = true; render() }, 6000)
    }

    private fun render() = when {
        stage < stages.size -> renderMoneyStage()
        stage == stages.size -> renderCashStage()
        else -> renderSummary()
    }

    private fun baseColumn(title: String, subtitle: String): LinearLayout {
        val outer = root()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        col.addView(label("TILL COUNTER", 16, true, Color.rgb(214, 183, 107)), matchWrap())
        col.addView(label(title, 30, true, Color.WHITE).apply { setPadding(0, dp(8), 0, 0) }, matchWrap())
        col.addView(label(subtitle, 15, false, Color.rgb(169, 184, 176)).apply { setPadding(0, dp(4), 0, dp(18)) }, matchWrap())
        scroll.addView(col, ViewGroup.LayoutParams(-1, -1))
        outer.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(outer)
        return col
    }

    private fun renderMoneyStage() {
        val list = entries[stage]
        val col = baseColumn(stages[stage], "Enter an amount. Tap + for another, or NEXT when finished.")

        val total = totalCard("RUNNING TOTAL", money(list.sum()))
        col.addView(total)

        if (list.isNotEmpty()) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), 0, dp(4), dp(12))
            }
            list.forEachIndexed { index, cents ->
                box.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label("${index + 1}.  ${money(cents)}", 16, false, Color.rgb(226, 231, 228)), LinearLayout.LayoutParams(0, dp(42), 1f))
                    addView(textAction("Remove") { list.removeAt(index); render() }, LinearLayout.LayoutParams(dp(92), dp(42)))
                })
            }
            col.addView(box, matchWrap())
        }

        col.addView(display(if (input.isBlank()) "$0.00" else "$$input"))
        moneyPad(col)

        val divider = View(this).apply { setBackgroundColor(Color.rgb(69, 72, 65)) }
        col.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(18); bottomMargin = dp(14)
        })

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(navButton("BACK", false) {
            if (stage > 0) {
                stage--
                input = ""
                render()
            }
        }, weightedButton(10))
        nav.addView(navButton(if (stage == stages.lastIndex) "NEXT: CASH" else "NEXT", true) {
            commitPendingMoney()
            stage++
            input = ""
            render()
        }, LinearLayout.LayoutParams(0, dp(60), 1.35f))
        col.addView(nav, matchWrap())
    }

    private fun commitPendingMoney(): Boolean {
        val cents = parseCents(input) ?: return false
        if (cents > 0) entries[stage].add(cents)
        input = ""
        return cents > 0
    }

    private fun renderCashStage() {
        val count = cashCounts[cashIndex]
        val col = baseColumn("Cash", "Enter the number of each denomination.")
        col.addView(label("${cashIndex + 1} of ${cashLabels.size}", 14, true, Color.rgb(214, 183, 107)))
        col.addView(label(cashLabels[cashIndex], 28, true, Color.WHITE).apply { setPadding(0, dp(16), 0, dp(6)) })
        col.addView(label("Count: $count", 24, true, Color.WHITE))
        col.addView(label("Value: ${money(count.toLong() * cashCents[cashIndex])}", 18, false, Color.rgb(169, 184, 176)).apply { setPadding(0, 0, 0, dp(14)) })
        col.addView(display(if (input.isBlank()) count.toString() else input))
        countPad(col)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        nav.addView(action("BACK") {
            commitCash()
            if (cashIndex > 0) cashIndex-- else stage = stages.size - 1
            input = ""; render()
        }, weightedButton(10))
        nav.addView(action(if (cashIndex == cashLabels.lastIndex) "FINISH" else "NEXT") {
            commitCash()
            if (cashIndex == cashLabels.lastIndex) stage = stages.size + 1 else cashIndex++
            input = ""; render()
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        col.addView(nav, matchWrap())
    }

    private fun renderSummary() {
        val categoryTotals = entries.map { it.sum() }
        val cashTotal = cashCounts.indices.sumOf { cashCounts[it].toLong() * cashCents[it] }
        val grand = categoryTotals.sum() + cashTotal
        val col = baseColumn("Till Summary", "Copy these totals to your till form.")
        stages.forEachIndexed { index, name -> col.addView(summaryRow(name, categoryTotals[index])) }
        col.addView(summaryRow("Cash", cashTotal))
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(16, 42, 33))
            addView(label("TOTAL", 15, true, Color.rgb(214, 183, 107)))
            addView(label(money(grand), 34, true, Color.WHITE))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        col.addView(label("Cash breakdown", 20, true, Color.WHITE).apply { setPadding(0, dp(22), 0, dp(8)) })
        cashCounts.indices.filter { cashCounts[it] > 0 }.forEach {
            col.addView(label("${cashLabels[it]} × ${cashCounts[it]} = ${money(cashCounts[it].toLong() * cashCents[it])}", 16, false, Color.WHITE))
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(22), 0, dp(10)) }
        nav.addView(action("BACK") { stage = stages.size; cashIndex = cashLabels.lastIndex; render() }, weightedButton(10))
        nav.addView(action("NEW COUNT") { resetAll(); render() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        col.addView(nav, matchWrap())
    }

    private fun moneyPad(col: LinearLayout) {
        val rows = listOf(
            listOf("7", "8", "9", "⌫"),
            listOf("4", "5", "6", "C"),
            listOf("1", "2", "3", "+"),
            listOf("00", "0", ".", "+")
        )
        rows.forEachIndexed { rowIndex, keys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEachIndexed { keyIndex, key ->
                val isPlus = key == "+"
                if (isPlus && rowIndex == 3) {
                    row.addView(Space(this), keyParams())
                } else {
                    val button = when {
                        isPlus -> plusButton { commitPendingMoney(); render() }
                        key == "C" || key == "⌫" -> editKey(key) { moneyKey(key) }
                        else -> numberKey(key) { moneyKey(key) }
                    }
                    row.addView(button, keyParams())
                }
            }
            col.addView(row, matchWrap())
        }
    }

    private fun countPad(col: LinearLayout) {
        listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf("C", "0", "⌫")).forEach { keys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEach { key -> row.addView(action(key) { countKey(key) }, keyParams()) }
            col.addView(row, matchWrap())
        }
    }

    private fun moneyKey(key: String) {
        when (key) {
            "C" -> input = ""
            "⌫" -> input = input.dropLast(1)
            "." -> if (!input.contains('.')) input = if (input.isBlank()) "0." else "$input."
            else -> {
                if (input.contains('.') && input.substringAfter('.').length >= 2) return
                if (input.length < 9) input += key
            }
        }
        render()
    }

    private fun countKey(key: String) {
        when (key) {
            "C" -> input = ""
            "⌫" -> input = input.dropLast(1)
            else -> if (input.length < 5) input += key
        }
        render()
    }

    private fun parseCents(raw: String): Long? {
        if (raw.isBlank()) return null
        val parts = raw.split('.', limit = 2)
        val dollars = parts[0].ifBlank { "0" }.toLongOrNull() ?: return null
        val cents = if (parts.size == 1) 0 else when (parts[1].length) {
            0 -> 0
            1 -> parts[1].toIntOrNull()?.times(10) ?: return null
            else -> parts[1].take(2).toIntOrNull() ?: return null
        }
        return dollars * 100 + cents
    }

    private fun commitCash() {
        if (input.isNotBlank()) cashCounts[cashIndex] = input.toIntOrNull()?.coerceIn(0, 99999) ?: cashCounts[cashIndex]
    }

    private fun resetAll() {
        entries.forEach { it.clear() }
        cashCounts.fill(0)
        stage = 0; cashIndex = 0; input = ""
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("stage", stage); out.putInt("cashIndex", cashIndex); out.putString("input", input); out.putBoolean("splashDone", splashDone)
        entries.indices.forEach { out.putLongArray("entry$it", entries[it].toLongArray()) }
        out.putIntArray("cashCounts", cashCounts)
    }

    private fun restore(state: Bundle?) {
        if (state == null) return
        stage = state.getInt("stage"); cashIndex = state.getInt("cashIndex"); input = state.getString("input", ""); splashDone = state.getBoolean("splashDone")
        entries.indices.forEach { entries[it].addAll((state.getLongArray("entry$it") ?: longArrayOf()).toList()) }
        state.getIntArray("cashCounts")?.forEachIndexed { i, value -> if (i < cashCounts.size) cashCounts[i] = value }
    }

    private fun display(value: String) = label(value, 32, true, Color.WHITE).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(dp(18), 0, dp(18), 0)
        setBackgroundColor(Color.rgb(16, 42, 33))
        layoutParams = LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) }
    }

    private fun totalCard(name: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        setBackgroundColor(Color.rgb(16, 42, 33))
        addView(label(name, 14, true, Color.rgb(169, 184, 176)))
        addView(label(value, 30, true, Color.WHITE))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun summaryRow(name: String, cents: Long) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(11), dp(4), dp(11))
        addView(label(name, 17, false, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(money(cents), 18, true, Color.WHITE))
    }

    private fun action(name: String, click: () -> Unit) = Button(this).apply {
        text = name; textSize = 15f; isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 57, 48))
        setOnClickListener { click() }
    }

    private fun numberKey(name: String, click: () -> Unit) = Button(this).apply {
        text = name; textSize = 22f; isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(29, 38, 35))
        setOnClickListener { click() }
    }

    private fun editKey(name: String, click: () -> Unit) = Button(this).apply {
        text = name; textSize = 18f; isAllCaps = false
        setTextColor(Color.rgb(221, 225, 222))
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(49, 57, 53))
        setOnClickListener { click() }
    }

    private fun plusButton(click: () -> Unit) = Button(this).apply {
        text = "+"; textSize = 28f; isAllCaps = false
        setTextColor(Color.rgb(7, 26, 20))
        setTypeface(typeface, Typeface.BOLD)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(214, 183, 107))
        contentDescription = "Add amount"
        setOnClickListener { click() }
    }

    private fun navButton(name: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = name; textSize = 15f; isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        if (primary) {
            setTextColor(Color.rgb(7, 26, 20))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(214, 183, 107))
        } else {
            setTextColor(Color.rgb(224, 229, 226))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(42, 49, 46))
        }
        setOnClickListener { click() }
    }

    private fun textAction(name: String, click: () -> Unit) = Button(this).apply {
        text = name; textSize = 13f; isAllCaps = false
        setTextColor(Color.rgb(214, 183, 107))
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(7, 26, 20))
        setOnClickListener { click() }
    }
    private fun label(value: String, size: Int, bold: Boolean, color: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun money(cents: Long) = currency.format(cents / 100.0)
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun weightedButton(endMargin: Int) = LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(endMargin) }
    private fun keyParams() = LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
