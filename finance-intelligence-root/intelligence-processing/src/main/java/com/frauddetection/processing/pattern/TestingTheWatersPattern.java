package com.frauddetection.processing.pattern;

import com.frauddetection.common.model.*;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CEP Pattern for detecting "Testing the Waters" fraud pattern.
 * 
 * This pattern identifies a common fraud behavior where a criminal:
 * 1. Makes several very small transactions (< $1.00) to verify a stolen card is active
 * 2. Followed by a large transaction (> $500) to maximize the take
 * 
 * The pattern uses Flink's CEP library to match this sequence within a 10-minute window.
 * 
 * Example sequence detected:
 *   $0.50 -> $0.75 -> $0.25 -> $750.00 (within 10 minutes)
 * 
 * Reference: Common credit card fraud pattern analysis
 */
public class TestingTheWatersPattern {

    private static final BigDecimal SMALL_TRANSACTION_THRESHOLD = new BigDecimal("1.00");
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("500.00");
    private static final int MINIMUM_SMALL_TRANSACTIONS = 3;
    private static final int WINDOW_MINUTES = 10;

    /**
     * Creates the "Testing the Waters" CEP pattern.
     * 
     * Pattern definition:
     * - "small_txns": 3 or more consecutive transactions under $1.00
     * - "large_txn": Followed by one transaction over $500
     * - Window: 10 minutes
     * 
     * @return The configured CEP pattern
     */
    public static Pattern<Transaction, ?> getPattern() {
        return Pattern.<Transaction>begin("small_txns")
                .where(new SmallTransactionCondition())
                .timesOrMore(MINIMUM_SMALL_TRANSACTIONS)
                .consecutive()
                .followedBy("large_txn")
                .where(new LargeTransactionCondition())
                .within(Time.minutes(WINDOW_MINUTES));
    }

    /**
     * Creates a fraud alert from a matched pattern.
     * 
     * @param largeTransaction The large transaction that completed the pattern
     * @param smallTransactions The preceding small transactions
     * @return A FraudAlert describing the detected pattern
     */
    public static FraudAlert createAlert(Transaction largeTransaction, 
                                         List<Transaction> smallTransactions) {
        // Calculate total amount of small transactions
        BigDecimal smallTxnTotal = smallTransactions != null
                ? smallTransactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;

        int smallTxnCount = smallTransactions != null ? smallTransactions.size() : 0;

        // Build trigger reason
        String triggerReason = String.format(
                "Testing the Waters pattern detected: %d small transactions totaling $%.2f " +
                "followed by large transaction of $%.2f within %d minutes",
                smallTxnCount,
                smallTxnTotal.doubleValue(),
                largeTransaction.getAmount().doubleValue(),
                WINDOW_MINUTES);

        // Create triggered rule
        TriggeredRule rule = TriggeredRule.builder()
                .ruleId("CEP-001")
                .ruleName("Testing the Waters Pattern")
                .category(RuleCategory.CEP)
                .triggerReason(triggerReason)
                .riskContribution(0.85) // High risk for this pattern
                .weight(0.6)
                .threshold(LARGE_TRANSACTION_THRESHOLD.doubleValue())
                .actualValue(largeTransaction.getAmount().doubleValue())
                .build();

        List<TriggeredRule> rules = new ArrayList<>();
        rules.add(rule);

        Instant alertTime = Instant.now();
        Instant transactionTime = largeTransaction.getEventTime();
        long latencyMs = alertTime.toEpochMilli() - transactionTime.toEpochMilli();

        return FraudAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .transactionId(largeTransaction.getTransactionId())
                .customerId(largeTransaction.getCustomerId())
                .severity(AlertSeverity.HIGH)
                .riskScore(0.85)
                .triggeredRules(rules)
                .transactionAmount(largeTransaction.getAmount())
                .currency(largeTransaction.getCurrency())
                .merchantName(largeTransaction.getMerchantName())
                .location(largeTransaction.getLocation())
                .recommendedAction(RecommendedAction.HOLD_TRANSACTION)
                .alertTime(alertTime)
                .transactionTime(transactionTime)
                .processingLatencyMs(latencyMs)
                .status(AlertStatus.NEW)
                .build();
    }

    /**
     * Condition for identifying small "test" transactions.
     */
    private static class SmallTransactionCondition extends SimpleCondition<Transaction> {
        @Override
        public boolean filter(Transaction transaction) {
            if (transaction == null || transaction.getAmount() == null) {
                return false;
            }
            // Transaction must be under the small threshold and a purchase
            return transaction.getAmount().compareTo(SMALL_TRANSACTION_THRESHOLD) < 0
                    && transaction.getTransactionType() == TransactionType.PURCHASE;
        }
    }

    /**
     * Condition for identifying large "cash-out" transactions.
     */
    private static class LargeTransactionCondition extends SimpleCondition<Transaction> {
        @Override
        public boolean filter(Transaction transaction) {
            if (transaction == null || transaction.getAmount() == null) {
                return false;
            }
            // Transaction must be over the large threshold
            return transaction.getAmount().compareTo(LARGE_TRANSACTION_THRESHOLD) > 0;
        }
    }
}


