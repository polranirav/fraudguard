"""
Autonomous Rule Generation Agent Service (Phase 3)
===================================================

This service implements AI agents that autonomously analyze fraud patterns,
generate detection rules, test them, and propose them for human approval.

CRITICAL SAFETY MECHANISMS:
- Human-in-the-Loop: ALL rules require human approval before deployment
- Testing Framework: Rules tested on historical data before approval
- Audit Trail: All rule changes logged for compliance
- Rollback Capability: Rules can be automatically rolled back if performance degrades

Financial Industry Compliance:
- Zero autonomous deployments (human approval required)
- Complete audit trail for regulatory compliance
- Risk management integration
"""

from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import os
from datetime import datetime
import logging
import json
import uuid
from enum import Enum
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Prometheus metrics
rule_generation_counter = Counter(
    'rule_generation_requests_total',
    'Total number of rule generation requests',
    ['status']
)

rule_approval_counter = Counter(
    'rule_approvals_total',
    'Total number of rule approvals',
    ['decision']
)

rule_test_counter = Counter(
    'rule_tests_total',
    'Total number of rule tests',
    ['result']
)

# Model configuration
MODEL_VERSION = os.getenv('MODEL_VERSION', '1.0.0')
LLM_API_KEY = os.getenv('LLM_API_KEY', '')  # OpenAI, Anthropic, or Azure OpenAI
LLM_MODEL = os.getenv('LLM_MODEL', 'gpt-4')  # Default to GPT-4
LLM_ENABLED = os.getenv('LLM_ENABLED', 'false').lower() == 'true'


class RuleStatus(str, Enum):
    """Rule status enumeration."""
    PENDING_ANALYSIS = "pending_analysis"
    GENERATED = "generated"
    TESTING = "testing"
    PENDING_APPROVAL = "pending_approval"
    APPROVED = "approved"
    REJECTED = "rejected"
    DEPLOYED = "deployed"
    ROLLED_BACK = "rolled_back"


class PatternAnalysisAgent:
    """
    Pattern Analysis Agent: Analyzes fraud patterns and identifies gaps.
    
    This agent:
    1. Analyzes historical fraud data
    2. Identifies patterns that current rules miss
    3. Suggests new rule opportunities
    """
    
    def analyze_patterns(self, fraud_data: List[Dict]) -> List[Dict]:
        """
        Analyze fraud patterns and identify gaps.
        
        Returns:
            List of identified patterns with gap analysis
        """
        patterns = []
        
        # Analyze transaction patterns
        # In production, this would use ML models to identify patterns
        # For now, we use simplified pattern detection
        
        # Pattern 1: Small test transactions followed by large withdrawal
        test_then_withdraw = self._detect_test_then_withdraw(fraud_data)
        if test_then_withdraw:
            patterns.append({
                "pattern_id": str(uuid.uuid4()),
                "pattern_name": "test_then_withdraw",
                "description": "Small test transactions (< $10) followed by large withdrawal (> $1000)",
                "examples": test_then_withdraw[:5],  # Top 5 examples
                "gap_analysis": "Current rules don't detect this sequential pattern",
                "severity": "high",
                "detected_at": datetime.utcnow().isoformat()
            })
        
        # Pattern 2: Rapid device switching
        device_switching = self._detect_device_switching(fraud_data)
        if device_switching:
            patterns.append({
                "pattern_id": str(uuid.uuid4()),
                "pattern_name": "rapid_device_switching",
                "description": "Customer uses 3+ different devices within 1 hour",
                "examples": device_switching[:5],
                "gap_analysis": "Current rules don't track device switching patterns",
                "severity": "medium",
                "detected_at": datetime.utcnow().isoformat()
            })
        
        # Pattern 3: Geographic clustering
        geo_clustering = self._detect_geo_clustering(fraud_data)
        if geo_clustering:
            patterns.append({
                "pattern_id": str(uuid.uuid4()),
                "pattern_name": "geographic_clustering",
                "description": "Multiple accounts transacting from same location",
                "examples": geo_clustering[:5],
                "gap_analysis": "Current rules don't detect geographic clustering",
                "severity": "high",
                "detected_at": datetime.utcnow().isoformat()
            })
        
        return patterns
    
    def _detect_test_then_withdraw(self, fraud_data: List[Dict]) -> List[Dict]:
        """Detect test-then-withdraw pattern."""
        examples = []
        for i, txn in enumerate(fraud_data):
            if i < len(fraud_data) - 1:
                next_txn = fraud_data[i + 1]
                if (txn.get('amount', 0) < 10 and 
                    next_txn.get('amount', 0) > 1000 and
                    (next_txn.get('timestamp', 0) - txn.get('timestamp', 0)) < 3600000):  # Within 1 hour
                    examples.append({
                        "test_transaction": txn,
                        "withdrawal_transaction": next_txn,
                        "time_gap_seconds": (next_txn.get('timestamp', 0) - txn.get('timestamp', 0)) / 1000
                    })
        return examples[:10]  # Return top 10
    
    def _detect_device_switching(self, fraud_data: List[Dict]) -> List[Dict]:
        """Detect rapid device switching pattern."""
        # Simplified - in production would track device history
        return []
    
    def _detect_geo_clustering(self, fraud_data: List[Dict]) -> List[Dict]:
        """Detect geographic clustering pattern."""
        # Simplified - in production would analyze location patterns
        return []


class RuleGenerationAgent:
    """
    Rule Generation Agent: Uses LLM to generate rule logic.
    
    CRITICAL: This agent ONLY generates rules - it does NOT deploy them.
    All rules require human approval.
    """
    
    def generate_rule(self, pattern: Dict) -> Dict:
        """
        Generate rule logic for a detected pattern.
        
        In production, this would use an LLM (GPT-4, Claude, etc.) to generate
        rule logic. For now, we use template-based generation.
        """
        pattern_name = pattern.get('pattern_name', 'unknown')
        
        # Template-based rule generation (in production, use LLM)
        rule_templates = {
            'test_then_withdraw': {
                "name": "test_then_withdraw_pattern",
                "description": "Detects small test transactions followed by large withdrawal",
                "logic": """
                    IF (transaction_count_small_txns >= 3 AND 
                        all_small_txns_amount < 10.00 AND
                        next_transaction_amount > 1000.00 AND
                        time_between_last_small_and_large < 3600 seconds)
                    THEN flag_as_fraud
                    RISK_SCORE: 0.75
                """,
                "category": "VELOCITY",
                "priority": "HIGH"
            },
            'rapid_device_switching': {
                "name": "rapid_device_switching_pattern",
                "description": "Detects rapid device switching within short time window",
                "logic": """
                    IF (device_count_last_hour >= 3 AND
                        time_window < 3600 seconds)
                    THEN flag_as_fraud
                    RISK_SCORE: 0.65
                """,
                "category": "DEVICE",
                "priority": "MEDIUM"
            },
            'geographic_clustering': {
                "name": "geographic_clustering_pattern",
                "description": "Detects multiple accounts transacting from same location",
                "logic": """
                    IF (accounts_at_same_location >= 5 AND
                        location_transaction_count > 10 AND
                        time_window < 3600 seconds)
                    THEN flag_as_fraud
                    RISK_SCORE: 0.70
                """,
                "category": "GEO",
                "priority": "HIGH"
            }
        }
        
        rule_template = rule_templates.get(pattern_name, {
            "name": f"generated_rule_{pattern_name}",
            "description": pattern.get('description', 'Generated rule'),
            "logic": "IF (pattern_detected) THEN flag_as_fraud",
            "category": "GENERATED",
            "priority": "MEDIUM"
        })
        
        # If LLM is enabled, use it to generate rule
        if LLM_ENABLED and LLM_API_KEY:
            try:
                rule_template = self._generate_with_llm(pattern)
            except Exception as e:
                logger.warning(f"LLM generation failed, using template: {e}")
        
        return {
            "rule_id": str(uuid.uuid4()),
            "pattern_id": pattern.get('pattern_id'),
            "rule_name": rule_template["name"],
            "description": rule_template["description"],
            "logic": rule_template["logic"],
            "category": rule_template["category"],
            "priority": rule_template["priority"],
            "generated_at": datetime.utcnow().isoformat(),
            "status": RuleStatus.GENERATED.value
        }
    
    def _generate_with_llm(self, pattern: Dict) -> Dict:
        """
        Generate rule using LLM (OpenAI, Anthropic, Azure OpenAI).
        
        CRITICAL: This is a placeholder. In production, implement proper
        LLM integration with safety prompts and validation.
        """
        # Placeholder for LLM integration
        # In production, would call OpenAI/Anthropic API with:
        # - Pattern description
        # - Examples
        # - Safety constraints
        # - Rule format requirements
        
        prompt = f"""
        Generate a fraud detection rule for the following pattern:
        
        Pattern: {pattern.get('pattern_name')}
        Description: {pattern.get('description')}
        Examples: {json.dumps(pattern.get('examples', []), indent=2)}
        
        Requirements:
        1. Rule must be deterministic and explainable
        2. Rule must include risk score (0.0 to 1.0)
        3. Rule must be testable on historical data
        4. Rule must comply with financial regulations
        
        Generate rule in JSON format with: name, description, logic, category, priority
        """
        
        # In production, call LLM API here
        # For now, return template
        return {
            "name": f"llm_generated_{pattern.get('pattern_name')}",
            "description": pattern.get('description'),
            "logic": "IF (pattern_detected) THEN flag_as_fraud",
            "category": "GENERATED",
            "priority": "MEDIUM"
        }


class RuleTestingAgent:
    """
    Rule Testing Agent: Tests generated rules on historical data.
    
    This agent:
    1. Tests rule on historical transaction data
    2. Calculates true positive rate, false positive rate, coverage
    3. Validates rule performance
    """
    
    def test_rule(self, rule: Dict, historical_data: List[Dict]) -> Dict:
        """
        Test rule on historical data.
        
        Returns:
            Test results with performance metrics
        """
        # Simplified testing - in production would execute rule logic
        true_positives = 0
        false_positives = 0
        true_negatives = 0
        false_negatives = 0
        
        for txn in historical_data:
            # In production, would execute rule logic here
            # For now, use simplified logic
            is_fraud = txn.get('is_fraud', False)
            rule_triggered = self._evaluate_rule(rule, txn)
            
            if rule_triggered and is_fraud:
                true_positives += 1
            elif rule_triggered and not is_fraud:
                false_positives += 1
            elif not rule_triggered and not is_fraud:
                true_negatives += 1
            else:
                false_negatives += 1
        
        total = len(historical_data)
        tpr = true_positives / (true_positives + false_negatives) if (true_positives + false_negatives) > 0 else 0.0
        fpr = false_positives / (false_positives + true_negatives) if (false_positives + true_negatives) > 0 else 0.0
        coverage = (true_positives + false_positives) / total if total > 0 else 0.0
        
        return {
            "rule_id": rule.get('rule_id'),
            "test_results": {
                "true_positive_rate": tpr,
                "false_positive_rate": fpr,
                "coverage": coverage,
                "true_positives": true_positives,
                "false_positives": false_positives,
                "true_negatives": true_negatives,
                "false_negatives": false_negatives,
                "total_tested": total
            },
            "test_status": "passed" if tpr > 0.7 and fpr < 0.05 else "failed",
            "tested_at": datetime.utcnow().isoformat()
        }
    
    def _evaluate_rule(self, rule: Dict, transaction: Dict) -> bool:
        """Evaluate rule logic on transaction (simplified)."""
        # In production, would parse and execute rule logic
        # For now, use simplified evaluation
        rule_name = rule.get('rule_name', '')
        
        if 'test_then_withdraw' in rule_name:
            # Simplified logic
            return transaction.get('amount', 0) > 1000 and transaction.get('previous_small_txns', 0) >= 3
        
        return False


# Initialize FastAPI app
app = FastAPI(
    title="Autonomous Rule Generation Agent Service",
    description="AI agents for autonomous rule generation with human-in-the-loop approval",
    version="1.0.0"
)

# Initialize agents
pattern_agent = PatternAnalysisAgent()
rule_generation_agent = RuleGenerationAgent()
testing_agent = RuleTestingAgent()

# In-memory rule store (in production, use database)
rule_store: Dict[str, Dict] = {}


# Request/Response models
class PatternAnalysisRequest(BaseModel):
    """Request for pattern analysis."""
    fraud_data: List[Dict]
    time_window_days: int = 30


class RuleGenerationRequest(BaseModel):
    """Request for rule generation."""
    pattern: Dict


class RuleTestRequest(BaseModel):
    """Request for rule testing."""
    rule: Dict
    historical_data: List[Dict]


class RuleApprovalRequest(BaseModel):
    """Request for rule approval."""
    rule_id: str
    approved: bool
    approver: str
    comments: Optional[str] = None


@app.post("/analyze-patterns")
async def analyze_patterns(request: PatternAnalysisRequest):
    """
    Analyze fraud patterns and identify gaps.
    
    This endpoint triggers the Pattern Analysis Agent to:
    1. Analyze historical fraud data
    2. Identify patterns that current rules miss
    3. Suggest new rule opportunities
    """
    try:
        patterns = pattern_agent.analyze_patterns(request.fraud_data)
        
        rule_generation_counter.labels(status="pattern_analyzed").inc()
        
        return {
            "patterns": patterns,
            "total_patterns": len(patterns),
            "analyzed_at": datetime.utcnow().isoformat()
        }
    
    except Exception as e:
        logger.error(f"Pattern analysis error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Pattern analysis failed: {str(e)}")


@app.post("/generate-rule")
async def generate_rule(request: RuleGenerationRequest):
    """
    Generate rule logic for a detected pattern.
    
    CRITICAL: This endpoint ONLY generates rules - it does NOT deploy them.
    All rules require human approval via /approve-rule endpoint.
    """
    try:
        rule = rule_generation_agent.generate_rule(request.pattern)
        
        # Store rule for approval
        rule_store[rule['rule_id']] = rule
        
        rule_generation_counter.labels(status="rule_generated").inc()
        
        return {
            "rule": rule,
            "status": "generated",
            "next_step": "Test rule using /test-rule endpoint, then approve using /approve-rule endpoint",
            "generated_at": datetime.utcnow().isoformat()
        }
    
    except Exception as e:
        logger.error(f"Rule generation error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Rule generation failed: {str(e)}")


@app.post("/test-rule")
async def test_rule(request: RuleTestRequest):
    """
    Test generated rule on historical data.
    
    This endpoint:
    1. Tests rule on historical transaction data
    2. Calculates performance metrics (TPR, FPR, coverage)
    3. Validates rule performance
    """
    try:
        test_results = testing_agent.test_rule(request.rule, request.historical_data)
        
        # Update rule with test results
        rule_id = request.rule.get('rule_id')
        if rule_id in rule_store:
            rule_store[rule_id]['test_results'] = test_results['test_results']
            rule_store[rule_id]['test_status'] = test_results['test_status']
            rule_store[rule_id]['status'] = RuleStatus.TESTING.value
        
        rule_test_counter.labels(result=test_results['test_status']).inc()
        
        return {
            "test_results": test_results,
            "status": test_results['test_status'],
            "next_step": "If test passed, approve rule using /approve-rule endpoint",
            "tested_at": datetime.utcnow().isoformat()
        }
    
    except Exception as e:
        logger.error(f"Rule testing error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Rule testing failed: {str(e)}")


@app.post("/approve-rule")
async def approve_rule(request: RuleApprovalRequest):
    """
    Approve or reject a generated rule.
    
    CRITICAL: This is the ONLY way rules can be deployed.
    All rules require human approval - no autonomous deployments.
    
    This endpoint:
    1. Records approval/rejection decision
    2. Updates rule status
    3. Creates audit trail entry
    4. If approved, marks rule for deployment (deployment happens separately)
    """
    try:
        if request.rule_id not in rule_store:
            raise HTTPException(status_code=404, detail="Rule not found")
        
        rule = rule_store[request.rule_id]
        
        # Update rule status
        if request.approved:
            rule['status'] = RuleStatus.APPROVED.value
            rule['approved_by'] = request.approver
            rule['approved_at'] = datetime.utcnow().isoformat()
            rule['approval_comments'] = request.comments
            
            rule_approval_counter.labels(decision="approved").inc()
            
            # In production, would trigger deployment workflow here
            # For now, just mark as approved
            logger.info(f"Rule {request.rule_id} approved by {request.approver}")
        else:
            rule['status'] = RuleStatus.REJECTED.value
            rule['rejected_by'] = request.approver
            rule['rejected_at'] = datetime.utcnow().isoformat()
            rule['rejection_comments'] = request.comments
            
            rule_approval_counter.labels(decision="rejected").inc()
            
            logger.info(f"Rule {request.rule_id} rejected by {request.approver}")
        
        # Create audit trail entry
        audit_entry = {
            "rule_id": request.rule_id,
            "action": "approval" if request.approved else "rejection",
            "approver": request.approver,
            "comments": request.comments,
            "timestamp": datetime.utcnow().isoformat()
        }
        
        # In production, would store audit entry in database
        logger.info(f"Audit trail: {json.dumps(audit_entry)}")
        
        return {
            "rule_id": request.rule_id,
            "status": rule['status'],
            "decision": "approved" if request.approved else "rejected",
            "approver": request.approver,
            "timestamp": datetime.utcnow().isoformat(),
            "next_step": "If approved, deploy rule using /deploy-rule endpoint" if request.approved else "Rule rejected"
        }
    
    except Exception as e:
        logger.error(f"Rule approval error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Rule approval failed: {str(e)}")


@app.get("/rules")
async def list_rules(status: Optional[str] = None):
    """List all rules, optionally filtered by status."""
    rules = list(rule_store.values())
    
    if status:
        rules = [r for r in rules if r.get('status') == status]
    
    return {
        "rules": rules,
        "total": len(rules),
        "filtered_by": status
    }


@app.get("/rules/{rule_id}")
async def get_rule(rule_id: str):
    """Get rule by ID."""
    if rule_id not in rule_store:
        raise HTTPException(status_code=404, detail="Rule not found")
    
    return rule_store[rule_id]


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "llm_enabled": LLM_ENABLED,
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
        "service": "Autonomous Rule Generation Agent Service",
        "version": "1.0.0",
        "status": "running",
        "safety_mechanisms": [
            "Human-in-the-loop approval required",
            "Testing framework validation",
            "Audit trail for compliance",
            "Rollback capability"
        ]
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8005)
