#!/bin/bash
#
# Deployment Script for gChess
#
# Automates the deployment process for different environments:
# - Builds Docker image
# - Stops old containers
# - Starts new containers
# - Runs health checks
# - Provides rollback instructions on failure
#
# Usage:
#   ./scripts/deploy.sh <environment> [version]
#
# Examples:
#   ./scripts/deploy.sh local                    # Deploy local with :local tag
#   ./scripts/deploy.sh test v1.2.3              # Deploy test with version tag
#   ./scripts/deploy.sh prod v1.2.3              # Deploy prod with version tag
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
VERSION=${2:-latest}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "  gChess Deployment - $ENVIRONMENT"
echo "=========================================="
echo "Version: $VERSION"
echo "Project: $PROJECT_ROOT"
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

if [ ! -f "$PROJECT_ROOT/$COMPOSE_FILE" ]; then
    echo -e "${RED}❌ Error: $COMPOSE_FILE not found${NC}"
    exit 1
fi

# Function to run command with logging
run_step() {
    local description=$1
    shift
    echo -e "${BLUE}▶ $description${NC}"
    if "$@"; then
        echo -e "${GREEN}  ✅ Done${NC}"
        return 0
    else
        echo -e "${RED}  ❌ Failed${NC}"
        return 1
    fi
}

# Build for local environment
if [ "$ENVIRONMENT" = "local" ]; then
    echo -e "${YELLOW}📦 Building application...${NC}"
    echo ""

    run_step "Building JAR with Gradle" \
        "$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" clean shadowJar --no-daemon

    echo ""
    run_step "Building Docker image" \
        docker build -t "gchess:$ENVIRONMENT" "$PROJECT_ROOT"

    IMAGE_TAG="gchess:$ENVIRONMENT"
else
    # For test/prod, image should already exist
    IMAGE_TAG="gchess:$ENVIRONMENT"

    if ! docker image inspect "$IMAGE_TAG" > /dev/null 2>&1; then
        echo -e "${RED}❌ Error: Docker image '$IMAGE_TAG' not found${NC}"
        echo ""
        echo "For $ENVIRONMENT environment, you must:"
        echo "1. Pull the image from registry: docker pull ghcr.io/USERNAME/gchess:$VERSION"
        echo "2. Tag it locally: docker tag ghcr.io/USERNAME/gchess:$VERSION $IMAGE_TAG"
        exit 1
    fi
fi

echo ""
echo -e "${YELLOW}🔄 Deploying containers...${NC}"
echo ""

# Save current container IDs for rollback
OLD_APP_CONTAINER=$(docker ps -q -f name="gchess-app-$ENVIRONMENT" || echo "")
if [ -n "$OLD_APP_CONTAINER" ]; then
    echo "📸 Saving current container ID for rollback: $OLD_APP_CONTAINER"
    echo "$OLD_APP_CONTAINER" > "/tmp/gchess-rollback-$ENVIRONMENT.txt"
fi

# Stop and remove old containers
run_step "Stopping old containers" \
    docker-compose -f "$PROJECT_ROOT/$COMPOSE_FILE" down || true

echo ""

# Start new containers
run_step "Starting new containers" \
    docker-compose -f "$PROJECT_ROOT/$COMPOSE_FILE" up -d

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
    echo -e "${GREEN}✅ Deployment successful!${NC}"
    echo "=========================================="
    echo ""
    echo "Environment: $ENVIRONMENT"
    echo "Version: $VERSION"
    echo "Image: $IMAGE_TAG"
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
    echo "Useful commands:"
    echo "  View logs: docker-compose -f $COMPOSE_FILE logs -f gchess-app-$ENVIRONMENT"
    echo "  Check status: docker-compose -f $COMPOSE_FILE ps"
    echo "  Health check: ./scripts/health-check.sh $ENVIRONMENT"

    # Cleanup rollback file on success
    rm -f "/tmp/gchess-rollback-$ENVIRONMENT.txt"

    exit 0
else
    echo ""
    echo "=========================================="
    echo -e "${RED}❌ Deployment failed!${NC}"
    echo "=========================================="
    echo ""
    echo "Health checks did not pass."
    echo ""

    echo "Troubleshooting:"
    echo "  1. Check logs: docker-compose -f $COMPOSE_FILE logs gchess-app-$ENVIRONMENT"
    echo "  2. Check containers: docker-compose -f $COMPOSE_FILE ps"
    echo "  3. Manual health check: curl http://localhost:8080/health"
    echo ""

    if [ -f "/tmp/gchess-rollback-$ENVIRONMENT.txt" ]; then
        echo -e "${YELLOW}To rollback to previous version:${NC}"
        echo "  ./scripts/rollback.sh $ENVIRONMENT"
    fi

    exit 1
fi
