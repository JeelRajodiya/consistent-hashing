#!/bin/bash

# Test Script for Consistent Hashing Load Balancer

LB_URL="http://localhost:8080"

echo "=========================================="
echo "  Load Balancer Test Suite"
echo "=========================================="
echo ""

# Function to print colored output
print_test() {
    echo "🧪 TEST: $1"
}

print_success() {
    echo "✅ $1"
}

print_info() {
    echo "ℹ️  $1"
}

# Wait for user to confirm load balancer is running
echo "⚠️  Make sure the load balancer is running on port 8080"
echo "   Run: ./run.sh in another terminal"
echo ""
read -p "Press Enter when ready to start testing..."
echo ""

# Test 1: Basic Load Balancing
print_test "Basic Load Balancing (10 requests)"
echo "Sending 10 requests to observe distribution..."
for i in {1..10}; do
    echo -n "Request $i: "
    curl -s "$LB_URL/test$i" | grep -o '"server":"[^"]*"' || echo "Failed"
done
echo ""

# Test 2: Stats Endpoint
print_test "Fetching Statistics"
curl -s "$LB_URL/stats" | python3 -m json.tool 2>/dev/null || curl -s "$LB_URL/stats"
echo ""

# Test 3: Add Server
print_test "Adding a New Server"
curl -s "$LB_URL/add-server" | python3 -m json.tool 2>/dev/null || curl -s "$LB_URL/add-server"
echo ""

print_info "Waiting 2 seconds for server to stabilize..."
sleep 2

# Test 4: Stats After Adding
print_test "Fetching Statistics After Adding Server"
curl -s "$LB_URL/stats" | python3 -m json.tool 2>/dev/null || curl -s "$LB_URL/stats"
echo ""

# Test 5: More Load Balanced Requests
print_test "Load Balancing with More Servers (10 requests)"
echo "Sending 10 more requests to see new distribution..."
for i in {11..20}; do
    echo -n "Request $i: "
    curl -s "$LB_URL/api/test$i" | grep -o '"server":"[^"]*"' || echo "Failed"
done
echo ""

# Test 6: Different Paths (Consistent Hashing)
print_test "Testing Consistent Hashing (same path should go to same server)"
echo "Request to /path/user/123 (should be consistent):"
for i in {1..3}; do
    echo -n "  Attempt $i: "
    curl -s "$LB_URL/path/user/123" | grep -o '"server":"[^"]*"' || echo "Failed"
done
echo ""

echo "Request to /path/user/456 (should be consistent):"
for i in {1..3}; do
    echo -n "  Attempt $i: "
    curl -s "$LB_URL/path/user/456" | grep -o '"server":"[^"]*"' || echo "Failed"
done
echo ""

# Test 7: Get final stats
print_test "Final Statistics"
curl -s "$LB_URL/stats" | python3 -m json.tool 2>/dev/null || curl -s "$LB_URL/stats"
echo ""

echo "=========================================="
print_success "All Tests Completed!"
echo "=========================================="
echo ""
echo "Note: To remove a server, run:"
echo "  curl 'http://localhost:8080/remove-server?id=server-<PORT>'"
echo ""
