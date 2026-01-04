#!/bin/bash

# Run All Tests Script
# Executes all test suites for CNS-DIS upgrade

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║     CNS-DIS Upgrade - Complete Test Suite Execution        ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

cd "$(dirname "$0")/.."

# Test 1: Python Service Tests
echo "Running Python Service Tests..."
echo "----------------------------------------"
python3 tests/test_services.py
SERVICE_TEST_RESULT=$?

echo ""
echo ""

# Test 2: Integration Tests
echo "Running Integration Tests..."
echo "----------------------------------------"
python3 tests/test_integration.py
INTEGRATION_TEST_RESULT=$?

echo ""
echo ""

# Test 3: Docker Build Tests
echo "Running Docker Build Tests..."
echo "----------------------------------------"
./tests/test_docker_builds.sh
DOCKER_TEST_RESULT=$?

echo ""
echo ""

# Final Summary
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                    FINAL TEST SUMMARY                        ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

TOTAL_TESTS=3
PASSED_TESTS=0

if [ $SERVICE_TEST_RESULT -eq 0 ]; then
    echo "✓ Python Service Tests: PASSED"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo "✗ Python Service Tests: FAILED"
fi

if [ $INTEGRATION_TEST_RESULT -eq 0 ]; then
    echo "✓ Integration Tests: PASSED"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo "✗ Integration Tests: FAILED"
fi

if [ $DOCKER_TEST_RESULT -eq 0 ]; then
    echo "✓ Docker Build Tests: PASSED"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo "✗ Docker Build Tests: FAILED"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "OVERALL: $PASSED_TESTS/$TOTAL_TESTS test suites passed"
echo "═══════════════════════════════════════════════════════════════"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo ""
    echo "✅ ALL TESTS PASSED - System is ready for deployment!"
    exit 0
else
    echo ""
    echo "⚠️  Some tests failed - Review output above"
    exit 1
fi
