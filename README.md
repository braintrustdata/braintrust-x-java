# Braintrust Java SDK

An OpenTelemetry-based Braintrust SDK for Java 17 and up. Contains instrumentation for major AI vendors (OpenAI, Anthropic, etc)

# Basic Usage

## Using the SDK in your code

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'dev.braintrust:braintrust-x-java:0.1.0'
}
```

## Running examples

NOTE: if you wish to develop the SDK you can skip this section (developer setup includes a better way to set up the JDK).

First, install java 17 or greater

- **Java 17** (LTS) - Required for building and running
  - macOS: `brew install openjdk@17`
  - Ubuntu/Debian: `sudo apt install openjdk-17-jdk`
  - Windows: Download from [Adoptium](https://adoptium.net/)

```
# assumes you have BRAINTRUST_API_KEY and OPENAI_API_KEY exported
./gradlew :examples:runOpenAIInstrumentation
```

# Developer Docs
## Setup

TODO -- document sdkman, gradle, etc

## Running a local OpenTelemetry collector

OpenTelemetry provides a local collector with a debug exporter which logs all traces, logs, and metrics to stdout.

To run a local collector:

```
# Assumes you're in the repo root
docker run --rm -p 4318:4318 -v "$PWD/localcollector/collector.yaml:/etc/otelcol/config.yaml" otel/opentelemetry-collector:latest
```

## TODO: finish these docs
