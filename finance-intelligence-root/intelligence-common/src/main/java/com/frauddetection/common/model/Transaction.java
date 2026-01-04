package com.frauddetection.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Core Transaction POJO representing a credit card transaction event.
 * 
 * This class is designed to be Flink-serializable by following POJO conventions:
 * - Public fields or public getters/setters
 * - Default (no-argument) constructor
 * - Implements Serializable
 * 
 * Used across all modules for consistent transaction representation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the transaction.
     */
    private String transactionId;

    /**
     * Customer/account identifier (used as key for stateful processing).
     */
    private String customerId;

    /**
     * Credit card number (masked in logs, protected by Purview sensitivity labels).
     */
    private String cardNumber;

    /**
     * Transaction amount in the transaction currency.
     */
    private BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g., "USD", "CAD", "EUR").
     */
    private String currency;

    /**
     * Merchant identifier where the transaction occurred.
     */
    private String merchantId;

    /**
     * Merchant category code (MCC) for industry classification.
     */
    private String merchantCategoryCode;

    /**
     * Name of the merchant.
     */
    private String merchantName;

    /**
     * Geographic location of the transaction.
     */
    private Location location;

    /**
     * Device information used for the transaction.
     */
    private DeviceInfo deviceInfo;

    /**
     * Transaction type: PURCHASE, WITHDRAWAL, TRANSFER, REFUND.
     */
    private TransactionType transactionType;

    /**
     * Channel through which transaction was initiated: POS, ONLINE, ATM, MOBILE.
     */
    private TransactionChannel channel;

    /**
     * Event timestamp in UTC (event-time for Flink watermarking).
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant eventTime;

    /**
     * Processing timestamp (wall-clock time when event was received).
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant processingTime;

    /**
     * Indicates if the transaction was approved or declined by the issuer.
     */
    private Boolean approved;

    /**
     * Response code from the payment network.
     */
    private String responseCode;

    /**
     * Returns the event time as epoch milliseconds for Flink timestamp assignment.
     */
    public long getEventTimeMillis() {
        return eventTime != null ? eventTime.toEpochMilli() : System.currentTimeMillis();
    }

    /**
     * Masks the card number for logging purposes.
     * Shows only the last 4 digits: **** **** **** 1234
     */
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    @Override
    public String toString() {
        return String.format("Transaction{id=%s, customer=%s, card=%s, amount=%s %s, merchant=%s, time=%s}",
                transactionId,
                customerId,
                getMaskedCardNumber(),
                amount,
                currency,
                merchantName,
                eventTime);
    }
}


