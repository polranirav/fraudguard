-- =============================================================================
-- Azure Synapse Analytics Stored Procedures
-- Real-Time Financial Fraud Detection Platform
-- =============================================================================
-- These procedures aggregate fraud data for Power BI dashboards
-- and operational reporting.
-- =============================================================================

-- =============================================================================
-- Procedure: Hourly Fraud Summary
-- =============================================================================
-- Aggregates fraud alerts by hour for the dashboard line chart.
-- Optimized for Power BI DirectQuery mode.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_fraud_hourly_summary]
    @days_back INT = 7
AS
BEGIN
    SET NOCOUNT ON;
    
    -- Calculate the start date
    DECLARE @start_date DATETIME2 = DATEADD(DAY, -@days_back, GETUTCDATE());
    
    -- Create hourly aggregation
    SELECT 
        DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0) AS [Hour],
        COUNT(*) AS [Alert Count],
        AVG(risk_score) AS [Avg Risk Score],
        SUM(transaction_amount) AS [Total Amount],
        COUNT(CASE WHEN severity = 'CRITICAL' THEN 1 END) AS [Critical],
        COUNT(CASE WHEN severity = 'HIGH' THEN 1 END) AS [High],
        COUNT(CASE WHEN severity = 'MEDIUM' THEN 1 END) AS [Medium],
        COUNT(CASE WHEN severity = 'LOW' THEN 1 END) AS [Low],
        AVG(processing_latency_ms) AS [Avg Latency (ms)]
    FROM [dbo].[fact_fraud_alerts]
    WHERE alert_time >= @start_date
    GROUP BY DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0)
    ORDER BY [Hour] DESC;
END;
GO

-- =============================================================================
-- Procedure: Geographic Distribution
-- =============================================================================
-- Aggregates fraud alerts by region for the map visualization.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_fraud_geographic_distribution]
    @days_back INT = 30
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @start_date DATETIME2 = DATEADD(DAY, -@days_back, GETUTCDATE());
    
    SELECT 
        COALESCE(country_code, 'Unknown') AS [Country],
        COALESCE(city, 'Unknown') AS [City],
        latitude AS [Latitude],
        longitude AS [Longitude],
        COUNT(*) AS [Alert Count],
        SUM(transaction_amount) AS [Total Amount],
        AVG(risk_score) AS [Avg Risk Score],
        COUNT(CASE WHEN severity IN ('CRITICAL', 'HIGH') THEN 1 END) AS [High Risk Count]
    FROM [dbo].[fact_fraud_alerts]
    WHERE alert_time >= @start_date
      AND latitude IS NOT NULL 
      AND longitude IS NOT NULL
    GROUP BY country_code, city, latitude, longitude
    ORDER BY [Alert Count] DESC;
END;
GO

-- =============================================================================
-- Procedure: Top Triggered Rules
-- =============================================================================
-- Shows which fraud detection rules are triggering most frequently.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_top_triggered_rules]
    @days_back INT = 7,
    @top_n INT = 10
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @start_date DATETIME2 = DATEADD(DAY, -@days_back, GETUTCDATE());
    
    SELECT TOP (@top_n)
        tr.rule_name AS [Rule Name],
        tr.category AS [Category],
        COUNT(*) AS [Trigger Count],
        AVG(tr.risk_contribution) AS [Avg Risk Contribution],
        SUM(fa.transaction_amount) AS [Total Amount at Risk],
        COUNT(CASE WHEN fa.severity = 'CRITICAL' THEN 1 END) AS [Critical Alerts],
        AVG(tr.actual_value) AS [Avg Actual Value],
        AVG(tr.threshold_value) AS [Threshold]
    FROM [dbo].[fact_triggered_rules] tr
    INNER JOIN [dbo].[fact_fraud_alerts] fa ON tr.alert_id = fa.alert_id
    WHERE fa.alert_time >= @start_date
    GROUP BY tr.rule_name, tr.category
    ORDER BY [Trigger Count] DESC;
END;
GO

-- =============================================================================
-- Procedure: Real-Time Dashboard Metrics
-- =============================================================================
-- Returns key metrics for the executive dashboard.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_dashboard_metrics]
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @now DATETIME2 = GETUTCDATE();
    DECLARE @24h_ago DATETIME2 = DATEADD(HOUR, -24, @now);
    DECLARE @1h_ago DATETIME2 = DATEADD(HOUR, -1, @now);
    DECLARE @7d_ago DATETIME2 = DATEADD(DAY, -7, @now);
    
    -- Last 24 hours metrics
    SELECT 
        'Last 24 Hours' AS [Period],
        COUNT(*) AS [Total Alerts],
        COUNT(CASE WHEN severity = 'CRITICAL' THEN 1 END) AS [Critical Alerts],
        COUNT(CASE WHEN severity = 'HIGH' THEN 1 END) AS [High Alerts],
        SUM(transaction_amount) AS [Total Amount at Risk],
        AVG(processing_latency_ms) AS [Avg Processing Latency (ms)],
        COUNT(CASE WHEN status = 'CONFIRMED_FRAUD' THEN 1 END) AS [Confirmed Fraud],
        COUNT(CASE WHEN status = 'FALSE_POSITIVE' THEN 1 END) AS [False Positives]
    FROM [dbo].[fact_fraud_alerts]
    WHERE alert_time >= @24h_ago
    
    UNION ALL
    
    -- Last 1 hour metrics
    SELECT 
        'Last Hour' AS [Period],
        COUNT(*) AS [Total Alerts],
        COUNT(CASE WHEN severity = 'CRITICAL' THEN 1 END) AS [Critical Alerts],
        COUNT(CASE WHEN severity = 'HIGH' THEN 1 END) AS [High Alerts],
        SUM(transaction_amount) AS [Total Amount at Risk],
        AVG(processing_latency_ms) AS [Avg Processing Latency (ms)],
        COUNT(CASE WHEN status = 'CONFIRMED_FRAUD' THEN 1 END) AS [Confirmed Fraud],
        COUNT(CASE WHEN status = 'FALSE_POSITIVE' THEN 1 END) AS [False Positives]
    FROM [dbo].[fact_fraud_alerts]
    WHERE alert_time >= @1h_ago
    
    UNION ALL
    
    -- Last 7 days metrics
    SELECT 
        'Last 7 Days' AS [Period],
        COUNT(*) AS [Total Alerts],
        COUNT(CASE WHEN severity = 'CRITICAL' THEN 1 END) AS [Critical Alerts],
        COUNT(CASE WHEN severity = 'HIGH' THEN 1 END) AS [High Alerts],
        SUM(transaction_amount) AS [Total Amount at Risk],
        AVG(processing_latency_ms) AS [Avg Processing Latency (ms)],
        COUNT(CASE WHEN status = 'CONFIRMED_FRAUD' THEN 1 END) AS [Confirmed Fraud],
        COUNT(CASE WHEN status = 'FALSE_POSITIVE' THEN 1 END) AS [False Positives]
    FROM [dbo].[fact_fraud_alerts]
    WHERE alert_time >= @7d_ago;
END;
GO

-- =============================================================================
-- Procedure: Analyst Workload Summary
-- =============================================================================
-- Shows alert distribution across analysts for workload management.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_analyst_workload]
AS
BEGIN
    SET NOCOUNT ON;
    
    SELECT 
        COALESCE(assigned_analyst, 'Unassigned') AS [Analyst],
        COUNT(*) AS [Total Assigned],
        COUNT(CASE WHEN status = 'NEW' THEN 1 END) AS [New],
        COUNT(CASE WHEN status = 'INVESTIGATING' THEN 1 END) AS [Investigating],
        COUNT(CASE WHEN status = 'PENDING_INFO' THEN 1 END) AS [Pending Info],
        COUNT(CASE WHEN status IN ('CONFIRMED_FRAUD', 'FALSE_POSITIVE', 'CLOSED') THEN 1 END) AS [Resolved],
        AVG(DATEDIFF(MINUTE, alert_time, COALESCE(resolved_at, GETUTCDATE()))) AS [Avg Resolution Time (min)]
    FROM [dbo].[fact_fraud_alerts]
    WHERE alert_time >= DATEADD(DAY, -30, GETUTCDATE())
    GROUP BY assigned_analyst
    ORDER BY [Total Assigned] DESC;
END;
GO

-- =============================================================================
-- Procedure: Customer Risk Analysis
-- =============================================================================
-- Identifies high-risk customers for proactive outreach.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_customer_risk_analysis]
    @min_alerts INT = 3,
    @days_back INT = 30
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @start_date DATETIME2 = DATEADD(DAY, -@days_back, GETUTCDATE());
    
    SELECT 
        fa.customer_id AS [Customer ID],
        COUNT(*) AS [Alert Count],
        AVG(fa.risk_score) AS [Avg Risk Score],
        SUM(fa.transaction_amount) AS [Total Amount Flagged],
        MAX(fa.alert_time) AS [Last Alert],
        COUNT(CASE WHEN fa.severity IN ('CRITICAL', 'HIGH') THEN 1 END) AS [High Severity Alerts],
        COUNT(CASE WHEN fa.status = 'CONFIRMED_FRAUD' THEN 1 END) AS [Confirmed Frauds],
        STRING_AGG(DISTINCT tr.rule_name, ', ') AS [Triggered Rules]
    FROM [dbo].[fact_fraud_alerts] fa
    LEFT JOIN [dbo].[fact_triggered_rules] tr ON fa.alert_id = tr.alert_id
    WHERE fa.alert_time >= @start_date
    GROUP BY fa.customer_id
    HAVING COUNT(*) >= @min_alerts
    ORDER BY [Alert Count] DESC, [Avg Risk Score] DESC;
END;
GO

-- =============================================================================
-- Procedure: Merchant Risk Report
-- =============================================================================
-- Identifies merchants with high fraud rates.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_merchant_risk_report]
    @days_back INT = 30,
    @min_transactions INT = 10
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @start_date DATETIME2 = DATEADD(DAY, -@days_back, GETUTCDATE());
    
    ;WITH MerchantStats AS (
        SELECT 
            t.merchant_id,
            t.merchant_name,
            COUNT(DISTINCT t.transaction_id) AS total_transactions,
            SUM(t.amount) AS total_amount
        FROM [dbo].[fact_transactions] t
        WHERE t.event_time >= @start_date
        GROUP BY t.merchant_id, t.merchant_name
        HAVING COUNT(DISTINCT t.transaction_id) >= @min_transactions
    ),
    FraudStats AS (
        SELECT 
            a.merchant_name,
            COUNT(*) AS fraud_alerts,
            SUM(a.transaction_amount) AS fraud_amount
        FROM [dbo].[fact_fraud_alerts] a
        WHERE a.alert_time >= @start_date
        GROUP BY a.merchant_name
    )
    SELECT 
        ms.merchant_id AS [Merchant ID],
        ms.merchant_name AS [Merchant Name],
        ms.total_transactions AS [Total Transactions],
        ms.total_amount AS [Total Amount],
        COALESCE(fs.fraud_alerts, 0) AS [Fraud Alerts],
        COALESCE(fs.fraud_amount, 0) AS [Fraud Amount],
        CAST(COALESCE(fs.fraud_alerts, 0) * 100.0 / ms.total_transactions AS DECIMAL(5,2)) AS [Fraud Rate %]
    FROM MerchantStats ms
    LEFT JOIN FraudStats fs ON ms.merchant_name = fs.merchant_name
    ORDER BY [Fraud Rate %] DESC;
END;
GO

-- =============================================================================
-- Procedure: Detection Latency Analysis
-- =============================================================================
-- Analyzes system performance for SLA monitoring.
-- =============================================================================

CREATE OR ALTER PROCEDURE [dbo].[sp_latency_analysis]
    @hours_back INT = 24
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @start_date DATETIME2 = DATEADD(HOUR, -@hours_back, GETUTCDATE());
    
    SELECT 
        DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0) AS [Hour],
        COUNT(*) AS [Alerts Processed],
        AVG(processing_latency_ms) AS [Avg Latency (ms)],
        MIN(processing_latency_ms) AS [Min Latency (ms)],
        MAX(processing_latency_ms) AS [Max Latency (ms)],
        PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY processing_latency_ms) 
            OVER (PARTITION BY DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0)) AS [P50 Latency],
        PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY processing_latency_ms) 
            OVER (PARTITION BY DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0)) AS [P95 Latency],
        PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY processing_latency_ms) 
            OVER (PARTITION BY DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0)) AS [P99 Latency],
        COUNT(CASE WHEN processing_latency_ms > 100 THEN 1 END) AS [> 100ms Count],
        COUNT(CASE WHEN processing_latency_ms > 500 THEN 1 END) AS [> 500ms Count]
    FROM [dbo].[fact_fraud_alerts]
    WHERE alert_time >= @start_date
    GROUP BY DATEADD(HOUR, DATEDIFF(HOUR, 0, alert_time), 0)
    ORDER BY [Hour] DESC;
END;
GO

-- =============================================================================
-- Materialized View: Fraud Summary (for Power BI Import mode)
-- =============================================================================

CREATE OR ALTER VIEW [dbo].[vw_fraud_summary_daily]
AS
SELECT 
    CAST(alert_time AS DATE) AS [Date],
    country_code AS [Country],
    severity AS [Severity],
    status AS [Status],
    COUNT(*) AS [Alert Count],
    SUM(transaction_amount) AS [Total Amount],
    AVG(risk_score) AS [Avg Risk Score],
    AVG(processing_latency_ms) AS [Avg Latency]
FROM [dbo].[fact_fraud_alerts]
WHERE alert_time >= DATEADD(MONTH, -3, GETUTCDATE())
GROUP BY CAST(alert_time AS DATE), country_code, severity, status;
GO

-- =============================================================================
-- Grant Permissions
-- =============================================================================

GRANT EXECUTE ON [dbo].[sp_fraud_hourly_summary] TO [fraud_analyst_role];
GRANT EXECUTE ON [dbo].[sp_fraud_geographic_distribution] TO [fraud_analyst_role];
GRANT EXECUTE ON [dbo].[sp_top_triggered_rules] TO [fraud_analyst_role];
GRANT EXECUTE ON [dbo].[sp_dashboard_metrics] TO [fraud_analyst_role];
GRANT EXECUTE ON [dbo].[sp_analyst_workload] TO [fraud_analyst_role];
GRANT EXECUTE ON [dbo].[sp_customer_risk_analysis] TO [fraud_analyst_role];
GRANT EXECUTE ON [dbo].[sp_merchant_risk_report] TO [fraud_analyst_role];
GRANT EXECUTE ON [dbo].[sp_latency_analysis] TO [fraud_analyst_role];
GRANT SELECT ON [dbo].[vw_fraud_summary_daily] TO [fraud_analyst_role];

PRINT 'Stored procedures created successfully!';
GO
