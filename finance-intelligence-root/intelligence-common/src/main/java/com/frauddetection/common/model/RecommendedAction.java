package com.frauddetection.common.model;

/**
 * Recommended actions for handling detected fraud alerts.
 */
public enum RecommendedAction {
    
    /**
     * No action needed, continue monitoring.
     */
    MONITOR,
    
    /**
     * Flag for analyst review.
     */
    REVIEW,
    
    /**
     * Send notification to customer for verification.
     */
    NOTIFY_CUSTOMER,
    
    /**
     * Temporarily hold the transaction.
     */
    HOLD_TRANSACTION,
    
    /**
     * Decline the transaction.
     */
    DECLINE,
    
    /**
     * Block the card/account.
     */
    BLOCK_ACCOUNT,
    
    /**
     * Require additional authentication (step-up).
     */
    STEP_UP_AUTH,
    
    /**
     * Contact customer immediately.
     */
    URGENT_CONTACT
}


