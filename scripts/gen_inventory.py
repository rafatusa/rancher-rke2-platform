#!/usr/bin/env python3
"""Generate an Ansible inventory from terraform outputs.

Run from the `infra/` directory (terraform must already be initialised there).
Writes an INI inventory to stdout:

    [rke2_server]
    master ansible_host=<master public ip> ...

    [rke2_agents]
    worker-1 ansible_host=<worker public ip> ...

The SSH user comes from the SSH_USER environment variable, which the platform
derives from the provisioned OS image (Ubuntu 22.04 -> "ubuntu"). It is never
hardcoded here.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys


def terraform_output() -> dict:
    """Read every terraform output as JSON."""
    try:
        raw = subprocess.run(
            ["terraform", "output", "-json"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except FileNotFoundError:
        sys.exit("terraform executable not found on PATH")
    except subprocess.CalledProcessError as exc:
        sys.exit(f"terraform output failed: {exc.stderr.strip()}")

    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        sys.exit(f"could not parse terraform output as JSON: {exc}")


def value_of(outputs: dict, name: str):
    if name not in outputs:
        sys.exit(f"terraform output '{name}' is missing - did provision run?")
    return outputs[name].get("value")


def main() -> None:
    ssh_user = os.environ.get("SSH_USER", "").strip()
    if not ssh_user:
        sys.exit("SSH_USER environment variable is required")

    outputs = terraform_output()

    master_ip = value_of(outputs, "master_public_ip")
    master_private_ip = value_of(outputs, "master_private_ip")
    worker_public_ips = value_of(outputs, "worker_public_ips") or []
    worker_private_ips = value_of(outputs, "worker_private_ips") or []
    rke2_token = value_of(outputs, "rke2_token")

    if not master_ip:
        sys.exit("master_public_ip output is empty")
    if len(worker_public_ips) != len(worker_private_ips):
        sys.exit("worker public/private IP output lengths disagree")

    ssh_common = (
        "ansible_user={user} "
        "ansible_ssh_private_key_file=~/.ssh/deploy_key "
        "ansible_ssh_common_args='-o StrictHostKeyChecking=accept-new "
        "-o UserKnownHostsFile=/dev/null -o ConnectTimeout=20'"
    ).format(user=ssh_user)

    lines: list[str] = []
    lines.append("[rke2_server]")
    lines.append(
        f"master ansible_host={master_ip} node_private_ip={master_private_ip} {ssh_common}"
    )
    lines.append("")
    lines.append("[rke2_agents]")
    for index, (public_ip, private_ip) in enumerate(
        zip(worker_public_ips, worker_private_ips), start=1
    ):
        lines.append(
            f"worker-{index} ansible_host={public_ip} "
            f"node_private_ip={private_ip} {ssh_common}"
        )
    lines.append("")
    lines.append("[rke2_cluster:children]")
    lines.append("rke2_server")
    lines.append("rke2_agents")
    lines.append("")
    lines.append("[all:vars]")
    lines.append(f"master_public_ip={master_ip}")
    lines.append(f"master_private_ip={master_private_ip}")
    lines.append(f"rke2_token={rke2_token}")
    lines.append("ansible_python_interpreter=/usr/bin/python3")

    print("\n".join(lines))


if __name__ == "__main__":
    main()
