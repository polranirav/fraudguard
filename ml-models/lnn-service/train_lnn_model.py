"""
Liquid Neural Network Training Script for Fraud Detection
==========================================================

Trains an LNN model to detect fraud patterns in irregularly sampled
transaction sequences.

Key Features:
- Handles variable-length sequences
- Preserves temporal intervals between transactions
- Detects irregular patterns that fixed-window approaches miss

Financial Industry Use Case:
- Customer makes 5 transactions in 1 minute, then silence for a week
- LNN preserves the critical information in intervals between events
"""

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import joblib
import json
import os
from datetime import datetime
from typing import List, Tuple
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TransactionSequenceDataset(Dataset):
    """
    Dataset for irregular time-series transaction sequences.
    """
    
    def __init__(self, sequences: List[List[dict]], labels: List[int]):
        self.sequences = sequences
        self.labels = labels
    
    def __len__(self):
        return len(self.sequences)
    
    def __getitem__(self, idx):
        sequence = self.sequences[idx]
        label = self.labels[idx]
        
        # Extract features and time intervals
        features = []
        time_intervals = []
        
        prev_timestamp = None
        for txn in sequence:
            # Extract 8 features
            feat = [
                txn['amount'] / 1000.0,  # Normalize
                txn['distance_from_home_km'] / 100.0,
                txn['velocity_kmh'] / 1000.0,
                txn['is_known_device'],
                txn['is_vpn_detected'],
                txn['merchant_reputation_score'],
                txn['time_since_last_txn_minutes'] / 60.0,
                min(txn['amount'] * txn['velocity_kmh'] / 100000.0, 1.0)  # Interaction
            ]
            features.append(feat)
            
            # Calculate time interval
            if prev_timestamp is not None:
                time_delta = (txn['timestamp'] - prev_timestamp) / 1000.0  # Convert to seconds
                time_intervals.append(max(time_delta, 0.1))
            else:
                time_intervals.append(60.0)  # Default 1 minute
            
            prev_timestamp = txn['timestamp']
        
        return {
            'features': torch.tensor(features, dtype=torch.float32),
            'time_intervals': torch.tensor(time_intervals, dtype=torch.float32),
            'label': torch.tensor(label, dtype=torch.float32)
        }


class LiquidTimeConstantNetwork(nn.Module):
    """
    Liquid Time-Constant Network for irregular time-series.
    """
    
    def __init__(self, input_dim=8, hidden_dim=64, output_dim=1):
        super(LiquidTimeConstantNetwork, self).__init__()
        
        self.input_proj = nn.Linear(input_dim, hidden_dim)
        self.ltc_cell = nn.LSTMCell(hidden_dim, hidden_dim)
        self.output_proj = nn.Linear(hidden_dim, output_dim)
        self.sigmoid = nn.Sigmoid()
        
    def forward(self, sequences, time_intervals):
        """
        Process irregular time-series sequences.
        """
        batch_size = sequences.size(0)
        seq_len = sequences.size(1)
        
        hidden = torch.zeros(batch_size, self.ltc_cell.hidden_size).to(sequences.device)
        cell = torch.zeros(batch_size, self.ltc_cell.hidden_size).to(sequences.device)
        
        # Process each time step
        for t in range(seq_len):
            x = sequences[:, t, :]
            x = self.input_proj(x)
            
            # Apply time-dependent processing
            time_weight = torch.clamp(time_intervals[:, t] / 60.0, 0.1, 10.0)
            x = x * time_weight.unsqueeze(1)
            
            # LTC cell update
            hidden, cell = self.ltc_cell(x, (hidden, cell))
        
        # Final output
        output = self.output_proj(hidden)
        return self.sigmoid(output)


def generate_synthetic_training_data(n_samples=10000, max_sequence_length=20):
    """
    Generate synthetic transaction sequences for training.
    
    In production, this would load from historical transaction data.
    """
    sequences = []
    labels = []
    
    np.random.seed(42)
    
    for i in range(n_samples):
        # Random sequence length (1 to max_sequence_length)
        seq_len = np.random.randint(1, max_sequence_length + 1)
        
        # Determine if this is fraud (30% fraud rate)
        is_fraud = np.random.random() < 0.3
        
        sequence = []
        base_timestamp = 1700000000000  # Base timestamp in milliseconds
        
        for j in range(seq_len):
            # Generate transaction features
            if is_fraud:
                # Fraud patterns: high amounts, irregular intervals, high velocity
                amount = np.random.lognormal(mean=6.0, sigma=1.5)  # Higher amounts
                time_delta = np.random.exponential(scale=30) if j > 0 else 0  # Irregular intervals
                velocity = np.random.exponential(scale=500)  # High velocity
                is_known_device = 0 if np.random.random() < 0.7 else 1  # Mostly unknown
                is_vpn = 1 if np.random.random() < 0.6 else 0  # Often VPN
            else:
                # Normal patterns: moderate amounts, regular intervals, low velocity
                amount = np.random.lognormal(mean=4.0, sigma=1.0)
                time_delta = np.random.normal(loc=1800, scale=600) if j > 0 else 0  # Regular intervals (~30 min)
                velocity = np.random.exponential(scale=50)  # Low velocity
                is_known_device = 1 if np.random.random() < 0.8 else 0  # Mostly known
                is_vpn = 1 if np.random.random() < 0.1 else 0  # Rarely VPN
            
            timestamp = base_timestamp + int(sum([np.random.exponential(scale=1800) for _ in range(j)]))
            
            txn = {
                'timestamp': timestamp,
                'amount': amount,
                'distance_from_home_km': np.random.exponential(scale=10),
                'velocity_kmh': velocity,
                'is_known_device': is_known_device,
                'is_vpn_detected': is_vpn,
                'merchant_reputation_score': np.random.beta(2, 2),
                'time_since_last_txn_minutes': time_delta / 60.0 if j > 0 else 30.0
            }
            sequence.append(txn)
        
        sequences.append(sequence)
        labels.append(1 if is_fraud else 0)
    
    return sequences, labels


def train_lnn_model(sequences: List[List[dict]], labels: List[int],
                    epochs=50, batch_size=32, learning_rate=0.001):
    """
    Train the LNN model.
    """
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    logger.info(f"Training on device: {device}")
    
    # Create dataset
    dataset = TransactionSequenceDataset(sequences, labels)
    
    # Pad sequences to same length for batching
    # In production, use proper padding/collation
    max_len = max(len(seq) for seq in sequences)
    
    def collate_fn(batch):
        """Custom collate function to pad sequences."""
        features_list = []
        intervals_list = []
        labels_list = []
        
        for item in batch:
            seq_len = item['features'].size(0)
            # Pad to max_len
            pad_len = max_len - seq_len
            if pad_len > 0:
                features_padded = torch.cat([
                    item['features'],
                    torch.zeros(pad_len, item['features'].size(1))
                ])
                intervals_padded = torch.cat([
                    item['time_intervals'],
                    torch.zeros(pad_len)
                ])
            else:
                features_padded = item['features'][:max_len]
                intervals_padded = item['time_intervals'][:max_len]
            
            features_list.append(features_padded)
            intervals_list.append(intervals_padded)
            labels_list.append(item['label'])
        
        return {
            'features': torch.stack(features_list),
            'time_intervals': torch.stack(intervals_list),
            'labels': torch.stack(labels_list)
        }
    
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True, collate_fn=collate_fn)
    
    # Initialize model
    model = LiquidTimeConstantNetwork().to(device)
    criterion = nn.BCELoss()
    optimizer = optim.Adam(model.parameters(), lr=learning_rate)
    
    # Training loop
    model.train()
    for epoch in range(epochs):
        total_loss = 0.0
        correct = 0
        total = 0
        
        for batch in dataloader:
            features = batch['features'].to(device)
            time_intervals = batch['time_intervals'].to(device)
            labels = batch['labels'].to(device)
            
            optimizer.zero_grad()
            
            # Forward pass
            outputs = model(features, time_intervals).squeeze()
            loss = criterion(outputs, labels)
            
            # Backward pass
            loss.backward()
            optimizer.step()
            
            total_loss += loss.item()
            predicted = (outputs > 0.5).float()
            total += labels.size(0)
            correct += (predicted == labels).sum().item()
        
        accuracy = 100 * correct / total
        avg_loss = total_loss / len(dataloader)
        
        if (epoch + 1) % 10 == 0:
            logger.info(f"Epoch [{epoch+1}/{epochs}], Loss: {avg_loss:.4f}, Accuracy: {accuracy:.2f}%")
    
    return model


def main():
    """Main training function."""
    logger.info("Starting LNN model training...")
    
    # Generate synthetic training data
    # In production, load from historical transaction data
    logger.info("Generating synthetic training data...")
    sequences, labels = generate_synthetic_training_data(n_samples=10000)
    
    # Split into train/validation
    split_idx = int(0.8 * len(sequences))
    train_sequences = sequences[:split_idx]
    train_labels = labels[:split_idx]
    val_sequences = sequences[split_idx:]
    val_labels = labels[split_idx:]
    
    logger.info(f"Training samples: {len(train_sequences)}, Validation samples: {len(val_sequences)}")
    
    # Train model
    logger.info("Training LNN model...")
    model = train_lnn_model(train_sequences, train_labels, epochs=50)
    
    # Evaluate on validation set
    logger.info("Evaluating model...")
    model.eval()
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    
    val_dataset = TransactionSequenceDataset(val_sequences, val_labels)
    val_dataloader = DataLoader(val_dataset, batch_size=32, shuffle=False)
    
    correct = 0
    total = 0
    with torch.no_grad():
        for batch in val_dataloader:
            features = batch['features'].to(device)
            time_intervals = batch['time_intervals'].to(device)
            labels = batch['labels'].to(device)
            
            outputs = model(features, time_intervals).squeeze()
            predicted = (outputs > 0.5).float()
            total += labels.size(0)
            correct += (predicted == labels).sum().item()
    
    accuracy = 100 * correct / total
    logger.info(f"Validation Accuracy: {accuracy:.2f}%")
    
    # Save model
    model_dir = os.path.join(os.path.dirname(__file__), '..', 'models')
    os.makedirs(model_dir, exist_ok=True)
    
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    model_path = os.path.join(model_dir, f'lnn_fraud_model_{timestamp}.pt')
    metadata_path = os.path.join(model_dir, f'lnn_fraud_model_{timestamp}_metadata.json')
    
    torch.save(model.state_dict(), model_path)
    logger.info(f"Model saved to: {model_path}")
    
    # Save metadata
    metadata = {
        'version': '1.0.0',
        'timestamp': timestamp,
        'accuracy': accuracy,
        'training_samples': len(train_sequences),
        'validation_samples': len(val_sequences),
        'input_dim': 8,
        'hidden_dim': 64,
        'output_dim': 1
    }
    
    with open(metadata_path, 'w') as f:
        json.dump(metadata, f, indent=2)
    
    logger.info(f"Metadata saved to: {metadata_path}")
    logger.info("LNN model training completed successfully!")


if __name__ == "__main__":
    main()
