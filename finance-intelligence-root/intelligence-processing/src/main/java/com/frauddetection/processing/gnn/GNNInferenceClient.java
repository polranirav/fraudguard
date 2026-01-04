package com.frauddetection.processing.gnn;

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
 * Client for calling Graph Neural Network (GNN) inference service.
 * 
 * GNN detects fraud networks, money laundering rings, and organized crime
 * patterns by analyzing relationships between entities (customers, devices,
 * merchants, accounts).
 * 
 * Financial Industry Value:
 * - Detects fraud rings that individual transaction analysis misses
 * - Identifies shared device clusters (multiple accounts using same device)
 * - Detects circular fund flows (money laundering patterns)
 * - Network-level fraud detection (not just individual transactions)
 * 
 * Architecture:
 * - Similar to LNNInferenceClient but processes graph structures
 * - Includes circuit breaker for resilience
 * - Returns network fraud probability + detected patterns
 */
public class GNNInferenceClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(GNNInferenceClient.class);
    
    private final HttpClient httpClient;
    private final String gnnServiceUrl;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    
    // Circuit breaker state
    private boolean circuitOpen = false;
    private long circuitOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute
    
    public GNNInferenceClient(String gnnServiceUrl) {
        this.gnnServiceUrl = gnnServiceUrl;
        this.timeoutSeconds = 3; // 3 second timeout
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
    
    /**
     * Get network fraud probability from GNN model.
     * 
     * @param transaction The transaction to evaluate
     * @param mlFeatures Extracted features for the transaction
     * @param customerId Customer ID for graph context
     * @return GNN prediction result with network fraud probability and patterns
     */
    public GNNPredictionResult getNetworkFraudProbability(
            Transaction transaction,
            MLFeatures mlFeatures,
            String customerId) {
        
        // Check circuit breaker
        if (circuitOpen) {
            if (System.currentTimeMillis() - circuitOpenTime > CIRCUIT_BREAKER_TIMEOUT_MS) {
                circuitOpen = false;
                LOG.info("GNN circuit breaker closed, retrying service");
            } else {
                LOG.debug("GNN circuit breaker open, skipping GNN inference");
                return GNNPredictionResult.fallback(); // Fallback to zero score
            }
        }
        
        try {
            // Build transaction data map
            Map<String, Object> transactionData = buildTransactionData(transaction, mlFeatures);
            
            // Build graph context
            Map<String, Object> graphContext = new HashMap<>();
            graphContext.put("customer_id", customerId);
            graphContext.put("time_window", "1hour");
            graphContext.put("max_hop_distance", 3);
            
            // Build request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("transaction_id", transaction.getTransactionId());
            requestBody.put("transaction", transactionData);
            requestBody.put("graph_context", graphContext);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gnnServiceUrl + "/detect-network-fraud"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // Parse response
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                double networkFraudProbability = ((Number) responseBody.get("network_fraud_probability")).doubleValue();
                
                @SuppressWarnings("unchecked")
                List<String> detectedPatterns = (List<String>) responseBody.get("detected_patterns");
                
                @SuppressWarnings("unchecked")
                List<String> suspiciousEntities = (List<String>) responseBody.get("suspicious_entities");
                
                String explanation = (String) responseBody.get("explanation");
                
                LOG.debug("GNN inference successful for transaction {}: network_prob={}, patterns={}", 
                        transaction.getTransactionId(), networkFraudProbability, detectedPatterns);
                
                // Close circuit breaker on success
                circuitOpen = false;
                
                return new GNNPredictionResult(
                    networkFraudProbability,
                    detectedPatterns != null ? detectedPatterns : new ArrayList<>(),
                    suspiciousEntities != null ? suspiciousEntities : new ArrayList<>(),
                    explanation != null ? explanation : "No explanation available"
                );
            } else {
                LOG.warn("GNN service returned error status {}: {}", response.statusCode(), response.body());
                openCircuitBreaker();
                return GNNPredictionResult.fallback();
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("GNN inference failed for transaction {}: {}", 
                    transaction.getTransactionId(), e.getMessage());
            openCircuitBreaker();
            return GNNPredictionResult.fallback();
        }
    }
    
    /**
     * Build transaction data map for GNN service.
     */
    private Map<String, Object> buildTransactionData(Transaction txn, MLFeatures features) {
        Map<String, Object> data = new HashMap<>();
        data.put("customer_id", txn.getCustomerId());
        data.put("device_id", txn.getDeviceInfo() != null ? 
                 txn.getDeviceInfo().getDeviceId() : "unknown");
        data.put("merchant_id", txn.getMerchantName());
        data.put("account_id", txn.getCustomerId() + "-ACCOUNT"); // Simplified
        
        data.put("amount", txn.getAmount().doubleValue());
        data.put("transaction_count_1min", features.getTransactionCount1Min());
        data.put("total_amount_1min", features.getTotalAmount1Min());
        data.put("velocity_kmh", features.getVelocityKmh());
        data.put("distance_from_home_km", features.getDistanceFromHomeKm());
        
        data.put("is_known_device", features.getIsKnownDevice() ? 1 : 0);
        data.put("is_vpn_detected", features.getIsVpnDetected() ? 1 : 0);
        data.put("device_usage_count", features.getDeviceUsageCount());
        data.put("merchant_reputation_score", features.getMerchantReputationScore());
        
        // Additional graph features (simplified - would come from graph DB in production)
        data.put("customer_age_days", features.getCustomerAgeDays());
        data.put("customer_risk_score", 0.5); // Would come from customer DB
        data.put("device_age_days", 30); // Would come from device DB
        data.put("merchant_category_risk", features.getMerchantCategoryRisk());
        data.put("merchant_transaction_count", 100); // Would come from merchant DB
        data.put("merchant_fraud_rate", 0.01); // Would come from merchant DB
        data.put("account_balance", 10000.0); // Would come from account DB
        data.put("account_age_days", features.getCustomerAgeDays());
        data.put("account_transaction_count", features.getTransactionCount1Hour());
        data.put("account_risk_score", 0.5); // Would come from account DB
        data.put("account_transfer_count", 0); // Would come from transfer DB
        
        return data;
    }
    
    private void openCircuitBreaker() {
        if (!circuitOpen) {
            circuitOpen = true;
            circuitOpenTime = System.currentTimeMillis();
            LOG.warn("GNN circuit breaker opened due to service failure");
        }
    }
    
    /**
     * Check if GNN service is healthy.
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gnnServiceUrl + "/health"))
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
     * Result from GNN inference.
     */
    public static class GNNPredictionResult {
        private final double networkFraudProbability;
        private final List<String> detectedPatterns;
        private final List<String> suspiciousEntities;
        private final String explanation;
        
        public GNNPredictionResult(double networkFraudProbability, List<String> detectedPatterns,
                                 List<String> suspiciousEntities, String explanation) {
            this.networkFraudProbability = networkFraudProbability;
            this.detectedPatterns = detectedPatterns;
            this.suspiciousEntities = suspiciousEntities;
            this.explanation = explanation;
        }
        
        public static GNNPredictionResult fallback() {
            return new GNNPredictionResult(0.0, new ArrayList<>(), new ArrayList<>(), 
                    "GNN service unavailable");
        }
        
        public double getNetworkFraudProbability() {
            return networkFraudProbability;
        }
        
        public List<String> getDetectedPatterns() {
            return detectedPatterns;
        }
        
        public List<String> getSuspiciousEntities() {
            return suspiciousEntities;
        }
        
        public String getExplanation() {
            return explanation;
        }
    }
}
