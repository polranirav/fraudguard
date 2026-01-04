package com.frauddetection.processing.ml;

import com.frauddetection.common.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for calling ML inference service to get fraud probability scores.
 * 
 * This client makes HTTP requests to the ML inference service (FastAPI)
 * deployed on Kubernetes. It includes retry logic and circuit breaker
 * pattern for resilience.
 */
public class MLInferenceClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(MLInferenceClient.class);
    
    private final HttpClient httpClient;
    private final String mlServiceUrl;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    
    // Circuit breaker state
    private boolean circuitOpen = false;
    private long circuitOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute
    
    public MLInferenceClient(String mlServiceUrl) {
        this.mlServiceUrl = mlServiceUrl;
        this.timeoutSeconds = 2; // 2 second timeout for real-time inference
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
    
    /**
     * Get fraud probability from ML model.
     * 
     * @param transaction The transaction to score
     * @param features Extracted features for the transaction
     * @return Fraud probability (0.0 to 1.0), or 0.0 if service unavailable
     */
    public double getFraudProbability(Transaction transaction, MLFeatures features) {
        // Check circuit breaker
        if (circuitOpen) {
            if (System.currentTimeMillis() - circuitOpenTime > CIRCUIT_BREAKER_TIMEOUT_MS) {
                circuitOpen = false;
                LOG.info("Circuit breaker closed, retrying ML service");
            } else {
                LOG.debug("Circuit breaker open, skipping ML inference");
                return 0.0; // Fallback to rule-based only
            }
        }
        
        try {
            // Build request payload (using HashMap because Map.of() has 10-pair limit)
            Map<String, Object> featuresMap = new HashMap<>();
            featuresMap.put("transaction_amount", features.getTransactionAmount());
            featuresMap.put("transaction_count_1min", features.getTransactionCount1Min());
            featuresMap.put("transaction_count_1hour", features.getTransactionCount1Hour());
            featuresMap.put("total_amount_1min", features.getTotalAmount1Min());
            featuresMap.put("total_amount_1hour", features.getTotalAmount1Hour());
            featuresMap.put("distance_from_home_km", features.getDistanceFromHomeKm());
            featuresMap.put("time_since_last_txn_minutes", features.getTimeSinceLastTxnMinutes());
            featuresMap.put("velocity_kmh", features.getVelocityKmh());
            featuresMap.put("merchant_reputation_score", features.getMerchantReputationScore());
            featuresMap.put("customer_age_days", features.getCustomerAgeDays());
            featuresMap.put("is_known_device", features.getIsKnownDevice() ? 1 : 0);
            featuresMap.put("is_vpn_detected", features.getIsVpnDetected() ? 1 : 0);
            featuresMap.put("device_usage_count", features.getDeviceUsageCount());
            featuresMap.put("hour_of_day", features.getHourOfDay());
            featuresMap.put("day_of_week", features.getDayOfWeek());
            featuresMap.put("merchant_category_risk", features.getMerchantCategoryRisk());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("transaction_id", transaction.getTransactionId());
            requestBody.put("features", featuresMap);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/predict"))
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
                
                LOG.debug("ML inference successful for transaction {}: probability={}", 
                        transaction.getTransactionId(), fraudProbability);
                
                // Close circuit breaker on success
                circuitOpen = false;
                
                return fraudProbability;
            } else {
                LOG.warn("ML service returned error status {}: {}", response.statusCode(), response.body());
                openCircuitBreaker();
                return 0.0;
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("ML inference failed for transaction {}: {}", 
                    transaction.getTransactionId(), e.getMessage());
            openCircuitBreaker();
            return 0.0; // Fallback to rule-based only
        }
    }
    
    private void openCircuitBreaker() {
        if (!circuitOpen) {
            circuitOpen = true;
            circuitOpenTime = System.currentTimeMillis();
            LOG.warn("Circuit breaker opened due to ML service failure");
        }
    }
    
    /**
     * Check if ML service is healthy.
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
