"""
Causal Inference Service for Fraud Detection Explanations
==========================================================

This service provides counterfactual explanations and root cause analysis
for fraud detection decisions. It answers:
- "Why was this transaction flagged?"
- "What would have happened if the amount was different?"
- "What are the causal factors?"

Financial Industry Value:
- Regulatory Compliance: EU AI Act requires explainability
- Analyst Trust: Analysts need to understand why decisions were made
- Customer Service: Can explain to customers why transactions were blocked
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict
import numpy as np
import pandas as pd
from datetime import datetime
import logging
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Prometheus metrics
causal_explanation_counter = Counter(
    'causal_explanations_total',
    'Total number of causal explanations generated',
    ['model_version']
)

causal_explanation_latency = Histogram(
    'causal_explanation_latency_seconds',
    'Causal explanation generation latency',
    ['model_version']
)

# Initialize FastAPI app
app = FastAPI(
    title="Causal Inference Service",
    description="Counterfactual explanations and root cause analysis for fraud detection",
    version="1.0.0"
)

model_version = "1.0.0"


class TransactionFeatures(BaseModel):
    """Transaction features for causal analysis."""
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


class CausalExplanationRequest(BaseModel):
    """Request for causal explanation."""
    transaction_id: str
    transaction: TransactionFeatures
    fraud_probability: float
    triggered_rules: Optional[List[str]] = []


class CausalFactor(BaseModel):
    """A causal factor contributing to fraud probability."""
    factor: str
    impact: float  # Contribution to fraud probability (0-1)
    current_value: float
    baseline_value: float
    description: str


class CounterfactualScenario(BaseModel):
    """A counterfactual scenario (what-if analysis)."""
    scenario: str
    fraud_probability: float
    change: float  # Change from original probability
    modified_features: Dict[str, float]


class CausalExplanationResponse(BaseModel):
    """Response with causal explanation."""
    transaction_id: str
    causal_factors: List[CausalFactor]
    counterfactuals: List[CounterfactualScenario]
    root_cause: str
    explanation_summary: str
    timestamp: str


class CausalInferenceEngine:
    """
    Causal inference engine for fraud detection.
    
    Uses feature importance and counterfactual reasoning to explain
    fraud detection decisions.
    """
    
    # Feature importance weights (learned from historical data)
    # In production, these would be learned from causal models
    FEATURE_IMPORTANCE = {
        'transaction_amount': 0.15,
        'transaction_count_1min': 0.20,
        'total_amount_1min': 0.18,
        'velocity_kmh': 0.15,
        'distance_from_home_km': 0.10,
        'is_known_device': 0.08,
        'is_vpn_detected': 0.07,
        'merchant_reputation_score': 0.05,
        'time_since_last_txn_minutes': 0.02
    }
    
    # Baseline values (normal transaction characteristics)
    BASELINE_VALUES = {
        'transaction_amount': 100.0,
        'transaction_count_1min': 1,
        'total_amount_1min': 100.0,
        'velocity_kmh': 0.0,
        'distance_from_home_km': 5.0,
        'is_known_device': 1,
        'is_vpn_detected': 0,
        'merchant_reputation_score': 0.8,
        'time_since_last_txn_minutes': 30.0
    }
    
    def identify_causal_factors(self, transaction: TransactionFeatures, 
                               fraud_probability: float) -> List[CausalFactor]:
        """Identify causal factors contributing to fraud probability."""
        factors = []
        
        # Calculate impact for each feature
        for feature_name, importance in self.FEATURE_IMPORTANCE.items():
            current_value = getattr(transaction, feature_name, 0)
            baseline_value = self.BASELINE_VALUES.get(feature_name, 0)
            
            # Calculate normalized deviation
            if baseline_value != 0:
                deviation = abs(current_value - baseline_value) / abs(baseline_value)
            else:
                deviation = abs(current_value)
            
            # Impact = importance * deviation * fraud_probability
            impact = importance * min(deviation, 2.0) * fraud_probability
            
            if impact > 0.05:  # Only include significant factors
                description = self._get_factor_description(feature_name, current_value, baseline_value)
                
                factors.append(CausalFactor(
                    factor=feature_name,
                    impact=impact,
                    current_value=current_value,
                    baseline_value=baseline_value,
                    description=description
                ))
        
        # Sort by impact
        factors.sort(key=lambda x: x.impact, reverse=True)
        
        return factors[:5]  # Top 5 factors
    
    def _get_factor_description(self, feature_name: str, current: float, baseline: float) -> str:
        """Generate human-readable description of factor."""
        descriptions = {
            'transaction_amount': f"Transaction amount ${current:.2f} vs. typical ${baseline:.2f}",
            'transaction_count_1min': f"{int(current)} transactions in 1 minute vs. typical {int(baseline)}",
            'total_amount_1min': f"${current:.2f} total in 1 minute vs. typical ${baseline:.2f}",
            'velocity_kmh': f"Travel velocity {current:.0f} km/h (impossible if > 800 km/h)",
            'distance_from_home_km': f"{current:.1f} km from home vs. typical {baseline:.1f} km",
            'is_known_device': "Unknown device" if current == 0 else "Known device",
            'is_vpn_detected': "VPN detected" if current == 1 else "No VPN",
            'merchant_reputation_score': f"Merchant reputation {current:.2f} vs. typical {baseline:.2f}",
            'time_since_last_txn_minutes': f"{current:.1f} minutes since last transaction"
        }
        return descriptions.get(feature_name, f"{feature_name}: {current} vs. {baseline}")
    
    def generate_counterfactuals(self, transaction: TransactionFeatures,
                                fraud_probability: float) -> List[CounterfactualScenario]:
        """Generate counterfactual scenarios (what-if analysis)."""
        counterfactuals = []
        
        # Scenario 1: What if amount was normal?
        if transaction.transaction_amount > 500:
            modified = transaction.dict()
            modified['transaction_amount'] = 100.0
            new_prob = self._estimate_probability(modified, fraud_probability, 'transaction_amount', 100.0)
            counterfactuals.append(CounterfactualScenario(
                scenario="If transaction amount was $100 instead of ${:.2f}".format(transaction.transaction_amount),
                fraud_probability=max(0.0, new_prob),
                change=new_prob - fraud_probability,
                modified_features={'transaction_amount': 100.0}
            ))
        
        # Scenario 2: What if velocity was normal?
        if transaction.velocity_kmh > 100:
            modified = transaction.dict()
            modified['velocity_kmh'] = 0.0
            new_prob = self._estimate_probability(modified, fraud_probability, 'velocity_kmh', 0.0)
            counterfactuals.append(CounterfactualScenario(
                scenario="If travel velocity was 0 km/h instead of {:.0f} km/h".format(transaction.velocity_kmh),
                fraud_probability=max(0.0, new_prob),
                change=new_prob - fraud_probability,
                modified_features={'velocity_kmh': 0.0}
            ))
        
        # Scenario 3: What if transaction count was normal?
        if transaction.transaction_count_1min > 3:
            modified = transaction.dict()
            modified['transaction_count_1min'] = 1
            new_prob = self._estimate_probability(modified, fraud_probability, 'transaction_count_1min', 1)
            counterfactuals.append(CounterfactualScenario(
                scenario="If transaction count was 1 instead of {} in 1 minute".format(transaction.transaction_count_1min),
                fraud_probability=max(0.0, new_prob),
                change=new_prob - fraud_probability,
                modified_features={'transaction_count_1min': 1}
            ))
        
        # Scenario 4: What if device was known?
        if transaction.is_known_device == 0:
            modified = transaction.dict()
            modified['is_known_device'] = 1
            new_prob = self._estimate_probability(modified, fraud_probability, 'is_known_device', 1)
            counterfactuals.append(CounterfactualScenario(
                scenario="If device was known instead of unknown",
                fraud_probability=max(0.0, new_prob),
                change=new_prob - fraud_probability,
                modified_features={'is_known_device': 1}
            ))
        
        return counterfactuals
    
    def _estimate_probability(self, modified_features: dict, original_prob: float,
                            changed_feature: str, new_value: float) -> float:
        """Estimate fraud probability with modified feature."""
        # Simplified estimation: adjust based on feature importance
        importance = self.FEATURE_IMPORTANCE.get(changed_feature, 0.1)
        
        # Calculate change in feature value
        original_value = modified_features.get(changed_feature, 0)
        if original_value != 0:
            change_ratio = (new_value - original_value) / original_value
        else:
            change_ratio = new_value - original_value
        
        # Adjust probability
        adjustment = importance * change_ratio * original_prob
        new_prob = original_prob + adjustment
        
        return max(0.0, min(1.0, new_prob))
    
    def identify_root_cause(self, factors: List[CausalFactor]) -> str:
        """Identify the primary root cause from causal factors."""
        if not factors:
            return "No significant causal factors identified"
        
        top_factor = factors[0]
        
        root_causes = {
            'transaction_count_1min': "High transaction velocity (rapid succession of transactions)",
            'total_amount_1min': "High spending velocity (large amounts in short time)",
            'velocity_kmh': "Impossible travel (geographically impossible transaction sequence)",
            'transaction_amount': "Unusually large transaction amount",
            'is_known_device': "Unknown device (device not recognized)",
            'is_vpn_detected': "VPN usage detected (attempting to hide location)",
            'distance_from_home_km': "Unusual distance from home location"
        }
        
        return root_causes.get(top_factor.factor, 
                              f"Primary factor: {top_factor.factor} ({top_factor.description})")


# Global engine instance
causal_engine = CausalInferenceEngine()


@app.post("/explain", response_model=CausalExplanationResponse)
async def explain_fraud(request: CausalExplanationRequest):
    """
    Generate causal explanation for fraud detection decision.
    
    Provides:
    - Causal factors (what contributed to the decision)
    - Counterfactual scenarios (what-if analysis)
    - Root cause identification
    """
    try:
        # Identify causal factors
        factors = causal_engine.identify_causal_factors(
            request.transaction,
            request.fraud_probability
        )
        
        # Generate counterfactuals
        counterfactuals = causal_engine.generate_counterfactuals(
            request.transaction,
            request.fraud_probability
        )
        
        # Identify root cause
        root_cause = causal_engine.identify_root_cause(factors)
        
        # Generate summary
        summary = f"Fraud probability {request.fraud_probability:.2%} due to: {root_cause}"
        if factors:
            summary += f". Top contributing factors: {', '.join([f.factor for f in factors[:3]])}"
        
        # Update metrics
        causal_explanation_counter.labels(model_version=model_version).inc()
        
        return CausalExplanationResponse(
            transaction_id=request.transaction_id,
            causal_factors=factors,
            counterfactuals=counterfactuals,
            root_cause=root_cause,
            explanation_summary=summary,
            timestamp=datetime.utcnow().isoformat()
        )
    
    except Exception as e:
        logger.error(f"Causal explanation error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Causal explanation failed: {str(e)}")


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
        "service": "Causal Inference Service",
        "version": "1.0.0",
        "status": "running"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)
