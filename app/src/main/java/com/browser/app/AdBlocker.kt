package com.browser.app

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

object AdBlocker {

    var enabled = true

    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    private val blockedDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "connect.facebook.net",
        "amazon-adsystem.com", "assoc-amazon.com",
        "ads.youtube.com", "ad.youtube.com", "googleads.g.doubleclick.net",
        "static.doubleclick.net", "adnxs.com", "adsrvr.org", "adform.net",
        "advertising.com", "adcolony.com", "admob.com", "adroll.com",
        "ads.twitter.com", "ads.linkedin.com", "mediamath.com", "moatads.com",
        "openx.net", "pubmatic.com", "rubiconproject.com",
        "scorecardresearch.com", "taboola.com", "outbrain.com",
        "revcontent.com", "mgid.com", "criteo.com", "criteo.net",
        "thetradedesk.com", "casalemedia.com", "sharethrough.com",
        "appnexus.com", "zedo.com", "hotjar.com", "segment.com",
        "mixpanel.com", "amplitude.com", "quantserve.com", "quantcast.com",
        "chartbeat.com", "fullstory.com", "optimizely.com", "appsflyer.com",
        "popcash.net", "popads.net", "exoclick.com", "juicyads.com",
        "coinhive.com", "coin-hive.com", "cryptoloot.pro"
    )

    private val blockedPatterns = listOf(
        "/ads/", "/ad/", "/adserver/", "/advertisement", "/banner",
        "/tracking", "/pixel.gif", "/pixel.png", "/beacon.",
        "googlesyndication", "doubleclick", "coinhive",
        "/prebid", "vast.xml", "vast_url", "/ima/", "imasdk.googleapis.com"
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

    fun getAdHidingCss(): String = if (!enabled) "" else """
        (function() {
            var style = document.createElement('style');
            style.textContent = [
                '[class*="banner-ad"]', '[class*="google-ad"]',
                '[id*="google_ads"]', '[id*="div-gpt-ad"]',
                'ins.adsbygoogle', '[class*="advertisement"]',
                '[class*="ad-container"]', '[class*="ad-wrapper"]',
                '.taboola-widget', '.outbrain-widget',
                '#taboola-below-article'
            ].join(',') + ' { display:none !important; }';
            document.head.appendChild(style);
        })();
    """.trimIndent()
}
