/** Runs the Maven-only WASM consumer with working and denied browser storage. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require(process.env.QM_PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '../release-consumers/wasm/build/dist/wasmJs/productionExecutable');
const server = http.createServer((req, res) => {
  if (req.url === '/') {
    res.setHeader('Content-Type', 'text/html');
    res.end('<script src="/quietmetrix-wasm-release-proof.js"></script>'); return;
  }
  const file = path.resolve(root, '.' + req.url.split('?')[0]);
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404).end(); return;
  }
  res.setHeader('Content-Type', file.endsWith('.wasm') ? 'application/wasm' : 'text/javascript');
  res.end(fs.readFileSync(file));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({headless:true});
  try {
    for (const denied of [false, true]) {
      const context = await browser.newContext();
      if (denied) await context.addInitScript(() => {
        Storage.prototype.getItem = () => { throw new DOMException('Fixture denial', 'SecurityError'); };
      });
      const page = await context.newPage(); const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto('http://127.0.0.1:' + server.address().port);
      await page.waitForFunction(() => localStorage.consumer_cookie_consent === '0' && localStorage.consumer_analytics_enabled === '0');
      assert.deepEqual(errors, [], 'Published WASM SDK must initialize and fail closed without uncaught errors');
      await context.close();
    }
    console.log('Published WASM browser proof passed: durable refusal, opt-out, and denied-storage initialization.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; server.close(); });
