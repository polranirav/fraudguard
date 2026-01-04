package com.frauddetection.common.dto;

import com.frauddetection.common.model.AlertSeverity;
import com.frauddetection.common.model.RecommendedAction;
import com.frauddetection.common.model.TriggeredRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for the composite risk scoring result.
 * 
 * Aggregates scores from multiple detection methods:
 * - Rule-based checks (velocity, geo-velocity)
 * - Machine learning model probabilities
 * - Vector embedding similarities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoreResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Rule-based component score (0.0 to 1.0).
     */
    private Double ruleBasedScore;

    /**
     * ML model probability score (0.0 to 1.0).
     */
    private Double mlScore;

    /**
     * Embedding similarity score (0.0 to 1.0).
     * Higher = more deviation from normal behavior.
     */
    private Double embeddingScore;

    /**
     * Final composite risk score (0.0 to 1.0).
     * Calculated as: w1 * ruleBasedScore + w2 * mlScore + w3 * embeddingScore
     */
    private Double compositeScore;

    /**
     * Weight applied to rule-based score.
     */
    @Builder.Default
    private Double ruleWeight = 0.4;

    /**
     * Weight applied to ML score.
     */
    @Builder.Default
    private Double mlWeight = 0.35;

    /**
     * Weight applied to embedding score.
     */
    @Builder.Default
    private Double embeddingWeight = 0.25;

    /**
     * List of rules that contributed to the score.
     */
    @Builder.Default
    private List<TriggeredRule> triggeredRules = new ArrayList<>();

    /**
     * Determined severity based on composite score.
     */
    private AlertSeverity severity;

    /**
     * Recommended action based on the score.
     */
    private RecommendedAction recommendedAction;

    /**
     * Calculates the composite score using configured weights.
     * Formula: compositeScore = w1 * ruleBasedScore + w2 * mlScore + w3 * embeddingScore
     */
    public void calculateCompositeScore() {
        double rule = ruleBasedScore != null ? ruleBasedScore : 0.0;
        double ml = mlScore != null ? mlScore : 0.0;
        double embedding = embeddingScore != null ? embeddingScore : 0.0;

        this.compositeScore = (ruleWeight * rule) + (mlWeight * ml) + (embeddingWeight * embedding);
        
        // Determine severity based on composite score
        this.severity = determineSeverity(compositeScore);
        this.recommendedAction = determineAction(severity);
    }

    /**
     * Determines alert severity based on composite score thresholds.
     */
    private AlertSeverity determineSeverity(double score) {
        if (score >= 0.9) {
            return AlertSeverity.CRITICAL;
        } else if (score >= 0.7) {
            return AlertSeverity.HIGH;
        } else if (score >= 0.5) {
            return AlertSeverity.MEDIUM;
        } else {
            return AlertSeverity.LOW;
        }
    }

    /**
     * Determines recommended action based on severity.
     */
    private RecommendedAction determineAction(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> RecommendedAction.BLOCK_ACCOUNT;
            case HIGH -> RecommendedAction.HOLD_TRANSACTION;
            case MEDIUM -> RecommendedAction.REVIEW;
            case LOW -> RecommendedAction.MONITOR;
        };
    }

    /**
     * Adds a triggered rule to the list.
     */
    public void addTriggeredRule(TriggeredRule rule) {
        if (triggeredRules == null) {
            triggeredRules = new ArrayList<>();
        }
        triggeredRules.add(rule);
    }

    /**
     * Checks if any high-risk rules were triggered.
     */
    public boolean hasHighRiskRules() {
        return triggeredRules != null && triggeredRules.stream()
                .anyMatch(r -> r.getRiskContribution() != null && r.getRiskContribution() > 0.5);
    }
}


