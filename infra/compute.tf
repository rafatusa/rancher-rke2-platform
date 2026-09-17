data "aws_ami" "ubuntu_2204" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

# The platform generates and stores exactly one keypair per project; terraform
# registers the public half. Never inject keys through user_data.
resource "aws_key_pair" "main" {
  key_name   = "${var.project_name}-key"
  public_key = var.ssh_public_key

  tags = {
    Name = "${var.project_name}-key"
  }
}

# Shared RKE2 cluster join token. Generated once and kept in state so that
# re-running apply never rotates it out from under a joined cluster.
resource "random_password" "rke2_token" {
  length  = 40
  special = false

  keepers = {
    cluster = var.project_name
  }
}

locals {
  cloudwatch_agent_config = jsonencode({
    agent = {
      metrics_collection_interval = 60
      run_as_user                 = "cwagent"
    }
    metrics = {
      namespace = "RancherRKE2/${var.project_name}"
      append_dimensions = {
        InstanceId = "$${aws:InstanceId}"
      }
      metrics_collected = {
        mem = {
          measurement = ["mem_used_percent"]
        }
        disk = {
          measurement = ["used_percent"]
          resources   = ["/"]
        }
        cpu = {
          measurement                 = ["cpu_usage_idle", "cpu_usage_iowait"]
          totalcpu                    = true
          metrics_collection_interval = 60
        }
      }
    }
    logs = {
      logs_collected = {
        files = {
          collect_list = [
            {
              file_path         = "/var/log/syslog"
              log_group_name    = "/${var.project_name}/syslog"
              log_stream_name   = "{instance_id}"
              retention_in_days = 14
            },
            {
              file_path         = "/var/log/rke2-install.log"
              log_group_name    = "/${var.project_name}/rke2"
              log_stream_name   = "{instance_id}"
              retention_in_days = 14
            }
          ]
        }
      }
    }
  })

  # NOTE: this heredoc is deliberately written at column 0 and uses <<EOT
  # (not <<-EOT). Terraform's <<- strips only the indentation COMMON to every
  # line, and the interpolated JSON below sits at column 0 — so with <<-EOT the
  # common indent computes to zero and NOTHING is stripped, leaving four spaces
  # in front of the shebang. A shebang is only honoured when "#!" are the first
  # two bytes of the file, so the kernel ignored it, ran the script under dash,
  # and it died on `set -o pipefail` (dash has no pipefail). Keep column 0.
  node_user_data = <<EOT
#!/bin/bash
set -euxo pipefail

# Bootstrap only: everything else is Ansible's job.
export DEBIAN_FRONTEND=noninteractive
for i in $(seq 1 10); do
  apt-get update -y && break || sleep 15
done
apt-get install -y curl unzip

# CloudWatch agent. Non-fatal: RKE2 is the critical path, and a transient
# S3/download failure must not stop the node from finishing bootstrap.
install_cloudwatch_agent() {
  ARCH=$(dpkg --print-architecture)
  curl -fsSL --retry 5 --retry-delay 10 -o /tmp/amazon-cloudwatch-agent.deb \
    "https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/$${ARCH}/latest/amazon-cloudwatch-agent.deb"
  dpkg -i -E /tmp/amazon-cloudwatch-agent.deb
  mkdir -p /opt/aws/amazon-cloudwatch-agent/etc
  cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<'CWJSON'
${local.cloudwatch_agent_config}
CWJSON
  /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
    -a fetch-config -m ec2 -s \
    -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
}

if install_cloudwatch_agent; then
  echo "cloudwatch agent configured"
else
  echo "WARNING: cloudwatch agent setup failed; continuing bootstrap" >&2
fi
EOT
}

resource "aws_instance" "master" {
  ami                    = data.aws_ami.ubuntu_2204.id
  instance_type          = var.master_instance_type
  subnet_id              = aws_subnet.public.id
  key_name               = aws_key_pair.main.key_name
  iam_instance_profile   = aws_iam_instance_profile.node.name
  vpc_security_group_ids = [aws_security_group.cluster.id, aws_security_group.ingress.id]
  user_data              = local.node_user_data

  root_block_device {
    volume_size           = var.master_root_volume_size
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  # IAM instance profiles propagate asynchronously; without this the first
  # apply intermittently fails to attach the profile.
  depends_on = [aws_iam_instance_profile.node]

  tags = {
    Name = "${var.project_name}-master"
    Role = "rke2-server"
  }
}

resource "aws_instance" "worker" {
  count                  = var.worker_count
  ami                    = data.aws_ami.ubuntu_2204.id
  instance_type          = var.worker_instance_type
  subnet_id              = aws_subnet.public.id
  key_name               = aws_key_pair.main.key_name
  iam_instance_profile   = aws_iam_instance_profile.node.name
  vpc_security_group_ids = [aws_security_group.cluster.id]
  user_data              = local.node_user_data

  root_block_device {
    volume_size           = var.worker_root_volume_size
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  depends_on = [aws_iam_instance_profile.node]

  tags = {
    Name = "${var.project_name}-worker-${count.index + 1}"
    Role = "rke2-agent"
  }
}

resource "aws_eip" "master" {
  domain   = "vpc"
  instance = aws_instance.master.id

  depends_on = [aws_internet_gateway.main]

  tags = {
    Name = "${var.project_name}-master-eip"
  }
}
