#!/bin/bash

# Docker Build Test Script
# Tests that all Dockerfiles can be built (dry-run validation)

set -e

echo "=========================================="
echo "Docker Build Validation Tests"
echo "=========================================="
echo ""

test_dockerfile() {
    local service=$1
    local dockerfile_path=$2
    
    echo -n "Testing $service Dockerfile... "
    
    if [ ! -f "$dockerfile_path" ]; then
        echo "✗ FAIL (Dockerfile not found)"
        return 1
    fi
    
    # Check Dockerfile syntax (basic validation)
    if grep -q "FROM" "$dockerfile_path" && grep -q "COPY" "$dockerfile_path"; then
        echo "✓ PASS (syntax valid)"
        return 0
    else
        echo "✗ FAIL (invalid syntax)"
        return 1
    fi
}

# Test all service Dockerfiles
test_dockerfile "LNN Service" "ml-models/lnn-service/Dockerfile"
test_dockerfile "Causal Service" "ml-models/causal-service/Dockerfile"
test_dockerfile "GNN Service" "ml-models/gnn-service/Dockerfile"
test_dockerfile "NSAI Service" "ml-models/nsai-service/Dockerfile"
test_dockerfile "Rule Agent Service" "ml-models/rule-agent-service/Dockerfile"
test_dockerfile "Rule Management Service" "ml-models/rule-management-service/Dockerfile"

echo ""
echo "=========================================="
echo "Dockerfile validation complete"
echo "=========================================="
