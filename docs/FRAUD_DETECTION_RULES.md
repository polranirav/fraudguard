# Fraud Detection Rules & Patterns

## Real-Time Financial Fraud Detection & Legacy Migration Platform

---

## Overview

This document describes the multi-layered fraud detection strategy implemented in the platform. Rules are ordered by complexity and executed in parallel where possible.

---

## Rule Categories

| Category | Description | Latency Target |
|----------|-------------|----------------|
| VELOCITY | Transaction frequency and amount monitoring | < 5ms |
| GEO | Geographic location-based detection | < 10ms |
| CEP | Complex event pattern detection | < 50ms |
| ENRICHMENT | External data lookup (Redis) | < 10ms |
| ML | Machine learning model inference | < 20ms |
| EMBEDDING | Vector similarity comparison | < 15ms |

---

## Rule 1: Velocity/Frequency Detection

### Description

Detects rapid succession of transactions that deviate from normal spending patterns. This rule identifies "velocity attacks" where fraudsters attempt to maximize unauthorized charges before detection.

### Thresholds

| Metric | Threshold | Window |
|--------|-----------|--------|
| Transaction Amount | > $2,000 | 1 minute |
| Transaction Count | > 5 transactions | 1 minute |
| Combined | Both exceeded | 1 minute |

### Risk Scoring

| Condition | Risk Contribution |
|-----------|-------------------|
| Amount only exceeded | 0.70 |
| Count only exceeded | 0.60 |
| Both exceeded | 0.90 |

### Implementation

```java
// Velocity check using Flink MapState
private VelocityResult checkVelocity(Transaction txn, long currentTime) {
    long windowStart = currentTime - VELOCITY_WINDOW_MS; // 60 seconds
    BigDecimal totalAmount = txn.getAmount();
    int txnCount = 1;

    // Sum transactions in window from state
    for (Transaction historicalTxn : recentTransactions.values()) {
        if (historicalTxn.getEventTimeMillis() >= windowStart) {
            totalAmount = totalAmount.add(historicalTxn.getAmount());
            txnCount++;
        }
    }

    boolean amountViolation = totalAmount.compareTo(VELOCITY_AMOUNT_THRESHOLD) > 0;
    boolean countViolation = txnCount > VELOCITY_COUNT_THRESHOLD;
    
    // Create triggered rule if violation detected
    ...
}
```

### State Requirements

```
MapState<Long, Transaction> recentTransactions
  - Key: Transaction timestamp (epoch milliseconds)
  - Value: Transaction object
  - TTL: 60 seconds (sliding window)
```

---

## Rule 2: Geo-Velocity (Impossible Travel)

### Description

Detects transactions occurring in geographically distant locations within a time frame that would be physically impossible to traverse. For example, a transaction in Toronto followed by one in London 20 minutes later.

### Thresholds

| Metric | Threshold | Description |
|--------|-----------|-------------|
| Maximum Velocity | 800 km/h | Typical commercial flight speed |

### Mathematical Formula

**Haversine Formula for Distance Calculation:**

```
d = 2r × arcsin(√[sin²((φ₂-φ₁)/2) + cos(φ₁)cos(φ₂)sin²((λ₂-λ₁)/2)])

Where:
  d = great-circle distance between points
  r = Earth's radius (6,371 km)
  φ₁, φ₂ = latitudes in radians
  λ₁, λ₂ = longitudes in radians
```

**Velocity Calculation:**

```
v = d / Δt

Where:
  v = required travel velocity (km/h)
  d = distance (km)
  Δt = time elapsed (hours)
```

### Risk Scoring

| Calculated Velocity | Risk Contribution |
|---------------------|-------------------|
| 800 - 1200 km/h | 0.40 - 0.60 |
| 1200 - 2000 km/h | 0.60 - 0.80 |
| > 2000 km/h | 0.80 - 1.00 |

### Implementation

```java
// Geo-velocity check using Flink ValueState
private GeoVelocityResult checkGeoVelocity(Transaction txn, long currentTime) {
    Location currentLocation = txn.getLocation();
    Location previousLocation = lastKnownLocation.value();
    Long previousTime = lastTransactionTime.value();

    if (currentLocation == null || previousLocation == null) {
        return new GeoVelocityResult(false, null, 0.0);
    }

    long timeElapsedMs = currentTime - previousTime;
    double distanceKm = previousLocation.distanceTo(currentLocation);
    double velocityKmh = previousLocation.velocityTo(currentLocation, timeElapsedMs);

    if (velocityKmh > MAX_TRAVEL_VELOCITY_KMH) {
        // Create triggered rule
        ...
    }
}
```

### State Requirements

```
ValueState<Location> lastKnownLocation
  - Value: Last transaction location (lat, lon, city, country)

ValueState<Long> lastTransactionTime
  - Value: Timestamp of last transaction (epoch milliseconds)
```

---

## Rule 3: "Testing the Waters" Pattern (CEP)

### Description

Identifies a common fraud behavior where a criminal makes several very small transactions (< $1.00) to verify a stolen card is active, followed by a large transaction (> $500) to maximize the unauthorized charge.

### Pattern Definition

```
[Small Transaction 1] -> [Small Transaction 2] -> [Small Transaction 3] -> [Large Transaction]
        (< $1.00)              (< $1.00)              (< $1.00)              (> $500.00)
         ──────────────────── Within 10 minutes ─────────────────────
```

### Thresholds

| Metric | Threshold |
|--------|-----------|
| Small Transaction | < $1.00 |
| Large Transaction | > $500.00 |
| Minimum Small Count | 3 |
| Time Window | 10 minutes |

### Risk Score

| Pattern Match | Risk Contribution |
|---------------|-------------------|
| 3 small + 1 large | 0.85 |
| 4+ small + 1 large | 0.90 |
| Pattern with VPN | 0.95 |

### Implementation (Flink CEP)

```java
// CEP Pattern definition
Pattern<Transaction, ?> testingTheWaters = Pattern.<Transaction>begin("small_txns")
    .where(new SimpleCondition<Transaction>() {
        @Override
        public boolean filter(Transaction t) {
            return t.getAmount().compareTo(new BigDecimal("1.00")) < 0
                && t.getTransactionType() == TransactionType.PURCHASE;
        }
    })
    .timesOrMore(3)
    .consecutive()
    .followedBy("large_txn")
    .where(new SimpleCondition<Transaction>() {
        @Override
        public boolean filter(Transaction t) {
            return t.getAmount().compareTo(new BigDecimal("500.00")) > 0;
        }
    })
    .within(Time.minutes(10));
```

### Contiguity Strategies

| Strategy | Behavior |
|----------|----------|
| Strict | Events must follow immediately |
| Relaxed | Non-matching events allowed between |
| Non-Deterministic | Allows multiple matches |

---

## Rule 4: IP Blacklist Check

### Description

Checks if the transaction originates from a known fraudulent IP address. Blacklist is maintained in Redis for sub-millisecond lookups.

### Data Source

Redis Set: `fraud:ip:blacklist`

### Risk Scoring

| Match Type | Risk Contribution |
|------------|-------------------|
| Exact IP match | 1.00 (auto-decline) |
| IP range match | 0.80 |
| VPN/Proxy detected | 0.60 |

### Implementation

```java
public boolean isIpBlacklisted(String ipAddress) {
    try (Jedis jedis = jedisPool.getResource()) {
        return jedis.sismember(IP_BLACKLIST_KEY, ipAddress);
    }
}
```

---

## Rule 5: Merchant Reputation Check

### Description

Evaluates the reputation of the merchant where the transaction is occurring. Low-reputation merchants may indicate higher fraud risk.

### Data Source

Redis String: `fraud:merchant:score:{merchantId}`

### Scoring

| Merchant Score | Risk Contribution |
|----------------|-------------------|
| 0.0 - 0.3 | 0.50 (high risk merchant) |
| 0.3 - 0.6 | 0.20 (moderate risk) |
| 0.6 - 1.0 | 0.00 (trusted merchant) |

---

## Rule 6: Device Fingerprint Check

### Description

Evaluates whether the transaction originates from a known or unknown device for the customer.

### Risk Indicators

| Indicator | Risk Contribution |
|-----------|-------------------|
| Unknown device | 0.30 |
| VPN detected | 0.40 |
| First-time device | 0.20 |
| Device used < 2 times | 0.25 |

### Implementation

```java
public boolean isHighRiskDevice(DeviceInfo device, String customerId) {
    return Boolean.TRUE.equals(device.getVpnDetected())
        || Boolean.FALSE.equals(device.getKnownDevice())
        || (device.getDeviceUsageCount() != null && device.getDeviceUsageCount() < 2);
}
```

---

## Composite Risk Scoring

### Formula

```
RiskScore = w₁ × RuleBasedScore + w₂ × MLScore + w₃ × EmbeddingSimilarity

Where:
  w₁ = 0.40 (Rule-based weight)
  w₂ = 0.35 (ML model weight)
  w₃ = 0.25 (Embedding similarity weight)
```

### Severity Mapping

| Composite Score | Severity | Recommended Action |
|-----------------|----------|-------------------|
| 0.90 - 1.00 | CRITICAL | Block account, urgent contact |
| 0.70 - 0.89 | HIGH | Hold transaction, request verification |
| 0.50 - 0.69 | MEDIUM | Flag for analyst review |
| 0.00 - 0.49 | LOW | Monitor only |

---

## Rule Execution Order

```
┌─────────────────────────────────────────────────────────────────┐
│                    RULE EXECUTION PIPELINE                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Transaction Arrives                                            │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              PARALLEL EXECUTION PHASE                    │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐    │   │
│  │  │Velocity │  │   Geo   │  │   IP    │  │Merchant │    │   │
│  │  │ Check   │  │Velocity │  │Blacklist│  │  Score  │    │   │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘    │   │
│  │       │            │            │            │          │   │
│  └───────┴────────────┴────────────┴────────────┴──────────┘   │
│                         │                                       │
│                         ▼                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  AGGREGATION PHASE                       │   │
│  │              Combine triggered rules                     │   │
│  │              Calculate rule-based score                  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                         │                                       │
│                         ▼                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              CEP PATTERN MATCHING                        │   │
│  │         (Asynchronous, longer window)                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                         │                                       │
│                         ▼                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              COMPOSITE SCORE CALCULATION                 │   │
│  │         Rule + ML + Embedding = Final Score              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                         │                                       │
│                         ▼                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              ALERT GENERATION                            │   │
│  │         If score > threshold, create alert               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Tuning Guidelines

### False Positive Management

| Adjustment | Effect |
|------------|--------|
| Increase velocity threshold | Fewer alerts, may miss attacks |
| Decrease geo-velocity threshold | More alerts, catch more attacks |
| Adjust CEP window | Longer = more patterns, more state |
| Weight adjustments | Balance rule vs ML contribution |

### Performance Optimization

| Setting | Recommended Value | Impact |
|---------|-------------------|--------|
| State TTL | 60 seconds | Memory usage vs detection window |
| Checkpoint interval | 60 seconds | Recovery time vs overhead |
| Parallelism | 1 slot per Kafka partition | Even workload distribution |
| Redis connection pool | 50 max connections | Latency vs resource usage |

---

## Monitoring Metrics

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Rule evaluation latency | < 50ms p99 | > 100ms |
| False positive rate | < 5% | > 10% |
| Detection rate | > 95% | < 90% |
| Alert processing time | < 100ms | > 200ms |
| State size per key | < 1MB | > 5MB |


