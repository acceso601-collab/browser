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

    // Gamepad
    private lateinit var gamepadOverlay: View
    private lateinit var btnToggleGamepad: ImageButton

    // Video
    private lateinit var videoControls: LinearLayout
    private lateinit var btnVideoPlay: ImageButton
    private lateinit var btnVideoFullscreen: ImageButton
    private lateinit var btnVideoClose: ImageButton

    // Search bar
    private lateinit var searchBar: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var tvSearchResult: TextView

    // ── State ──────────────────────────────────────────────────────────────
    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0
    private var tabCounter = 0
    private var gamepadVisible = false
    private var videoControlsVisible = false
    private var searchBarVisible = false

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
        etAddress        = findViewById(R.id.etAddress)
        progressBar      = findViewById(R.id.progressBar)
        webContainer     = findViewById(R.id.webContainer)
        rvTabs           = findViewById(R.id.rvTabs)
        gamepadOverlay   = findViewById(R.id.gamepadOverlay)
        btnToggleGamepad = findViewById(R.id.btnToggleGamepad)
        videoControls    = findViewById(R.id.videoControls)
        btnVideoPlay     = findViewById(R.id.btnVideoPlay)
        btnVideoFullscreen = findViewById(R.id.btnVideoFullscreen)
        btnVideoClose    = findViewById(R.id.btnVideoClose)
        searchBar        = findViewById(R.id.searchBar)
        etSearch         = findViewById(R.id.etSearch)
        tvSearchResult   = findViewById(R.id.tvSearchResult)
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

    @SuppressLint("SetJavaScriptEnabled")
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

        // Ocultar WebView anterior
        tabs.getOrNull(activeTabIndex)?.webView?.visibility = View.GONE

        activeTabIndex = index
        tabAdapter.setActive(index)

        // Mostrar WebView nuevo
        val wv = tabs[index].webView
        wv?.visibility = View.VISIBLE

        // Actualizar barra de dirección
        val url = tabs[index].url
        etAddress.setText(if (url == "about:blank" || url.isEmpty()) "" else url)

        rvTabs.scrollToPosition(index)
    }

    private fun closeTab(index: Int) {
        if (tabs.size == 1) {
            // Si es la última, abrir una nueva
            tabs[0].webView?.loadUrl("https://www.google.com")
            return
        }

        webContainer.removeView(tabs[index].webView)
        tabs.removeAt(index)
        tabAdapter.notifyItemRemoved(index)
        tabAdapter.notifyItemRangeChanged(index, tabs.size)

        val newIndex = if (index >= tabs.size) tabs.size - 1 else index
        switchTab(newIndex)
    }

    // ── WEBVIEW CLIENTS ────────────────────────────────────────────────────

    private fun buildWebViewClient() = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            progressBar.visibility = View.VISIBLE
            etAddress.setText(url)
            // Actualizar URL en tab
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
        }

        override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
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
        if (tabs.isEmpty()) openNewTab(url)
        else activeWebView?.loadUrl(url)
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

        // D-Pad
        setupGamepadBtn(R.id.btnUp)    { injectKey("ArrowUp") }
        setupGamepadBtn(R.id.btnDown)  { injectKey("ArrowDown") }
        setupGamepadBtn(R.id.btnLeft)  { injectKey("ArrowLeft") }
        setupGamepadBtn(R.id.btnRight) { injectKey("ArrowRight") }

        // Botones de acción
        setupGamepadBtn(R.id.btnA) { injectKey("Enter") }
        setupGamepadBtn(R.id.btnB) { injectKey("Escape") }
        setupGamepadBtn(R.id.btnX) { injectKey(" ") }        // Space
        setupGamepadBtn(R.id.btnY) { injectKey("Backspace") }

        // L / R
        setupGamepadBtn(R.id.btnL) { activeWebView?.scrollBy(-100, 0) }
        setupGamepadBtn(R.id.btnR) { activeWebView?.scrollBy(100, 0) }

        // START / SELECT
        setupGamepadBtn(R.id.btnStart)  { activeWebView?.scrollBy(0, 300) }
        setupGamepadBtn(R.id.btnSelect) { activeWebView?.scrollBy(0, -300) }
    }

    private fun setupGamepadBtn(id: Int, action: () -> Unit) {
        val btn = gamepadOverlay.findViewById<View>(id) ?: return
        btn.setOnClickListener { action() }

        // Soporte de pulsación continua
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.6f
                    action()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    v.performClick()
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
        val js = """
            (function() {
                var videos = document.querySelectorAll('video');
                if (videos.length > 0) {
                    VideoBridge.onVideoFound(videos.length);
                }
                // Observar si se agrega un video después
                var obs = new MutationObserver(function() {
                    var v = document.querySelectorAll('video');
                    if (v.length > 0) VideoBridge.onVideoFound(v.length);
                });
                obs.observe(document.body, {childList:true, subtree:true});
            })();
        """.trimIndent()

        view.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideoFound(count: Int) {
                runOnUiThread {
                    videoControls.visibility = View.VISIBLE
                }
            }
        }, "VideoBridge")

        view.evaluateJavascript(js, null)
    }

    private fun setupVideoControls() {
        videoControls.visibility = View.GONE

        btnVideoPlay.setOnClickListener {
            activeWebView?.evaluateJavascript("""
                (function() {
                    var v = document.querySelector('video');
                    if (!v) return;
                    if (v.paused) { v.play(); } else { v.pause(); }
                })();
            """.trimIndent(), null)
        }

        btnVideoFullscreen.setOnClickListener {
            activeWebView?.evaluateJavascript("""
                (function() {
                    var v = document.querySelector('video');
                    if (!v) return;
                    if (v.requestFullscreen) v.requestFullscreen();
                    else if (v.webkitRequestFullscreen) v.webkitRequestFullscreen();
                })();
            """.trimIndent(), null)
        }

        btnVideoClose.setOnClickListener {
            videoControls.visibility = View.GONE
        }
    }

    // ── SEARCH IN PAGE ─────────────────────────────────────────────────────

    private fun setupSearchBar() {
        searchBar.visibility = View.GONE
        var searchIndex = 0
        var searchTotal = 0

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchIndex = 0
                    activeWebView?.findAllAsync(query)
                }
                true
            } else false
        }

        activeWebView?.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            searchTotal = numberOfMatches
            searchIndex = activeMatchOrdinal
            tvSearchResult.text = if (numberOfMatches > 0)
                "${activeMatchOrdinal + 1}/$numberOfMatches"
            else "0 resultados"
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
    }

    // ── UTILS ───────────────────────────────────────────────────────────────

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
