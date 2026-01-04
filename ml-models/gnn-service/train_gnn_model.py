"""
Graph Neural Network Training Script for Fraud Detection
========================================================

Trains a GNN model to detect fraud networks, money laundering rings,
and organized crime patterns by analyzing graph structures.

Key Features:
- Heterogeneous graph with multiple node types (Customer, Device, Merchant, Account)
- Graph Attention Network (GAT) for learning node embeddings
- Graph-level pooling for network-level fraud prediction
"""

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch_geometric.nn import GCNConv, GATConv, global_mean_pool
from torch_geometric.data import Data, Batch
from torch.utils.data import Dataset, DataLoader
import os
from datetime import datetime
import logging
import json

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class FraudGNN(nn.Module):
    """GNN model for fraud network detection."""
    
    def __init__(self, node_feature_dim=16, hidden_dim=64, num_layers=3, num_heads=4):
        super(FraudGNN, self).__init__()
        
        self.input_proj = nn.Linear(node_feature_dim, hidden_dim)
        
        self.gat_layers = nn.ModuleList()
        for i in range(num_layers):
            if i == 0:
                self.gat_layers.append(GATConv(hidden_dim, hidden_dim, heads=num_heads, concat=False))
            else:
                self.gat_layers.append(GATConv(hidden_dim, hidden_dim, heads=num_heads, concat=False))
        
        self.output_proj = nn.Linear(hidden_dim, 1)
        self.sigmoid = nn.Sigmoid()
        
    def forward(self, data):
        x, edge_index, batch = data.x, data.edge_index, data.batch
        
        x = self.input_proj(x)
        
        for gat_layer in self.gat_layers:
            x = F.relu(gat_layer(x, edge_index))
            x = F.dropout(x, p=0.2, training=self.training)
        
        graph_embedding = global_mean_pool(x, batch)
        output = self.output_proj(graph_embedding)
        return self.sigmoid(output)


class GraphDataset(Dataset):
    """Dataset for graph data."""
    
    def __init__(self, graphs, labels):
        self.graphs = graphs
        self.labels = labels
    
    def __len__(self):
        return len(self.graphs)
    
    def __getitem__(self, idx):
        return self.graphs[idx], self.labels[idx]


def generate_synthetic_graph_data(n_samples=5000):
    """Generate synthetic graph data for training."""
    graphs = []
    labels = []
    
    np.random.seed(42)
    
    for i in range(n_samples):
        # Determine if this is fraud network (30% fraud rate)
        is_fraud = np.random.random() < 0.3
        
        # Build graph with 4 nodes (customer, device, merchant, account)
        node_features = []
        
        # Customer node
        if is_fraud:
            customer_features = [
                np.random.lognormal(mean=6.0, sigma=1.5) / 1000.0,  # High amount
                0.5,  # Medium age
                0.8,  # High risk
                np.random.randint(5, 15) / 10.0,  # High transaction count
            ] + [0.0] * 12
        else:
            customer_features = [
                np.random.lognormal(mean=4.0, sigma=1.0) / 1000.0,
                1.0,  # Normal age
                0.3,  # Low risk
                np.random.randint(1, 5) / 10.0,
            ] + [0.0] * 12
        
        # Device node
        if is_fraud:
            device_features = [
                0,  # Unknown device
                1,  # VPN detected
                np.random.randint(5, 20) / 100.0,  # High usage count
                0.1,  # New device
            ] + [0.0] * 12
        else:
            device_features = [
                1,  # Known device
                0,  # No VPN
                np.random.randint(1, 5) / 100.0,
                0.5,  # Established device
            ] + [0.0] * 12
        
        # Merchant node
        if is_fraud:
            merchant_features = [
                0.3,  # Low reputation
                1.0,  # High risk category
                np.random.randint(10, 50) / 1000.0,  # High transaction count
                0.1,  # High fraud rate
            ] + [0.0] * 12
        else:
            merchant_features = [
                0.8,  # High reputation
                0.0,  # Low risk category
                np.random.randint(1, 10) / 1000.0,
                0.01,  # Low fraud rate
            ] + [0.0] * 12
        
        # Account node
        if is_fraud:
            account_features = [
                np.random.lognormal(mean=7.0, sigma=1.0) / 10000.0,  # High balance
                0.3,  # New account
                np.random.randint(10, 30) / 100.0,  # High transaction count
                0.7,  # High risk
            ] + [0.0] * 12
        else:
            account_features = [
                np.random.lognormal(mean=5.0, sigma=0.5) / 10000.0,
                1.0,  # Established account
                np.random.randint(1, 10) / 100.0,
                0.3,  # Low risk
            ] + [0.0] * 12
        
        node_features = [customer_features, device_features, merchant_features, account_features]
        
        # Build edges: customer->merchant, customer->device, merchant->account
        edges = [[0, 2], [0, 1], [2, 3]]
        
        # Create PyTorch Geometric Data object
        x = torch.tensor(node_features, dtype=torch.float32)
        edge_index = torch.tensor(edges, dtype=torch.long).t().contiguous()
        batch = torch.zeros(len(node_features), dtype=torch.long)
        
        graph = Data(x=x, edge_index=edge_index, batch=batch)
        graphs.append(graph)
        labels.append(1 if is_fraud else 0)
    
    return graphs, labels


def train_gnn_model(graphs, labels, epochs=50, batch_size=32, learning_rate=0.001):
    """Train the GNN model."""
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    logger.info(f"Training on device: {device}")
    
    # Create dataset
    dataset = GraphDataset(graphs, labels)
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)
    
    # Initialize model
    model = FraudGNN().to(device)
    criterion = nn.BCELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    
    # Training loop
    model.train()
    for epoch in range(epochs):
        total_loss = 0.0
        correct = 0
        total = 0
        
        for batch_graphs, batch_labels in dataloader:
            batch_graphs = batch_graphs.to(device)
            batch_labels = batch_labels.to(device).float()
            
            optimizer.zero_grad()
            
            outputs = model(batch_graphs).squeeze()
            loss = criterion(outputs, batch_labels)
            
            loss.backward()
            optimizer.step()
            
            total_loss += loss.item()
            predicted = (outputs > 0.5).float()
            total += batch_labels.size(0)
            correct += (predicted == batch_labels).sum().item()
        
        accuracy = 100 * correct / total
        avg_loss = total_loss / len(dataloader)
        
        if (epoch + 1) % 10 == 0:
            logger.info(f"Epoch [{epoch+1}/{epochs}], Loss: {avg_loss:.4f}, Accuracy: {accuracy:.2f}%")
    
    return model


def main():
    """Main training function."""
    logger.info("Starting GNN model training...")
    
    # Generate synthetic training data
    logger.info("Generating synthetic graph data...")
    graphs, labels = generate_synthetic_graph_data(n_samples=5000)
    
    # Split into train/validation
    split_idx = int(0.8 * len(graphs))
    train_graphs = graphs[:split_idx]
    train_labels = labels[:split_idx]
    val_graphs = graphs[split_idx:]
    val_labels = labels[split_idx:]
    
    logger.info(f"Training samples: {len(train_graphs)}, Validation samples: {len(val_graphs)}")
    
    # Train model
    logger.info("Training GNN model...")
    model = train_gnn_model(train_graphs, train_labels, epochs=50)
    
    # Evaluate on validation set
    logger.info("Evaluating model...")
    model.eval()
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    
    val_dataset = GraphDataset(val_graphs, val_labels)
    val_dataloader = DataLoader(val_dataset, batch_size=32, shuffle=False)
    
    correct = 0
    total = 0
    with torch.no_grad():
        for batch_graphs, batch_labels in val_dataloader:
            batch_graphs = batch_graphs.to(device)
            batch_labels = batch_labels.to(device).float()
            
            outputs = model(batch_graphs).squeeze()
            predicted = (outputs > 0.5).float()
            total += batch_labels.size(0)
            correct += (predicted == batch_labels).sum().item()
    
    accuracy = 100 * correct / total
    logger.info(f"Validation Accuracy: {accuracy:.2f}%")
    
    # Save model
    model_dir = os.path.join(os.path.dirname(__file__), '..', 'models')
    os.makedirs(model_dir, exist_ok=True)
    
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    model_path = os.path.join(model_dir, f'gnn_fraud_model_{timestamp}.pt')
    metadata_path = os.path.join(model_dir, f'gnn_fraud_model_{timestamp}_metadata.json')
    
    torch.save(model.state_dict(), model_path)
    logger.info(f"Model saved to: {model_path}")
    
    # Save metadata
    metadata = {
        'version': '1.0.0',
        'timestamp': timestamp,
        'accuracy': accuracy,
        'training_samples': len(train_graphs),
        'validation_samples': len(val_graphs),
        'node_feature_dim': 16,
        'hidden_dim': 64,
        'num_layers': 3,
        'num_heads': 4
    }
    
    with open(metadata_path, 'w') as f:
        json.dump(metadata, f, indent=2)
    
    logger.info(f"Metadata saved to: {metadata_path}")
    logger.info("GNN model training completed successfully!")


if __name__ == "__main__":
    main()
