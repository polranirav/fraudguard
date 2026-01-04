package com.frauddetection.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Location model and geo-velocity calculations.
 */
class LocationTest {

    // Known distances for validation
    // Toronto to Montreal: ~540 km
    // Toronto to London UK: ~5,700 km
    // New York to Los Angeles: ~3,940 km

    @Test
    @DisplayName("Should calculate distance between Toronto and Montreal")
    void shouldCalculateDistanceTorontoMontreal() {
        // Given: Toronto and Montreal coordinates
        Location toronto = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .countryCode("CA")
                .build();

        Location montreal = Location.builder()
                .latitude(45.5017)
                .longitude(-73.5673)
                .city("Montreal")
                .countryCode("CA")
                .build();

        // When: Calculate distance
        double distance = toronto.distanceTo(montreal);

        // Then: Should be approximately 540 km (allowing 5% tolerance)
        assertTrue(distance > 500 && distance < 600, 
                "Distance should be ~540 km, but was: " + distance);
    }

    @Test
    @DisplayName("Should calculate distance between Toronto and London UK")
    void shouldCalculateDistanceTorontoLondon() {
        // Given: Toronto and London coordinates
        Location toronto = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .countryCode("CA")
                .build();

        Location london = Location.builder()
                .latitude(51.5074)
                .longitude(-0.1278)
                .city("London")
                .countryCode("GB")
                .build();

        // When: Calculate distance
        double distance = toronto.distanceTo(london);

        // Then: Should be approximately 5,700 km
        assertTrue(distance > 5400 && distance < 6000, 
                "Distance should be ~5,700 km, but was: " + distance);
    }

    @Test
    @DisplayName("Should calculate distance between New York and Los Angeles")
    void shouldCalculateDistanceNYLA() {
        // Given: NY and LA coordinates
        Location newYork = Location.builder()
                .latitude(40.7128)
                .longitude(-74.0060)
                .city("New York")
                .countryCode("US")
                .build();

        Location losAngeles = Location.builder()
                .latitude(34.0522)
                .longitude(-118.2437)
                .city("Los Angeles")
                .countryCode("US")
                .build();

        // When: Calculate distance
        double distance = newYork.distanceTo(losAngeles);

        // Then: Should be approximately 3,940 km
        assertTrue(distance > 3700 && distance < 4200, 
                "Distance should be ~3,940 km, but was: " + distance);
    }

    @Test
    @DisplayName("Should return zero distance to self")
    void shouldReturnZeroDistanceToSelf() {
        // Given: Same location
        Location location = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .build();

        // When: Calculate distance to self
        double distance = location.distanceTo(location);

        // Then: Should be 0
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    @DisplayName("Should return zero distance to null")
    void shouldReturnZeroDistanceToNull() {
        // Given: A location
        Location location = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .build();

        // When: Calculate distance to null
        double distance = location.distanceTo(null);

        // Then: Should be 0
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    @DisplayName("Should calculate velocity correctly")
    void shouldCalculateVelocity() {
        // Given: Toronto to Montreal (540 km) in 2 hours
        Location toronto = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .build();

        Location montreal = Location.builder()
                .latitude(45.5017)
                .longitude(-73.5673)
                .city("Montreal")
                .build();

        // When: Calculate velocity for 2 hour travel
        long twoHoursMs = 2 * 60 * 60 * 1000;
        double velocity = toronto.velocityTo(montreal, twoHoursMs);

        // Then: Should be approximately 270 km/h (540 km / 2 hours)
        assertTrue(velocity > 250 && velocity < 300, 
                "Velocity should be ~270 km/h, but was: " + velocity);
    }

    @Test
    @DisplayName("Should return zero velocity for zero time")
    void shouldReturnZeroVelocityForZeroTime() {
        // Given: Two locations
        Location a = Location.builder().latitude(0).longitude(0).build();
        Location b = Location.builder().latitude(1).longitude(1).build();

        // When: Calculate velocity with 0 time
        double velocity = a.velocityTo(b, 0);

        // Then: Should be 0
        assertEquals(0.0, velocity, 0.001);
    }

    @Test
    @DisplayName("Should detect impossible travel Toronto to London in 20 minutes")
    void shouldDetectImpossibleTravel() {
        // Given: Toronto to London (5,700 km) in 20 minutes
        Location toronto = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .build();

        Location london = Location.builder()
                .latitude(51.5074)
                .longitude(-0.1278)
                .city("London")
                .build();

        // When: Check if travel is impossible
        long twentyMinutesMs = 20 * 60 * 1000;
        boolean impossible = toronto.isImpossibleTravel(london, twentyMinutesMs);

        // Then: Should be impossible (would require ~17,000 km/h)
        assertTrue(impossible, "Travel from Toronto to London in 20 minutes should be impossible");
    }

    @Test
    @DisplayName("Should allow possible travel Toronto to Montreal in 2 hours")
    void shouldAllowPossibleTravel() {
        // Given: Toronto to Montreal (540 km) in 2 hours
        Location toronto = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .build();

        Location montreal = Location.builder()
                .latitude(45.5017)
                .longitude(-73.5673)
                .city("Montreal")
                .build();

        // When: Check if travel is impossible
        long twoHoursMs = 2 * 60 * 60 * 1000;
        boolean impossible = toronto.isImpossibleTravel(montreal, twoHoursMs);

        // Then: Should be possible (270 km/h is reasonable)
        assertFalse(impossible, "Travel from Toronto to Montreal in 2 hours should be possible");
    }

    @Test
    @DisplayName("Should allow flight-speed travel")
    void shouldAllowFlightSpeedTravel() {
        // Given: Toronto to London (5,700 km) in 8 hours (typical flight time)
        Location toronto = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .build();

        Location london = Location.builder()
                .latitude(51.5074)
                .longitude(-0.1278)
                .city("London")
                .build();

        // When: Check if travel is impossible
        long eightHoursMs = 8 * 60 * 60 * 1000;
        boolean impossible = toronto.isImpossibleTravel(london, eightHoursMs);

        // Then: Should be possible (~712 km/h is typical flight speed)
        assertFalse(impossible, "Flight from Toronto to London in 8 hours should be possible");
    }

    @Test
    @DisplayName("Should format location as string correctly")
    void shouldFormatLocationAsString() {
        // Given: A location
        Location location = Location.builder()
                .latitude(43.6532)
                .longitude(-79.3832)
                .city("Toronto")
                .countryCode("CA")
                .build();

        // When: Convert to string
        String result = location.toString();

        // Then: Should contain key information
        assertTrue(result.contains("Toronto"));
        assertTrue(result.contains("CA"));
        assertTrue(result.contains("43.6532"));
    }
}


