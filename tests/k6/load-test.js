import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Base URL is injected by the validation pipeline:
//   k6 run -e BASE_URL=http://api.<ip>.sslip.io tests/k6/load-test.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('application_errors');
const employeesLatency = new Trend('employees_latency', true);

export const options = {
  scenarios: {
    ramping_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },  // ramp up
        { duration: '1m', target: 25 },   // sustained load
        { duration: '30s', target: 25 },  // hold
        { duration: '20s', target: 0 },   // ramp down
      ],
      gracefulRampDown: '20s',
    },
  },
  thresholds: {
    // Fail the stage if the platform cannot keep these promises.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800', 'p(99)<2000'],
    employees_latency: ['p(95)<800'],
    application_errors: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  group('health', () => {
    const res = http.get(`${BASE_URL}/health`, {
      tags: { endpoint: 'health' },
      timeout: '30s',
    });
    const ok = check(res, {
      'health status is 200': (r) => r.status === 200,
      'health reports UP': (r) => r.json('status') === 'UP',
    });
    errorRate.add(!ok);
  });

  group('employees', () => {
    const res = http.get(`${BASE_URL}/employees`, {
      tags: { endpoint: 'employees' },
      timeout: '30s',
    });
    employeesLatency.add(res.timings.duration);
    const ok = check(res, {
      'employees status is 200': (r) => r.status === 200,
      'employees returns an array': (r) => Array.isArray(r.json()),
      'employees is not empty': (r) => r.json().length > 0,
    });
    errorRate.add(!ok);
  });

  group('employee by id', () => {
    const res = http.get(`${BASE_URL}/employees/1`, {
      tags: { endpoint: 'employee_by_id' },
      timeout: '30s',
    });
    const ok = check(res, {
      'employee by id status is 200': (r) => r.status === 200,
      'employee has a name': (r) => typeof r.json('name') === 'string',
    });
    errorRate.add(!ok);
  });

  group('landing page', () => {
    const res = http.get(`${BASE_URL}/`, {
      tags: { endpoint: 'home' },
      timeout: '30s',
    });
    const ok = check(res, {
      'home status is 200': (r) => r.status === 200,
      'home serves html': (r) => String(r.headers['Content-Type'] || '').includes('text/html'),
    });
    errorRate.add(!ok);
  });

  sleep(1);
}

export function handleSummary(data) {
  const p95 = data.metrics.http_req_duration
    ? data.metrics.http_req_duration.values['p(95)'].toFixed(1)
    : 'n/a';
  const failed = data.metrics.http_req_failed
    ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2)
    : 'n/a';

  return {
    stdout: `\nk6 load test summary\n  target:        ${BASE_URL}\n  p95 latency:   ${p95} ms\n  failed reqs:   ${failed} %\n  iterations:    ${data.metrics.iterations.values.count}\n\n`,
  };
}
