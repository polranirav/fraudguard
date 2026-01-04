package com.frauddetection.processing.pattern;

import com.frauddetection.common.model.*;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the "Testing the Waters" CEP pattern.
 */
class TestingTheWatersPatternTest {

    private static StreamExecutionEnvironment env;
    private static List<FraudAlert> collectedAlerts;

    @BeforeEach
    void setup() {
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        collectedAlerts = new CopyOnWriteArrayList<>();
    }

    @Test
    @DisplayName("Should detect Testing the Waters pattern")
    void shouldDetectTestingTheWatersPattern() throws Exception {
        // Given: A sequence of small transactions followed by a large one
        String customerId = "CUST-TEST-001";
        long baseTime = System.currentTimeMillis();
        
        List<Transaction> transactions = Arrays.asList(
                createTransaction(customerId, "0.50", baseTime),
                createTransaction(customerId, "0.75", baseTime + 60000),
                createTransaction(customerId, "0.25", baseTime + 120000),
                createTransaction(customerId, "750.00", baseTime + 180000) // Large transaction
        );
        
        // When: Processing through CEP pattern
        DataStream<Transaction> stream = env.fromCollection(transactions)
                .keyBy(Transaction::getCustomerId);
        
        PatternStream<Transaction> patternStream = CEP.pattern(
                stream,
                TestingTheWatersPattern.getPattern()
        );
        
        patternStream.process(new PatternProcessor()).addSink(new CollectSink());
        
        env.execute("Testing the Waters Test");
        
        // Then: Alert should be generated
        assertEquals(1, collectedAlerts.size(), "Should detect exactly one pattern match");
        
        FraudAlert alert = collectedAlerts.get(0);
        assertEquals(customerId, alert.getCustomerId());
        assertEquals(AlertSeverity.HIGH, alert.getSeverity());
        assertTrue(alert.getTriggeredRules().stream()
                .anyMatch(r -> r.getRuleId().equals("CEP-001")));
    }

    @Test
    @DisplayName("Should not trigger with only small transactions")
    void shouldNotTriggerWithOnlySmallTransactions() throws Exception {
        // Given: Only small transactions (no large cash-out)
        String customerId = "CUST-TEST-002";
        long baseTime = System.currentTimeMillis();
        
        List<Transaction> transactions = Arrays.asList(
                createTransaction(customerId, "0.50", baseTime),
                createTransaction(customerId, "0.75", baseTime + 60000),
                createTransaction(customerId, "0.25", baseTime + 120000),
                createTransaction(customerId, "0.99", baseTime + 180000) // Still small
        );
        
        // When: Processing
        DataStream<Transaction> stream = env.fromCollection(transactions)
                .keyBy(Transaction::getCustomerId);
        
        PatternStream<Transaction> patternStream = CEP.pattern(
                stream,
                TestingTheWatersPattern.getPattern()
        );
        
        patternStream.process(new PatternProcessor()).addSink(new CollectSink());
        
        env.execute("No Pattern Test");
        
        // Then: No alert
        assertTrue(collectedAlerts.isEmpty(), "Should not trigger without large transaction");
    }

    @Test
    @DisplayName("Should not trigger with less than 3 small transactions")
    void shouldNotTriggerWithTooFewSmallTransactions() throws Exception {
        // Given: Only 2 small transactions before large one
        String customerId = "CUST-TEST-003";
        long baseTime = System.currentTimeMillis();
        
        List<Transaction> transactions = Arrays.asList(
                createTransaction(customerId, "0.50", baseTime),
                createTransaction(customerId, "0.75", baseTime + 60000),
                createTransaction(customerId, "750.00", baseTime + 120000)
        );
        
        // When: Processing
        DataStream<Transaction> stream = env.fromCollection(transactions)
                .keyBy(Transaction::getCustomerId);
        
        PatternStream<Transaction> patternStream = CEP.pattern(
                stream,
                TestingTheWatersPattern.getPattern()
        );
        
        patternStream.process(new PatternProcessor()).addSink(new CollectSink());
        
        env.execute("Too Few Small Transactions Test");
        
        // Then: No alert (need at least 3 small transactions)
        assertTrue(collectedAlerts.isEmpty(), 
                "Should not trigger with less than 3 small transactions");
    }

    @Test
    @DisplayName("Should detect pattern with more than 3 small transactions")
    void shouldDetectPatternWithMoreSmallTransactions() throws Exception {
        // Given: 5 small transactions followed by a large one
        String customerId = "CUST-TEST-004";
        long baseTime = System.currentTimeMillis();
        
        List<Transaction> transactions = Arrays.asList(
                createTransaction(customerId, "0.10", baseTime),
                createTransaction(customerId, "0.20", baseTime + 30000),
                createTransaction(customerId, "0.30", baseTime + 60000),
                createTransaction(customerId, "0.40", baseTime + 90000),
                createTransaction(customerId, "0.50", baseTime + 120000),
                createTransaction(customerId, "999.00", baseTime + 150000)
        );
        
        // When: Processing
        DataStream<Transaction> stream = env.fromCollection(transactions)
                .keyBy(Transaction::getCustomerId);
        
        PatternStream<Transaction> patternStream = CEP.pattern(
                stream,
                TestingTheWatersPattern.getPattern()
        );
        
        patternStream.process(new PatternProcessor()).addSink(new CollectSink());
        
        env.execute("More Small Transactions Test");
        
        // Then: Alert should be generated
        assertFalse(collectedAlerts.isEmpty(), "Should detect pattern with 5+ small transactions");
    }

    @Test
    @DisplayName("Should handle multiple customers independently")
    void shouldHandleMultipleCustomersIndependently() throws Exception {
        // Given: Two customers with different patterns
        long baseTime = System.currentTimeMillis();
        
        List<Transaction> transactions = Arrays.asList(
                // Customer 1: Valid pattern
                createTransaction("CUST-A", "0.50", baseTime),
                createTransaction("CUST-A", "0.75", baseTime + 30000),
                createTransaction("CUST-A", "0.25", baseTime + 60000),
                createTransaction("CUST-A", "600.00", baseTime + 90000),
                
                // Customer 2: No pattern (only normal transactions)
                createTransaction("CUST-B", "50.00", baseTime + 10000),
                createTransaction("CUST-B", "75.00", baseTime + 40000),
                createTransaction("CUST-B", "100.00", baseTime + 70000)
        );
        
        // When: Processing
        DataStream<Transaction> stream = env.fromCollection(transactions)
                .keyBy(Transaction::getCustomerId);
        
        PatternStream<Transaction> patternStream = CEP.pattern(
                stream,
                TestingTheWatersPattern.getPattern()
        );
        
        patternStream.process(new PatternProcessor()).addSink(new CollectSink());
        
        env.execute("Multiple Customers Test");
        
        // Then: Only Customer A should trigger an alert
        assertEquals(1, collectedAlerts.size());
        assertEquals("CUST-A", collectedAlerts.get(0).getCustomerId());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Transaction createTransaction(String customerId, String amount, long eventTime) {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customerId)
                .cardNumber("4111111111111111")
                .amount(new BigDecimal(amount))
                .currency("USD")
                .merchantId("MER-TEST")
                .merchantName("Test Merchant")
                .transactionType(TransactionType.PURCHASE)
                .channel(TransactionChannel.ONLINE)
                .eventTime(Instant.ofEpochMilli(eventTime))
                .processingTime(Instant.now())
                .build();
    }

    // Pattern processor for testing
    private static class PatternProcessor 
            extends PatternProcessFunction<Transaction, FraudAlert> {
        @Override
        public void processMatch(Map<String, List<Transaction>> match, 
                                 Context ctx, 
                                 Collector<FraudAlert> out) {
            List<Transaction> smallTxns = match.get("small_txns");
            List<Transaction> largeTxn = match.get("large_txn");
            
            if (largeTxn != null && !largeTxn.isEmpty()) {
                FraudAlert alert = TestingTheWatersPattern.createAlert(
                        largeTxn.get(0), smallTxns);
                out.collect(alert);
            }
        }
    }

    // Sink for collecting results
    private static class CollectSink implements SinkFunction<FraudAlert> {
        @Override
        public void invoke(FraudAlert value, Context context) {
            collectedAlerts.add(value);
        }
    }
}


