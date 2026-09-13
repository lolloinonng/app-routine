package com.routine.calendar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            // Stessi file del sito GitHub, copiati negli asset in fase di build
            web.loadUrl("file:///android_asset/www/index.html")
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack()
        else super.onBackPressed()
    }

    inner class Bridge {
        // [{title, body, at (epoch ms), tag}] — sveglie di oggi dal sito
        @JavascriptInterface
        fun schedule(json: String) {
            EventScheduler.schedule(this@MainActivity, json)
        }

        @JavascriptInterface
        fun cancel() {
            EventScheduler.cancelAll(this@MainActivity)
        }
    }
}
