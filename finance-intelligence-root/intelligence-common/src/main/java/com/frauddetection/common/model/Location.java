package com.frauddetection.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Geographic location information for transaction geo-velocity analysis.
 * 
 * Used to detect "impossible travel" scenarios where transactions occur
 * in geographically distant locations within a time frame that would
 * be physically impossible to traverse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Location implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Earth's radius in kilometers for Haversine calculations.
     */
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Latitude coordinate in decimal degrees.
     */
    private double latitude;

    /**
     * Longitude coordinate in decimal degrees.
     */
    private double longitude;

    /**
     * City name.
     */
    private String city;

    /**
     * State or province.
     */
    private String state;

    /**
     * ISO 3166-1 alpha-2 country code (e.g., "US", "CA", "GB").
     */
    private String countryCode;

    /**
     * Postal or ZIP code.
     */
    private String postalCode;

    /**
     * IP address associated with the transaction location.
     */
    private String ipAddress;

    /**
     * Calculates the distance to another location using the Haversine formula.
     * 
     * The Haversine formula determines the great-circle distance between two points
     * on a sphere given their longitudes and latitudes:
     * 
     * d = 2r × arcsin(√[sin²((φ₂-φ₁)/2) + cos(φ₁)cos(φ₂)sin²((λ₂-λ₁)/2)])
     * 
     * @param other The other location to calculate distance to
     * @return Distance in kilometers
     */
    public double distanceTo(Location other) {
        if (other == null) {
            return 0.0;
        }

        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLatRad = Math.toRadians(other.latitude - this.latitude);
        double deltaLonRad = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculates the velocity required to travel to another location in the given time.
     * 
     * @param other The other location
     * @param timeElapsedMs Time elapsed in milliseconds
     * @return Velocity in km/h, or 0 if time is 0 or negative
     */
    public double velocityTo(Location other, long timeElapsedMs) {
        if (timeElapsedMs <= 0) {
            return 0.0;
        }
        double distanceKm = distanceTo(other);
        double timeHours = timeElapsedMs / (1000.0 * 60 * 60);
        return distanceKm / timeHours;
    }

    /**
     * Checks if travel to another location within the given time is physically possible.
     * Uses 800 km/h as the maximum velocity threshold (typical commercial flight speed).
     * 
     * @param other The other location
     * @param timeElapsedMs Time elapsed in milliseconds
     * @return true if the travel velocity exceeds the physical threshold
     */
    public boolean isImpossibleTravel(Location other, long timeElapsedMs) {
        double velocity = velocityTo(other, timeElapsedMs);
        return velocity > 800.0; // km/h - max commercial flight speed
    }

    @Override
    public String toString() {
        return String.format("Location{lat=%.4f, lon=%.4f, city=%s, country=%s}",
                latitude, longitude, city, countryCode);
    }
}


