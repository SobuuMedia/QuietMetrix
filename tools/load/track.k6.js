import http from 'k6/http';
import { check, sleep } from 'k6';

// Run only against a disposable test project. Limits remain enabled: 429 is measured separately.
export const options = {
  vus: Number(__ENV.VUS || 5), duration: __ENV.DURATION || '30s',
  thresholds: { http_req_duration: ['p(95)<1000'], checks: ['rate>0.99'] },
};
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY;
if (!API_KEY) throw new Error('API_KEY for a disposable load-test project is required');
export default function () {
  const batchId = `load_${Date.now()}_${__VU}_${__ITER}`;
  const payload = JSON.stringify({ schema: 2, batch_id: batchId,
    sdk: {platform: 'web', version: '0.6.0'}, items: [{
      day: new Date().toISOString().slice(0, 10), hour: null, metric: 'event_v2',
      segment: {kind: 'global', value: 'all'}, dimensions: {name: 'load_test_open'}, n: 1,
    }],
  });
  const response = http.post(`${BASE_URL}/api/v2/counters`, payload,
    {headers: {'Content-Type': 'application/json', 'X-QM-Api-Key': API_KEY}});
  check(response, {'accepted or explicitly throttled': r => r.status === 202 || r.status === 429});
  sleep(1);
}
