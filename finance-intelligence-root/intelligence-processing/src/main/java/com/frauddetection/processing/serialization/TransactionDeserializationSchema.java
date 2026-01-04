package com.frauddetection.processing.serialization;

import com.frauddetection.common.model.Transaction;
import com.frauddetection.common.util.JsonUtils;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Flink DeserializationSchema for Transaction objects from Kafka.
 * 
 * Deserializes JSON-encoded transaction messages into Transaction POJOs
 * for processing in the Flink pipeline.
 */
public class TransactionDeserializationSchema implements DeserializationSchema<Transaction> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionDeserializationSchema.class);
    private static final long serialVersionUID = 1L;

    @Override
    public Transaction deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            LOG.warn("Received null or empty message");
            return null;
        }

        try {
            Transaction transaction = JsonUtils.fromJson(message, Transaction.class);
            
            if (transaction != null && LOG.isDebugEnabled()) {
                LOG.debug("Deserialized transaction: {}", transaction.getTransactionId());
            }
            
            return transaction;
        } catch (Exception e) {
            LOG.error("Failed to deserialize transaction: {}", new String(message), e);
            // Return null to skip malformed messages rather than failing the job
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(Transaction nextElement) {
        // Kafka streams are unbounded - never end of stream
        return false;
    }

    @Override
    public TypeInformation<Transaction> getProducedType() {
        return TypeInformation.of(Transaction.class);
    }
}


