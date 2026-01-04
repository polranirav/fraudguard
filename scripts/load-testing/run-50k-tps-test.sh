#!/bin/bash
# 50K TPS Load Test Script
# Tests system capability to handle 50,000 transactions per second

set -e

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
TOPIC=${TOPIC:-transactions-live}
TARGET_TPS=${TARGET_TPS:-50000}
DURATION_SECONDS=${DURATION_SECONDS:-60}
MESSAGE_SIZE=${MESSAGE_SIZE:-1024}
RESULTS_DIR="../../load-test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "=========================================="
echo "50K TPS Load Test - Fraud Detection Platform"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  Kafka: ${KAFKA_BOOTSTRAP_SERVERS}"
echo "  Topic: ${TOPIC}"
echo "  Target TPS: ${TARGET_TPS}"
echo "  Duration: ${DURATION_SECONDS} seconds"
echo "  Message Size: ${MESSAGE_SIZE} bytes"
echo "  Total Messages: $((TARGET_TPS * DURATION_SECONDS))"
echo ""

# Create results directory
mkdir -p "${RESULTS_DIR}"

# Record start time
START_TIME=$(date +%s)
START_TIME_ISO=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

echo "Starting load test at ${START_TIME_ISO}..."
echo ""

# Get initial metrics
echo "=== Pre-Test Metrics ==="
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" > "${RESULTS_DIR}/pre-test-metrics-${TIMESTAMP}.txt"
cat "${RESULTS_DIR}/pre-test-metrics-${TIMESTAMP}.txt"

# Get initial Kafka topic offsets
KAFKA_OFFSETS_BEFORE=$(docker exec fraud-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic ${TOPIC} 2>/dev/null | awk -F: '{sum+=$3} END {print sum}')

echo ""
echo "Kafka offset before test: ${KAFKA_OFFSETS_BEFORE:-0}"
echo ""

# Run the load test
echo "=== Running Load Test ==="
echo ""

if command -v kafka-producer-perf-test &> /dev/null; then
    echo "Using local kafka-producer-perf-test..."
    kafka-producer-perf-test \
        --topic ${TOPIC} \
        --num-records $((TARGET_TPS * DURATION_SECONDS)) \
        --record-size ${MESSAGE_SIZE} \
        --throughput ${TARGET_TPS} \
        --producer-props bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS} \
        --producer-props acks=1 \
        --producer-props batch.size=65536 \
        --producer-props linger.ms=5 \
        --producer-props compression.type=lz4 \
        --producer-props buffer.memory=67108864 \
        2>&1 | tee "${RESULTS_DIR}/load-test-output-${TIMESTAMP}.log"
elif docker ps | grep -q fraud-kafka; then
    echo "Using Docker kafka-producer-perf-test..."
    docker exec fraud-kafka kafka-producer-perf-test \
        --topic ${TOPIC} \
        --num-records $((TARGET_TPS * DURATION_SECONDS)) \
        --record-size ${MESSAGE_SIZE} \
        --throughput ${TARGET_TPS} \
        --producer-props bootstrap.servers=localhost:9092 \
        --producer-props acks=1 \
        --producer-props batch.size=65536 \
        --producer-props linger.ms=5 \
        --producer-props compression.type=lz4 \
        --producer-props buffer.memory=67108864 \
        2>&1 | tee "${RESULTS_DIR}/load-test-output-${TIMESTAMP}.log"
else
    echo "ERROR: kafka-producer-perf-test not found and Kafka container not running"
    exit 1
fi

# Record end time
END_TIME=$(date +%s)
END_TIME_ISO=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
DURATION=$((END_TIME - START_TIME))

# Get final metrics
echo ""
echo "=== Post-Test Metrics ==="
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" > "${RESULTS_DIR}/post-test-metrics-${TIMESTAMP}.txt"
cat "${RESULTS_DIR}/post-test-metrics-${TIMESTAMP}.txt"

# Get final Kafka topic offsets
KAFKA_OFFSETS_AFTER=$(docker exec fraud-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic ${TOPIC} 2>/dev/null | awk -F: '{sum+=$3} END {print sum}')

MESSAGES_PRODUCED=$((KAFKA_OFFSETS_AFTER - KAFKA_OFFSETS_BEFORE))

echo ""
echo "Kafka offset after test: ${KAFKA_OFFSETS_AFTER:-0}"
echo "Messages produced: ${MESSAGES_PRODUCED}"
echo ""

# Parse results from log
if [ -f "${RESULTS_DIR}/load-test-output-${TIMESTAMP}.log" ]; then
    ACTUAL_TPS=$(grep -E "records/sec|throughput" "${RESULTS_DIR}/load-test-output-${TIMESTAMP}.log" | \
        grep -oE "[0-9]+\.[0-9]+ records/sec" | head -1 | grep -oE "[0-9]+\.[0-9]+" || echo "0")
    
    LATENCY_P50=$(grep -E "50th|p50" "${RESULTS_DIR}/load-test-output-${TIMESTAMP}.log" | \
        grep -oE "[0-9]+\.[0-9]+" | head -1 || echo "0")
    LATENCY_P99=$(grep -E "99th|p99" "${RESULTS_DIR}/load-test-output-${TIMESTAMP}.log" | \
        grep -oE "[0-9]+\.[0-9]+" | head -1 || echo "0")
fi

# Generate summary report
cat > "${RESULTS_DIR}/load-test-summary-${TIMESTAMP}.md" << EOF
# Load Test Results - 50K TPS

**Test Date**: ${START_TIME_ISO}
**Duration**: ${DURATION} seconds
**Target TPS**: ${TARGET_TPS}

## Results

### Throughput
- **Target**: ${TARGET_TPS} TPS
- **Actual**: ${ACTUAL_TPS:-N/A} TPS
- **Messages Produced**: ${MESSAGES_PRODUCED}
- **Success Rate**: $(echo "scale=2; ${ACTUAL_TPS:-0} / ${TARGET_TPS} * 100" | bc 2>/dev/null || echo "N/A")%

### Latency
- **P50**: ${LATENCY_P50:-N/A} ms
- **P99**: ${LATENCY_P99:-N/A} ms

### System Metrics
- **Pre-test metrics**: pre-test-metrics-${TIMESTAMP}.txt
- **Post-test metrics**: post-test-metrics-${TIMESTAMP}.txt

## Conclusion

$(if [ -n "${ACTUAL_TPS}" ] && (( $(echo "${ACTUAL_TPS} >= ${TARGET_TPS}" | bc -l 2>/dev/null || echo 0) )); then
    echo "✅ **SUCCESS**: System achieved ${ACTUAL_TPS} TPS, exceeding target of ${TARGET_TPS} TPS"
elif [ -n "${ACTUAL_TPS}" ]; then
    echo "⚠️ **PARTIAL**: System achieved ${ACTUAL_TPS} TPS, below target of ${TARGET_TPS} TPS"
else
    echo "❌ **FAILED**: Could not determine actual throughput"
fi)

## Files
- Test output: load-test-output-${TIMESTAMP}.log
- Summary: load-test-summary-${TIMESTAMP}.md
EOF

echo ""
echo "=========================================="
echo "Load Test Complete!"
echo "=========================================="
echo ""
echo "Results saved to: ${RESULTS_DIR}/"
echo ""
cat "${RESULTS_DIR}/load-test-summary-${TIMESTAMP}.md"

echo ""
echo "Next steps:"
echo "1. Check Flink dashboard: http://localhost:8081"
echo "2. Check Kafka UI: http://localhost:8080"
echo "3. Review results: ${RESULTS_DIR}/load-test-summary-${TIMESTAMP}.md"
echo ""
