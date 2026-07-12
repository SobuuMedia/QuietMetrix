package com.quietmetrix.analytics.internal

/**
 * The analytics tracker JavaScript snippet, with two placeholders:
 *   __QM_ENDPOINT__     — the POST URL
 *   __QM_CONSENT_KEY__  — the localStorage key holding the consent value ("0" / "1")
 *
 * After substitution and injection, `window.__quietmetrixTrack(event, screen)` becomes
 * available globally. The IIFE shape is preserved so it can be appended as a single
 * <script> textContent without leaking helpers into the global scope.
 *
 * Lives in commonMain so both the `js` and `wasmJs` browser targets share the exact same
 * snippet (it is plain text with no platform interop).
 */
internal const val TRACKER_JS_TEMPLATE = """
(function () {
    var ANALYTICS_URL = '__QM_ENDPOINT__';
    var CONSENT_KEY = '__QM_CONSENT_KEY__';

    // Generate or reuse a session ID (resets on tab close)
    var sid = sessionStorage.getItem('_sid');
    if (!sid) {
        sid = Math.random().toString(36).slice(2) + Date.now().toString(36);
        sessionStorage.setItem('_sid', sid);
    }

    function isTrackingAllowed() {
        try { return localStorage.getItem(CONSENT_KEY) !== '0'; } catch(e) { return true; }
    }

    function send(event, screen) {
        if (!isTrackingAllowed()) return;
        try {
            var lang = navigator.language || null;
            var body = JSON.stringify({
                event:  event,
                screen: screen || null,
                sid:    sid,
                ctx: {
                    referrer: document.referrer || null,
                    language: lang ? lang.split('-')[0] : null,
                    ua:       navigator.userAgent || null,
                    viewport: (window.innerWidth || '') + 'x' + (window.innerHeight || ''),
                    country:  (lang && lang.indexOf('-') !== -1) ? lang.split('-')[1].toUpperCase() : null,
                },
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
