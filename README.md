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
