package com.frauddetection.processing.serialization;

import com.frauddetection.common.model.FraudAlert;
import com.frauddetection.common.util.JsonUtils;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Kafka RecordSerializationSchema for FraudAlert objects.
 * 
 * Serializes FraudAlert POJOs to JSON for publishing to the fraud-alerts topic.
 * Uses the customer ID as the partition key to ensure alerts for the same
 * customer are processed in order.
 */
public class FraudAlertSerializationSchema implements KafkaRecordSerializationSchema<FraudAlert> {

    private static final Logger LOG = LoggerFactory.getLogger(FraudAlertSerializationSchema.class);
    private static final long serialVersionUID = 1L;

    private final String topic;

    public FraudAlertSerializationSchema(String topic) {
        this.topic = topic;
    }

    @Nullable
    @Override
    public ProducerRecord<byte[], byte[]> serialize(FraudAlert alert, 
                                                     KafkaSinkContext context, 
                                                     Long timestamp) {
        if (alert == null) {
            LOG.warn("Attempted to serialize null alert");
            return null;
        }

        try {
            // Use customer ID as the key for partition affinity
            byte[] key = alert.getCustomerId() != null
                    ? alert.getCustomerId().getBytes()
                    : null;

            byte[] value = JsonUtils.toJsonBytes(alert);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Serializing alert {} for customer {}", 
                        alert.getAlertId(), alert.getCustomerId());
            }

            return new ProducerRecord<>(topic, null, timestamp, key, value);
        } catch (Exception e) {
            LOG.error("Failed to serialize fraud alert: {}", alert.getAlertId(), e);
            return null;
        }
    }
}


