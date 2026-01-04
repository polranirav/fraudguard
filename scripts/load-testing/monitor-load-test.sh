#!/bin/bash
# Monitor system during load test

echo "=== Load Test Monitoring ==="
echo ""

# Get Kafka offsets
echo "Kafka Topic Offsets:"
docker exec fraud-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic transactions-live 2>/dev/null | \
  awk -F: '{sum+=$3} END {print "  Total messages: " sum}'

echo ""
echo "Container Resource Usage:"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | \
  grep -E "(kafka|flink|ml-inference)"

echo ""
echo "Flink Job Status:"
curl -s http://localhost:8081/jobs 2>/dev/null | \
  python3 -c "import sys,json; d=json.load(sys.stdin); jobs=d.get('jobs',[]); print(f\"  Running Jobs: {len([j for j in jobs if j['status']=='RUNNING'])}\")" 2>/dev/null || \
  echo "  Check: http://localhost:8081"

echo ""
echo "ML Service Health:"
curl -s http://localhost:8000/health 2>/dev/null | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"  Status: {d.get('status')}\")" 2>/dev/null || \
  echo "  Status: Unknown"

echo ""
echo "Fraud Alerts Generated:"
docker exec fraud-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic fraud-alerts \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 1000 2>/dev/null | wc -l | xargs echo "  Alerts:"
