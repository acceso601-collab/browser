package com.browser.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────
    private lateinit var etAddress: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var webContainer: FrameLayout
    private lateinit var rvTabs: RecyclerView
    private lateinit var tabAdapter: TabAdapter
    private lateinit var gamepadOverlay: View
    private lateinit var btnToggleGamepad: ImageButton
    private lateinit var videoControls: LinearLayout
    private lateinit var btnVideoPlay: ImageButton
    private lateinit var btnVideoFullscreen: ImageButton
    private lateinit var btnVideoClose: ImageButton
    private lateinit var searchBar: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var tvSearchResult: TextView
    private lateinit var btnAdBlock: ImageButton

    // ── State ──────────────────────────────────────────────────────────────
    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0
    private var tabCounter = 0
    private var gamepadVisible = false
    private var searchBarVisible = false

    // Fullscreen video
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    private val activeWebView get() = tabs.getOrNull(activeTabIndex)?.webView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupTabs()
        setupAddressBar()
        setupGamepad()
        setupVideoControls()
        setupSearchBar()
        setupBottomBar()

        openNewTab("https://www.google.com")
    }

    // ── BIND VIEWS ─────────────────────────────────────────────────────────

    private fun bindViews() {
        etAddress          = findViewById(R.id.etAddress)
        progressBar        = findViewById(R.id.progressBar)
        webContainer       = findViewById(R.id.webContainer)
        rvTabs             = findViewById(R.id.rvTabs)
        gamepadOverlay     = findViewById(R.id.gamepadOverlay)
        btnToggleGamepad   = findViewById(R.id.btnToggleGamepad)
        videoControls      = findViewById(R.id.videoControls)
        btnVideoPlay       = findViewById(R.id.btnVideoPlay)
        btnVideoFullscreen = findViewById(R.id.btnVideoFullscreen)
        btnVideoClose      = findViewById(R.id.btnVideoClose)
        searchBar          = findViewById(R.id.searchBar)
        etSearch           = findViewById(R.id.etSearch)
        tvSearchResult     = findViewById(R.id.tvSearchResult)
        btnAdBlock         = findViewById(R.id.btnAdBlock)
    }

    // ── TABS ───────────────────────────────────────────────────────────────

    private fun setupTabs() {
        tabAdapter = TabAdapter(tabs, activeTabIndex,
            onTabClick = { switchTab(it) },
            onTabClose = { closeTab(it) }
        )
        rvTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvTabs.adapter = tabAdapter
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun openNewTab(url: String = "https://www.google.com") {
        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = buildWebViewClient()
            webChromeClient = buildWebChromeClient()
        }

        val tab = BrowserTab(id = tabCounter++, webView = wv)
        tabs.add(tab)
        webContainer.addView(wv)
        wv.visibility = View.GONE

        tabAdapter.notifyItemInserted(tabs.size - 1)
        switchTab(tabs.size - 1)
        wv.loadUrl(url)
    }

    private fun switchTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        tabs.getOrNull(activeTabIndex)?.webView?.visibility = View.GONE
        activeTabIndex = index
        tabAdapter.setActive(index)
        val wv = tabs[index].webView
        wv?.visibility = View.VISIBLE
        val url = tabs[index].url
        etAddress.setText(if (url == "about:blank" || url.isEmpty()) "" else url)
        rvTabs.scrollToPosition(index)
    }

    private fun closeTab(index: Int) {
        if (tabs.size == 1) {
            tabs[0].webView?.loadUrl("https://www.google.com")
            return
        }
        webContainer.removeView(tabs[index].webView)
        tabs.removeAt(index)
        tabAdapter.notifyItemRemoved(index)
        tabAdapter.notifyItemRangeChanged(index, tabs.size)
        switchTab(if (index >= tabs.size) tabs.size - 1 else index)
    }

    // ── WEBVIEW CLIENTS ────────────────────────────────────────────────────

    private fun buildWebViewClient() = object : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (AdBlocker.shouldBlock(request)) return AdBlocker.getEmptyResponse()
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            progressBar.visibility = View.VISIBLE
            etAddress.setText(url)
            tabs.getOrNull(activeTabIndex)?.url = url
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = View.GONE
            val title = view.title ?: url
            tabs.getOrNull(activeTabIndex)?.apply {
                this.title = title
                this.url = url
            }
            tabAdapter.notifyItemChanged(activeTabIndex)
            etAddress.setText(url)
            injectVideoDetector(view)

            // Ocultar elementos de anuncios via CSS
            view.evaluateJavascript(AdBlocker.getAdHidingCss(), null)
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: android.net.http.SslError
        ) {
            handler.proceed()
        }
    }

    private fun buildWebChromeClient() = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            if (newProgress == 100) progressBar.visibility = View.GONE
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            tabs.getOrNull(activeTabIndex)?.title = title
            tabAdapter.notifyItemChanged(activeTabIndex)
        }

        // ── FULLSCREEN VIDEO ──────────────────────────────────────────────
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) { callback.onCustomViewHidden(); return }

            customView = view
            customViewCallback = callback

            val container = FrameLayout(this@MainActivity).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            container.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            val root = window.decorView as FrameLayout
            root.addView(container)
            fullscreenContainer = container

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        override fun onHideCustomView() {
            customView ?: return
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            val root = window.decorView as FrameLayout
            fullscreenContainer?.let { root.removeView(it) }
            fullscreenContainer = null
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
        }
    }

    // ── ADDRESS BAR ────────────────────────────────────────────────────────

    private fun setupAddressBar() {
        etAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(etAddress.text.toString().trim())
                hideKeyboard()
                true
            } else false
        }
        etAddress.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) etAddress.selectAll()
        }
    }

    private fun navigate(input: String) {
        if (input.isEmpty()) return
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(input)}"
        }
        if (tabs.isEmpty()) openNewTab(url) else activeWebView?.loadUrl(url)
    }

    // ── GAMEPAD ────────────────────────────────────────────────────────────

    private fun setupGamepad() {
        btnToggleGamepad.setOnClickListener {
            gamepadVisible = !gamepadVisible
            gamepadOverlay.visibility = if (gamepadVisible) View.VISIBLE else View.GONE
            btnToggleGamepad.setColorFilter(
                if (gamepadVisible) 0xFF00ffc3.toInt() else 0xFF4a5568.toInt()
            )
        }

        setupGamepadBtn(R.id.btnUp)    { injectKey("ArrowUp") }
        setupGamepadBtn(R.id.btnDown)  { injectKey("ArrowDown") }
        setupGamepadBtn(R.id.btnLeft)  { injectKey("ArrowLeft") }
        setupGamepadBtn(R.id.btnRight) { injectKey("ArrowRight") }
        setupGamepadBtn(R.id.btnA)     { injectKey("Enter") }
        setupGamepadBtn(R.id.btnB)     { injectKey("Escape") }
        setupGamepadBtn(R.id.btnX)     { injectKey(" ") }
        setupGamepadBtn(R.id.btnY)     { injectKey("Backspace") }
        setupGamepadBtn(R.id.btnL)     { activeWebView?.scrollBy(-100, 0) }
        setupGamepadBtn(R.id.btnR)     { activeWebView?.scrollBy(100, 0) }
        setupGamepadBtn(R.id.btnStart) { activeWebView?.scrollBy(0, 300) }
        setupGamepadBtn(R.id.btnSelect){ activeWebView?.scrollBy(0, -300) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGamepadBtn(id: Int, action: () -> Unit) {
        val btn = gamepadOverlay.findViewById<View>(id) ?: return
        btn.setOnClickListener { action() }
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.6f; action() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f; v.performClick()
                }
            }
            true
        }
    }

    private fun injectKey(key: String) {
        val js = """
            (function() {
                var el = document.activeElement || document.body;
                ['keydown','keypress','keyup'].forEach(function(t) {
                    el.dispatchEvent(new KeyboardEvent(t, {key:'$key', bubbles:true}));
                });
            })();
        """.trimIndent()
        activeWebView?.evaluateJavascript(js, null)
    }

    // ── VIDEO CONTROLS ─────────────────────────────────────────────────────

    private fun injectVideoDetector(view: WebView) {
        view.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideoFound(count: Int) {
                runOnUiThread { videoControls.visibility = View.VISIBLE }
            }
        }, "VideoBridge")

        view.evaluateJavascript("""
            (function() {
                if (document.querySelectorAll('video').length > 0) VideoBridge.onVideoFound(1);
                new MutationObserver(function() {
                    if (document.querySelectorAll('video').length > 0) VideoBridge.onVideoFound(1);
                }).observe(document.body, {childList:true, subtree:true});
            })();
        """.trimIndent(), null)
    }

    private fun setupVideoControls() {
        videoControls.visibility = View.GONE

        btnVideoPlay.setOnClickListener {
            activeWebView?.evaluateJavascript("""
                (function(){var v=document.querySelector('video');
                if(v){if(v.paused)v.play();else v.pause();}})();
            """.trimIndent(), null)
        }

        btnVideoFullscreen.setOnClickListener {
            activeWebView?.evaluateJavascript("""
                (function(){var v=document.querySelector('video');
                if(!v)return;
                if(v.requestFullscreen)v.requestFullscreen();
                else if(v.webkitRequestFullscreen)v.webkitRequestFullscreen();})();
            """.trimIndent(), null)
        }

        btnVideoClose.setOnClickListener { videoControls.visibility = View.GONE }
    }

    // ── SEARCH IN PAGE ─────────────────────────────────────────────────────

    private fun setupSearchBar() {
        searchBar.visibility = View.GONE

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) activeWebView?.findAllAsync(query)
                true
            } else false
        }

        findViewById<ImageButton>(R.id.btnSearchNext).setOnClickListener {
            activeWebView?.findNext(true)
        }
        findViewById<ImageButton>(R.id.btnSearchPrev).setOnClickListener {
            activeWebView?.findNext(false)
        }
        findViewById<ImageButton>(R.id.btnSearchClose).setOnClickListener {
            activeWebView?.clearMatches()
            searchBar.visibility = View.GONE
            searchBarVisible = false
        }
    }

    // ── BOTTOM BAR ─────────────────────────────────────────────────────────

    private fun setupBottomBar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (activeWebView?.canGoBack() == true) activeWebView?.goBack()
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            if (activeWebView?.canGoForward() == true) activeWebView?.goForward()
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            activeWebView?.reload()
        }
        findViewById<ImageButton>(R.id.btnNewTab).setOnClickListener {
            openNewTab()
        }
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            searchBarVisible = !searchBarVisible
            searchBar.visibility = if (searchBarVisible) View.VISIBLE else View.GONE
            if (searchBarVisible) {
                etSearch.requestFocus()
                showKeyboard(etSearch)
            } else {
                activeWebView?.clearMatches()
            }
        }

        // ── ADBLOCK TOGGLE ─────────────────────────────────────────────
        updateAdBlockBtn()
        btnAdBlock.setOnClickListener {
            AdBlocker.toggle()
            updateAdBlockBtn()
            val msg = if (AdBlocker.enabled) "🛡️ Bloqueador activado" else "⚠️ Bloqueador desactivado"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            activeWebView?.reload()
        }
    }

    private fun updateAdBlockBtn() {
        btnAdBlock.setColorFilter(
            if (AdBlocker.enabled) 0xFF00ffc3.toInt() else 0xFF4a5568.toInt()
        )
    }

    // ── UTILS ──────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etAddress.windowToken, 0)
        etAddress.clearFocus()
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onBackPressed() {
        when {
            customView != null -> {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                val root = window.decorView as FrameLayout
                fullscreenContainer?.let { root.removeView(it) }
                fullscreenContainer = null
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
            searchBarVisible -> {
                activeWebView?.clearMatches()
                searchBar.visibility = View.GONE
                searchBarVisible = false
            }
            activeWebView?.canGoBack() == true -> activeWebView?.goBack()
            else -> super.onBackPressed()
        }
    }
}
