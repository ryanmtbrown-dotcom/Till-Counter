package com.tillcounter.app

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(7,24,18))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.databaseEnabled = false
            webViewClient = WebViewClient()
            isLongClickable = false
            setOnLongClickListener { true }
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
