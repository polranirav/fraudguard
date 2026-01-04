package com.frauddetection.processing.function;

import com.frauddetection.common.dto.RiskScoreResult;
import com.frauddetection.common.model.*;
import com.frauddetection.processing.ml.MLFeatures;
import com.frauddetection.processing.ml.MLInferenceClient;
import com.frauddetection.processing.lnn.LNNInferenceClient;
import com.frauddetection.processing.causal.CausalInferenceClient;
import com.frauddetection.processing.gnn.GNNInferenceClient;
import com.frauddetection.processing.nsai.NSAIInferenceClient;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Stateful Flink ProcessFunction for fraud detection.
 * 
 * This function maintains per-user state to detect:
 * 1. Velocity violations (transaction frequency/amount thresholds)
 * 2. Geo-velocity violations (impossible travel detection)
 * 
 * State is managed entirely in-memory by Flink, eliminating
 * the need for external database lookups during hot-path processing.
 * 
 * Architectural Pattern: KeyedProcessFunction with Managed State
 * - MapState for recent transactions (sliding window)
 * - ValueState for last known location (geo-velocity)
 */
public class FraudDetectionProcessFunction 
        extends KeyedProcessFunction<String, Transaction, FraudAlert> {

    private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionProcessFunction.class);

    // Velocity thresholds
    private static final BigDecimal VELOCITY_AMOUNT_THRESHOLD = new BigDecimal("2000.00");
    private static final int VELOCITY_COUNT_THRESHOLD = 5;
    private static final long VELOCITY_WINDOW_MS = 60 * 1000; // 1 minute

    // Geo-velocity threshold (km/h)
    private static final double MAX_TRAVEL_VELOCITY_KMH = 800.0;

    // State descriptors
    private transient MapState<Long, Transaction> recentTransactions;
    private transient ValueState<Location> lastKnownLocation;
    private transient ValueState<Long> lastTransactionTime;
    private transient ValueState<Location> homeLocation; // Customer's home location
    
    // ML Inference Client
    private transient MLInferenceClient mlClient;
    private static final String ML_SERVICE_URL = System.getenv().getOrDefault(
            "ML_INFERENCE_SERVICE_URL", "http://ml-inference-service:8000");
    
    // LNN Inference Client (Phase 1)
    private transient LNNInferenceClient lnnClient;
    private static final String LNN_SERVICE_URL = System.getenv().getOrDefault(
            "LNN_SERVICE_URL", "http://lnn-service:8001");
    
    // Causal Inference Client (Phase 1)
    private transient CausalInferenceClient causalClient;
    private static final String CAUSAL_SERVICE_URL = System.getenv().getOrDefault(
            "CAUSAL_SERVICE_URL", "http://causal-service:8002");
    
    // GNN Inference Client (Phase 2)
    private transient GNNInferenceClient gnnClient;
    private static final String GNN_SERVICE_URL = System.getenv().getOrDefault(
            "GNN_SERVICE_URL", "http://gnn-service:8003");
    
    // NSAI Inference Client (Phase 2)
    private transient NSAIInferenceClient nsaiClient;
    private static final String NSAI_SERVICE_URL = System.getenv().getOrDefault(
            "NSAI_SERVICE_URL", "http://nsai-service:8004");

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        // Initialize MapState for recent transactions
        MapStateDescriptor<Long, Transaction> txnStateDescriptor = new MapStateDescriptor<>(
                "recent-transactions",
                TypeInformation.of(Long.class),
                TypeInformation.of(new TypeHint<Transaction>() {})
        );
        recentTransactions = getRuntimeContext().getMapState(txnStateDescriptor);

        // Initialize ValueState for last location
        ValueStateDescriptor<Location> locationStateDescriptor = new ValueStateDescriptor<>(
                "last-location",
                TypeInformation.of(Location.class)
        );
        lastKnownLocation = getRuntimeContext().getState(locationStateDescriptor);

        // Initialize ValueState for last transaction time
        ValueStateDescriptor<Long> timeStateDescriptor = new ValueStateDescriptor<>(
                "last-txn-time",
                TypeInformation.of(Long.class)
        );
        lastTransactionTime = getRuntimeContext().getState(timeStateDescriptor);
        
        // Initialize ValueState for home location
        ValueStateDescriptor<Location> homeLocationDescriptor = new ValueStateDescriptor<>(
                "home-location",
                TypeInformation.of(Location.class)
        );
        homeLocation = getRuntimeContext().getState(homeLocationDescriptor);
        
        // Initialize ML inference client
        mlClient = new MLInferenceClient(ML_SERVICE_URL);
        LOG.info("ML Inference Client initialized with URL: {}", ML_SERVICE_URL);
        
        // Initialize LNN inference client (Phase 1)
        lnnClient = new LNNInferenceClient(LNN_SERVICE_URL);
        LOG.info("LNN Inference Client initialized with URL: {}", LNN_SERVICE_URL);
        
        // Initialize Causal Inference client (Phase 1)
        causalClient = new CausalInferenceClient(CAUSAL_SERVICE_URL);
        LOG.info("Causal Inference Client initialized with URL: {}", CAUSAL_SERVICE_URL);
        
        // Initialize GNN Inference client (Phase 2)
        gnnClient = new GNNInferenceClient(GNN_SERVICE_URL);
        LOG.info("GNN Inference Client initialized with URL: {}", GNN_SERVICE_URL);
        
        // Initialize NSAI Inference client (Phase 2)
        nsaiClient = new NSAIInferenceClient(NSAI_SERVICE_URL);
        LOG.info("NSAI Inference Client initialized with URL: {}", NSAI_SERVICE_URL);

        LOG.info("FraudDetectionProcessFunction initialized with Phase 1 (LNN + Causal) and Phase 2 (GNN + NSAI) upgrades");
    }

    @Override
    public void processElement(Transaction txn, Context ctx, Collector<FraudAlert> out) throws Exception {
        long currentTime = txn.getEventTimeMillis();
        String customerId = txn.getCustomerId();

        List<TriggeredRule> triggeredRules = new ArrayList<>();
        double ruleScore = 0.0;

        // Clean up old transactions outside the window
        cleanupOldTransactions(currentTime);

        // Rule 1: Velocity/Frequency Check
        VelocityResult velocityResult = checkVelocity(txn, currentTime);
        if (velocityResult.isViolation()) {
            triggeredRules.add(velocityResult.getTriggeredRule());
            ruleScore = Math.max(ruleScore, velocityResult.getRiskContribution());
        }

        // Rule 2: Geo-Velocity (Impossible Travel) Check
        GeoVelocityResult geoResult = checkGeoVelocity(txn, currentTime);
        if (geoResult.isViolation()) {
            triggeredRules.add(geoResult.getTriggeredRule());
            ruleScore = Math.max(ruleScore, geoResult.getRiskContribution());
        }

        // Extract features for ML model
        MLFeatures mlFeatures = extractMLFeatures(txn, currentTime);
        
        // Get ML fraud probability (with fallback to 0.0 if service unavailable)
        double mlScore = mlClient.getFraudProbability(txn, mlFeatures);
        
        // Phase 1: Get LNN score for irregular time-series patterns
        double lnnScore = 0.0;
        double temporalPatternScore = 0.0;
        try {
            // Build transaction sequence from recent transactions
            List<Transaction> sequence = new ArrayList<>();
            for (Transaction historicalTxn : recentTransactions.values()) {
                sequence.add(historicalTxn);
            }
            
            // Get LNN prediction
            LNNInferenceClient.LNNPredictionResult lnnResult = 
                lnnClient.getFraudProbability(txn, sequence, mlFeatures);
            lnnScore = lnnResult.getFraudProbability();
            temporalPatternScore = lnnResult.getTemporalPatternScore();
            
            // If irregularity detected, boost risk score
            if (lnnResult.isIrregularityDetected() && lnnScore > 0.3) {
                lnnScore = Math.min(lnnScore + 0.1, 1.0); // Boost by 0.1, cap at 1.0
            }
        } catch (Exception e) {
            LOG.debug("LNN inference failed (non-critical): {}", e.getMessage());
            // Continue without LNN score (graceful degradation)
        }
        
        // Update state
        recentTransactions.put(currentTime, txn);
        if (txn.getLocation() != null) {
            lastKnownLocation.update(txn.getLocation());
            // Set home location on first transaction
            if (homeLocation.value() == null) {
                homeLocation.update(txn.getLocation());
            }
        }
        lastTransactionTime.update(currentTime);

        // Phase 2: Get GNN score for network fraud detection
        double gnnScore = 0.0;
        List<String> detectedPatterns = new ArrayList<>();
        String gnnExplanation = "";
        try {
            GNNInferenceClient.GNNPredictionResult gnnResult = 
                gnnClient.getNetworkFraudProbability(txn, mlFeatures, customerId);
            gnnScore = gnnResult.getNetworkFraudProbability();
            detectedPatterns = gnnResult.getDetectedPatterns();
            gnnExplanation = gnnResult.getExplanation();
            
            // If fraud network patterns detected, boost risk score
            if (!detectedPatterns.isEmpty() && !detectedPatterns.contains("individual_transaction")) {
                gnnScore = Math.min(gnnScore + 0.1, 1.0); // Boost by 0.1, cap at 1.0
            }
        } catch (Exception e) {
            LOG.debug("GNN inference failed (non-critical): {}", e.getMessage());
            // Continue without GNN score (graceful degradation)
        }
        
        // Phase 2: Get NSAI score with explanation (for high-risk transactions)
        double nsaiScore = 0.0;
        String nsaiExplanation = "";
        boolean useNSAI = false; // Only use NSAI for high-risk to avoid performance impact
        
        // Phase 1 + Phase 2: Enhanced composite scoring
        // Weighted combination: Rules (30%), ML (20%), LNN (15%), Temporal (10%), GNN (15%), NSAI (10%)
        // For initial calculation, use previous scores
        double preliminaryScore = 
            0.30 * ruleScore +
            0.20 * mlScore +
            0.15 * lnnScore +
            0.10 * temporalPatternScore +
            0.15 * gnnScore;
        
        // Use NSAI for high-risk transactions (preliminary score > 0.6)
        if (preliminaryScore > 0.6) {
            useNSAI = true;
            try {
                NSAIInferenceClient.NSAIPredictionResult nsaiResult = 
                    nsaiClient.predictWithExplanation(txn, mlFeatures);
                nsaiScore = nsaiResult.getFraudProbability();
                nsaiExplanation = nsaiResult.getExplanationSummary();
            } catch (Exception e) {
                LOG.debug("NSAI inference failed (non-critical): {}", e.getMessage());
                // Continue without NSAI score (graceful degradation)
            }
        }
        
        // Final composite score (includes NSAI if available)
        double compositeScore;
        if (useNSAI && nsaiScore > 0) {
            compositeScore = 
                0.25 * ruleScore +
                0.15 * mlScore +
                0.12 * lnnScore +
                0.08 * temporalPatternScore +
                0.12 * gnnScore +
                0.28 * nsaiScore; // Higher weight for NSAI when available
        } else {
            compositeScore = preliminaryScore;
        }
        
        // Generate alert if composite score exceeds threshold OR rules triggered
        boolean shouldAlert = !triggeredRules.isEmpty() || compositeScore > 0.5;
        
        if (shouldAlert) {
            FraudAlert alert = createFraudAlert(txn, triggeredRules, ruleScore, mlScore, 
                                                lnnScore, temporalPatternScore, gnnScore, 
                                                nsaiScore, compositeScore);
            
            // Phase 1: Generate causal explanation (async, non-blocking)
            // Only for high-risk alerts to avoid performance impact
            if (compositeScore > 0.7) {
                try {
                    CausalInferenceClient.CausalExplanation explanation = 
                        causalClient.generateExplanation(txn, mlFeatures, compositeScore, triggeredRules);
                    
                    // Log explanation for analyst review
                    LOG.info("Causal explanation for transaction {}: {}", 
                            txn.getTransactionId(), explanation.getExplanationSummary());
                    LOG.debug("Root cause: {}", explanation.getRootCause());
                } catch (Exception e) {
                    LOG.debug("Causal explanation failed (non-critical): {}", e.getMessage());
                    // Continue without explanation (graceful degradation)
                }
            }
            
            // Phase 2: Log GNN and NSAI explanations
            if (!gnnExplanation.isEmpty()) {
                LOG.info("GNN network detection for transaction {}: {}", 
                        txn.getTransactionId(), gnnExplanation);
                if (!detectedPatterns.isEmpty()) {
                    LOG.info("Detected patterns: {}", String.join(", ", detectedPatterns));
                }
            }
            
            if (!nsaiExplanation.isEmpty()) {
                LOG.info("NSAI explanation for transaction {}: {}", 
                        txn.getTransactionId(), nsaiExplanation);
            }
            
            out.collect(alert);
            LOG.warn("Fraud alert generated for customer {}: composite_score={:.2f} (Rules:{:.2f}, ML:{:.2f}, LNN:{:.2f}, GNN:{:.2f}, NSAI:{:.2f})", 
                    customerId, compositeScore, ruleScore, mlScore, lnnScore, gnnScore, nsaiScore);
        }
    }

    /**
     * Cleans up transactions older than the velocity window.
     */
    private void cleanupOldTransactions(long currentTime) throws Exception {
        long windowStart = currentTime - VELOCITY_WINDOW_MS;
        
        Iterator<Long> iterator = recentTransactions.keys().iterator();
        List<Long> keysToRemove = new ArrayList<>();
        
        while (iterator.hasNext()) {
            Long timestamp = iterator.next();
            if (timestamp < windowStart) {
                keysToRemove.add(timestamp);
            }
        }
        
        for (Long key : keysToRemove) {
            recentTransactions.remove(key);
        }
    }

    /**
     * Checks velocity violations (transaction frequency and amount).
     */
    private VelocityResult checkVelocity(Transaction txn, long currentTime) throws Exception {
        long windowStart = currentTime - VELOCITY_WINDOW_MS;
        BigDecimal totalAmount = txn.getAmount();
        int txnCount = 1;

        for (Transaction historicalTxn : recentTransactions.values()) {
            if (historicalTxn.getEventTimeMillis() >= windowStart) {
                totalAmount = totalAmount.add(historicalTxn.getAmount());
                txnCount++;
            }
        }

        boolean amountViolation = totalAmount.compareTo(VELOCITY_AMOUNT_THRESHOLD) > 0;
        boolean countViolation = txnCount > VELOCITY_COUNT_THRESHOLD;

        if (amountViolation || countViolation) {
            double riskContribution = 0.0;
            String reason;

            if (amountViolation && countViolation) {
                riskContribution = 0.9;
                reason = String.format("Both amount ($%.2f) and count (%d) thresholds exceeded in 1 minute",
                        totalAmount.doubleValue(), txnCount);
            } else if (amountViolation) {
                riskContribution = 0.7;
                reason = String.format("Amount threshold exceeded: $%.2f > $%.2f in 1 minute",
                        totalAmount.doubleValue(), VELOCITY_AMOUNT_THRESHOLD.doubleValue());
            } else {
                riskContribution = 0.6;
                reason = String.format("Transaction count exceeded: %d > %d in 1 minute",
                        txnCount, VELOCITY_COUNT_THRESHOLD);
            }

            TriggeredRule rule = TriggeredRule.builder()
                    .ruleId("VELOCITY-001")
                    .ruleName("Transaction Velocity")
                    .category(RuleCategory.VELOCITY)
                    .triggerReason(reason)
                    .riskContribution(riskContribution)
                    .weight(0.4)
                    .threshold(VELOCITY_AMOUNT_THRESHOLD.doubleValue())
                    .actualValue(totalAmount.doubleValue())
                    .build();

            return new VelocityResult(true, rule, riskContribution);
        }

        return new VelocityResult(false, null, 0.0);
    }

    /**
     * Checks geo-velocity violations (impossible travel).
     */
    private GeoVelocityResult checkGeoVelocity(Transaction txn, long currentTime) throws Exception {
        Location currentLocation = txn.getLocation();
        Location previousLocation = lastKnownLocation.value();
        Long previousTime = lastTransactionTime.value();

        if (currentLocation == null || previousLocation == null || previousTime == null) {
            return new GeoVelocityResult(false, null, 0.0);
        }

        long timeElapsedMs = currentTime - previousTime;
        if (timeElapsedMs <= 0) {
            return new GeoVelocityResult(false, null, 0.0);
        }

        double distanceKm = previousLocation.distanceTo(currentLocation);
        double velocityKmh = previousLocation.velocityTo(currentLocation, timeElapsedMs);

        if (velocityKmh > MAX_TRAVEL_VELOCITY_KMH) {
            double riskContribution = Math.min(velocityKmh / 2000.0, 1.0); // Scale to max 1.0

            String reason = String.format(
                    "Impossible travel detected: %.0f km in %.1f minutes (%.0f km/h) from %s to %s",
                    distanceKm,
                    timeElapsedMs / 60000.0,
                    velocityKmh,
                    previousLocation.getCity(),
                    currentLocation.getCity());

            TriggeredRule rule = TriggeredRule.builder()
                    .ruleId("GEO-001")
                    .ruleName("Geo-Velocity (Impossible Travel)")
                    .category(RuleCategory.GEO)
                    .triggerReason(reason)
                    .riskContribution(riskContribution)
                    .weight(0.5)
                    .threshold(MAX_TRAVEL_VELOCITY_KMH)
                    .actualValue(velocityKmh)
                    .build();

            return new GeoVelocityResult(true, rule, riskContribution);
        }

        return new GeoVelocityResult(false, null, 0.0);
    }

    /**
     * Extracts features from transaction and state for ML model inference.
     */
    private MLFeatures extractMLFeatures(Transaction txn, long currentTime) throws Exception {
        // Calculate velocity features
        long windowStart1Min = currentTime - (60 * 1000);
        long windowStart1Hour = currentTime - (60 * 60 * 1000);
        
        BigDecimal totalAmount1Min = txn.getAmount();
        BigDecimal totalAmount1Hour = txn.getAmount();
        int count1Min = 1;
        int count1Hour = 1;
        
        for (Transaction historicalTxn : recentTransactions.values()) {
            long txnTime = historicalTxn.getEventTimeMillis();
            if (txnTime >= windowStart1Min) {
                totalAmount1Min = totalAmount1Min.add(historicalTxn.getAmount());
                count1Min++;
            }
            if (txnTime >= windowStart1Hour) {
                totalAmount1Hour = totalAmount1Hour.add(historicalTxn.getAmount());
                count1Hour++;
            }
        }
        
        // Calculate time since last transaction
        Long previousTime = lastTransactionTime.value();
        double timeSinceLastTxnMinutes = (previousTime != null && previousTime > 0) 
                ? (currentTime - previousTime) / (60.0 * 1000.0) 
                : 60.0; // Default to 1 hour if no previous transaction
        
        // Calculate distance from home
        Location currentLoc = txn.getLocation();
        Location homeLoc = homeLocation.value();
        double distanceFromHomeKm = (currentLoc != null && homeLoc != null) 
                ? homeLoc.distanceTo(currentLoc) 
                : 0.0;
        
        // Calculate velocity
        Location lastLoc = lastKnownLocation.value();
        double velocityKmh = 0.0;
        if (currentLoc != null && lastLoc != null && previousTime != null && previousTime > 0) {
            velocityKmh = lastLoc.velocityTo(currentLoc, currentTime - previousTime);
        }
        
        // Extract device info
        DeviceInfo deviceInfo = txn.getDeviceInfo();
        boolean isKnownDevice = deviceInfo != null && Boolean.TRUE.equals(deviceInfo.getKnownDevice());
        boolean isVpnDetected = deviceInfo != null && Boolean.TRUE.equals(deviceInfo.getVpnDetected());
        int deviceUsageCount = deviceInfo != null && deviceInfo.getDeviceUsageCount() != null 
                ? deviceInfo.getDeviceUsageCount() 
                : 1;
        
        // Extract time features
        Instant eventTime = txn.getEventTime();
        int hourOfDay = eventTime != null ? eventTime.atZone(ZoneOffset.UTC).getHour() : 12;
        int dayOfWeek = eventTime != null ? eventTime.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() - 1 : 0;
        
        // Merchant category risk (simplified - would come from enrichment in production)
        int merchantCategoryRisk = 0; // 0=low, 1=medium, 2=high
        
        // Merchant reputation (would come from Redis enrichment)
        double merchantReputationScore = 0.7; // Default to moderate
        
        // Customer age (simplified - would come from customer database)
        int customerAgeDays = 365; // Default to 1 year
        
        return MLFeatures.builder()
                .transactionAmount(txn.getAmount().doubleValue())
                .transactionCount1Min(count1Min)
                .transactionCount1Hour(count1Hour)
                .totalAmount1Min(totalAmount1Min.doubleValue())
                .totalAmount1Hour(totalAmount1Hour.doubleValue())
                .distanceFromHomeKm(distanceFromHomeKm)
                .timeSinceLastTxnMinutes(timeSinceLastTxnMinutes)
                .velocityKmh(velocityKmh)
                .merchantReputationScore(merchantReputationScore)
                .customerAgeDays(customerAgeDays)
                .isKnownDevice(isKnownDevice)
                .isVpnDetected(isVpnDetected)
                .deviceUsageCount(deviceUsageCount)
                .hourOfDay(hourOfDay)
                .dayOfWeek(dayOfWeek)
                .merchantCategoryRisk(merchantCategoryRisk)
                .build();
    }
    
    /**
     * Creates a FraudAlert from triggered rules, ML score, Phase 1 components (LNN + Causal),
     * and Phase 2 components (GNN + NSAI).
     */
    private FraudAlert createFraudAlert(Transaction txn, List<TriggeredRule> rules, 
                                        double ruleScore, double mlScore, 
                                        double lnnScore, double temporalPatternScore,
                                        double gnnScore, double nsaiScore,
                                        double compositeScore) {
        RiskScoreResult scoreResult = RiskScoreResult.builder()
                .ruleBasedScore(ruleScore)
                .mlScore(mlScore) // Real ML score from inference service
                .embeddingScore(Math.max(lnnScore, gnnScore)) // Phase 1: LNN score, Phase 2: GNN score (use max)
                .triggeredRules(rules)
                .build();
        
        // Phase 1 + Phase 2: Set enhanced composite score
        // Includes: Rules, ML, LNN, Temporal Pattern, GNN, NSAI
        scoreResult.setCompositeScore(compositeScore);

        Instant alertTime = Instant.now();
        Instant transactionTime = txn.getEventTime();
        long latencyMs = alertTime.toEpochMilli() - transactionTime.toEpochMilli();

        return FraudAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .transactionId(txn.getTransactionId())
                .customerId(txn.getCustomerId())
                .severity(scoreResult.getSeverity())
                .riskScore(scoreResult.getCompositeScore())
                .triggeredRules(rules)
                .transactionAmount(txn.getAmount())
                .currency(txn.getCurrency())
                .merchantName(txn.getMerchantName())
                .location(txn.getLocation())
                .recommendedAction(scoreResult.getRecommendedAction())
                .alertTime(alertTime)
                .transactionTime(transactionTime)
                .processingLatencyMs(latencyMs)
                .status(AlertStatus.NEW)
                .build();
    }

    // Inner classes for rule check results
    private record VelocityResult(boolean violation, TriggeredRule triggeredRule, double riskContribution) {
        public boolean isViolation() { return violation; }
        public TriggeredRule getTriggeredRule() { return triggeredRule; }
        public double getRiskContribution() { return riskContribution; }
    }

    private record GeoVelocityResult(boolean violation, TriggeredRule triggeredRule, double riskContribution) {
        public boolean isViolation() { return violation; }
        public TriggeredRule getTriggeredRule() { return triggeredRule; }
        public double getRiskContribution() { return riskContribution; }
    }
}


