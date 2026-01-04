package com.frauddetection.processing.causal;

import com.frauddetection.common.model.Transaction;
import com.frauddetection.common.model.TriggeredRule;
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
 * Client for calling Causal Inference service to generate explanations.
 * 
 * Provides counterfactual explanations and root cause analysis for fraud
 * detection decisions. Answers:
 * - "Why was this transaction flagged?"
 * - "What would have happened if the amount was different?"
 * - "What are the causal factors?"
 * 
 * Financial Industry Value:
 * - Regulatory Compliance: EU AI Act requires explainability
 * - Analyst Trust: Analysts need to understand why decisions were made
 * - Customer Service: Can explain to customers why transactions were blocked
 * 
 * Architecture:
 * - Called after fraud is detected (not in hot path)
 * - Generates explanations asynchronously or on-demand
 * - Returns causal factors and counterfactual scenarios
 */
public class CausalInferenceClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(CausalInferenceClient.class);
    
    private final HttpClient httpClient;
    private final String causalServiceUrl;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    
    // Circuit breaker state
    private boolean circuitOpen = false;
    private long circuitOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute
    
    public CausalInferenceClient(String causalServiceUrl) {
        this.causalServiceUrl = causalServiceUrl;
        this.timeoutSeconds = 5; // 5 second timeout (explanations can take longer)
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
    
    /**
     * Generate causal explanation for fraud detection decision.
     * 
     * @param transaction The transaction that was flagged
     * @param mlFeatures Features used for detection
     * @param fraudProbability The fraud probability score
     * @param triggeredRules Rules that were triggered
     * @return Causal explanation with factors and counterfactuals
     */
    public CausalExplanation generateExplanation(
            Transaction transaction,
            MLFeatures mlFeatures,
            double fraudProbability,
            List<TriggeredRule> triggeredRules) {
        
        // Check circuit breaker
        if (circuitOpen) {
            if (System.currentTimeMillis() - circuitOpenTime > CIRCUIT_BREAKER_TIMEOUT_MS) {
                circuitOpen = false;
                LOG.info("Causal inference circuit breaker closed, retrying service");
            } else {
                LOG.debug("Causal inference circuit breaker open, skipping explanation");
                return CausalExplanation.fallback(transaction.getTransactionId());
            }
        }
        
        try {
            // Build transaction features map
            Map<String, Object> featuresMap = new HashMap<>();
            featuresMap.put("transaction_amount", mlFeatures.getTransactionAmount());
            featuresMap.put("transaction_count_1min", mlFeatures.getTransactionCount1Min());
            featuresMap.put("transaction_count_1hour", mlFeatures.getTransactionCount1Hour());
            featuresMap.put("total_amount_1min", mlFeatures.getTotalAmount1Min());
            featuresMap.put("total_amount_1hour", mlFeatures.getTotalAmount1Hour());
            featuresMap.put("distance_from_home_km", mlFeatures.getDistanceFromHomeKm());
            featuresMap.put("time_since_last_txn_minutes", mlFeatures.getTimeSinceLastTxnMinutes());
            featuresMap.put("velocity_kmh", mlFeatures.getVelocityKmh());
            featuresMap.put("merchant_reputation_score", mlFeatures.getMerchantReputationScore());
            featuresMap.put("customer_age_days", mlFeatures.getCustomerAgeDays());
            featuresMap.put("is_known_device", mlFeatures.getIsKnownDevice() ? 1 : 0);
            featuresMap.put("is_vpn_detected", mlFeatures.getIsVpnDetected() ? 1 : 0);
            featuresMap.put("device_usage_count", mlFeatures.getDeviceUsageCount());
            featuresMap.put("hour_of_day", mlFeatures.getHourOfDay());
            featuresMap.put("day_of_week", mlFeatures.getDayOfWeek());
            featuresMap.put("merchant_category_risk", mlFeatures.getMerchantCategoryRisk());
            
            // Build request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("transaction_id", transaction.getTransactionId());
            requestBody.put("transaction", featuresMap);
            requestBody.put("fraud_probability", fraudProbability);
            
            // Add triggered rule names
            List<String> ruleNames = new ArrayList<>();
            for (TriggeredRule rule : triggeredRules) {
                ruleNames.add(rule.getRuleName());
            }
            requestBody.put("triggered_rules", ruleNames);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(causalServiceUrl + "/explain"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // Parse response
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                
                String rootCause = (String) responseBody.get("root_cause");
                String explanationSummary = (String) responseBody.get("explanation_summary");
                
                // Parse causal factors
                List<Map<String, Object>> factorsList = (List<Map<String, Object>>) responseBody.get("causal_factors");
                List<CausalFactor> factors = new ArrayList<>();
                if (factorsList != null) {
                    for (Map<String, Object> factorMap : factorsList) {
                        factors.add(new CausalFactor(
                            (String) factorMap.get("factor"),
                            ((Number) factorMap.get("impact")).doubleValue(),
                            (String) factorMap.get("description")
                        ));
                    }
                }
                
                // Parse counterfactuals
                List<Map<String, Object>> counterfactualsList = (List<Map<String, Object>>) responseBody.get("counterfactuals");
                List<CounterfactualScenario> counterfactuals = new ArrayList<>();
                if (counterfactualsList != null) {
                    for (Map<String, Object> cfMap : counterfactualsList) {
                        counterfactuals.add(new CounterfactualScenario(
                            (String) cfMap.get("scenario"),
                            ((Number) cfMap.get("fraud_probability")).doubleValue(),
                            ((Number) cfMap.get("change")).doubleValue()
                        ));
                    }
                }
                
                LOG.debug("Causal explanation generated for transaction {}: {}", 
                        transaction.getTransactionId(), explanationSummary);
                
                // Close circuit breaker on success
                circuitOpen = false;
                
                return new CausalExplanation(
                    transaction.getTransactionId(),
                    factors,
                    counterfactuals,
                    rootCause,
                    explanationSummary
                );
            } else {
                LOG.warn("Causal inference service returned error status {}: {}", 
                        response.statusCode(), response.body());
                openCircuitBreaker();
                return CausalExplanation.fallback(transaction.getTransactionId());
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("Causal explanation failed for transaction {}: {}", 
                    transaction.getTransactionId(), e.getMessage());
            openCircuitBreaker();
            return CausalExplanation.fallback(transaction.getTransactionId());
        }
    }
    
    private void openCircuitBreaker() {
        if (!circuitOpen) {
            circuitOpen = true;
            circuitOpenTime = System.currentTimeMillis();
            LOG.warn("Causal inference circuit breaker opened due to service failure");
        }
    }
    
    /**
     * Check if causal inference service is healthy.
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(causalServiceUrl + "/health"))
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
     * Causal explanation result.
     */
    public static class CausalExplanation {
        private final String transactionId;
        private final List<CausalFactor> causalFactors;
        private final List<CounterfactualScenario> counterfactuals;
        private final String rootCause;
        private final String explanationSummary;
        
        public CausalExplanation(String transactionId, List<CausalFactor> causalFactors,
                                List<CounterfactualScenario> counterfactuals,
                                String rootCause, String explanationSummary) {
            this.transactionId = transactionId;
            this.causalFactors = causalFactors;
            this.counterfactuals = counterfactuals;
            this.rootCause = rootCause;
            this.explanationSummary = explanationSummary;
        }
        
        public static CausalExplanation fallback(String transactionId) {
            return new CausalExplanation(
                transactionId,
                new ArrayList<>(),
                new ArrayList<>(),
                "Explanation unavailable (service error)",
                "Causal explanation service unavailable"
            );
        }
        
        // Getters
        public String getTransactionId() { return transactionId; }
        public List<CausalFactor> getCausalFactors() { return causalFactors; }
        public List<CounterfactualScenario> getCounterfactuals() { return counterfactuals; }
        public String getRootCause() { return rootCause; }
        public String getExplanationSummary() { return explanationSummary; }
    }
    
    /**
     * A causal factor contributing to fraud probability.
     */
    public static class CausalFactor {
        private final String factor;
        private final double impact;
        private final String description;
        
        public CausalFactor(String factor, double impact, String description) {
            this.factor = factor;
            this.impact = impact;
            this.description = description;
        }
        
        public String getFactor() { return factor; }
        public double getImpact() { return impact; }
        public String getDescription() { return description; }
    }
    
    /**
     * A counterfactual scenario (what-if analysis).
     */
    public static class CounterfactualScenario {
        private final String scenario;
        private final double fraudProbability;
        private final double change;
        
        public CounterfactualScenario(String scenario, double fraudProbability, double change) {
            this.scenario = scenario;
            this.fraudProbability = fraudProbability;
            this.change = change;
        }
        
        public String getScenario() { return scenario; }
        public double getFraudProbability() { return fraudProbability; }
        public double getChange() { return change; }
    }
}
