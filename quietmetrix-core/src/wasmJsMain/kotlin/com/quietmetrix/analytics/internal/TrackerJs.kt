package com.quietmetrix.analytics.internal

/**
 * The analytics tracker JavaScript snippet, with two placeholders:
 *   __QM_ENDPOINT__     — the POST URL
 *   __QM_CONSENT_KEY__  — the localStorage key holding the consent value ("0" / "1")
 *
 * After substitution and injection, `window.__quietmetrixTrack(event, screen)` becomes
 * available globally. The IIFE shape is preserved so it can be appended as a single
 * <script> textContent without leaking helpers into the global scope.
 */
internal const val TRACKER_JS_TEMPLATE = """
(function () {
    var ANALYTICS_URL = '__QM_ENDPOINT__';

    // Generate or reuse a session ID (resets on tab close)
    var sid = sessionStorage.getItem('_sid');
    if (!sid) {
        sid = Math.random().toString(36).slice(2) + Date.now().toString(36);
        sessionStorage.setItem('_sid', sid);
    }

    function isTrackingAllowed() {
        try { return localStorage.getItem('__QM_CONSENT_KEY__') !== '0'; } catch(e) { return true; }
    }

    function send(event, screen) {
        if (!isTrackingAllowed()) return;
        try {
            var body = JSON.stringify({
                event:    event,
                screen:   screen   || null,
                referrer: document.referrer || null,
                language: navigator.language ? navigator.language.split('-')[0] : null,
                ua:       navigator.userAgent || null,
                width:    window.innerWidth   || null,
                sid:      sid,
            });
            var blob = new Blob([body], { type: 'application/json' });
            navigator.sendBeacon
                ? navigator.sendBeacon(ANALYTICS_URL, blob)
                : fetch(ANALYTICS_URL, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body, keepalive: true });
        } catch (e) {}
    }

    // __QM_INITIAL_PAGE_VIEW__
    send('page_view', null);

    window.__quietmetrixTrack = function (event, screen) {
        send(event, screen || null);
    };
})();
"""
