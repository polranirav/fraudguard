package com.frauddetection.enrichment.redis;

import com.frauddetection.common.exception.FraudDetectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-based enrichment service for real-time fraud detection.
 * 
 * Provides millisecond-latency lookups for:
 * - IP address blacklists
 * - Merchant reputation scores
 * - Customer behavior embeddings (for vector similarity)
 * 
 * This service is designed to be called from within the Flink pipeline
 * for synchronous enrichment during stream processing.
 */
public class RedisEnrichmentService implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(RedisEnrichmentService.class);

    // Redis key prefixes
    private static final String IP_BLACKLIST_KEY = "fraud:ip:blacklist";
    private static final String MERCHANT_SCORE_PREFIX = "fraud:merchant:score:";
    private static final String CUSTOMER_EMBEDDING_PREFIX = "fraud:customer:embedding:";
    private static final String DEVICE_HISTORY_PREFIX = "fraud:device:history:";

    private final JedisPool jedisPool;

    /**
     * Creates a new RedisEnrichmentService with the specified connection parameters.
     */
    public RedisEnrichmentService(String host, int port) {
        this(host, port, null);
    }

    /**
     * Creates a new RedisEnrichmentService with authentication.
     */
    public RedisEnrichmentService(String host, int port, String password) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(20);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setMaxWait(Duration.ofMillis(2000));

        if (password != null && !password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }

        LOG.info("RedisEnrichmentService initialized: {}:{}", host, port);
    }

    /**
     * Checks if an IP address is on the blacklist.
     * 
     * @param ipAddress The IP address to check
     * @return true if the IP is blacklisted
     */
    public boolean isIpBlacklisted(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            boolean blacklisted = jedis.sismember(IP_BLACKLIST_KEY, ipAddress);
            if (blacklisted) {
                LOG.debug("IP address {} found in blacklist", ipAddress);
            }
            return blacklisted;
        } catch (Exception e) {
            LOG.error("Error checking IP blacklist for {}: {}", ipAddress, e.getMessage());
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.REDIS_LOOKUP_ERROR,
                    "Failed to check IP blacklist",
                    e);
        }
    }

    /**
     * Adds an IP address to the blacklist.
     * 
     * @param ipAddress The IP address to blacklist
     */
    public void addToIpBlacklist(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd(IP_BLACKLIST_KEY, ipAddress);
            LOG.info("Added IP {} to blacklist", ipAddress);
        } catch (Exception e) {
            LOG.error("Error adding IP to blacklist: {}", e.getMessage());
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.REDIS_LOOKUP_ERROR,
                    "Failed to add IP to blacklist",
                    e);
        }
    }

    /**
     * Gets the reputation score for a merchant.
     * 
     * @param merchantId The merchant identifier
     * @return Optional containing the score (0.0-1.0, higher = better reputation)
     */
    public Optional<Double> getMerchantReputationScore(String merchantId) {
        if (merchantId == null || merchantId.isEmpty()) {
            return Optional.empty();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String scoreStr = jedis.get(MERCHANT_SCORE_PREFIX + merchantId);
            if (scoreStr != null) {
                double score = Double.parseDouble(scoreStr);
                LOG.debug("Merchant {} reputation score: {}", merchantId, score);
                return Optional.of(score);
            }
            return Optional.empty();
        } catch (NumberFormatException e) {
            LOG.warn("Invalid merchant score format for {}", merchantId);
            return Optional.empty();
        } catch (Exception e) {
            LOG.error("Error getting merchant reputation for {}: {}", merchantId, e.getMessage());
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.REDIS_LOOKUP_ERROR,
                    "Failed to get merchant reputation",
                    e);
        }
    }

    /**
     * Sets the reputation score for a merchant.
     * 
     * @param merchantId The merchant identifier
     * @param score The reputation score (0.0-1.0)
     */
    public void setMerchantReputationScore(String merchantId, double score) {
        if (merchantId == null || merchantId.isEmpty()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(MERCHANT_SCORE_PREFIX + merchantId, String.valueOf(score));
            LOG.debug("Set merchant {} reputation score: {}", merchantId, score);
        } catch (Exception e) {
            LOG.error("Error setting merchant reputation: {}", e.getMessage());
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.REDIS_LOOKUP_ERROR,
                    "Failed to set merchant reputation",
                    e);
        }
    }

    /**
     * Gets the behavioral embedding vector for a customer.
     * Used for similarity comparison with current transaction.
     * 
     * @param customerId The customer identifier
     * @return Optional containing the embedding as a list of doubles
     */
    public Optional<List<Double>> getCustomerEmbedding(String customerId) {
        if (customerId == null || customerId.isEmpty()) {
            return Optional.empty();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            List<String> embeddingStrs = jedis.lrange(
                    CUSTOMER_EMBEDDING_PREFIX + customerId, 0, -1);
            
            if (embeddingStrs != null && !embeddingStrs.isEmpty()) {
                List<Double> embedding = embeddingStrs.stream()
                        .map(Double::parseDouble)
                        .toList();
                LOG.debug("Retrieved embedding for customer {}: {} dimensions", 
                        customerId, embedding.size());
                return Optional.of(embedding);
            }
            return Optional.empty();
        } catch (Exception e) {
            LOG.error("Error getting customer embedding for {}: {}", customerId, e.getMessage());
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.REDIS_LOOKUP_ERROR,
                    "Failed to get customer embedding",
                    e);
        }
    }

    /**
     * Stores the behavioral embedding vector for a customer.
     * 
     * @param customerId The customer identifier
     * @param embedding The embedding vector
     */
    public void setCustomerEmbedding(String customerId, List<Double> embedding) {
        if (customerId == null || customerId.isEmpty() || embedding == null) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = CUSTOMER_EMBEDDING_PREFIX + customerId;
            jedis.del(key); // Clear existing
            
            String[] values = embedding.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new);
            jedis.rpush(key, values);
            
            LOG.debug("Stored embedding for customer {}: {} dimensions", 
                    customerId, embedding.size());
        } catch (Exception e) {
            LOG.error("Error storing customer embedding: {}", e.getMessage());
            throw new FraudDetectionException(
                    FraudDetectionException.ErrorCode.REDIS_LOOKUP_ERROR,
                    "Failed to store customer embedding",
                    e);
        }
    }

    /**
     * Calculates cosine similarity between two vectors.
     * 
     * @param vectorA First vector
     * @param vectorB Second vector
     * @return Cosine similarity (-1.0 to 1.0)
     */
    public double cosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += vectorA.get(i) * vectorA.get(i);
            normB += vectorB.get(i) * vectorB.get(i);
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Gets device usage history for a customer.
     * 
     * @param customerId The customer identifier
     * @return Set of device IDs previously used by the customer
     */
    public Set<String> getDeviceHistory(String customerId) {
        if (customerId == null || customerId.isEmpty()) {
            return Set.of();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.smembers(DEVICE_HISTORY_PREFIX + customerId);
        } catch (Exception e) {
            LOG.error("Error getting device history for {}: {}", customerId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Adds a device to the customer's history.
     * 
     * @param customerId The customer identifier
     * @param deviceId The device identifier
     */
    public void addDeviceToHistory(String customerId, String deviceId) {
        if (customerId == null || deviceId == null) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd(DEVICE_HISTORY_PREFIX + customerId, deviceId);
        } catch (Exception e) {
            LOG.error("Error adding device to history: {}", e.getMessage());
        }
    }

    /**
     * Checks if a device is known for a customer.
     * 
     * @param customerId The customer identifier
     * @param deviceId The device identifier
     * @return true if the device has been used by this customer before
     */
    public boolean isKnownDevice(String customerId, String deviceId) {
        if (customerId == null || deviceId == null) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(DEVICE_HISTORY_PREFIX + customerId, deviceId);
        } catch (Exception e) {
            LOG.error("Error checking known device: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Health check for Redis connection.
     * 
     * @return true if Redis is reachable
     */
    public boolean isHealthy() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            LOG.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            LOG.info("RedisEnrichmentService closed");
        }
    }
}


