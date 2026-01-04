package com.frauddetection.persistence.sink;

import com.frauddetection.common.exception.FraudDetectionException;
import com.frauddetection.common.model.Transaction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Flink Sink Function for writing transactions to Azure Synapse Analytics.
 * 
 * Uses JDBC batch inserts for high-throughput writing to the Synapse
 * dedicated SQL pool. Implements batching to optimize network usage
 * and reduce transaction overhead.
 * 
 * Connection is managed using Azure Managed Identity for security.
 */
public class SynapseSinkFunction extends RichSinkFunction<Transaction> {

    private static final Logger LOG = LoggerFactory.getLogger(SynapseSinkFunction.class);

    private static final int BATCH_SIZE = 1000;
    private static final int BATCH_INTERVAL_MS = 5000;

    private final String jdbcUrl;
    private final String tableName;

    private transient Connection connection;
    private transient PreparedStatement insertStatement;
    private transient List<Transaction> batch;
    private transient long lastFlushTime;

    public SynapseSinkFunction(String jdbcUrl, String tableName) {
        this.jdbcUrl = jdbcUrl;
        this.tableName = tableName;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        LOG.info("Opening Synapse sink connection to table: {}", tableName);

        // Initialize batch buffer
        this.batch = new ArrayList<>(BATCH_SIZE);
        this.lastFlushTime = System.currentTimeMillis();

        // Establish database connection
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            connection.setAutoCommit(false);

            // Prepare the insert statement
            String insertSql = buildInsertStatement();
            insertStatement = connection.prepareStatement(insertSql);

            LOG.info("Synapse sink connection established");
        } catch (SQLException e) {
            LOG.error("Failed to connect to Synapse: {}", e.getMessage());
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.SYNAPSE_CONNECTION_ERROR,
                    "Failed to establish Synapse connection",
                    e);
        }
    }

    @Override
    public void invoke(Transaction transaction, Context context) throws Exception {
        if (transaction == null) {
            return;
        }

        batch.add(transaction);

        // Flush if batch size reached or interval exceeded
        long currentTime = System.currentTimeMillis();
        if (batch.size() >= BATCH_SIZE || (currentTime - lastFlushTime) >= BATCH_INTERVAL_MS) {
            flush();
        }
    }

    /**
     * Flushes the current batch to Synapse.
     */
    private void flush() throws SQLException {
        if (batch.isEmpty()) {
            return;
        }

        try {
            for (Transaction txn : batch) {
                setStatementParameters(insertStatement, txn);
                insertStatement.addBatch();
            }

            int[] results = insertStatement.executeBatch();
            connection.commit();

            int successCount = 0;
            for (int result : results) {
                if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                    successCount++;
                }
            }

            LOG.info("Flushed {} transactions to Synapse ({} successful)", 
                    batch.size(), successCount);

        } catch (SQLException e) {
            LOG.error("Batch insert failed, attempting rollback: {}", e.getMessage());
            connection.rollback();
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.SYNAPSE_WRITE_ERROR,
                    "Failed to write batch to Synapse",
                    e);
        } finally {
            batch.clear();
            lastFlushTime = System.currentTimeMillis();
        }
    }

    /**
     * Builds the INSERT statement for the transactions table.
     */
    private String buildInsertStatement() {
        return String.format(
                "INSERT INTO %s (" +
                "transaction_id, customer_id, card_number_hash, amount, currency, " +
                "merchant_id, merchant_name, merchant_category_code, " +
                "latitude, longitude, city, country_code, " +
                "device_id, device_type, transaction_type, channel, " +
                "event_time, processing_time, approved, response_code" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tableName);
    }

    /**
     * Sets the parameters on the prepared statement from a Transaction.
     */
    private void setStatementParameters(PreparedStatement stmt, Transaction txn) throws SQLException {
        int idx = 1;
        
        stmt.setString(idx++, txn.getTransactionId());
        stmt.setString(idx++, txn.getCustomerId());
        stmt.setString(idx++, hashCardNumber(txn.getCardNumber()));
        stmt.setBigDecimal(idx++, txn.getAmount());
        stmt.setString(idx++, txn.getCurrency());
        stmt.setString(idx++, txn.getMerchantId());
        stmt.setString(idx++, txn.getMerchantName());
        stmt.setString(idx++, txn.getMerchantCategoryCode());

        // Location
        if (txn.getLocation() != null) {
            stmt.setDouble(idx++, txn.getLocation().getLatitude());
            stmt.setDouble(idx++, txn.getLocation().getLongitude());
            stmt.setString(idx++, txn.getLocation().getCity());
            stmt.setString(idx++, txn.getLocation().getCountryCode());
        } else {
            stmt.setNull(idx++, Types.DOUBLE);
            stmt.setNull(idx++, Types.DOUBLE);
            stmt.setNull(idx++, Types.VARCHAR);
            stmt.setNull(idx++, Types.VARCHAR);
        }

        // Device
        if (txn.getDeviceInfo() != null) {
            stmt.setString(idx++, txn.getDeviceInfo().getDeviceId());
            stmt.setString(idx++, txn.getDeviceInfo().getDeviceType());
        } else {
            stmt.setNull(idx++, Types.VARCHAR);
            stmt.setNull(idx++, Types.VARCHAR);
        }

        stmt.setString(idx++, txn.getTransactionType() != null ? 
                txn.getTransactionType().name() : null);
        stmt.setString(idx++, txn.getChannel() != null ? 
                txn.getChannel().name() : null);

        // Timestamps
        stmt.setTimestamp(idx++, txn.getEventTime() != null ? 
                Timestamp.from(txn.getEventTime()) : null);
        stmt.setTimestamp(idx++, txn.getProcessingTime() != null ? 
                Timestamp.from(txn.getProcessingTime()) : null);

        stmt.setBoolean(idx++, Boolean.TRUE.equals(txn.getApproved()));
        stmt.setString(idx, txn.getResponseCode());
    }

    /**
     * Hashes the card number for secure storage.
     * In production, use a proper one-way hash with salt.
     */
    private String hashCardNumber(String cardNumber) {
        if (cardNumber == null) {
            return null;
        }
        // Simple hash for demonstration - use proper hashing in production
        return String.valueOf(cardNumber.hashCode());
    }

    @Override
    public void close() throws Exception {
        // Flush any remaining records
        if (batch != null && !batch.isEmpty()) {
            try {
                flush();
            } catch (SQLException e) {
                LOG.warn("Error flushing remaining batch on close: {}", e.getMessage());
            }
        }

        // Close resources
        if (insertStatement != null) {
            try {
                insertStatement.close();
            } catch (SQLException e) {
                LOG.warn("Error closing statement: {}", e.getMessage());
            }
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.warn("Error closing connection: {}", e.getMessage());
            }
        }

        LOG.info("Synapse sink closed");
        super.close();
    }
}


