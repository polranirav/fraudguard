package com.frauddetection.processing.job;

import com.frauddetection.common.model.FraudAlert;
import com.frauddetection.common.model.Transaction;
import com.frauddetection.processing.function.FraudDetectionProcessFunction;
import com.frauddetection.processing.pattern.TestingTheWatersPattern;
import com.frauddetection.processing.serialization.TransactionDeserializationSchema;
import com.frauddetection.processing.serialization.FraudAlertSerializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Main Apache Flink job for real-time fraud detection.
 * 
 * This job implements a multi-layered detection strategy:
 * 1. Velocity and frequency monitoring
 * 2. Geo-velocity (impossible travel) detection
 * 3. Complex Event Processing for sequential patterns
 * 4. Redis enrichment for blacklist/reputation scoring
 * 
 * Architecture:
 * Kafka Source -> Keyed Processing -> CEP Patterns -> Kafka Sink
 *                                  -> Synapse Sink (all events)
 */
public class FraudDetectionJob {

    private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionJob.class);

    // Configuration constants
    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";
    private static final String TRANSACTIONS_TOPIC = "transactions-live";
    private static final String ALERTS_TOPIC = "fraud-alerts";
    private static final String CONSUMER_GROUP = "fraud-detection-group";

    public static void main(String[] args) throws Exception {
        LOG.info("Starting Real-Time Fraud Detection Job");

        // Load configuration from environment or properties
        Properties config = loadConfiguration();

        // Set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Configure checkpointing for exactly-once semantics
        configureCheckpointing(env);

        // Build and execute the fraud detection pipeline
        buildPipeline(env, config);

        // Execute the Flink job
        env.execute("Real-Time Fraud Detection Pipeline");
    }

    /**
     * Configures Flink checkpointing for fault tolerance and exactly-once processing.
     */
    private static void configureCheckpointing(StreamExecutionEnvironment env) {
        // Enable checkpointing every 60 seconds
        env.enableCheckpointing(60000);

        // Set exactly-once processing guarantee
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        // Minimum time between checkpoints: 30 seconds
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000);

        // Checkpoint timeout: 10 minutes
        env.getCheckpointConfig().setCheckpointTimeout(600000);

        // Allow 3 consecutive checkpoint failures
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        // Maximum concurrent checkpoints
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

        LOG.info("Checkpointing configured: mode=EXACTLY_ONCE, interval=60s");
    }

    /**
     * Builds the fraud detection processing pipeline.
     */
    private static void buildPipeline(StreamExecutionEnvironment env, Properties config) {
        String bootstrapServers = config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092");

        // Create Kafka source for transactions
        KafkaSource<Transaction> transactionSource = KafkaSource.<Transaction>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(TRANSACTIONS_TOPIC)
                .setGroupId(CONSUMER_GROUP)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TransactionDeserializationSchema())
                .build();

        // Watermark strategy for event-time processing
        // Allows up to 5 seconds of out-of-orderness
        WatermarkStrategy<Transaction> watermarkStrategy = WatermarkStrategy
                .<Transaction>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((transaction, timestamp) -> transaction.getEventTimeMillis())
                .withIdleness(Duration.ofMinutes(1));

        // Create the source stream with watermarks
        DataStream<Transaction> transactionStream = env
                .fromSource(transactionSource, watermarkStrategy, "Kafka Transaction Source")
                .name("Transaction Source");

        // Key by customer ID for stateful per-user processing
        KeyedStream<Transaction, String> keyedStream = transactionStream
                .keyBy(Transaction::getCustomerId);

        // Apply the main fraud detection process function
        // This handles velocity checks, geo-velocity, and Redis enrichment
        SingleOutputStreamOperator<FraudAlert> velocityAlerts = keyedStream
                .process(new FraudDetectionProcessFunction())
                .name("Velocity & Geo-Velocity Detection");

        // Apply CEP pattern for "Testing the Waters" detection
        PatternStream<Transaction> patternStream = CEP.pattern(
                keyedStream,
                TestingTheWatersPattern.getPattern());

        DataStream<FraudAlert> cepAlerts = patternStream
                .process(new TestingTheWatersPatternProcessor())
                .name("CEP Pattern Detection");

        // Union all alert streams
        DataStream<FraudAlert> allAlerts = velocityAlerts.union(cepAlerts);

        // Create Kafka sink for alerts
        KafkaSink<FraudAlert> alertSink = KafkaSink.<FraudAlert>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(new FraudAlertSerializationSchema(ALERTS_TOPIC))
                .build();

        // Sink alerts to Kafka
        allAlerts
                .sinkTo(alertSink)
                .name("Kafka Alert Sink");

        // Log alert count
        allAlerts
                .map(alert -> {
                    LOG.info("Fraud Alert Generated: {}", alert.getSummary());
                    return alert;
                })
                .name("Alert Logger");

        LOG.info("Pipeline built successfully");
    }

    /**
     * Loads configuration from environment variables or default properties.
     */
    private static Properties loadConfiguration() {
        Properties props = new Properties();

        // Load from environment variables with defaults
        props.setProperty(KAFKA_BOOTSTRAP_SERVERS,
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));

        return props;
    }

    /**
     * Pattern processor for the "Testing the Waters" CEP pattern.
     * Generates a fraud alert when the pattern is matched.
     */
    private static class TestingTheWatersPatternProcessor 
            extends PatternProcessFunction<Transaction, FraudAlert> {

        @Override
        public void processMatch(Map<String, List<Transaction>> pattern, 
                                 Context ctx, 
                                 Collector<FraudAlert> out) {
            List<Transaction> smallTxns = pattern.get("small_txns");
            List<Transaction> largeTxn = pattern.get("large_txn");

            if (largeTxn != null && !largeTxn.isEmpty()) {
                Transaction largeTransaction = largeTxn.get(0);
                
                FraudAlert alert = TestingTheWatersPattern.createAlert(
                        largeTransaction, 
                        smallTxns);
                
                out.collect(alert);
            }
        }
    }
}


