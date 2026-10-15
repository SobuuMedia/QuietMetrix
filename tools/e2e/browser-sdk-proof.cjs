/** Real Chromium proof against the production JS library; no customer endpoint is contacted. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require(process.env.QM_PLAYWRIGHT_MODULE || 'playwright');
const sdkDir = process.env.QM_SDK_DIST_DIR || path.resolve(__dirname, '../../quietmetrix-sdk/build/dist/js/productionLibrary');
const server = http.createServer((req, res) => {
  if (req.url.startsWith('/sdk/')) {
    const file = path.join(sdkDir, path.basename(req.url.split('?')[0]));
    if (!fs.existsSync(file)) { res.writeHead(404).end(); return; }
    res.setHeader('Content-Type', 'text/javascript'); res.end(fs.readFileSync(file));
  } else {
    res.setHeader('Content-Type', 'text/html');
    res.end('<script type="module">import * as sdk from "/sdk/QuietMetrix-quietmetrix-sdk.mjs";window.sdk=sdk;</script>');
  }
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({headless:true});
  const posts = []; const outbound = [];
  try {
    const page = await browser.newPage();
    await page.route('https://sdk-proof.example/**', async route => {
      const request = route.request(); outbound.push(request.url());
      if (request.method() === 'POST') posts.push(JSON.parse(request.postData()));
      const response = request.url().includes('/sdk/capabilities') ? {capabilities:['counter_batch_receipts','unique_window_v2','counter_protocol_v2_hourly','counter_protocol_v2_schema2']} : {experiments:[],funnels:[],revision:0};
      await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(response)});
    });
    const origin = 'http://127.0.0.1:'+server.address().port;
    async function initialize() {
      await page.goto(origin); await page.waitForFunction(() => window.sdk);
      await page.evaluate(() => sdk.init({storageKeyPrefix:'browser_proof_',trackingEndpoint:'https://sdk-proof.example/api/v1',apiKey:'qm_ak_fixture',flushIntervalMs:60000,autoTrackInitialPageView:true,trackingAllowedByDefault:false}));
    }
    await initialize();
    await page.evaluate(async () => { await sdk.trackEvent('before_consent'); await sdk.flush(); });
    await page.waitForTimeout(200);
    assert.equal(outbound.length, 0, 'No configuration, raw events, or aggregate traffic before consent');
    await page.evaluate(async () => { sdk.setCookieConsent(true); await sdk.trackScreen('Login'); await sdk.trackEvent('login_open', {screen:'Login'}); await sdk.flush(); });
    await page.waitForTimeout(200);
    assert(posts.length > 0, 'Consent must enable aggregate delivery');
    const envelopes = posts.filter(body => body.schema === 2);
    assert(envelopes.length > 1, 'Expanded counters must span bounded envelopes');
    for (const body of envelopes) {
      assert(body.items.length <= 512);
      if (process.env.QM_EXPECTED_SDK_VERSION) assert.equal(body.sdk.version, process.env.QM_EXPECTED_SDK_VERSION);
    }
    assert(outbound.every(url => !url.includes('/track')), 'Legacy raw-event endpoint must not be used');
    for (const body of posts) {
      const json = JSON.stringify(body);
      assert(!/anonymous_id|anonymousId|session_id|device_model|user_agent/.test(json), 'No raw identity/fingerprint fields');
    }
    await page.reload(); await page.waitForFunction(() => window.sdk);
    await page.evaluate(() => sdk.init({storageKeyPrefix:'browser_proof_',trackingEndpoint:'https://sdk-proof.example/api/v1',apiKey:'qm_ak_fixture',flushIntervalMs:60000,autoTrackInitialPageView:false,trackingAllowedByDefault:false}));
    assert(await page.evaluate(() => sdk.isTrackingAllowed()), 'Consent persists through reload');
    await page.evaluate(() => { sdk.setCookieConsent(false); sdk.setAnalyticsEnabled(false); sdk.stop(); });
    await page.waitForTimeout(200); const afterRevocation = outbound.length;
    await page.reload(); await page.waitForFunction(() => window.sdk);
    await page.evaluate(() => sdk.init({storageKeyPrefix:'browser_proof_',trackingEndpoint:'https://sdk-proof.example/api/v1',apiKey:'qm_ak_fixture',flushIntervalMs:60000,autoTrackInitialPageView:true,trackingAllowedByDefault:true}));
    assert(!await page.evaluate(() => sdk.isTrackingAllowed()), 'Explicit refusal overrides default after reload');
    await page.evaluate(async () => { await sdk.trackEvent('after_revocation'); await sdk.flush(); });
    await page.waitForTimeout(200);
    assert.equal(outbound.length, afterRevocation, 'Revoked consent prevents all subsequent traffic');
    const deniedContext = await browser.newContext();
    await deniedContext.addInitScript(() => {
      Storage.prototype.getItem = () => { throw new DOMException('Fixture storage denial','SecurityError'); };
    });
    const deniedPage = await deniedContext.newPage(); let deniedTraffic = 0;
    await deniedPage.route('https://sdk-proof.example/**', async route => {
      deniedTraffic++; await route.fulfill({status:200,contentType:'application/json',body:'{}'});
    });
    await deniedPage.goto(origin); await deniedPage.waitForFunction(() => window.sdk);
    await deniedPage.evaluate(() => sdk.init({storageKeyPrefix:'denied_proof_',trackingEndpoint:'https://sdk-proof.example/api/v1',apiKey:'qm_ak_fixture',trackingAllowedByDefault:true,autoTrackInitialPageView:true}));
    assert(!await deniedPage.evaluate(() => sdk.isTrackingAllowed()), 'Unreadable consent must override an allowed default');
    await deniedPage.evaluate(async () => { await sdk.trackEvent('storage_denied'); await sdk.flush(); });
    assert.equal(deniedTraffic,0,'Unavailable persistent storage must fail closed');
    await deniedContext.close();
    console.log(`Browser SDK proof passed: ${posts.length} aggregate requests; bounded schema-2 envelopes; no pre-consent traffic; durable consent and revocation.`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; server.close(); });
