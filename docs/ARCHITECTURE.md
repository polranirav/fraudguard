# System Architecture

## Real-Time Financial Fraud Detection & Legacy Migration Platform

---

## High-Level Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                        REAL-TIME FRAUD DETECTION ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                              DATA INGESTION LAYER                               │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │   │
│  │  │   Mobile     │  │    Web       │  │    POS       │  │    ATM       │        │   │
│  │  │   Banking    │  │  Gateway     │  │  Terminals   │  │  Network     │        │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘        │   │
│  │          │                 │                 │                 │               │   │
│  │          └─────────────────┴─────────────────┴─────────────────┘               │   │
│  │                                      │                                          │   │
│  │                                      ▼                                          │   │
│  │                          ┌────────────────────┐                                 │   │
│  │                          │   Apache Kafka     │                                 │   │
│  │                          │ transactions-live  │                                 │   │
│  │                          └────────────────────┘                                 │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                         │                                              │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                          STREAM PROCESSING LAYER                                │   │
│  │                                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐   │   │
│  │  │                        Apache Flink Cluster                              │   │   │
│  │  │  ┌──────────────────────────────────────────────────────────────────┐   │   │   │
│  │  │  │                     JobManager                                    │   │   │   │
│  │  │  └──────────────────────────────────────────────────────────────────┘   │   │   │
│  │  │         │                    │                    │                      │   │   │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │   │   │
│  │  │  │ TaskManager  │  │ TaskManager  │  │ TaskManager  │                   │   │   │
│  │  │  │   ┌──────┐   │  │   ┌──────┐   │  │   ┌──────┐   │                   │   │   │
│  │  │  │   │State │   │  │   │State │   │  │   │State │   │                   │   │   │
│  │  │  │   │(User)│   │  │   │(User)│   │  │   │(User)│   │                   │   │   │
│  │  │  │   └──────┘   │  │   └──────┘   │  │   └──────┘   │                   │   │   │
│  │  │  └──────────────┘  └──────────────┘  └──────────────┘                   │   │   │
│  │  └─────────────────────────────────────────────────────────────────────────┘   │   │
│  │                                    │                                            │   │
│  │              ┌─────────────────────┼─────────────────────┐                     │   │
│  │              │                     │                     │                      │   │
│  │              ▼                     ▼                     ▼                      │   │
│  │    ┌────────────────┐   ┌────────────────┐   ┌────────────────┐                │   │
│  │    │    Velocity    │   │  Geo-Velocity  │   │   CEP Pattern  │                │   │
│  │    │    Detection   │   │  (Impossible   │   │   ("Testing    │                │   │
│  │    │                │   │   Travel)      │   │   the Waters") │                │   │
│  │    └────────────────┘   └────────────────┘   └────────────────┘                │   │
│  │                                                                                 │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                         │                                              │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                           ENRICHMENT LAYER                                      │   │
│  │                                                                                 │   │
│  │  ┌────────────────────────────────────────────────────────────────────────┐    │   │
│  │  │                            Redis Cluster                                │    │   │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │    │   │
│  │  │  │ IP Blacklist │  │  Merchant    │  │  Customer    │                  │    │   │
│  │  │  │              │  │  Reputation  │  │  Embeddings  │                  │    │   │
│  │  │  └──────────────┘  └──────────────┘  └──────────────┘                  │    │   │
│  │  └────────────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                                 │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                         │                                              │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                          OUTPUT & PERSISTENCE LAYER                             │   │
│  │                                                                                 │   │
│  │   ┌─────────────────┐                           ┌─────────────────┐            │   │
│  │   │  Kafka Topic    │                           │  Azure Synapse  │            │   │
│  │   │  fraud-alerts   │                           │  Analytics      │            │   │
│  │   └─────────────────┘                           └─────────────────┘            │   │
│  │           │                                              │                      │   │
│  │           ▼                                              ▼                      │   │
│  │   ┌─────────────────┐                           ┌─────────────────┐            │   │
│  │   │ Alert Service   │                           │   Power BI      │            │   │
│  │   │ (Spring Boot)   │                           │   Dashboards    │            │   │
│  │   └─────────────────┘                           └─────────────────┘            │   │
│  │                                                                                 │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Architecture

### 1. Apache Kafka (Event Streaming)

**Purpose:** Durable, high-throughput event streaming backbone

```
┌─────────────────────────────────────────────────────────────────┐
│                      KAFKA CLUSTER                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Topic: transactions-live                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Partition 0  │  Partition 1  │  Partition 2  │  ...    │   │
│  │  [Customer    │  [Customer    │  [Customer    │         │   │
│  │   A-G]        │   H-N]        │   O-Z]        │         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Topic: fraud-alerts                                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Partition 0  │  Partition 1  │  Partition 2  │  ...    │   │
│  │  [Alerts by   │  [Alerts by   │  [Alerts by   │         │   │
│  │   Customer]   │   Customer]   │   Customer]   │         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Configuration:                                                 │
│  • Replication Factor: 3                                       │
│  • Min ISR: 2                                                  │
│  • Retention: 7 days                                           │
│  • Compression: lz4                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2. Apache Flink (Stream Processing)

**Purpose:** Stateful, exactly-once stream processing

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLINK JOB TOPOLOGY                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Kafka Source                          │   │
│  │              (transactions-live topic)                   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            │                                    │
│                   Watermark Strategy                            │
│               (5 second out-of-orderness)                       │
│                            │                                    │
│                            ▼                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   KeyBy(customerId)                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            │                                    │
│              ┌─────────────┴─────────────┐                     │
│              │                           │                      │
│              ▼                           ▼                      │
│  ┌─────────────────────┐    ┌─────────────────────┐            │
│  │ FraudDetection      │    │  CEP Pattern        │            │
│  │ ProcessFunction     │    │  (Testing Waters)   │            │
│  │                     │    │                     │            │
│  │ State:              │    │ Pattern:            │            │
│  │ • MapState<Txns>    │    │ • 3+ small txns     │            │
│  │ • ValueState<Loc>   │    │ • then large txn    │            │
│  │ • ValueState<Time>  │    │ • within 10 min     │            │
│  └─────────────────────┘    └─────────────────────┘            │
│              │                           │                      │
│              └───────────┬───────────────┘                      │
│                          │                                      │
│                          ▼                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                      Union Alerts                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          │                                      │
│              ┌───────────┴───────────┐                         │
│              │                       │                          │
│              ▼                       ▼                          │
│  ┌─────────────────────┐  ┌─────────────────────┐              │
│  │    Kafka Sink       │  │   Synapse Sink      │              │
│  │  (fraud-alerts)     │  │   (all events)      │              │
│  └─────────────────────┘  └─────────────────────┘              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3. State Management

**Purpose:** In-memory per-user state for millisecond lookups

```
┌─────────────────────────────────────────────────────────────────┐
│                   FLINK STATE MANAGEMENT                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Per-User Keyed State (In TaskManager Memory)                   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Customer: CUST-12345                                    │   │
│  │  ──────────────────────────────────────────────────────  │   │
│  │                                                          │   │
│  │  MapState<Long, Transaction> recentTransactions          │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ 1703577600000 -> Transaction{$45.00, Starbucks}  │   │   │
│  │  │ 1703577660000 -> Transaction{$120.00, Amazon}    │   │   │
│  │  │ 1703577720000 -> Transaction{$8.50, Subway}      │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  ValueState<Location> lastKnownLocation                  │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ {lat: 43.6532, lon: -79.3832, city: "Toronto"}   │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  ValueState<Long> lastTransactionTime                    │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ 1703577720000                                     │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  State Backend: RocksDB (for large state)                       │
│  Checkpointing: Azure Blob Storage                              │
│  Interval: 60 seconds                                           │
│  Semantics: Exactly-Once                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Architecture

### Real-Time Processing Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    REAL-TIME DATA FLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Transaction Event Generated                                 │
│     ─────────────────────────                                   │
│     Customer swipes card at merchant                            │
│                     │                                           │
│                     ▼                                           │
│  2. Kafka Ingestion                                             │
│     ────────────────                                            │
│     Producer sends to transactions-live topic                   │
│     Key: customerId (for partition affinity)                    │
│     Latency: < 10ms                                             │
│                     │                                           │
│                     ▼                                           │
│  3. Flink Processing                                            │
│     ────────────────                                            │
│     a) Watermark extraction (event time)                        │
│     b) Key by customerId                                        │
│     c) Process with stateful function                           │
│        - Check velocity (amount/count in window)                │
│        - Check geo-velocity (impossible travel)                 │
│        - Enrich from Redis (blacklist, reputation)              │
│     d) CEP pattern matching                                     │
│     Latency: 10-50ms                                            │
│                     │                                           │
│                     ▼                                           │
│  4. Alert Generation                                            │
│     ─────────────────                                           │
│     If rules triggered:                                         │
│        - Create FraudAlert with risk score                      │
│        - Publish to fraud-alerts topic                          │
│     Latency: < 5ms                                              │
│                     │                                           │
│                     ▼                                           │
│  5. Alert Service Processing                                    │
│     ─────────────────────────                                   │
│     Spring Boot service consumes alert                          │
│        - Route by severity                                      │
│        - Trigger notifications                                  │
│        - Initiate transaction holds                             │
│     Latency: 20-100ms                                           │
│                     │                                           │
│                     ▼                                           │
│  TOTAL END-TO-END LATENCY: < 100ms                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Legacy Migration Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    LEGACY MIGRATION FLOW                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐                                           │
│  │    Oracle DB    │─────────┐                                 │
│  │   (On-Premise)  │         │                                 │
│  └─────────────────┘         │                                 │
│           │                  │                                  │
│    Apache Sqoop              │  SSMA                            │
│    (Historical Data)         │  (Schema Translation)            │
│           │                  │                                  │
│           ▼                  ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  ADLS Gen2 (Data Lake)                   │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │   │
│  │  │    Raw      │  │   Curated   │  │  Processed  │      │   │
│  │  │   Layer     │──▶│   Layer    │──▶│   Layer    │      │   │
│  │  │  (Parquet)  │  │            │  │             │      │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                  │
│                    Azure Data Factory                           │
│                    (Orchestration)                              │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Azure Synapse Analytics                 │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │              Dedicated SQL Pool                  │    │   │
│  │  │  • Fact tables (transactions, alerts)           │    │   │
│  │  │  • Dimension tables (customers, merchants)      │    │   │
│  │  │  • Materialized views for dashboards            │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                      Power BI                            │   │
│  │  • Fraud Attempts per Hour                              │   │
│  │  • Geographic Heat Map                                  │   │
│  │  • Rule Trigger Analysis                                │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Deployment Architecture

### Azure Kubernetes Service (AKS)

```
┌─────────────────────────────────────────────────────────────────┐
│                    AKS CLUSTER ARCHITECTURE                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Ingress Controller                    │   │
│  │                    (nginx / Azure AG)                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                  │
│  ┌───────────────────────────┴───────────────────────────┐     │
│  │                                                        │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │           Flink Namespace                        │  │     │
│  │  │  ┌───────────────────────────────────────────┐  │  │     │
│  │  │  │         Flink Kubernetes Operator         │  │  │     │
│  │  │  └───────────────────────────────────────────┘  │  │     │
│  │  │                      │                           │  │     │
│  │  │  ┌───────────────────┴───────────────────────┐  │  │     │
│  │  │  │                                           │  │  │     │
│  │  │  │  ┌─────────────┐    ┌─────────────┐      │  │  │     │
│  │  │  │  │ JobManager  │    │ JobManager  │      │  │  │     │
│  │  │  │  │   (HA)      │    │   (Standby) │      │  │  │     │
│  │  │  │  └─────────────┘    └─────────────┘      │  │  │     │
│  │  │  │                                           │  │  │     │
│  │  │  │  ┌─────────────┐ ┌─────────────┐ ┌────┐  │  │  │     │
│  │  │  │  │TaskManager 1│ │TaskManager 2│ │... │  │  │  │     │
│  │  │  │  │ (8 slots)   │ │ (8 slots)   │ │    │  │  │  │     │
│  │  │  │  └─────────────┘ └─────────────┘ └────┘  │  │  │     │
│  │  │  │                                           │  │  │     │
│  │  │  └───────────────────────────────────────────┘  │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  │                                                        │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │           Alerts Namespace                       │  │     │
│  │  │  ┌───────────────────────────────────────────┐  │  │     │
│  │  │  │  alert-service-deployment (3 replicas)    │  │  │     │
│  │  │  └───────────────────────────────────────────┘  │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  │                                                        │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │           Monitoring Namespace                   │  │     │
│  │  │  ┌───────────────┐  ┌───────────────┐           │  │     │
│  │  │  │  Prometheus   │  │   Grafana     │           │  │     │
│  │  │  └───────────────┘  └───────────────┘           │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  │                                                        │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Security Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    SECURITY ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   Network Security                       │   │
│  │  • Virtual Network (VNET) isolation                     │   │
│  │  • Private Links for all Azure services                 │   │
│  │  • Network Security Groups (NSG)                        │   │
│  │  • No public internet exposure                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Identity & Access                       │   │
│  │  • Azure AD for authentication                          │   │
│  │  • Managed Identities (no credentials in code)          │   │
│  │  • Role-Based Access Control (RBAC)                     │   │
│  │  • Just-In-Time (JIT) access for production             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Data Protection                         │   │
│  │  • Encryption at rest (Azure Storage Service Encryption) │   │
│  │  • Encryption in transit (TLS 1.3)                      │   │
│  │  • Column-level encryption for PII                      │   │
│  │  • Purview sensitivity labels for auto-masking          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Governance                              │   │
│  │  • Microsoft Purview for data lineage                   │   │
│  │  • Automated PII detection                              │   │
│  │  • Audit logging to Azure Monitor                       │   │
│  │  • Compliance dashboards                                │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Scalability Considerations

| Component | Scaling Strategy | Trigger |
|-----------|-----------------|---------|
| Kafka | Add partitions | Topic lag > threshold |
| Flink TaskManagers | Horizontal pod autoscaling | CPU > 70% or backpressure |
| Redis | Cluster mode sharding | Memory > 80% |
| Alert Service | HPA based on queue depth | Pending alerts > 1000 |
| Synapse | Scale DWU | Query latency degradation |

---

## Failure Handling

| Failure Type | Detection | Recovery |
|--------------|-----------|----------|
| Flink job crash | JobManager heartbeat | Automatic restart from checkpoint |
| Kafka broker down | Consumer lag spike | Rebalance to healthy brokers |
| Redis unavailable | Connection timeout | Circuit breaker, continue without enrichment |
| Synapse timeout | Write timeout | Retry with exponential backoff |
| Network partition | Health check failures | Automatic pod rescheduling |


