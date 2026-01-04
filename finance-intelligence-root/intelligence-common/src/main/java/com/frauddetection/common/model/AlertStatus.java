package com.frauddetection.common.model;

/**
 * Status tracking for fraud alerts through the investigation lifecycle.
 */
public enum AlertStatus {
    
    /**
     * Alert has been generated and is awaiting review.
     */
    NEW,
    
    /**
     * Alert has been assigned to an analyst.
     */
    ASSIGNED,
    
    /**
     * Alert is under active investigation.
     */
    INVESTIGATING,
    
    /**
     * Additional information has been requested.
     */
    PENDING_INFO,
    
    /**
     * Alert has been confirmed as fraud.
     */
    CONFIRMED_FRAUD,
    
    /**
     * Alert was a false positive.
     */
    FALSE_POSITIVE,
    
    /**
     * Alert has been resolved and closed.
     */
    CLOSED,
    
    /**
     * Alert has been escalated to senior team.
     */
    ESCALATED
}


