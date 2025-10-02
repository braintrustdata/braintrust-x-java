# Braintrust Java SDK

An OpenTelemetry-based Braintrust SDK for Java 17 and up. Contains:

- Instrumentation for major AI vendors (OpenAI, Anthropic, etc)
- LLM Eval framework for sending Experiments to Braintrust
- Utils for sending Open Telemetry data to Braintrust

If you're simply looking to call the Braintrust API with Java code, see [https://github.com/braintrustdata/braintrust-java](https://github.com/braintrustdata/braintrust-java)

This SDK is currently is in BETA status and APIs may change

<!-- # Using the SDK in your code -->

<!-- *NOTE: The SDK has not published a release to maven yet. This section will not work until our first release is published* -->

<!-- build.gradle -->
<!-- ```gradle -->
<!-- dependencies { -->
<!--   implementation 'dev.braintrust:sdk:0.0.1' -->
<!-- } -->
<!-- ``` -->

# Examples

All examples can be found here: [./examples/src/main/java/dev/braintrust/examples](./examples/src/main/java/dev/braintrust/examples)

To run the examples from the command line you need:
- A Braintrust account and a valid BRAINTRUST_API_KEY
- Java 17 (or greater):
    - macOS: `brew install openjdk@17`
    - Ubuntu: `sudo apt install openjdk-17-jdk`
- (Optional to run the oai example) a valid OPENAI_API_KEY

Then, from the repo root:
```
./gradlew :examples:runSimpleOpenTelemetry
```

If you wish to see all examples run:
```
./gradlew :examples:tasks --group="Braintrust SDK Examples"
```

If you wish to hack around with the examples you can modify source then re-run the gradle task.

## Logging

The SDK uses a standard slf4j logger and will use the default log level (or not log at all if slf4j is not installed).

All Braintrust loggers will log into the `dev.braintrust` namespace. To adjust the log level, consult your logger documentation.

For example, to enable debug logging for slf4j-simple you would set the system property `org.slf4j.simpleLogger.log.dev.braintrust=DEBUG`

# SDK Developer Docs

The remaining sections document developing the SDK itself. Nothing below is required if you simply wish to use the SDK or run examples.

## Setup

- Install JDK 17
  - Recommended to use SDK Man: https://sdkman.io/ and `sdk use java 17.0.16-tem`
- Ensure you can run all tests and checks: `./gradlew check build`
- IDE Setup
  - Intellij Community
    - Ubuntu: `sudo snap install intellij-idea-community`
    - Other: https://www.jetbrains.com/idea/download/
- (Optional) Install pre-commit hooks: `./gradlew installGitHooks`
  - These hooks automatically run common checks for you but CI also runs the same checks before merging to the main branch is allowed
  - NOTE: this will overwrite existing hooks. Take backups before running

## Releasing the SDK

TODO: polish this section

- summary: push the button on github
- bumping a major

## Running a local OpenTelemetry collector

OpenTelemetry provides a local collector with a debug exporter which logs all traces, logs, and metrics to stdout.

To run a local collector:

```
# Assumes you're in the repo root
docker run --rm -p 4318:4318 -v "$PWD/localcollector/collector.yaml:/etc/otelcol/config.yaml" otel/opentelemetry-collector:0.136.0 # latest release will probably also work
```

To send Braintrust otel data to the local collector:

```
# assumes you have BRAINTRUST_API_KEY and OPENAI_API_KEY exported
export BRAINTRUST_API_URL="http://localhost:4318" ; export BRAINTRUST_TRACES_PATH="/v1/traces"; export BRAINTRUST_LOGS_PATH="/v1/logs" ; ./gradlew :examples:runOpenAIInstrumentation
```
