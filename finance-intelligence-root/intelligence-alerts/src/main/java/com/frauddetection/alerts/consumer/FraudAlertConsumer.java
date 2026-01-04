package com.frauddetection.alerts.consumer;

import com.frauddetection.common.model.FraudAlert;
import com.frauddetection.common.model.AlertSeverity;
import com.frauddetection.common.util.JsonUtils;
import com.frauddetection.alerts.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for fraud alerts.
 * 
 * Consumes alerts from the fraud-alerts topic and routes them
 * to the appropriate notification handlers based on severity.
 */
@Component
public class FraudAlertConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FraudAlertConsumer.class);

    private final NotificationService notificationService;

    public FraudAlertConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Consumes fraud alerts from Kafka.
     * 
     * @param message The JSON-encoded FraudAlert message
     */
    @KafkaListener(
            topics = "${fraud.alerts.topic:fraud-alerts}",
            groupId = "${fraud.alerts.consumer-group:alert-notification-group}")
    public void consumeAlert(String message) {
        try {
            FraudAlert alert = JsonUtils.fromJson(message, FraudAlert.class);
            
            if (alert == null) {
                LOG.warn("Received null alert after deserialization");
                return;
            }

            LOG.info("Received fraud alert: {} - Severity: {} - Score: {}", 
                    alert.getAlertId(), 
                    alert.getSeverity(), 
                    alert.getRiskScore());

            // Route based on severity
            routeAlert(alert);

        } catch (Exception e) {
            LOG.error("Error processing fraud alert: {}", e.getMessage(), e);
            // In production, send to dead-letter queue for investigation
        }
    }

    /**
     * Routes the alert to appropriate handlers based on severity.
     */
    private void routeAlert(FraudAlert alert) {
        AlertSeverity severity = alert.getSeverity();
        
        if (severity == null) {
            LOG.warn("Alert {} has no severity, defaulting to MEDIUM", alert.getAlertId());
            severity = AlertSeverity.MEDIUM;
        }

        switch (severity) {
            case CRITICAL -> handleCriticalAlert(alert);
            case HIGH -> handleHighAlert(alert);
            case MEDIUM -> handleMediumAlert(alert);
            case LOW -> handleLowAlert(alert);
        }
    }

    /**
     * Handles CRITICAL severity alerts.
     * Triggers immediate transaction hold and urgent customer contact.
     */
    private void handleCriticalAlert(FraudAlert alert) {
        LOG.warn("CRITICAL ALERT: {} for customer {}", 
                alert.getAlertId(), alert.getCustomerId());

        // Hold the transaction
        notificationService.holdTransaction(alert);

        // Urgent customer notification
        notificationService.sendUrgentNotification(alert);

        // Escalate to fraud team
        notificationService.escalateToFraudTeam(alert);
    }

    /**
     * Handles HIGH severity alerts.
     * Triggers transaction hold and customer verification.
     */
    private void handleHighAlert(FraudAlert alert) {
        LOG.warn("HIGH ALERT: {} for customer {}", 
                alert.getAlertId(), alert.getCustomerId());

        // Hold pending transactions
        notificationService.holdTransaction(alert);

        // Request customer verification
        notificationService.requestVerification(alert);
    }

    /**
     * Handles MEDIUM severity alerts.
     * Flags for analyst review without immediate action.
     */
    private void handleMediumAlert(FraudAlert alert) {
        LOG.info("MEDIUM ALERT: {} for customer {}", 
                alert.getAlertId(), alert.getCustomerId());

        // Queue for analyst review
        notificationService.queueForReview(alert);
    }

    /**
     * Handles LOW severity alerts.
     * Logs for pattern analysis, no immediate action.
     */
    private void handleLowAlert(FraudAlert alert) {
        LOG.debug("LOW ALERT: {} for customer {}", 
                alert.getAlertId(), alert.getCustomerId());

        // Just log for metrics/analysis
        notificationService.logForAnalysis(alert);
    }
}


