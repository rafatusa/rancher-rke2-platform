# Deployment Guide

How to take this repository from nothing to a running Rancher-managed RKE2
platform with the Employee API deployed on it.

---

## 1. Prerequisites

### Cloud

An AWS account with permission to create VPCs, EC2 instances, Elastic IPs, IAM
roles and instance profiles. The deploy consumes:

| Resource | Count | Quota to check |
| --- | --- | --- |
| vCPU (standard on-demand) | 8 | *Running On-Demand Standard instances* |
| Elastic IP | 1 | *Elastic IPs* (default 5) |
| VPC | 1 | *VPCs per region* (default 5) |

### Repository secrets

Most secrets are set by the platform at deploy time and need no action:

| Secret | Set by | Purpose |
| --- | --- | --- |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | platform | Terraform + AWS API |
| `PROJECT_NAME` | platform | Resource name prefix and state key |
| `TF_STATE_BUCKET` | platform | Terraform remote state |
| `SSH_USER`, `SSH_PRIVATE_KEY`, `SSH_PUBLIC_KEY` | platform | Ansible access to the nodes |
| `GITHUB_TOKEN` | GitHub | GHCR push and pull |
| **`RANCHER_BOOTSTRAP_PASSWORD`** | **you** | **Rancher admin password** |

`RANCHER_BOOTSTRAP_PASSWORD` must be at least 12 characters. Use alphanumerics
only — it travels through a shell command line, and shell metacharacters break
the Ansible invocation.

```bash
# Generate a safe one
tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24; echo
```

---

## 2. Deploy

Run the **deploy** workflow (Actions → deploy → Run workflow), or trigger a
deploy from the UDAP console.

### What each stage does

| Stage | Duration | What happens |
| --- | --- | --- |
| `lint` | ~2 min | Compiles main, test and integration-test sources. |
| `test` | ~3 min | Unit tests; JaCoCo gate at 70 % instruction / 50 % branch coverage. |
| `build` | ~2 min | `gradle bootJar` produces `employee-api.jar`. |
| `image` | ~4 min | Multi-stage Docker build; pushes `ghcr.io/<owner>/employee-api:<sha>` and `:latest`. |
| `provision` | ~4 min | `terraform apply`: VPC, subnet, IGW, route table, security groups, IAM, key pair, 4 EC2 instances, Elastic IP. |
| `configure` | **~25–40 min** | Ansible: OS prep, RKE2 server, three agents join, Helm, cert-manager, ingress-nginx, Rancher. |
| `release` | ~5 min | Copies the chart to the master, `helm upgrade --install --wait`, waits for the rollout. |
| `verify` | ~3 min | Cluster health, Employee API `/health`, Rancher `/healthz`. |

**Total: roughly 45–60 minutes** on a cold deploy. The `configure` stage is the
long one — RKE2 pulls its whole image set on four machines and Rancher takes
several minutes to become ready. This is normal; the stage has a 120-minute
timeout.

---

## 3. Verify

The `verify` stage writes a summary table with the live URLs. Check it yourself:

```bash
MASTER_IP=<elastic ip from the run summary>

curl "http://api.${MASTER_IP}.sslip.io/health"
curl "http://api.${MASTER_IP}.sslip.io/employees"
curl -k -o /dev/null -w '%{http_code}\n' "https://rancher.${MASTER_IP}.sslip.io/healthz"
```

Then run the **validation** workflow for the full suite: cluster health, smoke
tests, REST Assured API tests, and the k6 load test.

### Log in to Rancher

1. Open `https://rancher.<IP>.sslip.io`.
2. Accept the self-signed certificate warning (expected — see the README).
3. Log in as `admin` with `RANCHER_BOOTSTRAP_PASSWORD`.
4. Set a new password when prompted.
5. The `local` cluster is this RKE2 cluster: 1 control-plane + 3 workers, all Ready.

---

## 4. Deploying a change

```
edit code → commit → run deploy
```

The deploy is idempotent end to end:

- `terraform apply` is a no-op when nothing in `infra/` changed.
- Ansible converges — a healthy cluster reports `changed=0`.
- Helm rolls the new image tag with `maxUnavailable: 0`, so the API stays up.

An application-only change therefore skips straight past provisioning and
reaches `release` in a few minutes.

---

## 5. Rollback

**Application** — roll the Helm release back on the master:

```bash
ssh -i <key> ubuntu@<MASTER_IP>
sudo helm history employee-api -n employee-api
sudo helm rollback employee-api <revision> -n employee-api --wait
```

**Whole platform** — use the platform's rollback action, which reverts the
repository to the last green commit and redeploys it. Applying the previous
Terraform configuration *is* the infrastructure rollback.

**Do not** hand-edit cluster objects to fix a bad release: the next deploy
reconciles them away.

---

## 6. Teardown

Run the **destroy** workflow. It runs `terraform destroy` against the same state
key and removes every resource this project created: instances, Elastic IP,
security groups, IAM role and instance profile, subnet, route table, gateway and
VPC.

The repository and all `.udap/` configuration survive. Redeploying later is just
a deploy — no re-scaffolding.

> CloudWatch log groups created by the agent (`/<project>/syslog`,
> `/<project>/rke2`) are **not** Terraform-managed and survive teardown. Delete
> them manually if you want a completely clean account.

---

## 7. Switching to a real domain and real TLS

The sslip.io hostnames and self-signed certificate exist only because no domain
was provided. With a domain:

1. Point `rancher.example.com` and `api.example.com` at the master's Elastic IP (A records).
2. In `ansible/group_vars/all.yml`, set `rancher_hostname: rancher.example.com`.
3. In `ansible/roles/platform/templates/rancher-values.yaml.j2`, change:
   ```yaml
   ingress:
     tls:
       source: letsEncrypt
   letsEncrypt:
     email: you@example.com
     ingress:
       class: nginx
   ```
4. In `charts/employee-api/values.yaml`, set `ingress.host` and add a
   `cert-manager.io/cluster-issuer` annotation.
5. Redeploy.
