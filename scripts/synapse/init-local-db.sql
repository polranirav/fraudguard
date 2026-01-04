-- =============================================================================
-- Local PostgreSQL Database Initialization (Simulating Azure Synapse)
-- Real-Time Financial Fraud Detection Platform
-- =============================================================================

-- Create schema
CREATE SCHEMA IF NOT EXISTS dbo;

-- =============================================================================
-- Fact Table: Transactions
-- =============================================================================
CREATE TABLE IF NOT EXISTS dbo.fact_transactions (
    transaction_id          VARCHAR(50) PRIMARY KEY,
    customer_id             VARCHAR(50) NOT NULL,
    card_number_hash        VARCHAR(64),
    amount                  DECIMAL(18,2) NOT NULL,
    currency                VARCHAR(3) DEFAULT 'USD',
    merchant_id             VARCHAR(50),
    merchant_name           VARCHAR(255),
    merchant_category_code  VARCHAR(10),
    latitude                DECIMAL(10,6),
    longitude               DECIMAL(10,6),
    city                    VARCHAR(100),
    country_code            VARCHAR(2),
    device_id               VARCHAR(100),
    device_type             VARCHAR(50),
    transaction_type        VARCHAR(20),
    channel                 VARCHAR(20),
    event_time              TIMESTAMP WITH TIME ZONE NOT NULL,
    processing_time         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved                BOOLEAN DEFAULT TRUE,
    response_code           VARCHAR(10),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_transactions_customer ON dbo.fact_transactions(customer_id);
CREATE INDEX idx_transactions_event_time ON dbo.fact_transactions(event_time);
CREATE INDEX idx_transactions_merchant ON dbo.fact_transactions(merchant_id);

-- =============================================================================
-- Fact Table: Fraud Alerts
-- =============================================================================
CREATE TABLE IF NOT EXISTS dbo.fact_fraud_alerts (
    alert_id                VARCHAR(50) PRIMARY KEY,
    transaction_id          VARCHAR(50) NOT NULL,
    customer_id             VARCHAR(50) NOT NULL,
    severity                VARCHAR(20) NOT NULL,
    risk_score              DECIMAL(5,4),
    transaction_amount      DECIMAL(18,2),
    currency                VARCHAR(3),
    merchant_name           VARCHAR(255),
    latitude                DECIMAL(10,6),
    longitude               DECIMAL(10,6),
    city                    VARCHAR(100),
    country_code            VARCHAR(2),
    recommended_action      VARCHAR(50),
    alert_time              TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_time        TIMESTAMP WITH TIME ZONE,
    processing_latency_ms   BIGINT,
    status                  VARCHAR(20) DEFAULT 'NEW',
    assigned_analyst        VARCHAR(100),
    investigation_notes     TEXT,
    resolved_at             TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_alerts_customer ON dbo.fact_fraud_alerts(customer_id);
CREATE INDEX idx_alerts_status ON dbo.fact_fraud_alerts(status);
CREATE INDEX idx_alerts_severity ON dbo.fact_fraud_alerts(severity);
CREATE INDEX idx_alerts_alert_time ON dbo.fact_fraud_alerts(alert_time);

-- =============================================================================
-- Triggered Rules Table
-- =============================================================================
CREATE TABLE IF NOT EXISTS dbo.fact_triggered_rules (
    id                      SERIAL PRIMARY KEY,
    alert_id                VARCHAR(50) NOT NULL REFERENCES dbo.fact_fraud_alerts(alert_id),
    rule_id                 VARCHAR(50) NOT NULL,
    rule_name               VARCHAR(100),
    category                VARCHAR(50),
    trigger_reason          TEXT,
    risk_contribution       DECIMAL(5,4),
    weight                  DECIMAL(5,4),
    threshold_value         DECIMAL(18,4),
    actual_value            DECIMAL(18,4),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_triggered_rules_alert ON dbo.fact_triggered_rules(alert_id);

-- =============================================================================
-- Dimension Table: Customers
-- =============================================================================
CREATE TABLE IF NOT EXISTS dbo.dim_customers (
    customer_id             VARCHAR(50) PRIMARY KEY,
    first_name              VARCHAR(100),
    last_name               VARCHAR(100),
    email_hash              VARCHAR(64),
    phone_hash              VARCHAR(64),
    account_open_date       DATE,
    risk_tier               VARCHAR(20) DEFAULT 'STANDARD',
    credit_limit            DECIMAL(18,2),
    average_transaction     DECIMAL(18,2),
    home_city               VARCHAR(100),
    home_country            VARCHAR(2),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Dimension Table: Merchants
-- =============================================================================
CREATE TABLE IF NOT EXISTS dbo.dim_merchants (
    merchant_id             VARCHAR(50) PRIMARY KEY,
    merchant_name           VARCHAR(255),
    category_code           VARCHAR(10),
    category_name           VARCHAR(100),
    reputation_score        DECIMAL(3,2) DEFAULT 0.50,
    risk_level              VARCHAR(20) DEFAULT 'MEDIUM',
    city                    VARCHAR(100),
    country_code            VARCHAR(2),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Aggregation Table: Hourly Summary
-- =============================================================================
CREATE TABLE IF NOT EXISTS dbo.agg_fraud_hourly (
    hour                    TIMESTAMP WITH TIME ZONE PRIMARY KEY,
    alert_count             INTEGER DEFAULT 0,
    avg_risk_score          DECIMAL(5,4),
    total_amount            DECIMAL(18,2),
    critical_count          INTEGER DEFAULT 0,
    high_count              INTEGER DEFAULT 0,
    medium_count            INTEGER DEFAULT 0,
    low_count               INTEGER DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Aggregation Table: Daily Summary by Region
-- =============================================================================
CREATE TABLE IF NOT EXISTS dbo.agg_fraud_daily_region (
    date                    DATE,
    country_code            VARCHAR(2),
    city                    VARCHAR(100),
    alert_count             INTEGER DEFAULT 0,
    total_amount            DECIMAL(18,2),
    avg_risk_score          DECIMAL(5,4),
    PRIMARY KEY (date, country_code, city)
);

-- =============================================================================
-- Stored Procedure: Hourly Aggregation
-- =============================================================================
CREATE OR REPLACE FUNCTION dbo.sp_aggregate_fraud_hourly()
RETURNS void AS $$
BEGIN
    -- Truncate and reload for the last 7 days
    DELETE FROM dbo.agg_fraud_hourly 
    WHERE hour >= CURRENT_TIMESTAMP - INTERVAL '7 days';
    
    INSERT INTO dbo.agg_fraud_hourly (
        hour, alert_count, avg_risk_score, total_amount,
        critical_count, high_count, medium_count, low_count
    )
    SELECT 
        DATE_TRUNC('hour', alert_time) AS hour,
        COUNT(*) AS alert_count,
        AVG(risk_score) AS avg_risk_score,
        SUM(transaction_amount) AS total_amount,
        COUNT(*) FILTER (WHERE severity = 'CRITICAL') AS critical_count,
        COUNT(*) FILTER (WHERE severity = 'HIGH') AS high_count,
        COUNT(*) FILTER (WHERE severity = 'MEDIUM') AS medium_count,
        COUNT(*) FILTER (WHERE severity = 'LOW') AS low_count
    FROM dbo.fact_fraud_alerts
    WHERE alert_time >= CURRENT_TIMESTAMP - INTERVAL '7 days'
    GROUP BY DATE_TRUNC('hour', alert_time);
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- Stored Procedure: Daily Regional Aggregation
-- =============================================================================
CREATE OR REPLACE FUNCTION dbo.sp_aggregate_fraud_daily_region()
RETURNS void AS $$
BEGIN
    -- Truncate and reload for the last 30 days
    DELETE FROM dbo.agg_fraud_daily_region 
    WHERE date >= CURRENT_DATE - INTERVAL '30 days';
    
    INSERT INTO dbo.agg_fraud_daily_region (
        date, country_code, city, alert_count, total_amount, avg_risk_score
    )
    SELECT 
        DATE(alert_time) AS date,
        COALESCE(country_code, 'UNKNOWN') AS country_code,
        COALESCE(city, 'UNKNOWN') AS city,
        COUNT(*) AS alert_count,
        SUM(transaction_amount) AS total_amount,
        AVG(risk_score) AS avg_risk_score
    FROM dbo.fact_fraud_alerts
    WHERE alert_time >= CURRENT_DATE - INTERVAL '30 days'
    GROUP BY DATE(alert_time), country_code, city;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- View: Real-Time Dashboard Summary
-- =============================================================================
CREATE OR REPLACE VIEW dbo.vw_dashboard_summary AS
SELECT
    'Total Alerts (24h)' AS metric,
    COUNT(*)::TEXT AS value
FROM dbo.fact_fraud_alerts
WHERE alert_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
UNION ALL
SELECT
    'Critical Alerts (24h)',
    COUNT(*)::TEXT
FROM dbo.fact_fraud_alerts
WHERE alert_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
  AND severity = 'CRITICAL'
UNION ALL
SELECT
    'Avg Processing Latency (ms)',
    ROUND(AVG(processing_latency_ms))::TEXT
FROM dbo.fact_fraud_alerts
WHERE alert_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
UNION ALL
SELECT
    'Total Amount at Risk',
    '$' || TO_CHAR(SUM(transaction_amount), 'FM999,999,999.00')
FROM dbo.fact_fraud_alerts
WHERE alert_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
  AND severity IN ('CRITICAL', 'HIGH');

-- =============================================================================
-- Sample Data for Testing
-- =============================================================================
INSERT INTO dbo.dim_merchants (merchant_id, merchant_name, category_code, category_name, reputation_score, risk_level)
VALUES 
    ('MER-12345678', 'Starbucks Coffee', '5812', 'Restaurants', 0.95, 'LOW'),
    ('MER-23456789', 'Amazon', '5999', 'Miscellaneous', 0.90, 'LOW'),
    ('MER-34567890', 'Suspicious Electronics', '5732', 'Electronics', 0.25, 'HIGH'),
    ('MER-45678901', 'Gas Station ABC', '5541', 'Gas Stations', 0.75, 'MEDIUM')
ON CONFLICT (merchant_id) DO NOTHING;

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA dbo TO sqladmin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA dbo TO sqladmin;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA dbo TO sqladmin;

-- Success message
DO $$
BEGIN
    RAISE NOTICE 'Database initialization completed successfully!';
END $$;


