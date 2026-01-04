"""
Rule Management Service (Phase 3)
==================================

Manages rule lifecycle: storage, versioning, deployment, and rollback.

CRITICAL FEATURES:
- Rule versioning and history
- Deployment automation (with human approval)
- Rollback capability
- Audit trail for compliance
- Rule performance monitoring
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict
import os
from datetime import datetime
import logging
import json
import uuid
from enum import Enum
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Prometheus metrics
rule_deployment_counter = Counter(
    'rule_deployments_total',
    'Total number of rule deployments',
    ['status']
)

rule_rollback_counter = Counter(
    'rule_rollbacks_total',
    'Total number of rule rollbacks',
    ['reason']
)

# Initialize FastAPI app
app = FastAPI(
    title="Rule Management Service",
    description="Manages rule lifecycle: storage, versioning, deployment, and rollback",
    version="1.0.0"
)

# In-memory rule store (in production, use database)
rule_registry: Dict[str, Dict] = {}
rule_versions: Dict[str, List[Dict]] = {}
deployment_history: List[Dict] = []


class DeploymentStatus(str, Enum):
    """Deployment status enumeration."""
    PENDING = "pending"
    DEPLOYING = "deploying"
    DEPLOYED = "deployed"
    FAILED = "failed"
    ROLLED_BACK = "rolled_back"


class RuleDeploymentRequest(BaseModel):
    """Request for rule deployment."""
    rule_id: str
    deployed_by: str
    environment: str = "production"
    comments: Optional[str] = None


class RuleRollbackRequest(BaseModel):
    """Request for rule rollback."""
    rule_id: str
    rolled_back_by: str
    reason: str
    rollback_to_version: Optional[str] = None


@app.post("/deploy-rule")
async def deploy_rule(request: RuleDeploymentRequest):
    """
    Deploy an approved rule to production.
    
    CRITICAL: This endpoint requires the rule to be approved first.
    It does NOT bypass human approval.
    """
    try:
        # Check if rule exists and is approved
        if request.rule_id not in rule_registry:
            raise HTTPException(status_code=404, detail="Rule not found")
        
        rule = rule_registry[request.rule_id]
        
        if rule.get('status') != 'approved':
            raise HTTPException(
                status_code=400, 
                detail=f"Rule must be approved before deployment. Current status: {rule.get('status')}"
            )
        
        # Create deployment record
        deployment_id = str(uuid.uuid4())
        deployment = {
            "deployment_id": deployment_id,
            "rule_id": request.rule_id,
            "rule_name": rule.get('rule_name'),
            "deployed_by": request.deployed_by,
            "environment": request.environment,
            "status": DeploymentStatus.DEPLOYING.value,
            "deployed_at": datetime.utcnow().isoformat(),
            "comments": request.comments
        }
        
        deployment_history.append(deployment)
        
        # In production, would:
        # 1. Validate rule syntax
        # 2. Deploy to Flink/Kafka
        # 3. Update rule registry
        # 4. Monitor performance
        
        # For now, simulate deployment
        rule['status'] = DeploymentStatus.DEPLOYED.value
        rule['deployed_at'] = datetime.utcnow().isoformat()
        rule['deployed_by'] = request.deployed_by
        rule['deployment_id'] = deployment_id
        
        deployment['status'] = DeploymentStatus.DEPLOYED.value
        
        rule_deployment_counter.labels(status="success").inc()
        
        logger.info(f"Rule {request.rule_id} deployed by {request.deployed_by}")
        
        return {
            "deployment_id": deployment_id,
            "rule_id": request.rule_id,
            "status": "deployed",
            "deployed_at": datetime.utcnow().isoformat(),
            "next_steps": [
                "Monitor rule performance",
                "Review metrics after 24 hours",
                "Rollback if performance degrades"
            ]
        }
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Rule deployment error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Rule deployment failed: {str(e)}")


@app.post("/rollback-rule")
async def rollback_rule(request: RuleRollbackRequest):
    """
    Rollback a deployed rule.
    
    This endpoint:
    1. Removes rule from production
    2. Optionally rolls back to previous version
    3. Creates audit trail entry
    """
    try:
        if request.rule_id not in rule_registry:
            raise HTTPException(status_code=404, detail="Rule not found")
        
        rule = rule_registry[request.rule_id]
        
        if rule.get('status') != DeploymentStatus.DEPLOYED.value:
            raise HTTPException(
                status_code=400,
                detail=f"Rule is not deployed. Current status: {rule.get('status')}"
            )
        
        # Create rollback record
        rollback_id = str(uuid.uuid4())
        rollback = {
            "rollback_id": rollback_id,
            "rule_id": request.rule_id,
            "rule_name": rule.get('rule_name'),
            "rolled_back_by": request.rolled_back_by,
            "reason": request.reason,
            "rolled_back_at": datetime.utcnow().isoformat(),
            "previous_version": rule.get('version'),
            "rollback_to_version": request.rollback_to_version
        }
        
        # Update rule status
        rule['status'] = DeploymentStatus.ROLLED_BACK.value
        rule['rolled_back_at'] = datetime.utcnow().isoformat()
        rule['rolled_back_by'] = request.rolled_back_by
        rule['rollback_reason'] = request.reason
        
        # In production, would:
        # 1. Remove rule from Flink/Kafka
        # 2. Restore previous version if specified
        # 3. Update rule registry
        
        rule_rollback_counter.labels(reason=request.reason).inc()
        
        logger.warn(f"Rule {request.rule_id} rolled back by {request.rolled_back_by}: {request.reason}")
        
        return {
            "rollback_id": rollback_id,
            "rule_id": request.rule_id,
            "status": "rolled_back",
            "rolled_back_at": datetime.utcnow().isoformat(),
            "reason": request.reason
        }
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Rule rollback error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Rule rollback failed: {str(e)}")


@app.get("/rules")
async def list_rules(status: Optional[str] = None):
    """List all rules, optionally filtered by status."""
    rules = list(rule_registry.values())
    
    if status:
        rules = [r for r in rules if r.get('status') == status]
    
    return {
        "rules": rules,
        "total": len(rules),
        "filtered_by": status
    }


@app.get("/deployments")
async def list_deployments(limit: int = 50):
    """List recent deployments."""
    return {
        "deployments": deployment_history[-limit:],
        "total": len(deployment_history)
    }


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
        "service": "Rule Management Service",
        "version": "1.0.0",
        "status": "running"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8006)
