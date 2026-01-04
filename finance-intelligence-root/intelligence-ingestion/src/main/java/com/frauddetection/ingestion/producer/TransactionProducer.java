package com.frauddetection.ingestion.producer;

import com.frauddetection.common.model.*;
import com.frauddetection.common.util.JsonUtils;
import com.github.javafaker.Faker;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka producer for simulated credit card transactions.
 * 
 * Uses Java Faker to generate realistic transaction data for testing
 * the fraud detection pipeline. Supports multiple scenarios:
 * - Normal transactions
 * - Velocity attacks (rapid-fire transactions)
 * - Geo-velocity anomalies (impossible travel)
 * - Testing the waters patterns (small then large transactions)
 */
public class TransactionProducer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionProducer.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final Faker faker;
    private final Random random;
    private final AtomicLong transactionCounter;

    // Customer profiles for generating consistent behavior
    private final Map<String, CustomerProfile> customerProfiles;

    public TransactionProducer(String bootstrapServers, String topic) {
        this.topic = topic;
        this.faker = new Faker();
        this.random = new Random();
        this.transactionCounter = new AtomicLong(0);
        this.customerProfiles = new HashMap<>();

        // Configure Kafka producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        this.producer = new KafkaProducer<>(props);
        LOG.info("TransactionProducer initialized for topic: {}", topic);
    }

    /**
     * Generates and sends a normal transaction.
     */
    public void sendNormalTransaction(String customerId) {
        Transaction txn = generateNormalTransaction(customerId);
        sendTransaction(txn);
    }

    /**
     * Generates and sends a velocity attack pattern.
     * Sends multiple transactions rapidly for the same customer.
     */
    public void sendVelocityAttack(String customerId, int transactionCount) {
        LOG.info("Generating velocity attack for customer {}: {} transactions", 
                customerId, transactionCount);
        
        for (int i = 0; i < transactionCount; i++) {
            Transaction txn = generateHighValueTransaction(customerId);
            sendTransaction(txn);
            
            // Small delay between transactions to simulate rapid-fire
            try {
                Thread.sleep(100 + random.nextInt(200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Generates and sends a geo-velocity anomaly (impossible travel).
     */
    public void sendImpossibleTravel(String customerId) {
        LOG.info("Generating impossible travel for customer {}", customerId);

        // First transaction in Toronto
        Transaction txn1 = generateTransactionAtLocation(customerId,
                Location.builder()
                        .latitude(43.6532)
                        .longitude(-79.3832)
                        .city("Toronto")
                        .state("Ontario")
                        .countryCode("CA")
                        .build());
        sendTransaction(txn1);

        // Wait 10 minutes (simulated)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Second transaction in London (impossible travel)
        Transaction txn2 = generateTransactionAtLocation(customerId,
                Location.builder()
                        .latitude(51.5074)
                        .longitude(-0.1278)
                        .city("London")
                        .state("England")
                        .countryCode("GB")
                        .build());
        sendTransaction(txn2);
    }

    /**
     * Generates and sends a "Testing the Waters" pattern.
     */
    public void sendTestingTheWaters(String customerId) {
        LOG.info("Generating 'Testing the Waters' pattern for customer {}", customerId);

        // Send 3-4 small transactions
        int smallTxnCount = 3 + random.nextInt(2);
        for (int i = 0; i < smallTxnCount; i++) {
            Transaction smallTxn = generateSmallTransaction(customerId);
            sendTransaction(smallTxn);
            
            try {
                Thread.sleep(200 + random.nextInt(300));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Follow with a large transaction
        Transaction largeTxn = generateLargeTransaction(customerId);
        sendTransaction(largeTxn);
    }

    /**
     * Sends a transaction to Kafka.
     */
    private void sendTransaction(Transaction txn) {
        String key = txn.getCustomerId();
        String value = JsonUtils.toJson(txn);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                LOG.error("Failed to send transaction {}: {}", 
                        txn.getTransactionId(), exception.getMessage());
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Sent transaction {} to partition {} offset {}", 
                        txn.getTransactionId(), metadata.partition(), metadata.offset());
            }
        });
    }

    /**
     * Generates a normal transaction with realistic values.
     */
    private Transaction generateNormalTransaction(String customerId) {
        CustomerProfile profile = getOrCreateProfile(customerId);
        BigDecimal amount = generateNormalAmount(profile);
        
        return buildTransaction(customerId, amount, profile.getHomeLocation());
    }

    /**
     * Generates a high-value transaction for velocity attacks.
     */
    private Transaction generateHighValueTransaction(String customerId) {
        CustomerProfile profile = getOrCreateProfile(customerId);
        BigDecimal amount = new BigDecimal(300 + random.nextInt(700))
                .setScale(2, RoundingMode.HALF_UP);
        
        return buildTransaction(customerId, amount, profile.getHomeLocation());
    }

    /**
     * Generates a small transaction (< $1.00) for testing patterns.
     */
    private Transaction generateSmallTransaction(String customerId) {
        CustomerProfile profile = getOrCreateProfile(customerId);
        BigDecimal amount = new BigDecimal(random.nextDouble() * 0.99)
                .setScale(2, RoundingMode.HALF_UP);
        
        return buildTransaction(customerId, amount, profile.getHomeLocation());
    }

    /**
     * Generates a large transaction (> $500) for cash-out patterns.
     */
    private Transaction generateLargeTransaction(String customerId) {
        CustomerProfile profile = getOrCreateProfile(customerId);
        BigDecimal amount = new BigDecimal(500 + random.nextInt(1500))
                .setScale(2, RoundingMode.HALF_UP);
        
        return buildTransaction(customerId, amount, profile.getHomeLocation());
    }

    /**
     * Generates a transaction at a specific location.
     */
    private Transaction generateTransactionAtLocation(String customerId, Location location) {
        BigDecimal amount = generateNormalAmount(getOrCreateProfile(customerId));
        return buildTransaction(customerId, amount, location);
    }

    /**
     * Builds a complete Transaction object.
     */
    private Transaction buildTransaction(String customerId, BigDecimal amount, Location location) {
        long txnNum = transactionCounter.incrementAndGet();
        Instant now = Instant.now();

        return Transaction.builder()
                .transactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customerId(customerId)
                .cardNumber(faker.finance().creditCard().replaceAll("-", ""))
                .amount(amount)
                .currency("USD")
                .merchantId("MER-" + faker.number().digits(8))
                .merchantCategoryCode(getRandomMCC())
                .merchantName(faker.company().name())
                .location(location)
                .deviceInfo(generateDeviceInfo())
                .transactionType(TransactionType.PURCHASE)
                .channel(getRandomChannel())
                .eventTime(now)
                .processingTime(now)
                .approved(true)
                .responseCode("00")
                .build();
    }

    /**
     * Generates a normal transaction amount based on customer profile.
     */
    private BigDecimal generateNormalAmount(CustomerProfile profile) {
        double baseAmount = profile.getAverageTransactionAmount();
        double variance = baseAmount * 0.5;
        double amount = baseAmount + (random.nextGaussian() * variance);
        amount = Math.max(1.0, Math.min(amount, 1000.0)); // Clamp to reasonable range
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Gets or creates a customer profile.
     */
    private CustomerProfile getOrCreateProfile(String customerId) {
        return customerProfiles.computeIfAbsent(customerId, id -> {
            return new CustomerProfile(
                    id,
                    50.0 + random.nextDouble() * 150.0, // Average transaction $50-$200
                    generateRandomLocation()
            );
        });
    }

    /**
     * Generates a random location.
     */
    private Location generateRandomLocation() {
        String[] cities = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};
        String[] states = {"NY", "CA", "IL", "TX", "AZ"};
        double[][] coords = {
                {40.7128, -74.0060},
                {34.0522, -118.2437},
                {41.8781, -87.6298},
                {29.7604, -95.3698},
                {33.4484, -112.0740}
        };
        
        int idx = random.nextInt(cities.length);
        return Location.builder()
                .city(cities[idx])
                .state(states[idx])
                .countryCode("US")
                .latitude(coords[idx][0])
                .longitude(coords[idx][1])
                .build();
    }

    /**
     * Generates device information.
     */
    private DeviceInfo generateDeviceInfo() {
        String[] deviceTypes = {"MOBILE", "DESKTOP", "TABLET"};
        String[] browsers = {"Chrome", "Safari", "Firefox", "Edge"};
        String[] os = {"iOS 17", "Android 14", "Windows 11", "macOS Sonoma"};
        
        return DeviceInfo.builder()
                .deviceId(UUID.randomUUID().toString())
                .deviceType(deviceTypes[random.nextInt(deviceTypes.length)])
                .browser(browsers[random.nextInt(browsers.length)])
                .operatingSystem(os[random.nextInt(os.length)])
                .knownDevice(random.nextBoolean())
                .vpnDetected(random.nextDouble() < 0.05) // 5% VPN usage
                .build();
    }

    /**
     * Returns a random Merchant Category Code.
     */
    private String getRandomMCC() {
        String[] mccs = {"5411", "5812", "5912", "5999", "7011", "4111"};
        return mccs[random.nextInt(mccs.length)];
    }

    /**
     * Returns a random transaction channel.
     */
    private TransactionChannel getRandomChannel() {
        TransactionChannel[] channels = {
                TransactionChannel.POS,
                TransactionChannel.ONLINE,
                TransactionChannel.MOBILE
        };
        return channels[random.nextInt(channels.length)];
    }

    @Override
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
            LOG.info("TransactionProducer closed");
        }
    }

    /**
     * Main method for standalone testing.
     */
    public static void main(String[] args) throws InterruptedException {
        String bootstrapServers = System.getenv().getOrDefault(
                "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String topic = "transactions-live";

        try (TransactionProducer producer = new TransactionProducer(bootstrapServers, topic)) {
            LOG.info("Starting transaction simulation...");

            // Generate some normal transactions
            for (int i = 0; i < 10; i++) {
                producer.sendNormalTransaction("CUST-" + (1000 + i));
                Thread.sleep(500);
            }

            // Generate a velocity attack
            producer.sendVelocityAttack("CUST-ATTACK-001", 8);

            // Generate impossible travel
            producer.sendImpossibleTravel("CUST-TRAVEL-001");

            // Generate testing the waters
            producer.sendTestingTheWaters("CUST-TEST-001");

            LOG.info("Transaction simulation completed");
        }
    }

    /**
     * Internal class representing a customer's behavioral profile.
     */
    private static class CustomerProfile {
        private final String customerId;
        private final double averageTransactionAmount;
        private final Location homeLocation;

        public CustomerProfile(String customerId, double averageTransactionAmount, Location homeLocation) {
            this.customerId = customerId;
            this.averageTransactionAmount = averageTransactionAmount;
            this.homeLocation = homeLocation;
        }

        public String getCustomerId() { return customerId; }
        public double getAverageTransactionAmount() { return averageTransactionAmount; }
        public Location getHomeLocation() { return homeLocation; }
    }
}


