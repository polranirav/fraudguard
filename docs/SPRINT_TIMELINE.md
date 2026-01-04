# Sprint Timeline & Implementation Guide

## Real-Time Financial Fraud Detection & Legacy Migration Platform

---

## Executive Summary

This document provides a detailed 5-week sprint timeline for implementing the Real-Time Financial Fraud Detection & Legacy Migration Platform. Each day is structured with specific deliverables, dependencies, and validation checkpoints.

---

## Sprint Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           5-WEEK SPRINT TIMELINE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Week 1          Week 2          Week 3          Week 4          Week 5    │
│  Foundation      Migration       Intelligence    Analytics      Go-Live    │
│  ─────────       ─────────       ────────────    ─────────      ───────    │
│                                                                             │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐  │
│  │ Azure   │───▶│ Sqoop   │───▶│ Flink   │───▶│ Sinks   │───▶│ Purview │  │
│  │ Setup   │    │ Scripts │    │ Deploy  │    │ Config  │    │ Setup   │  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘  │
│       │              │              │              │              │        │
│       ▼              ▼              ▼              ▼              ▼        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐  │
│  │ Network │───▶│ Bulk    │───▶│ Stateful│───▶│ Synapse │───▶│ Lineage │  │
│  │ Config  │    │ Load    │    │ Logic   │    │ Procs   │    │ Mapping │  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘  │
│       │              │              │              │              │        │
│       ▼              ▼              ▼              ▼              ▼        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐  │
│  │ Maven   │───▶│ Kafka   │───▶│ CEP     │───▶│ Power   │───▶│ Testing │  │
│  │ Project │    │ Topics  │    │ Patterns│    │ BI      │    │ (War    │  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘    │ Games)  │  │
│       │              │              │              │         └─────────┘  │
│       ▼              ▼              ▼              ▼              │        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐        ▼        │
│  │ Legacy  │───▶│ Producer│───▶│ Redis   │───▶│ Recon-  │    ┌─────────┐  │
│  │ Discover│    │ Dev     │    │ Enrich  │    │ cile    │    │ PROD    │  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘    │ CUTOVER │  │
│       │              │              │              │         └─────────┘  │
│       ▼              ▼              │              ▼                      │
│  ┌─────────┐    ┌─────────┐        │         ┌─────────┐                  │
│  │ DevOps  │───▶│ ADF     │        │         │ Tuning  │                  │
│  │ Setup   │    │ Pipeline│        │         │         │                  │
│  └─────────┘    └─────────┘        │         └─────────┘                  │
│                                    │                                       │
│                                    ▼                                       │
│                              ┌──────────┐                                  │
│                              │  CORE IP │                                  │
│                              │  (Brain) │                                  │
│                              └──────────┘                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Week 1: Infrastructure & Foundation

**Goal:** Establish the cloud environment and Java project framework.

### Day 1: Azure Provisioning

**Objective:** Create the core Azure infrastructure

**Deliverables:**
1. Resource Group: `rg-fraud-detection-prod`
2. Azure Kubernetes Service (AKS) cluster
3. Azure Synapse Analytics workspace
4. Azure Data Lake Storage (ADLS) Gen2 account

**Azure CLI Commands:**

```bash
# Create Resource Group
az group create --name rg-fraud-detection-prod --location eastus

# Create AKS Cluster
az aks create \
    --resource-group rg-fraud-detection-prod \
    --name aks-fraud-detection \
    --node-count 3 \
    --node-vm-size Standard_D4s_v3 \
    --enable-managed-identity \
    --generate-ssh-keys

# Create Storage Account (ADLS Gen2)
az storage account create \
    --name stfrauddetectiondl \
    --resource-group rg-fraud-detection-prod \
    --location eastus \
    --sku Standard_LRS \
    --kind StorageV2 \
    --hns true

# Create Synapse Workspace
az synapse workspace create \
    --name synw-fraud-detection \
    --resource-group rg-fraud-detection-prod \
    --storage-account stfrauddetectiondl \
    --file-system synapse-fs \
    --sql-admin-login sqladmin \
    --sql-admin-login-password <secure-password> \
    --location eastus
```

**Validation:**
- [ ] Resource Group visible in Azure Portal
- [ ] AKS cluster running with 3 nodes
- [ ] ADLS Gen2 account accessible
- [ ] Synapse workspace deployed

---

### Day 2: Networking & Security

**Objective:** Configure secure network isolation

**Deliverables:**
1. Virtual Network (VNET) with subnets
2. Private Endpoints for Synapse and Storage
3. Network Security Groups (NSG)
4. Managed Identities configuration

**Network Architecture:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    VNET: vnet-fraud-detection                   │
│                        10.0.0.0/16                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐  ┌─────────────────────┐             │
│  │ Subnet: aks-subnet  │  │ Subnet: pe-subnet   │             │
│  │    10.0.1.0/24      │  │    10.0.2.0/24      │             │
│  │                     │  │                     │             │
│  │  • AKS Node Pool    │  │  • Private Endpoint │             │
│  │  • Flink Pods       │  │    (Synapse)        │             │
│  │  • Alert Service    │  │  • Private Endpoint │             │
│  │                     │  │    (Storage)        │             │
│  └─────────────────────┘  └─────────────────────┘             │
│                                                                 │
│  ┌─────────────────────┐  ┌─────────────────────┐             │
│  │ Subnet: kafka-subnet│  │ Subnet: redis-subnet│             │
│  │    10.0.3.0/24      │  │    10.0.4.0/24      │             │
│  │                     │  │                     │             │
│  │  • Kafka Brokers    │  │  • Redis Cache      │             │
│  │    (if self-hosted) │  │                     │             │
│  └─────────────────────┘  └─────────────────────┘             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Validation:**
- [ ] No public IP exposure on critical services
- [ ] Private DNS zones configured
- [ ] NSG rules verified
- [ ] Managed Identity assigned to AKS

---

### Day 3: Project Scaffolding

**Objective:** Initialize the Maven multi-module project

**Deliverables:**
1. Parent POM with dependency management
2. Module structure:
   - `intelligence-common` (POJOs, DTOs)
   - `intelligence-ingestion` (Kafka producers)
   - `intelligence-processing` (Flink jobs)
   - `intelligence-enrichment` (Redis client)
   - `intelligence-persistence` (Synapse sinks)
   - `intelligence-alerts` (Spring Boot service)

**Project Structure:**

```
finance-intelligence-root/
├── pom.xml                         # Parent POM
├── intelligence-common/
│   └── src/main/java/com/frauddetection/common/
│       ├── model/                  # Transaction, FraudAlert, Location
│       ├── dto/                    # RiskScoreResult
│       ├── exception/              # FraudDetectionException
│       └── util/                   # JsonUtils
├── intelligence-ingestion/
├── intelligence-processing/
├── intelligence-enrichment/
├── intelligence-persistence/
└── intelligence-alerts/
```

**Validation:**
- [ ] `mvn clean compile` succeeds
- [ ] All module dependencies resolved
- [ ] Unit tests pass: `mvn test`

---

### Day 4: Legacy Discovery

**Objective:** Inventory on-premise data sources

**Deliverables:**
1. Oracle schema documentation
2. Hadoop table inventory
3. Sensitive column identification
4. Data volume estimates

**Discovery Queries:**

```sql
-- Oracle: List all transaction tables
SELECT table_name, num_rows, avg_row_len
FROM dba_tables
WHERE owner = 'TRANSACTION_OWNER'
ORDER BY num_rows DESC;

-- Oracle: Identify sensitive columns
SELECT table_name, column_name, data_type
FROM all_tab_columns
WHERE column_name LIKE '%CARD%'
   OR column_name LIKE '%SSN%'
   OR column_name LIKE '%ACCOUNT%';
```

**Sensitive Data Classification:**

| Table | Column | Classification | PII Type |
|-------|--------|----------------|----------|
| TXN_FACT | CARD_NUMBER | Highly Confidential | PCI |
| CUSTOMER_DIM | SSN | Highly Confidential | PII |
| CUSTOMER_DIM | EMAIL | Confidential | PII |

**Validation:**
- [ ] Complete table inventory documented
- [ ] Row counts and data sizes estimated
- [ ] Sensitive columns flagged for Purview

---

### Day 5: DevOps Setup

**Objective:** Configure CI/CD pipeline

**Deliverables:**
1. Azure Container Registry (ACR)
2. Azure DevOps project/Jenkins
3. Docker build pipeline for Flink jobs
4. Kubernetes deployment manifests

**Dockerfile for Flink Job:**

```dockerfile
FROM flink:1.18.1-java17

# Add the uber-JAR
COPY intelligence-processing/target/intelligence-processing-1.0.0-SNAPSHOT.jar /opt/flink/usrlib/

# Set entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["jobmanager"]
```

**Validation:**
- [ ] Docker image builds successfully
- [ ] Image pushed to ACR
- [ ] Basic deployment to AKS verified

---

## Week 2: Legacy Migration & Ingestion Layer

**Goal:** Move historical data and start live data streams.

### Day 6: Bash Scripting for Sqoop

**Objective:** Create incremental import scripts

**Sqoop Script (`sqoop_import.sh`):**

```bash
#!/bin/bash

# Configuration
ORACLE_CONN="jdbc:oracle:thin:@//oracle-host:1521/ORCL"
ORACLE_USER="etl_user"
HDFS_TARGET="abfss://raw@stfrauddetectiondl.dfs.core.windows.net/oracle_migration"

# Incremental import using lastmodified
sqoop import \
    --connect "$ORACLE_CONN" \
    --username "$ORACLE_USER" \
    --password-file /secure/oracle.pwd \
    --table TRANSACTION_FACT \
    --target-dir "$HDFS_TARGET/transactions" \
    --incremental lastmodified \
    --check-column LAST_MODIFIED \
    --last-value "2024-01-01 00:00:00" \
    --as-parquetfile \
    --compress \
    --num-mappers 8
```

**Validation:**
- [ ] Test import completes without errors
- [ ] Data appears in ADLS Gen2
- [ ] Parquet files readable in Synapse

---

### Day 7: Bulk Data Load

**Objective:** Load historical data into Synapse

**Synapse Table DDL:**

```sql
CREATE TABLE dbo.fact_transactions (
    transaction_id          NVARCHAR(50) NOT NULL,
    customer_id             NVARCHAR(50) NOT NULL,
    card_number_hash        NVARCHAR(64),
    amount                  DECIMAL(18,2),
    currency                NVARCHAR(3),
    merchant_id             NVARCHAR(50),
    merchant_name           NVARCHAR(255),
    merchant_category_code  NVARCHAR(10),
    latitude                DECIMAL(10,6),
    longitude               DECIMAL(10,6),
    city                    NVARCHAR(100),
    country_code            NVARCHAR(2),
    device_id               NVARCHAR(100),
    device_type             NVARCHAR(50),
    transaction_type        NVARCHAR(20),
    channel                 NVARCHAR(20),
    event_time              DATETIME2,
    processing_time         DATETIME2,
    approved                BIT,
    response_code           NVARCHAR(10)
)
WITH (
    DISTRIBUTION = HASH(customer_id),
    CLUSTERED COLUMNSTORE INDEX
);
```

**COPY Command:**

```sql
COPY INTO dbo.fact_transactions
FROM 'https://stfrauddetectiondl.dfs.core.windows.net/raw/oracle_migration/transactions/*.parquet'
WITH (
    FILE_TYPE = 'PARQUET',
    CREDENTIAL = (IDENTITY = 'Managed Identity')
);
```

**Validation:**
- [ ] Row counts match source Oracle tables
- [ ] Data quality checks pass
- [ ] Query performance acceptable

---

### Day 8: Kafka Configuration

**Objective:** Set up Kafka topics

**Topic Configuration:**

| Topic | Partitions | Replication | Retention |
|-------|------------|-------------|-----------|
| transactions-live | 12 | 3 | 7 days |
| fraud-alerts | 6 | 3 | 30 days |

**Kafka Commands:**

```bash
# Create transactions topic
kafka-topics.sh --create \
    --bootstrap-server kafka:9092 \
    --topic transactions-live \
    --partitions 12 \
    --replication-factor 3 \
    --config retention.ms=604800000 \
    --config compression.type=lz4

# Create alerts topic
kafka-topics.sh --create \
    --bootstrap-server kafka:9092 \
    --topic fraud-alerts \
    --partitions 6 \
    --replication-factor 3 \
    --config retention.ms=2592000000
```

**Validation:**
- [ ] Topics created with correct configuration
- [ ] Producer can send test messages
- [ ] Consumer can receive messages

---

### Day 9: Live Transaction Producer

**Objective:** Develop Java Faker-based producer

**Key Features:**
- Simulates realistic transaction patterns
- Supports fraud scenario injection
- Configurable transaction rate

**Validation:**
- [ ] Producer sends 1000+ TPS without errors
- [ ] Messages visible in Kafka
- [ ] Fraud patterns generate correctly

---

### Day 10: ADF Pipeline Creation

**Objective:** Automate data movement

**Pipeline Design:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    ADF Pipeline: Daily ETL                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │  Lookup:     │───▶│  Copy Data:  │───▶│  Stored      │      │
│  │  Last Load   │    │  Raw → Stage │    │  Procedure   │      │
│  │  Timestamp   │    │              │    │  Merge       │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                 │
│  Schedule: Daily at 02:00 UTC                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Validation:**
- [ ] Pipeline runs successfully
- [ ] Data lands in staging tables
- [ ] Merge to production tables works

---

## Week 3: Real-Time Stream Processing

**Goal:** Implement the fraud detection "brain".

### Day 11: Flink on Kubernetes

**Objective:** Deploy Flink cluster

**FlinkDeployment CRD:**

```yaml
apiVersion: flink.apache.org/v1beta1
kind: FlinkDeployment
metadata:
  name: fraud-detection-job
spec:
  image: acr-fraud.azurecr.io/flink-fraud:1.0.0
  flinkVersion: v1_18
  flinkConfiguration:
    taskmanager.numberOfTaskSlots: "8"
    state.backend: rocksdb
    state.checkpoints.dir: abfss://checkpoints@stfrauddetectiondl.dfs.core.windows.net/flink
    execution.checkpointing.interval: "60000"
  serviceAccount: flink-sa
  jobManager:
    resource:
      memory: "2048m"
      cpu: 1
  taskManager:
    replicas: 3
    resource:
      memory: "4096m"
      cpu: 2
  job:
    jarURI: local:///opt/flink/usrlib/intelligence-processing-1.0.0-SNAPSHOT.jar
    parallelism: 8
    upgradeMode: savepoint
```

**Validation:**
- [ ] JobManager pod running
- [ ] TaskManager pods running (3 replicas)
- [ ] Flink Web UI accessible
- [ ] Checkpoints saving to ADLS

---

### Day 12: Stateful Core Logic

**Objective:** Implement KeyedProcessFunction

**Key Implementation Points:**
- MapState for transaction window
- ValueState for last location
- State cleanup on TTL expiry

**Validation:**
- [ ] State persists across restarts
- [ ] Memory usage stable
- [ ] Throughput > 10K TPS

---

### Day 13: Velocity & Geo-Velocity Rules

**Objective:** Implement detection rules

**Rule Specifications:**

| Rule | Threshold | Window |
|------|-----------|--------|
| Velocity Amount | $2,000 | 60 seconds |
| Velocity Count | 5 transactions | 60 seconds |
| Geo-Velocity | 800 km/h | N/A |

**Validation:**
- [ ] Velocity attacks detected within 5 seconds
- [ ] Impossible travel flagged correctly
- [ ] False positive rate < 5%

---

### Day 14: CEP Patterns

**Objective:** Implement "Testing the Waters"

**Pattern Validation:**
- Send 3 transactions < $1.00
- Follow with 1 transaction > $500
- Verify alert generated within 10 minutes

**Validation:**
- [ ] Pattern matches correctly
- [ ] Alert contains all triggering transactions
- [ ] No false positives on normal sequences

---

### Day 15: Redis Enrichment

**Objective:** Integrate Redis lookups

**Redis Data Setup:**

```bash
# Add test IP to blacklist
redis-cli SADD fraud:ip:blacklist "192.168.1.100"

# Set merchant reputation scores
redis-cli SET fraud:merchant:score:MER-12345 "0.95"
redis-cli SET fraud:merchant:score:MER-99999 "0.15"
```

**Validation:**
- [ ] Blacklist lookup < 1ms
- [ ] Merchant scores retrieved correctly
- [ ] Fallback works when Redis unavailable

---

## Week 4: Storage, Analytics & Reporting

**Goal:** Persist data and create visualizations.

### Day 16: Sink Implementation

**Objective:** Configure Flink sinks

**Sinks:**
1. Kafka Sink → fraud-alerts topic
2. Synapse Sink → fact_transactions table

**Validation:**
- [ ] All transactions written to Synapse
- [ ] Alerts published to Kafka
- [ ] No data loss during failures

---

### Day 17: Synapse Optimization

**Objective:** Create stored procedures

**Aggregation Procedure:**

```sql
CREATE PROCEDURE dbo.sp_fraud_hourly_summary
AS
BEGIN
    SELECT 
        DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0) AS hour,
        COUNT(*) AS alert_count,
        AVG(risk_score) AS avg_risk_score,
        SUM(transaction_amount) AS total_amount
    INTO #hourly_summary
    FROM dbo.fact_fraud_alerts
    WHERE alert_time >= DATEADD(DAY, -7, GETDATE())
    GROUP BY DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0);
    
    -- Materialize for Power BI
    TRUNCATE TABLE dbo.agg_fraud_hourly;
    INSERT INTO dbo.agg_fraud_hourly SELECT * FROM #hourly_summary;
END;
```

**Validation:**
- [ ] Procedure executes in < 30 seconds
- [ ] Aggregation data correct
- [ ] Dashboard loads in < 5 seconds

---

### Day 18: Power BI Development

**Objective:** Build fraud dashboard

**Dashboard Components:**
1. Fraud Attempts per Hour (Line Chart)
2. Geographic Heat Map
3. Top Triggered Rules (Bar Chart)
4. Real-Time Alert Feed

**Validation:**
- [ ] Dashboard connects via DirectQuery
- [ ] Real-time refresh works
- [ ] Filter interactions functional

---

### Day 19: Batch Reconciliation

**Objective:** Validate data completeness

**Reconciliation Query:**

```sql
-- Compare source (Oracle) vs target (Synapse)
WITH oracle_counts AS (
    SELECT COUNT(*) AS oracle_count
    FROM oracle_link.TRANSACTION_OWNER.TRANSACTION_FACT
    WHERE event_date >= '2024-01-01'
),
synapse_counts AS (
    SELECT COUNT(*) AS synapse_count
    FROM dbo.fact_transactions
    WHERE event_time >= '2024-01-01'
)
SELECT 
    oracle_count,
    synapse_count,
    oracle_count - synapse_count AS difference,
    CASE WHEN oracle_count = synapse_count THEN 'PASS' ELSE 'FAIL' END AS status
FROM oracle_counts, synapse_counts;
```

**Validation:**
- [ ] Row counts match within tolerance
- [ ] Discrepancies investigated
- [ ] Report generated for audit

---

### Day 20: Performance Tuning

**Objective:** Optimize system performance

**Tuning Areas:**

| Component | Metric | Target | Tuning Action |
|-----------|--------|--------|---------------|
| Flink | Throughput | > 50K TPS | Increase parallelism |
| Flink | Latency | < 100ms p99 | Reduce checkpoint interval |
| Synapse | Query time | < 5s | Add materialized views |
| Redis | Lookup time | < 1ms | Connection pooling |

**Validation:**
- [ ] Performance targets met
- [ ] Resource utilization optimal
- [ ] No memory leaks

---

## Week 5: Governance, Security & Go-Live

**Goal:** Secure the platform and deploy to production.

### Day 21: Purview Governance

**Objective:** Register data sources in Purview

**Data Sources to Register:**
1. Azure Synapse Analytics
2. ADLS Gen2
3. Kafka (via connector)

**Validation:**
- [ ] All assets scanned
- [ ] Metadata visible in catalog
- [ ] Classifications applied

---

### Day 22: Lineage Mapping

**Objective:** Document data flow

**Lineage Paths:**

```
Oracle → Sqoop → ADLS Gen2 (Raw) → ADF → Synapse
Kafka (transactions-live) → Flink → Kafka (fraud-alerts)
Kafka (transactions-live) → Flink → Synapse
```

**Validation:**
- [ ] End-to-end lineage visible
- [ ] Impact analysis works
- [ ] Lineage documented

---

### Day 23: Data Sensitivity

**Objective:** Apply sensitivity labels

**Labels:**
- `Highly Confidential`: card_number, ssn
- `Confidential`: email, phone, account_number
- `Internal`: transaction amounts, timestamps

**Validation:**
- [ ] Auto-masking works for PII
- [ ] Labels propagate to downstream assets
- [ ] Access policies enforced

---

### Day 24: End-to-End Testing

**Objective:** War games validation

**Test Scenarios:**

| Scenario | Input | Expected Output |
|----------|-------|-----------------|
| Velocity Attack | 8 transactions in 30 seconds | CRITICAL alert |
| Impossible Travel | Toronto → London in 20 min | HIGH alert |
| Testing Waters | 4x$0.50 then $800 | HIGH alert |
| Normal Activity | Regular spending pattern | No alert |

**Validation:**
- [ ] All fraud scenarios detected
- [ ] Alert latency < 100ms
- [ ] No false positives on normal traffic

---

### Day 25: Production Cutover

**Objective:** Deploy to production

**Cutover Checklist:**

- [ ] Final Docker images tagged as `prod`
- [ ] Kubernetes manifests applied to prod cluster
- [ ] Prometheus/Grafana dashboards configured
- [ ] Alert routing to on-call team
- [ ] Runbooks documented
- [ ] Rollback procedure tested

**Monitoring Setup:**

```yaml
# Prometheus ServiceMonitor
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: flink-metrics
spec:
  selector:
    matchLabels:
      app: flink
  endpoints:
    - port: metrics
      interval: 15s
```

**Validation:**
- [ ] Production traffic flowing
- [ ] Metrics visible in Grafana
- [ ] Alerts firing to PagerDuty/Slack
- [ ] System stable for 24 hours

---

## Post-Go-Live Support

### Week 6+ Activities

| Activity | Frequency | Owner |
|----------|-----------|-------|
| Performance review | Weekly | Platform Team |
| False positive analysis | Daily | Fraud Team |
| Model retraining | Monthly | Data Science |
| Security audit | Quarterly | Security Team |
| Capacity planning | Quarterly | Infrastructure |

---

## Risk Mitigation Summary

| Risk | Mitigation | Checkpoint |
|------|------------|------------|
| Data loss | Exactly-once semantics, checkpoints | Day 11 |
| Late detection | Latency monitoring, alerts | Day 24 |
| False positives | Threshold tuning, analyst feedback | Day 20 |
| System failure | HA deployment, auto-recovery | Day 25 |
| Security breach | Private links, managed identity | Day 2 |


