#!/bin/bash
#===============================================================================
# Oracle to ADLS Gen2 Incremental Import Script using Apache Sqoop
#
# This script performs incremental data imports from Oracle Database to
# Azure Data Lake Storage Gen2 using Apache Sqoop.
#
# Usage: ./sqoop-oracle-import.sh [TABLE_NAME] [LAST_VALUE]
#
# Environment Variables Required:
#   - ORACLE_CONNECTION_STRING: JDBC connection string for Oracle
#   - ORACLE_USER: Oracle database username
#   - ADLS_ACCOUNT: ADLS Gen2 storage account name
#   - ADLS_CONTAINER: Container name in ADLS
#===============================================================================

set -euo pipefail

# Configuration
ORACLE_CONNECTION_STRING="${ORACLE_CONNECTION_STRING:-jdbc:oracle:thin:@//oracle-host:1521/ORCL}"
ORACLE_USER="${ORACLE_USER:-fraud_user}"
ADLS_ACCOUNT="${ADLS_ACCOUNT:-adlsfraud}"
ADLS_CONTAINER="${ADLS_CONTAINER:-raw}"

# Table to import (can be overridden by argument)
TABLE_NAME="${1:-TRANSACTIONS}"

# Last imported value (for incremental imports)
LAST_VALUE="${2:-}"

# Determine check column based on table
case $TABLE_NAME in
    TRANSACTIONS)
        CHECK_COLUMN="TRANSACTION_TIMESTAMP"
        SPLIT_BY="TRANSACTION_ID"
        ;;
    CUSTOMERS)
        CHECK_COLUMN="LAST_MODIFIED"
        SPLIT_BY="CUSTOMER_ID"
        ;;
    MERCHANTS)
        CHECK_COLUMN="UPDATED_AT"
        SPLIT_BY="MERCHANT_ID"
        ;;
    *)
        CHECK_COLUMN="LAST_MODIFIED"
        SPLIT_BY="ID"
        ;;
esac

# Target path in ADLS Gen2
TARGET_DIR="abfss://${ADLS_CONTAINER}@${ADLS_ACCOUNT}.dfs.core.windows.net/oracle/${TABLE_NAME,,}"

# Log function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log "Starting Sqoop import for table: $TABLE_NAME"
log "Oracle connection: $ORACLE_CONNECTION_STRING"
log "Target: $TARGET_DIR"

# Build Sqoop command
SQOOP_CMD="sqoop import \
    --connect '${ORACLE_CONNECTION_STRING}' \
    --username '${ORACLE_USER}' \
    --password-file hdfs:///user/sqoop/.oracle_password \
    --table '${TABLE_NAME}' \
    --target-dir '${TARGET_DIR}' \
    --as-parquetfile \
    --compress \
    --compression-codec snappy \
    --split-by '${SPLIT_BY}' \
    --num-mappers 8 \
    --delete-target-dir"

# Add incremental options if last value provided
if [ -n "$LAST_VALUE" ]; then
    log "Incremental import from: $LAST_VALUE"
    SQOOP_CMD="$SQOOP_CMD \
        --incremental lastmodified \
        --check-column '${CHECK_COLUMN}' \
        --last-value '${LAST_VALUE}'"
else
    log "Full table import (no last value provided)"
fi

# Execute Sqoop import
log "Executing Sqoop command..."
eval $SQOOP_CMD

# Check exit status
if [ $? -eq 0 ]; then
    log "SUCCESS: Import completed for $TABLE_NAME"
    
    # Get the current timestamp for next incremental import
    CURRENT_TIMESTAMP=$(date -u '+%Y-%m-%d %H:%M:%S')
    log "Next incremental import should use: --last-value '$CURRENT_TIMESTAMP'"
    
    # Store the last value for next run
    echo "$CURRENT_TIMESTAMP" > "/tmp/sqoop_last_value_${TABLE_NAME}.txt"
else
    log "ERROR: Import failed for $TABLE_NAME"
    exit 1
fi

log "Import complete."
