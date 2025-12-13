#!/bin/bash
#
# Health Check Script for gChess Deployment
#
# Validates that the application is running correctly by checking:
# - HTTP health endpoint
# - Detailed actuator health
# - WebSocket connectivity (optional)
#
# Usage:
#   ./scripts/health-check.sh <environment> [URL]
#
# Examples:
#   ./scripts/health-check.sh local                              # http://localhost
#   ./scripts/health-check.sh test                               # https://gchess-test.sur-le-web.fr
#   ./scripts/health-check.sh prod                               # https://gchess.sur-le-web.fr
#   ./scripts/health-check.sh custom https://my-domain.com       # Custom URL
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT=${1:-local}
MAX_RETRIES=30
RETRY_DELAY=2

# Determine URL based on environment
case "$ENVIRONMENT" in
    local)
        BASE_URL=${2:-"http://localhost"}
        ;;
    test)
        BASE_URL=${2:-"https://gchess-test.sur-le-web.fr"}
        ;;
    prod)
        BASE_URL=${2:-"https://gchess.sur-le-web.fr"}
        ;;
    custom)
        if [ -z "$2" ]; then
            echo -e "${RED}❌ Error: Custom URL required${NC}"
            echo "Usage: $0 custom <URL>"
            exit 1
        fi
        BASE_URL=$2
        ;;
    *)
        echo -e "${RED}❌ Error: Invalid environment '$ENVIRONMENT'${NC}"
        echo "Valid environments: local, test, prod, custom"
        exit 1
        ;;
esac

echo "=========================================="
echo "  gChess Health Check - $ENVIRONMENT"
echo "=========================================="
echo "Target: $BASE_URL"
echo ""

# Function to check endpoint
check_endpoint() {
    local endpoint=$1
    local description=$2
    local expected_status=${3:-200}

    echo -n "Checking $description... "

    for i in $(seq 1 $MAX_RETRIES); do
        HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$endpoint" || echo "000")

        if [ "$HTTP_STATUS" = "$expected_status" ]; then
            echo -e "${GREEN}✅ OK${NC} (HTTP $HTTP_STATUS)"
            return 0
        fi

        if [ $i -eq $MAX_RETRIES ]; then
            echo -e "${RED}❌ FAILED${NC} (HTTP $HTTP_STATUS, expected $expected_status)"
            return 1
        fi

        echo -n "."
        sleep $RETRY_DELAY
    done
}

# Function to check JSON response
check_json_status() {
    local endpoint=$1
    local description=$2

    echo -n "Checking $description... "

    RESPONSE=$(curl -s "$BASE_URL$endpoint" || echo '{}')

    # Use jq to extract top-level status if available, otherwise try grep
    if command -v jq > /dev/null 2>&1; then
        STATUS=$(echo "$RESPONSE" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
    else
        STATUS=$(echo "$RESPONSE" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)".*/\1/' || echo "UNKNOWN")
    fi

    if [ "$STATUS" = "UP" ]; then
        echo -e "${GREEN}✅ UP${NC}"
        echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
        return 0
    else
        echo -e "${RED}❌ DOWN or UNKNOWN${NC} (status: $STATUS)"
        echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
        return 1
    fi
}

# Run health checks
echo "🏥 Running health checks..."
echo ""

SUCCESS=true

# 1. Basic health check
if ! check_endpoint "/health" "Basic health endpoint"; then
    SUCCESS=false
fi

echo ""

# 2. Detailed actuator health
if ! check_json_status "/actuator/health" "Actuator health endpoint"; then
    SUCCESS=false
fi

echo ""

# 3. Check root endpoint
if ! check_endpoint "/" "Root endpoint"; then
    SUCCESS=false
fi

echo ""
echo "=========================================="

if [ "$SUCCESS" = true ]; then
    echo -e "${GREEN}✅ All health checks passed!${NC}"
    echo ""
    echo "Application is healthy and ready to serve traffic."
    exit 0
else
    echo -e "${RED}❌ Some health checks failed!${NC}"
    echo ""
    echo "Please check the logs:"
    echo "  docker logs gchess-app-$ENVIRONMENT"
    exit 1
fi
