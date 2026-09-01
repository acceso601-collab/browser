package com.browser.app

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

object AdBlocker {

    var enabled = true

    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    // ── DOMINIOS BLOQUEADOS ──────────────────────────────────────────────────
    private val blockedDomains = setOf(
        // Google Ads / Analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "googleads.g.doubleclick.net",
        "static.doubleclick.net", "stats.g.doubleclick.net",

        // YouTube ads
        "ads.youtube.com", "ad.youtube.com", "imasdk.googleapis.com",

        // Facebook / Amazon
        "connect.facebook.net", "facebook.com/tr", "amazon-adsystem.com",

        // Redes de anuncios y video ads
        "adnxs.com", "adsrvr.org", "adform.net", "advertising.com",
        "adcolony.com", "admob.com", "adroll.com", "ads.twitter.com",
        "mediamath.com", "moatads.com", "openx.net", "pubmatic.com",
        "rubiconproject.com", "scorecardresearch.com", "taboola.com",
        "outbrain.com", "revcontent.com", "mgid.com", "criteo.com",
        "criteo.net", "thetradedesk.com", "casalemedia.com", "sharethrough.com",
        "spotxchange.com", "spotx.tv", "sonobi.com", "appnexus.com", "zedo.com",
        "smartadserver.com", "indexexchange.com", "33across.com", "triplelift.com",
        "teads.tv", "unruly.co", "videoplayerhub.com", "vidible.tv",
        "brightcove.net", "innovid.com", "tremorhub.com", "adsafeprotected.com",
        "doubleverify.com", "conversantmedia.com", "flashtalking.com",
        "bidswitch.net", "rlcdn.com", "adkernel.com", "gumgum.com",
        "lkqd.net", "springserve.com", "vungle.com", "chartboost.com",
        "applovin.com", "ironsrc.com", "unityads.unity3d.com",

        // Redes de popunder / redirect agresivas
        "popcash.net", "popads.net", "clkrev.com", "realsrv.com",
        "trafficjunky.net", "exoclick.com", "juicyads.com", "plugrush.com",
        "tsyndicate.com", "propellerads.com", "adsterra.com", "hilltopads.net",
        "clickadu.com", "adcash.com", "richads.com", "monetag.com",
        "popunder.net", "adreactor.com", "trafficfactory.biz", "popin.cc",
        "adf.ly", "shorte.st", "linkbucks.com", "adshort.co", "ouo.io", "bc.vc",

        // Trackers
        "hotjar.com", "segment.com", "segment.io", "mixpanel.com",
        "amplitude.com", "quantserve.com", "quantcast.com", "chartbeat.com",
        "chartbeat.net", "parsely.com", "newrelic.com", "nr-data.net",
        "fullstory.com", "loggly.com", "tealiumiq.com", "optimizely.com",
        "branch.io", "appsflyer.com", "crazyegg.com", "clicktale.net",
        "mouseflow.com", "luckyorange.com",

        // Crypto miners
        "coinhive.com", "coin-hive.com", "cryptoloot.pro", "jsecoin.com",
        "minero.cc", "webminerpool.com"
    )

    // ── PATRONES EN LA URL ───────────────────────────────────────────────────
    // Nota: se evitan patrones demasiado genéricos como "ref=", "aff=",
    // "/go/", "/click/", "/download/" porque coinciden con URLs de sitios
    // completamente normales (tiendas, tu propio gestor de descargas, etc.)
    // y bloquearían contenido legítimo, no solo anuncios.
    private val blockedPatterns = listOf(
        "/ads/", "/ad/", "/adserver/", "/advertisement", "/banner",
        "/tracking", "/pixel.gif", "/pixel.png", "/beacon.",
        "googlesyndication", "doubleclick", "coinhive",
        "/prebid", "vast.xml", "vast_url", "vastclick", "/ima/",
        "imasdk.googleapis.com", "adsbygoogle", "/adframe",
        "/adserve", "/adunit", "ad_type=", "adunit=", "/preroll",
        "/midroll", "/postroll", "video_ad", "videoads",
        "/gpt.js", "/gpt/pubads", "outstream", "instream_ad",
        "/ads.js", "/ad.js", "sponsored-", "/promo/ad", "nativead",
        "popunder", "prestitial", "/popads", "/popcash"
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
     * CSS + JS:
     * 1. Oculta elementos de anuncios usando selectores ESPECÍFICOS
     *    (no términos genéricos como "container"/"wrapper"/"player" que
     *    también los usan reproductores y layouts legítimos).
     * 2. Bloquea window.open (popups) — de forma segura, sin tocar
     *    window.location (eso rompía el script y además navegación normal).
     * 3. Detecta y hace clic en botones "Saltar anuncio" de reproductores.
     * 4. Bloquea SOLO clics en links que apuntan directo a dominios de
     *    redes de popunder conocidas — no bloquea clics genéricos en
     *    "overlay"/"player" que podrían ser el video real.
     */
    fun getAdHidingCss(): String = if (!enabled) "" else """
        (function() {
            if (window.__novaAdblockActive) return;
            window.__novaAdblockActive = true;

            // ── 1. CSS: ocultar elementos de anuncios específicos ────────
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
                '[class*="preroll-ad"]', '[class*="ima-ad"]',
                '.ytp-ad-module', '.ytp-ad-overlay-container',
                '.video-ads.ytp-ad-module', 'ytd-promoted-video-renderer',
                '[class*="popup-ad"]', '[class*="ad-interstitial"]',
                '[class*="modal-ad"]', '[id*="sticky-ad"]',
                '[class*="sticky-ad"]', '[class*="floating-ad"]',
                '[class*="popunder"]'
            ].join(',') + ' { display:none !important; visibility:hidden !important; height:0 !important; }';
            document.head.appendChild(style);

            // ── 2. Bloquear popups (window.open) — seguro, no rompe nada ─
            window.open = function(url) {
                console.log('[AdBlock] Popup bloqueado: ' + url);
                return null;
            };

            // ── 3. Bloquear SOLO clics en links a redes de popunder ──────
            var popunderHosts = [
                'popcash', 'popads', 'propellerads', 'adsterra', 'monetag',
                'exoclick', 'juicyads', 'trafficjunky', 'clkrev', 'realsrv',
                'hilltopads', 'clickadu', 'adcash', 'richads'
            ];
            document.addEventListener('click', function(e) {
                var t = e.target;
                while (t && t.tagName !== 'A') t = t.parentElement;
                if (!t || !t.href) return;
                var href = t.href.toLowerCase();
                if (popunderHosts.some(function(h) { return href.indexOf(h) !== -1; })) {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log('[AdBlock] Enlace de popunder bloqueado: ' + href);
                }
            }, true);

            // ── 4. Auto-saltar anuncios de video ──────────────────────────
            function skipVideoAds() {
                var skipBtns = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
                    '[class*="skip-ad"], [class*="ad-skip"], ' +
                    'button[aria-label*="Skip"], button[aria-label*="Saltar"]'
                );
                skipBtns.forEach(function(btn) {
                    if (btn.offsetParent !== null) btn.click();
                });

                var closeBtns = document.querySelectorAll(
                    '[class*="ad-close"], [class*="close-ad"], ' +
                    '[aria-label*="Close ad"], [aria-label*="Cerrar anuncio"]'
                );
                closeBtns.forEach(function(btn) {
                    if (btn.offsetParent !== null) btn.click();
                });
            }

            setInterval(skipVideoAds, 1000);

            var observer = new MutationObserver(function() {
                skipVideoAds();
            });
            observer.observe(document.body, { childList: true, subtree: true });

        })();
    """.trimIndent()
}
