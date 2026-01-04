package com.frauddetection.processing.agent;

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
 * Client for calling Rule Generation Agent service.
 * 
 * This client is used by background processes to:
 * 1. Analyze fraud patterns
 * 2. Generate new detection rules
 * 3. Test rules on historical data
 * 
 * CRITICAL: This client does NOT deploy rules autonomously.
 * All rules require human approval via the approval endpoint.
 * 
 * Financial Industry Compliance:
 * - Zero autonomous deployments
 * - Complete audit trail
 * - Human-in-the-loop approval required
 */
public class RuleGenerationClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(RuleGenerationClient.class);
    
    private final HttpClient httpClient;
    private final String agentServiceUrl;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    
    public RuleGenerationClient(String agentServiceUrl) {
        this.agentServiceUrl = agentServiceUrl;
        this.timeoutSeconds = 30; // 30 second timeout (rule generation can take time)
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
    
    /**
     * Analyze fraud patterns and identify gaps.
     * 
     * @param fraudData Historical fraud transaction data
     * @return List of detected patterns
     */
    public PatternAnalysisResult analyzePatterns(List<Map<String, Object>> fraudData) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("fraud_data", fraudData);
            requestBody.put("time_window_days", 30);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentServiceUrl + "/analyze-patterns"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> patterns = (List<Map<String, Object>>) responseBody.get("patterns");
                
                return new PatternAnalysisResult(
                    patterns != null ? patterns : new ArrayList<>(),
                    ((Number) responseBody.getOrDefault("total_patterns", 0)).intValue()
                );
            } else {
                LOG.warn("Pattern analysis returned error status {}: {}", response.statusCode(), response.body());
                return PatternAnalysisResult.empty();
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("Pattern analysis failed: {}", e.getMessage());
            return PatternAnalysisResult.empty();
        }
    }
    
    /**
     * Generate rule for a detected pattern.
     * 
     * CRITICAL: This only generates the rule - it does NOT deploy it.
     * All rules require human approval.
     * 
     * @param pattern Detected fraud pattern
     * @return Generated rule (status: generated, requires approval)
     */
    public GeneratedRule generateRule(Map<String, Object> pattern) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("pattern", pattern);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentServiceUrl + "/generate-rule"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> rule = (Map<String, Object>) responseBody.get("rule");
                
                return new GeneratedRule(
                    (String) rule.get("rule_id"),
                    (String) rule.get("rule_name"),
                    (String) rule.get("description"),
                    (String) rule.get("logic"),
                    (String) rule.get("status")
                );
            } else {
                LOG.warn("Rule generation returned error status {}: {}", response.statusCode(), response.body());
                return GeneratedRule.empty();
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("Rule generation failed: {}", e.getMessage());
            return GeneratedRule.empty();
        }
    }
    
    /**
     * Test rule on historical data.
     * 
     * @param rule Generated rule
     * @param historicalData Historical transaction data
     * @return Test results with performance metrics
     */
    public RuleTestResult testRule(GeneratedRule rule, List<Map<String, Object>> historicalData) {
        try {
            Map<String, Object> ruleMap = new HashMap<>();
            ruleMap.put("rule_id", rule.getRuleId());
            ruleMap.put("rule_name", rule.getRuleName());
            ruleMap.put("description", rule.getDescription());
            ruleMap.put("logic", rule.getLogic());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("rule", ruleMap);
            requestBody.put("historical_data", historicalData);
            
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentServiceUrl + "/test-rule"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> testResults = (Map<String, Object>) responseBody.get("test_results");
                
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = (Map<String, Object>) testResults.get("test_results");
                
                return new RuleTestResult(
                    (String) testResults.get("rule_id"),
                    ((Number) metrics.get("true_positive_rate")).doubleValue(),
                    ((Number) metrics.get("false_positive_rate")).doubleValue(),
                    ((Number) metrics.get("coverage")).doubleValue(),
                    (String) responseBody.get("test_status")
                );
            } else {
                LOG.warn("Rule testing returned error status {}: {}", response.statusCode(), response.body());
                return RuleTestResult.empty();
            }
            
        } catch (IOException | InterruptedException e) {
            LOG.warn("Rule testing failed: {}", e.getMessage());
            return RuleTestResult.empty();
        }
    }
    
    /**
     * Result from pattern analysis.
     */
    public static class PatternAnalysisResult {
        private final List<Map<String, Object>> patterns;
        private final int totalPatterns;
        
        public PatternAnalysisResult(List<Map<String, Object>> patterns, int totalPatterns) {
            this.patterns = patterns;
            this.totalPatterns = totalPatterns;
        }
        
        public static PatternAnalysisResult empty() {
            return new PatternAnalysisResult(new ArrayList<>(), 0);
        }
        
        public List<Map<String, Object>> getPatterns() { return patterns; }
        public int getTotalPatterns() { return totalPatterns; }
    }
    
    /**
     * Generated rule (requires approval).
     */
    public static class GeneratedRule {
        private final String ruleId;
        private final String ruleName;
        private final String description;
        private final String logic;
        private final String status;
        
        public GeneratedRule(String ruleId, String ruleName, String description, 
                           String logic, String status) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.description = description;
            this.logic = logic;
            this.status = status;
        }
        
        public static GeneratedRule empty() {
            return new GeneratedRule("", "", "", "", "error");
        }
        
        public String getRuleId() { return ruleId; }
        public String getRuleName() { return ruleName; }
        public String getDescription() { return description; }
        public String getLogic() { return logic; }
        public String getStatus() { return status; }
    }
    
    /**
     * Rule test results.
     */
    public static class RuleTestResult {
        private final String ruleId;
        private final double truePositiveRate;
        private final double falsePositiveRate;
        private final double coverage;
        private final String testStatus;
        
        public RuleTestResult(String ruleId, double truePositiveRate, double falsePositiveRate,
                            double coverage, String testStatus) {
            this.ruleId = ruleId;
            this.truePositiveRate = truePositiveRate;
            this.falsePositiveRate = falsePositiveRate;
            this.coverage = coverage;
            this.testStatus = testStatus;
        }
        
        public static RuleTestResult empty() {
            return new RuleTestResult("", 0.0, 0.0, 0.0, "error");
        }
        
        public String getRuleId() { return ruleId; }
        public double getTruePositiveRate() { return truePositiveRate; }
        public double getFalsePositiveRate() { return falsePositiveRate; }
        public double getCoverage() { return coverage; }
        public String getTestStatus() { return testStatus; }
    }
}
