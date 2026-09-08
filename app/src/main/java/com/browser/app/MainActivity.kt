package com.browser.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

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
    private lateinit var btnMenu: ImageButton

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0
    private var tabCounter = 0
    private var gamepadVisible = false
    private var searchBarVisible = false
    private var desktopMode = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    companion object {
        const val REQ_HISTORY = 301
        const val REQ_FAVORITES = 302
    }

    private val activeWebView get() = tabs.getOrNull(activeTabIndex)?.webView

    override fun onCreate(savedInstanceState: Bundle?) {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = if (result.resultCode == Activity.RESULT_OK) result.data else null
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupTabs()
        setupAddressBar()
        setupGamepad()
        setupVideoControls()
        setupSearchBar()
        setupBottomBar()
        setupMenuButton()

        val incomingUrl = intent?.data?.toString()
        if (incomingUrl != null) {
            openNewTab(incomingUrl)
        } else {
            restoreTabs()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingUrl = intent.data?.toString()
        if (incomingUrl != null) {
            openNewTab(incomingUrl)
        }
    }

    private fun applyTheme() {
        when (StorageManager.getTheme(this)) {
            StorageManager.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun applyForcedDarkToWebView(wv: WebView) {
        if (StorageManager.getTheme(this) == StorageManager.THEME_FORCED_DARK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                wv.settings.forceDark = WebSettings.FORCE_DARK_ON
            } else {
                wv.evaluateJavascript("""
                    (function(){
                        var s=document.createElement('style');
                        s.id='force-dark-css';
                        s.textContent='html{filter:invert(1) hue-rotate(180deg)!important}'+
                            'img,video,canvas{filter:invert(1) hue-rotate(180deg)!important}';
                        if(!document.getElementById('force-dark-css'))
                            document.head.appendChild(s);
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private fun showThemeDialog() {
        val options = arrayOf("🌑 Oscuro", "☀️ Claro", "🌚 Oscuro forzado")
        val current = StorageManager.getTheme(this)

        AlertDialog.Builder(this)
            .setTitle("Elegir tema")
            .setSingleChoiceItems(options, current) { dialog, which ->
                StorageManager.saveTheme(this, which)
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

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
        btnMenu            = findViewById(R.id.btnMenu)
    }

    private fun setupMenuButton() {
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)

            val currentUrl = tabs.getOrNull(activeTabIndex)?.url ?: ""
            val isFav = StorageManager.isFavorite(this, currentUrl)
            popup.menu.findItem(R.id.menu_favorite)?.title =
                if (isFav) "💛 Quitar de favoritos" else "⭐ Añadir a favoritos"

            popup.menu.findItem(R.id.menu_desktop)?.title =
                if (desktopMode) "📱 Modo móvil" else "🖥️ Modo escritorio"

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_favorite -> {
                        val tab = tabs.getOrNull(activeTabIndex)
                        if (tab != null) {
                            if (isFav) {
                                StorageManager.removeFavorite(this, tab.url)
                                Toast.makeText(this, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                            } else {
                                StorageManager.addFavorite(this, tab.url, tab.title)
                                Toast.makeText(this, "⭐ Añadido a favoritos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    R.id.menu_favorites -> startActivityForResult(Intent(this, FavoritesActivity::class.java), REQ_FAVORITES)
                    R.id.menu_history -> startActivityForResult(Intent(this, HistoryActivity::class.java), REQ_HISTORY)
                    R.id.menu_downloads -> startActivity(Intent(this, DownloadsActivity::class.java))
                    R.id.menu_passwords -> startActivity(Intent(this, PasswordsActivity::class.java))
                    R.id.menu_clear_cache -> showClearCacheDialog()
                    R.id.menu_theme -> showThemeDialog()
                    R.id.menu_desktop -> toggleDesktopMode()
                }
                true
            }
            popup.show()
        }
    }

    private fun getCacheSize(): Long {
        fun dirSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            var size = 0L
            dir.listFiles()?.forEach { f ->
                size += if (f.isDirectory) dirSize(f) else f.length()
            }
            return size
        }
        val cacheDirSize = dirSize(cacheDir)
        val webviewDataDir = File(applicationInfo.dataDir, "app_webview/Default/Cache")
        val webviewCacheSize = dirSize(webviewDataDir)
        return cacheDirSize + webviewCacheSize
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun showClearCacheDialog() {
        val sizeBytes = getCacheSize()
        val sizeText = formatBytes(sizeBytes)

        AlertDialog.Builder(this)
            .setTitle("🧹 Borrar caché")
            .setMessage("¿Desea borrar $sizeText de caché? Las páginas tardarán un poco más en cargar la próxima vez.")
            .setPositiveButton("Aceptar") { _, _ ->
                clearAllCache()
                Toast.makeText(this, "✅ Caché borrado ($sizeText liberados)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun clearAllCache() {
        tabs.forEach { it.webView?.clearCache(true) }

        fun deleteDirContents(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) {
                    deleteDirContents(f)
                    f.delete()
                } else {
                    f.delete()
                }
            }
        }
        deleteDirContents(cacheDir)
        deleteDirContents(File(applicationInfo.dataDir, "app_webview/Default/Cache"))
    }

    private fun toggleDesktopMode() {
        desktopMode = !desktopMode
        val wv = activeWebView ?: return
        val ua = if (desktopMode)
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
        else
            "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        wv.settings.userAgentString = ua
        wv.reload()
        Toast.makeText(this,
            if (desktopMode) "🖥️ Modo escritorio" else "📱 Modo móvil",
            Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val url = data?.getStringExtra(HistoryActivity.RESULT_URL)
                ?: data?.getStringExtra(FavoritesActivity.RESULT_URL)
            if (!url.isNullOrEmpty()) navigate(url)
        }
    }

    private fun setupTabs() {
        tabAdapter = TabAdapter(tabs, activeTabIndex,
            onTabClick = { switchTab(it) },
            onTabClose = { closeTab(it) }
        )
        rvTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvTabs.adapter = tabAdapter
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openNewTab(url: String = "https://www.google.com", switchToIt: Boolean = true) {
        val tab = BrowserTab(id = tabCounter++, url = url, title = "Cargando...")

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

                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true

                // Identificarse como Chrome normal para evitar bloqueos de Google
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            webViewClient = buildWebViewClient(tab)
            webChromeClient = buildWebChromeClient(tab)

            setDownloadListener { url2, userAgent, contentDisposition, mimeType, _ ->
                try {
                    val fileName = URLUtil.guessFileName(url2, contentDisposition, mimeType)
                    val request = DownloadManager.Request(android.net.Uri.parse(url2)).apply {
                        setMimeType(mimeType)
                        addRequestHeader("User-Agent", userAgent)
                        setDescription("Descargando archivo...")
                        setTitle(fileName)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        setAllowedOverMetered(true)
                        setAllowedOverRoaming(true)
                    }
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    val destPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName).absolutePath
                    StorageManager.addDownload(this@MainActivity, destPath)
                    Toast.makeText(this@MainActivity, "📥 Descargando: $fileName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        tab.webView = wv
        applyForcedDarkToWebView(wv)

        tabs.add(tab)
        webContainer.addView(wv)
        wv.visibility = View.GONE

        tabAdapter.notifyItemInserted(tabs.size - 1)

        if (switchToIt) switchTab(tabs.size - 1) else tabAdapter.notifyItemChanged(tabs.size - 1)

        wv.loadUrl(url)
    }

    private fun switchTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        tabs.getOrNull(activeTabIndex)?.webView?.visibility = View.GONE
        activeTabIndex = index
        tabAdapter.setActive(index)
        tabs[index].webView?.visibility = View.VISIBLE
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

    private fun restoreTabs() {
        val (saved, activeIdx) = StorageManager.loadSavedTabs(this)
        if (saved.isEmpty()) {
            openNewTab("https://www.google.com")
            return
        }
        saved.forEach { (url, _) ->
            openNewTab(url, switchToIt = false)
        }
        switchTab(activeIdx.coerceIn(0, tabs.size - 1))
    }

    override fun onStop() {
        super.onStop()
        StorageManager.saveTabs(this, tabs.map { Pair(it.url, it.title) }, activeTabIndex)
    }

    private fun buildWebViewClient(tab: BrowserTab) = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (AdBlocker.shouldBlock(request)) return AdBlocker.getEmptyResponse()
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            tab.url = url
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx == activeTabIndex) {
                progressBar.visibility = View.VISIBLE
                etAddress.setText(url)
            }
            if (idx >= 0) tabAdapter.notifyItemChanged(idx)
        }

        override fun onPageFinished(view: WebView, url: String) {
            val title = view.title ?: url
            tab.title = title
            tab.url = url
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx >= 0) tabAdapter.notifyItemChanged(idx)
            if (idx == activeTabIndex) {
                progressBar.visibility = View.GONE
                etAddress.setText(url)
            }
            StorageManager.addHistory(this@MainActivity, url, title)
            injectVideoDetector(view)
            injectLoginDetector(view, url)
            injectAutofill(view, url)
            view.evaluateJavascript(AdBlocker.getAdHidingCss(), null)
            applyForcedDarkToWebView(view)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            handler.proceed()
        }
    }

    private fun buildWebChromeClient(tab: BrowserTab) = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx == activeTabIndex) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            tab.title = title
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx >= 0) tabAdapter.notifyItemChanged(idx)
        }

        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            filePathCallback = callback
            val intent = fileChooserParams.createIntent()
            return try {
                fileChooserLauncher.launch(intent)
                true
            } catch (e: Exception) {
                filePathCallback = null
                false
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) { callback.onCustomViewHidden(); return }
            customView = view
            customViewCallback = callback
            val container = FrameLayout(this@MainActivity).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            (window.decorView as FrameLayout).addView(container)
            fullscreenContainer = container
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        override fun onHideCustomView() {
            customView ?: return
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            fullscreenContainer?.let { (window.decorView as FrameLayout).removeView(it) }
            fullscreenContainer = null
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
        }
    }

    private fun injectAutofill(view: WebView, url: String) {
        val creds = PasswordManager.getForDomain(this, url)
        if (creds.isEmpty()) return
        val cred = creds.first()
        val user = cred.username.replace("'", "\\'")
        val pass = cred.password.replace("'", "\\'")
        val js = """
            (function() {
                function setNativeValue(el, value) {
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    setter.call(el, value);
                    el.dispatchEvent(new Event('input', {bubbles:true}));
                    el.dispatchEvent(new Event('change', {bubbles:true}));
                }
                var inputs = document.querySelectorAll('input');
                var userField = null, passField = null;
                inputs.forEach(function(el) {
                    if (!el.offsetParent) return;
                    if (el.type === 'password') { passField = el; }
                    else if ((el.type === 'text' || el.type === 'email' || el.type === '') && !userField) { userField = el; }
                });
                if (userField) setNativeValue(userField, '$user');
                if (passField) setNativeValue(passField, '$pass');
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun injectLoginDetector(view: WebView, url: String) {
        view.addJavascriptInterface(object {
            @JavascriptInterface
            fun onLoginSubmit(username: String, password: String) {
                runOnUiThread { offerSaveCredentials(url, username, password) }
            }
        }, "PasswordBridge")
        val js = """
            (function() {
                if (window.__pwInjected) return;
                window.__pwInjected = true;
                document.addEventListener('submit', function(e) {
                    var form = e.target;
                    if (!form || form.tagName !== 'FORM') return;
                    var passField = form.querySelector('input[type=password]');
                    if (!passField || !passField.value) return;
                    var userField = form.querySelector('input[type=text], input[type=email], input[name*=user], input[id*=user]');
                    var username = userField ? userField.value : '';
                    if (username) PasswordBridge.onLoginSubmit(username, passField.value);
                }, true);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun offerSaveCredentials(url: String, username: String, password: String) {
        val domain = PasswordManager.domainFromUrl(url)
        val already = PasswordManager.getForDomain(this, url).any { it.username == username && it.password == password }
        if (already) return
        AlertDialog.Builder(this)
            .setTitle("🔑 Guardar contraseña")
            .setMessage("¿Guardar el usuario y contraseña para $domain?")
            .setPositiveButton("Guardar") { _, _ ->
                PasswordManager.save(this, url, username, password)
                Toast.makeText(this, "✅ Contraseña guardada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No, gracias", null)
            .show()
    }

    private fun setupAddressBar() {
        etAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(etAddress.text.toString().trim())
                hideKeyboard()
                true
            } else false
        }
        etAddress.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etAddress.selectAll() }
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

    private fun setupGamepad() {
        btnToggleGamepad.setOnClickListener {
            gamepadVisible = !gamepadVisible
            gamepadOverlay.visibility = if (gamepadVisible) View.VISIBLE else View.GONE
            btnToggleGamepad.setColorFilter(if (gamepadVisible) 0xFF00ffc3.toInt() else 0xFF4a5568.toInt())
        }
        setupGamepadBtn(R.id.btnUp)     { injectKey("ArrowUp") }
        setupGamepadBtn(R.id.btnDown)   { injectKey("ArrowDown") }
        setupGamepadBtn(R.id.btnLeft)   { injectKey("ArrowLeft") }
        setupGamepadBtn(R.id.btnRight)  { injectKey("ArrowRight") }
        setupGamepadBtn(R.id.btnA)      { injectKey("Enter") }
        setupGamepadBtn(R.id.btnB)      { injectKey("Escape") }
        setupGamepadBtn(R.id.btnX)      { injectKey(" ") }
        setupGamepadBtn(R.id.btnY)      { injectKey("Backspace") }
        setupGamepadBtn(R.id.btnL)      { activeWebView?.scrollBy(-100, 0) }
        setupGamepadBtn(R.id.btnR)      { activeWebView?.scrollBy(100, 0) }
        setupGamepadBtn(R.id.btnStart)  { activeWebView?.scrollBy(0, 300) }
        setupGamepadBtn(R.id.btnSelect) { activeWebView?.scrollBy(0, -300) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGamepadBtn(id: Int, action: () -> Unit) {
        val btn = gamepadOverlay.findViewById<View>(id) ?: return
        btn.setOnClickListener { action() }
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.6f; action() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 1f; v.performClick() }
            }
            true
        }
    }

    private fun injectKey(key: String) {
        activeWebView?.evaluateJavascript("""
            (function(){var el=document.activeElement||document.body;
            ['keydown','keypress','keyup'].forEach(function(t){
            el.dispatchEvent(new KeyboardEvent(t,{key:'$key',bubbles:true}));});})();
        """.trimIndent(), null)
    }

    private fun injectVideoDetector(view: WebView) {
        view.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideoFound(count: Int) {
                runOnUiThread { videoControls.visibility = View.VISIBLE }
            }
        }, "VideoBridge")
        view.evaluateJavascript("""
            (function(){if(document.querySelectorAll('video').length>0)VideoBridge.onVideoFound(1);
            new MutationObserver(function(){if(document.querySelectorAll('video').length>0)
            VideoBridge.onVideoFound(1);}).observe(document.body,{childList:true,subtree:true});})();
        """.trimIndent(), null)
    }

    private fun setupVideoControls() {
        videoControls.visibility = View.GONE
        btnVideoPlay.setOnClickListener {
            activeWebView?.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})();", null)
        }
        btnVideoFullscreen.setOnClickListener {
            activeWebView?.evaluateJavascript("""
                (function(){var v=document.querySelector('video');if(!v)return;
                if(v.requestFullscreen)v.requestFullscreen();
                else if(v.webkitRequestFullscreen)v.webkitRequestFullscreen();})();
            """.trimIndent(), null)
        }
        btnVideoClose.setOnClickListener { videoControls.visibility = View.GONE }
    }

    private fun setupSearchBar() {
        searchBar.visibility = View.GONE
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val q = etSearch.text.toString().trim()
                if (q.isNotEmpty()) activeWebView?.findAllAsync(q)
                true
            } else false
        }
        findViewById<ImageButton>(R.id.btnSearchNext).setOnClickListener { activeWebView?.findNext(true) }
        findViewById<ImageButton>(R.id.btnSearchPrev).setOnClickListener { activeWebView?.findNext(false) }
        findViewById<ImageButton>(R.id.btnSearchClose).setOnClickListener {
            activeWebView?.clearMatches()
            searchBar.visibility = View.GONE
            searchBarVisible = false
        }
    }

    private fun setupBottomBar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (activeWebView?.canGoBack() == true) activeWebView?.goBack()
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            if (activeWebView?.canGoForward() == true) activeWebView?.goForward()
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener { activeWebView?.reload() }
        findViewById<ImageButton>(R.id.btnNewTab).setOnClickListener { openNewTab() }
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            searchBarVisible = !searchBarVisible
            searchBar.visibility = if (searchBarVisible) View.VISIBLE else View.GONE
            if (searchBarVisible) { etSearch.requestFocus(); showKeyboard(etSearch) }
            else activeWebView?.clearMatches()
        }
        updateAdBlockBtn()
        btnAdBlock.setOnClickListener {
            AdBlocker.toggle()
            updateAdBlockBtn()
            Toast.makeText(this,
                if (AdBlocker.enabled) "🛡️ Bloqueador activado" else "⚠️ Bloqueador desactivado",
                Toast.LENGTH_SHORT).show()
            activeWebView?.reload()
        }
    }

    private fun updateAdBlockBtn() {
        btnAdBlock.setColorFilter(if (AdBlocker.enabled) 0xFF00ffc3.toInt() else 0xFF4a5568.toInt())
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(etAddress.windowToken, 0)
        etAddress.clearFocus()
    }

    private fun showKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onBackPressed() {
        when {
            customView != null -> {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                fullscreenContainer?.let { (window.decorView as FrameLayout).removeView(it) }
                fullscreenContainer = null; customView = null
                customViewCallback?.onCustomViewHidden(); customViewCallback = null
            }
            searchBarVisible -> {
                activeWebView?.clearMatches()
                searchBar.visibility = View.GONE; searchBarVisible = false
            }
            activeWebView?.canGoBack() == true -> activeWebView?.goBack()
            else -> super.onBackPressed()
        }
    }
}
