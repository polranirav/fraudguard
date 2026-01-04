package com.frauddetection.alerts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Spring Boot application for the Fraud Alert Notification Service.
 * 
 * This microservice consumes fraud alerts from Kafka and:
 * - Triggers customer notifications (SMS, email, push)
 * - Initiates transaction holds via banking APIs
 * - Provides a REST API for alert management
 * - Exposes metrics for monitoring
 */
@SpringBootApplication
@EnableKafka
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}


