#!/bin/bash
#===============================================================================
# Hadoop HDFS to ADLS Gen2 Migration Script
#
# This script migrates data from on-premise Hadoop HDFS to Azure Data Lake
# Storage Gen2 using Hadoop DistCp (Distributed Copy).
#
# Usage: ./hadoop-to-adls-migration.sh [SOURCE_PATH] [DEST_PATH]
#
# Environment Variables Required:
#   - ADLS_ACCOUNT: ADLS Gen2 storage account name
#   - ADLS_CONTAINER: Container name in ADLS
#   - HDFS_NAMENODE: HDFS namenode address
#===============================================================================

set -euo pipefail

# Configuration
ADLS_ACCOUNT="${ADLS_ACCOUNT:-adlsfraud}"
ADLS_CONTAINER="${ADLS_CONTAINER:-raw}"
HDFS_NAMENODE="${HDFS_NAMENODE:-hdfs://namenode:8020}"

# Source and destination paths
SOURCE_PATH="${1:-/data/fraud/historical}"
DEST_PATH="${2:-hadoop/historical}"

# Full destination URL
DEST_URL="abfss://${ADLS_CONTAINER}@${ADLS_ACCOUNT}.dfs.core.windows.net/${DEST_PATH}"

# Log function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log "Starting Hadoop to ADLS migration"
log "Source: ${HDFS_NAMENODE}${SOURCE_PATH}"
log "Destination: $DEST_URL"

# Check if source exists
hadoop fs -test -d "${HDFS_NAMENODE}${SOURCE_PATH}"
if [ $? -ne 0 ]; then
    log "ERROR: Source path does not exist: ${HDFS_NAMENODE}${SOURCE_PATH}"
    exit 1
fi

# Get source size for monitoring
SOURCE_SIZE=$(hadoop fs -du -s -h "${HDFS_NAMENODE}${SOURCE_PATH}" | awk '{print $1, $2}')
log "Source size: $SOURCE_SIZE"

# Execute DistCp with optimal settings
log "Executing DistCp..."
hadoop distcp \
    -D mapreduce.job.queuename=migration \
    -D mapreduce.map.memory.mb=4096 \
    -D mapreduce.map.java.opts=-Xmx3072m \
    -D fs.azure.account.auth.type.${ADLS_ACCOUNT}.dfs.core.windows.net=OAuth \
    -D fs.azure.account.oauth.provider.type.${ADLS_ACCOUNT}.dfs.core.windows.net=org.apache.hadoop.fs.azurebfs.oauth2.ClientCredsTokenProvider \
    -D fs.azure.account.oauth2.client.id.${ADLS_ACCOUNT}.dfs.core.windows.net=${AZURE_CLIENT_ID} \
    -D fs.azure.account.oauth2.client.secret.${ADLS_ACCOUNT}.dfs.core.windows.net=${AZURE_CLIENT_SECRET} \
    -D fs.azure.account.oauth2.client.endpoint.${ADLS_ACCOUNT}.dfs.core.windows.net=https://login.microsoftonline.com/${AZURE_TENANT_ID}/oauth2/token \
    -update \
    -strategy dynamic \
    -bandwidth 100 \
    -m 50 \
    "${HDFS_NAMENODE}${SOURCE_PATH}" \
    "$DEST_URL"

# Check exit status
if [ $? -eq 0 ]; then
    log "SUCCESS: Migration completed"
    
    # Verify destination
    DEST_SIZE=$(hadoop fs -du -s -h "$DEST_URL" 2>/dev/null | awk '{print $1, $2}')
    log "Destination size: $DEST_SIZE"
    
    # Compare file counts
    SOURCE_COUNT=$(hadoop fs -count "${HDFS_NAMENODE}${SOURCE_PATH}" | awk '{print $2}')
    DEST_COUNT=$(hadoop fs -count "$DEST_URL" | awk '{print $2}')
    log "Source file count: $SOURCE_COUNT"
    log "Destination file count: $DEST_COUNT"
    
    if [ "$SOURCE_COUNT" -eq "$DEST_COUNT" ]; then
        log "VERIFIED: File counts match"
    else
        log "WARNING: File counts do not match. Manual verification recommended."
    fi
else
    log "ERROR: Migration failed"
    exit 1
fi

log "Migration complete."
