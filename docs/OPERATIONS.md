# Operations Guide

Day-2 operation of the Rancher RKE2 platform.

---

## Access

```bash
# The platform-managed private key; SSH_USER is "ubuntu" for Ubuntu 22.04.
ssh -i ~/.ssh/deploy_key ubuntu@<MASTER_IP>

# kubectl and helm are on PATH on the master, KUBECONFIG is preset.
sudo kubectl get nodes -o wide
sudo helm list --all-namespaces
```

Worker nodes have no Elastic IP but do have public IPs in the same subnet; they
are listed in the `worker_public_ips` Terraform output.

Break-glass access without SSH is available through AWS Session Manager — every
node carries `AmazonSSMManagedInstanceCore`.

---

## Routine checks

```bash
# The same script the verify and validation pipelines run.
sudo /usr/local/bin/cluster-health.sh
```

It asserts: all nodes registered and Ready, no pods outside Running/Succeeded,
cert-manager / ingress-nginx / Rancher Helm releases `deployed`, Rancher rollout
complete, ingress controller DaemonSet rolled out.

| Check | Command |
| --- | --- |
| Node status | `sudo kubectl get nodes -o wide` |
| Pod status | `sudo kubectl get pods -A` |
| Recent events | `sudo kubectl get events -A --sort-by=.lastTimestamp` |
| Resource pressure | `sudo kubectl top nodes` |
| RKE2 server log | `sudo journalctl -u rke2-server -f` |
| RKE2 agent log | `sudo journalctl -u rke2-agent -f` |
| App logs | `sudo kubectl -n employee-api logs -l app.kubernetes.io/name=employee-api -f` |

---

## Scaling

### More workers

1. Set `worker_count` in `infra/variables.tf` (or pass `TF_VAR_worker_count`).
2. Run the deploy workflow.

Terraform adds the instances, the inventory generator picks them up, and the
`rke2_agent` role joins them. Existing nodes are untouched — the play is
idempotent.

> Removing workers scales the *count* down, and Terraform destroys the
> highest-indexed instances. Drain them first:
> `sudo kubectl drain <node> --ignore-daemonsets --delete-emptydir-data`.

### More application replicas

```bash
sudo helm upgrade employee-api /tmp/employee-api-chart \
  -n employee-api --reuse-values --set replicaCount=4 --wait
```

Persist it by editing `replicaCount` in `charts/employee-api/values.yaml` and
redeploying — otherwise the next deploy resets it.

### Bigger nodes

Change `master_instance_type` / `worker_instance_type` and redeploy. **This
replaces the instances.** The master holds etcd, so take a snapshot first (see
Backups) and expect downtime.

---

## Upgrades

Every version is pinned in `ansible/group_vars/all.yml`. Upgrade one component
at a time and redeploy:

```yaml
rke2_version: "v1.30.5+rke2r1"
cert_manager_version: "v1.15.3"
ingress_nginx_chart_version: "4.11.2"
rancher_chart_version: "2.9.2"
helm_version: "v3.15.4"
```

**RKE2**: upgrade the server first, then agents — the play already orders them
this way. Never skip a minor version. Check the Rancher support matrix before
moving Kubernetes: Rancher pins the Kubernetes versions it supports.

**Rancher**: read the release notes for your jump. Snapshot etcd first — a
failed Rancher upgrade is recovered by restoring etcd, not by re-running Helm.

---

## Backups

### etcd (the cluster's entire state)

RKE2 takes automatic snapshots every 6 hours, 10 retained (configured in
`ansible/roles/rke2_server/templates/config.yaml.j2`).

```bash
# List
sudo ls -lh /var/lib/rancher/rke2/server/db/snapshots/

# Take one on demand
sudo rke2 etcd-snapshot save --name pre-upgrade-$(date +%Y%m%d)

# Restore (destructive; stops the cluster)
sudo systemctl stop rke2-server
sudo rke2 server --cluster-reset \
  --cluster-reset-restore-path=/var/lib/rancher/rke2/server/db/snapshots/<snapshot>
sudo systemctl start rke2-server
```

Snapshots live on the master's EBS volume. **They do not survive losing the
master.** Ship them to S3 for real durability — this is the top recommended
Tier-2 enhancement for this platform.

### Application state

The Employee API is intentionally stateless (in-memory store). Nothing to back
up. Adding a database means adding a backup story with it.

---

## Monitoring

The CloudWatch agent runs on every node, publishing to namespace
`RancherRKE2/<project>`:

- `mem_used_percent`
- `disk used_percent` (root volume)
- `cpu_usage_idle`, `cpu_usage_iowait`

Log groups: `/<project>/syslog` and `/<project>/rke2`, 14-day retention.

Suggested alarms (not provisioned by default):

| Alarm | Threshold |
| --- | --- |
| Master memory | `mem_used_percent > 85` for 10 min |
| Any node disk | `used_percent > 80` |
| Instance health | `StatusCheckFailed >= 1` |

In-cluster observability is available through Rancher: **Cluster → Monitoring**
installs the Prometheus/Grafana stack. Budget ~2 GB of memory for it — consider
a larger master first.

---

## Certificate rotation

Rancher's self-signed certificate is issued by cert-manager and renewed
automatically.

```bash
sudo kubectl -n cattle-system get certificate
sudo kubectl -n cattle-system describe certificate tls-rancher-ingress
```

Force a renewal:

```bash
sudo kubectl -n cattle-system delete secret tls-rancher-ingress
# cert-manager reissues within ~60s
```

---

## Secret rotation

**Rancher admin password** — change it in the Rancher UI (User → Preferences).
The `RANCHER_BOOTSTRAP_PASSWORD` secret only seeds the *initial* password;
changing it later does not change a live installation.

**SSH keys** — rotate from the UDAP Integrations page. The key pair and the
instances must be replaced in the same run; a half-rotation leaves nodes
unreachable.

**GHCR** — `GITHUB_TOKEN` is per-run and needs no rotation. The in-cluster pull
secret is recreated on every deploy.

---

## Cost control

| Action | Saving |
| --- | --- |
| Run the destroy workflow when idle | 100 % |
| Stop instances overnight (state preserved, EIP keeps the address) | ~70 % |
| Drop to 2 workers | ~$30/month |
| 1-year Savings Plan | ~30 % |

Stopped instances still bill for EBS. An Elastic IP **not** attached to a
running instance bills hourly — that is the trap when stopping instances.
