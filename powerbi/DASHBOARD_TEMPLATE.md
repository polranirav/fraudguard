# Power BI Dashboard Template

## Real-Time Financial Fraud Detection Platform

---

## Dashboard Overview

This document describes the Power BI dashboard design for the Real-Time Financial Fraud Detection Platform. The dashboard provides fraud analysts and executives with real-time visibility into fraud detection metrics, trends, and investigation status.

---

## Dashboard Pages

### Page 1: Executive Summary

**Purpose:** High-level KPIs for executive oversight

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FRAUD DETECTION EXECUTIVE DASHBOARD                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │   ALERTS     │  │  CRITICAL    │  │  AMOUNT AT   │  │   AVG        │   │
│  │   (24H)      │  │  ALERTS      │  │    RISK      │  │  LATENCY     │   │
│  │              │  │              │  │              │  │              │   │
│  │    1,247     │  │     23       │  │  $2.4M       │  │   45ms       │   │
│  │   ▲ 12%      │  │   ▼ 8%       │  │   ▲ 5%       │  │   ▼ 10%      │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                 FRAUD ALERTS BY HOUR (Last 7 Days)                   │   │
│  │                                                                      │   │
│  │  120│    ╭─╮                                                        │   │
│  │     │   ╭╯ ╰╮    ╭─╮                                               │   │
│  │   80│  ╭╯   ╰╮  ╭╯ ╰╮    ╭─╮                                      │   │
│  │     │ ╭╯     ╰──╯   ╰──╮╭╯ ╰╮  ╭╮                                │   │
│  │   40│╭╯                 ╰╯   ╰──╯╰───╮                           │   │
│  │     │                               ╰────────                    │   │
│  │    0└────────────────────────────────────────────────────────    │   │
│  │      Mon     Tue     Wed     Thu     Fri     Sat     Sun         │   │
│  │                                                                      │   │
│  │  ── Total   ── Critical   ── High   ── Medium                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌────────────────────────────┐  ┌────────────────────────────────────┐    │
│  │   SEVERITY DISTRIBUTION    │  │      TOP TRIGGERED RULES           │    │
│  │                            │  │                                    │    │
│  │      ┌────────┐           │  │  Velocity Check      ████████ 342  │    │
│  │     ╱ Critical ╲           │  │  Geo-Velocity       ██████   198  │    │
│  │    ╱    18%     ╲          │  │  Testing Waters     ████     124  │    │
│  │   │     High     │         │  │  IP Blacklist       ███       89  │    │
│  │   │      32%     │         │  │  Device Anomaly     ██        45  │    │
│  │    ╲   Medium   ╱          │  │                                    │    │
│  │     ╲    35%   ╱           │  │                                    │    │
│  │      ╲  Low  ╱             │  │                                    │    │
│  │       ╲ 15% ╱              │  │                                    │    │
│  │        ╲──╱               │  │                                    │    │
│  └────────────────────────────┘  └────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Data Sources:**
- Stored Procedure: `[dbo].[sp_dashboard_metrics]`
- Stored Procedure: `[dbo].[sp_fraud_hourly_summary]`
- Stored Procedure: `[dbo].[sp_top_triggered_rules]`

**DAX Measures:**

```dax
// Total Alerts (24h)
Total Alerts 24H = 
CALCULATE(
    COUNTROWS('fact_fraud_alerts'),
    'fact_fraud_alerts'[alert_time] >= NOW() - 1
)

// Critical Alert Count
Critical Alerts = 
CALCULATE(
    COUNTROWS('fact_fraud_alerts'),
    'fact_fraud_alerts'[severity] = "CRITICAL",
    'fact_fraud_alerts'[alert_time] >= NOW() - 1
)

// Total Amount at Risk
Amount at Risk = 
CALCULATE(
    SUM('fact_fraud_alerts'[transaction_amount]),
    'fact_fraud_alerts'[severity] IN {"CRITICAL", "HIGH"},
    'fact_fraud_alerts'[alert_time] >= NOW() - 1
)

// Average Processing Latency
Avg Latency = 
AVERAGE('fact_fraud_alerts'[processing_latency_ms])

// Week-over-Week Change
WoW Change % = 
VAR CurrentWeek = [Total Alerts 24H]
VAR LastWeek = 
    CALCULATE(
        COUNTROWS('fact_fraud_alerts'),
        'fact_fraud_alerts'[alert_time] >= NOW() - 8 &&
        'fact_fraud_alerts'[alert_time] < NOW() - 1
    )
RETURN
DIVIDE(CurrentWeek - LastWeek, LastWeek, 0) * 100
```

---

### Page 2: Geographic Analysis

**Purpose:** Geographic distribution of fraud alerts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FRAUD GEOGRAPHIC DISTRIBUTION                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                      │   │
│  │                        🌍 WORLD MAP                                  │   │
│  │                                                                      │   │
│  │     ●              (Bubble size = Alert Count)                      │   │
│  │       ●●                                                            │   │
│  │         ●●●           ●                                             │   │
│  │            ●●●●●●        ●●                                         │   │
│  │                   ●●●       ●●●                                     │   │
│  │                        ●●●●    ●                                    │   │
│  │                                  ●                                   │   │
│  │                                                                      │   │
│  │                                                                      │   │
│  │  Legend: 🔴 Critical  🟠 High  🟡 Medium  🟢 Low                     │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌────────────────────────────┐  ┌────────────────────────────────────┐    │
│  │   TOP COUNTRIES            │  │      TOP CITIES                    │    │
│  │                            │  │                                    │    │
│  │  🇺🇸 USA         45%       │  │  New York       ████████  234     │    │
│  │  🇨🇦 Canada      22%       │  │  Toronto        ██████    156     │    │
│  │  🇬🇧 UK          15%       │  │  London         █████     132     │    │
│  │  🇩🇪 Germany      8%       │  │  Chicago        ████      98      │    │
│  │  🇫🇷 France       5%       │  │  Los Angeles    ███       76      │    │
│  │  Other           5%       │  │  Miami          ██        54      │    │
│  └────────────────────────────┘  └────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Visual Configuration:**
- Map Visual: Azure Maps or Bing Maps
- Latitude/Longitude fields from `fact_fraud_alerts`
- Bubble size: Alert Count
- Bubble color: Severity (conditional formatting)

---

### Page 3: Real-Time Alert Feed

**Purpose:** Live monitoring of incoming alerts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         REAL-TIME ALERT FEED                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Filter: [All Severities ▼] [All Status ▼] [Last 1 Hour ▼]  🔄 Auto-refresh│
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Time       │ Alert ID │ Customer │ Amount │ Severity│ Rule    │Status│  │
│  │────────────│──────────│──────────│────────│─────────│─────────│──────│  │
│  │ 14:32:15   │ ALT-4521 │ C-98234  │ $1,250 │🔴 CRIT  │Velocity │ NEW  │  │
│  │ 14:31:48   │ ALT-4520 │ C-76543  │   $890 │🟠 HIGH  │Geo-Vel  │ NEW  │  │
│  │ 14:31:22   │ ALT-4519 │ C-12098  │   $456 │🟡 MED   │CEP      │ASSIGN│  │
│  │ 14:30:55   │ ALT-4518 │ C-45678  │ $2,100 │🔴 CRIT  │Combined │ NEW  │  │
│  │ 14:30:12   │ ALT-4517 │ C-33456  │   $234 │🟢 LOW   │Device   │CLOSED│  │
│  │ 14:29:45   │ ALT-4516 │ C-87654  │   $567 │🟠 HIGH  │Blacklist│INVEST│  │
│  │ 14:29:18   │ ALT-4515 │ C-11223  │   $789 │🟡 MED   │Velocity │ NEW  │  │
│  │ 14:28:52   │ ALT-4514 │ C-99887  │ $1,567 │🔴 CRIT  │Geo-Vel  │ESCAL │  │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│                           [Load More...]                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Configuration:**
- Data refresh: 10 seconds (streaming dataset) or 1 minute (DirectQuery)
- Conditional formatting for severity colors
- Drill-through to Alert Detail page

---

### Page 4: Analyst Performance

**Purpose:** Workload and performance tracking

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ANALYST PERFORMANCE DASHBOARD                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │  OPEN        │  │  ASSIGNED    │  │  AVG RESOLVE │  │  FALSE POS   │   │
│  │  ALERTS      │  │  TODAY       │  │    TIME      │  │    RATE      │   │
│  │              │  │              │  │              │  │              │   │
│  │     342      │  │     156      │  │   2.4 hrs    │  │    4.2%      │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ANALYST WORKLOAD                                  │   │
│  │                                                                      │   │
│  │  Sarah Chen     ████████████████████  45  (8 Critical)              │   │
│  │  John Smith     ██████████████████    42  (5 Critical)              │   │
│  │  Maria Garcia   █████████████████     38  (6 Critical)              │   │
│  │  Alex Johnson   ████████████████      35  (4 Critical)              │   │
│  │  Unassigned     ██████████████        32  (12 Critical)             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌────────────────────────────┐  ┌────────────────────────────────────┐    │
│  │   RESOLUTION BY STATUS     │  │      DAILY TREND                   │    │
│  │                            │  │                                    │    │
│  │  Confirmed Fraud    32%    │  │   ▲                               │    │
│  │  False Positive     18%    │  │   │   ╭──╮    ╭──╮                │    │
│  │  Investigating      28%    │  │   │ ╭─╯  ╰────╯  ╰─╮ ╭─           │    │
│  │  Pending Info       12%    │  │   │╭╯              ╰─╯            │    │
│  │  New                10%    │  │   └─────────────────────────────   │    │
│  │                            │  │    Mon  Tue  Wed  Thu  Fri        │    │
│  └────────────────────────────┘  └────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Data Source:**
- Stored Procedure: `[dbo].[sp_analyst_workload]`

---

### Page 5: System Performance

**Purpose:** Technical metrics for SLA monitoring

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       SYSTEM PERFORMANCE METRICS                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │   P50        │  │   P95        │  │   P99        │  │  THROUGHPUT  │   │
│  │  LATENCY     │  │  LATENCY     │  │  LATENCY     │  │   (TPS)      │   │
│  │              │  │              │  │              │  │              │   │
│  │    32ms      │  │    78ms      │  │   145ms      │  │   52,340     │   │
│  │   ✅ < 50ms  │  │   ✅ < 100ms │  │   ⚠️ > 100ms │  │   ✅ Target  │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                 LATENCY DISTRIBUTION (Last 24h)                      │   │
│  │                                                                      │   │
│  │  Count                                                               │   │
│  │   │                                                                  │   │
│  │   │  ████                                                           │   │
│  │   │  ████████                                                       │   │
│  │   │  ██████████████                                                 │   │
│  │   │  ███████████████████████                                        │   │
│  │   │  ████████████████████████████████                               │   │
│  │   │  ██████████████████████████████████████████████                 │   │
│  │   └──────────────────────────────────────────────────────────────   │   │
│  │      0-25   25-50  50-75  75-100 100-150 150-200 200+  (ms)         │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌────────────────────────────┐  ┌────────────────────────────────────┐    │
│  │   SLA COMPLIANCE           │  │      ERROR RATE                    │    │
│  │                            │  │                                    │    │
│  │  Target: 99.9%             │  │   Processing: 0.02%               │    │
│  │                            │  │   Enrichment: 0.15%               │    │
│  │     ████████████░░         │  │   Persistence: 0.05%              │    │
│  │       99.87%               │  │                                    │    │
│  │                            │  │   Total: 0.22% (Target < 1%)      │    │
│  └────────────────────────────┘  └────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Data Source:**
- Stored Procedure: `[dbo].[sp_latency_analysis]`
- Prometheus/Grafana metrics via Azure Monitor

---

## Data Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           POWER BI DATA MODEL                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐                    ┌─────────────────┐                │
│  │ dim_customers   │───────1:*──────────│ fact_fraud_     │                │
│  │ ─────────────── │                    │     alerts      │                │
│  │ customer_id (PK)│                    │ ───────────────  │                │
│  │ first_name      │                    │ alert_id (PK)   │                │
│  │ last_name       │                    │ transaction_id  │                │
│  │ risk_tier       │                    │ customer_id (FK)│                │
│  │ credit_limit    │                    │ severity        │                │
│  └─────────────────┘                    │ risk_score      │                │
│                                         │ transaction_amt │                │
│  ┌─────────────────┐                    │ merchant_name   │                │
│  │ dim_merchants   │───────1:*──────────│ alert_time      │                │
│  │ ─────────────── │                    │ status          │                │
│  │ merchant_id (PK)│                    │ ...             │                │
│  │ merchant_name   │                    └────────┬────────┘                │
│  │ category_code   │                             │                         │
│  │ reputation_score│                             │ 1:*                     │
│  └─────────────────┘                             │                         │
│                                         ┌────────┴────────┐                │
│  ┌─────────────────┐                    │ fact_triggered  │                │
│  │ dim_date        │──────────────────  │     rules       │                │
│  │ ─────────────── │                    │ ───────────────  │                │
│  │ date (PK)       │                    │ id (PK)         │                │
│  │ year            │                    │ alert_id (FK)   │                │
│  │ month           │                    │ rule_id         │                │
│  │ day_of_week     │                    │ rule_name       │                │
│  │ is_weekend      │                    │ category        │                │
│  │ ...             │                    │ risk_contrib    │                │
│  └─────────────────┘                    └─────────────────┘                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Connection Settings

### DirectQuery (Real-Time)

```
Server: synw-frauddetect-prod.sql.azuresynapse.net
Database: sqldw
Authentication: Azure Active Directory - Integrated
```

### Import Mode (Historical)

- Schedule refresh: Every 15 minutes
- Incremental refresh: Last 30 days full, 3 months incremental

---

## Deployment

1. **Development:** Connect to dev Synapse workspace
2. **Staging:** Deploy to Power BI staging workspace
3. **Production:** Promote to production workspace with row-level security

### Row-Level Security

```dax
// Analyst can only see their assigned alerts
[assigned_analyst] = USERPRINCIPALNAME()
|| [status] = "UNASSIGNED"
|| ISBLANK([assigned_analyst])
```

---

## Refresh Schedule

| Dataset | Mode | Refresh Frequency |
|---------|------|-------------------|
| Executive Summary | DirectQuery | Real-time |
| Geographic Analysis | Import | Every 15 minutes |
| Alert Feed | Streaming | Real-time push |
| Analyst Performance | DirectQuery | Real-time |
| System Performance | Import | Every 5 minutes |


