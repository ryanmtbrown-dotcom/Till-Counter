package com.tillcounter.app

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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
    private val rollSizes = intArrayOf(0, 0, 0, 0, 0, 0, 0, 25, 20, 40, 50, 40, 50)
    private val rollCounts = IntArray(cashLabels.size)
    private val currency = NumberFormat.getCurrencyInstance(Locale.US)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: TillSettings
    private var settingsOpen = false
    private var stage = 0
    private var cashIndex = 0
    private var input = ""
    private var editingRolls = false
    private var splashDone = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        settings = TillSettings(this)
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

    private fun render() = if (settingsOpen) renderSettings() else when {
        stage < stages.size -> renderMoneyStage()
        stage == stages.size -> renderCashStage()
        else -> renderSummary()
    }

    private fun baseColumn(title: String, subtitle: String, scrollable: Boolean = false): LinearLayout {
        val outer = root()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("TILL COUNTER", 15, true, Color.rgb(214, 183, 107)), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(navButton("⚙", false) { settingsOpen = true; input = ""; render() }.apply { contentDescription = "Settings"; textSize = 21f }, LinearLayout.LayoutParams(dp(54), dp(46)))
        col.addView(header, matchWrap())
        col.addView(label(title, 27, true, Color.WHITE).apply { setPadding(0, dp(4), 0, 0) }, matchWrap())
        col.addView(label(subtitle, 14, false, Color.rgb(169, 184, 176)).apply { setPadding(0, dp(2), 0, dp(8)) }, matchWrap())
        if (scrollable) {
            val scroll = ScrollView(this).apply { isFillViewport = true }
            scroll.addView(col, ViewGroup.LayoutParams(-1, -2))
            outer.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        } else {
            outer.addView(col, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(outer)
        return col
    }

    private fun renderMoneyStage() {
        if (!settings.isEnabled(stages[stage])) {
            stage = nextEnabledStage(stage)
            render()
            return
        }
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
                stage = previousEnabledStage(stage) ?: 0
                input = ""
                render()
            }
        }, weightedButton(10))
        nav.addView(navButton(if (stage == stages.lastIndex) "NEXT: CASH" else "NEXT", true) {
            commitPendingMoney()
            stage = nextEnabledStage(stage)
            input = ""
            render()
        }, LinearLayout.LayoutParams(0, dp(60), 1.35f))
        col.addView(nav, matchWrap())
    }

    private fun nextEnabledStage(from: Int): Int {
        for (i in from + 1 until stages.size) if (settings.isEnabled(stages[i])) return i
        return stages.size
    }

    private fun previousEnabledStage(from: Int): Int? {
        for (i in from - 1 downTo 0) if (settings.isEnabled(stages[i])) return i
        return null
    }

    private fun renderSettings() {
        val outer = root()
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("TILL COUNTER", 15, true, Color.rgb(214, 183, 107)), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("SETTINGS", 18, true, Color.WHITE))
        shell.addView(header, LinearLayout.LayoutParams(-1, dp(46)))

        val scroll = ScrollView(this).apply { isFillViewport = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        col.addView(label("COUNT WORKFLOW", 13, true, Color.rgb(214, 183, 107)))
        col.addView(label("Choose which non-cash steps appear during a count.", 14, false, Color.rgb(169, 184, 176)).apply { setPadding(0, dp(2), 0, dp(6)) })
        stages.forEach { name ->
            col.addView(CheckBox(this).apply {
                text = name; textSize = 16f; setTextColor(Color.WHITE); isChecked = settings.isEnabled(name)
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(214, 183, 107))
                setOnCheckedChangeListener { _, checked -> settings.setEnabled(name, checked) }
            }, LinearLayout.LayoutParams(-1, dp(46)))
        }
        col.addView(label("BASE TILL AMOUNT", 13, true, Color.rgb(214, 183, 107)).apply { setPadding(0, dp(14), 0, dp(5)) })
        val base = EditText(this).apply {
            setText(if (settings.baseTillCents() == 0L) "" else String.format(Locale.US, "%.2f", settings.baseTillCents() / 100.0))
            hint = "Example: 300.00"; textSize = 20f; setTextColor(Color.WHITE); setHintTextColor(Color.rgb(169, 184, 176))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            showSoftInputOnFocus = false
            setBackgroundColor(Color.rgb(16, 42, 33)); setPadding(dp(16), 0, dp(16), 0)
        }
        col.addView(base, LinearLayout.LayoutParams(-1, dp(54)))
        col.addView(navButton("SAVE BASE TILL", true) {
            val cents = parseCents(base.text.toString()) ?: 0L
            settings.setBaseTillCents(cents)
            base.clearFocus()
            Toast.makeText(this, "Base till saved: " + money(cents), Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        col.addView(label("APP", 13, true, Color.rgb(214, 183, 107)).apply { setPadding(0, dp(18), 0, dp(5)) })
        col.addView(label("Version " + appVersion(), 14, false, Color.rgb(169, 184, 176)))
        col.addView(navButton("CHECK FOR UPDATE", false) { checkForUpdate() }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        scroll.addView(col, ViewGroup.LayoutParams(-1, -2))
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(navButton("DONE", true) {
            base.clearFocus()
            settingsOpen = false
            input = ""
            render()
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
        outer.addView(shell, FrameLayout.LayoutParams(-1, -1))
        setContentView(outer)
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
        val isCoin = rollSizes[cashIndex] > 0
        val rolls = rollCounts[cashIndex]
        val totalValue = count.toLong() * cashCents[cashIndex] + rolls.toLong() * rollSizes[cashIndex] * cashCents[cashIndex]
        if (isCoin) {
            val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(8)) }
            modes.addView(navButton("LOOSE: " + count, !editingRolls) { commitCash(); editingRolls = false; input = ""; render() }, weightedButton(8))
            modes.addView(navButton("ROLLS: " + rolls, editingRolls) { commitCash(); editingRolls = true; input = ""; render() }, LinearLayout.LayoutParams(0, dp(58), 1f))
            col.addView(modes, matchWrap())
            col.addView(label("1 roll = " + rollSizes[cashIndex] + " coins", 14, false, Color.rgb(169, 184, 176)))
        } else {
            col.addView(label("Count: $count", 24, true, Color.WHITE))
        }
        col.addView(label("Value: " + money(totalValue), 18, false, Color.rgb(169, 184, 176)).apply { setPadding(0, 0, 0, dp(14)) })
        val shownCount = if (editingRolls && isCoin) rolls else count
        col.addView(display(if (input.isBlank()) shownCount.toString() else input))
        countPad(col)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        nav.addView(action("BACK") {
            commitCash()
            if (cashIndex > 0) cashIndex-- else stage = stages.size - 1
            editingRolls = false; input = ""; render()
        }, weightedButton(10))
        nav.addView(action(if (cashIndex == cashLabels.lastIndex) "FINISH" else "NEXT") {
            commitCash()
            if (cashIndex == cashLabels.lastIndex) stage = stages.size + 1 else cashIndex++
            editingRolls = false; input = ""; render()
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        col.addView(nav, matchWrap())
    }

    private fun renderSummary() {
        val categoryTotals = entries.map { it.sum() }
        val cashTotal = cashCounts.indices.sumOf { cashCounts[it].toLong() * cashCents[it] + rollCounts[it].toLong() * rollSizes[it] * cashCents[it] }
        val grand = categoryTotals.filterIndexed { index, _ -> settings.isEnabled(stages[index]) }.sum() + cashTotal
        val baseTill = settings.baseTillCents()
        val drop = grand - baseTill
        val col = baseColumn("Till Summary", "Copy these totals to your till form.", scrollable = true)
        stages.forEachIndexed { index, name -> if (settings.isEnabled(name)) col.addView(summaryRow(name, categoryTotals[index])) }
        col.addView(summaryRow("Cash", cashTotal))
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(16, 42, 33))
            addView(label("TOTAL", 15, true, Color.rgb(214, 183, 107)))
            addView(label(money(grand), 34, true, Color.WHITE))
            if (baseTill > 0) {
                addView(label("BASE TILL  " + money(baseTill), 14, true, Color.rgb(169, 184, 176)).apply { setPadding(0, dp(10), 0, 0) })
                addView(label("DROP  " + money(drop), 24, true, if (drop >= 0) Color.rgb(214, 183, 107) else Color.rgb(244, 170, 160)))
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        col.addView(label("Cash breakdown", 20, true, Color.WHITE).apply { setPadding(0, dp(22), 0, dp(8)) })
        cashCounts.indices.filter { cashCounts[it] > 0 || rollCounts[it] > 0 }.forEach {
            if (cashCounts[it] > 0) col.addView(label("${cashLabels[it]} × ${cashCounts[it]} = ${money(cashCounts[it].toLong() * cashCents[it])}", 16, false, Color.WHITE))
            if (rollCounts[it] > 0) col.addView(label("${cashLabels[it]} rolls × ${rollCounts[it]} (${rollSizes[it]} each) = ${money(rollCounts[it].toLong() * rollSizes[it] * cashCents[it])}", 16, false, Color.WHITE))
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(22), 0, dp(10)) }
        nav.addView(action("BACK") { stage = stages.size; cashIndex = cashLabels.lastIndex; render() }, weightedButton(10))
        nav.addView(action("NEW COUNT") { resetAll(); render() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        col.addView(nav, matchWrap())
        
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun appVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"

    private fun checkForUpdate() {
        Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val connection = (URL("https://api.github.com/repos/ryanmtbrown-dotcom/Till-Counter/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Till-Counter/${appVersion()}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                connection.disconnect()
                runOnUiThread {
                    if (isNewerVersion(tag, appVersion()) && apkUrl != null) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Till Counter $tag available")
                            .setMessage("Download and install the update now?")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Update") { _, _ -> downloadUpdate(apkUrl!!, tag) }
                            .show()
                    } else {
                        Toast.makeText(this, "Till Counter is up to date.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Could not check for updates. Try again later.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun downloadUpdate(url: String, version: String) {
        Toast.makeText(this, "Downloading Till Counter $version…", Toast.LENGTH_LONG).show()
        thread {
            try {
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "Till-Counter-v$version.apk")
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Till-Counter/${appVersion()}")
                }
                connection.inputStream.use { inputStream -> apk.outputStream().use { output -> inputStream.copyTo(output) } }
                connection.disconnect()
                if (!apk.isFile || apk.length() < 1024) throw IllegalStateException("Downloaded APK is empty")
                runOnUiThread { launchInstaller(apk) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Update download failed. Try again later.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun launchInstaller(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "com.tillcounter.app.updates", apk)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun isNewerVersion(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
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
        if (input.isNotBlank()) {
            val value = input.toIntOrNull()?.coerceIn(0, 99999) ?: return
            if (editingRolls && rollSizes[cashIndex] > 0) rollCounts[cashIndex] = value else cashCounts[cashIndex] = value
        }
    }

    private fun resetAll() {
        entries.forEach { it.clear() }
        cashCounts.fill(0)
        rollCounts.fill(0)
        stage = 0; cashIndex = 0; input = ""; editingRolls = false
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("stage", stage); out.putInt("cashIndex", cashIndex); out.putString("input", input); out.putBoolean("splashDone", splashDone); out.putBoolean("settingsOpen", settingsOpen)
        entries.indices.forEach { out.putLongArray("entry$it", entries[it].toLongArray()) }
        out.putIntArray("cashCounts", cashCounts); out.putIntArray("rollCounts", rollCounts); out.putBoolean("editingRolls", editingRolls)
    }

    private fun restore(state: Bundle?) {
        if (state == null) return
        stage = state.getInt("stage"); cashIndex = state.getInt("cashIndex"); input = state.getString("input", ""); splashDone = state.getBoolean("splashDone"); settingsOpen = state.getBoolean("settingsOpen")
        entries.indices.forEach { entries[it].addAll((state.getLongArray("entry$it") ?: longArrayOf()).toList()) }
        state.getIntArray("cashCounts")?.forEachIndexed { i, value -> if (i < cashCounts.size) cashCounts[i] = value }
        state.getIntArray("rollCounts")?.forEachIndexed { i, value -> if (i < rollCounts.size) rollCounts[i] = value }
        editingRolls = state.getBoolean("editingRolls")
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
