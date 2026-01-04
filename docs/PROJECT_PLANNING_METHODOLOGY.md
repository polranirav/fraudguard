# Project Planning Methodology

## Real-Time Financial Fraud Detection & Legacy Migration Platform

---

## Executive Summary

This document outlines the strategic planning methodology used to design and structure the 5-week enterprise sprint for the Real-Time Financial Fraud Detection & Legacy Migration Platform. The approach prioritizes a bottom-up dependency chain, ensuring each phase builds on verified foundations.

---

## 1. Problem-Driven Architecture Design

### Core Business Problem

The project planning commenced with a rigorous analysis of the **fundamental business challenge**: legacy batch-oriented systems (Hadoop/Oracle) are fundamentally incapable of detecting fraud in real-time. Financial institutions face a critical "latency gap" where data used for risk scoring may be hours or even days old.

### Transformation Journey

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           TRANSFORMATION JOURNEY                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   LEGACY STATE                              TARGET STATE                    │
│   ════════════                              ════════════                    │
│                                                                             │
│   ┌──────────────┐                         ┌──────────────┐                │
│   │  Oracle DB   │                         │ Apache Kafka │                │
│   │  (On-Prem)   │                         │ (Streaming)  │                │
│   └──────────────┘                         └──────────────┘                │
│          │                                        │                         │
│          ▼                                        ▼                         │
│   ┌──────────────┐         ═══════>        ┌──────────────┐                │
│   │   Hadoop     │        MIGRATION        │ Apache Flink │                │
│   │  (HDFS)      │                         │ (Stateful)   │                │
│   └──────────────┘                         └──────────────┘                │
│          │                                        │                         │
│          ▼                                        ▼                         │
│   ┌──────────────┐                         ┌──────────────┐                │
│   │  Batch ETL   │                         │Azure Synapse │                │
│   │  (Slow)      │                         │   + Redis    │                │
│   └──────────────┘                         └──────────────┘                │
│                                                                             │
│   Latency: Hours/Days                      Latency: Milliseconds           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Constraints Identified

| Constraint | Description | Impact on Design |
|------------|-------------|------------------|
| **Latency Requirement** | Must drop from hours/days to milliseconds | Requires streaming architecture |
| **Regulatory Compliance** | Must maintain compliance during migration | Phased cutover with parallel running |
| **Zero Data Loss** | No tolerance for transaction data loss | Exactly-once processing semantics |
| **Peak Scalability** | Handle high-volume shopping seasons | Horizontal scaling via Kubernetes |

---

## 2. Technology Stack Selection Rationale

### "Java-First" Approach Justification

The decision to adopt a Java-centric framework was driven by several factors:

1. **Type Safety**: Compile-time type checking reduces runtime errors in critical financial logic
2. **Ecosystem Maturity**: Apache Kafka and Flink are native Java projects
3. **Enterprise Adoption**: Java remains the dominant language in banking IT departments
4. **Performance**: JVM optimizations and Flink's POJO serialization provide high throughput

### Component Selection Matrix

| Component | Technology | Selection Rationale |
|-----------|------------|---------------------|
| **Event Streaming** | Apache Kafka | Durable, replayable logs; 1M+ events/sec throughput; partitioning for parallel processing |
| **Stream Processing** | Apache Flink | Native stateful processing; exactly-once semantics; CEP pattern support |
| **Low-Latency Lookup** | Redis | Sub-millisecond enrichment for IP blacklists and merchant scores |
| **Data Warehouse** | Azure Synapse | MPP architecture for historical analysis; Power BI integration |
| **Orchestration** | Azure Kubernetes Service | Elastic scaling for Flink clusters; Operator pattern support |
| **Data Governance** | Microsoft Purview | Automated lineage tracking; sensitivity labeling for PII |
| **CI/CD** | Azure DevOps / Jenkins | Docker-based Flink job deployment; uber-JAR packaging |

### Comparative Analysis: Legacy vs. Modern

| Dimension | Legacy (Hadoop/Oracle) | Modern (Flink/Azure) |
|-----------|------------------------|----------------------|
| Data Processing | Batch-oriented, scheduled intervals | Continuous event streaming |
| Detection Speed | Reactive (post-transaction) | Proactive (in-flight, sub-second) |
| Scalability | Fixed clusters, high CapEx | Elastic cloud-native scaling |
| State Management | External database lookups | In-memory managed keyed state |
| Fault Tolerance | Checkpoint-based recovery (slow) | Exactly-once distributed snapshots |

---

## 3. Sprint Structure Philosophy

### Bottom-Up Dependency Chain

The 5-week plan follows a carefully orchestrated dependency sequence:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SPRINT DEPENDENCY CHAIN                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  WEEK 1                WEEK 2                WEEK 3                         │
│  ══════                ══════                ══════                         │
│  Foundation            Data Pipelines        Intelligence Engine            │
│                                                                             │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                 │
│  │ Azure       │─────▶│ Sqoop       │─────▶│ Flink on    │                 │
│  │ Provisioning│      │ Scripts     │      │ Kubernetes  │                 │
│  └─────────────┘      └─────────────┘      └─────────────┘                 │
│         │                    │                    │                         │
│         ▼                    ▼                    ▼                         │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                 │
│  │ Network     │─────▶│ Kafka       │─────▶│ Stateful    │                 │
│  │ Security    │      │ Topics      │      │ Processing  │                 │
│  └─────────────┘      └─────────────┘      └─────────────┘                 │
│         │                    │                    │                         │
│         ▼                    ▼                    ▼                         │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                 │
│  │ Maven       │─────▶│ Transaction │─────▶│ CEP         │                 │
│  │ Project     │      │ Producer    │      │ Patterns    │                 │
│  └─────────────┘      └─────────────┘      └─────────────┘                 │
│                                                   │                         │
│                                                   ▼                         │
│                             WEEK 4                WEEK 5                    │
│                             ══════                ══════                    │
│                             Analytics             Go-Live                   │
│                                                                             │
│                       ┌─────────────┐      ┌─────────────┐                 │
│                       │ Flink       │─────▶│ Purview     │                 │
│                       │ Sinks       │      │ Governance  │                 │
│                       └─────────────┘      └─────────────┘                 │
│                              │                    │                         │
│                              ▼                    ▼                         │
│                       ┌─────────────┐      ┌─────────────┐                 │
│                       │ Synapse     │─────▶│ War Games   │                 │
│                       │ Procedures  │      │ Testing     │                 │
│                       └─────────────┘      └─────────────┘                 │
│                              │                    │                         │
│                              ▼                    ▼                         │
│                       ┌─────────────┐      ┌─────────────┐                 │
│                       │ Power BI    │─────▶│ Production  │                 │
│                       │ Dashboards  │      │ Cutover     │                 │
│                       └─────────────┘      └─────────────┘                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Week-by-Week Dependency Rationale

#### Week 1: Foundation (Days 1-5)
**Why it must complete first:**
- AKS cluster is required before deploying Flink
- VNET/Private Links are required for secure data movement
- Maven modules define code organization for all subsequent development

#### Week 2: Data Pipelines (Days 6-10)
**Dependencies on Week 1:**
- Kafka requires network infrastructure to be in place
- Sqoop scripts need ADLS Gen2 endpoints provisioned
- ADF pipelines connect to the provisioned Synapse instance

#### Week 3: Intelligence Engine (Days 11-15)
**Dependencies on Week 2:**
- Flink jobs consume from Kafka topics established in Week 2
- Redis enrichment requires network paths configured in Week 1
- CEP patterns are the core fraud detection "brain"

#### Week 4: Analytics (Days 16-20)
**Dependencies on Week 3:**
- Sinks write to infrastructure provisioned in Week 1
- Power BI visualizes data processed by Flink in Week 3
- Synapse procedures optimize query performance for dashboards

#### Week 5: Governance & Go-Live (Days 21-25)
**Dependencies on All Previous Weeks:**
- Purview scans require populated data sources
- Lineage mapping needs complete end-to-end data flow
- War games validate the entire integrated system

---

## 4. Module Design Decisions

### Maven Multi-Module Architecture

```
finance-intelligence-root/
│
├── pom.xml                         # Parent POM (dependency management)
│
├── intelligence-common/            # Shared components
│   └── src/main/java/
│       └── com/frauddetection/common/
│           ├── model/              # POJOs (Transaction, Alert, Location)
│           ├── dto/                # Data Transfer Objects
│           ├── exception/          # Custom exceptions
│           └── util/               # Shared utilities
│
├── intelligence-ingestion/         # Kafka producers
│   └── src/main/java/
│       └── com/frauddetection/ingestion/
│           ├── producer/           # Transaction producer (Java Faker)
│           └── config/             # Kafka configuration
│
├── intelligence-processing/        # Flink jobs (uber-JAR)
│   └── src/main/java/
│       └── com/frauddetection/processing/
│           ├── job/                # Main Flink job entry points
│           ├── function/           # KeyedProcessFunction implementations
│           ├── pattern/            # CEP pattern definitions
│           └── state/              # State management utilities
│
├── intelligence-enrichment/        # External lookups
│   └── src/main/java/
│       └── com/frauddetection/enrichment/
│           ├── redis/              # Redis client (Jedis)
│           └── vector/             # Embedding similarity search
│
├── intelligence-persistence/       # Sinks and storage
│   └── src/main/java/
│       └── com/frauddetection/persistence/
│           ├── sink/               # Flink sink implementations
│           ├── parquet/            # Parquet serialization
│           └── synapse/            # Synapse JDBC connectors
│
└── intelligence-alerts/            # Notification service
    └── src/main/java/
        └── com/frauddetection/alerts/
            ├── consumer/           # Kafka consumer for alerts
            ├── notification/       # Alert dispatch logic
            └── api/                # REST API (Spring Boot)
```

### Module Separation Benefits

| Benefit | Description |
|---------|-------------|
| **Independent Deployment** | Flink jobs can be deployed separately from alert services |
| **Shared POJOs** | Common module provides consistent data models across all components |
| **Isolated Testing** | Each module can be unit tested in isolation |
| **Parallel Development** | Teams can work on different modules simultaneously |
| **Dependency Management** | Parent POM centralizes version control |

---

## 5. Fraud Detection Rule Sequencing

### Complexity-Based Ordering

Rules were ordered by increasing complexity and state requirements:

| Day | Rule | Complexity | Flink State Required | Description |
|-----|------|------------|---------------------|-------------|
| 13 | Velocity/Frequency | Low | `MapState<Long, Transaction>` | Detect rapid transaction successions |
| 13 | Geo-Velocity | Medium | `ValueState<Location>` | "Impossible travel" detection |
| 14 | CEP "Testing Waters" | High | Pattern matching across events | Small transactions followed by large transfer |
| 15 | Hybrid Scoring | Very High | Multiple state types + Redis | Combined rule/ML/embedding score |

### Mathematical Foundations

#### Haversine Formula (Geo-Velocity)

Used to calculate great-circle distance between two geographic coordinates:

```
d = 2r × arcsin(√[sin²((φ₂-φ₁)/2) + cos(φ₁)cos(φ₂)sin²((λ₂-λ₁)/2)])

Where:
  d = distance between two points
  r = Earth's radius (6,371 km)
  φ₁, φ₂ = latitudes of point 1 and point 2
  λ₁, λ₂ = longitudes of point 1 and point 2
```

Velocity is then computed as: `v = d / Δt`

If velocity exceeds 800 km/h (maximum commercial flight speed), the transaction is flagged.

#### Hybrid Risk Scoring

```
RiskScore = w₁ × RuleBasedChecks + w₂ × MLProbabilities + w₃ × cos(θ)_Similarity

Where:
  w₁, w₂, w₃ = configurable weights (sum to 1.0)
  RuleBasedChecks = binary flags from velocity/geo rules
  MLProbabilities = output from trained fraud model
  cos(θ)_Similarity = cosine similarity between transaction embedding 
                      and historical behavior vectors
```

---

## 6. Risk Mitigation Strategies

### Data Loss Prevention

| Strategy | Implementation | Timeline |
|----------|----------------|----------|
| Bulk Historical Load | Complete before streaming switch | Day 7 |
| Batch Reconciliation | ADF pipeline comparing Oracle vs. Synapse counts | Day 19 |
| Exactly-Once Semantics | Flink checkpointing to Azure Blob Storage | Day 11+ |
| Kafka Durability | Replication factor ≥ 3 for all topics | Day 8 |

### Security by Design

| Strategy | Implementation | Timeline |
|----------|----------------|----------|
| Network Isolation | Private Links before data movement | Day 2 |
| Managed Identities | No credentials in code/config | Day 2 |
| Data Lineage | Purview end-to-end tracking | Day 22 |
| Sensitivity Labels | Auto-mask credit card/PII | Day 23 |

### Performance Validation

| Strategy | Implementation | Timeline |
|----------|----------------|----------|
| Parallelism Tuning | Adjust based on test data | Day 20 |
| War Games | Known fraud scenarios | Day 24 |
| Monitoring | Prometheus/Grafana dashboards | Day 25 |

---

## 7. Key Architectural Patterns Applied

### 1. Kappa Architecture

A single streaming pipeline processes both real-time events and historical replays, ensuring consistency across different analytical timeframes.

```
┌─────────────────────────────────────────────────────────────────┐
│                      KAPPA ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐              │
│   │  Source  │────▶│  Kafka   │────▶│  Flink   │              │
│   │ Systems  │     │  Topics  │     │  Jobs    │              │
│   └──────────┘     └──────────┘     └──────────┘              │
│                          │                │                    │
│                          │                ▼                    │
│                          │         ┌──────────┐               │
│                    Historical      │  Synapse │               │
│                    Replay ─────────│  + ADLS  │               │
│                                    └──────────┘               │
│                                                                 │
│   Key Benefit: Single processing logic for real-time           │
│   and batch, eliminating "lambda complexity"                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2. Stateful Processing Pattern

Flink's `KeyedProcessFunction` maintains per-user state in-memory, eliminating external database lookups during hot-path processing.

```java
// Conceptual Example
public class FraudDetectionFunction 
    extends KeyedProcessFunction<String, Transaction, Alert> {
    
    // Per-user state maintained by Flink
    private ValueState<UserProfile> userProfile;
    private MapState<Long, Transaction> recentTransactions;
    
    @Override
    public void processElement(Transaction txn, Context ctx, Collector<Alert> out) {
        // State access at memory speed - no DB lookup
        UserProfile profile = userProfile.value();
        
        // Update state
        recentTransactions.put(txn.getTimestamp(), txn);
        
        // Fraud logic...
    }
}
```

### 3. Event-Time Processing

Watermarking ensures transactions are processed in the order they actually occurred, even if they arrive delayed due to network issues.

```
┌─────────────────────────────────────────────────────────────────┐
│                    WATERMARK PROGRESSION                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Event Time:    10:00   10:01   10:02   10:03   10:04         │
│                    │       │       │       │       │            │
│   Arrival Order:   1       3       2       5       4            │
│                    │       │       │       │       │            │
│   Watermark:     10:00 ─────────▶ 10:02 ─────────▶ 10:04       │
│                                                                 │
│   Effect: Late-arriving event (10:01 arriving 3rd) is          │
│   correctly processed before 10:02 window closes               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4. Hybrid Enrichment Pattern

Combines streaming state (Flink) with external lookups (Redis) for comprehensive risk scoring.

```
┌─────────────────────────────────────────────────────────────────┐
│                    HYBRID ENRICHMENT                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌──────────────────────────────────────────────┐             │
│   │              Flink TaskManager                │             │
│   │  ┌────────────────────────────────────────┐  │             │
│   │  │  KeyedProcessFunction                   │  │             │
│   │  │                                         │  │             │
│   │  │  1. Check in-memory state (< 1µs)       │  │             │
│   │  │     └── Recent transactions             │  │             │
│   │  │     └── User profile                    │  │             │
│   │  │                                         │  │             │
│   │  │  2. Async Redis lookup (< 1ms)   ──────────────▶ Redis  │
│   │  │     └── IP blacklist                    │  │             │
│   │  │     └── Merchant reputation             │  │             │
│   │  │     └── Vector embeddings               │  │             │
│   │  │                                         │  │             │
│   │  │  3. Compute composite score             │  │             │
│   │  └────────────────────────────────────────┘  │             │
│   └──────────────────────────────────────────────┘             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Summary

### Planning Methodology Recap

The Real-Time Fraud Detection Platform was planned through a systematic approach:

1. **Problem Identification**: Recognized the latency gap as the core business problem driving modernization

2. **Technology Selection**: Chose technologies enabling stateful stream processing with exactly-once guarantees

3. **Dependency Ordering**: Structured weeks by infrastructure → data → logic → analytics → governance

4. **Modular Design**: Separated concerns via Maven multi-module for independent deployment and testing

5. **Rule Sequencing**: Ordered fraud rules by complexity within the processing week

6. **Validation Checkpoints**: Built in reconciliation (Day 19) and war games (Day 24) for risk mitigation

### Success Criteria

| Metric | Target | Validation Method |
|--------|--------|-------------------|
| Detection Latency | < 100ms | War games timing |
| Data Loss | 0 transactions | Batch reconciliation |
| Throughput | > 100K txn/sec | Load testing |
| False Positive Rate | < 5% | Historical replay |
| System Uptime | 99.9% | Prometheus monitoring |

This methodology ensures each phase builds on verified foundations, reducing integration risk and enabling iterative testing throughout the 5-week sprint.


