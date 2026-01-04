# =============================================================================
# Real-Time Financial Fraud Detection Platform - Main Terraform Configuration
# =============================================================================
# This configuration deploys the complete Azure infrastructure:
# - Resource Group
# - Azure Kubernetes Service (AKS)
# - Azure Synapse Analytics
# - Azure Data Lake Storage Gen2
# - Azure Cache for Redis
# - Azure Event Hubs (Kafka-compatible)
# - Virtual Network with Private Endpoints
# - Azure Container Registry
# - Microsoft Purview (Governance)
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.80.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 2.45.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5.0"
    }
  }

  # Backend configuration for state storage
  # Uncomment and configure for production use
  # backend "azurerm" {
  #   resource_group_name  = "rg-terraform-state"
  #   storage_account_name = "stterraformstate"
  #   container_name       = "tfstate"
  #   key                  = "fraud-detection.tfstate"
  # }
}

# =============================================================================
# Provider Configuration
# =============================================================================

provider "azurerm" {
  features {
    resource_group {
      prevent_deletion_if_contains_resources = false
    }
    key_vault {
      purge_soft_delete_on_destroy = true
    }
  }
}

provider "azuread" {}

# =============================================================================
# Data Sources
# =============================================================================

data "azurerm_client_config" "current" {}

data "azuread_client_config" "current" {}

# =============================================================================
# Random Suffix for Unique Names
# =============================================================================

resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}

# =============================================================================
# Local Variables
# =============================================================================

locals {
  # Naming convention: {resource-type}-{project}-{environment}-{suffix}
  project_name = "frauddetect"
  environment  = var.environment
  location     = var.location
  suffix       = random_string.suffix.result

  # Resource names
  resource_group_name     = "rg-${local.project_name}-${local.environment}"
  vnet_name               = "vnet-${local.project_name}-${local.environment}"
  aks_name                = "aks-${local.project_name}-${local.environment}"
  acr_name                = "acr${local.project_name}${local.suffix}"
  storage_account_name    = "st${local.project_name}${local.suffix}"
  synapse_workspace_name  = "synw-${local.project_name}-${local.environment}"
  eventhub_namespace_name = "evhns-${local.project_name}-${local.environment}"
  redis_name              = "redis-${local.project_name}-${local.environment}"
  purview_name            = "pview-${local.project_name}-${local.environment}"
  key_vault_name          = "kv-${local.project_name}-${local.suffix}"

  # Common tags
  common_tags = {
    Project     = "Real-Time Fraud Detection"
    Environment = local.environment
    ManagedBy   = "Terraform"
    Owner       = var.owner_email
    CostCenter  = var.cost_center
  }
}

# =============================================================================
# Resource Group
# =============================================================================

resource "azurerm_resource_group" "main" {
  name     = local.resource_group_name
  location = local.location
  tags     = local.common_tags
}

# =============================================================================
# Virtual Network and Subnets
# =============================================================================

resource "azurerm_virtual_network" "main" {
  name                = local.vnet_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  address_space       = ["10.0.0.0/16"]
  tags                = local.common_tags
}

# AKS Subnet
resource "azurerm_subnet" "aks" {
  name                 = "snet-aks"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.1.0/22"]

  service_endpoints = [
    "Microsoft.Storage",
    "Microsoft.Sql",
    "Microsoft.EventHub",
    "Microsoft.KeyVault"
  ]
}

# Private Endpoints Subnet
resource "azurerm_subnet" "private_endpoints" {
  name                 = "snet-private-endpoints"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.8.0/24"]

  private_endpoint_network_policies_enabled = false
}

# Redis Subnet
resource "azurerm_subnet" "redis" {
  name                 = "snet-redis"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.9.0/24"]
}

# =============================================================================
# Network Security Groups
# =============================================================================

resource "azurerm_network_security_group" "aks" {
  name                = "nsg-aks"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags

  security_rule {
    name                       = "AllowHTTPS"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "AllowFlinkUI"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "8081"
    source_address_prefix      = var.admin_cidr
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "aks" {
  subnet_id                 = azurerm_subnet.aks.id
  network_security_group_id = azurerm_network_security_group.aks.id
}

# =============================================================================
# Azure Container Registry
# =============================================================================

resource "azurerm_container_registry" "main" {
  name                = local.acr_name
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Premium"
  admin_enabled       = false
  tags                = local.common_tags

  network_rule_set {
    default_action = "Deny"

    virtual_network {
      action    = "Allow"
      subnet_id = azurerm_subnet.aks.id
    }
  }

  retention_policy {
    days    = 30
    enabled = true
  }
}

# =============================================================================
# Azure Kubernetes Service (AKS)
# =============================================================================

resource "azurerm_kubernetes_cluster" "main" {
  name                = local.aks_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = local.aks_name
  kubernetes_version  = var.kubernetes_version
  tags                = local.common_tags

  default_node_pool {
    name                = "system"
    node_count          = var.aks_system_node_count
    vm_size             = var.aks_system_vm_size
    vnet_subnet_id      = azurerm_subnet.aks.id
    enable_auto_scaling = true
    min_count           = 1
    max_count           = 3
    os_disk_size_gb     = 100

    node_labels = {
      "nodepool-type" = "system"
      "environment"   = local.environment
    }
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin     = "azure"
    network_policy     = "azure"
    load_balancer_sku  = "standard"
    service_cidr       = "10.1.0.0/16"
    dns_service_ip     = "10.1.0.10"
  }

  oms_agent {
    log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  }

  key_vault_secrets_provider {
    secret_rotation_enabled = true
  }
}

# Flink Node Pool
resource "azurerm_kubernetes_cluster_node_pool" "flink" {
  name                  = "flink"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.main.id
  vm_size               = var.aks_flink_vm_size
  node_count            = var.aks_flink_node_count
  vnet_subnet_id        = azurerm_subnet.aks.id
  enable_auto_scaling   = true
  min_count             = 2
  max_count             = 10
  os_disk_size_gb       = 200
  tags                  = local.common_tags

  node_labels = {
    "nodepool-type" = "flink"
    "workload"      = "stream-processing"
  }

  node_taints = [
    "workload=flink:NoSchedule"
  ]
}

# ACR Pull Permission for AKS
resource "azurerm_role_assignment" "aks_acr" {
  principal_id                     = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
  role_definition_name             = "AcrPull"
  scope                            = azurerm_container_registry.main.id
  skip_service_principal_aad_check = true
}

# =============================================================================
# Azure Data Lake Storage Gen2
# =============================================================================

resource "azurerm_storage_account" "datalake" {
  name                     = local.storage_account_name
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  account_kind             = "StorageV2"
  is_hns_enabled           = true # Hierarchical namespace for ADLS Gen2
  min_tls_version          = "TLS1_2"
  tags                     = local.common_tags

  network_rules {
    default_action             = "Deny"
    virtual_network_subnet_ids = [azurerm_subnet.aks.id]
    bypass                     = ["AzureServices"]
  }

  blob_properties {
    versioning_enabled = true

    delete_retention_policy {
      days = 30
    }

    container_delete_retention_policy {
      days = 30
    }
  }
}

# Storage Containers
resource "azurerm_storage_data_lake_gen2_filesystem" "raw" {
  name               = "raw"
  storage_account_id = azurerm_storage_account.datalake.id
}

resource "azurerm_storage_data_lake_gen2_filesystem" "curated" {
  name               = "curated"
  storage_account_id = azurerm_storage_account.datalake.id
}

resource "azurerm_storage_data_lake_gen2_filesystem" "checkpoints" {
  name               = "checkpoints"
  storage_account_id = azurerm_storage_account.datalake.id
}

resource "azurerm_storage_data_lake_gen2_filesystem" "synapse" {
  name               = "synapse"
  storage_account_id = azurerm_storage_account.datalake.id
}

# =============================================================================
# Azure Synapse Analytics
# =============================================================================

resource "azurerm_synapse_workspace" "main" {
  name                                 = local.synapse_workspace_name
  resource_group_name                  = azurerm_resource_group.main.name
  location                             = azurerm_resource_group.main.location
  storage_data_lake_gen2_filesystem_id = azurerm_storage_data_lake_gen2_filesystem.synapse.id
  sql_administrator_login              = var.synapse_admin_username
  sql_administrator_login_password     = var.synapse_admin_password
  managed_virtual_network_enabled      = true
  tags                                 = local.common_tags

  identity {
    type = "SystemAssigned"
  }

  aad_admin {
    login     = var.aad_admin_login
    object_id = var.aad_admin_object_id
    tenant_id = data.azurerm_client_config.current.tenant_id
  }
}

# Synapse Dedicated SQL Pool
resource "azurerm_synapse_sql_pool" "main" {
  name                 = "sqldw"
  synapse_workspace_id = azurerm_synapse_workspace.main.id
  sku_name             = var.synapse_sku
  create_mode          = "Default"
  tags                 = local.common_tags
}

# Synapse Firewall Rule (for management)
resource "azurerm_synapse_firewall_rule" "allow_azure" {
  name                 = "AllowAllWindowsAzureIps"
  synapse_workspace_id = azurerm_synapse_workspace.main.id
  start_ip_address     = "0.0.0.0"
  end_ip_address       = "0.0.0.0"
}

# =============================================================================
# Azure Event Hubs (Kafka-compatible)
# =============================================================================

resource "azurerm_eventhub_namespace" "main" {
  name                = local.eventhub_namespace_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "Standard"
  capacity            = var.eventhub_capacity
  tags                = local.common_tags

  network_rulesets {
    default_action                 = "Deny"
    trusted_service_access_enabled = true

    virtual_network_rule {
      subnet_id = azurerm_subnet.aks.id
    }
  }
}

# Transactions Topic
resource "azurerm_eventhub" "transactions" {
  name                = "transactions-live"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = azurerm_resource_group.main.name
  partition_count     = 12
  message_retention   = 7
}

# Fraud Alerts Topic
resource "azurerm_eventhub" "alerts" {
  name                = "fraud-alerts"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = azurerm_resource_group.main.name
  partition_count     = 6
  message_retention   = 30
}

# Consumer Groups
resource "azurerm_eventhub_consumer_group" "flink" {
  name                = "fraud-detection-group"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.transactions.name
  resource_group_name = azurerm_resource_group.main.name
}

resource "azurerm_eventhub_consumer_group" "alerts" {
  name                = "alert-notification-group"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.alerts.name
  resource_group_name = azurerm_resource_group.main.name
}

# =============================================================================
# Azure Cache for Redis
# =============================================================================

resource "azurerm_redis_cache" "main" {
  name                = local.redis_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  capacity            = var.redis_capacity
  family              = var.redis_family
  sku_name            = var.redis_sku
  enable_non_ssl_port = false
  minimum_tls_version = "1.2"
  tags                = local.common_tags

  redis_configuration {
    maxmemory_reserved = 50
    maxmemory_delta    = 50
    maxmemory_policy   = "allkeys-lru"
  }
}

# =============================================================================
# Azure Key Vault
# =============================================================================

resource "azurerm_key_vault" "main" {
  name                        = local.key_vault_name
  location                    = azurerm_resource_group.main.location
  resource_group_name         = azurerm_resource_group.main.name
  enabled_for_disk_encryption = true
  tenant_id                   = data.azurerm_client_config.current.tenant_id
  soft_delete_retention_days  = 7
  purge_protection_enabled    = false
  sku_name                    = "standard"
  tags                        = local.common_tags

  network_acls {
    default_action             = "Deny"
    bypass                     = "AzureServices"
    virtual_network_subnet_ids = [azurerm_subnet.aks.id]
  }

  access_policy {
    tenant_id = data.azurerm_client_config.current.tenant_id
    object_id = data.azurerm_client_config.current.object_id

    secret_permissions = [
      "Get", "List", "Set", "Delete", "Purge"
    ]
  }
}

# Store secrets in Key Vault
resource "azurerm_key_vault_secret" "synapse_password" {
  name         = "synapse-admin-password"
  value        = var.synapse_admin_password
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "redis_connection" {
  name         = "redis-connection-string"
  value        = azurerm_redis_cache.main.primary_connection_string
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "eventhub_connection" {
  name         = "eventhub-connection-string"
  value        = azurerm_eventhub_namespace.main.default_primary_connection_string
  key_vault_id = azurerm_key_vault.main.id
}

# =============================================================================
# Log Analytics Workspace
# =============================================================================

resource "azurerm_log_analytics_workspace" "main" {
  name                = "law-${local.project_name}-${local.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.common_tags
}

# =============================================================================
# Private DNS Zones
# =============================================================================

resource "azurerm_private_dns_zone" "storage" {
  name                = "privatelink.dfs.core.windows.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

resource "azurerm_private_dns_zone" "synapse" {
  name                = "privatelink.sql.azuresynapse.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "storage" {
  name                  = "storage-vnet-link"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.storage.name
  virtual_network_id    = azurerm_virtual_network.main.id
}

resource "azurerm_private_dns_zone_virtual_network_link" "synapse" {
  name                  = "synapse-vnet-link"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.synapse.name
  virtual_network_id    = azurerm_virtual_network.main.id
}

# =============================================================================
# Private Endpoints
# =============================================================================

# Storage Private Endpoint
resource "azurerm_private_endpoint" "storage" {
  name                = "pe-storage"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.private_endpoints.id
  tags                = local.common_tags

  private_service_connection {
    name                           = "storage-privateserviceconnection"
    private_connection_resource_id = azurerm_storage_account.datalake.id
    subresource_names              = ["dfs"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "storage-dns-zone-group"
    private_dns_zone_ids = [azurerm_private_dns_zone.storage.id]
  }
}

# Synapse Private Endpoint
resource "azurerm_private_endpoint" "synapse" {
  name                = "pe-synapse"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.private_endpoints.id
  tags                = local.common_tags

  private_service_connection {
    name                           = "synapse-privateserviceconnection"
    private_connection_resource_id = azurerm_synapse_workspace.main.id
    subresource_names              = ["Sql"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "synapse-dns-zone-group"
    private_dns_zone_ids = [azurerm_private_dns_zone.synapse.id]
  }
}
