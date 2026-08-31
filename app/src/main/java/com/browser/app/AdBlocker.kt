package com.browser.app

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

object AdBlocker {

    var enabled = true

    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    // ── DOMINIOS BLOQUEADOS (ampliado) ──────────────────────────────────────
    private val blockedDomains = setOf(
        // Google Ads / Analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "googleads.g.doubleclick.net",
        "static.doubleclick.net", "stats.g.doubleclick.net",
        "www.googleadservices.com", "partner.googleadservices.com",

        // YouTube ads específicamente
        "ads.youtube.com", "ad.youtube.com", "youtube.com/api/stats/ads",
        "youtube.com/pagead", "youtube.com/ptracking",

        // Facebook / Meta
        "connect.facebook.net", "facebook.com/tr", "an.facebook.com",

        // Amazon
        "amazon-adsystem.com", "assoc-amazon.com", "aax.amazon-adsystem.com",

        // Redes de anuncios y video ads
        "adnxs.com", "adsrvr.org", "adform.net", "advertising.com",
        "adcolony.com", "admob.com", "adroll.com", "ads.twitter.com",
        "ads.linkedin.com", "mediamath.com", "moatads.com", "openx.net",
        "pubmatic.com", "rubiconproject.com", "scorecardresearch.com",
        "taboola.com", "outbrain.com", "revcontent.com", "mgid.com",
        "criteo.com", "criteo.net", "thetradedesk.com", "casalemedia.com",
        "sharethrough.com", "spotxchange.com", "spotx.tv", "sonobi.com",
        "appnexus.com", "zedo.com", "yieldmanager.com", "smartadserver.com",
        "adition.com", "improvedigital.com", "indexexchange.com",
        "33across.com", "triplelift.com", "teads.tv", "unruly.co",
        "videoplayerhub.com", "vidible.tv", "brightcove.net",
        "innovid.com", "tremorhub.com", "adsafeprotected.com",
        "doubleverify.com", "adtechus.com", "conversantmedia.com",
        "flashtalking.com", "bidswitch.net", "rlcdn.com",
        "adkernel.com", "gumgum.com", "lkqd.net", "springserve.com",
        "vungle.com", "chartboost.com", "applovin.com",
        "ironsrc.com", "unityads.unity3d.com",

        // Trackers
        "hotjar.com", "segment.com", "segment.io", "mixpanel.com",
        "amplitude.com", "quantserve.com", "quantcast.com", "chartbeat.com",
        "chartbeat.net", "parsely.com", "newrelic.com", "nr-data.net",
        "fullstory.com", "loggly.com", "tealiumiq.com", "optimizely.com",
        "branch.io", "appsflyer.com", "crazyegg.com", "clicktale.net",
        "mouseflow.com", "luckyorange.com",

        // Popups / redirects agresivos
        "popcash.net", "popads.net", "pop.clicksfly.com", "clkrev.com",
        "realsrv.com", "trafficjunky.net", "exoclick.com", "juicyads.com",
        "plugrush.com", "tsyndicate.com", "propellerads.com",
        "adsterra.com", "hilltopads.net", "clickadu.com", "adcash.com",
        "mgid.com", "richads.com", "monetag.com",

        // Crypto miners
        "coinhive.com", "coin-hive.com", "cryptoloot.pro", "jsecoin.com",
        "minero.cc", "webminerpool.com",

        // Periódicos / paywalls con overlays de anuncios comunes
        "cliqz.com", "criteo.com", "permutive.com", "chartbeat.com",
        "adsafeprotected.com", "moatpixel.com", "serving-sys.com"
    )

    // ── PATRONES EN LA URL ───────────────────────────────────────────────────
    private val blockedPatterns = listOf(
        "/ads/", "/ad/", "/adserver/", "/advertisement", "/banner",
        "/tracking", "/pixel.gif", "/pixel.png", "/beacon.",
        "googlesyndication", "doubleclick", "coinhive",
        "/prebid", "vast.xml", "vast_url", "vastclick", "/ima/",
        "imasdk.googleapis.com", "adsbygoogle", "/adframe",
        "/adserve", "/adunit", "ad_type=", "adunit=", "/preroll",
        "/midroll", "/postroll", "video_ad", "videoads",
        "/gpt.js", "/gpt/pubads", "outstream", "instream_ad",
        "/ads.js", "/ad.js", "sponsored-", "/promo/ad",
        "affiliate-ad", "nativead", "/skinads"
    )

    private val emptyResponse = WebResourceResponse(
        "text/plain", "utf-8", "".byteInputStream()
    )

    fun shouldBlock(request: WebResourceRequest): Boolean {
        if (!enabled) return false
        val url = request.url.toString().lowercase()
        val host = request.url.host?.lowercase() ?: return false
        if (blockedDomains.any { host.endsWith(it) || host == it }) return true
        if (blockedPatterns.any { url.contains(it) }) return true
        return false
    }

    fun getEmptyResponse(): WebResourceResponse = emptyResponse

    /**
     * CSS + JS combinado:
     * 1. Oculta elementos visuales de anuncios (banners, overlays)
     * 2. Bloquea window.open / popups no solicitados
     * 3. Detecta y hace clic automáticamente en botones "Saltar anuncio"
     *    de reproductores de video genéricos
     * 4. Vuelve a ejecutarse cada vez que el DOM cambia (SPA / lazy load)
     */
    fun getAdHidingCss(): String = if (!enabled) "" else """
        (function() {
            if (window.__novaAdblockActive) return;
            window.__novaAdblockActive = true;

            // ── 1. CSS: ocultar elementos de anuncios visibles ──────────
            var style = document.createElement('style');
            style.textContent = [
                '[class*="banner-ad"]', '[class*="google-ad"]',
                '[id*="google_ads"]', '[id*="div-gpt-ad"]',
                'ins.adsbygoogle', '[class*="advertisement"]',
                '[class*="ad-container"]', '[class*="ad-wrapper"]',
                '[class*="ad-slot"]', '[class*="ad-banner"]',
                '[class*="sponsored-content"]', '[data-ad-slot]',
                '[id^="ad-"]', '[class^="ad-"]', '[class*="_ad_"]',
                '.taboola-widget', '.outbrain-widget',
                '#taboola-below-article', 'aside[data-type="ad"]',
                '[class*="video-ads"]', '[class*="ad-overlay"]',
                '[class*="preroll"]', '[class*="ima-ad"]',
                '.ytp-ad-module', '.ytp-ad-overlay-container',
                '.video-ads.ytp-ad-module', 'ytd-promoted-video-renderer',
                '[class*="popup-ad"]', '[class*="interstitial"]',
                '[class*="modal-ad"]', '[id*="sticky-ad"]',
                '[class*="sticky-ad"]', '[class*="floating-ad"]'
            ].join(',') + ' { display:none !important; visibility:hidden !important; height:0 !important; }';
            document.head.appendChild(style);

            // ── 2. Bloquear popups no solicitados ────────────────────────
            var originalOpen = window.open;
            window.open = function(url) {
                console.log('[AdBlock] Popup bloqueado: ' + url);
                return null;
            };

            // Bloquear listeners de click que abren popups en overlays sospechosos
            document.addEventListener('click', function(e) {
                var t = e.target;
                var cls = (t.className || '').toString().toLowerCase();
                if (cls.indexOf('popup') !== -1 || cls.indexOf('interstitial') !== -1) {
                    e.stopPropagation();
                    e.preventDefault();
                }
            }, true);

            // ── 3. Auto-saltar anuncios de video (YouTube y genéricos) ───
            function skipVideoAds() {
                // YouTube: botón "Saltar anuncio"
                var skipBtns = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
                    '[class*="skip-ad"], [class*="ad-skip"], ' +
                    'button[aria-label*="Skip"], button[aria-label*="Saltar"]'
                );
                skipBtns.forEach(function(btn) {
                    if (btn.offsetParent !== null) btn.click();
                });

                // Reproductores genéricos: si el video tiene clase/id de anuncio,
                // intentar avanzarlo al final para que termine rápido
                var adVideos = document.querySelectorAll(
                    'video[class*="ad"], video[id*="ad"], [class*="ad-player"] video'
                );
                adVideos.forEach(function(v) {
                    if (v.duration && v.duration < 60) {
                        try { v.currentTime = v.duration; } catch(e) {}
                    }
                });

                // Cerrar overlays de anuncio con botón "X" o "Cerrar"
                var closeBtns = document.querySelectorAll(
                    '[class*="ad-close"], [class*="close-ad"], ' +
                    '[aria-label*="Close ad"], [aria-label*="Cerrar anuncio"]'
                );
                closeBtns.forEach(function(btn) {
                    if (btn.offsetParent !== null) btn.click();
                });
            }

            // Ejecutar cada segundo (los anuncios de video suelen tardar en aparecer)
            setInterval(skipVideoAds, 1000);

            // ── 4. Reobservar cuando el DOM cambia (SPA, lazy load) ──────
            var observer = new MutationObserver(function() {
                skipVideoAds();
            });
            observer.observe(document.body, { childList: true, subtree: true });

        })();
    """.trimIndent()
}
