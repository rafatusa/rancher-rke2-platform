#!/usr/bin/env bash
#
# Manual cluster bootstrap / re-convergence helper.
#
# The deploy pipeline normally does all of this. Use this script when you want
# to re-run the configuration by hand from a workstation — for example after
# fixing a node, or to verify the playbook is idempotent against a live cluster.
#
# Usage:
#   scripts/bootstrap-cluster.sh [--check]
#
#   --check   run Ansible in check mode (no changes made)
#
# Requirements:
#   - terraform initialised in infra/ against the project's state backend
#   - ansible-core plus community.general and ansible.posix collections
#   - the project's SSH private key at ~/.ssh/deploy_key (chmod 600)
#   - SSH_USER and RANCHER_BOOTSTRAP_PASSWORD exported
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK_MODE=""

if [ "${1:-}" = "--check" ]; then
  CHECK_MODE="--check --diff"
  echo "Running in CHECK MODE — no changes will be made."
fi

: "${SSH_USER:?export SSH_USER (ubuntu for Ubuntu 22.04)}"
: "${RANCHER_BOOTSTRAP_PASSWORD:?export RANCHER_BOOTSTRAP_PASSWORD}"

if [ ! -f "$HOME/.ssh/deploy_key" ]; then
  echo "FAIL: ~/.ssh/deploy_key not found." >&2
  echo "Fetch the project's private key from the platform and chmod 600 it." >&2
  exit 1
fi

command -v ansible-playbook >/dev/null 2>&1 || {
  echo "FAIL: ansible-playbook not on PATH. Install ansible-core first." >&2
  exit 1
}

echo "==> Reading infrastructure outputs"
cd "${REPO_ROOT}/infra"

if ! terraform output -raw master_public_ip >/dev/null 2>&1; then
  echo "FAIL: terraform outputs unavailable." >&2
  echo "Run terraform init in infra/ with the project's -backend-config flags." >&2
  exit 1
fi

MASTER_IP="$(terraform output -raw master_public_ip)"
echo "    master: ${MASTER_IP}"

echo "==> Generating Ansible inventory"
SSH_USER="$SSH_USER" python3 "${REPO_ROOT}/scripts/gen_inventory.py" \
  > "${REPO_ROOT}/ansible/inventory.ini"
grep -v 'rke2_token' "${REPO_ROOT}/ansible/inventory.ini"

echo "==> Trusting host keys"
mkdir -p "$HOME/.ssh"
while read -r ip; do
  [ -n "$ip" ] || continue
  ssh-keyscan -T 20 -H "$ip" >> "$HOME/.ssh/known_hosts" 2>/dev/null || true
done < <(terraform output -json node_public_ips | python3 -c \
  'import json,sys; [print(x) for x in json.load(sys.stdin)]')

echo "==> Running the platform playbook"
cd "${REPO_ROOT}/ansible"

# shellcheck disable=SC2086
ansible-playbook -i inventory.ini site.yml $CHECK_MODE \
  -e "rancher_bootstrap_password=${RANCHER_BOOTSTRAP_PASSWORD}"

echo
echo "==> Cluster health"
ssh -i "$HOME/.ssh/deploy_key" "${SSH_USER}@${MASTER_IP}" \
  "sudo /usr/local/bin/cluster-health.sh"

cat <<SUMMARY

=========================================================
  Platform is converged.

  Rancher UI    https://rancher.${MASTER_IP}.sslip.io
  Employee API  http://api.${MASTER_IP}.sslip.io
=========================================================
SUMMARY
