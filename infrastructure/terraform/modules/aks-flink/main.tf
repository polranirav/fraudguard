# =============================================================================
# Flink on AKS Module - Kubernetes Resources for Flink Deployment
# =============================================================================
# This module configures all Kubernetes resources needed for Flink:
# - Namespaces
# - Service Accounts
# - RBAC
# - ConfigMaps
# - Flink Operator
# =============================================================================

terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.24.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12.0"
    }
  }
}

# =============================================================================
# Namespaces
# =============================================================================

resource "kubernetes_namespace" "flink" {
  metadata {
    name = "flink-fraud-detection"
    
    labels = {
      "app.kubernetes.io/name"       = "fraud-detection"
      "app.kubernetes.io/component"  = "stream-processing"
      "app.kubernetes.io/managed-by" = "terraform"
    }
  }
}

resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = "monitoring"
    
    labels = {
      "app.kubernetes.io/name"       = "monitoring"
      "app.kubernetes.io/managed-by" = "terraform"
    }
  }
}

# =============================================================================
# Service Account for Flink
# =============================================================================

resource "kubernetes_service_account" "flink" {
  metadata {
    name      = "flink-sa"
    namespace = kubernetes_namespace.flink.metadata[0].name
    
    labels = {
      "app.kubernetes.io/name"      = "flink"
      "app.kubernetes.io/component" = "service-account"
    }
    
    annotations = {
      "azure.workload.identity/client-id" = var.aks_workload_identity_client_id
    }
  }
}

# =============================================================================
# RBAC for Flink
# =============================================================================

resource "kubernetes_cluster_role" "flink" {
  metadata {
    name = "flink-cluster-role"
  }

  rule {
    api_groups = [""]
    resources  = ["pods", "services", "configmaps", "secrets"]
    verbs      = ["create", "delete", "get", "list", "patch", "update", "watch"]
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "statefulsets"]
    verbs      = ["create", "delete", "get", "list", "patch", "update", "watch"]
  }

  rule {
    api_groups = ["flink.apache.org"]
    resources  = ["flinkdeployments", "flinksessionjobs"]
    verbs      = ["*"]
  }
}

resource "kubernetes_cluster_role_binding" "flink" {
  metadata {
    name = "flink-cluster-role-binding"
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role.flink.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.flink.metadata[0].name
    namespace = kubernetes_namespace.flink.metadata[0].name
  }
}

# =============================================================================
# ConfigMaps
# =============================================================================

resource "kubernetes_config_map" "flink_config" {
  metadata {
    name      = "flink-config"
    namespace = kubernetes_namespace.flink.metadata[0].name
  }

  data = {
    "flink-conf.yaml" = <<-EOT
      # Flink Configuration
      taskmanager.numberOfTaskSlots: ${var.flink_task_slots}
      parallelism.default: ${var.flink_parallelism}
      
      # State Backend
      state.backend: rocksdb
      state.backend.rocksdb.memory.managed: true
      state.backend.rocksdb.memory.fixed-per-slot: 256mb
      
      # Checkpointing
      state.checkpoints.dir: ${var.checkpoint_storage_url}
      state.savepoints.dir: ${var.savepoint_storage_url}
      execution.checkpointing.interval: 60000
      execution.checkpointing.mode: EXACTLY_ONCE
      execution.checkpointing.min-pause: 30000
      execution.checkpointing.timeout: 600000
      execution.checkpointing.tolerable-failed-checkpoints: 3
      
      # Memory Configuration
      jobmanager.memory.process.size: ${var.jobmanager_memory}
      taskmanager.memory.process.size: ${var.taskmanager_memory}
      taskmanager.memory.managed.fraction: 0.4
      
      # Network
      taskmanager.network.memory.fraction: 0.1
      taskmanager.network.memory.min: 64mb
      taskmanager.network.memory.max: 1gb
      
      # High Availability
      high-availability: kubernetes
      high-availability.storageDir: ${var.ha_storage_url}
      
      # Metrics
      metrics.reporters: prom
      metrics.reporter.prom.factory.class: org.apache.flink.metrics.prometheus.PrometheusReporterFactory
      metrics.reporter.prom.port: 9999
      
      # Web UI
      rest.port: 8081
      web.upload.dir: /tmp
    EOT

    "log4j-console.properties" = <<-EOT
      rootLogger.level = INFO
      rootLogger.appenderRef.console.ref = ConsoleAppender
      appender.console.name = ConsoleAppender
      appender.console.type = Console
      appender.console.layout.type = PatternLayout
      appender.console.layout.pattern = %d{yyyy-MM-dd HH:mm:ss,SSS} %-5p %-60c %x - %m%n
      logger.frauddetection.name = com.frauddetection
      logger.frauddetection.level = DEBUG
      logger.flink.name = org.apache.flink
      logger.flink.level = INFO
      logger.kafka.name = org.apache.kafka
      logger.kafka.level = WARN
    EOT
  }
}

resource "kubernetes_config_map" "app_config" {
  metadata {
    name      = "app-config"
    namespace = kubernetes_namespace.flink.metadata[0].name
  }

  data = {
    "kafka.bootstrap.servers" = var.kafka_bootstrap_servers
    "redis.host"              = var.redis_host
    "redis.port"              = var.redis_port
    "synapse.jdbc.url"        = var.synapse_jdbc_url
  }
}

# =============================================================================
# Secrets (from Azure Key Vault via CSI Driver)
# =============================================================================

resource "kubernetes_manifest" "secret_provider_class" {
  manifest = {
    apiVersion = "secrets-store.csi.x-k8s.io/v1"
    kind       = "SecretProviderClass"
    metadata = {
      name      = "azure-keyvault-secrets"
      namespace = kubernetes_namespace.flink.metadata[0].name
    }
    spec = {
      provider = "azure"
      parameters = {
        usePodIdentity         = "false"
        useVMManagedIdentity   = "true"
        userAssignedIdentityID = var.aks_workload_identity_client_id
        keyvaultName           = var.key_vault_name
        cloudName              = ""
        objects = yamlencode([
          {
            objectName = "redis-connection-string"
            objectType = "secret"
          },
          {
            objectName = "eventhub-connection-string"
            objectType = "secret"
          },
          {
            objectName = "synapse-admin-password"
            objectType = "secret"
          }
        ])
        tenantId = var.tenant_id
      }
      secretObjects = [
        {
          secretName = "fraud-detection-secrets"
          type       = "Opaque"
          data = [
            {
              objectName = "redis-connection-string"
              key        = "REDIS_CONNECTION_STRING"
            },
            {
              objectName = "eventhub-connection-string"
              key        = "KAFKA_CONNECTION_STRING"
            },
            {
              objectName = "synapse-admin-password"
              key        = "SYNAPSE_PASSWORD"
            }
          ]
        }
      ]
    }
  }
}

# =============================================================================
# Flink Kubernetes Operator (Helm)
# =============================================================================

resource "helm_release" "flink_operator" {
  name       = "flink-kubernetes-operator"
  repository = "https://downloads.apache.org/flink/flink-kubernetes-operator-1.7.0/"
  chart      = "flink-kubernetes-operator"
  version    = "1.7.0"
  namespace  = kubernetes_namespace.flink.metadata[0].name

  set {
    name  = "webhook.create"
    value = "true"
  }

  set {
    name  = "metrics.port"
    value = "9999"
  }

  depends_on = [
    kubernetes_namespace.flink,
    kubernetes_service_account.flink
  ]
}

# =============================================================================
# Prometheus and Grafana (Monitoring Stack)
# =============================================================================

resource "helm_release" "prometheus" {
  name       = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = "55.0.0"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name

  values = [
    <<-EOT
    prometheus:
      prometheusSpec:
        serviceMonitorSelectorNilUsesHelmValues: false
        podMonitorSelectorNilUsesHelmValues: false
        retention: 15d
        storageSpec:
          volumeClaimTemplate:
            spec:
              accessModes: ["ReadWriteOnce"]
              resources:
                requests:
                  storage: 50Gi

    grafana:
      enabled: true
      adminPassword: "${var.grafana_admin_password}"
      persistence:
        enabled: true
        size: 10Gi
      dashboardProviders:
        dashboardproviders.yaml:
          apiVersion: 1
          providers:
          - name: 'flink'
            orgId: 1
            folder: 'Flink'
            type: file
            disableDeletion: false
            editable: true
            options:
              path: /var/lib/grafana/dashboards/flink

    alertmanager:
      enabled: true
      config:
        global:
          resolve_timeout: 5m
        route:
          group_by: ['alertname', 'namespace']
          group_wait: 30s
          group_interval: 5m
          repeat_interval: 12h
          receiver: 'default'
        receivers:
        - name: 'default'
    EOT
  ]

  depends_on = [kubernetes_namespace.monitoring]
}

# =============================================================================
# Variables
# =============================================================================

variable "aks_workload_identity_client_id" {
  description = "Client ID for AKS workload identity"
  type        = string
}

variable "key_vault_name" {
  description = "Name of the Azure Key Vault"
  type        = string
}

variable "tenant_id" {
  description = "Azure AD Tenant ID"
  type        = string
}

variable "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers"
  type        = string
}

variable "redis_host" {
  description = "Redis hostname"
  type        = string
}

variable "redis_port" {
  description = "Redis port"
  type        = string
  default     = "6380"
}

variable "synapse_jdbc_url" {
  description = "Synapse JDBC URL"
  type        = string
}

variable "checkpoint_storage_url" {
  description = "URL for Flink checkpoints storage"
  type        = string
}

variable "savepoint_storage_url" {
  description = "URL for Flink savepoints storage"
  type        = string
}

variable "ha_storage_url" {
  description = "URL for Flink HA storage"
  type        = string
}

variable "flink_task_slots" {
  description = "Number of task slots per TaskManager"
  type        = number
  default     = 8
}

variable "flink_parallelism" {
  description = "Default parallelism for Flink jobs"
  type        = number
  default     = 8
}

variable "jobmanager_memory" {
  description = "JobManager memory"
  type        = string
  default     = "2048m"
}

variable "taskmanager_memory" {
  description = "TaskManager memory"
  type        = string
  default     = "4096m"
}

variable "grafana_admin_password" {
  description = "Grafana admin password"
  type        = string
  sensitive   = true
}


