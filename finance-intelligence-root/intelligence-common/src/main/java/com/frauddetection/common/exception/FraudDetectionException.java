package com.frauddetection.common.exception;

/**
 * Base exception for all fraud detection system errors.
 * 
 * All custom exceptions in the platform should extend this class
 * for consistent error handling across modules.
 */
public class FraudDetectionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public FraudDetectionException(String message) {
        super(message);
        this.errorCode = ErrorCode.GENERAL_ERROR;
    }

    public FraudDetectionException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.GENERAL_ERROR;
    }

    public FraudDetectionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FraudDetectionException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Error codes for categorizing exceptions.
     */
    public enum ErrorCode {
        GENERAL_ERROR("FD-001", "General fraud detection error"),
        KAFKA_CONNECTION_ERROR("FD-100", "Kafka connection failed"),
        KAFKA_SERIALIZATION_ERROR("FD-101", "Kafka serialization error"),
        REDIS_CONNECTION_ERROR("FD-200", "Redis connection failed"),
        REDIS_LOOKUP_ERROR("FD-201", "Redis lookup failed"),
        STATE_ACCESS_ERROR("FD-300", "Flink state access error"),
        PROCESSING_ERROR("FD-301", "Stream processing error"),
        SYNAPSE_CONNECTION_ERROR("FD-400", "Synapse connection failed"),
        SYNAPSE_WRITE_ERROR("FD-401", "Synapse write failed"),
        INVALID_TRANSACTION("FD-500", "Invalid transaction data"),
        RULE_EVALUATION_ERROR("FD-600", "Rule evaluation failed"),
        ENRICHMENT_ERROR("FD-700", "Data enrichment failed");

        private final String code;
        private final String description;

        ErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}


