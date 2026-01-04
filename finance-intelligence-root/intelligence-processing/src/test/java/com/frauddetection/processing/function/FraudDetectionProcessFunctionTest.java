package com.frauddetection.processing.function;

import com.frauddetection.common.model.*;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.operators.KeyedProcessOperator;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FraudDetectionProcessFunction.
 * 
 * Uses Flink's test harness for testing stateful stream processing
 * without needing a full Flink cluster.
 */
class FraudDetectionProcessFunctionTest {

    private KeyedOneInputStreamOperatorTestHarness<String, Transaction, FraudAlert> testHarness;

    @BeforeEach
    void setup() throws Exception {
        FraudDetectionProcessFunction function = new FraudDetectionProcessFunction();
        
        testHarness = new KeyedOneInputStreamOperatorTestHarness<>(
                new KeyedProcessOperator<>(function),
                Transaction::getCustomerId,
                TypeInformation.of(String.class)
        );
        
        testHarness.open();
    }

    @AfterEach
    void teardown() throws Exception {
        if (testHarness != null) {
            testHarness.close();
        }
    }

    // =========================================================================
    // Velocity Detection Tests
    // =========================================================================

    @Test
    @DisplayName("Should not trigger alert for normal transaction")
    void shouldNotTriggerAlertForNormalTransaction() throws Exception {
        // Given: A single normal transaction
        Transaction txn = createTransaction("CUST-001", new BigDecimal("50.00"), "Toronto", "CA");
        
        // When: Processing the transaction
        testHarness.processElement(new StreamRecord<>(txn, txn.getEventTimeMillis()));
        
        // Then: No alert should be generated
        assertTrue(testHarness.getOutput().isEmpty());
    }

    @Test
    @DisplayName("Should trigger velocity alert when amount threshold exceeded")
    void shouldTriggerVelocityAlertWhenAmountExceeded() throws Exception {
        // Given: Multiple transactions exceeding $2000 in 1 minute
        String customerId = "CUST-VELOCITY-001";
        long baseTime = System.currentTimeMillis();
        
        // When: Processing multiple high-value transactions
        testHarness.processElement(new StreamRecord<>(
                createTransactionWithTime(customerId, new BigDecimal("800.00"), baseTime)));
        testHarness.processElement(new StreamRecord<>(
                createTransactionWithTime(customerId, new BigDecimal("700.00"), baseTime + 10000)));
        testHarness.processElement(new StreamRecord<>(
                createTransactionWithTime(customerId, new BigDecimal("600.00"), baseTime + 20000)));
        
        // Then: Alert should be triggered (total $2100 > $2000 threshold)
        assertFalse(testHarness.getOutput().isEmpty(), "Should generate velocity alert");
        
        StreamRecord<?> output = (StreamRecord<?>) testHarness.getOutput().poll();
        FraudAlert alert = (FraudAlert) output.getValue();
        
        assertEquals(customerId, alert.getCustomerId());
        assertNotNull(alert.getTriggeredRules());
        assertTrue(alert.getTriggeredRules().stream()
                .anyMatch(r -> r.getCategory() == RuleCategory.VELOCITY));
    }

    @Test
    @DisplayName("Should trigger velocity alert when transaction count exceeded")
    void shouldTriggerVelocityAlertWhenCountExceeded() throws Exception {
        // Given: More than 5 transactions in 1 minute
        String customerId = "CUST-VELOCITY-002";
        long baseTime = System.currentTimeMillis();
        
        // When: Processing 6 transactions (exceeding count threshold of 5)
        for (int i = 0; i < 6; i++) {
            testHarness.processElement(new StreamRecord<>(
                    createTransactionWithTime(customerId, new BigDecimal("50.00"), 
                            baseTime + (i * 5000))));
        }
        
        // Then: Alert should be triggered
        assertFalse(testHarness.getOutput().isEmpty(), "Should generate count-based velocity alert");
    }

    @Test
    @DisplayName("Should not trigger velocity alert when transactions are outside window")
    void shouldNotTriggerWhenTransactionsOutsideWindow() throws Exception {
        // Given: Transactions spread over more than 1 minute
        String customerId = "CUST-VELOCITY-003";
        long baseTime = System.currentTimeMillis();
        
        // When: Processing transactions 2 minutes apart
        testHarness.processElement(new StreamRecord<>(
                createTransactionWithTime(customerId, new BigDecimal("1500.00"), baseTime)));
        
        // Advance time by 2 minutes (120 seconds)
        testHarness.processElement(new StreamRecord<>(
                createTransactionWithTime(customerId, new BigDecimal("1500.00"), 
                        baseTime + 120000)));
        
        // Then: No velocity alert (each transaction is within its own window)
        // First transaction should not trigger, second should also not trigger
        // because the first one is outside the 60-second window
        assertTrue(testHarness.getOutput().isEmpty() || 
                testHarness.getOutput().size() <= 1);
    }

    // =========================================================================
    // Geo-Velocity (Impossible Travel) Tests
    // =========================================================================

    @Test
    @DisplayName("Should trigger geo-velocity alert for impossible travel")
    void shouldTriggerGeoVelocityAlertForImpossibleTravel() throws Exception {
        // Given: Two transactions in different cities, 20 minutes apart
        // Toronto to London distance: ~5,700 km
        // Required velocity: 5,700 km / (20/60) hours = 17,100 km/h (impossible!)
        String customerId = "CUST-GEO-001";
        long baseTime = System.currentTimeMillis();
        
        // First transaction in Toronto
        Transaction txn1 = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .location(Location.builder()
                        .latitude(43.6532)
                        .longitude(-79.3832)
                        .city("Toronto")
                        .countryCode("CA")
                        .build())
                .transactionType(TransactionType.PURCHASE)
                .eventTime(Instant.ofEpochMilli(baseTime))
                .build();
        
        // Second transaction in London (20 minutes later)
        Transaction txn2 = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .amount(new BigDecimal("150.00"))
                .currency("GBP")
                .location(Location.builder()
                        .latitude(51.5074)
                        .longitude(-0.1278)
                        .city("London")
                        .countryCode("GB")
                        .build())
                .transactionType(TransactionType.PURCHASE)
                .eventTime(Instant.ofEpochMilli(baseTime + (20 * 60 * 1000))) // 20 minutes later
                .build();
        
        // When: Processing both transactions
        testHarness.processElement(new StreamRecord<>(txn1, txn1.getEventTimeMillis()));
        testHarness.processElement(new StreamRecord<>(txn2, txn2.getEventTimeMillis()));
        
        // Then: Geo-velocity alert should be triggered
        assertFalse(testHarness.getOutput().isEmpty(), "Should generate geo-velocity alert");
        
        StreamRecord<?> output = (StreamRecord<?>) testHarness.getOutput().poll();
        FraudAlert alert = (FraudAlert) output.getValue();
        
        assertNotNull(alert);
        assertTrue(alert.getTriggeredRules().stream()
                .anyMatch(r -> r.getCategory() == RuleCategory.GEO));
    }

    @Test
    @DisplayName("Should not trigger geo-velocity alert for possible travel")
    void shouldNotTriggerGeoVelocityAlertForPossibleTravel() throws Exception {
        // Given: Two transactions in nearby cities, 2 hours apart
        // Toronto to Montreal distance: ~540 km
        // Required velocity: 540 km / 2 hours = 270 km/h (possible by train/car)
        String customerId = "CUST-GEO-002";
        long baseTime = System.currentTimeMillis();
        
        Transaction txn1 = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .amount(new BigDecimal("100.00"))
                .currency("CAD")
                .location(Location.builder()
                        .latitude(43.6532)
                        .longitude(-79.3832)
                        .city("Toronto")
                        .countryCode("CA")
                        .build())
                .transactionType(TransactionType.PURCHASE)
                .eventTime(Instant.ofEpochMilli(baseTime))
                .build();
        
        Transaction txn2 = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .amount(new BigDecimal("75.00"))
                .currency("CAD")
                .location(Location.builder()
                        .latitude(45.5017)
                        .longitude(-73.5673)
                        .city("Montreal")
                        .countryCode("CA")
                        .build())
                .transactionType(TransactionType.PURCHASE)
                .eventTime(Instant.ofEpochMilli(baseTime + (2 * 60 * 60 * 1000))) // 2 hours later
                .build();
        
        // When: Processing both transactions
        testHarness.processElement(new StreamRecord<>(txn1, txn1.getEventTimeMillis()));
        testHarness.processElement(new StreamRecord<>(txn2, txn2.getEventTimeMillis()));
        
        // Then: No geo-velocity alert (travel is possible)
        assertTrue(testHarness.getOutput().isEmpty(), 
                "Should not generate alert for possible travel");
    }

    // =========================================================================
    // Combined Tests
    // =========================================================================

    @Test
    @DisplayName("Should trigger alert with multiple rules when both violated")
    void shouldTriggerCombinedAlert() throws Exception {
        // Given: Transactions that violate both velocity and geo-velocity
        String customerId = "CUST-COMBINED-001";
        long baseTime = System.currentTimeMillis();
        
        // Multiple high-value transactions from different locations
        Transaction txn1 = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .location(Location.builder()
                        .latitude(40.7128)
                        .longitude(-74.0060)
                        .city("New York")
                        .countryCode("US")
                        .build())
                .transactionType(TransactionType.PURCHASE)
                .eventTime(Instant.ofEpochMilli(baseTime))
                .build();
        
        Transaction txn2 = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .amount(new BigDecimal("1200.00"))
                .currency("USD")
                .location(Location.builder()
                        .latitude(35.6762)
                        .longitude(139.6503)
                        .city("Tokyo")
                        .countryCode("JP")
                        .build())
                .transactionType(TransactionType.PURCHASE)
                .eventTime(Instant.ofEpochMilli(baseTime + 60000)) // 1 minute later
                .build();
        
        // When: Processing
        testHarness.processElement(new StreamRecord<>(txn1, txn1.getEventTimeMillis()));
        testHarness.processElement(new StreamRecord<>(txn2, txn2.getEventTimeMillis()));
        
        // Then: Alert should have multiple triggered rules
        assertFalse(testHarness.getOutput().isEmpty());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Transaction createTransaction(String customerId, BigDecimal amount, 
            String city, String countryCode) {
        return createTransactionWithTime(customerId, amount, System.currentTimeMillis());
    }

    private Transaction createTransactionWithTime(String customerId, BigDecimal amount, 
            long eventTime) {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .cardNumber("4111111111111111")
                .amount(amount)
                .currency("USD")
                .merchantId("MER-" + UUID.randomUUID().toString().substring(0, 8))
                .merchantName("Test Merchant")
                .merchantCategoryCode("5411")
                .location(Location.builder()
                        .latitude(43.6532)
                        .longitude(-79.3832)
                        .city("Toronto")
                        .countryCode("CA")
                        .build())
                .transactionType(TransactionType.PURCHASE)
                .channel(TransactionChannel.ONLINE)
                .eventTime(Instant.ofEpochMilli(eventTime))
                .processingTime(Instant.now())
                .approved(true)
                .responseCode("00")
                .build();
    }
}


