import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 250 },
    { duration: '2m', target: 500 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<100'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'qm_ak_test_key_replace_me';

export default function () {
  const payload = JSON.stringify({
    event: `load_test_event_${__VU}_${__ITER}`,
    screen: 'load_test',
    props: { iteration: __ITER, vu: __VU },
    sid: `load_session_${__VU}`,
    ts: new Date().toISOString(),
    sdk: { platform: 'k6', version: '0.1.0' },
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-QM-Api-Key': API_KEY,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/track`, payload, params);

  check(res, {
    'status is 202': (r) => r.status === 202,
    'response has ok': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.ok === true;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.05);
}

export function handleSummary(data) {
  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}