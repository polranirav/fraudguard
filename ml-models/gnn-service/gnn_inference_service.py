"""
Graph Neural Network (GNN) Inference Service for Fraud Detection
=================================================================

This service detects fraud networks, money laundering rings, and organized
crime patterns by analyzing relationships between entities (customers, devices,
merchants, accounts).

Key Advantages:
- Detects fraud rings that individual transaction analysis misses
- Identifies shared device clusters (multiple accounts using same device)
- Detects circular fund flows (money laundering patterns)
- Network-level fraud detection (not just individual transactions)

Financial Industry Use Case:
- Fraud ring: 10 accounts sharing 3 devices, making coordinated transactions
- Money laundering: Circular fund flows between accounts
- Organized crime: Coordinated attacks across multiple merchants
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch_geometric.nn import GCNConv, GATConv, global_mean_pool
from torch_geometric.data import Data, Batch
import os
from datetime import datetime
import logging
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response
import json

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Prometheus metrics
gnn_prediction_counter = Counter(
    'gnn_predictions_total',
    'Total number of GNN predictions',
    ['model_version']
)

gnn_prediction_latency = Histogram(
    'gnn_prediction_latency_seconds',
    'GNN prediction latency in seconds',
    ['model_version']
)

gnn_network_detections = Counter(
    'gnn_network_detections_total',
    'Total number of fraud networks detected',
    ['pattern_type']
)

# Model configuration
MODEL_DIR = os.getenv('MODEL_DIR', '/app/models')
MODEL_FILE = os.getenv('GNN_MODEL_FILE', 'gnn_fraud_model.pt')
DEVICE = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

# Global model
gnn_model = None
model_version = "1.0.0"


class FraudGNN(nn.Module):
    """
    Graph Neural Network for fraud network detection.
    
    Architecture:
    - Heterogeneous graph with multiple node types (Customer, Device, Merchant, Account)
    - Graph Attention Network (GAT) for learning node embeddings
    - Graph-level pooling for network-level fraud prediction
    """
    
    def __init__(self, node_feature_dim=16, hidden_dim=64, num_layers=3, num_heads=4):
        super(FraudGNN, self).__init__()
        
        # Input projection
        self.input_proj = nn.Linear(node_feature_dim, hidden_dim)
        
        # Graph Attention layers
        self.gat_layers = nn.ModuleList()
        for i in range(num_layers):
            if i == 0:
                self.gat_layers.append(GATConv(hidden_dim, hidden_dim, heads=num_heads, concat=False))
            else:
                self.gat_layers.append(GATConv(hidden_dim, hidden_dim, heads=num_heads, concat=False))
        
        # Output projection
        self.output_proj = nn.Linear(hidden_dim, 1)
        self.sigmoid = nn.Sigmoid()
        
    def forward(self, data):
        """
        Process graph data.
        
        Args:
            data: PyTorch Geometric Data object with:
                - x: Node features [num_nodes, node_feature_dim]
                - edge_index: Edge connectivity [2, num_edges]
                - batch: Batch assignment for graph-level pooling
        
        Returns:
            Fraud probability for the network
        """
        x, edge_index, batch = data.x, data.edge_index, data.batch
        
        # Project input features
        x = self.input_proj(x)
        
        # Apply GAT layers
        for gat_layer in self.gat_layers:
            x = F.relu(gat_layer(x, edge_index))
            x = F.dropout(x, p=0.2, training=self.training)
        
        # Graph-level pooling (aggregate node embeddings)
        graph_embedding = global_mean_pool(x, batch)
        
        # Predict fraud probability
        output = self.output_proj(graph_embedding)
        return self.sigmoid(output)


class GraphBuilder:
    """
    Builds graph from transaction data for GNN inference.
    
    Creates heterogeneous graph with:
    - Nodes: Customers, Devices, Merchants, Accounts
    - Edges: Transactions, Device usage, Account transfers
    """
    
    def build_graph(self, transaction, graph_context: Dict) -> Data:
        """
        Build graph from transaction and context.
        
        In production, this would query a graph database (Neo4j/Redis Graph)
        to get the subgraph around the transaction.
        
        For now, we build a simplified graph from the transaction data.
        """
        customer_id = transaction.get('customer_id', 'unknown')
        device_id = transaction.get('device_id', 'unknown')
        merchant_id = transaction.get('merchant_id', 'unknown')
        account_id = transaction.get('account_id', 'unknown')
        
        # Extract entities
        entities = {
            'customer': customer_id,
            'device': device_id,
            'merchant': merchant_id,
            'account': account_id
        }
        
        # Build node features
        # In production, these would come from graph database
        node_features = []
        node_mapping = {}
        
        # Customer node
        node_mapping['customer'] = 0
        customer_features = self._get_customer_features(transaction)
        node_features.append(customer_features)
        
        # Device node
        node_mapping['device'] = 1
        device_features = self._get_device_features(transaction)
        node_features.append(device_features)
        
        # Merchant node
        node_mapping['merchant'] = 2
        merchant_features = self._get_merchant_features(transaction)
        node_features.append(merchant_features)
        
        # Account node
        node_mapping['account'] = 3
        account_features = self._get_account_features(transaction)
        node_features.append(account_features)
        
        # Build edges
        # TRANSACTION: customer -> merchant
        # USES_DEVICE: customer -> device
        # MERCHANT_ACCOUNT: merchant -> account
        edges = [
            [0, 2],  # customer -> merchant (transaction)
            [0, 1],  # customer -> device (uses device)
            [2, 3],  # merchant -> account (merchant account)
        ]
        
        # Convert to PyTorch Geometric format
        node_features_tensor = torch.tensor(node_features, dtype=torch.float32)
        edge_index = torch.tensor(edges, dtype=torch.long).t().contiguous()
        batch = torch.zeros(len(node_features), dtype=torch.long)
        
        return Data(x=node_features_tensor, edge_index=edge_index, batch=batch)
    
    def _get_customer_features(self, transaction):
        """Extract customer node features."""
        return [
            transaction.get('amount', 0.0) / 1000.0,  # Normalized amount
            transaction.get('customer_age_days', 365) / 365.0,  # Normalized age
            transaction.get('customer_risk_score', 0.5),  # Risk score
            transaction.get('transaction_count_1hour', 1) / 10.0,  # Normalized count
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0  # Padding to 16 dims
        ][:16]
    
    def _get_device_features(self, transaction):
        """Extract device node features."""
        return [
            transaction.get('is_known_device', 0),
            transaction.get('is_vpn_detected', 0),
            transaction.get('device_usage_count', 1) / 100.0,
            transaction.get('device_age_days', 30) / 365.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        ][:16]
    
    def _get_merchant_features(self, transaction):
        """Extract merchant node features."""
        return [
            transaction.get('merchant_reputation_score', 0.7),
            transaction.get('merchant_category_risk', 0) / 2.0,
            transaction.get('merchant_transaction_count', 1) / 1000.0,
            transaction.get('merchant_fraud_rate', 0.0),
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        ][:16]
    
    def _get_account_features(self, transaction):
        """Extract account node features."""
        return [
            transaction.get('account_balance', 0.0) / 10000.0,
            transaction.get('account_age_days', 365) / 365.0,
            transaction.get('account_transaction_count', 1) / 100.0,
            transaction.get('account_risk_score', 0.5),
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        ][:16]


def load_model():
    """Load the trained GNN model."""
    global gnn_model, model_version
    
    model_path = os.path.join(MODEL_DIR, MODEL_FILE)
    
    if not os.path.exists(model_path):
        logger.warning(f"No GNN model found, initializing default model")
        gnn_model = FraudGNN()
        gnn_model.eval()
        return
    
    logger.info(f"Loading GNN model from: {model_path}")
    
    try:
        gnn_model = FraudGNN()
        gnn_model.load_state_dict(torch.load(model_path, map_location=DEVICE))
        gnn_model.to(DEVICE)
        gnn_model.eval()
        
        # Load metadata if available
        metadata_path = model_path.replace('.pt', '_metadata.json')
        if os.path.exists(metadata_path):
            with open(metadata_path, 'r') as f:
                metadata = json.load(f)
                model_version = metadata.get('version', '1.0.0')
        
        logger.info(f"GNN model loaded successfully on {DEVICE}")
    except Exception as e:
        logger.error(f"Failed to load GNN model: {e}")
        gnn_model = FraudGNN()
        gnn_model.eval()


# Initialize FastAPI app
app = FastAPI(
    title="GNN Fraud Detection Service",
    description="Graph Neural Network for fraud network and money laundering detection",
    version="1.0.0"
)

# Initialize graph builder
graph_builder = GraphBuilder()

# Load model on startup
@app.on_event("startup")
async def startup_event():
    try:
        load_model()
        logger.info("GNN Inference Service started successfully")
    except Exception as e:
        logger.error(f"Failed to load GNN model: {e}")
        load_model()


# Request/Response models
class GraphContext(BaseModel):
    """Graph context for network detection."""
    customer_id: str
    time_window: str = "1hour"  # Time window for subgraph extraction
    max_hop_distance: int = 3  # Maximum hop distance for subgraph


class GNNPredictionRequest(BaseModel):
    """Request for GNN fraud network detection."""
    transaction_id: str
    transaction: Dict  # Transaction data
    graph_context: GraphContext


class GNNPredictionResponse(BaseModel):
    """Response with GNN fraud network detection."""
    transaction_id: str
    network_fraud_probability: float
    detected_patterns: List[str]
    suspicious_entities: List[str]
    explanation: str
    model_version: str
    timestamp: str


def detect_patterns(network_prob: float, transaction: Dict) -> List[str]:
    """Detect fraud patterns based on network probability and transaction features."""
    patterns = []
    
    if network_prob > 0.7:
        patterns.append("fraud_ring")
    
    # Check for shared device patterns
    if transaction.get('device_usage_count', 1) > 5:
        patterns.append("shared_device_cluster")
    
    # Check for circular fund flows (simplified)
    if transaction.get('account_transfer_count', 0) > 3:
        patterns.append("circular_fund_flow")
    
    # Check for coordinated attacks
    if transaction.get('merchant_transaction_count', 0) > 10:
        patterns.append("coordinated_merchant_attack")
    
    return patterns if patterns else ["individual_transaction"]


@app.post("/detect-network-fraud", response_model=GNNPredictionResponse)
async def detect_network_fraud(request: GNNPredictionRequest):
    """
    Detect fraud networks and money laundering patterns using GNN.
    
    This endpoint analyzes the graph structure around a transaction to detect:
    - Fraud rings (multiple accounts coordinating)
    - Shared device clusters
    - Circular fund flows (money laundering)
    - Coordinated merchant attacks
    """
    if gnn_model is None:
        raise HTTPException(status_code=503, detail="GNN model not loaded")
    
    try:
        with torch.no_grad():
            # Build graph from transaction
            graph_data = graph_builder.build_graph(
                request.transaction,
                request.graph_context.dict()
            )
            
            # Move to device
            graph_data = graph_data.to(DEVICE)
            
            # Predict network fraud probability
            network_prob = gnn_model(graph_data)
            network_prob = network_prob.item()
            
            # Detect patterns
            patterns = detect_patterns(network_prob, request.transaction)
            
            # Identify suspicious entities
            suspicious_entities = []
            if network_prob > 0.6:
                suspicious_entities.append(request.graph_context.customer_id)
                if request.transaction.get('device_usage_count', 0) > 3:
                    suspicious_entities.append(f"DEVICE-{request.transaction.get('device_id', 'unknown')}")
            
            # Generate explanation
            if network_prob > 0.7:
                explanation = f"High network fraud risk ({network_prob:.2%}) - Detected patterns: {', '.join(patterns)}"
            elif network_prob > 0.5:
                explanation = f"Moderate network fraud risk ({network_prob:.2%}) - Potential fraud network detected"
            else:
                explanation = f"Low network fraud risk ({network_prob:.2%}) - No significant network patterns detected"
            
            # Update metrics
            gnn_prediction_counter.labels(model_version=model_version).inc()
            for pattern in patterns:
                if pattern != "individual_transaction":
                    gnn_network_detections.labels(pattern_type=pattern).inc()
            
            return GNNPredictionResponse(
                transaction_id=request.transaction_id,
                network_fraud_probability=network_prob,
                detected_patterns=patterns,
                suspicious_entities=suspicious_entities,
                explanation=explanation,
                model_version=model_version,
                timestamp=datetime.utcnow().isoformat()
            )
    
    except Exception as e:
        logger.error(f"GNN prediction error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"GNN prediction failed: {str(e)}")


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy" if gnn_model is not None else "unhealthy",
        "model_loaded": gnn_model is not None,
        "device": str(DEVICE),
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
        "service": "GNN Fraud Detection Service",
        "version": "1.0.0",
        "status": "running",
        "device": str(DEVICE)
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8003)
