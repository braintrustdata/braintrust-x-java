# Makefile for Braintrust Java SDK
# This is a convenience wrapper around Gradle commands

.PHONY: help build test clean run-examples docs check-java setup

# Default target
help:
	@echo "Braintrust Java SDK - Make targets:"
	@echo "  make setup       - Check Java installation and setup"
	@echo "  make build       - Build the project"
	@echo "  make test        - Run all tests"
	@echo "  make clean       - Clean build artifacts"
	@echo "  make docs        - Generate Javadoc"
	@echo "  make run-examples - Run example applications"
	@echo "  make check-java  - Verify Java 17 is installed"

# Check Java version
check-java:
	@echo "Checking Java version..."
	@java -version 2>&1 | grep -q "17\." && echo "✓ Java 17 found" || (echo "✗ Java 17 required. Install with: brew install openjdk@17" && exit 1)

# Initial setup
setup: check-java
	@echo "Setting up project..."
	@test -f gradlew || (echo "✗ gradlew not found" && exit 1)
	@chmod +x gradlew
	@echo "✓ Project setup complete"

# Build project
build: setup
	./gradlew build

# Run tests
test: setup
	./gradlew test

# Clean build artifacts  
clean:
	./gradlew clean
	@echo "✓ Build artifacts cleaned"

# Generate documentation
docs: setup
	./gradlew javadoc
	@echo "✓ Documentation generated in build/docs/javadoc"

# Run examples
run-examples: build
	@echo "Running examples..."
	@echo "1. Quick Start Example:"
	./gradlew :examples:run
	@echo "\n2. Evaluation Example:"
	./gradlew :examples:runEvaluation
	@echo "\n3. Dataset Example:"
	./gradlew :examples:runDataset

# Development workflow
dev: clean build test
	@echo "✓ Development build complete"

# CI/CD build
ci: clean build test docs
	@echo "✓ CI build complete"