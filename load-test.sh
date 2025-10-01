#!/bin/bash

# Load Test Script - Send 1000 requests to the Load Balancer

LB_URL="http://localhost:8080"
NUM_REQUESTS=10000

echo "=========================================="
echo "  Load Balancer - Load Test"
echo "=========================================="
echo ""
echo "Target: $LB_URL"
echo "Requests: $NUM_REQUESTS"
echo ""

# Check if load balancer is running
echo "Checking if load balancer is running..."
if ! curl -s -f "$LB_URL/stats" > /dev/null 2>&1; then
    echo "❌ Load balancer is not running on port 8080!"
    echo "   Start it with: ./run.sh"
    exit 1
fi

echo "✅ Load balancer is running"
echo ""

# Get initial stats
echo "📊 Initial Statistics:"
curl -s "$LB_URL/stats" | python3 -c "import sys, json; data=json.load(sys.stdin); print(f\"  Total Servers: {data['servers']['total']}\"); print(f\"  Total Requests: {data['loadBalancer']['totalRequests']}\")" 2>/dev/null || echo "  (Stats retrieved)"
echo ""

# Create a temporary file to store results
TEMP_FILE=$(mktemp)
echo "🚀 Sending $NUM_REQUESTS requests..."
echo ""

# Progress indicators
START_TIME=$(date +%s)

# Send requests in parallel for faster execution
# Using different paths to test consistent hashing distribution
for i in $(seq 1 $NUM_REQUESTS); do
    # Vary the path to simulate different users/resources
    PATH_VARIANT=$((i % 100))
    
    # Send request in background (parallel)
    curl -s "$LB_URL/api/resource/$PATH_VARIANT" | grep -o '"server":"[^"]*"' >> "$TEMP_FILE" &
    
    # Print progress every 50 requests
    if [ $((i % 50)) -eq 0 ]; then
        echo -ne "Progress: $i/$NUM_REQUESTS requests sent\r"
    fi
    
    # Limit concurrent requests to avoid overwhelming the system
    if [ $((i % 20)) -eq 0 ]; then
        wait
    fi
done

# Wait for all background jobs to complete
wait

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo -ne "\n"
echo "✅ All $NUM_REQUESTS requests completed in $DURATION seconds"
echo ""

# Analyze distribution
echo "📊 Analyzing Request Distribution..."
echo ""

# Count requests per server
echo "Distribution by Server:"
sort "$TEMP_FILE" | uniq -c | while read count server; do
    percentage=$(awk "BEGIN {printf \"%.2f\", ($count/$NUM_REQUESTS)*100}")
    echo "  $server => $count requests ($percentage%)"
done

echo ""

# Calculate requests per second
RPS=$(awk "BEGIN {printf \"%.2f\", $NUM_REQUESTS/$DURATION}")
echo "Performance: $RPS requests/second"
echo ""

# Get final stats from load balancer
echo "📊 Final Statistics from Load Balancer:"
curl -s "$LB_URL/stats" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(f\"  Total Servers: {data['servers']['total']}\")
    print(f\"  Total Requests: {data['loadBalancer']['totalRequests']}\")
    print(f\"  Virtual Nodes per Server: {data['loadBalancer']['virtualNodesPerServer']}\")
    print()
    print('  Active Servers:')
    for server in data['servers']['nodes']:
        status = '✓' if server['active'] else '✗'
        print(f\"    {status} {server['id']} - {server['address']}\")
except:
    pass
" 2>/dev/null || curl -s "$LB_URL/stats"

echo ""

# Cleanup
rm -f "$TEMP_FILE"

echo "=========================================="
echo "✅ Load Test Complete!"
echo "=========================================="
echo ""
echo "💡 Tips:"
echo "  - Add more servers: curl $LB_URL/add-server"
echo "  - View stats: curl $LB_URL/stats"
echo "  - Run test again to see new distribution"
echo ""
