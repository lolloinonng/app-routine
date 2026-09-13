package com.routine.calendar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    companion object {
        // Sito pubblicato (stesso Firebase del PC) + copia locale di riserva offline
        const val REMOTE_URL = "https://lolloinonng.github.io/app-routine/"
        const val LOCAL_URL = "file:///android_asset/www/index.html"
    }

    private lateinit var web: WebView
    private var fellBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        // Fondamentale: senza WebChromeClient i confirm()/alert() del sito
        // (es. "Ripristina Routine Predefinita") vengono ignorati e il reset non parte
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                // Se il sito remoto non è raggiungibile, usa la copia offline
                if (!fellBack && request.isForMainFrame && request.url.toString() == REMOTE_URL) {
                    fellBack = true
                    view.loadUrl(LOCAL_URL)
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            // Stesso sito del PC (stesso Firebase): condivisioni eventi garantite.
            // Copia locale solo se offline (ma lì Firebase non sincronizza).
            web.loadUrl(REMOTE_URL)
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
