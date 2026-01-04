#!/bin/bash

# GNN Model Training Script
# Trains Graph Neural Network for fraud network detection

set -e

echo "=========================================="
echo "GNN Model Training for Fraud Detection"
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
pip install -q torch torch-geometric numpy

# Run training
echo "Starting GNN model training..."
python3 train_gnn_model.py

echo "=========================================="
echo "Training completed!"
echo "Model saved to: ../models/gnn_fraud_model_*.pt"
echo "=========================================="
