data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project_name}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-igw"
  }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name                     = "${var.project_name}-public-subnet"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${var.project_name}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# ---------------------------------------------------------------------------
# Security groups
#
# Public surface is deliberately narrow: SSH (CI configures the cluster over
# SSH) plus HTTP/HTTPS on the master, which is where ingress-nginx terminates
# traffic for Rancher and the Employee API. The Kubernetes API (6443) and the
# RKE2 supervisor/join port (9345) are reachable only from inside the VPC.
# ---------------------------------------------------------------------------

resource "aws_security_group" "cluster" {
  name        = "${var.project_name}-cluster-sg"
  description = "Intra-cluster traffic between RKE2 server and agent nodes"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-cluster-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "cluster_internal" {
  security_group_id            = aws_security_group.cluster.id
  description                  = "All traffic between cluster nodes (RKE2, etcd, kubelet, CNI, NodePorts)"
  referenced_security_group_id = aws_security_group.cluster.id
  ip_protocol                  = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "cluster_api_vpc" {
  security_group_id = aws_security_group.cluster.id
  description       = "Kubernetes API from inside the VPC only"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 6443
  to_port           = 6443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "cluster_supervisor_vpc" {
  security_group_id = aws_security_group.cluster.id
  description       = "RKE2 supervisor / agent join port from inside the VPC only"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 9345
  to_port           = 9345
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "cluster_ssh" {
  security_group_id = aws_security_group.cluster.id
  description       = "SSH for Ansible configuration from CI"
  cidr_ipv4         = var.ssh_ingress_cidr
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "cluster_all" {
  security_group_id = aws_security_group.cluster.id
  description       = "Allow all egress (package installs, image pulls, CloudWatch)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "ingress" {
  name        = "${var.project_name}-ingress-sg"
  description = "Public HTTP/HTTPS ingress to the master node (Rancher UI + Employee API)"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-ingress-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "ingress_http" {
  security_group_id = aws_security_group.ingress.id
  description       = "HTTP to ingress-nginx"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "ingress_https" {
  security_group_id = aws_security_group.ingress.id
  description       = "HTTPS to ingress-nginx (Rancher UI)"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "ingress_all" {
  security_group_id = aws_security_group.ingress.id
  description       = "Allow all egress"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
