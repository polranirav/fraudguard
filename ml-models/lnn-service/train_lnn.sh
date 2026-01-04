#!/bin/bash

# LNN Model Training Script
# Trains Liquid Neural Network for irregular time-series fraud detection

set -e

echo "=========================================="
echo "LNN Model Training for Fraud Detection"
echo "=========================================="

# Navigate to script directory
cd "$(dirname "$0")"

# Check Python version
python3 --version

# Install dependencies if needed
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

source venv/bin/activate

echo "Installing dependencies..."
pip install -q torch numpy

# Run training
echo "Starting LNN model training..."
python3 train_lnn_model.py

echo "=========================================="
echo "Training completed!"
echo "Model saved to: ../models/lnn_fraud_model_*.pt"
echo "=========================================="
