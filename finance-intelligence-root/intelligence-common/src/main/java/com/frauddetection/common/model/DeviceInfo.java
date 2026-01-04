package com.frauddetection.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Device information associated with a transaction.
 * 
 * Used for device fingerprinting and anomaly detection when
 * a transaction originates from an unrecognized device.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique device identifier (fingerprint hash).
     */
    private String deviceId;

    /**
     * Device type: MOBILE, DESKTOP, TABLET, POS_TERMINAL, ATM.
     */
    private String deviceType;

    /**
     * Operating system (e.g., "iOS 17.2", "Android 14", "Windows 11").
     */
    private String operatingSystem;

    /**
     * Browser information for web transactions.
     */
    private String browser;

    /**
     * Browser user agent string.
     */
    private String userAgent;

    /**
     * Screen resolution (e.g., "1920x1080").
     */
    private String screenResolution;

    /**
     * Timezone offset from UTC in hours.
     */
    private Integer timezoneOffset;

    /**
     * Language preference (e.g., "en-US", "fr-CA").
     */
    private String language;

    /**
     * Indicates if this device has been seen before for this customer.
     */
    private Boolean knownDevice;

    /**
     * Number of times this device has been used by the customer.
     */
    private Integer deviceUsageCount;

    /**
     * Indicates if a VPN or proxy is detected.
     */
    private Boolean vpnDetected;

    /**
     * Indicates if the device is considered high-risk.
     */
    public boolean isHighRisk() {
        return Boolean.TRUE.equals(vpnDetected) 
                || Boolean.FALSE.equals(knownDevice)
                || (deviceUsageCount != null && deviceUsageCount < 2);
    }

    @Override
    public String toString() {
        return String.format("DeviceInfo{id=%s, type=%s, os=%s, known=%s}",
                deviceId, deviceType, operatingSystem, knownDevice);
    }
}


