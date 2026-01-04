package com.frauddetection.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.frauddetection.common.exception.FraudDetectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Utility class for JSON serialization and deserialization.
 * 
 * Uses a pre-configured ObjectMapper with support for Java 8 time types
 * and lenient parsing for forward compatibility.
 */
public final class JsonUtils {

    private static final Logger LOG = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

    private JsonUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Serializes an object to JSON string.
     *
     * @param object The object to serialize
     * @return JSON string representation
     * @throws FraudDetectionException if serialization fails
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize object to JSON: {}", object.getClass().getSimpleName(), e);
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.KAFKA_SERIALIZATION_ERROR,
                    "Failed to serialize object to JSON",
                    e);
        }
    }

    /**
     * Serializes an object to formatted (pretty-printed) JSON string.
     *
     * @param object The object to serialize
     * @return Formatted JSON string representation
     * @throws FraudDetectionException if serialization fails
     */
    public static String toPrettyJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize object to JSON: {}", object.getClass().getSimpleName(), e);
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.KAFKA_SERIALIZATION_ERROR,
                    "Failed to serialize object to JSON",
                    e);
        }
    }

    /**
     * Deserializes a JSON string to an object of the specified type.
     *
     * @param json  The JSON string
     * @param clazz The target class
     * @param <T>   The target type
     * @return Deserialized object
     * @throws FraudDetectionException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            LOG.error("Failed to deserialize JSON to {}: {}", clazz.getSimpleName(), json, e);
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.KAFKA_SERIALIZATION_ERROR,
                    "Failed to deserialize JSON to " + clazz.getSimpleName(),
                    e);
        }
    }

    /**
     * Deserializes a JSON byte array to an object of the specified type.
     *
     * @param jsonBytes The JSON bytes
     * @param clazz     The target class
     * @param <T>       The target type
     * @return Deserialized object
     * @throws FraudDetectionException if deserialization fails
     */
    public static <T> T fromJson(byte[] jsonBytes, Class<T> clazz) {
        if (jsonBytes == null || jsonBytes.length == 0) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(jsonBytes, clazz);
        } catch (IOException e) {
            LOG.error("Failed to deserialize JSON bytes to {}", clazz.getSimpleName(), e);
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.KAFKA_SERIALIZATION_ERROR,
                    "Failed to deserialize JSON bytes to " + clazz.getSimpleName(),
                    e);
        }
    }

    /**
     * Converts an object to JSON bytes.
     *
     * @param object The object to convert
     * @return JSON byte array
     * @throws FraudDetectionException if conversion fails
     */
    public static byte[] toJsonBytes(Object object) {
        if (object == null) {
            return new byte[0];
        }
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to convert object to JSON bytes: {}", object.getClass().getSimpleName(), e);
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.KAFKA_SERIALIZATION_ERROR,
                    "Failed to convert object to JSON bytes",
                    e);
        }
    }

    /**
     * Returns the shared ObjectMapper instance.
     * Use with caution - modifications will affect all users.
     *
     * @return The shared ObjectMapper
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}


