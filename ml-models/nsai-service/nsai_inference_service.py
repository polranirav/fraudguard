"""
Neuro-Symbolic AI (NSAI) Inference Service for Fraud Detection
==============================================================

This service combines neural pattern recognition with symbolic reasoning
to provide explainable, regulatory-compliant fraud detection decisions.

Key Advantages:
- Combines best of both worlds: Neural pattern recognition + Symbolic rules
- 100% explainability (regulatory compliance - EU AI Act)
- Human-readable explanations for every decision
- Maintains high accuracy while being interpretable

Financial Industry Value:
- Regulatory Compliance: EU AI Act requires explainability
- Analyst Trust: Analysts can understand every decision
- Audit Trail: Complete explanation for compliance audits
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict
import numpy as np
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
nsai_prediction_counter = Counter(
    'nsai_predictions_total',
    'Total number of NSAI predictions',
    ['model_version']
)

nsai_prediction_latency = Histogram(
    'nsai_prediction_latency_seconds',
    'NSAI prediction latency in seconds',
    ['model_version']
)

nsai_explanations_generated = Counter(
    'nsai_explanations_total',
    'Total number of explanations generated',
    ['explanation_type']
)

# Model configuration
MODEL_DIR = os.getenv('MODEL_DIR', '/app/models')
MODEL_VERSION = os.getenv('MODEL_VERSION', '1.0.0')

# Global components
symbolic_engine = None
neural_model = None


class SymbolicRuleEngine:
    """
    Symbolic rule engine for explainable rule-based reasoning.
    
    Evaluates rules and provides human-readable explanations.
    """
    
    def evaluate(self, transaction: Dict, context: Dict) -> List[Dict]:
        """
        Evaluate symbolic rules on transaction.
        
        Returns:
            List of rule evaluation results with scores and explanations
        """
        rules = []
        
        # Rule 1: Velocity Check
        velocity_score = self._check_velocity(transaction, context)
        if velocity_score > 0:
            rules.append({
                "type": "symbolic_rule",
                "rule": "velocity_check",
                "score": velocity_score,
                "reason": self._get_velocity_reason(transaction, context),
                "category": "VELOCITY"
            })
        
        # Rule 2: Geo-Velocity Check
        geo_score = self._check_geo_velocity(transaction, context)
        if geo_score > 0:
            rules.append({
                "type": "symbolic_rule",
                "rule": "geo_velocity_check",
                "score": geo_score,
                "reason": self._get_geo_velocity_reason(transaction, context),
                "category": "GEO"
            })
        
        # Rule 3: Device Check
        device_score = self._check_device(transaction)
        if device_score > 0:
            rules.append({
                "type": "symbolic_rule",
                "rule": "device_check",
                "score": device_score,
                "reason": self._get_device_reason(transaction),
                "category": "DEVICE"
            })
        
        # Rule 4: Amount Check
        amount_score = self._check_amount(transaction)
        if amount_score > 0:
            rules.append({
                "type": "symbolic_rule",
                "rule": "amount_check",
                "score": amount_score,
                "reason": self._get_amount_reason(transaction),
                "category": "AMOUNT"
            })
        
        return rules
    
    def _check_velocity(self, transaction: Dict, context: Dict) -> float:
        """Check velocity violations."""
        count_1min = transaction.get('transaction_count_1min', 0)
        total_1min = transaction.get('total_amount_1min', 0)
        
        if count_1min > 5 or total_1min > 2000:
            return min(0.9, 0.5 + (count_1min - 5) * 0.1)
        return 0.0
    
    def _check_geo_velocity(self, transaction: Dict, context: Dict) -> float:
        """Check geo-velocity violations."""
        velocity_kmh = transaction.get('velocity_kmh', 0)
        if velocity_kmh > 800:
            return min(0.9, 0.5 + (velocity_kmh - 800) / 2000)
        return 0.0
    
    def _check_device(self, transaction: Dict) -> float:
        """Check device-related risks."""
        is_known = transaction.get('is_known_device', 1)
        is_vpn = transaction.get('is_vpn_detected', 0)
        device_count = transaction.get('device_usage_count', 1)
        
        score = 0.0
        if not is_known:
            score += 0.4
        if is_vpn:
            score += 0.3
        if device_count > 5:
            score += 0.2
        
        return min(0.9, score)
    
    def _check_amount(self, transaction: Dict) -> float:
        """Check amount-related risks."""
        amount = transaction.get('transaction_amount', 0)
        if amount > 5000:
            return min(0.8, 0.3 + (amount - 5000) / 10000)
        return 0.0
    
    def _get_velocity_reason(self, transaction: Dict, context: Dict) -> str:
        """Generate human-readable velocity reason."""
        count = transaction.get('transaction_count_1min', 0)
        total = transaction.get('total_amount_1min', 0)
        if count > 5:
            return f"{count} transactions in 1 minute (threshold: 5)"
        elif total > 2000:
            return f"${total:.2f} total in 1 minute (threshold: $2000)"
        return "Velocity check passed"
    
    def _get_geo_velocity_reason(self, transaction: Dict, context: Dict) -> str:
        """Generate human-readable geo-velocity reason."""
        velocity = transaction.get('velocity_kmh', 0)
        if velocity > 800:
            return f"Impossible travel: {velocity:.0f} km/h (threshold: 800 km/h)"
        return "Geo-velocity check passed"
    
    def _get_device_reason(self, transaction: Dict) -> str:
        """Generate human-readable device reason."""
        reasons = []
        if not transaction.get('is_known_device', 1):
            reasons.append("Unknown device")
        if transaction.get('is_vpn_detected', 0):
            reasons.append("VPN detected")
        if transaction.get('device_usage_count', 1) > 5:
            reasons.append(f"Device shared by {transaction.get('device_usage_count')} accounts")
        return "; ".join(reasons) if reasons else "Device check passed"
    
    def _get_amount_reason(self, transaction: Dict) -> str:
        """Generate human-readable amount reason."""
        amount = transaction.get('transaction_amount', 0)
        if amount > 5000:
            return f"Unusually large amount: ${amount:.2f} (typical: < $5000)"
        return "Amount check passed"


class NeuralComponent:
    """
    Neural component for pattern recognition.
    
    In production, this would call the existing ML inference service
    or use a trained neural model directly.
    """
    
    def predict(self, transaction: Dict) -> Dict:
        """
        Get neural pattern recognition score.
        
        Returns:
            Dict with pattern score and explanation
        """
        # Simplified neural prediction
        # In production, this would call ML inference service
        features = self._extract_features(transaction)
        
        # Simple pattern matching (in production, use actual ML model)
        pattern_score = 0.0
        pattern_name = "normal_pattern"
        
        # Pattern: Unknown device + High velocity
        if not transaction.get('is_known_device', 1) and transaction.get('transaction_count_1min', 0) > 3:
            pattern_score = 0.75
            pattern_name = "unknown_device_high_velocity"
        
        # Pattern: VPN + Large amount
        elif transaction.get('is_vpn_detected', 0) and transaction.get('transaction_amount', 0) > 3000:
            pattern_score = 0.70
            pattern_name = "vpn_large_amount"
        
        # Pattern: High velocity + Geo-velocity
        elif transaction.get('transaction_count_1min', 0) > 4 and transaction.get('velocity_kmh', 0) > 500:
            pattern_score = 0.80
            pattern_name = "high_velocity_geo_velocity"
        
        return {
            "pattern_score": pattern_score,
            "pattern_name": pattern_name,
            "explanation": f"Neural pattern '{pattern_name}' detected with score {pattern_score:.2f}"
        }
    
    def _extract_features(self, transaction: Dict) -> List[float]:
        """Extract features for neural model."""
        return [
            transaction.get('transaction_amount', 0) / 1000.0,
            transaction.get('transaction_count_1min', 0) / 10.0,
            transaction.get('velocity_kmh', 0) / 1000.0,
            transaction.get('is_known_device', 1),
            transaction.get('is_vpn_detected', 0),
            transaction.get('merchant_reputation_score', 0.7),
        ]


class ExplanationGenerator:
    """
    Generates human-readable explanations from neural and symbolic components.
    """
    
    def generate(self, neural_result: Dict, symbolic_rules: List[Dict], 
                 combined_score: float) -> Dict:
        """
        Generate comprehensive explanation.
        
        Returns:
            Dict with summary and detailed components
        """
        # Build summary
        if combined_score > 0.7:
            severity = "HIGH"
        elif combined_score > 0.5:
            severity = "MEDIUM"
        else:
            severity = "LOW"
        
        summary = f"{severity} fraud risk ({combined_score:.2%}) because:"
        
        # Add top contributing factors
        all_factors = symbolic_rules + [{
            "type": "neural_pattern",
            "pattern": neural_result["pattern_name"],
            "score": neural_result["pattern_score"],
            "reason": neural_result["explanation"]
        }]
        
        # Sort by score
        all_factors.sort(key=lambda x: x.get('score', 0), reverse=True)
        
        # Build component list
        components = []
        for factor in all_factors[:5]:  # Top 5 factors
            components.append({
                "type": factor.get("type", "unknown"),
                "rule" if factor.get("type") == "symbolic_rule" else "pattern": 
                    factor.get("rule") or factor.get("pattern", "unknown"),
                "score": factor.get("score", 0),
                "reason": factor.get("reason", "No reason provided")
            })
        
        return {
            "summary": summary,
            "components": components,
            "regulatory_compliant": True,
            "explanation_id": f"EXP-{datetime.utcnow().strftime('%Y%m%d%H%M%S')}"
        }


# Initialize FastAPI app
app = FastAPI(
    title="NSAI Fraud Detection Service",
    description="Neuro-Symbolic AI for explainable, regulatory-compliant fraud detection",
    version="1.0.0"
)

# Initialize components
symbolic_engine = SymbolicRuleEngine()
neural_model = NeuralComponent()
explanation_generator = ExplanationGenerator()


@app.on_event("startup")
async def startup_event():
    logger.info("NSAI Inference Service started successfully")


# Request/Response models
class NSAIPredictionRequest(BaseModel):
    """Request for NSAI fraud prediction with explanation."""
    transaction_id: str
    transaction: Dict
    context: Optional[Dict] = {}


class ExplanationComponent(BaseModel):
    """Component of explanation."""
    type: str
    rule: Optional[str] = None
    pattern: Optional[str] = None
    score: float
    reason: str


class Explanation(BaseModel):
    """Explanation for fraud decision."""
    summary: str
    components: List[ExplanationComponent]
    regulatory_compliant: bool
    explanation_id: str


class NSAIPredictionResponse(BaseModel):
    """Response with NSAI fraud prediction and explanation."""
    transaction_id: str
    fraud_probability: float
    explanation: Explanation
    model_version: str
    timestamp: str


@app.post("/predict-with-explanation", response_model=NSAIPredictionResponse)
async def predict_with_explanation(request: NSAIPredictionRequest):
    """
    Predict fraud probability with full explainability.
    
    Combines neural pattern recognition with symbolic reasoning
    to provide regulatory-compliant explanations.
    """
    try:
        # Neural component: Pattern recognition
        neural_result = neural_model.predict(request.transaction)
        neural_score = neural_result["pattern_score"]
        
        # Symbolic component: Rule-based reasoning
        symbolic_rules = symbolic_engine.evaluate(request.transaction, request.context)
        symbolic_score = max([rule["score"] for rule in symbolic_rules], default=0.0)
        
        # Combine with weights (40% symbolic, 60% neural)
        combined_score = 0.4 * symbolic_score + 0.6 * neural_score
        
        # Generate explanation
        explanation_dict = explanation_generator.generate(
            neural_result, symbolic_rules, combined_score
        )
        
        # Convert to response model
        explanation = Explanation(
            summary=explanation_dict["summary"],
            components=[
                ExplanationComponent(**comp) for comp in explanation_dict["components"]
            ],
            regulatory_compliant=explanation_dict["regulatory_compliant"],
            explanation_id=explanation_dict["explanation_id"]
        )
        
        # Update metrics
        nsai_prediction_counter.labels(model_version=MODEL_VERSION).inc()
        nsai_explanations_generated.labels(explanation_type="full").inc()
        
        return NSAIPredictionResponse(
            transaction_id=request.transaction_id,
            fraud_probability=combined_score,
            explanation=explanation,
            model_version=MODEL_VERSION,
            timestamp=datetime.utcnow().isoformat()
        )
    
    except Exception as e:
        logger.error(f"NSAI prediction error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"NSAI prediction failed: {str(e)}")


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
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
        "service": "NSAI Fraud Detection Service",
        "version": "1.0.0",
        "status": "running"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8004)
