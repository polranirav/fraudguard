#!/bin/bash
#===============================================================================
# Kafka Topics Setup Script
#
# Creates and configures Kafka topics for the fraud detection platform.
# Topics are optimized for high-throughput streaming.
#
# Usage: ./create-topics.sh [BOOTSTRAP_SERVER]
#===============================================================================

set -euo pipefail

# Configuration
BOOTSTRAP_SERVER="${1:-localhost:9092}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-3}"
RETENTION_MS="${RETENTION_MS:-604800000}"  # 7 days

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log "Creating Kafka topics on: $BOOTSTRAP_SERVER"

# Topic: transactions-live
# High-volume topic for live transaction events
log "Creating topic: transactions-live"
kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic transactions-live \
    --partitions 12 \
    --replication-factor "$REPLICATION_FACTOR" \
    --config retention.ms="$RETENTION_MS" \
    --config cleanup.policy=delete \
    --config compression.type=snappy \
    --config max.message.bytes=1048576 \
    --config min.insync.replicas=2

# Topic: fraud-alerts
# Medium-volume topic for fraud alerts (downstream consumers)
log "Creating topic: fraud-alerts"
kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic fraud-alerts \
    --partitions 6 \
    --replication-factor "$REPLICATION_FACTOR" \
    --config retention.ms="$RETENTION_MS" \
    --config cleanup.policy=delete \
    --config compression.type=snappy \
    --config min.insync.replicas=2

# Topic: transactions-enriched
# Enriched transactions with Redis lookup data
log "Creating topic: transactions-enriched"
kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic transactions-enriched \
    --partitions 12 \
    --replication-factor "$REPLICATION_FACTOR" \
    --config retention.ms="$RETENTION_MS" \
    --config cleanup.policy=delete \
    --config compression.type=snappy

# Topic: transactions-dlq (Dead Letter Queue)
# Failed/problematic messages for investigation
log "Creating topic: transactions-dlq"
kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic transactions-dlq \
    --partitions 3 \
    --replication-factor "$REPLICATION_FACTOR" \
    --config retention.ms=2592000000 \
    --config cleanup.policy=delete

# List all topics
log "Listing all topics:"
kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list

# Describe topics
log "Topic details:"
for topic in transactions-live fraud-alerts transactions-enriched transactions-dlq; do
    log "=== $topic ==="
    kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --describe --topic "$topic"
done

log "Kafka topics setup complete."
