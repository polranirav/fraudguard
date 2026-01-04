#!/bin/bash
# Kafka Load Testing Script
# Tests system capability to handle 50K+ transactions per second

set -e

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
TOPIC=${TOPIC:-transactions-live}
TARGET_TPS=${TARGET_TPS:-50000}
DURATION_SECONDS=${DURATION_SECONDS:-60}
MESSAGE_SIZE=${MESSAGE_SIZE:-1024}

echo "=========================================="
echo "Kafka Load Test - Target: ${TARGET_TPS} TPS"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  Kafka: ${KAFKA_BOOTSTRAP_SERVERS}"
echo "  Topic: ${TOPIC}"
echo "  Target TPS: ${TARGET_TPS}"
echo "  Duration: ${DURATION_SECONDS} seconds"
echo "  Message Size: ${MESSAGE_SIZE} bytes"
echo ""

# Check if kafka-producer-perf-test is available
if command -v kafka-producer-perf-test &> /dev/null; then
    echo "Using kafka-producer-perf-test..."
    kafka-producer-perf-test \
        --topic ${TOPIC} \
        --num-records $((TARGET_TPS * DURATION_SECONDS)) \
        --record-size ${MESSAGE_SIZE} \
        --throughput ${TARGET_TPS} \
        --producer-props bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS} \
        --producer-props acks=1 \
        --producer-props batch.size=16384 \
        --producer-props linger.ms=5
elif docker ps | grep -q fraud-kafka; then
    echo "Using Docker kafka-producer-perf-test..."
    docker exec fraud-kafka kafka-producer-perf-test \
        --topic ${TOPIC} \
        --num-records $((TARGET_TPS * DURATION_SECONDS)) \
        --record-size ${MESSAGE_SIZE} \
        --throughput ${TARGET_TPS} \
        --producer-props bootstrap.servers=localhost:9092 \
        --producer-props acks=1 \
        --producer-props batch.size=16384 \
        --producer-props linger.ms=5
else
    echo "ERROR: kafka-producer-perf-test not found and Kafka container not running"
    echo "Please install Kafka tools or start Docker containers"
    exit 1
fi

echo ""
echo "=========================================="
echo "Load Test Complete"
echo "=========================================="
echo ""
echo "Check Flink dashboard for processing metrics:"
echo "  http://localhost:8081"
echo ""
