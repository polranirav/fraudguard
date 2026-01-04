# =============================================================================
# Real-Time Financial Fraud Detection Platform - Terraform Outputs
# =============================================================================

# =============================================================================
# Resource Group
# =============================================================================

output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.main.name
}

output "resource_group_location" {
  description = "Location of the resource group"
  value       = azurerm_resource_group.main.location
}

# =============================================================================
# AKS Cluster
# =============================================================================

output "aks_cluster_name" {
  description = "Name of the AKS cluster"
  value       = azurerm_kubernetes_cluster.main.name
}

output "aks_cluster_id" {
  description = "ID of the AKS cluster"
  value       = azurerm_kubernetes_cluster.main.id
}

output "aks_kube_config" {
  description = "Kubernetes configuration for kubectl"
  value       = azurerm_kubernetes_cluster.main.kube_config_raw
  sensitive   = true
}

output "aks_get_credentials_command" {
  description = "Azure CLI command to get AKS credentials"
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${azurerm_kubernetes_cluster.main.name}"
}

output "aks_fqdn" {
  description = "FQDN of the AKS cluster"
  value       = azurerm_kubernetes_cluster.main.fqdn
}

# =============================================================================
# Container Registry
# =============================================================================

output "acr_name" {
  description = "Name of the Azure Container Registry"
  value       = azurerm_container_registry.main.name
}

output "acr_login_server" {
  description = "Login server URL for ACR"
  value       = azurerm_container_registry.main.login_server
}

output "acr_login_command" {
  description = "Azure CLI command to login to ACR"
  value       = "az acr login --name ${azurerm_container_registry.main.name}"
}

# =============================================================================
# Storage Account (ADLS Gen2)
# =============================================================================

output "storage_account_name" {
  description = "Name of the storage account"
  value       = azurerm_storage_account.datalake.name
}

output "storage_account_primary_dfs_endpoint" {
  description = "Primary DFS endpoint for ADLS Gen2"
  value       = azurerm_storage_account.datalake.primary_dfs_endpoint
}

output "storage_containers" {
  description = "Storage containers created"
  value = {
    raw         = azurerm_storage_data_lake_gen2_filesystem.raw.name
    curated     = azurerm_storage_data_lake_gen2_filesystem.curated.name
    checkpoints = azurerm_storage_data_lake_gen2_filesystem.checkpoints.name
    synapse     = azurerm_storage_data_lake_gen2_filesystem.synapse.name
  }
}

# =============================================================================
# Synapse Analytics
# =============================================================================

output "synapse_workspace_name" {
  description = "Name of the Synapse workspace"
  value       = azurerm_synapse_workspace.main.name
}

output "synapse_sql_endpoint" {
  description = "SQL endpoint for Synapse dedicated pool"
  value       = azurerm_synapse_workspace.main.connectivity_endpoints["sql"]
}

output "synapse_sql_pool_name" {
  description = "Name of the Synapse SQL pool"
  value       = azurerm_synapse_sql_pool.main.name
}

output "synapse_jdbc_url" {
  description = "JDBC connection string for Synapse"
  value       = "jdbc:sqlserver://${azurerm_synapse_workspace.main.name}.sql.azuresynapse.net:1433;database=${azurerm_synapse_sql_pool.main.name};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.sql.azuresynapse.net;loginTimeout=30;"
  sensitive   = true
}

# =============================================================================
# Event Hubs (Kafka)
# =============================================================================

output "eventhub_namespace" {
  description = "Name of the Event Hub namespace"
  value       = azurerm_eventhub_namespace.main.name
}

output "eventhub_kafka_endpoint" {
  description = "Kafka-compatible endpoint for Event Hubs"
  value       = "${azurerm_eventhub_namespace.main.name}.servicebus.windows.net:9093"
}

output "eventhub_topics" {
  description = "Event Hub topics (Kafka topics)"
  value = {
    transactions = azurerm_eventhub.transactions.name
    alerts       = azurerm_eventhub.alerts.name
  }
}

output "eventhub_connection_string" {
  description = "Connection string for Event Hubs"
  value       = azurerm_eventhub_namespace.main.default_primary_connection_string
  sensitive   = true
}

# =============================================================================
# Redis Cache
# =============================================================================

output "redis_hostname" {
  description = "Hostname of the Redis cache"
  value       = azurerm_redis_cache.main.hostname
}

output "redis_port" {
  description = "SSL port for Redis"
  value       = azurerm_redis_cache.main.ssl_port
}

output "redis_connection_string" {
  description = "Connection string for Redis"
  value       = azurerm_redis_cache.main.primary_connection_string
  sensitive   = true
}

# =============================================================================
# Key Vault
# =============================================================================

output "key_vault_name" {
  description = "Name of the Key Vault"
  value       = azurerm_key_vault.main.name
}

output "key_vault_uri" {
  description = "URI of the Key Vault"
  value       = azurerm_key_vault.main.vault_uri
}

# =============================================================================
# Networking
# =============================================================================

output "vnet_id" {
  description = "ID of the virtual network"
  value       = azurerm_virtual_network.main.id
}

output "vnet_name" {
  description = "Name of the virtual network"
  value       = azurerm_virtual_network.main.name
}

output "subnet_ids" {
  description = "IDs of the subnets"
  value = {
    aks               = azurerm_subnet.aks.id
    private_endpoints = azurerm_subnet.private_endpoints.id
    redis             = azurerm_subnet.redis.id
  }
}

# =============================================================================
# Log Analytics
# =============================================================================

output "log_analytics_workspace_id" {
  description = "ID of the Log Analytics workspace"
  value       = azurerm_log_analytics_workspace.main.id
}

output "log_analytics_workspace_name" {
  description = "Name of the Log Analytics workspace"
  value       = azurerm_log_analytics_workspace.main.name
}

# =============================================================================
# Quick Start Commands
# =============================================================================

output "quick_start_commands" {
  description = "Quick start commands for common operations"
  value       = <<-EOT

    # =============================================================================
    # Quick Start Commands for Fraud Detection Platform
    # =============================================================================

    # 1. Get AKS credentials
    ${format("az aks get-credentials --resource-group %s --name %s", azurerm_resource_group.main.name, azurerm_kubernetes_cluster.main.name)}

    # 2. Login to Container Registry
    ${format("az acr login --name %s", azurerm_container_registry.main.name)}

    # 3. Build and push Flink image
    ${format("docker build -t %s/flink-fraud:latest ./finance-intelligence-root", azurerm_container_registry.main.login_server)}
    ${format("docker push %s/flink-fraud:latest", azurerm_container_registry.main.login_server)}

    # 4. Deploy Flink job
    kubectl apply -f kubernetes/flink-deployment.yaml

    # 5. Connect to Synapse (using sqlcmd)
    ${format("sqlcmd -S %s.sql.azuresynapse.net -d %s -U %s -P '<password>'", azurerm_synapse_workspace.main.name, azurerm_synapse_sql_pool.main.name, var.synapse_admin_username)}

    # =============================================================================

  EOT
}


