package com.frauddetection.processing.lnn;

import com.frauddetection.common.model.Transaction;
import com.frauddetection.processing.ml.MLFeatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for calling Liquid Neural Network (LNN) inference service.
 * 
 * LNN processes irregularly sampled transaction sequences to detect
 * temporal fraud patterns that fixed-window approaches miss.
 * 
 * Financial Industry Value:
 * - Handles real-world transaction irregularity (5 txns in 1 min, then silence for a week)
 * - Preserves temporal fidelity (critical for fraud detection)
 * - Detects subtle patterns in transaction timing
 * 
 * Architecture:
 * - Similar to MLInferenceClient but processes sequences instead of single transactions
 * - Includes circuit breaker for resilience
 * - Returns fraud probability + temporal pattern score
 */
public class LNNInferenceClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(LNNInferenceClient.class);
    
    private final HttpClient httpClient;
    private final String lnnServiceUrl;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    
    // Circuit breaker state
    private boolean circuitOpen = false;
    private long circuitOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute
    
    public LNNInferenceClient(String lnnServiceUrl) {
        this.lnnServiceUrl = lnnServiceUrl;
        this.timeoutSeconds = 3; // 3 second timeout (slightly longer than ML service)
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
    
    /**
     * Get fraud probability from LNN model for irregular time-series sequence.
     * 
     * @param currentTransaction The current transaction to evaluate
     * @param transactionSequence Historical transactions in sequence (with timestamps)
     * @param mlFeatures Extracted features for current transaction
     * @return LNN prediction result with fraud probability and temporal pattern score
     */
    public LNNPredictionResult getFraudProbability(
            Transaction currentTransaction,
            List<Transaction> transactionSequence,
            MLFeatures mlFeatures) {
        
        // Check circuit breaker
        if (circuitOpen) {
            if (System.currentTimeMillis() - circuitOpenTime > CIRCUIT_BREAKER_TIMEOUT_MS) {
                circuitOpen = false;
                LOG.info("LNN circuit breaker closed, retrying service");
            } else {
                LOG.debug("LNN circuit breaker open, skipping LNN inference");
                return LNNPredictionResult.fallback(); // Fallback to zero score
            }
        }
        
        try {
            // Build transaction sequence for LNN
            List<Map<String, Object>> sequence = new ArrayList<>();
            
            // Add historical transactions
            for (Transaction txn : transactionSequence) {
                Map<String, Object> txnData = buildTransactionData(txn, mlFeatures);
                sequence.add(txnData);
            }
            
            // Add current transaction
            Map<String, Object> currentData = buildTransactionData(currentTransaction, mlFeatures);
            
            // Build request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("transaction_id", currentTransaction.getTransactionId());
            requestBody.put("transaction_sequence", sequence);
            requestBody.put("current_transaction", currentData);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lnnServiceUrl + "/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // Parse response
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                double fraudProbability = ((Number) responseBody.get("fraud_probability")).doubleValue();
                double temporalPatternScore = ((Number) responseBody.getOrDefault("temporal_pattern_score", 0.0)).doubleValue();
                boolean irregularityDetected = (Boolean) responseBody.getOrDefault("irregularity_detected", false);
                
                LOG.debug("LNN inference successful for transaction {}: probability={}, temporal_score={}", 
                        currentTransaction.getTransactionId(), fraudProbability, temporalPatternScore);
                
                // Close circuit breaker on success
                circuitOpen = false;
                
                return new LNNPredictionResult(fraudProbability, temporalPatternScore, irregularityDetected);
            } else {
                LOG.warn("LNN service returned error status {}: {}", response.statusCode(), response.body());
                openCircuitBreaker();
                return LNNPredictionResult.fallback();
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("LNN inference failed for transaction {}: {}", 
                    currentTransaction.getTransactionId(), e.getMessage());
            openCircuitBreaker();
            return LNNPredictionResult.fallback();
        }
    }
    
    /**
     * Build transaction data map for LNN service.
     */
    private Map<String, Object> buildTransactionData(Transaction txn, MLFeatures features) {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", txn.getEventTimeMillis());
        data.put("amount", txn.getAmount().doubleValue());
        data.put("distance_from_home_km", features.getDistanceFromHomeKm());
        data.put("velocity_kmh", features.getVelocityKmh());
        data.put("is_known_device", features.getIsKnownDevice() ? 1 : 0);
        data.put("is_vpn_detected", features.getIsVpnDetected() ? 1 : 0);
        data.put("merchant_reputation_score", features.getMerchantReputationScore());
        data.put("time_since_last_txn_minutes", features.getTimeSinceLastTxnMinutes());
        return data;
    }
    
    private void openCircuitBreaker() {
        if (!circuitOpen) {
            circuitOpen = true;
            circuitOpenTime = System.currentTimeMillis();
            LOG.warn("LNN circuit breaker opened due to service failure");
        }
    }
    
    /**
     * Check if LNN service is healthy.
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lnnServiceUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Result from LNN inference.
     */
    public static class LNNPredictionResult {
        private final double fraudProbability;
        private final double temporalPatternScore;
        private final boolean irregularityDetected;
        
        public LNNPredictionResult(double fraudProbability, double temporalPatternScore, boolean irregularityDetected) {
            this.fraudProbability = fraudProbability;
            this.temporalPatternScore = temporalPatternScore;
            this.irregularityDetected = irregularityDetected;
        }
        
        public static LNNPredictionResult fallback() {
            return new LNNPredictionResult(0.0, 0.0, false);
        }
        
        public double getFraudProbability() {
            return fraudProbability;
        }
        
        public double getTemporalPatternScore() {
            return temporalPatternScore;
        }
        
        public boolean isIrregularityDetected() {
            return irregularityDetected;
        }
    }
}
