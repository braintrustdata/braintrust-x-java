# Braintrust Java SDK

An OpenTelemetry-based Braintrust SDK for Java 17 and up. Contains instrumentation for major AI vendors (OpenAI, Anthropic, etc)

**WARNING (HERE BE DRAGONS):** *This is an experimental SDK for Braintrust. APIs are in active development and cannot be relied upon.*

# Quickstart (Running examples and reporting data to Braintrust)

Prerequisites:
- A Braintrust account and a valid BRAINTRUST_API_KEY
- Java 17 (or greater):
    - macOS: `brew install openjdk@17`
    - Ubuntu: `sudo apt install openjdk-17-jdk`

Then, from the repo root:
```
# assumes you have BRAINTRUST_API_KEY exported
./gradlew :examples:runSimpleOpenTelemetry
```

If you wish to see all examples run:
```
./gradlew :examples:tasks --group="Braintrust SDK Examples"
```

Source code for all examples can be found here: [./examples/src/main/java/dev/braintrust/examples](./examples/src/main/java/dev/braintrust/examples)

If you wish to hack around with the examples you can modify source then re-run the gradle task.

# Using the SDK in your code

TODO: update this section when we have published our first release

# Developer Setup

This section documents how to set up your machine to develop the SDK. This is not required if you simply wish to use the SDK or run examples.

## Setup

TODO -- document sdkman, gradle, etc

## Running a local OpenTelemetry collector

OpenTelemetry provides a local collector with a debug exporter which logs all traces, logs, and metrics to stdout.

To run a local collector:

```
# Assumes you're in the repo root
docker run --rm -p 4318:4318 -v "$PWD/localcollector/collector.yaml:/etc/otelcol/config.yaml" otel/opentelemetry-collector:latest
```

To send otel data to the local collector:

```
# assumes you have BRAINTRUST_API_KEY and OPENAI_API_KEY exported
export BRAINTRUST_API_URL="http://localhost:4318" ; export BRAINTRUST_TRACES_PATH="/v1/traces"; export BRAINTRUST_LOGS_PATH="/v1/logs" ; ./gradlew :examples:runOpenAIInstrumentation
```


## TODO: finish these docs
