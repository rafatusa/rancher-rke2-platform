# Troubleshooting Guide

Failure modes of this platform, ordered by the stage where they surface.
Each entry: what you see → what it means → how to fix it.

**Diagnose before acting.** Read the first error in a log, not the last — later
errors are usually cascades.

---

## Pipeline: `test` fails on coverage

```
Rule violated for bundle employee-api: instructions covered ratio is 0.6, but expected minimum is 0.7
```

New code shipped without tests. **Write the tests.** Do not lower the threshold
in `app/build.gradle` — that is the gate doing its job.

Local reproduction:

```bash
cd app && gradle test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## Pipeline: `image` fails to push

```
denied: installation not allowed to Create organization package
```

The workflow's `GITHUB_TOKEN` lacks `packages: write`, or the organization
restricts package creation. Check **Settings → Actions → Workflow permissions**
(needs read *and* write), and organization package-creation policy.

---

## Pipeline: `provision` fails

### `Error: creating EC2 Instance: VcpuLimitExceeded`

Account vCPU quota. This deploy needs 8. Request an increase for *Running
On-Demand Standard instances*, or reduce `worker_count` / instance sizes. This
is **account-level** — retrying will not help.

### `Error: creating EC2 VPC: VpcLimitExceeded`

Five VPCs per region by default. Delete an unused VPC or request an increase.

### `Error: Backend initialization required`

The `terraform init` flags drifted. Every init in every stage must carry
`-reconfigure` plus the same bucket/key/region. Fix the init, **never** write
import scripts around lost state.

### Duplicate resource errors on a retry

Same root cause as above: the retry initialised a *different* state key, so
Terraform cannot see what the previous attempt built. Fix the backend flags; the
deterministic key means a correct retry always reconciles the partial estate.

---

## Pipeline: `configure` fails

### `UNREACHABLE! ... Permission denied (publickey)`

Work in this order — do not guess.

1. **Login user.** If the denial lists `gssapi-keyex,gssapi-with-mic`, the host
   is RHEL-family and `SSH_USER` is wrong. This project provisions Ubuntu 22.04,
   so `SSH_USER` must be `ubuntu`. Fix by redeploying through the platform (the
   user re-derives from the image), not by rotating keys.
2. **Key material.** Compare the derived public key with the stored one:
   ```bash
   ssh-keygen -y -f ~/.ssh/deploy_key
   ```
   Matches `SSH_PUBLIC_KEY` → the instance has stale `authorized_keys`; replace
   the instance (keys are seeded at launch and never updated afterwards).
   Does not match → the key store is inconsistent: rotate the project keys from
   Integrations. Do not burn retries on key-writing experiments.

### `UNREACHABLE!` with a timeout

Security group or boot timing. Port 22 is open to `ssh_ingress_cidr`
(`0.0.0.0/0` by default). The configure stage already waits up to 10 minutes for
`sshd`; a timeout past that means the instance failed to boot — check the EC2
console's system log.

### `couldn't resolve module/action 'community.general.modprobe'`

The Galaxy collections were not installed. The *Install Ansible* step must run
`ansible-galaxy collection install community.general ansible.posix` in the
**same step** that installs `ansible-core`. Ansible resolves every module before
executing anything, so this fails at parse time.

### `E: Unable to locate package` / `404 Not Found` on apt

A stale package index. The playbook uses plain `update_cache: true` with retries
and deliberately **no** `cache_valid_time` — cloud images ship a cache that
looks fresh and names superseded versions. If you see this, someone added
`cache_valid_time` back. Remove it.

### The play hangs on "Wait for the Kubernetes API"

RKE2 is still pulling images (several GB across four nodes). The task allows 15
minutes. If it expires:

```bash
ssh ubuntu@<MASTER_IP>
sudo journalctl -u rke2-server -n 200 --no-pager
```

Common causes: insufficient memory (the master needs ≥ 8 GB — do not shrink it
below `t3.large`), or no egress to `registry.rancher.com`.

### A worker never reaches Ready

```bash
ssh ubuntu@<WORKER_IP>
sudo journalctl -u rke2-agent -n 200 --no-pager
```

| Log line | Cause |
| --- | --- |
| `failed to get CA certs` | Cannot reach the master on 9345 — check the cluster security group. |
| `token is invalid` | The agent's token disagrees with the server's. Both come from the `rke2_token` Terraform output; a mismatch means the state was recreated. Re-run configure. |
| `no space left on device` | Root volume full. Raise `worker_root_volume_size`. |

### Rancher never becomes ready

```bash
sudo kubectl -n cattle-system get pods
sudo kubectl -n cattle-system logs -l app=rancher --tail=100
```

| Symptom | Cause |
| --- | --- |
| `Pending` | Not enough memory on the master. Rancher requests 1 GB and cert-manager/ingress take more. |
| `CrashLoopBackOff` with cert errors | cert-manager was not ready when Rancher installed. Re-run configure — ordering is enforced and the retry converges. |
| Ready, but `/healthz` times out | ingress-nginx is not on the master, so port 443 on the Elastic IP reaches nothing. Check `kubectl -n ingress-nginx get pods -o wide`. |

---

## Pipeline: `release` fails

### `ImagePullBackOff`

```bash
sudo kubectl -n employee-api describe pod -l app.kubernetes.io/name=employee-api
```

- `unauthorized` → the `ghcr-pull-secret` is stale or the package is private to
  another account. The deploy script recreates the secret each run; confirm the
  image reference is all-lowercase (GHCR rejects uppercase owners).
- `manifest unknown` → the `image` stage did not push this SHA. Check that stage.

### `CrashLoopBackOff`

```bash
sudo kubectl -n employee-api logs -l app.kubernetes.io/name=employee-api --previous
```

`--previous` is the important flag: it shows the *crashed* container, not the
one currently starting.

Likely causes: the JVM ran out of memory (raise `resources.limits.memory` above
768 Mi), or a read-only-filesystem write outside `/tmp` (the chart mounts an
emptyDir there).

### `timed out waiting for the condition`

The rollout did not finish within 10 minutes. Usually pods are `Pending` for
lack of schedulable capacity:

```bash
sudo kubectl -n employee-api get pods -o wide
sudo kubectl describe node <worker> | grep -A5 "Allocated resources"
```

---

## Pipeline: `verify` / `validation` fails

### `curl: (22) ... 503 Service Unavailable`

The ingress exists but has no ready endpoint behind it:

```bash
sudo kubectl -n employee-api get endpoints employee-api
```

Empty → no pod is passing its readiness probe. Go back to the pod logs above.

### `curl: (6) Could not resolve host: api.<ip>.sslip.io`

sslip.io is unreachable from the runner, or the IP in the hostname is wrong.
Test the ingress directly, bypassing DNS:

```bash
curl -H "Host: api.<IP>.sslip.io" "http://<IP>/health"
```

Works → a DNS problem, not a platform problem.

### k6 threshold breach

```
✗ http_req_duration..........: p(95)=1.4s
```

The platform is slower than its SLO. Check whether pods are being CPU-starved
(`kubectl top pods -n employee-api`) and whether replicas are spread across
workers. Raise `replicaCount` before touching the thresholds — the thresholds
are the contract.

---

## Cluster-level recovery

| Situation | Action |
| --- | --- |
| One worker wedged | `kubectl drain` it, then reboot or replace the instance. |
| Master wedged but etcd intact | `sudo systemctl restart rke2-server`, then re-run configure. |
| Master lost | Restore etcd from a snapshot on a new master, or redeploy from scratch — snapshots live on the master's EBS volume only. |
| Cluster unrecoverable | Destroy, then deploy. Nothing in the cluster is stateful by design. |

Escalate to a human rather than retrying when the cause is account-level: quota
limits, IAM denials, billing, or region availability. Retries cannot fix those.
