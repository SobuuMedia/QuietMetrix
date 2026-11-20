/** Production browser SDK against an explicitly supplied disposable backend/project. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require(process.env.QM_PLAYWRIGHT_MODULE || 'playwright');
const backend = process.env.QM_SDK_PROOF_URL;
const apiKey = process.env.QM_SDK_PROOF_API_KEY;
assert(backend && apiKey, 'Supply a disposable backend and project API key');
const sdkDir = path.resolve(__dirname, '../../quietmetrix-sdk/build/dist/js/productionLibrary');
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
  const deadline = setTimeout(() => { console.error("Live SDK fixture exceeded five minutes"); browser.close(); }, 300000);
  const receipts = new Map(); let replayed = 0; let dropped = false;
  try {
    for (let install = 0; install < 5; install++) {
      console.log(`Checking anonymous installation ${install + 1}/5`);
      const context = await browser.newContext(); const page = await context.newPage();
      await page.route('https://sdk-live-proof.example/**', async route => {
        const req = route.request(); const body = req.postData();
        let response;
        for (let attempt = 0; attempt < 3; attempt++) {
          response = await fetch(backend + new URL(req.url()).pathname, {
            method: req.method(), headers: {'X-QM-Api-Key':apiKey,'Content-Type':'application/json'},
            ...(body ? {body} : {})
          });
          if (response.status !== 429) break;
          const seconds = Number(response.headers.get('retry-after'));
          assert(seconds > 0 && seconds <= 60, 'Unexpected daily quota exhaustion in the fixture');
          await response.text();
          await new Promise(resolve => setTimeout(resolve, seconds * 1000));
        }
        const text = await response.text();
        assert(response.ok, `Backend rejected SDK request: ${new URL(req.url()).pathname} ${response.status} ${text}; fields=${body ? Object.keys(JSON.parse(body)) : []}`);
        if (body) {
          const envelope = JSON.parse(body);
          assert((envelope.items || envelope.counters).length <= 512);
          assert(!/anonymous_id|anonymousId|session_id|device_model|user_agent/.test(body));
          const previous = receipts.get(envelope.batch_id);
          if (previous) assert.equal(body, previous, 'Retry must preserve the immutable payload');
          receipts.set(envelope.batch_id, body);
          let ack; try { ack = JSON.parse(text); } catch { throw new Error('Non-JSON aggregate acknowledgement: '+text.slice(0,1200)); } if (ack.replayed) replayed++;
          // Lose an acknowledgement after the database commits, then reload the
          // browser to force durable outbox replay against server receipts.
          if (!dropped && install === 0 && envelope.schema === 2) { dropped = true; await route.abort('failed'); return; }
        }
        await route.fulfill({status:response.status,contentType:'application/json',body:text});
      });
      async function init() {
        await page.goto('http://127.0.0.1:'+server.address().port);
        await page.waitForFunction(() => window.sdk);
        await page.evaluate(apiKey => sdk.init({storageKeyPrefix:'live_proof_',trackingEndpoint:'https://sdk-live-proof.example/api/v1',apiKey,flushIntervalMs:60000,autoTrackInitialPageView:false,trackingAllowedByDefault:false}), apiKey);
      }
      await init();
      console.log(`Installation ${install+1}: initialized`);
      await page.evaluate(async () => { sdk.setCookieConsent(true); await sdk.trackScreen('Login'); await sdk.trackEvent('login_open',{screen:'Login'}); await sdk.flush(); });
      console.log(`Installation ${install+1}: flush completed`);
      if (install === 0) {
        await init();
        await page.evaluate(async () => { await sdk.flush(); });
      }
      console.log(`Installation ${install+1}: closing context`);
      await context.close();
      console.log(`Installation ${install+1}: closed`);
    }
    assert(dropped && replayed > 0, 'Server must deduplicate the post-reload receipt retry');
    console.log(`Live browser SDK proof passed: 5 independent installs, ${receipts.size} bounded receipts, ${replayed} replay acknowledgements.`);
  } finally { clearTimeout(deadline); await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode=1; server.close(); });
