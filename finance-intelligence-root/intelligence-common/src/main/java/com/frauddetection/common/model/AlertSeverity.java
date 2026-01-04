package com.frauddetection.common.model;

/**
 * Severity levels for fraud alerts.
 * Determines the urgency and routing of alerts.
 */
public enum AlertSeverity {
    
    /**
     * Low severity - minor anomaly detected.
     * Typically logged for pattern analysis but not escalated.
     */
    LOW(1, "Low priority - monitor only"),
    
    /**
     * Medium severity - suspicious activity detected.
     * Queued for analyst review within normal SLA.
     */
    MEDIUM(2, "Medium priority - analyst review required"),
    
    /**
     * High severity - likely fraud detected.
     * Immediate analyst review required, may trigger customer notification.
     */
    HIGH(3, "High priority - immediate review required"),
    
    /**
     * Critical severity - confirmed fraud pattern.
     * Automatic transaction hold and immediate escalation.
     */
    CRITICAL(4, "Critical - automatic hold and escalation");

    private final int level;
    private final String description;

    AlertSeverity(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determines if this severity requires immediate action.
     */
    public boolean requiresImmediateAction() {
        return this == HIGH || this == CRITICAL;
    }

    /**
     * Determines if this severity should trigger a transaction hold.
     */
    public boolean shouldHoldTransaction() {
        return this == CRITICAL;
    }
}


