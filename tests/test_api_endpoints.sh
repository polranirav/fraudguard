#!/bin/bash

# API Endpoint Test Script
# Tests all Phase 1-3 service endpoints (requires services to be running)

set -e

BASE_URL="http://localhost"
TIMEOUT=5

echo "=========================================="
echo "API Endpoint Tests for CNS-DIS Services"
echo "=========================================="
echo ""

test_endpoint() {
    local service=$1
    local port=$2
    local endpoint=$3
    local method=${4:-GET}
    
    echo -n "Testing $service ($endpoint)... "
    
    if curl -s --max-time $TIMEOUT -X $method "$BASE_URL:$port$endpoint" > /dev/null 2>&1; then
        echo "✓ PASS"
        return 0
    else
        echo "✗ FAIL (service may not be running)"
        return 1
    fi
}

# Test Phase 1 Services
echo "=== Phase 1: LNN + Causal ==="
test_endpoint "LNN Service" 8001 "/health"
test_endpoint "LNN Service" 8001 "/"
test_endpoint "Causal Service" 8002 "/health"
test_endpoint "Causal Service" 8002 "/"

# Test Phase 2 Services
echo ""
echo "=== Phase 2: GNN + NSAI ==="
test_endpoint "GNN Service" 8003 "/health"
test_endpoint "GNN Service" 8003 "/"
test_endpoint "NSAI Service" 8004 "/health"
test_endpoint "NSAI Service" 8004 "/"

# Test Phase 3 Services
echo ""
echo "=== Phase 3: Rule Agents ==="
test_endpoint "Rule Agent Service" 8005 "/health"
test_endpoint "Rule Agent Service" 8005 "/"
test_endpoint "Rule Management Service" 8006 "/health"
test_endpoint "Rule Management Service" 8006 "/"

echo ""
echo "=========================================="
echo "Note: These tests require services to be"
echo "running. Start with: docker-compose up -d"
echo "=========================================="
