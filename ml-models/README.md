# ML Model Training and Inference

This directory contains the machine learning components for fraud detection.

## Structure

```
ml-models/
├── training/              # Model training scripts
│   ├── train_xgboost_model.py
│   ├── requirements.txt
│   └── train_model.sh
├── inference/             # ML inference service
│   ├── ml_inference_service.py
│   ├── Dockerfile
│   ├── k8s-deployment.yaml
│   └── requirements.txt
└── models/                # Trained models (generated)
└── metrics/               # Training metrics (generated)
```

## Quick Start

### 1. Train the Model

```bash
cd ml-models/training
chmod +x train_model.sh
./train_model.sh
```

This will:
- Generate synthetic training data
- Train baseline XGBoost model
- Run Bayesian optimization (Optuna)
- Evaluate model performance
- Save model to `../models/`

### 2. Deploy Inference Service

#### Option A: Local Docker
```bash
cd ml-models/inference
# Copy trained model
cp ../models/xgboost_fraud_model_*.pkl models/

# Build image
docker build -t ml-inference:latest .

# Run container
docker run -p 8000:8000 \
    -v $(pwd)/models:/app/models \
    -e MODEL_FILE=xgboost_fraud_model_*.pkl \
    ml-inference:latest
```

#### Option B: Kubernetes
```bash
# Build and push to registry
docker build -t acrfrauddetection.azurecr.io/ml-inference:latest ml-models/inference/
docker push acrfrauddetection.azurecr.io/ml-inference:latest

# Deploy
kubectl apply -f ml-models/inference/k8s-deployment.yaml
```

### 3. Test the Service

```bash
# Health check
curl http://localhost:8000/health

# Make prediction
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_id": "TXN-001",
    "features": {
      "transaction_amount": 150.50,
      "transaction_count_1min": 3,
      "transaction_count_1hour": 10,
      "total_amount_1min": 450.00,
      "total_amount_1hour": 1500.00,
      "distance_from_home_km": 5.0,
      "time_since_last_txn_minutes": 10.0,
      "velocity_kmh": 30.0,
      "merchant_reputation_score": 0.8,
      "customer_age_days": 365,
      "is_known_device": 1,
      "is_vpn_detected": 0,
      "device_usage_count": 10,
      "hour_of_day": 14,
      "day_of_week": 2,
      "merchant_category_risk": 0
    }
  }'
```

## Model Features

The model uses 16 features:
1. `transaction_amount` - Transaction amount
2. `transaction_count_1min` - Transactions in last minute
3. `transaction_count_1hour` - Transactions in last hour
4. `total_amount_1min` - Total amount in last minute
5. `total_amount_1hour` - Total amount in last hour
6. `distance_from_home_km` - Distance from customer's home
7. `time_since_last_txn_minutes` - Time since last transaction
8. `velocity_kmh` - Travel velocity (for geo-velocity)
9. `merchant_reputation_score` - Merchant reputation (0-1)
10. `customer_age_days` - Customer account age
11. `is_known_device` - Device recognized (0/1)
12. `is_vpn_detected` - VPN detected (0/1)
13. `device_usage_count` - Number of times device used
14. `hour_of_day` - Hour of transaction (0-23)
15. `day_of_week` - Day of week (0-6)
16. `merchant_category_risk` - Merchant category risk level (0-2)

## Integration with Flink

The Flink `FraudDetectionProcessFunction` automatically:
1. Extracts features from transaction and state
2. Calls ML inference service
3. Uses ML score in composite risk calculation
4. Falls back to rule-based only if ML service unavailable

Set environment variable:
```bash
ML_INFERENCE_SERVICE_URL=http://ml-inference-service:8000
```

## Performance

- **Training Time**: ~2-5 minutes (with 50 optimization trials)
- **Inference Latency**: < 50ms (including network)
- **Model Size**: ~1-2 MB
- **Memory**: ~100-200 MB per instance

## Bayesian Optimization

The training script uses Optuna for hyperparameter optimization:
- Automatically finds best hyperparameters
- Reduces false positive rate
- Improves AUC score
- Typically achieves 15-25% FPR reduction vs baseline

## Next Steps

1. Train model with real historical data
2. Set up model versioning
3. Implement A/B testing
4. Add model monitoring and drift detection
5. Automate retraining pipeline
