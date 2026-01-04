# **Architectural Transformation of Real-Time Financial Intelligence: A Java-Centric Framework for High-Performance Fraud Detection and Legacy Cloud Migration**

The global financial services sector is currently navigating a period of unprecedented structural reorganization. Large-scale retail banks, exemplified by institutions such as the Royal Bank of Canada (RBC) and TD Bank, are under intense pressure to modernize their aging data infrastructure. The historical reliance on slow, batch-oriented legacy systems, primarily built on Hadoop and on-premise Oracle databases, has become a significant liability in an era characterized by instantaneous digital transactions and increasingly sophisticated financial crime.1 The dual challenge facing these organizations is the requirement to decommission expensive, high-maintenance legacy hardware while simultaneously implementing real-time fraud detection capabilities that can identify and stop malicious credit card activity within millisecond windows.1 This report examines the implementation of a "Java-First" Real-Time Financial Intelligence Platform, a cloud-native architecture leveraging Apache Flink, Apache Kafka, and Azure Synapse to bridge the gap between historical data analysis and immediate operational response.

## **The Impetus for Modernization: From Legacy Constraints to Cloud Latency Requirements**

Legacy architectures in the banking sector often follow a traditional multi-tier model where transaction data is captured in an Online Transaction Processing (OLTP) database—typically Oracle—and then moved via batch ETL (Extract, Transform, Load) processes into a Hadoop-based data lake for analytical processing.1 This model inherently introduces a "latency gap," where data used for risk scoring may be hours or even days old. In modern digital banking, such delays are unacceptable; a fraudster can drain an account or maximize a credit limit long before a batch job has finished processing the previous day’s logs.1

The migration to a cloud-native environment, specifically Azure, allows for the replacement of static, vertically scaled hardware with elastic, horizontally scaled compute resources. However, the move is not a simple "lift and shift" exercise. It requires a fundamental shift in data processing philosophy from batch to stream.1 The "Java-First" approach prioritizes the use of a robust, type-safe programming language capable of handling complex business logic while maintaining the high performance necessary for low-latency event processing.3

| Dimension | Legacy Infrastructure (Hadoop/Oracle) | Modern Intelligence Platform (Flink/Azure) |
| :---- | :---- | :---- |
| **Data Processing** | Batch-oriented, scheduled intervals 1 | Continuous event streaming 1 |
| **Detection Speed** | Reactive (post-transaction) 3 | Proactive (in-flight or sub-second) 1 |
| **Scalability** | Fixed clusters, high capital expenditure 2 | Elastic Kubernetes/Cloud-native scaling 6 |
| **State Management** | External database lookups 6 | In-memory managed keyed state 6 |
| **Fault Tolerance** | Checkpoint-based recovery (slow) 8 | Exactly-once distributed snapshots 6 |

## **High-Performance Tech Stack and Project Architecture**

The architecture of the Real-Time Financial Intelligence Platform is designed to ensure decoupling, resilience, and horizontal scalability. At its core, the system utilizes a "Kappa" architecture where both real-time and historical data are processed through the same streaming pipeline, ensuring consistency across different analytical timeframes.1

### **The Streaming Backbone: Apache Kafka**

Apache Kafka serves as the distributed log and messaging foundation for the entire platform. By ingesting events from diverse sources—mobile apps, web gateways, and point-of-sale terminals—Kafka provides a durable, replayable stream that decouples the production of transaction data from its consumption by the fraud engine.1 Kafka's ability to handle throughput in excess of one million events per second is critical for banking operations, particularly during peak periods such as high-volume shopping seasons or tax cycles.3 The partitioning of topics allows for parallel processing, where each partition is handled by a separate consumer instance, enabling the system to scale with the volume of transactions.3

### **Stateful Stream Processing: Apache Flink**

If Kafka is the nervous system of the platform, Apache Flink is the brain. Flink is uniquely suited for fraud detection because of its sophisticated state management.6 Unlike traditional processing engines that require an external database lookup for every event, Flink maintains a local, managed state for each user account directly within the processing nodes. This allows the system to store and update a user's behavioral profile—such as their average transaction amount or last five login locations—at memory speeds.6

The use of KeyedProcessFunction combined with Managed State and Event-Time Timers represents the architectural "gold standard" for this domain.9 This pattern allows the system to:

1. **Handle Out-of-Order Data:** Utilizing watermarking to ensure that transactions are processed in the order they actually occurred, even if they arrive at the system delayed due to network issues.8  
2. **Maintain Fault Tolerance:** Using distributed checkpoints that save the system state to Azure Blob Storage, ensuring that if a node fails, the system can resume with "exactly-once" consistency.6  
3. **Execute Fine-Grained Logic:** Tracking time-based behaviors, such as inactivity detection or rapid-fire transactions, with a level of precision that batch systems cannot match.9

### **Low-Latency Enrichment: Redis and Vector Search**

While Flink handles streaming state, certain "static" or "reference" data—such as IP blacklists, merchant reputation scores, or high-dimensional user embeddings—is stored in Redis.3 Modern implementations integrate Redis with vector similarity search capabilities. In this model, transactions are transformed into high-dimensional vectors (embeddings) using transformer-based AI models. These vectors represent complex, non-linear relationships between the customer, device, and merchant.3

The risk evaluation function integrates these embeddings to calculate a composite score:

$$RiskScore \= w\_1 \\cdot RuleBasedChecks \+ w\_2 \\cdot MLProbabilities \+ w\_3 \\cdot \\cos(\\theta)\_{Similarity}$$

where $\\cos(\\theta)\_{Similarity}$ represents the cosine similarity between the current transaction embedding and the customer’s historical behavior vectors stored in Redis.3 This hybrid approach provides sub-100 millisecond scoring latency while maintaining higher precision than simple rule-based systems.3

### **Analytic Persistence and Reporting: Azure Synapse**

The long-term storage and heavy analytical requirements of the platform are met by Azure Synapse Analytics.2 Synapse acts as a unified data warehouse and big data analytics service, integrating directly with Azure Data Lake Storage (ADLS) Gen2.15 While Flink handles the immediate detection of fraud, Synapse is used for:

* **Deep Historical Auditing:** Storing millions of rows of transaction history for regulatory compliance and retrospective investigations.2  
* **Model Training:** Serving as the source for training new machine learning models using historical fraud patterns.1  
* **Business Intelligence:** Providing the backend for Power BI dashboards that allow fraud analysts to visualize trends and investigate specific alerts.17

## **Fraud Detection Logic: Implementation of Millisecond Risk Scoring**

The intelligence platform implements a multi-layered detection strategy, progressing from simple velocity checks to complex event sequencing and behavioral anomaly detection.4

### **Velocity and Frequency Monitoring**

Velocity checks are the first line of defense, identifying rapid successions of transactions that deviate from normal spending rates.8 For instance, if a user exceeds a threshold of $2,000 in transactions within a single minute, the system triggers an immediate alert.8 In Java, this is implemented by maintaining a MapState\<Long, Transaction\> in Flink where the key is the timestamp and the value is the transaction object. During each new event, the system iterates through the state, sums the amounts within the specific time window (e.g., last 60 seconds), and triggers an alert if the sum exceeds the threshold.8

### **Location-Based and Geo-Velocity Checks**

Geo-velocity detection identifies "impossible travel" scenarios—transactions occurring in geographically distant locations within a time frame that would be physically impossible to traverse.8 For example, a transaction in Toronto followed by one in London, UK, twenty minutes later indicates that the card details have been compromised.8 The system stores the latitude and longitude of the last successful transaction in Flink's ValueState. When a new transaction arrives, the distance is calculated using the Haversine formula:

$$d \= 2r \\arcsin\\left(\\sqrt{\\sin^2\\left(\\frac{\\phi\_2 \- \\phi\_1}{2}\\right) \+ \\cos(\\phi\_1)\\cos(\\phi\_2)\\sin^2\\left(\\frac{\\lambda\_2 \- \\lambda\_1}{2}\\right)}\\right)$$

The required velocity is then computed as $v \= d/\\Delta t$. If the velocity exceeds a set limit (e.g., 800 km/h), the transaction is flagged as high-risk.8

### **Complex Event Processing (CEP) for Sequential Patterns**

Many fraudulent behaviors involve a sequence of seemingly benign events that, when viewed together, signal clear intent.4 A common pattern is "Testing the Waters," where a fraudster makes several very small purchases (under $1.00) to verify a card is active before attempting a large-scale transfer.19

Using Flink's Pattern API, this sequence is defined declaratively in Java:

Java

Pattern\<Transaction,?\> testingTheWaters \= Pattern.\<Transaction\>begin("small\_txns")  
   .where(new SimpleCondition\<Transaction\>() {  
        @Override  
        public boolean filter(Transaction t) { return t.getAmount() \< 1.00; }  
    })  
   .timesOrMore(3).consecutive()  
   .followedBy("large\_txn")  
   .where(new SimpleCondition\<Transaction\>() {  
        @Override  
        public boolean filter(Transaction t) { return t.getAmount() \> 500.00; }  
    })  
   .within(Time.minutes(10));

This pattern detects if three small transactions are followed by a large one within a ten-minute window.19 Flink supports different contiguity strategies, such as "Strict" (events must follow each other immediately) or "Relaxed" (ignoring non-matching events between pattern matches), allowing for nuanced rule definitions that minimize false positives.19

## **Legacy Migration Strategy: Transitioning from Hadoop and Oracle**

The decommissioning of legacy systems like Hadoop and Oracle is a complex orchestration task that must be handled with extreme care to avoid data loss or operational downtime.2

### **Data Extraction and Orchestration**

For the migration of historical fact tables, institutions utilize a combination of network-based and physical transfer methods. Smaller dimension tables and ongoing incremental updates are often handled via Azure Data Factory (ADF), which provides built-in connectors for Oracle and can orchestrate complex extraction pipelines.22 For petabyte-scale historical data where network bandwidth is a bottleneck, the use of Azure Data Box is recommended.22 This involves extracting data to a physical storage device provided by Microsoft, which is then shipped to an Azure data center for high-speed upload to ADLS Gen2.22

In the context of Hadoop decommissioning, Apache Sqoop is the primary tool for moving data into the Azure environment.26 Sqoop supports incremental imports using lastmodified timestamps or row IDs, ensuring that only new data since the last migration run is transferred.28

| Migration Tool | Source | Target | Primary Use Case |
| :---- | :---- | :---- | :---- |
| **Apache Sqoop** | Hadoop (HDFS) | Azure Blob / ADLS Gen2 | Bulk migration of legacy data lakes.26 |
| **SSMA for Oracle** | Oracle DB | Azure Synapse | Automated schema and code translation.2 |
| **Azure Data Factory** | Diverse (Cloud/On-prem) | Azure Synapse / SQL DB | ETL orchestration and near-real-time ingestion.16 |
| **Oracle GoldenGate** | Oracle DB | Kafka / Event Hubs | Real-time CDC (Change Data Capture) for parallel running.16 |
| **AzCopy** | Local Files | Azure Storage | High-speed command-line file transfer.22 |

### **Schema and Procedural Translation**

A significant hurdle in the Oracle-to-Synapse migration is the incompatibility of PL/SQL with the T-SQL used in Azure Synapse Analytics.2 Legacy systems often contain complex stored procedures, triggers, and custom data types that do not have direct equivalents in a cloud-native data warehouse.2 The SQL Server Migration Assistant (SSMA) for Oracle is used to automate the translation of schemas and some procedural code.2 However, complex logic often requires manual re-engineering into Azure Synapse stored procedures or Data Factory pipelines.2

To validate the performance of the new environment during migration, it is critical to use the EXPLAIN command on representative SQL statements in Synapse. This command identifies potential incompatibilities or performance bottlenecks early in the migration lifecycle, allowing for design adjustments before the full data load.2

## **Enterprise-Grade Project Implementation: Structure and Build End-to-End**

A professional, banking-grade Java project must be modular, maintainable, and designed for continuous integration and deployment (CI/CD).32

### **Maven Multi-Module Folder Structure**

The platform is organized into a Maven multi-module project to enforce a clear separation of concerns. This structure allows the core business logic to remain independent of the specific infrastructure adapters.32

**finance-intelligence-root/**

* **pom.xml:** The parent POM that manages shared dependencies, versions, and plugins.32  
* **intelligence-common:** Contains shared Plain Old Java Objects (POJOs), Data Transfer Objects (DTOs), and custom exceptions.32  
* **intelligence-ingestion:** Handles Kafka producer logic and connectivity to legacy ingestion gateways.11  
* **intelligence-processing:** The core module containing Apache Flink jobs, CEP patterns, and stateful logic.5  
* **intelligence-enrichment:** Manages the integration with Redis and other external lookup services.11  
* **intelligence-persistence:** Contains the sinks for Azure Synapse and ADLS Gen2, handling formatting tasks such as Parquet serialization.15  
* **intelligence-alerts:** A Spring Boot-based microservice that consumes from the Kafka fraud-alerts topic to trigger notifications or hold transactions.3

### **The Build and Packaging Process**

In Flink applications, the intelligence-processing module must be packaged into an "uber-JAR" (or "fat JAR") using the maven-shade-plugin.5 This ensures that all dependencies required at runtime are included in a single artifact that can be submitted to the Flink cluster. Every Flink job starts with a standard Java main method, which initializes the StreamExecutionEnvironment.5 This environment is the engine that drives the data flow, and it is here that the Kafka sources, processing functions, and sinks are wired together.5

For high performance, the platform relies on Flink’s internal POJO serializer. A Java class is recognized as a POJO by Flink if it has public fields or public getters and setters for all private fields, and a default (no-argument) constructor.5 Adhering to these conventions allows Flink to bypass expensive Java reflection during serialization, significantly reducing the CPU overhead of state management.5

## **Infrastructure Deployment: Cloud-Native Orchestration on Azure**

The deployment of a high-performance streaming platform requires a robust orchestration layer, typically provided by Azure Kubernetes Service (AKS).11

### **Kubernetes and the Flink Operator**

Modern Flink deployments on AKS utilize the Kubernetes Operator pattern.7 The Flink Kubernetes Operator allows for the declarative management of Flink jobs using custom Kubernetes resource definitions (CRDs). Instead of manually managing pods for JobManagers and TaskManagers, developers define the desired state in a YAML manifest.7 The operator then ensures that the cluster reconciles with this state, handling tasks such as spawning TaskManager pods, managing config maps, and providing an ingress for the Flink web UI.7

A typical FlinkDeployment manifest specifies:

* **Image:** The location of the uber-JAR in the Azure Container Registry (ACR).40  
* **Flink Configuration:** Memory settings, the number of task slots per manager, and the checkpointing interval.7  
* **Job Specification:** The URI of the JAR file and the desired parallelism.7

### **Observability and Monitoring**

For a financial intelligence platform, monitoring is not just about uptime; it is about performance transparency. Prometheus and Grafana are integrated into the AKS cluster to track system health.11

* **Prometheus:** Scrapes metrics from the Flink TaskManagers, such as CPU utilization, memory pressure, and backpressure.39  
* **Grafana:** Provides real-time dashboards for infrastructure loads and job-specific metrics, such as the number of transactions processed per second or the latency of fraud checks.39

## **Security, Governance, and Regulatory Compliance**

In a banking environment, security and governance are not optional add-ons but core architectural requirements.1

### **Data Lineage with Microsoft Purview**

Microsoft Purview is the central governance tool used to track data movement and lineage across the platform.41 When an Azure Data Factory or Synapse pipeline runs, metadata about the source and destination is automatically pushed to the Purview Data Map.42 This lineage tracking allows auditors to follow the flow of a sensitive data point—such as a credit card number—from its origin in the legacy Oracle database, through the Kafka-Flink streaming pipeline, and into the final Synapse reporting layer.41 If the automated lineage is incomplete, custom entries can be added via the Apache Atlas API or Purview’s manual lineage interface.45

### **Sensitivity Labeling and Data Protection**

Purview’s automated scanning engine identifies sensitive data types, such as credit card numbers or social security numbers, and applies sensitivity labels.41 These labels are tags that travel with the data and can trigger protection settings across the Azure ecosystem.47 For instance, a column labeled "Confidential" in a Synapse table can be automatically masked for users who do not have the appropriate security clearance, ensuring that PII (Personally Identifiable Information) is never exposed to unauthorized personnel during fraud investigations.41

### **Network Isolation and Identity Management**

Security is further enhanced through network isolation and managed identities. All services—Kafka, Flink, and Synapse—run within a secured Virtual Network (VNET).12 Communication between components is handled via Azure Private Links, ensuring that data never traverses the public internet.12 Furthermore, the platform utilizes System-Assigned Managed Identities (MIs) for authentication. This eliminates the need to store database credentials or API keys in code or configuration files, as Azure handles the identity and access management (IAM) automatically.12

## **Visualization and Investigation: Empowering the Fraud Analyst**

The final layer of the intelligence platform is the visualization layer, where data is turned into actionable insights for human investigators.1

### **Real-Time Power BI Dashboards**

Power BI dashboards provide a real-time view of fraud attempts and trends. For sub-second visibility, Power BI can consume directly from Azure Stream Analytics, which filters and aggregates the Flink-processed stream.18 These "streaming datasets" power line charts that show the number of fraudulent transactions per hour or map visuals showing the origin of suspicious login attempts.52

To optimize performance for historical analysis, Power BI developers utilize Data Analysis Expressions (DAX) to build fraud logic directly into the model.52 For instance, a measure for ReversalRate can be defined to track how many transactions are being reversed relative to total charges:

Code snippet

ReversalRate \= DIVIDE(  
    CALCULATE(COUNTROWS(fct\_reversals)),  
    CALCULATE(COUNTROWS(fct\_recharges))  
)  
\`\`\`.

\#\#\# Performance Optimization with Stored Procedures

When dealing with massive datasets in Synapse, Power BI's performance is maintained by aggregating data directly in SQL before it enters the report model.\[17\] Rather than pulling 12 million rows of raw transactions into Power BI, the report executes a stored procedure in Synapse that returns only the aggregated summary.\[17\] This centralized logic ensures that reports load faster and visual interactions remain smooth, while the heavy computational lifting is handled by the Massively Parallel Processing (MPP) architecture of Synapse.\[17\]

\#\# Conclusion: Synthesizing High Performance and Enterprise Security

The "Java-First" Real-Time Financial Intelligence Platform represents a paradigm shift for enterprise banking. By moving away from slow, batch-oriented legacy systems to a cloud-native streaming architecture, institutions can finally match the speed of modern financial fraud.\[1, 2, 3, 4\] The integration of Apache Flink for stateful processing, Apache Kafka for durable event streaming, and Azure Synapse for massive-scale analytics creates a unified framework that is both highly performant and rigorously governed.\[1, 2, 3, 8, 16\]

The success of such a platform depends on a structured, multi-module approach that prioritizes code quality, state management efficiency, and a well-orchestrated migration strategy.\[2, 8, 9, 32\] As banks continue their journey to the cloud, the ability to weave real-time intelligence into every transaction—while maintaining full regulatory compliance and data lineage—will be the primary differentiator in the fight against global financial crime.\[3, 16, 19, 41\]

#### **Works cited**

1. Real-Time Fraud Detection and Prevention \- Confluent, accessed December 26, 2025, [https://www.confluent.io/use-case/fraud-detection/](https://www.confluent.io/use-case/fraud-detection/)  
2. Design and performance for Oracle migrations \- Azure Synapse Analytics | Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/synapse-analytics/migration-guides/oracle/1-design-performance-migration](https://learn.microsoft.com/en-us/azure/synapse-analytics/migration-guides/oracle/1-design-performance-migration)  
3. real-time fraud detection in digital banking: an ai-driven event streaming architecture with java, kafka, redis, and embeddings \- ResearchGate, accessed December 26, 2025, [https://www.researchgate.net/publication/395399771\_REAL-TIME\_FRAUD\_DETECTION\_IN\_DIGITAL\_BANKING\_AN\_AI-DRIVEN\_EVENT\_STREAMING\_ARCHITECTURE\_WITH\_JAVA\_KAFKA\_REDIS\_AND\_EMBEDDINGS](https://www.researchgate.net/publication/395399771_REAL-TIME_FRAUD_DETECTION_IN_DIGITAL_BANKING_AN_AI-DRIVEN_EVENT_STREAMING_ARCHITECTURE_WITH_JAVA_KAFKA_REDIS_AND_EMBEDDINGS)  
4. How Does Real-Time Streaming Prevent Fraud in Banking and Payments? \- Confluent, accessed December 26, 2025, [https://www.confluent.io/blog/real-time-streaming-prevents-fraud/](https://www.confluent.io/blog/real-time-streaming-prevents-fraud/)  
5. Dataflow Programming: Building a Java Pipeline With Flink and Kafka \- Confluent, accessed December 26, 2025, [https://www.confluent.io/blog/flink-dataflow-programming-java-pipeline/](https://www.confluent.io/blog/flink-dataflow-programming-java-pipeline/)  
6. Use Cases \- Apache Flink, accessed December 26, 2025, [https://flink.apache.org/what-is-flink/use-cases/](https://flink.apache.org/what-is-flink/use-cases/)  
7. Get Running with Apache Flink on Kubernetes, part 1 of 2 \- Decodable, accessed December 26, 2025, [https://www.decodable.co/blog/get-running-with-apache-flink-on-kubernetes-1](https://www.decodable.co/blog/get-running-with-apache-flink-on-kubernetes-1)  
8. Real Time Fraud Detection Using Apache Flink — Part 1 | by Yugen.ai \- Medium, accessed December 26, 2025, [https://medium.com/yugen-ai-technology-blog/real-time-fraud-detection-using-apache-flink-part-1-f4b1c9d6e952](https://medium.com/yugen-ai-technology-blog/real-time-fraud-detection-using-apache-flink-part-1-f4b1c9d6e952)  
9. This One Apache Flink Pattern Changed Everything: Stateful Event-Driven Processing with KeyedProcessFunction | by CortexFlow | The Software Frontier | Medium, accessed December 26, 2025, [https://medium.com/the-software-frontier/this-one-apache-flink-pattern-changed-everything-stateful-event-driven-processing-with-1fd92f64fe79](https://medium.com/the-software-frontier/this-one-apache-flink-pattern-changed-everything-stateful-event-driven-processing-with-1fd92f64fe79)  
10. How to build a multi-agent orchestrator using Flink and Kafka \- SD Times, accessed December 26, 2025, [https://sdtimes.com/how-to-build-a-multi-agent-orchestrator-using-flink-and-kafka-2/](https://sdtimes.com/how-to-build-a-multi-agent-orchestrator-using-flink-and-kafka-2/)  
11. jorgermduarte/real-time-data-architecture-kafka-flink-dw-k8s: Real-time data processing architecture using Apache Kafka, Flink, and Kubernetes. This project demonstrates how to build a scalable and resilient pipeline for streaming data, performing ETL with Flink, and storing the processed data in a Data \- GitHub, accessed December 26, 2025, [https://github.com/jorgermduarte/real-time-data-architecture-kafka-flink-dw-k8s](https://github.com/jorgermduarte/real-time-data-architecture-kafka-flink-dw-k8s)  
12. Building a Serverless, Real-Time Fraud Detection System on AWS | by Sendoa Moronta, accessed December 26, 2025, [https://aws.plainenglish.io/building-a-serverless-real-time-fraud-detection-system-on-aws-1f2eb9c53243](https://aws.plainenglish.io/building-a-serverless-real-time-fraud-detection-system-on-aws-1f2eb9c53243)  
13. How to build a Fraud Detection System using Redis, accessed December 26, 2025, [https://redis.io/tutorials/howtos/frauddetection/](https://redis.io/tutorials/howtos/frauddetection/)  
14. Oracle to Azure Synapse Analytics Cloud Migration Checklist \- Next Pathway, accessed December 26, 2025, [https://www.nextpathway.com/resources/oracle-to-azure-synapse-checklist](https://www.nextpathway.com/resources/oracle-to-azure-synapse-checklist)  
15. Azure Data Factory Data Flow vs SQL script in Synapse : r/dataengineering \- Reddit, accessed December 26, 2025, [https://www.reddit.com/r/dataengineering/comments/1i4yvgd/azure\_data\_factory\_data\_flow\_vs\_sql\_script\_in/](https://www.reddit.com/r/dataengineering/comments/1i4yvgd/azure_data_factory_data_flow_vs_sql_script_in/)  
16. Real-Time data from Oracle OCI db into azure \- Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/answers/questions/5567361/real-time-data-from-oracle-oci-db-into-azure](https://learn.microsoft.com/en-us/answers/questions/5567361/real-time-data-from-oracle-oci-db-into-azure)  
17. Creating an Optimized Fraud Detection Dashboard with Power BI ..., accessed December 26, 2025, [https://medium.com/@mirandalxj/creating-an-optimized-fraud-detection-dashboard-with-power-bi-aggregating-data-with-stored-26dcb4190ea3](https://medium.com/@mirandalxj/creating-an-optimized-fraud-detection-dashboard-with-power-bi-aggregating-data-with-stored-26dcb4190ea3)  
18. stream-analytics-real-time-fraud-detection.md \- GitHub, accessed December 26, 2025, [https://github.com/MicrosoftDocs/azure-docs/blob/main/articles/stream-analytics/stream-analytics-real-time-fraud-detection.md?plain=1](https://github.com/MicrosoftDocs/azure-docs/blob/main/articles/stream-analytics/stream-analytics-real-time-fraud-detection.md?plain=1)  
19. Real Time Fraud Detection Using Apache Flink — Part 2 \- Canso, accessed December 26, 2025, [https://www.canso.ai/post/real-time-fraud-detection-using-apache-flink----part-2](https://www.canso.ai/post/real-time-fraud-detection-using-apache-flink----part-2)  
20. Fraud Detection with Apache Flink on Ververica \- GitHub, accessed December 26, 2025, [https://github.com/ververica/ververica-cep-fraud-detection](https://github.com/ververica/ververica-cep-fraud-detection)  
21. A Guide to Preventing Fraud Detection in Real-Time with Apache Flink \- Alibaba Cloud, accessed December 26, 2025, [https://www.alibabacloud.com/blog/a-guide-to-preventing-fraud-detection-in-real-time-with-apache-flink\_602024](https://www.alibabacloud.com/blog/a-guide-to-preventing-fraud-detection-in-real-time-with-apache-flink_602024)  
22. Data migration, ETL, and load for Oracle migrations \- Azure Synapse ..., accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/synapse-analytics/migration-guides/oracle/2-etl-load-migration-considerations](https://learn.microsoft.com/en-us/azure/synapse-analytics/migration-guides/oracle/2-etl-load-migration-considerations)  
23. Copy data to and from Oracle \- Azure Data Factory & Azure Synapse | Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/data-factory/connector-oracle](https://learn.microsoft.com/en-us/azure/data-factory/connector-oracle)  
24. How to Set Up Batch ETL Jobs Between Oracle and Azure Data Lake? \- Airbyte, accessed December 26, 2025, [https://airbyte.com/data-engineering-resources/setup-batch-etl-jobs-between-oracle-and-azure-data-lake](https://airbyte.com/data-engineering-resources/setup-batch-etl-jobs-between-oracle-and-azure-data-lake)  
25. Tools for Oracle data warehouse migration to Azure Synapse Analytics \- Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/synapse-analytics/migration-guides/oracle/6-microsoft-third-party-migration-tools](https://learn.microsoft.com/en-us/azure/synapse-analytics/migration-guides/oracle/6-microsoft-third-party-migration-tools)  
26. Sqoop User Guide (v1.4.6), accessed December 26, 2025, [https://sqoop.apache.org/docs/1.4.6/SqoopUserGuide.html](https://sqoop.apache.org/docs/1.4.6/SqoopUserGuide.html)  
27. Import data from Oracle Database to HDFS using Sqoop \- Princeton IT Services, accessed December 26, 2025, [https://princetonits.com/blog/technology/import-data-from-oracle-database-to-hdfs-using-sqoop/](https://princetonits.com/blog/technology/import-data-from-oracle-database-to-hdfs-using-sqoop/)  
28. Incremental Data Import into HDFS \- Tencent Cloud, accessed December 26, 2025, [https://www.tencentcloud.com/document/product/1026/31158](https://www.tencentcloud.com/document/product/1026/31158)  
29. How to write a Sqoop Job using shell script \- Stack Overflow, accessed December 26, 2025, [https://stackoverflow.com/questions/34757960/how-to-write-a-sqoop-job-using-shell-script](https://stackoverflow.com/questions/34757960/how-to-write-a-sqoop-job-using-shell-script)  
30. 28 Connecting to Microsoft Azure Data Lake \- Oracle Help Center, accessed December 26, 2025, [https://docs.oracle.com/en/middleware/goldengate/big-data/19.1/gadbd/azure-data-lake-gen1-adls-gen-1.html](https://docs.oracle.com/en/middleware/goldengate/big-data/19.1/gadbd/azure-data-lake-gen1-adls-gen-1.html)  
31. Oracle to Azure Synapse Data Warehouse Migration Validation \- Datagaps, accessed December 26, 2025, [https://www.datagaps.com/blog/oracle-to-azure-synapse-data-warehouse-migration-validation/](https://www.datagaps.com/blog/oracle-to-azure-synapse-data-warehouse-migration-validation/)  
32. Maven Multi-Module Project Guide: Structure, Examples & Real-World Challenges \- Medium, accessed December 26, 2025, [https://medium.com/@khileshsahu2007/maven-multi-module-project-guide-structure-examples-real-world-challenges-d867ff22a0a8](https://medium.com/@khileshsahu2007/maven-multi-module-project-guide-structure-examples-real-world-challenges-d867ff22a0a8)  
33. Multi-Module Project with Maven | Baeldung, accessed December 26, 2025, [https://www.baeldung.com/maven-multi-module](https://www.baeldung.com/maven-multi-module)  
34. fnmps/multilayer-java-template \- GitHub, accessed December 26, 2025, [https://github.com/fnmps/multilayer-java-template](https://github.com/fnmps/multilayer-java-template)  
35. Folder structure \- backend java \- DEV Community, accessed December 26, 2025, [https://dev.to/imajenasyon/folder-structure-backend-java-2402](https://dev.to/imajenasyon/folder-structure-backend-java-2402)  
36. 2.6 Tutorial Example Directory Structure \- Java Platform, Enterprise Edition, accessed December 26, 2025, [https://docs.oracle.com/javaee/7/tutorial/usingexamples006.htm](https://docs.oracle.com/javaee/7/tutorial/usingexamples006.htm)  
37. apache kafka \- Flink using multiple structures of data in java \- Stack Overflow, accessed December 26, 2025, [https://stackoverflow.com/questions/75323626/flink-using-multiple-structures-of-data-in-java](https://stackoverflow.com/questions/75323626/flink-using-multiple-structures-of-data-in-java)  
38. Quickstart: Deploy an Azure Kubernetes Service (AKS) cluster using the Azure portal, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/aks/learn/quick-kubernetes-deploy-portal](https://learn.microsoft.com/en-us/azure/aks/learn/quick-kubernetes-deploy-portal)  
39. Running Apache Flink on Kubernetes \- Data Engineering Works \- GitHub Pages, accessed December 26, 2025, [https://karlchris.github.io/data-engineering/projects/flink-k8s/](https://karlchris.github.io/data-engineering/projects/flink-k8s/)  
40. Tutorial \- Deploy an application to Azure Kubernetes Service (AKS), accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/aks/tutorial-kubernetes-deploy-application](https://learn.microsoft.com/en-us/azure/aks/tutorial-kubernetes-deploy-application)  
41. Microsoft Purview Data Catalog Guide: Features, Use Cases & Integration | by Kanerika Inc, accessed December 26, 2025, [https://medium.com/@kanerika/microsoft-purview-data-catalog-guide-features-use-cases-integration-2c937f86924f](https://medium.com/@kanerika/microsoft-purview-data-catalog-guide-features-use-cases-integration-2c937f86924f)  
42. Metadata and lineage from Azure Synapse Analytics \- Microsoft Purview, accessed December 26, 2025, [https://docs.azure.cn/en-us/purview/how-to-lineage-azure-synapse-analytics](https://docs.azure.cn/en-us/purview/how-to-lineage-azure-synapse-analytics)  
43. Metadata and lineage from Azure Synapse Analytics into Microsoft Purview, accessed December 26, 2025, [https://learn.microsoft.com/en-us/purview/data-map-lineage-azure-synapse-analytics](https://learn.microsoft.com/en-us/purview/data-map-lineage-azure-synapse-analytics)  
44. Connect Azure Data Factory to Microsoft Purview, accessed December 26, 2025, [https://learn.microsoft.com/en-us/purview/data-map-lineage-azure-data-factory](https://learn.microsoft.com/en-us/purview/data-map-lineage-azure-data-factory)  
45. Data lineage user guide for classic Microsoft Purview Data Catalog, accessed December 26, 2025, [https://learn.microsoft.com/en-us/purview/data-gov-classic-lineage-user-guide](https://learn.microsoft.com/en-us/purview/data-gov-classic-lineage-user-guide)  
46. Data lineage \- Cloud Adoption Framework \- Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/scenarios/cloud-scale-analytics/govern-lineage](https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/scenarios/cloud-scale-analytics/govern-lineage)  
47. Learn about sensitivity labels, accessed December 26, 2025, [https://learn.microsoft.com/en-us/purview/sensitivity-labels](https://learn.microsoft.com/en-us/purview/sensitivity-labels)  
48. Learn about the default sensitivity labels and policies to protect your data, accessed December 26, 2025, [https://learn.microsoft.com/en-us/purview/default-sensitivity-labels-policies](https://learn.microsoft.com/en-us/purview/default-sensitivity-labels-policies)  
49. Learn about sensitivity labels in Microsoft Purview Data Map ..., accessed December 26, 2025, [https://learn.microsoft.com/en-us/purview/data-map-sensitivity-labels](https://learn.microsoft.com/en-us/purview/data-map-sensitivity-labels)  
50. Copy and transform data to and from SQL Server \- Azure Data Factory & Azure Synapse | Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/data-factory/connector-sql-server](https://learn.microsoft.com/en-us/azure/data-factory/connector-sql-server)  
51. Copy and transform data in Azure SQL Database \- Azure Data Factory & Azure Synapse | Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/data-factory/connector-azure-sql-database](https://learn.microsoft.com/en-us/azure/data-factory/connector-azure-sql-database)  
52. How to build a Fraud Detection Dashboard in Power BI | by Ahmad Zubair \- Medium, accessed December 26, 2025, [https://ahmadzc12.medium.com/a-basic-guide-to-building-a-fraud-detection-dashboard-in-power-bi-2a5722542531](https://ahmadzc12.medium.com/a-basic-guide-to-building-a-fraud-detection-dashboard-in-power-bi-2a5722542531)  
53. Power BI output from Azure Stream Analytics \- Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/stream-analytics/power-bi-output](https://learn.microsoft.com/en-us/azure/stream-analytics/power-bi-output)  
54. Tutorial: Analyze fraudulent call data with Stream Analytics and visualize results in Power BI dashboard \- Microsoft Learn, accessed December 26, 2025, [https://learn.microsoft.com/en-us/azure/stream-analytics/stream-analytics-real-time-fraud-detection](https://learn.microsoft.com/en-us/azure/stream-analytics/stream-analytics-real-time-fraud-detection)