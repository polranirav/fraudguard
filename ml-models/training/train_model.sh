#!/bin/bash
# Train XGBoost Fraud Detection Model
# This script trains the model and saves it for deployment

set -e

echo "=========================================="
echo "Training XGBoost Fraud Detection Model"
echo "=========================================="

# Create directories
mkdir -p ../models
mkdir -p ../metrics

# Install dependencies if needed
if ! python3 -c "import xgboost" 2>/dev/null; then
    echo "Installing Python dependencies..."
    pip3 install -r requirements.txt
fi

# Train the model
echo ""
echo "Starting model training..."
python3 train_xgboost_model.py

echo ""
echo "=========================================="
echo "Model Training Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Copy the model file to ml-models/inference/models/"
echo "2. Build Docker image: docker build -t ml-inference:latest ml-models/inference/"
echo "3. Deploy to Kubernetes: kubectl apply -f ml-models/inference/k8s-deployment.yaml"
echo ""
