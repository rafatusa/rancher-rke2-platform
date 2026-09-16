#!/usr/bin/env bash
#
# Smoke tests against a deployed Rancher RKE2 platform.
#
# Usage: scripts/smoke-test.sh <master-public-ip>
#
# Verifies the externally observable contract only: ingress routing, the
# application endpoints, and Rancher reachability. Deeper cluster assertions
# live in cluster-health.sh, which runs on the master.
set -euo pipefail

MASTER_IP="${1:?usage: smoke-test.sh <master-public-ip>}"

API_HOST="api.${MASTER_IP}.sslip.io"
RANCHER_HOST="rancher.${MASTER_IP}.sslip.io"
API_URL="http://${API_HOST}"
RANCHER_URL="https://${RANCHER_HOST}"

PASS=0
FAIL=0

pass() { echo "  PASS  $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL  $*" >&2; FAIL=$((FAIL + 1)); }

# Retry wrapper: the ingress may still be converging when validation starts.
fetch() {
  curl --silent --show-error --location --max-time 20 \
       --retry 10 --retry-delay 10 --retry-all-errors "$@"
}

echo "Smoke testing ${API_URL} and ${RANCHER_URL}"
echo
echo "--- Employee API ---"

status=$(fetch -o /tmp/smoke_home.html -w '%{http_code}' "${API_URL}/" || echo "000")
if [ "$status" = "200" ]; then
  pass "GET / returned 200"
else
  fail "GET / returned ${status}, expected 200"
fi

if grep -qi "employee api" /tmp/smoke_home.html 2>/dev/null; then
  pass "landing page contains the application title"
else
  fail "landing page did not contain the expected title"
fi

status=$(fetch -o /tmp/smoke_health.json -w '%{http_code}' "${API_URL}/health" || echo "000")
if [ "$status" = "200" ]; then
  pass "GET /health returned 200"
else
  fail "GET /health returned ${status}, expected 200"
fi

if grep -q '"status":"UP"' /tmp/smoke_health.json 2>/dev/null; then
  pass "health endpoint reports status UP"
else
  echo "    body: $(cat /tmp/smoke_health.json 2>/dev/null || echo '<empty>')" >&2
  fail "health endpoint did not report UP"
fi

status=$(fetch -o /tmp/smoke_employees.json -w '%{http_code}' "${API_URL}/employees" || echo "000")
if [ "$status" = "200" ]; then
  pass "GET /employees returned 200"
else
  fail "GET /employees returned ${status}, expected 200"
fi

if grep -q '"name"' /tmp/smoke_employees.json 2>/dev/null; then
  pass "employees collection contains records"
else
  fail "employees collection was empty or malformed"
fi

status=$(fetch -o /dev/null -w '%{http_code}' "${API_URL}/employees/1" || echo "000")
if [ "$status" = "200" ]; then
  pass "GET /employees/1 returned 200"
else
  fail "GET /employees/1 returned ${status}, expected 200"
fi

status=$(curl --silent --output /dev/null --max-time 20 -w '%{http_code}' \
  "${API_URL}/employees/987654" || echo "000")
if [ "$status" = "404" ]; then
  pass "unknown employee id returned 404"
else
  fail "unknown employee id returned ${status}, expected 404"
fi

echo
echo "--- Ingress routing ---"

# Hitting the IP directly with a Host header proves ingress-nginx is routing by
# hostname rather than the request happening to land on a default backend.
status=$(curl --silent --output /dev/null --max-time 20 --retry 5 --retry-delay 10 \
  --retry-all-errors -H "Host: ${API_HOST}" -w '%{http_code}' "http://${MASTER_IP}/health" || echo "000")
if [ "$status" = "200" ]; then
  pass "ingress routes Host: ${API_HOST} to the application"
else
  fail "ingress host routing returned ${status}, expected 200"
fi

echo
echo "--- Rancher ---"

# Self-signed certificate issued by cert-manager, so --insecure is expected.
status=$(curl --silent --insecure --output /dev/null --max-time 25 \
  --retry 10 --retry-delay 10 --retry-all-errors -w '%{http_code}' \
  "${RANCHER_URL}/healthz" || echo "000")
if [ "$status" = "200" ]; then
  pass "Rancher /healthz returned 200"
else
  fail "Rancher /healthz returned ${status}, expected 200"
fi

status=$(curl --silent --insecure --output /dev/null --max-time 25 \
  --retry 5 --retry-delay 10 --retry-all-errors -w '%{http_code}' "${RANCHER_URL}/" || echo "000")
if [ "$status" = "200" ] || [ "$status" = "302" ]; then
  pass "Rancher UI responded (${status})"
else
  fail "Rancher UI returned ${status}, expected 200 or 302"
fi

echo
echo "==================================="
echo "  passed: ${PASS}   failed: ${FAIL}"
echo "==================================="

[ "$FAIL" -eq 0 ] || exit 1
echo "All smoke tests passed."
