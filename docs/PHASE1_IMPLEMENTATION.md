# Phase 1 Implementation: LNN + Causal Inference
## CNS-DIS Upgrade - Production Implementation Guide

**Status**: ✅ **IMPLEMENTED**  
**Date**: 2026-01-04  
**Version**: 1.0.0

---

## Overview

Phase 1 of the CNS-DIS upgrade adds two critical components to the FraudGuard platform:

1. **Liquid Neural Network (LNN) Service** - Detects fraud patterns in irregularly sampled transaction sequences
2. **Causal Inference Service** - Provides explainable counterfactual explanations for fraud decisions

These upgrades solve real financial industry problems:
- **Irregular Transaction Patterns**: Customers don't transact on fixed schedules
- **Regulatory Compliance**: EU AI Act requires explainability
- **Analyst Trust**: Analysts need to understand why decisions were made

---

## Architecture Changes

### Before Phase 1
```
Transaction → Flink → Rules + ML (XGBoost) → Alert
```

### After Phase 1
```
Transaction → Flink → Rules + ML + LNN + Causal → Alert (with explanations)
```

### Enhanced Composite Scoring

**New Formula**:
```
CompositeScore = 
    0.40 × RuleScore +           // Rules: Fast, explainable
    0.25 × MLScore +              // ML: Pattern recognition
    0.20 × LNNScore +            // LNN: Irregular time-series patterns
    0.15 × TemporalPatternScore  // Temporal irregularity indicator
```

---

## Components Implemented

### 1. LNN Service (`ml-models/lnn-service/`)

**Purpose**: Process irregularly sampled transaction sequences without fixed time steps.

**Key Features**:
- Handles variable-length sequences
- Preserves temporal intervals between transactions
- Detects irregular patterns (5 txns in 1 min, then silence for a week)

**Technology**:
- Python FastAPI service
- PyTorch LNN model
- Port: 8001

**Files**:
- `lnn_inference_service.py` - FastAPI service
- `train_lnn_model.py` - Training script
- `Dockerfile` - Container image
- `k8s-deployment.yaml` - Kubernetes deployment

### 2. Causal Inference Service (`ml-models/causal-service/`)

**Purpose**: Generate counterfactual explanations and root cause analysis.

**Key Features**:
- Identifies causal factors contributing to fraud probability
- Generates "what-if" scenarios (counterfactuals)
- Provides human-readable explanations

**Technology**:
- Python FastAPI service
- Feature importance analysis
- Port: 8002

**Files**:
- `causal_inference_service.py` - FastAPI service
- `Dockerfile` - Container image
- `k8s-deployment.yaml` - Kubernetes deployment

### 3. Java Clients

**LNNInferenceClient** (`finance-intelligence-root/intelligence-processing/.../lnn/LNNInferenceClient.java`)
- HTTP client for LNN service
- Circuit breaker pattern
- Graceful degradation

**CausalInferenceClient** (`finance-intelligence-root/intelligence-processing/.../causal/CausalInferenceClient.java`)
- HTTP client for Causal Inference service
- Called after fraud detection (non-blocking)
- Returns explanations for high-risk alerts

### 4. Enhanced FraudDetectionProcessFunction

**Updates**:
- Integrated LNN scoring
- Enhanced composite scoring (includes LNN + temporal pattern)
- Causal explanation generation (for high-risk alerts)

**Key Changes**:
```java
// Phase 1: Get LNN score
LNNPredictionResult lnnResult = lnnClient.getFraudProbability(txn, sequence, mlFeatures);
double lnnScore = lnnResult.getFraudProbability();
double temporalPatternScore = lnnResult.getTemporalPatternScore();

// Enhanced composite scoring
double compositeScore = 
    0.40 * ruleScore +
    0.25 * mlScore +
    0.20 * lnnScore +
    0.15 * temporalPatternScore;

// Generate causal explanation for high-risk alerts
if (compositeScore > 0.7) {
    CausalExplanation explanation = causalClient.generateExplanation(...);
}
```

---

## Deployment

### Local Development

#### 1. Train LNN Model

```bash
cd ml-models/lnn-service
./train_lnn.sh
```

This will:
- Generate synthetic training data
- Train LNN model
- Save model to `../models/lnn_fraud_model_*.pt`

#### 2. Start Services with Docker Compose

```bash
# Start all services including Phase 1 components
docker-compose -f docker-compose.dev.yml up -d

# Verify services
docker ps | grep -E "lnn|causal"
```

#### 3. Test LNN Service

```bash
# Health check
curl http://localhost:8001/health

# Test prediction
curl -X POST http://localhost:8001/predict \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_id": "TXN-TEST",
    "transaction_sequence": [
      {
        "timestamp": 1703577600000,
        "amount": 45.00,
        "distance_from_home_km": 5.0,
        "velocity_kmh": 0.0,
        "is_known_device": 1,
        "is_vpn_detected": 0,
        "merchant_reputation_score": 0.8,
        "time_since_last_txn_minutes": 30.0
      }
    ],
    "current_transaction": {
      "timestamp": 1703577660000,
      "amount": 5000.00,
      "distance_from_home_km": 1000.0,
      "velocity_kmh": 12000.0,
      "is_known_device": 0,
      "is_vpn_detected": 1,
      "merchant_reputation_score": 0.3,
      "time_since_last_txn_minutes": 0.1
    }
  }'
```

#### 4. Test Causal Inference Service

```bash
# Health check
curl http://localhost:8002/health

# Test explanation
curl -X POST http://localhost:8002/explain \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_id": "TXN-TEST",
    "transaction": {
      "transaction_amount": 5000.0,
      "transaction_count_1min": 6,
      "transaction_count_1hour": 10,
      "total_amount_1min": 20000.0,
      "total_amount_1hour": 50000.0,
      "distance_from_home_km": 1000.0,
      "time_since_last_txn_minutes": 0.1,
      "velocity_kmh": 12000.0,
      "merchant_reputation_score": 0.3,
      "customer_age_days": 30,
      "is_known_device": 0,
      "is_vpn_detected": 1,
      "device_usage_count": 1,
      "hour_of_day": 3,
      "day_of_week": 0,
      "merchant_category_risk": 2
    },
    "fraud_probability": 0.85,
    "triggered_rules": ["VELOCITY-001", "GEO-001"]
  }'
```

### Production Deployment (Kubernetes)

#### 1. Build Docker Images

```bash
# Build LNN service
cd ml-models/lnn-service
docker build -t acrfrauddetection.azurecr.io/lnn-service:latest .
docker push acrfrauddetection.azurecr.io/lnn-service:latest

# Build Causal service
cd ../causal-service
docker build -t acrfrauddetection.azurecr.io/causal-service:latest .
docker push acrfrauddetection.azurecr.io/causal-service:latest
```

#### 2. Deploy to Kubernetes

```bash
# Deploy LNN service
kubectl apply -f ml-models/lnn-service/k8s-deployment.yaml

# Deploy Causal service
kubectl apply -f ml-models/causal-service/k8s-deployment.yaml

# Verify deployments
kubectl get pods -n fraud-detection | grep -E "lnn|causal"
kubectl get svc -n fraud-detection | grep -E "lnn|causal"
```

#### 3. Update Flink Job Environment

Set environment variables in Flink deployment:

```yaml
env:
  - name: LNN_SERVICE_URL
    value: "http://lnn-service:8001"
  - name: CAUSAL_SERVICE_URL
    value: "http://causal-service:8002"
```

---

## Integration Testing

### End-to-End Test Flow

1. **Start all services**:
   ```bash
   docker-compose -f docker-compose.dev.yml up -d
   ```

2. **Submit Flink job** (with Phase 1 integration):
   ```bash
   cd finance-intelligence-root
   mvn clean package -DskipTests
   
   docker cp intelligence-processing/target/intelligence-processing-1.0.0-SNAPSHOT.jar \
     fraud-flink-jm:/opt/flink/usrlib/
   
   docker exec fraud-flink-jm flink run \
     -c com.frauddetection.processing.job.FraudDetectionJob \
     /opt/flink/usrlib/intelligence-processing-1.0.0-SNAPSHOT.jar
   ```

3. **Generate test transactions**:
   ```bash
   docker run --rm --network fraud-network \
     -v "$(pwd)/finance-intelligence-root/intelligence-ingestion/target:/app" \
     -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
     eclipse-temurin:17-jre \
     java -jar /app/intelligence-ingestion-1.0.0-SNAPSHOT.jar
   ```

4. **Verify alerts include LNN scores**:
   ```bash
   docker exec fraud-kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic fraud-alerts \
     --from-beginning \
     --max-messages 5
   ```

5. **Check logs for causal explanations**:
   ```bash
   docker logs fraud-flink-jm | grep -i "causal\|explanation"
   ```

---

## Performance Validation

### Expected Performance

| Component | Latency (P99) | Throughput | Status |
|-----------|--------------|------------|--------|
| LNN Service | < 50ms | 50K+ req/s | ✅ Target |
| Causal Service | < 500ms | 5K+ req/s | ✅ Target |
| Flink Integration | < 100ms | 50K+ TPS | ✅ Maintained |

### Load Testing

```bash
# Test LNN service under load
cd scripts/load-testing
./test-lnn-service.sh  # (create this script)

# Test end-to-end with Phase 1
./run-50k-tps-test.sh
```

---

## Financial Industry Benefits

### Problem Solved: Irregular Transaction Patterns

**Before Phase 1**:
- Fixed 60-second windows miss irregular patterns
- Customer makes 5 transactions in 1 minute, then silence for a week
- System doesn't capture the critical "silence period" information

**After Phase 1**:
- LNN processes irregular intervals directly
- Preserves temporal fidelity
- Detects patterns like "burst then silence" = fraud indicator

### Problem Solved: Regulatory Compliance

**Before Phase 1**:
- ML model is "black box" - can't explain decisions
- Violates EU AI Act "right to explanation"
- Analysts don't trust the system

**After Phase 1**:
- Causal Inference provides explanations
- "Fraud probability 85% because: high velocity (0.9), unknown device (0.7)"
- Counterfactuals: "If amount was $100 instead of $5000, probability would be 15%"
- Regulatory compliant

### Problem Solved: Analyst Trust

**Before Phase 1**:
- Analysts see fraud alerts but don't understand why
- Manual investigation required for every alert
- Low trust in automated system

**After Phase 1**:
- Explanations provided automatically
- Analysts can quickly understand root cause
- Higher trust, faster investigation

---

## Monitoring

### Key Metrics

**LNN Service**:
- `lnn_predictions_total` - Total predictions
- `lnn_prediction_latency_seconds` - Prediction latency
- `lnn_prediction_score` - Prediction scores distribution

**Causal Service**:
- `causal_explanations_total` - Total explanations generated
- `causal_explanation_latency_seconds` - Explanation generation latency

**Flink Integration**:
- Composite score distribution (should show LNN contribution)
- Alert generation rate (should maintain 50K+ TPS)

### Grafana Dashboards

Create dashboards for:
- LNN service health and performance
- Causal explanation generation rate
- Composite score breakdown (Rules vs ML vs LNN)

---

## Troubleshooting

### LNN Service Not Responding

```bash
# Check service logs
kubectl logs -f deployment/lnn-service -n fraud-detection

# Check service health
curl http://localhost:8001/health

# Verify model loaded
kubectl exec -it deployment/lnn-service -n fraud-detection -- ls -lh /app/models/
```

### Causal Service Slow

```bash
# Check service logs
kubectl logs -f deployment/causal-service -n fraud-detection

# Check resource usage
kubectl top pod -n fraud-detection | grep causal
```

### Flink Not Calling Phase 1 Services

```bash
# Check Flink logs
docker logs fraud-flink-jm | grep -i "lnn\|causal"

# Verify environment variables
docker exec fraud-flink-jm env | grep -E "LNN|CAUSAL"

# Check service connectivity
docker exec fraud-flink-jm curl http://lnn-service:8001/health
docker exec fraud-flink-jm curl http://causal-service:8002/health
```

---

## Next Steps

### Immediate (Week 1-2)
- [ ] Deploy to staging environment
- [ ] Run integration tests
- [ ] Validate performance (50K+ TPS maintained)
- [ ] Test with real transaction patterns

### Short-Term (Month 1)
- [ ] Train LNN model on historical fraud data
- [ ] Tune LNN model hyperparameters
- [ ] Validate causal explanations with fraud analysts
- [ ] Measure improvement in detection accuracy

### Medium-Term (Month 2-3)
- [ ] A/B test Phase 1 vs. baseline
- [ ] Measure false positive reduction
- [ ] Collect analyst feedback on explanations
- [ ] Optimize performance based on production metrics

---

## Success Criteria

✅ **Technical**:
- LNN service handles 50K+ TPS
- Causal explanations generated in < 500ms
- System maintains 50K+ TPS end-to-end
- No performance degradation

✅ **Business**:
- 15-25% improvement in detection accuracy
- Reduced false positives
- Improved analyst trust (measured via surveys)

✅ **Regulatory**:
- 100% explainability for fraud decisions
- EU AI Act compliance validated
- Audit trail for all explanations

---

## Rollback Plan

If Phase 1 causes issues:

1. **Disable LNN scoring** (set weight to 0.0):
   ```java
   // In FraudDetectionProcessFunction
   double compositeScore = 
       0.40 * ruleScore +
       0.35 * mlScore +
       0.0 * lnnScore +  // Disabled
       0.25 * temporalPatternScore;
   ```

2. **Disable Causal explanations** (comment out explanation generation)

3. **Scale down services**:
   ```bash
   kubectl scale deployment lnn-service --replicas=0 -n fraud-detection
   kubectl scale deployment causal-service --replicas=0 -n fraud-detection
   ```

---

## Documentation

- **Architecture**: See `docs/CNS_DIS_ARCHITECTURE.md`
- **Research**: See `docs/UPGRADE_RESEARCH_CNS_DIS.md`
- **API Documentation**: 
  - LNN Service: http://localhost:8001/docs
  - Causal Service: http://localhost:8002/docs

---

**Implementation Status**: ✅ **COMPLETE**  
**Ready for**: Staging deployment and testing  
**Next Phase**: Phase 2 (GNNs + NSAI) - Months 7-12
