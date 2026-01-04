# =============================================================================
# Real-Time Financial Fraud Detection Platform - Terraform Variables
# =============================================================================

# =============================================================================
# General Configuration
# =============================================================================

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "location" {
  description = "Azure region for all resources"
  type        = string
  default     = "eastus"
}

variable "owner_email" {
  description = "Email of the resource owner for tagging"
  type        = string
  default     = "platform-team@company.com"
}

variable "cost_center" {
  description = "Cost center for billing"
  type        = string
  default     = "fraud-detection"
}

variable "admin_cidr" {
  description = "CIDR block for admin access to Flink UI"
  type        = string
  default     = "10.0.0.0/8"
}

# =============================================================================
# Azure Kubernetes Service (AKS) Configuration
# =============================================================================

variable "kubernetes_version" {
  description = "Kubernetes version for AKS"
  type        = string
  default     = "1.28"
}

variable "aks_system_node_count" {
  description = "Number of nodes in the system node pool"
  type        = number
  default     = 2
}

variable "aks_system_vm_size" {
  description = "VM size for system node pool"
  type        = string
  default     = "Standard_D4s_v3"
}

variable "aks_flink_node_count" {
  description = "Number of nodes in the Flink node pool"
  type        = number
  default     = 3
}

variable "aks_flink_vm_size" {
  description = "VM size for Flink node pool (memory-optimized recommended)"
  type        = string
  default     = "Standard_E8s_v3"
}

# =============================================================================
# Azure Synapse Analytics Configuration
# =============================================================================

variable "synapse_admin_username" {
  description = "SQL administrator username for Synapse"
  type        = string
  default     = "sqladmin"
}

variable "synapse_admin_password" {
  description = "SQL administrator password for Synapse"
  type        = string
  sensitive   = true
}

variable "synapse_sku" {
  description = "SKU for Synapse dedicated SQL pool"
  type        = string
  default     = "DW100c"

  validation {
    condition     = can(regex("^DW[0-9]+c$", var.synapse_sku))
    error_message = "Synapse SKU must be in format DW###c (e.g., DW100c, DW200c)."
  }
}

variable "aad_admin_login" {
  description = "Azure AD admin login for Synapse"
  type        = string
}

variable "aad_admin_object_id" {
  description = "Azure AD admin object ID for Synapse"
  type        = string
}

# =============================================================================
# Azure Event Hubs (Kafka) Configuration
# =============================================================================

variable "eventhub_capacity" {
  description = "Throughput units for Event Hubs namespace"
  type        = number
  default     = 4

  validation {
    condition     = var.eventhub_capacity >= 1 && var.eventhub_capacity <= 40
    error_message = "Event Hub capacity must be between 1 and 40."
  }
}

# =============================================================================
# Azure Cache for Redis Configuration
# =============================================================================

variable "redis_capacity" {
  description = "Redis cache capacity (0-6 for Basic/Standard, 1-4 for Premium)"
  type        = number
  default     = 2
}

variable "redis_family" {
  description = "Redis cache family (C for Basic/Standard, P for Premium)"
  type        = string
  default     = "C"

  validation {
    condition     = contains(["C", "P"], var.redis_family)
    error_message = "Redis family must be C (Basic/Standard) or P (Premium)."
  }
}

variable "redis_sku" {
  description = "Redis cache SKU (Basic, Standard, Premium)"
  type        = string
  default     = "Standard"

  validation {
    condition     = contains(["Basic", "Standard", "Premium"], var.redis_sku)
    error_message = "Redis SKU must be Basic, Standard, or Premium."
  }
}

# =============================================================================
# Environment-Specific Defaults
# =============================================================================

locals {
  # Environment-specific configurations
  env_config = {
    dev = {
      aks_system_node_count = 1
      aks_flink_node_count  = 2
      synapse_sku           = "DW100c"
      eventhub_capacity     = 2
      redis_capacity        = 1
    }
    staging = {
      aks_system_node_count = 2
      aks_flink_node_count  = 3
      synapse_sku           = "DW200c"
      eventhub_capacity     = 4
      redis_capacity        = 2
    }
    prod = {
      aks_system_node_count = 3
      aks_flink_node_count  = 5
      synapse_sku           = "DW500c"
      eventhub_capacity     = 8
      redis_capacity        = 4
    }
  }
}


