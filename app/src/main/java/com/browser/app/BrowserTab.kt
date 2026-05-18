package com.browser.app

import android.webkit.WebView

data class BrowserTab(
    val id: Int,
    var title: String = "Nueva pestaña",
    var url: String = "",
    var webView: WebView? = null
)
