output "master_public_ip" {
  description = "Elastic IP of the RKE2 server node. Rancher UI and the Employee API are reached here."
  value       = aws_eip.master.public_ip
}

output "master_private_ip" {
  description = "Private IP of the RKE2 server node, used by agents to join the cluster."
  value       = aws_instance.master.private_ip
}

output "worker_public_ips" {
  description = "Public IPs of the RKE2 agent nodes (used by Ansible over SSH)."
  value       = [for w in aws_instance.worker : w.public_ip]
}

output "worker_private_ips" {
  description = "Private IPs of the RKE2 agent nodes."
  value       = [for w in aws_instance.worker : w.private_ip]
}

output "node_public_ips" {
  description = "Public IPs of every node in the platform, master first."
  value       = concat([aws_eip.master.public_ip], [for w in aws_instance.worker : w.public_ip])
}

output "rke2_token" {
  description = "Shared RKE2 cluster join token."
  value       = random_password.rke2_token.result
  sensitive   = true
}

output "vpc_id" {
  description = "ID of the platform VPC."
  value       = aws_vpc.main.id
}

output "rancher_url" {
  description = "Rancher management UI URL (sslip.io hostname derived from the master EIP)."
  value       = "https://rancher.${aws_eip.master.public_ip}.sslip.io"
}

output "employee_api_url" {
  description = "Employee API URL served through ingress-nginx."
  value       = "http://api.${aws_eip.master.public_ip}.sslip.io"
}
