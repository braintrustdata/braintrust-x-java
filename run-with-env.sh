#!/bin/bash

# Script to run Gradle tasks with environment variables

# Set a valid test API key format (still won't work with real API, but allows compilation/testing)
export BRAINTRUST_API_KEY="sk-test-1234567890abcdef1234567890abcdef"

# Check if we should use staging
if [ "$1" == "--staging" ]; then
    export BRAINTRUST_API_URL="https://staging-api.braintrust.dev"
    shift
fi

# Run the gradle command passed as arguments
if [ -z "$1" ]; then
    echo "Usage: ./run-with-env.sh [--staging] <gradle-task>"
    echo "Example: ./run-with-env.sh :examples:runOpenTelemetry"
    echo "Example: ./run-with-env.sh --staging :examples:runTestEndpoints"
    echo ""
    echo "Note: Using test API key. Will compile but API calls will fail with auth errors."
    exit 1
fi

echo "Running with test BRAINTRUST_API_KEY..."
if [ -n "$BRAINTRUST_API_URL" ]; then
    echo "Using API URL: $BRAINTRUST_API_URL"
fi

cd "$(dirname "$0")"
./gradlew "$@"