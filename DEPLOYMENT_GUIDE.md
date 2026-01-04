# Complete Deployment Guide

## 🚀 Deploy All Components to Make Resume Claims Accurate

This guide walks you through deploying all components step-by-step.

---

## Phase 1: Train and Deploy ML Model

### Step 1: Train XGBoost Model

```bash
cd ml-models/training

# Install Python dependencies
pip3 install -r requirements.txt

# Train the model (takes 5-10 minutes)
python3 train_xgboost_model.py
```

**Expected Output:**
- Model saved to `../models/xgboost_fraud_model_*.pkl`
- Metrics saved to `../metrics/model_metadata_*.json`
- AUC score: ~0.85-0.95
- False positive rate reduction: 15-25%

### Step 2: Build ML Inference Service

```bash
cd ml-models/inference

# Copy trained model
cp ../models/xgboost_fraud_model_*.pkl models/

# Build Docker image
docker build -t ml-inference:latest .

# Test locally
docker run -p 8000:8000 \
    -v $(pwd)/models:/app/models \
    -e MODEL_FILE=$(ls ../models/*.pkl | xargs basename) \
    ml-inference:latest
```

### Step 3: Deploy to Kubernetes

```bash
# Update k8s-deployment.yaml with correct model filename
# Then deploy:
kubectl apply -f ml-models/inference/k8s-deployment.yaml

# Verify deployment
kubectl get pods -n fraud-detection | grep ml-inference
kubectl get svc -n fraud-detection | grep ml-inference
```

### Step 4: Test ML Service

```bash
# Port forward to access service
kubectl port-forward svc/ml-inference-service 8000:8000 -n fraud-detection

# Test prediction
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_id": "TXN-TEST",
    "features": {
      "transaction_amount": 2500.0,
      "transaction_count_1min": 8,
      "transaction_count_1hour": 15,
      "total_amount_1min": 20000.0,
      "total_amount_1hour": 50000.0,
      "distance_from_home_km": 1000.0,
      "time_since_last_txn_minutes": 5.0,
      "velocity_kmh": 12000.0,
      "merchant_reputation_score": 0.3,
      "customer_age_days": 30,
      "is_known_device": 0,
      "is_vpn_detected": 1,
      "device_usage_count": 1,
      "hour_of_day": 3,
      "day_of_week": 0,
      "merchant_category_risk": 2
    }
  }'
```

**Expected Response:**
```json
{
  "transaction_id": "TXN-TEST",
  "fraud_probability": 0.85,
  "is_fraud": true,
  "model_version": "1.0.0",
  "timestamp": "2026-01-04T..."
}
```

---

## Phase 2: Deploy Monitoring Stack

### Step 1: Deploy Prometheus and Grafana

```bash
# Using Helm (recommended)
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin123

# OR using Kubernetes manifests
kubectl apply -f infrastructure/k8s/prometheus-grafana-stack.yaml
```

### Step 2: Access Grafana

```bash
# Port forward Grafana
kubectl port-forward svc/grafana 3000:3000 -n monitoring

# Open browser: http://localhost:3000
# Login: admin / admin123 (or your configured password)
```

### Step 3: Import Flink Dashboards

1. Go to Grafana → Dashboards → Import
2. Use dashboard ID or upload JSON from `infrastructure/k8s/grafana-dashboards/`
3. Configure Prometheus data source if needed

### Step 4: Verify Metrics Collection

```bash
# Check Prometheus targets
kubectl port-forward svc/prometheus 9090:9090 -n monitoring
# Open: http://localhost:9090/targets

# Should see:
# - flink-metrics (UP)
# - ml-inference-metrics (UP)
```

---

## Phase 3: Load Testing

### Step 1: Run Load Test

```bash
cd scripts/load-testing

# Set target TPS (default: 50,000)
export TARGET_TPS=50000
export DURATION_SECONDS=60

# Run load test
./kafka-load-test.sh
```

### Step 2: Monitor Results

```bash
# Check Kafka throughput
docker exec fraud-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic transactions-live

# Check Flink processing rate
# Open Flink dashboard: http://localhost:8081
# View: Metrics → numRecordsInPerSecond
```

### Step 3: Document Results

Create `LOAD_TEST_RESULTS.md` with:
- Actual TPS achieved
- Latency metrics
- System resource usage
- Scaling behavior

---

## Phase 4: Deploy Databricks

### Step 1: Set Up Azure Resources

```bash
cd infrastructure/databricks

# Initialize Terraform
terraform init

# Review plan
terraform plan

# Deploy
terraform apply
```

### Step 2: Access Databricks Workspace

```bash
# Get workspace URL
az databricks workspace show \
  --resource-group rg-fraud-detection-prod \
  --name db-fraud-detection-prod \
  --query workspaceUrl -o tsv

# Login and create cluster
# Use the notebook created by Terraform
```

### Step 3: Connect to Data Lake

```bash
# In Databricks notebook, mount ADLS:
dbutils.fs.mount(
  source = "abfss://fraud-detection@stfrauddetectiondl.dfs.core.windows.net/",
  mount_point = "/mnt/fraud-detection-data",
  extra_configs = {"fs.azure.account.key.stfrauddetectiondl.dfs.core.windows.net": "YOUR_KEY"}
)
```

### Step 4: Run Batch Jobs

```python
# In Databricks notebook
# Read streaming data from Delta Lake
transactions = spark.read.format("delta").load("/mnt/fraud-detection-data/transactions")

# Batch analytics
fraud_summary = transactions.groupBy("customer_id", "date").agg(
    count("transaction_id").alias("txn_count"),
    sum("amount").alias("total_amount")
)

# Write to analytics layer
fraud_summary.write.format("delta").save("/mnt/fraud-detection-data/analytics/summary")
```

---

## Phase 5: End-to-End Testing

### Step 1: Verify ML Integration

```bash
# 1. Ensure ML service is running
kubectl get pods -n fraud-detection | grep ml-inference

# 2. Rebuild Flink job with ML integration
cd finance-intelligence-root
mvn clean package -DskipTests

# 3. Submit Flink job
docker cp intelligence-processing/target/intelligence-processing-1.0.0-SNAPSHOT.jar \
  fraud-flink-jm:/opt/flink/usrlib/
  
docker exec fraud-flink-jm flink run \
  -c com.frauddetection.processing.job.FraudDetectionJob \
  /opt/flink/usrlib/intelligence-processing-1.0.0-SNAPSHOT.jar

# 4. Generate test transactions
docker run --rm --network fraud-network \
  -v "$(pwd)/../intelligence-ingestion/target:/app" \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  eclipse-temurin:17-jre \
  java -jar /app/intelligence-ingestion-1.0.0-SNAPSHOT.jar

# 5. Check alerts - should include ML scores
docker exec fraud-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic fraud-alerts \
  --from-beginning \
  --max-messages 5
```

### Step 2: Verify Monitoring

```bash
# Check Prometheus metrics
curl http://localhost:9090/api/v1/query?query=flink_taskmanager_job_task_numRecordsInPerSecond

# Check Grafana dashboards
# Open: http://localhost:3000
# Verify Flink metrics are visible
```

### Step 3: Verify Load Test Results

```bash
# Check if 50K TPS was achieved
# Review load test output
# Document in LOAD_TEST_RESULTS.md
```

---

## Verification Checklist

- [ ] ML model trained and deployed
- [ ] ML inference service responding
- [ ] Flink job using ML scores
- [ ] Prometheus collecting metrics
- [ ] Grafana dashboards showing data
- [ ] Load test proves 50K+ TPS
- [ ] Databricks workspace created
- [ ] Batch jobs running
- [ ] End-to-end pipeline working

---

## Troubleshooting

### ML Service Not Responding
```bash
# Check service logs
kubectl logs -f deployment/ml-inference-service -n fraud-detection

# Verify model file exists
kubectl exec -it deployment/ml-inference-service -n fraud-detection -- ls -lh /app/models/
```

### Flink Not Calling ML Service
```bash
# Check Flink logs
docker logs fraud-flink-jm | grep -i "ml\|inference"

# Verify environment variable
docker exec fraud-flink-jm env | grep ML_INFERENCE
```

### Prometheus Not Scraping
```bash
# Check ServiceMonitor
kubectl get servicemonitor -n monitoring

# Check Prometheus targets
# Open: http://localhost:9090/targets
```

---

**Last Updated**: 2026-01-04
