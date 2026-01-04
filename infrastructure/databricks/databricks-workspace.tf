# Azure Databricks Workspace for Data Lakehouse
# This creates a Databricks workspace for unified streaming and batch analytics

terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
    databricks = {
      source  = "databricks/databricks"
      version = "~> 1.0"
    }
  }
}

# Databricks Workspace
resource "azurerm_databricks_workspace" "fraud_detection" {
  name                = "db-fraud-detection-${var.environment}"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "premium"

  managed_resource_group_name = "rg-databricks-${var.environment}"

  tags = var.tags
}

# Databricks Cluster for Batch Processing
resource "databricks_cluster" "batch_processing" {
  cluster_name            = "fraud-detection-batch-cluster"
  spark_version           = "13.3.x-scala2.12"
  node_type_id            = "Standard_DS3_v2"
  driver_node_type_id     = "Standard_DS3_v2"
  autotermination_minutes = 20
  enable_elastic_disk     = true

  autoscale {
    min_workers = 2
    max_workers = 8
  }

  spark_conf = {
    "spark.databricks.delta.preview.enabled" = "true"
    "spark.sql.files.maxPartitionBytes"      = "134217728"
  }

  depends_on = [azurerm_databricks_workspace.fraud_detection]
}

# Mount ADLS Gen2 to Databricks
resource "databricks_mount" "adls" {
  name       = "fraud-detection-data"
  cluster_id = databricks_cluster.batch_processing.cluster_id

  abfss {
    container_name         = "fraud-detection"
    storage_account_name   = var.storage_account_name
    directory              = "/"
    tenant_id              = var.tenant_id
    client_id              = var.client_id
    client_secret_scope    = "fraud-detection-secrets"
    client_secret_key      = "adls-key"
  }

  depends_on = [databricks_cluster.batch_processing]
}

# Databricks Notebook for Batch Processing
resource "databricks_notebook" "batch_analytics" {
  path     = "/Shared/fraud-detection-batch-analytics"
  language = "python"
  content_base64 = base64encode(<<-EOT
    # Databricks notebook source
    # MAGIC %md
    # MAGIC # Fraud Detection Batch Analytics
    # MAGIC 
    # MAGIC This notebook processes historical transaction data for fraud analysis.

    # Read from Delta Lake
    transactions_df = spark.read.format("delta").load("/mnt/fraud-detection-data/transactions")
    
    # Batch fraud analysis
    fraud_analysis = transactions_df.groupBy("customer_id", "date").agg(
        count("transaction_id").alias("transaction_count"),
        sum("amount").alias("total_amount"),
        avg("amount").alias("avg_amount")
    )
    
    # Write results
    fraud_analysis.write.format("delta").mode("overwrite").save("/mnt/fraud-detection-data/analytics/fraud-summary")
    
    display(fraud_analysis)
  EOT
  )

  depends_on = [databricks_cluster.batch_processing]
}

# Databricks Job for Scheduled Batch Processing
resource "databricks_job" "daily_batch" {
  name = "fraud-detection-daily-batch"

  new_cluster {
    spark_version = "13.3.x-scala2.12"
    node_type_id  = "Standard_DS3_v2"
    num_workers   = 4
  }

  notebook_task {
    notebook_path = databricks_notebook.batch_analytics.path
  }

  schedule {
    quartz_cron_expression = "0 0 2 * * ?"  # Daily at 2 AM
    timezone_id            = "UTC"
  }

  depends_on = [databricks_notebook.batch_analytics]
}
