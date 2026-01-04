package com.frauddetection.processing.nsai;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Client for calling Neuro-Symbolic AI (NSAI) inference service.
 * 
 * NSAI combines neural pattern recognition with symbolic reasoning to provide
 * explainable, regulatory-compliant fraud detection decisions.
 * 
 * Financial Industry Value:
 * - Regulatory Compliance: EU AI Act requires explainability
 * - Analyst Trust: Analysts can understand every decision
 * - Audit Trail: Complete explanation for compliance audits
 * - 100% explainability for all fraud decisions
 * 
 * Architecture:
 * - Called for high-risk transactions to generate explanations
 * - Combines neural and symbolic components
 * - Returns fraud probability + full explanation
 */
public class NSAIInferenceClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(NSAIInferenceClient.class);
    
    private final HttpClient httpClient;
    private final String nsaiServiceUrl;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    
    // Circuit breaker state
    private boolean circuitOpen = false;
    private long circuitOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute
    
    public NSAIInferenceClient(String nsaiServiceUrl) {
        this.nsaiServiceUrl = nsaiServiceUrl;
        this.timeoutSeconds = 3; // 3 second timeout
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
    
    /**
     * Get fraud probability with full explanation from NSAI model.
     * 
     * @param transaction The transaction to evaluate
     * @param mlFeatures Extracted features for the transaction
     * @return NSAI prediction result with fraud probability and explanation
     */
    public NSAIPredictionResult predictWithExplanation(
            Transaction transaction,
            MLFeatures mlFeatures) {
        
        // Check circuit breaker
        if (circuitOpen) {
            if (System.currentTimeMillis() - circuitOpenTime > CIRCUIT_BREAKER_TIMEOUT_MS) {
                circuitOpen = false;
                LOG.info("NSAI circuit breaker closed, retrying service");
            } else {
                LOG.debug("NSAI circuit breaker open, skipping NSAI inference");
                return NSAIPredictionResult.fallback(transaction.getTransactionId());
            }
        }
        
        try {
            // Build transaction data map
            Map<String, Object> transactionData = buildTransactionData(transaction, mlFeatures);
            
            // Build context (empty for now, could include additional context)
            Map<String, Object> context = new HashMap<>();
            
            // Build request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("transaction_id", transaction.getTransactionId());
            requestBody.put("transaction", transactionData);
            requestBody.put("context", context);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(nsaiServiceUrl + "/predict-with-explanation"))
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
                
                @SuppressWarnings("unchecked")
                Map<String, Object> explanationMap = (Map<String, Object>) responseBody.get("explanation");
                
                String summary = (String) explanationMap.get("summary");
                boolean regulatoryCompliant = (Boolean) explanationMap.getOrDefault("regulatory_compliant", true);
                String explanationId = (String) explanationMap.get("explanation_id");
                
                LOG.debug("NSAI inference successful for transaction {}: probability={}, summary={}", 
                        transaction.getTransactionId(), fraudProbability, summary);
                
                // Close circuit breaker on success
                circuitOpen = false;
                
                return new NSAIPredictionResult(
                    fraudProbability,
                    summary,
                    regulatoryCompliant,
                    explanationId
                );
            } else {
                LOG.warn("NSAI service returned error status {}: {}", response.statusCode(), response.body());
                openCircuitBreaker();
                return NSAIPredictionResult.fallback(transaction.getTransactionId());
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("NSAI inference failed for transaction {}: {}", 
                    transaction.getTransactionId(), e.getMessage());
            openCircuitBreaker();
            return NSAIPredictionResult.fallback(transaction.getTransactionId());
        }
    }
    
    /**
     * Build transaction data map for NSAI service.
     */
    private Map<String, Object> buildTransactionData(Transaction txn, MLFeatures features) {
        Map<String, Object> data = new HashMap<>();
        data.put("transaction_amount", features.getTransactionAmount());
        data.put("transaction_count_1min", features.getTransactionCount1Min());
        data.put("transaction_count_1hour", features.getTransactionCount1Hour());
        data.put("total_amount_1min", features.getTotalAmount1Min());
        data.put("total_amount_1hour", features.getTotalAmount1Hour());
        data.put("distance_from_home_km", features.getDistanceFromHomeKm());
        data.put("time_since_last_txn_minutes", features.getTimeSinceLastTxnMinutes());
        data.put("velocity_kmh", features.getVelocityKmh());
        data.put("merchant_reputation_score", features.getMerchantReputationScore());
        data.put("customer_age_days", features.getCustomerAgeDays());
        data.put("is_known_device", features.getIsKnownDevice() ? 1 : 0);
        data.put("is_vpn_detected", features.getIsVpnDetected() ? 1 : 0);
        data.put("device_usage_count", features.getDeviceUsageCount());
        data.put("hour_of_day", features.getHourOfDay());
        data.put("day_of_week", features.getDayOfWeek());
        data.put("merchant_category_risk", features.getMerchantCategoryRisk());
        return data;
    }
    
    private void openCircuitBreaker() {
        if (!circuitOpen) {
            circuitOpen = true;
            circuitOpenTime = System.currentTimeMillis();
            LOG.warn("NSAI circuit breaker opened due to service failure");
        }
    }
    
    /**
     * Check if NSAI service is healthy.
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(nsaiServiceUrl + "/health"))
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
     * Result from NSAI inference.
     */
    public static class NSAIPredictionResult {
        private final double fraudProbability;
        private final String explanationSummary;
        private final boolean regulatoryCompliant;
        private final String explanationId;
        
        public NSAIPredictionResult(double fraudProbability, String explanationSummary,
                                   boolean regulatoryCompliant, String explanationId) {
            this.fraudProbability = fraudProbability;
            this.explanationSummary = explanationSummary;
            this.regulatoryCompliant = regulatoryCompliant;
            this.explanationId = explanationId;
        }
        
        public static NSAIPredictionResult fallback(String transactionId) {
            return new NSAIPredictionResult(
                0.0,
                "NSAI explanation unavailable (service error)",
                false,
                "FALLBACK-" + transactionId
            );
        }
        
        public double getFraudProbability() {
            return fraudProbability;
        }
        
        public String getExplanationSummary() {
            return explanationSummary;
        }
        
        public boolean isRegulatoryCompliant() {
            return regulatoryCompliant;
        }
        
        public String getExplanationId() {
            return explanationId;
        }
    }
}
