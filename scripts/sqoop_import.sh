#!/bin/bash
#
# Sqoop Import Script for Oracle to ADLS Gen2 Migration
# Real-Time Financial Fraud Detection & Legacy Migration Platform
#
# This script performs incremental imports from Oracle databases
# to Azure Data Lake Storage Gen2 in Parquet format.
#

set -e

# ============================================================
# CONFIGURATION
# ============================================================

# Oracle Connection
ORACLE_CONN="${ORACLE_CONN:-jdbc:oracle:thin:@//oracle-host:1521/ORCL}"
ORACLE_USER="${ORACLE_USER:-etl_user}"
ORACLE_PWD_FILE="${ORACLE_PWD_FILE:-/secure/oracle.pwd}"

# Azure ADLS Gen2 Target
ADLS_ACCOUNT="${ADLS_ACCOUNT:-stfrauddetectiondl}"
ADLS_CONTAINER="${ADLS_CONTAINER:-raw}"
ADLS_TARGET="abfss://${ADLS_CONTAINER}@${ADLS_ACCOUNT}.dfs.core.windows.net/oracle_migration"

# Import Configuration
NUM_MAPPERS="${NUM_MAPPERS:-8}"
BATCH_SIZE="${BATCH_SIZE:-10000}"

# Logging
LOG_DIR="/var/log/sqoop"
LOG_FILE="${LOG_DIR}/sqoop_import_$(date +%Y%m%d_%H%M%S).log"

# ============================================================
# FUNCTIONS
# ============================================================

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $1" | tee -a "$LOG_FILE" >&2
    exit 1
}

get_last_value() {
    local table=$1
    local file="${LOG_DIR}/.last_value_${table}"
    if [[ -f "$file" ]]; then
        cat "$file"
    else
        echo "1900-01-01 00:00:00"
    fi
}

save_last_value() {
    local table=$1
    local value=$2
    echo "$value" > "${LOG_DIR}/.last_value_${table}"
}

# ============================================================
# MAIN IMPORT FUNCTION
# ============================================================

import_table() {
    local table=$1
    local check_column=$2
    local split_column=$3
    
    log "Starting import for table: $table"
    
    local last_value
    last_value=$(get_last_value "$table")
    log "Last imported value: $last_value"
    
    local target_dir="${ADLS_TARGET}/${table,,}"
    
    sqoop import \
        --connect "$ORACLE_CONN" \
        --username "$ORACLE_USER" \
        --password-file "$ORACLE_PWD_FILE" \
        --table "$table" \
        --target-dir "$target_dir" \
        --incremental lastmodified \
        --check-column "$check_column" \
        --last-value "$last_value" \
        --split-by "$split_column" \
        --as-parquetfile \
        --compress \
        --compression-codec org.apache.hadoop.io.compress.SnappyCodec \
        --num-mappers "$NUM_MAPPERS" \
        --fetch-size "$BATCH_SIZE" \
        --null-string '\\N' \
        --null-non-string '\\N' \
        2>&1 | tee -a "$LOG_FILE"
    
    if [[ ${PIPESTATUS[0]} -eq 0 ]]; then
        # Update last value to current timestamp
        local new_value
        new_value=$(date '+%Y-%m-%d %H:%M:%S')
        save_last_value "$table" "$new_value"
        log "Import completed successfully for $table. New last value: $new_value"
    else
        error "Import failed for table $table"
    fi
}

# ============================================================
# BULK IMPORT (Initial Load)
# ============================================================

bulk_import_table() {
    local table=$1
    local split_column=$2
    
    log "Starting BULK import for table: $table"
    
    local target_dir="${ADLS_TARGET}/${table,,}_bulk"
    
    sqoop import \
        --connect "$ORACLE_CONN" \
        --username "$ORACLE_USER" \
        --password-file "$ORACLE_PWD_FILE" \
        --table "$table" \
        --target-dir "$target_dir" \
        --split-by "$split_column" \
        --as-parquetfile \
        --compress \
        --compression-codec org.apache.hadoop.io.compress.SnappyCodec \
        --num-mappers "$NUM_MAPPERS" \
        --fetch-size "$BATCH_SIZE" \
        --delete-target-dir \
        2>&1 | tee -a "$LOG_FILE"
    
    if [[ ${PIPESTATUS[0]} -eq 0 ]]; then
        log "Bulk import completed successfully for $table"
    else
        error "Bulk import failed for table $table"
    fi
}

# ============================================================
# VALIDATION
# ============================================================

validate_import() {
    local table=$1
    
    log "Validating import for table: $table"
    
    # Count rows in Oracle
    local oracle_count
    oracle_count=$(sqoop eval \
        --connect "$ORACLE_CONN" \
        --username "$ORACLE_USER" \
        --password-file "$ORACLE_PWD_FILE" \
        --query "SELECT COUNT(*) FROM $table" \
        2>/dev/null | grep -E '^[0-9]+$' | head -1)
    
    log "Oracle row count for $table: $oracle_count"
    
    # Parquet row count would require additional tooling
    # In production, use Azure Synapse or Spark to validate
    
    echo "$oracle_count"
}

# ============================================================
# MAIN
# ============================================================

main() {
    mkdir -p "$LOG_DIR"
    
    log "=================================================="
    log "Starting Sqoop Import Process"
    log "=================================================="
    log "Oracle Connection: $ORACLE_CONN"
    log "ADLS Target: $ADLS_TARGET"
    log "Mappers: $NUM_MAPPERS"
    log "=================================================="
    
    case "${1:-incremental}" in
        incremental)
            # Incremental imports for daily runs
            import_table "TRANSACTION_FACT" "LAST_MODIFIED" "TRANSACTION_ID"
            import_table "FRAUD_ALERT" "ALERT_TIME" "ALERT_ID"
            ;;
        bulk)
            # Bulk imports for initial load
            bulk_import_table "TRANSACTION_FACT" "TRANSACTION_ID"
            bulk_import_table "CUSTOMER_DIM" "CUSTOMER_ID"
            bulk_import_table "MERCHANT_DIM" "MERCHANT_ID"
            bulk_import_table "FRAUD_ALERT" "ALERT_ID"
            ;;
        validate)
            # Validation mode
            validate_import "TRANSACTION_FACT"
            ;;
        *)
            echo "Usage: $0 {incremental|bulk|validate}"
            exit 1
            ;;
    esac
    
    log "=================================================="
    log "Sqoop Import Process Completed"
    log "=================================================="
}

main "$@"


