#!/bin/bash
# Security validation script for BuildConfig fields
# Ensures no secrets (API keys, tokens, passwords) are accidentally added to BuildConfig

set -e

echo "🔒 Checking BuildConfig files for potential secrets..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Patterns that indicate potential secrets
SECRET_PATTERNS=(
    "API_KEY"
    "SECRET"
    "TOKEN"
    "PASSWORD"
    "CREDENTIAL"
    "PRIVATE_KEY"
    "ACCESS_TOKEN"
    "REFRESH_TOKEN"
    "CLIENT_SECRET"
    "GEMINI_API_KEY"
    "OPENAI_API_KEY"
)

# Files to check
BUILD_FILES=(
    "core/database/build.gradle.kts"
    "core/extension/build.gradle.kts"
    "app/build.gradle.kts"
)

FOUND_ISSUES=0

for BUILD_FILE in "${BUILD_FILES[@]}"; do
    if [ ! -f "$BUILD_FILE" ]; then
        continue
    fi

    echo "Checking: $BUILD_FILE"

    # Check for buildConfigField declarations
    if grep -q "buildConfigField" "$BUILD_FILE"; then
        echo -e "${YELLOW}⚠️  Found buildConfigField in $BUILD_FILE${NC}"

        # Check each secret pattern
        for PATTERN in "${SECRET_PATTERNS[@]}"; do
            if grep -i "buildConfigField.*$PATTERN" "$BUILD_FILE"; then
                echo -e "${RED}❌ SECURITY RISK: Found potential secret pattern '$PATTERN' in $BUILD_FILE${NC}"
                echo -e "${RED}   BuildConfig fields are embedded in APK and can be decompiled!${NC}"
                echo -e "${RED}   Use EncryptedSharedPreferences or secure storage instead.${NC}"
                FOUND_ISSUES=$((FOUND_ISSUES + 1))
            fi
        done
    fi
done

# Check for hardcoded secrets in build files
echo ""
echo "Checking for hardcoded credentials..."

for BUILD_FILE in "${BUILD_FILES[@]}"; do
    if [ ! -f "$BUILD_FILE" ]; then
        continue
    fi

    # Check for common hardcoded credential patterns
    if grep -E "(AIza[0-9A-Za-z_-]{35}|sk-[A-Za-z0-9]{48})" "$BUILD_FILE"; then
        echo -e "${RED}❌ SECURITY RISK: Found hardcoded API key in $BUILD_FILE${NC}"
        FOUND_ISSUES=$((FOUND_ISSUES + 1))
    fi
done

echo ""

if [ $FOUND_ISSUES -eq 0 ]; then
    echo -e "${GREEN}✅ No security issues found in BuildConfig declarations${NC}"
    echo -e "${GREEN}✅ All checks passed${NC}"
    exit 0
else
    echo -e "${RED}❌ Found $FOUND_ISSUES potential security issue(s)${NC}"
    echo ""
    echo "Security best practices for Otaku Reader:"
    echo "1. Never add secrets to BuildConfig (they're embedded in APK)"
    echo "2. Use EncryptedSharedPreferences for OAuth tokens, API keys"
    echo "3. Use Gradle properties (local.properties) for build-time secrets"
    echo "4. Reference: docs/database-migration-safety.md#security-considerations"
    exit 1
fi
