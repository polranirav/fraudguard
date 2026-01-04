package com.frauddetection.common.model;

/**
 * Enumeration of transaction channels indicating how the transaction was initiated.
 * Different channels may have different risk profiles and require different rules.
 */
public enum TransactionChannel {
    
    /**
     * Point-of-Sale terminal (card present).
     */
    POS,
    
    /**
     * Online/e-commerce transaction (card not present).
     */
    ONLINE,
    
    /**
     * ATM withdrawal or transaction.
     */
    ATM,
    
    /**
     * Mobile banking application.
     */
    MOBILE,
    
    /**
     * Telephone banking/call center.
     */
    TELEPHONE,
    
    /**
     * Branch/in-person transaction.
     */
    BRANCH,
    
    /**
     * API/programmatic access.
     */
    API
}


