variable "project_name" {
  description = "Branch-scoped project name used as the prefix for every resource."
  type        = string
}

variable "aws_region" {
  description = "AWS region the platform is deployed into."
  type        = string
  default     = "us-east-1"
}

variable "ssh_public_key" {
  description = "Public half of the platform-managed SSH keypair, registered as an EC2 key pair."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the platform VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for the public subnet hosting the cluster nodes."
  type        = string
  default     = "10.20.1.0/24"
}

variable "master_instance_type" {
  description = "Instance type for the RKE2 server node. Rancher + cert-manager + ingress-nginx need >= 8GB RAM."
  type        = string
  default     = "t3.large"
}

variable "worker_instance_type" {
  description = "Instance type for the RKE2 agent nodes."
  type        = string
  default     = "t3.medium"
}

variable "worker_count" {
  description = "Number of RKE2 agent (worker) nodes."
  type        = number
  default     = 3
}

variable "master_root_volume_size" {
  description = "Root EBS volume size (GiB) for the master node."
  type        = number
  default     = 60
}

variable "worker_root_volume_size" {
  description = "Root EBS volume size (GiB) for each worker node."
  type        = number
  default     = 40
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to reach SSH on the nodes. Defaults to anywhere so GitHub-hosted runners can configure the cluster."
  type        = string
  default     = "0.0.0.0/0"
}
