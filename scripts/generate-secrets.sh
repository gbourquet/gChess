#!/bin/bash
#
# Generate secure secrets for gChess deployment
#
# This script generates:
# - JWT secret (256-bit for HMAC256)
# - Database password (192-bit)
#
# Usage:
#   ./scripts/generate-secrets.sh

set -e

echo "=========================================="
echo "  gChess Secret Generator"
echo "=========================================="
echo ""

echo "🔐 JWT_SECRET (256-bit for HMAC256):"
echo "-------------------------------------"
openssl rand -base64 32
echo ""

echo "🔐 DATABASE_PASSWORD (192-bit):"
echo "------------------------------"
openssl rand -base64 24
echo ""

echo "=========================================="
echo "⚠️  SECURITY WARNINGS:"
echo "=========================================="
echo "1. Copy these values to your .env files"
echo "2. NEVER commit these secrets to version control!"
echo "3. Use different secrets for each environment"
echo "4. Store production secrets securely (password manager, vault)"
echo "5. Rotate secrets every 90 days"
echo ""
echo "📝 To use these secrets:"
echo "  - Test:       Add to Render dashboard environment variables"
echo "  - Production: Add to .env.prod on production server"
echo ""