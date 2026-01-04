# Azure Databricks Setup

This directory contains Terraform configuration for setting up Azure Databricks workspace for the Data Lakehouse architecture.

## What This Creates

1. **Databricks Workspace** - Premium tier workspace
2. **Batch Processing Cluster** - Auto-scaling Spark cluster
3. **ADLS Gen2 Mount** - Connection to data lake storage
4. **Batch Analytics Notebook** - Sample batch processing job
5. **Scheduled Jobs** - Daily batch processing workflow

## Deployment

```bash
cd infrastructure/databricks
terraform init
terraform plan
terraform apply
```

## Cost Estimate

- Databricks Premium: ~$200-500/month (depending on usage)
- Compute: Pay-per-use (can be significant)
- Storage: Included in ADLS costs

## Integration with Streaming

The Data Lakehouse architecture unifies:
- **Streaming**: Flink processes real-time transactions → Kafka → Synapse
- **Batch**: Databricks processes historical data → Delta Lake → Power BI

Both read from/write to the same ADLS Gen2 data lake.
