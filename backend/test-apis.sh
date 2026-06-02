#!/bin/bash

# FuOverflow API Test Script
# Tests Auth and User APIs using curl
# Usage: ./test-apis.sh [base_url]
# Default base_url: http://localhost:8080

set -e

BASE_URL="${1:-http://localhost:8080}"
API_V1="$BASE_URL/api/v1"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test header
print_test() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}TEST: $1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Function to print success
print_success() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((TESTS_PASSED++))
}

# Function to print failure
print_fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((TESTS_FAILED++))
}

# Function to print info
print_info() {
    echo -e "${YELLOW}ℹ INFO${NC}: $1"
}

# Check if app is running
print_test "Health Check"
HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/actuator/health" || echo "000")
HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n 1)
HEALTH_BODY=$(echo "$HEALTH_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" == "200" ]; then
    print_success "App is running - $HEALTH_BODY"
else
    print_fail "App is not running (HTTP $HTTP_CODE)"
    echo -e "${RED}Please start the app first:${NC}"
    echo "  cd backend"
    echo "  docker compose up -d postgres redis"
    echo "  mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local"
    exit 1
fi

# Variables to store tokens across tests
ACCESS_TOKEN=""
REFRESH_TOKEN=""
USER_ID=""

# ==========================================
# AUTH API TESTS
# ==========================================

# TEST 1: Register new user
print_test "POST /api/v1/auth/register"
TIMESTAMP=$(date +%s)
REGISTER_PAYLOAD=$(cat <<EOF
{
  "email": "testuser${TIMESTAMP}@example.com",
  "username": "testuser${TIMESTAMP}",
  "password": "StrongPassword123!",
  "displayName": "Test User ${TIMESTAMP}"
}
EOF
)

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$API_V1/auth/register" \
  -H "Content-Type: application/json" \
  -d "$REGISTER_PAYLOAD")

HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n 1)
REGISTER_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" == "200" ] || [ "$HTTP_CODE" == "201" ]; then
    print_success "Register successful (HTTP $HTTP_CODE)"
    echo "$REGISTER_BODY" | jq '.' 2>/dev/null || echo "$REGISTER_BODY"

    # Extract tokens and user info
    ACCESS_TOKEN=$(echo "$REGISTER_BODY" | jq -r '.accessToken // empty' 2>/dev/null)
    REFRESH_TOKEN=$(echo "$REGISTER_BODY" | jq -r '.refreshToken // empty' 2>/dev/null)
    USER_ID=$(echo "$REGISTER_BODY" | jq -r '.user.id // empty' 2>/dev/null)

    if [ -n "$ACCESS_TOKEN" ]; then
        print_info "Access token captured: ${ACCESS_TOKEN:0:20}..."
    fi
    if [ -n "$REFRESH_TOKEN" ]; then
        print_info "Refresh token captured: ${REFRESH_TOKEN:0:20}..."
    fi
else
    print_fail "Register failed (HTTP $HTTP_CODE)"
    echo "$REGISTER_BODY"
fi

# TEST 2: Login with existing user
print_test "POST /api/v1/auth/login"
LOGIN_PAYLOAD=$(cat <<EOF
{
  "identifier": "testuser${TIMESTAMP}@example.com",
  "password": "StrongPassword123!"
}
EOF
)

LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$API_V1/auth/login" \
  -H "Content-Type: application/json" \
  -d "$LOGIN_PAYLOAD")

HTTP_CODE=$(echo "$LOGIN_RESPONSE" | tail -n 1)
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" == "200" ]; then
    print_success "Login successful (HTTP $HTTP_CODE)"
    echo "$LOGIN_BODY" | jq '.' 2>/dev/null || echo "$LOGIN_BODY"

    # Update tokens from login
    ACCESS_TOKEN=$(echo "$LOGIN_BODY" | jq -r '.accessToken // empty' 2>/dev/null)
    REFRESH_TOKEN=$(echo "$LOGIN_BODY" | jq -r '.refreshToken // empty' 2>/dev/null)
else
    print_fail "Login failed (HTTP $HTTP_CODE)"
    echo "$LOGIN_BODY"
fi

# TEST 3: Access protected endpoint with token
print_test "GET /api/v1/users/me (with valid token)"

if [ -z "$ACCESS_TOKEN" ]; then
    print_fail "No access token available from previous tests"
else
    ME_RESPONSE=$(curl -s -w "\n%{http_code}" \
      -X GET "$API_V1/users/me" \
      -H "Authorization: Bearer $ACCESS_TOKEN")

    HTTP_CODE=$(echo "$ME_RESPONSE" | tail -n 1)
    ME_BODY=$(echo "$ME_RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" == "200" ]; then
        print_success "Get current user successful (HTTP $HTTP_CODE)"
        echo "$ME_BODY" | jq '.' 2>/dev/null || echo "$ME_BODY"
    else
        print_fail "Get current user failed (HTTP $HTTP_CODE)"
        echo "$ME_BODY"
    fi
fi

# TEST 4: Update user profile
print_test "PATCH /api/v1/users/me/profile"

if [ -z "$ACCESS_TOKEN" ]; then
    print_fail "No access token available from previous tests"
else
    UPDATE_PAYLOAD=$(cat <<EOF
{
  "displayName": "Updated Name ${TIMESTAMP}",
  "firstName": "Updated",
  "lastName": "User"
}
EOF
)

    UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" \
      -X PATCH "$API_V1/users/me/profile" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$UPDATE_PAYLOAD")

    HTTP_CODE=$(echo "$UPDATE_RESPONSE" | tail -n 1)
    UPDATE_BODY=$(echo "$UPDATE_RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" == "200" ]; then
        print_success "Update profile successful (HTTP $HTTP_CODE)"
        echo "$UPDATE_BODY" | jq '.' 2>/dev/null || echo "$UPDATE_BODY"
    else
        print_fail "Update profile failed (HTTP $HTTP_CODE)"
        echo "$UPDATE_BODY"
    fi
fi

# TEST 5: Refresh token
print_test "POST /api/v1/auth/token/refresh"

if [ -z "$REFRESH_TOKEN" ]; then
    print_fail "No refresh token available from previous tests"
else
    REFRESH_PAYLOAD=$(cat <<EOF
{
  "refreshToken": "$REFRESH_TOKEN"
}
EOF
)

    REFRESH_RESPONSE=$(curl -s -w "\n%{http_code}" \
      -X POST "$API_V1/auth/token/refresh" \
      -H "Content-Type: application/json" \
      -d "$REFRESH_PAYLOAD")

    HTTP_CODE=$(echo "$REFRESH_RESPONSE" | tail -n 1)
    REFRESH_BODY=$(echo "$REFRESH_RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" == "200" ]; then
        print_success "Refresh token successful (HTTP $HTTP_CODE)"
        echo "$REFRESH_BODY" | jq '.' 2>/dev/null || echo "$REFRESH_BODY"

        # Update tokens
        ACCESS_TOKEN=$(echo "$REFRESH_BODY" | jq -r '.accessToken // empty' 2>/dev/null)
        REFRESH_TOKEN=$(echo "$REFRESH_BODY" | jq -r '.refreshToken // empty' 2>/dev/null)
    else
        print_fail "Refresh token failed (HTTP $HTTP_CODE)"
        echo "$REFRESH_BODY"
    fi
fi

# TEST 6: Access protected endpoint without token
print_test "GET /api/v1/users/me (without token - should fail)"

NO_AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X GET "$API_V1/users/me")

HTTP_CODE=$(echo "$NO_AUTH_RESPONSE" | tail -n 1)
NO_AUTH_BODY=$(echo "$NO_AUTH_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" == "401" ] || [ "$HTTP_CODE" == "403" ]; then
    print_success "Correctly rejected unauthorized request (HTTP $HTTP_CODE)"
else
    print_fail "Should have rejected unauthorized request but got HTTP $HTTP_CODE"
    echo "$NO_AUTH_BODY"
fi

# TEST 7: Logout
print_test "POST /api/v1/auth/logout"

if [ -z "$ACCESS_TOKEN" ]; then
    print_fail "No access token available from previous tests"
else
    LOGOUT_RESPONSE=$(curl -s -w "\n%{http_code}" \
      -X POST "$API_V1/auth/logout" \
      -H "Authorization: Bearer $ACCESS_TOKEN")

    HTTP_CODE=$(echo "$LOGOUT_RESPONSE" | tail -n 1)
    LOGOUT_BODY=$(echo "$LOGOUT_RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" == "200" ] || [ "$HTTP_CODE" == "204" ]; then
        print_success "Logout successful (HTTP $HTTP_CODE)"
        echo "$LOGOUT_BODY"
    else
        print_fail "Logout failed (HTTP $HTTP_CODE)"
        echo "$LOGOUT_BODY"
    fi
fi

# ==========================================
# SUMMARY
# ==========================================

echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}TEST SUMMARY${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
TOTAL=$((TESTS_PASSED + TESTS_FAILED))
echo -e "Total:  $TOTAL"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}🎉 All tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}❌ Some tests failed${NC}"
    exit 1
fi
