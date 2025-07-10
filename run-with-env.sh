#!/bin/bash

# Script to run Gradle tasks with environment variables from .env file

cd "$(dirname "$0")"

# Check if .env file exists
if [ -f .env ]; then
    # Load environment variables from .env file
    set -a
    source .env
    set +a
    echo "Loaded environment variables from .env file"
else
    echo "Warning: .env file not found!"
    echo "Using fallback test API key for compilation only."
    export BRAINTRUST_API_KEY="sk-test-1234567890abcdef1234567890abcdef"
fi

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
    if [ ! -f .env ]; then
        echo "To use a real API key, create a .env file with:"
        echo "BRAINTRUST_API_KEY=your-actual-api-key"
    fi
    exit 1
fi

if [ -n "$BRAINTRUST_API_KEY" ]; then
    echo "Running with BRAINTRUST_API_KEY set (${BRAINTRUST_API_KEY:0:10}...)"
else
    echo "Error: BRAINTRUST_API_KEY not set!"
    exit 1
fi

if [ -n "$BRAINTRUST_API_URL" ]; then
    echo "Using API URL: $BRAINTRUST_API_URL"
fi

./gradlew "$@"