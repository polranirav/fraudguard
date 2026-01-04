package com.frauddetection.alerts.api;

import com.frauddetection.common.model.AlertStatus;
import com.frauddetection.common.model.FraudAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API controller for fraud alert management.
 * 
 * Provides endpoints for:
 * - Viewing alert details
 * - Updating alert status
 * - Analyst assignment
 * - Investigation notes
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private static final Logger LOG = LoggerFactory.getLogger(AlertController.class);

    // In-memory store for demo purposes
    // In production, this would be backed by a database
    private final Map<String, FraudAlert> alertStore = new ConcurrentHashMap<>();

    /**
     * Get all alerts with optional status filter.
     */
    @GetMapping
    public ResponseEntity<List<FraudAlert>> getAlerts(
            @RequestParam(required = false) AlertStatus status) {
        LOG.info("Fetching alerts with status filter: {}", status);
        
        List<FraudAlert> alerts = alertStore.values().stream()
                .filter(a -> status == null || a.getStatus() == status)
                .toList();
        
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get a specific alert by ID.
     */
    @GetMapping("/{alertId}")
    public ResponseEntity<FraudAlert> getAlert(@PathVariable String alertId) {
        LOG.info("Fetching alert: {}", alertId);
        
        FraudAlert alert = alertStore.get(alertId);
        if (alert == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alert);
    }

    /**
     * Update alert status.
     */
    @PatchMapping("/{alertId}/status")
    public ResponseEntity<FraudAlert> updateStatus(
            @PathVariable String alertId,
            @RequestBody StatusUpdateRequest request) {
        LOG.info("Updating alert {} status to {}", alertId, request.status());
        
        FraudAlert alert = alertStore.get(alertId);
        if (alert == null) {
            return ResponseEntity.notFound().build();
        }
        
        alert.setStatus(request.status());
        alertStore.put(alertId, alert);
        
        return ResponseEntity.ok(alert);
    }

    /**
     * Assign analyst to alert.
     */
    @PatchMapping("/{alertId}/assign")
    public ResponseEntity<FraudAlert> assignAnalyst(
            @PathVariable String alertId,
            @RequestBody AssignmentRequest request) {
        LOG.info("Assigning analyst {} to alert {}", request.analyst(), alertId);
        
        FraudAlert alert = alertStore.get(alertId);
        if (alert == null) {
            return ResponseEntity.notFound().build();
        }
        
        alert.setAssignedAnalyst(request.analyst());
        alert.setStatus(AlertStatus.ASSIGNED);
        alertStore.put(alertId, alert);
        
        return ResponseEntity.ok(alert);
    }

    /**
     * Add investigation notes.
     */
    @PostMapping("/{alertId}/notes")
    public ResponseEntity<FraudAlert> addNotes(
            @PathVariable String alertId,
            @RequestBody NotesRequest request) {
        LOG.info("Adding notes to alert {}", alertId);
        
        FraudAlert alert = alertStore.get(alertId);
        if (alert == null) {
            return ResponseEntity.notFound().build();
        }
        
        String existingNotes = alert.getInvestigationNotes();
        String newNotes = (existingNotes != null ? existingNotes + "\n" : "") +
                "[" + Instant.now() + "] " + request.notes();
        alert.setInvestigationNotes(newNotes);
        alertStore.put(alertId, alert);
        
        return ResponseEntity.ok(alert);
    }

    /**
     * Resolve alert (confirm fraud or false positive).
     */
    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<FraudAlert> resolveAlert(
            @PathVariable String alertId,
            @RequestBody ResolutionRequest request) {
        LOG.info("Resolving alert {} as {}", alertId, request.resolution());
        
        FraudAlert alert = alertStore.get(alertId);
        if (alert == null) {
            return ResponseEntity.notFound().build();
        }
        
        AlertStatus newStatus = "FRAUD".equalsIgnoreCase(request.resolution())
                ? AlertStatus.CONFIRMED_FRAUD
                : AlertStatus.FALSE_POSITIVE;
        
        alert.setStatus(newStatus);
        alert.setInvestigationNotes(
                (alert.getInvestigationNotes() != null ? alert.getInvestigationNotes() + "\n" : "") +
                "[" + Instant.now() + "] Resolved as: " + request.resolution() +
                (request.notes() != null ? " - " + request.notes() : ""));
        alertStore.put(alertId, alert);
        
        return ResponseEntity.ok(alert);
    }

    /**
     * Get alert statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<AlertStats> getStats() {
        long total = alertStore.size();
        long newAlerts = alertStore.values().stream()
                .filter(a -> a.getStatus() == AlertStatus.NEW)
                .count();
        long investigating = alertStore.values().stream()
                .filter(a -> a.getStatus() == AlertStatus.INVESTIGATING)
                .count();
        long confirmedFraud = alertStore.values().stream()
                .filter(a -> a.getStatus() == AlertStatus.CONFIRMED_FRAUD)
                .count();
        long falsePositives = alertStore.values().stream()
                .filter(a -> a.getStatus() == AlertStatus.FALSE_POSITIVE)
                .count();
        
        return ResponseEntity.ok(new AlertStats(
                total, newAlerts, investigating, confirmedFraud, falsePositives));
    }

    // Internal method to store alerts from Kafka consumer
    public void storeAlert(FraudAlert alert) {
        alertStore.put(alert.getAlertId(), alert);
    }

    // Request/Response DTOs
    public record StatusUpdateRequest(AlertStatus status) {}
    public record AssignmentRequest(String analyst) {}
    public record NotesRequest(String notes) {}
    public record ResolutionRequest(String resolution, String notes) {}
    public record AlertStats(
            long total,
            long newAlerts,
            long investigating,
            long confirmedFraud,
            long falsePositives) {}
}


