package com.frauddetection.common.model;

/**
 * Enumeration of transaction types for categorization and rule application.
 */
public enum TransactionType {
    
    /**
     * Standard purchase transaction at a merchant.
     */
    PURCHASE,
    
    /**
     * Cash withdrawal from ATM or bank.
     */
    WITHDRAWAL,
    
    /**
     * Money transfer to another account.
     */
    TRANSFER,
    
    /**
     * Refund/return transaction.
     */
    REFUND,
    
    /**
     * Recurring payment/subscription.
     */
    RECURRING,
    
    /**
     * Pre-authorization hold.
     */
    PRE_AUTH,
    
    /**
     * Balance inquiry (no funds movement).
     */
    BALANCE_INQUIRY,
    
    /**
     * Payment to credit card balance.
     */
    PAYMENT
}


