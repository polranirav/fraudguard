package com.frauddetection.common.model;

/**
 * Categories of fraud detection rules.
 */
public enum RuleCategory {
    
    /**
     * Velocity and frequency-based rules.
     * Detects rapid succession of transactions.
     */
    VELOCITY,
    
    /**
     * Geographic location-based rules.
     * Includes impossible travel detection.
     */
    GEO,
    
    /**
     * Complex Event Processing patterns.
     * Multi-event sequence detection (e.g., "testing the waters").
     */
    CEP,
    
    /**
     * Machine learning model-based detection.
     */
    ML,
    
    /**
     * Vector embedding similarity-based detection.
     */
    EMBEDDING,
    
    /**
     * Device fingerprinting rules.
     */
    DEVICE,
    
    /**
     * Merchant reputation-based rules.
     */
    MERCHANT,
    
    /**
     * Blacklist/blocklist matches.
     */
    BLACKLIST,
    
    /**
     * Behavioral anomaly detection.
     */
    BEHAVIORAL,
    
    /**
     * Time-based rules (unusual hours, patterns).
     */
    TEMPORAL
}


