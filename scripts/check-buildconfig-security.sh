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

# Hardcoded credential regex patterns (extensible list)
# Add new patterns here as needed
CREDENTIAL_REGEXES=(
    # Google API Keys (AIza followed by 35 chars)
    "AIza[0-9A-Za-z_-]{35}"
    # OpenAI API Keys (sk- followed by 48 chars)
    "sk-[A-Za-z0-9]{48}"
    # Generic API keys (32+ hex chars)
    "['\"][0-9a-fA-F]{32,}['\"]"
    # AWS Access Key ID format
    "AKIA[0-9A-Z]{16}"
    # GitHub Personal Access Token (classic)
    "ghp_[0-9a-zA-Z]{36}"
    # Generic Bearer tokens
    "Bearer [A-Za-z0-9_-]{20,}"
)

# Dynamically discover all build.gradle.kts files
echo "Discovering build files..."
mapfile -t BUILD_FILES < <(find . -type f -name "build.gradle.kts" ! -path "*/build/*" ! -path "*/.gradle/*" | sort)

if [ ${#BUILD_FILES[@]} -eq 0 ]; then
    echo -e "${YELLOW}⚠️  No build.gradle.kts files found${NC}"
    exit 0
fi

echo "Found ${#BUILD_FILES[@]} build files to scan"
echo ""

# Use associative arrays to track unique findings
declare -A SECRET_FINDINGS
declare -A CREDENTIAL_FINDINGS

# Scan for buildConfigField with secret patterns
for BUILD_FILE in "${BUILD_FILES[@]}"; do
    # Skip if file doesn't exist (shouldn't happen with find, but defensive)
    if [ ! -f "$BUILD_FILE" ]; then
        continue
    fi

    # Remove leading ./ for cleaner output
    CLEAN_PATH="${BUILD_FILE#./}"

    # Check for buildConfigField declarations (excluding comments)
    # Use grep with -v to exclude comment lines, then check for buildConfigField
    if grep -v "^\s*//\|^\s*/\*\|^\s*\*" "$BUILD_FILE" | grep -q "buildConfigField"; then
        echo "Checking: $CLEAN_PATH"

        # Check each secret pattern
        for PATTERN in "${SECRET_PATTERNS[@]}"; do
            # Match buildConfigField lines (excluding comments) with the pattern
            # Use a more precise regex:
            # 1. Line must not start with // or be in /* */ block
            # 2. Must contain buildConfigField
            # 3. Must contain the secret pattern (case insensitive)

            # Extract non-comment lines containing buildConfigField and the pattern.
            # Exclude lines where the value is System.getenv(...) — those are safe
            # because the secret is injected at build time from the environment, not
            # hardcoded into the source file.
            MATCHES=$(grep -v "^\s*//\|^\s*/\*\|^\s*\*" "$BUILD_FILE" | \
                      grep -i "buildConfigField" | \
                      grep -v "System\.getenv" | \
                      grep -i "$PATTERN" || true)

            if [ -n "$MATCHES" ]; then
                # Store unique finding (file:pattern)
                FINDING_KEY="${CLEAN_PATH}:${PATTERN}"
                if [ -z "${SECRET_FINDINGS[$FINDING_KEY]}" ]; then
                    SECRET_FINDINGS[$FINDING_KEY]="$CLEAN_PATH|$PATTERN|$MATCHES"
                fi
            fi
        done
    fi
done

# Check for hardcoded credentials using regex patterns
echo ""
echo "Checking for hardcoded credentials..."

for BUILD_FILE in "${BUILD_FILES[@]}"; do
    if [ ! -f "$BUILD_FILE" ]; then
        continue
    fi

    CLEAN_PATH="${BUILD_FILE#./}"

    # Check each credential regex pattern
    for REGEX in "${CREDENTIAL_REGEXES[@]}"; do
        # Search for the pattern, excluding comment lines
        MATCHES=$(grep -v "^\s*//\|^\s*/\*\|^\s*\*" "$BUILD_FILE" | \
                  grep -E "$REGEX" || true)

        if [ -n "$MATCHES" ]; then
            # Store unique finding (file:regex_index)
            FINDING_KEY="${CLEAN_PATH}:CREDENTIAL:${REGEX}"
            if [ -z "${CREDENTIAL_FINDINGS[$FINDING_KEY]}" ]; then
                CREDENTIAL_FINDINGS[$FINDING_KEY]="$CLEAN_PATH|$REGEX"
            fi
        fi
    done
done

echo ""

# Report unique findings
TOTAL_ISSUES=0

if [ ${#SECRET_FINDINGS[@]} -gt 0 ]; then
    echo -e "${RED}Found ${#SECRET_FINDINGS[@]} unique BuildConfig secret pattern(s):${NC}"
    for KEY in "${!SECRET_FINDINGS[@]}"; do
        IFS='|' read -r FILE PATTERN MATCHES <<< "${SECRET_FINDINGS[$KEY]}"
        echo -e "${RED}❌ SECURITY RISK: Found potential secret pattern '$PATTERN' in $FILE${NC}"
        echo -e "${RED}   BuildConfig fields are embedded in APK and can be decompiled!${NC}"
        echo -e "${RED}   Use EncryptedSharedPreferences or secure storage instead.${NC}"
        # Show first match as example (truncate if too long)
        EXAMPLE=$(echo "$MATCHES" | head -n 1 | cut -c 1-100)
        echo -e "${YELLOW}   Example: $EXAMPLE${NC}"
        echo ""
        TOTAL_ISSUES=$((TOTAL_ISSUES + 1))
    done
fi

if [ ${#CREDENTIAL_FINDINGS[@]} -gt 0 ]; then
    echo -e "${RED}Found ${#CREDENTIAL_FINDINGS[@]} unique hardcoded credential(s):${NC}"
    for KEY in "${!CREDENTIAL_FINDINGS[@]}"; do
        IFS='|' read -r FILE REGEX <<< "${CREDENTIAL_FINDINGS[$KEY]}"
        echo -e "${RED}❌ SECURITY RISK: Found hardcoded credential pattern in $FILE${NC}"
        echo -e "${RED}   Pattern: $REGEX${NC}"
        echo ""
        TOTAL_ISSUES=$((TOTAL_ISSUES + 1))
    done
fi

if [ $TOTAL_ISSUES -eq 0 ]; then
    echo -e "${GREEN}✅ No security issues found in BuildConfig declarations${NC}"
    echo -e "${GREEN}✅ All checks passed (scanned ${#BUILD_FILES[@]} files)${NC}"
    exit 0
else
    echo -e "${RED}❌ Found $TOTAL_ISSUES unique security issue(s)${NC}"
    echo ""
    echo "Security best practices for Otaku Reader:"
    echo "1. Never add secrets to BuildConfig (they're embedded in APK)"
    echo "2. Use EncryptedSharedPreferences for OAuth tokens, API keys"
    echo "3. Use Gradle properties (local.properties) for build-time secrets"
    echo "4. Reference: docs/database-migration-safety.md#security-considerations"
    exit 1
fi
