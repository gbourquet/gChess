#!/bin/bash
#
# Rollback Script for gChess
#
# Rolls back to the previous deployment in case of issues.
#
# Usage:
#   ./scripts/rollback.sh <environment> [container_id]
#
# Examples:
#   ./scripts/rollback.sh local              # Rollback using saved container ID
#   ./scripts/rollback.sh test abc123        # Rollback to specific container ID
#   ./scripts/rollback.sh prod               # Rollback using saved container ID
#
# Note: This script attempts to restart the previous container.
#       For image-based rollback, manually pull and deploy the previous version.
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
ENVIRONMENT=${1:-local}
CONTAINER_ID=${2:-""}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "  gChess Rollback - $ENVIRONMENT"
echo "=========================================="
echo ""

# Validate environment
case "$ENVIRONMENT" in
    local|test|prod)
        ;;
    *)
        echo -e "${RED}❌ Error: Invalid environment '$ENVIRONMENT'${NC}"
        echo "Valid environments: local, test, prod"
        exit 1
        ;;
esac

# Determine docker-compose file
COMPOSE_FILE="docker-compose.yml"
if [ "$ENVIRONMENT" != "local" ]; then
    COMPOSE_FILE="docker-compose.$ENVIRONMENT.yml"
fi

# Get container ID from saved file if not provided
if [ -z "$CONTAINER_ID" ]; then
    ROLLBACK_FILE="/tmp/gchess-rollback-$ENVIRONMENT.txt"
    if [ -f "$ROLLBACK_FILE" ]; then
        CONTAINER_ID=$(cat "$ROLLBACK_FILE")
        echo "📸 Using saved container ID: $CONTAINER_ID"
    else
        echo -e "${RED}❌ Error: No rollback container ID found${NC}"
        echo ""
        echo "Rollback options:"
        echo "1. Provide container ID manually:"
        echo "   ./scripts/rollback.sh $ENVIRONMENT <container_id>"
        echo ""
        echo "2. List recent containers:"
        echo "   docker ps -a --filter name=gchess-app"
        echo ""
        echo "3. Deploy previous version manually:"
        echo "   docker pull ghcr.io/USERNAME/gchess:PREVIOUS_VERSION"
        echo "   docker tag ghcr.io/USERNAME/gchess:PREVIOUS_VERSION gchess:$ENVIRONMENT"
        echo "   ./scripts/deploy.sh $ENVIRONMENT"
        exit 1
    fi
fi

# Verify container exists
if ! docker inspect "$CONTAINER_ID" > /dev/null 2>&1; then
    echo -e "${RED}❌ Error: Container '$CONTAINER_ID' not found${NC}"
    echo ""
    echo "The container may have been removed."
    echo "List available containers:"
    echo "  docker ps -a --filter name=gchess"
    exit 1
fi

# Get container info
CONTAINER_NAME=$(docker inspect --format='{{.Name}}' "$CONTAINER_ID" | sed 's/^\///')
IMAGE=$(docker inspect --format='{{.Config.Image}}' "$CONTAINER_ID")

echo ""
echo "Rollback details:"
echo "  Container: $CONTAINER_NAME"
echo "  Image: $IMAGE"
echo "  ID: $CONTAINER_ID"
echo ""

read -p "$(echo -e ${YELLOW}⚠️  Are you sure you want to rollback? [y/N]${NC} )" -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Rollback cancelled."
    exit 0
fi

echo ""
echo -e "${BLUE}🔄 Starting rollback...${NC}"
echo ""

# Stop current containers
echo "▶ Stopping current containers..."
docker-compose -f "$PROJECT_ROOT/$COMPOSE_FILE" down || true
echo -e "${GREEN}  ✅ Done${NC}"

echo ""

# Start old container
echo "▶ Starting previous container..."
if docker start "$CONTAINER_ID"; then
    echo -e "${GREEN}  ✅ Done${NC}"
else
    echo -e "${RED}  ❌ Failed to start container${NC}"
    echo ""
    echo "Attempting to restore using docker-compose..."
    docker-compose -f "$PROJECT_ROOT/$COMPOSE_FILE" up -d
    exit 1
fi

echo ""
echo -e "${YELLOW}⏳ Waiting for application to start...${NC}"
sleep 10

echo ""
echo -e "${YELLOW}🏥 Running health checks...${NC}"
echo ""

# Run health check
if bash "$SCRIPT_DIR/health-check.sh" "$ENVIRONMENT"; then
    echo ""
    echo "=========================================="
    echo -e "${GREEN}✅ Rollback successful!${NC}"
    echo "=========================================="
    echo ""
    echo "Previous version is now running."
    echo ""
    echo "Container: $CONTAINER_NAME ($CONTAINER_ID)"
    echo "Image: $IMAGE"
    echo ""

    case "$ENVIRONMENT" in
        local)
            echo "Access: http://localhost"
            ;;
        test)
            echo "Access: https://gchess-test.sur-le-web.fr"
            ;;
        prod)
            echo "Access: https://gchess.sur-le-web.fr"
            ;;
    esac

    echo ""
    echo "⚠️  This is a temporary rollback using the old container."
    echo "For a permanent fix:"
    echo "1. Investigate the issue that caused the rollback"
    echo "2. Fix the code/configuration"
    echo "3. Deploy the fixed version: ./scripts/deploy.sh $ENVIRONMENT"

    exit 0
else
    echo ""
    echo "=========================================="
    echo -e "${RED}❌ Rollback failed!${NC}"
    echo "=========================================="
    echo ""
    echo "The previous version is not healthy either."
    echo ""
    echo "Emergency recovery:"
    echo "1. Check container logs: docker logs $CONTAINER_ID"
    echo "2. Check database status: docker-compose -f $COMPOSE_FILE ps"
    echo "3. Manual intervention may be required"

    exit 1
fi
