# CNS-DIS Architecture: Technical Design
## Cognitive Neuro-Symbolic Digital Immune System

**Version**: 1.0  
**Date**: 2026-01-04  
**Status**: Design Phase

---

## Overview

This document describes the technical architecture for upgrading FraudGuard to a Cognitive Neuro-Symbolic Digital Immune System (CNS-DIS). The architecture integrates five advanced AI components while maintaining the existing high-performance streaming infrastructure.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CNS-DIS UPGRADED ARCHITECTURE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         DATA INGESTION                              │   │
│  │  Transaction Sources → Kafka (transactions-live)                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    APACHE FLINK PROCESSING LAYER                     │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │              FraudDetectionProcessFunction                     │  │   │
│  │  │  (Existing: Velocity, Geo-Velocity, CEP Patterns)             │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                            │                                        │   │
│  │  ┌─────────────────────────┼─────────────────────────┐            │   │
│  │  │                         │                         │            │   │
│  │  ▼                         ▼                         ▼            │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │   │
│  │  │   LNN        │  │    GNN       │  │   NSAI       │            │   │
│  │  │   Service    │  │   Service    │  │   Service    │            │   │
│  │  │ (Irregular   │  │  (Network    │  │ (Explainable │            │   │
│  │  │  Time-Series)│  │  Detection)   │  │   Decisions) │            │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘            │   │
│  │         │                 │                 │                     │   │
│  │         └─────────────────┴─────────────────┘                     │   │
│  │                            │                                        │   │
│  │                            ▼                                        │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │              Composite Risk Scoring Engine                    │  │   │
│  │  │  RiskScore = f(Rules, LNN, GNN, NSAI, Causal)                 │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                            │                                        │   │
│  │                            ▼                                        │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │         Causal Inference Engine (Explanations)                 │  │   │
│  │  │  Generates counterfactual explanations for decisions           │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    OUTPUT & PERSISTENCE                              │   │
│  │  Fraud Alerts (with explanations) → Kafka → Alert Service            │   │
│  │  All Events → Azure Synapse (for analytics)                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              AUTONOMOUS RULE GENERATION (Phase 3)                    │   │
│  │  AI Agent → Analyzes Patterns → Generates Rules → Human Approval    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Details

### 1. Liquid Neural Network (LNN) Service

**Purpose**: Process irregularly sampled transaction sequences without fixed time steps.

**Technology Stack**:
- Framework: PyTorch / TensorFlow
- Model: Liquid Time-Constant Network (LTC) or similar
- Deployment: FastAPI service (similar to current ML inference)

**API Design**:
```python
POST /lnn/predict
{
    "transaction_sequence": [
        {"timestamp": 1703577600, "amount": 45.00, ...},
        {"timestamp": 1703577660, "amount": 120.00, ...},
        {"timestamp": 1703577720, "amount": 8.50, ...}
    ],
    "current_transaction": {...}
}

Response:
{
    "fraud_probability": 0.72,
    "temporal_pattern_score": 0.85,
    "irregularity_detected": true
}
```

**Integration with Flink**:
```java
// In FraudDetectionProcessFunction
private double getLNNScore(Transaction txn, List<Transaction> sequence) {
    LNNRequest request = LNNRequest.builder()
        .transactionSequence(sequence)
        .currentTransaction(txn)
        .build();
    
    return lnnClient.predict(request).getFraudProbability();
}
```

**Performance Requirements**:
- Latency: < 50ms (P99)
- Throughput: 50K+ requests/second
- Accuracy: Maintain or improve current detection rates

---

### 2. Graph Neural Network (GNN) Service

**Purpose**: Detect fraud networks, money laundering rings, and organized crime patterns.

**Technology Stack**:
- Framework: PyTorch Geometric / DGL
- Model: Heterogeneous GNN (HAN, RGCN, or custom)
- Graph Database: Neo4j or in-memory graph (Redis Graph)

**Graph Schema**:
```
Nodes:
- Customer (id, account_age, risk_score)
- Device (device_id, device_type, fingerprint)
- Merchant (merchant_id, category, reputation)
- Account (account_id, account_type)

Edges:
- TRANSACTION (customer -> merchant, amount, timestamp)
- USES_DEVICE (customer -> device, first_seen, last_seen)
- SHARES_DEVICE (customer1 -> device <- customer2)
- TRANSFERS_TO (account1 -> account2, amount, timestamp)
```

**API Design**:
```python
POST /gnn/detect-network-fraud
{
    "transaction": {...},
    "graph_context": {
        "customer_id": "CUST-123",
        "time_window": "1hour",
        "max_hop_distance": 3
    }
}

Response:
{
    "network_fraud_probability": 0.88,
    "detected_patterns": [
        "fraud_ring",
        "shared_device_cluster",
        "circular_fund_flow"
    ],
    "suspicious_entities": ["CUST-456", "DEVICE-789"],
    "explanation": "Customer is part of a fraud ring with 5 other accounts"
}
```

**Graph Construction Pipeline**:
```java
// Graph Builder Service (runs periodically)
class TransactionGraphBuilder {
    TransactionGraph buildGraph(
        List<Transaction> transactions,
        Duration timeWindow
    ) {
        // 1. Extract entities (customers, devices, merchants)
        // 2. Build edges (transactions, relationships)
        // 3. Compute graph features
        // 4. Store in graph database
    }
}
```

**Performance Requirements**:
- Latency: < 200ms (P99) for graph inference
- Graph Construction: < 5 minutes for 1M transactions
- Accuracy: 80%+ detection rate for fraud rings

---

### 3. Neuro-Symbolic AI (NSAI) Service

**Purpose**: Combine neural pattern recognition with symbolic reasoning for explainable decisions.

**Architecture**:
```
┌─────────────────────────────────────────┐
│         Neural Component                │
│  (Pattern Recognition - XGBoost/LNN)    │
│  → Fraud Probability: 0.85              │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│      Symbolic Component                 │
│  (Rule-Based Reasoning)                 │
│  → Velocity: HIGH (0.9)                │
│  → Geo-Velocity: MEDIUM (0.6)          │
│  → Device: UNKNOWN (0.7)               │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│      Explanation Generator              │
│  → "High fraud risk (0.85) because:    │
│     1. Velocity violation (0.9)        │
│     2. Unknown device (0.7)            │
│     3. Neural pattern match (0.85)"    │
└─────────────────────────────────────────┘
```

**API Design**:
```python
POST /nsai/predict-with-explanation
{
    "transaction": {...},
    "context": {...}
}

Response:
{
    "fraud_probability": 0.85,
    "explanation": {
        "summary": "High fraud risk due to velocity violation and unknown device",
        "components": [
            {
                "type": "symbolic_rule",
                "rule": "velocity_check",
                "score": 0.9,
                "reason": "6 transactions in 60 seconds"
            },
            {
                "type": "neural_pattern",
                "pattern": "unknown_device_high_velocity",
                "score": 0.85,
                "reason": "Matches historical fraud pattern"
            }
        ],
        "regulatory_compliant": true
    }
}
```

**Implementation**:
```python
class NeuroSymbolicDetector:
    def predict(self, transaction):
        # Neural component
        neural_score = self.neural_model.predict(transaction)
        
        # Symbolic component
        symbolic_rules = self.symbolic_engine.evaluate(transaction)
        
        # Combine with weights
        combined_score = (
            0.4 * symbolic_rules.max_score() +
            0.6 * neural_score
        )
        
        # Generate explanation
        explanation = self.explanation_generator.generate(
            neural_score, symbolic_rules
        )
        
        return PredictionResult(combined_score, explanation)
```

**Performance Requirements**:
- Latency: < 80ms (P99)
- Explanation Quality: Human-validated 90%+ accuracy
- Regulatory Compliance: 100% explainability

---

### 4. Causal Inference Engine

**Purpose**: Provide counterfactual explanations and root cause analysis.

**API Design**:
```python
POST /causal/explain
{
    "transaction": {...},
    "fraud_probability": 0.85
}

Response:
{
    "causal_factors": [
        {
            "factor": "transaction_amount",
            "impact": 0.3,
            "current_value": 5000.00,
            "baseline_value": 100.00
        },
        {
            "factor": "velocity",
            "impact": 0.4,
            "current_value": "6 transactions/min",
            "baseline_value": "1 transaction/min"
        }
    ],
    "counterfactuals": [
        {
            "scenario": "If amount was $100 instead of $5000",
            "fraud_probability": 0.15,
            "change": -0.70
        },
        {
            "scenario": "If velocity was 1/min instead of 6/min",
            "fraud_probability": 0.25,
            "change": -0.60
        }
    ],
    "root_cause": "High velocity combined with large amount"
}
```

**Implementation**:
```python
class CausalInferenceEngine:
    def explain(self, transaction, fraud_probability):
        # Build causal model
        causal_model = self.build_causal_model(transaction)
        
        # Identify causal factors
        factors = self.identify_causes(causal_model)
        
        # Generate counterfactuals
        counterfactuals = self.generate_counterfactuals(
            transaction, factors
        )
        
        return Explanation(factors, counterfactuals)
```

**Performance Requirements**:
- Latency: < 500ms (P99) for explanation generation
- Counterfactual Quality: Human-validated explanations

---

### 5. Autonomous Rule Generation Agent (Phase 3)

**Purpose**: Automatically generate, test, and propose new fraud detection rules.

**Architecture**:
```
┌─────────────────────────────────────────┐
│      Pattern Analysis Agent             │
│  - Analyzes fraud patterns              │
│  - Identifies gaps in detection         │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│      Rule Generation Agent (LLM)        │
│  - Generates rule logic                 │
│  - Writes test cases                    │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│      Testing Agent                      │
│  - Tests on historical data             │
│  - Validates performance                │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│      Human Approval System              │
│  - Presents rule for review             │
│  - Requires approval before deployment  │
└─────────────────────────────────────────┘
```

**API Design**:
```python
POST /agent/generate-rule
{
    "fraud_pattern": {
        "description": "New pattern: Small test transactions followed by large withdrawal",
        "examples": [...]
    }
}

Response:
{
    "proposed_rule": {
        "name": "test_then_withdraw_pattern",
        "logic": "IF (3+ transactions < $10) AND (next transaction > $1000) THEN flag",
        "test_results": {
            "true_positive_rate": 0.85,
            "false_positive_rate": 0.02,
            "coverage": 0.12
        },
        "status": "pending_approval"
    }
}
```

**Safety Mechanisms**:
1. **Human-in-the-Loop**: All rules require human approval
2. **Testing Framework**: Rules tested on historical data before approval
3. **Rollback Capability**: Rules can be automatically rolled back if performance degrades
4. **Audit Trail**: All rule changes logged for compliance

---

## Integration Architecture

### Composite Risk Scoring

```java
public class CompositeRiskScorer {
    public RiskScoreResult calculateScore(Transaction txn, 
                                         CustomerState state) {
        // Existing components
        double ruleScore = ruleEngine.evaluate(txn, state);
        double mlScore = mlClient.predict(txn);
        
        // New components
        double lnnScore = lnnClient.predict(txn, state.getRecentTransactions());
        double gnnScore = gnnClient.detectNetworkFraud(txn, state.getGraphContext());
        double nsaiScore = nsaiClient.predictWithExplanation(txn).getScore();
        
        // Weighted combination
        double compositeScore = 
            0.20 * ruleScore +      // Rules: Fast, explainable
            0.15 * mlScore +        // ML: Pattern recognition
            0.20 * lnnScore +       // LNN: Temporal patterns
            0.25 * gnnScore +       // GNN: Network detection
            0.20 * nsaiScore;       // NSAI: Explainable AI
        
        // Generate explanations
        Explanation explanation = causalEngine.explain(txn, compositeScore);
        
        return RiskScoreResult.builder()
            .compositeScore(compositeScore)
            .componentScores(ComponentScores.builder()
                .ruleScore(ruleScore)
                .mlScore(mlScore)
                .lnnScore(lnnScore)
                .gnnScore(gnnScore)
                .nsaiScore(nsaiScore)
                .build())
            .explanation(explanation)
            .build();
    }
}
```

---

## Deployment Architecture

### Service Deployment (Kubernetes)

```yaml
# LNN Service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lnn-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: lnn-inference
        image: fraudguard/lnn-service:latest
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"

# GNN Service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gnn-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: gnn-inference
        image: fraudguard/gnn-service:latest
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
            nvidia.com/gpu: 1  # GPU for graph processing
          limits:
            memory: "8Gi"
            cpu: "4"
            nvidia.com/gpu: 1

# NSAI Service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nsai-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: nsai-inference
        image: fraudguard/nsai-service:latest
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"
```

---

## Data Flow

### Enhanced Processing Flow

```
1. Transaction arrives → Kafka
   ↓
2. Flink processes transaction
   ↓
3. Parallel calls to detection services:
   - Rule Engine (existing)
   - ML Service (existing)
   - LNN Service (NEW)
   - GNN Service (NEW)
   - NSAI Service (NEW)
   ↓
4. Composite Risk Scoring
   ↓
5. Causal Inference (if fraud detected)
   ↓
6. Generate Alert with Explanation
   ↓
7. Send to Alert Service + Persist to Synapse
```

---

## Performance Targets

| Component | Latency (P99) | Throughput | Resource Requirements |
|-----------|--------------|------------|----------------------|
| LNN Service | < 50ms | 50K+ req/s | 2GB RAM, 1 CPU per instance |
| GNN Service | < 200ms | 10K+ req/s | 4GB RAM, 2 CPU, 1 GPU per instance |
| NSAI Service | < 80ms | 50K+ req/s | 2GB RAM, 1 CPU per instance |
| Causal Engine | < 500ms | 5K+ req/s | 4GB RAM, 2 CPU per instance |
| Rule Agent | N/A (async) | 10 rules/day | 8GB RAM, 4 CPU per instance |

---

## Security & Compliance

### Data Privacy
- All PII masked in logs
- Graph data anonymized
- Explanations sanitized for external sharing

### Regulatory Compliance
- Full audit trail for all decisions
- Explainability for EU AI Act compliance
- Human oversight for autonomous agents

### Access Control
- Service-to-service authentication (mTLS)
- Role-based access for rule generation
- Approval workflows for rule deployment

---

## Monitoring & Observability

### Metrics
- Service latency (P50, P95, P99)
- Throughput (requests/second)
- Error rates
- Model performance (accuracy, FPR)

### Logging
- All fraud decisions logged with explanations
- Rule generation events logged
- Performance metrics exported to Prometheus

### Alerting
- Service health checks
- Performance degradation alerts
- High false positive rate alerts

---

## Next Steps

1. **Phase 1 Implementation** (Months 1-4):
   - Implement LNN service
   - Integrate with Flink
   - Performance testing

2. **Phase 2 Design** (Months 5-6):
   - Design GNN architecture
   - Design NSAI architecture
   - Prototype development

3. **Phase 3 Research** (Months 7-12):
   - Agent framework research
   - Safety mechanism design
   - Compliance review

---

**Document Status**: ✅ **APPROVED FOR DESIGN PHASE**  
**Next Review**: After Phase 1 POC completion
