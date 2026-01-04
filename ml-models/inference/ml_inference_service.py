"""
ML Inference Service for Fraud Detection
========================================

FastAPI service that provides real-time XGBoost model inference for fraud detection.
This service will be called from the Flink fraud detection pipeline.

Endpoints:
- POST /predict: Get fraud probability for a transaction
- GET /health: Health check
- GET /metrics: Prometheus metrics
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import joblib
import numpy as np
import os
from datetime import datetime
import logging
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Prometheus metrics
prediction_counter = Counter(
    'fraud_predictions_total',
    'Total number of fraud predictions',
    ['model_version']
)

prediction_latency = Histogram(
    'fraud_prediction_latency_seconds',
    'Fraud prediction latency in seconds',
    ['model_version']
)

prediction_score = Histogram(
    'fraud_prediction_score',
    'Fraud prediction scores',
    ['model_version']
)

# Load model
MODEL_DIR = os.getenv('MODEL_DIR', '/app/models')
MODEL_FILE = os.getenv('MODEL_FILE', 'xgboost_fraud_model.pkl')

model = None
feature_columns = None

def load_model():
    """Load the trained XGBoost model."""
    global model, feature_columns
    
    model_path = os.path.join(MODEL_DIR, MODEL_FILE)
    
    if not os.path.exists(model_path):
        # Try to find latest model
        if os.path.exists(MODEL_DIR):
            model_files = [f for f in os.listdir(MODEL_DIR) if f.endswith('.pkl')]
            if model_files:
                model_path = os.path.join(MODEL_DIR, sorted(model_files)[-1])
                logger.info(f"Using latest model: {model_path}")
            else:
                raise FileNotFoundError(f"No model found in {MODEL_DIR}")
        else:
            raise FileNotFoundError(f"Model directory not found: {MODEL_DIR}")
    
    logger.info(f"Loading model from: {model_path}")
    model = joblib.load(model_path)
    
    # Load feature columns from metadata if available
    metadata_path = model_path.replace('.pkl', '_metadata.json')
    if os.path.exists(metadata_path):
        import json
        with open(metadata_path, 'r') as f:
            metadata = json.load(f)
            feature_columns = metadata.get('feature_columns', [])
    else:
        # Default feature columns (should match training)
        feature_columns = [
            'transaction_amount',
            'transaction_count_1min',
            'transaction_count_1hour',
            'total_amount_1min',
            'total_amount_1hour',
            'distance_from_home_km',
            'time_since_last_txn_minutes',
            'velocity_kmh',
            'merchant_reputation_score',
            'customer_age_days',
            'is_known_device',
            'is_vpn_detected',
            'device_usage_count',
            'hour_of_day',
            'day_of_week',
            'merchant_category_risk',
        ]
    
    logger.info(f"Model loaded successfully. Features: {len(feature_columns)}")

# Initialize FastAPI app
app = FastAPI(
    title="Fraud Detection ML Inference Service",
    description="Real-time XGBoost model inference for fraud detection",
    version="1.0.0"
)

# Load model on startup
@app.on_event("startup")
async def startup_event():
    try:
        load_model()
        logger.info("ML Inference Service started successfully")
    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        raise

# Request/Response models
class TransactionFeatures(BaseModel):
    """Transaction features for fraud prediction."""
    transaction_amount: float
    transaction_count_1min: int
    transaction_count_1hour: int
    total_amount_1min: float
    total_amount_1hour: float
    distance_from_home_km: float
    time_since_last_txn_minutes: float
    velocity_kmh: float
    merchant_reputation_score: float
    customer_age_days: int
    is_known_device: int
    is_vpn_detected: int
    device_usage_count: int
    hour_of_day: int
    day_of_week: int
    merchant_category_risk: int

class PredictionRequest(BaseModel):
    """Request for fraud prediction."""
    transaction_id: str
    features: TransactionFeatures

class PredictionResponse(BaseModel):
    """Response with fraud prediction."""
    transaction_id: str
    fraud_probability: float
    is_fraud: bool
    model_version: str
    timestamp: str

@app.post("/predict", response_model=PredictionResponse)
async def predict_fraud(request: PredictionRequest):
    """
    Predict fraud probability for a transaction.
    """
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    
    try:
        # Extract features in correct order
        feature_values = [
            request.features.transaction_amount,
            request.features.transaction_count_1min,
            request.features.transaction_count_1hour,
            request.features.total_amount_1min,
            request.features.total_amount_1hour,
            request.features.distance_from_home_km,
            request.features.time_since_last_txn_minutes,
            request.features.velocity_kmh,
            request.features.merchant_reputation_score,
            request.features.customer_age_days,
            request.features.is_known_device,
            request.features.is_vpn_detected,
            request.features.device_usage_count,
            request.features.hour_of_day,
            request.features.day_of_week,
            request.features.merchant_category_risk,
        ]
        
        # Convert to numpy array and reshape
        X = np.array(feature_values).reshape(1, -1)
        
        # Predict
        fraud_probability = float(model.predict_proba(X)[0, 1])
        is_fraud = fraud_probability > 0.5
        
        # Update metrics
        model_version = os.getenv('MODEL_VERSION', '1.0.0')
        prediction_counter.labels(model_version=model_version).inc()
        prediction_score.labels(model_version=model_version).observe(fraud_probability)
        
        return PredictionResponse(
            transaction_id=request.transaction_id,
            fraud_probability=fraud_probability,
            is_fraud=is_fraud,
            model_version=model_version,
            timestamp=datetime.utcnow().isoformat()
        )
    
    except Exception as e:
        logger.error(f"Prediction error: {e}")
        raise HTTPException(status_code=500, detail=f"Prediction failed: {str(e)}")

@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy" if model is not None else "unhealthy",
        "model_loaded": model is not None,
        "timestamp": datetime.utcnow().isoformat()
    }

@app.get("/metrics")
async def metrics():
    """Prometheus metrics endpoint."""
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)

@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "Fraud Detection ML Inference Service",
        "version": "1.0.0",
        "status": "running"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
