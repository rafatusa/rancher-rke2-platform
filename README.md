# Rancher RKE2 Platform on AWS

A production-ready Kubernetes platform: **Terraform** provisions the AWS estate,
**Ansible** builds an **RKE2** cluster on it, **Rancher** manages the cluster,
and a **Spring Boot Employee API** is deployed onto it with **Helm** from
**GitHub Container Registry** — all driven by **GitHub Actions**.

Everything lives in this single repository.

---

## Architecture

```
                         Internet
                            │
                   ┌────────┴────────┐
                   │   Elastic IP    │   80 / 443 / 22
                   └────────┬────────┘
                            │
   ┌────────────────────────┼──────────────────────────────┐
   │ VPC 10.20.0.0/16       │  public subnet 10.20.1.0/24  │
   │                ┌───────┴────────┐                     │
   │                │  MASTER NODE   │  t3.large           │
   │                │  RKE2 server   │                     │
   │                │  ingress-nginx │  (hostNetwork DS)   │
   │                │  cert-manager  │                     │
   │                │  Rancher       │                     │
   │                └───┬───┬────┬───┘                     │
   │            9345 /  │   │    │  \ 9345                 │
   │        ┌───────────┘   │    └──────────┐              │
   │   ┌────┴─────┐   ┌─────┴────┐   ┌──────┴───┐          │
   │   │ WORKER 1 │   │ WORKER 2 │   │ WORKER 3 │ t3.medium│
   │   │RKE2 agent│   │RKE2 agent│   │RKE2 agent│          │
   │   └──────────┘   └──────────┘   └──────────┘          │
   │            (Employee API pods scheduled here)         │
   └───────────────────────────────────────────────────────┘
```

The authoritative diagram is [`.udap/architecture.d2`](.udap/architecture.d2);
the pipeline diagram is [`docs/cicd-pipeline.d2`](docs/cicd-pipeline.d2).

### Design decisions worth knowing

| Decision | Why |
| --- | --- |
| Dedicated VPC, not the default one | Cluster isolation; `10.20.0.0/16` does not collide with the account's existing VPCs. |
| Kubernetes API (6443) and RKE2 join port (9345) restricted to the VPC CIDR | The control plane is never exposed to the internet. CI operates the cluster over SSH to the master instead of shipping a kubeconfig through CI secrets. |
| ingress-nginx as a hostNetwork DaemonSet pinned to the master | Ports 80/443 on the Elastic IP reach the controller directly — no cloud load balancer to pay for or wait on. |
| `sslip.io` hostnames | No domain was supplied. `rancher.<ip>.sslip.io` resolves to the master's Elastic IP with zero DNS setup. Swap for a real domain when you have one. |
| Self-signed TLS from cert-manager | ACME needs a public domain. The issuer swaps to Let's Encrypt with a one-line values change. |
| Image tagged with the commit SHA | `:latest` is pushed but never deployed — pods always reference an immutable tag. |

---

## Stack

| Layer | Technology |
| --- | --- |
| Cloud | AWS (`us-east-1`) |
| Provisioning | Terraform `~> 5.60` AWS provider |
| Configuration | Ansible (`ansible-core` 2.16+) |
| OS | Ubuntu 22.04 LTS |
| Container runtime | containerd (bundled with RKE2) |
| Kubernetes | RKE2 `v1.30.5+rke2r1` |
| Management | Rancher `2.9.2` |
| Packaging | Helm `v3.15.4` |
| Ingress | ingress-nginx `4.11.2` |
| Certificates | cert-manager `v1.15.3` |
| Application | Spring Boot 3.3.4 / Java 17 / Gradle 8.10.2 |
| Registry | GitHub Container Registry |
| CI/CD | GitHub Actions |
| Monitoring | Amazon CloudWatch (agent on every node) |

---

## Repository layout

```
.
├── .udap/
│   ├── architecture.d2         architecture source of truth
│   ├── pipeline.yaml           pipeline spec (workflows are rendered from this)
│   └── notes.md                engineering notes and decisions
├── .github/workflows/          RENDERED — edit .udap/pipeline.yaml instead
│   ├── deploy.yml
│   ├── destroy.yml
│   └── validation.yml
├── infra/                      Terraform: VPC, subnet, IGW, SGs, IAM, 4x EC2, EIP
├── ansible/
│   ├── site.yml
│   ├── group_vars/all.yml      pinned component versions
│   └── roles/
│       ├── common/             OS prereqs, sysctl, kernel modules
│       ├── rke2_server/        control plane, kubectl, Helm, ops scripts
│       ├── rke2_agent/         worker join + Ready verification
│       └── platform/           cert-manager, ingress-nginx, Rancher
├── app/                        Spring Boot Employee API (Gradle, JaCoCo, REST Assured)
├── charts/employee-api/        Helm chart for the application
├── scripts/
│   ├── gen_inventory.py        terraform outputs -> Ansible inventory
│   └── smoke-test.sh           external smoke tests
├── tests/k6/load-test.js       k6 load test with thresholds
└── docs/                       deployment, operations, troubleshooting guides
```

---

## Endpoints

After a green deploy (`<IP>` is the master's Elastic IP, shown in the workflow summary):

| What | URL |
| --- | --- |
| Rancher UI | `https://rancher.<IP>.sslip.io` |
| Employee API landing page | `http://api.<IP>.sslip.io/` |
| Health | `http://api.<IP>.sslip.io/health` |
| Employees | `http://api.<IP>.sslip.io/employees` |
| One employee | `http://api.<IP>.sslip.io/employees/1` |

Rancher's certificate is self-signed, so your browser will warn once — expected.

---

## Quick start

1. Connect AWS and GitHub on the UDAP Integrations page.
2. Set the `RANCHER_BOOTSTRAP_PASSWORD` repository secret (the Rancher admin password).
3. Run the **deploy** workflow.
4. When it goes green, open the Rancher URL from the run summary.
5. Run the **validation** workflow for the full test suite.

Full detail: [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

---

## Pipelines

**`deploy.yml`** — `lint → test → build → image → provision → configure → release → verify`

Compiles, runs unit tests behind a JaCoCo coverage gate, builds and pushes the
container image, provisions AWS, configures the cluster with Ansible, deploys
the app with Helm, then verifies the cluster and both public endpoints.

**`validation.yml`** — `k8s_health → smoke → load`

Cluster health (nodes Ready, pods Running, Helm releases deployed, Rancher up),
smoke tests, REST Assured API/integration tests, and a k6 load test with
p95 < 800 ms and < 1 % error thresholds.

**`destroy.yml`** — `terraform destroy` against the same state key.

> The workflow files are **rendered** from `.udap/pipeline.yaml`. Edit the spec,
> never the YAML in `.github/workflows/`.

---

## Local development

```bash
cd app
gradle bootRun                  # http://localhost:8080
gradle test jacocoTestReport    # unit tests + coverage
gradle integrationTest -Papi.base.url=http://localhost:8080
```

Coverage report: `app/build/reports/jacoco/test/html/index.html`.

---

## Cost

Roughly **$175/month** on-demand in `us-east-1`:

| Item | Monthly |
| --- | --- |
| 1 × t3.large (master) | ~$61 |
| 3 × t3.medium (workers) | ~$90 |
| 4 × 40–60 GB gp3 EBS | ~$16 |
| Elastic IP (attached) | $0 |
| CloudWatch metrics + logs | ~$5–10 |

Run the **destroy** workflow when the platform is idle — it is the main cost lever.

---

## Documentation

- [Deployment Guide](docs/DEPLOYMENT.md) — prerequisites, first deploy, verification, rollback
- [Operations Guide](docs/OPERATIONS.md) — day-2 tasks, scaling, upgrades, backups, monitoring
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md) — failure modes and how to diagnose them
