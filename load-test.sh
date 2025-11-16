#!/bin/bash

# ANSI Color Codes
C_RESET='\033[0m'
C_BOLD='\033[1m'
C_BLUE='\033[34m'
C_GREEN='\033[32m'
C_RED='\033[31m'
C_YELLOW='\033[33m'
C_CYAN='\033[36m'

# Default values
DEFAULT_NUM_REQUESTS=50000
DEFAULT_WORKERS=50

# Load Test Script - Send requests to the Load Balancer
LB_URL="http://localhost:8080"
NUM_REQUESTS=$DEFAULT_NUM_REQUESTS
WORKERS=$DEFAULT_WORKERS

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -p)
            WORKERS="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [num_requests] [-p workers]"
            echo ""
            echo "Arguments:"
            echo "  num_requests    Number of requests to send (default: $DEFAULT_NUM_REQUESTS)"
            echo "  -p workers      Number of concurrent workers for xargs (default: $DEFAULT_WORKERS)"
            echo "  -h, --help      Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0              # Use default values"
            echo "  $0 10000        # Send 10000 requests with default workers"
            echo "  $0 10000 -p 300 # Send 10000 requests with 300 workers"
            echo "  $0 -p 1000      # Use default requests with 1000 workers"
            exit 0
            ;;
        *)
            if [[ $1 =~ ^[0-9]+$ ]]; then
                NUM_REQUESTS="$1"
                shift
            else
                echo -e "${C_RED}Error: Invalid argument '$1'${C_RESET}"
                echo "Use -h or --help for usage information"
                exit 1
            fi
            ;;
    esac
done

echo -e "${C_BOLD}${C_BLUE}==========================================${C_RESET}"
echo -e "${C_BOLD}${C_BLUE}      Load Balancer - Load Test         ${C_RESET}"
echo -e "${C_BOLD}${C_BLUE}==========================================${C_RESET}"
echo -e "${C_CYAN}Target:${C_RESET} $LB_URL"
echo -e "${C_CYAN}Requests:${C_RESET} $NUM_REQUESTS"
echo -e "${C_CYAN}Workers:${C_RESET} $WORKERS"
echo ""

# Check if load balancer is running
echo -n -e "${C_YELLOW}Checking if load balancer is running... ${C_RESET}"
if ! curl -s -f "$LB_URL/stats" > /dev/null 2>&1; then
    echo -e "${C_RED}❌ Not running on port 8080!${C_RESET}"
    echo -e "   ${C_YELLOW}Start it with: ./run.sh${C_RESET}"
    exit 1
fi
echo -e "${C_GREEN}✅ Running${C_RESET}"
echo ""

# Get initial stats
echo -e "${C_BOLD}${C_CYAN}📊 Initial Statistics:${C_RESET}"
curl -s "$LB_URL/stats" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(f\"  ${C_GREEN}Total Servers:${C_RESET} {data['servers']['total']}\")
    print(f\"  ${C_GREEN}Total Requests:${C_RESET} {data['loadBalancer']['totalRequests']}\")
except:
    print(f\"  ${C_YELLOW}(Could not parse stats)${C_RESET}\")
" 2>/dev/null || echo -e "  ${C_YELLOW}(Stats retrieved)${C_RESET}"
echo ""

# Create a temporary file to store results
TEMP_FILE=$(mktemp)
echo -e "${C_BOLD}${C_CYAN}🚀 Sending $NUM_REQUESTS requests...${C_RESET}"

# Progress indicators
START_TIME=$(date +%s)

# Send requests in parallel with maximum concurrency
# Using xargs for efficient parallelization
seq 1 $NUM_REQUESTS | xargs -P $WORKERS -I {} sh -c '
    PATH_VARIANT=$((({} - 1) % 100))
    curl -s "'$LB_URL'/api/resource/$PATH_VARIANT" | grep -o "\"server\":\"[^\"]*\"" >> "'$TEMP_FILE'"
    if [ $(({} % 1000)) -eq 0 ]; then
        printf "\033[32mProgress: {}/'$NUM_REQUESTS' requests sent\r\033[0m" >&2
    fi
'

# Wait for all background jobs to complete
wait

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo -ne "\n"
echo -e "${C_BOLD}${C_GREEN}✅ All $NUM_REQUESTS requests completed in $DURATION seconds${C_RESET}"
echo ""

# Analyze distribution
echo -e "${C_BOLD}${C_CYAN}📊 Request Distribution Analysis:${C_RESET}"
sort "$TEMP_FILE" | uniq -c | while read count server; do
    percentage=$(awk "BEGIN {printf \"%.2f\", ($count/$NUM_REQUESTS)*100}")
    echo -e "  ${C_YELLOW}$server${C_RESET} => ${C_GREEN}$count requests ($percentage%)${C_RESET}"
done
echo ""

# Calculate requests per second
RPS=$(awk "BEGIN {printf \"%.2f\", $NUM_REQUESTS/$DURATION}")
echo -e "${C_BOLD}${C_CYAN}⏱️ Performance:${C_RESET} ${C_GREEN}$RPS requests/second${C_RESET}"
echo ""

# Get final stats from load balancer
echo -e "${C_BOLD}${C_CYAN}📊 Final Statistics:${C_RESET}"
curl -s "$LB_URL/stats" | python3 -c "
import sys, json
C_RESET = '\033[0m'
C_GREEN = '\033[32m'
C_RED = '\033[31m'
C_YELLOW = '\033[33m'

try:
    data = json.load(sys.stdin)
    print(f\"  ${C_GREEN}Total Servers:${C_RESET} {data['servers']['total']}\")
    print(f\"  ${C_GREEN}Total Requests:${C_RESET} {data['loadBalancer']['totalRequests']}\")
    print(f\"  ${C_GREEN}Virtual Nodes:${C_RESET} {data['loadBalancer']['virtualNodesPerServer']}\")
    print()
    print(f'  ${C_YELLOW}Active Servers:${C_RESET}')
    for server in data['servers']['nodes']:
        status = f'{C_GREEN}✓{C_RESET}' if server['active'] else f'{C_RED}✗{C_RESET}'
        print(f\"    {status} {server['id']} - {server['address']}\")
except:
    print(f\"  ${C_YELLOW}(Could not parse final stats)${C_RESET}\")
" 2>/dev/null || curl -s "$LB_URL/stats"
echo ""

# Cleanup
rm -f "$TEMP_FILE"

echo -e "${C_BOLD}${C_BLUE}==========================================${C_RESET}"
echo -e "${C_BOLD}${C_GREEN}✅ Load Test Complete!${C_RESET}"
echo -e "${C_BOLD}${C_BLUE}==========================================${C_RESET}"
echo ""
echo -e "${C_BOLD}${C_YELLOW}💡 Tips:${C_RESET}"
echo -e "  - ${C_CYAN}Add more servers:${C_RESET} curl $LB_URL/add-server"
echo -e "  - ${C_CYAN}View stats:${C_RESET} curl $LB_URL/stats"
echo ""
