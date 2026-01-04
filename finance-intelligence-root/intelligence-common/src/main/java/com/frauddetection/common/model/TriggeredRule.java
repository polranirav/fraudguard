package com.frauddetection.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Represents a fraud detection rule that was triggered.
 * Part of the FraudAlert to document which rules contributed to the alert.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggeredRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the rule.
     */
    private String ruleId;

    /**
     * Human-readable name of the rule.
     */
    private String ruleName;

    /**
     * Category of the rule (VELOCITY, GEO, CEP, ML, etc.).
     */
    private RuleCategory category;

    /**
     * Description of why the rule was triggered.
     */
    private String triggerReason;

    /**
     * Contribution of this rule to the overall risk score (0.0 to 1.0).
     */
    private Double riskContribution;

    /**
     * Weight of this rule in the composite score calculation.
     */
    private Double weight;

    /**
     * Threshold that was exceeded (if applicable).
     */
    private Double threshold;

    /**
     * Actual value that triggered the rule.
     */
    private Double actualValue;

    /**
     * Formatted description of the rule trigger.
     */
    public String getFormattedTrigger() {
        if (threshold != null && actualValue != null) {
            return String.format("%s: %.2f (threshold: %.2f)", ruleName, actualValue, threshold);
        }
        return String.format("%s: %s", ruleName, triggerReason);
    }
}


