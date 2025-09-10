# Braintrust Java SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An OpenTelemetry-based Braintrust SDK for Java that leverages modern Java features and idiomatic patterns.

## Features

- **OpenTelemetry Integration**: Built on OTEL for standards-based distributed tracing
- **Modern Java**: Uses Java 17+ features including records, pattern matching, and streams
- **Type-Safe Evaluation Framework**: Generic evaluation system with compile-time type safety
- **Async API Client**: Non-blocking HTTP client using Java 11+ HTTP APIs
- **Library Instrumentation**: Automatic tracing for OpenAI and Anthropic SDKs
- **Functional Programming**: Extensive use of lambdas, method references, and functional interfaces

## Installation

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'dev.braintrust:braintrust-x-java:0.1.0'
}
```

## Quick Start

### Try It Out

```bash
# 1. Set up environment
cp .env.example .env
# Edit .env with your BRAINTRUST_API_KEY and OPENAI_API_KEY

# 2. Run the canonical evaluation example
./run-with-env.sh :examples:runTriviaEval

# 3. View results in Braintrust UI (link printed in output)
```

### Basic Usage

```java
import config.dev.braintrust.claude.BraintrustConfig;
import trace.dev.braintrust.claude.BraintrustTracing;

// Initialize with environment variables
var openTelemetry = BraintrustTracing.quickstart();

// Or with custom configuration
var config = BraintrustConfig.create(builder -> builder
    .apiKey("your-api-key")
    .defaultProjectId("my-project")
    .enableTraceConsoleLog(true)
);

var openTelemetry = BraintrustTracing.quickstart(config);
```

## Evaluation Framework

The evaluation framework uses Java generics and functional interfaces for type-safe, flexible testing.

### Canonical Example: TriviaEvaluation

See `examples/src/main/java/dev/braintrust/examples/TriviaEvaluation.java` for a complete real-world example that:
- Makes actual OpenAI API calls using the wrapped client
- Evaluates LLM responses against ground truth
- Scores based on accuracy and response time
- Tracks token usage and costs
- Demonstrates proper OpenTelemetry integration

Run it with: `./run-with-env.sh :examples:runTriviaEval`

### Basic Usage

```java
// Define your test cases using records
record TestCase(String input, String expected) {}

// Simple approach with automatic experiment registration (like Go SDK)
var results = Evaluation.<TestCase, String>builder()
    .name("My Evaluation")
    .data(testCases)
    .task(testCase -> myModel.process(testCase.input))
    .scorer("accuracy", (test, output) -> output.equals(test.expected) ? 1.0 : 0.0)
    .experiment("my-experiment", "my-project")  // Automatically creates both!
    .build()
    .run();

// Or use the lower-level API matching Go's RegisterExperiment
var project = Project.registerProjectSync("my-project");
var experiment = Experiment.registerExperimentSync("my-experiment", project.id());

var evaluation = Evaluation.<TestCase, String>builder()
    .name("My Evaluation")
    .data(testCases)
    .task(testCase -> myModel.process(testCase.input))
    .scorer(Scorer.StringScorers.exactMatch(tc -> tc.expected))
    .experimentId(experiment.id())
    .parallel(true)
    .build();
```

## Idiomatic Java Features

### Records for Data Classes
```java
record MathProblem(String question, int expected) {}
record ModelOutput(String answer, double confidence) {}
```

### Stream Processing
```java
// Process evaluation results
var scoreDistribution = results.results().stream()
    .collect(Collectors.groupingBy(
        r -> (int)(r.averageScore() * 10) / 10.0,
        Collectors.counting()
    ));
```

### CompletableFuture for Async Operations
```java
// Async API calls with composition
apiClient.createProject("my-project")
    .thenCompose(project -> apiClient.createExperiment(
        new CreateExperimentRequest(project.id(), "experiment-1")
    ))
    .thenAccept(experiment -> 
        System.out.println("Created experiment: " + experiment.id())
    );
```

### Functional Interfaces and Method References
```java
// Custom scorers using functional interfaces
.scorer("length_match", (input, output) -> 
    output.length() == input.expectedLength() ? 1.0 : 0.0)
    
// Method references for clean code
.data(files.stream().map(File::toPath))
.task(this::processFile)
```

### Try-with-Resources for Span Management
```java
try (var ignored = tracer.spanBuilder("operation")
        .startSpan()
        .makeCurrent()) {
    
    // Your traced code here
    performOperation();
}
```

### Optional for Null Safety
```java
config.defaultProjectId()
    .ifPresent(id -> span.setAttribute("project_id", id));
```

## Library Instrumentation

### OpenAI Integration

The SDK provides a wrapped OpenAI client that automatically adds Braintrust-specific tracing:

```java
import openai.dev.braintrust.claude.BraintrustOpenAI;
import com.openai.client.okhttp.OpenAIOkHttpClient;

// Create wrapped client
var openAI = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
var wrappedClient = BraintrustOpenAI.wrap(openAI, tracer);

// Use it exactly like the regular OpenAI client
var response = wrappedClient.chat().completions().create(
    ChatCompletionCreateParams.builder()
        .model(ChatModel.GPT_4O_MINI)
        .addSystemMessage("You are a helpful assistant.")
        .addUserMessage("What is 2 + 2?")
        .build(),
    // Optionally provide message info for better tracing
    List.of(
        BraintrustOpenAI.MessageInfo.system("You are a helpful assistant."),
        BraintrustOpenAI.MessageInfo.user("What is 2 + 2?")
    )
);
```

This automatically captures:
- Input messages as `braintrust.input_json`
- Output responses as `braintrust.output_json`
- Model metadata as `braintrust.metadata_json`
- Token usage as `braintrust.metrics_json`

See the TriviaEvaluation example for a complete working implementation.

### Anthropic Integration
```java
// Async Anthropic calls with tracing
AnthropicInterceptor.traceMessageAsync(
    request,
    requestExtractor,
    responseExtractor,
    () -> anthropicClient.sendMessageAsync(request)
);
```

## Advanced Usage

### Custom Span Processors
```java
var spanProcessor = new BraintrustSpanProcessor.Builder(config)
    .withExporter(customExporter)
    .enableConsoleLog(true)
    .build();
```

### Weighted Scorers
```java
var weightedScorer = Scorer.weightedAverage(Map.of(
    accuracyScorer, 0.7,
    speedScorer, 0.3
));
```

### Parallel Evaluation with Custom Executor
```java
var customExecutor = new ForkJoinPool(16);

var results = Evaluation.<Input, Output>builder()
    .executor(customExecutor)
    .parallel(true)
    .timeout(Duration.ofSeconds(30))
    .run();
```

## Environment Variables

- `BRAINTRUST_API_KEY`: API key for authentication
- `BRAINTRUST_API_URL`: API endpoint (default: https://api.braintrust.dev)
- `BRAINTRUST_DEFAULT_PROJECT_ID`: Default project for spans
- `BRAINTRUST_ENABLE_TRACE_CONSOLE_LOG`: Enable console trace output
- `BRAINTRUST_DEBUG`: Enable debug logging

## Requirements

- **Java 17** (LTS) - Required for building and running
  - macOS: `brew install openjdk@17`
  - Ubuntu/Debian: `sudo apt install openjdk-17-jdk`
  - Windows: Download from [Adoptium](https://adoptium.net/)
  
- **Gradle** - Build tool (wrapper included)

## Setup

### 1. Install Java 17

```bash
# macOS with Homebrew
brew install openjdk@17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Or add to your shell profile for permanent setup
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
```

### 2. Verify Java Version

```bash
java -version
# Should output: openjdk version "17.0.x"
```

### 3. Building

The project includes Gradle Wrapper, so you don't need to install Gradle separately:

```bash
# First time setup (downloads Gradle automatically)
./gradlew build

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

## Development

### IDE Setup

- **IntelliJ IDEA**: Import as Gradle project
- **VS Code**: Install "Extension Pack for Java"
- **Eclipse**: Import as Gradle project

### Code Formatting

This project uses [Google Java Format](https://github.com/google/google-java-format) for consistent code style.

```bash
# Check code formatting
./gradlew spotlessCheck

# Fix formatting issues
./gradlew spotlessApply

# Install git pre-commit hooks
./gradlew installGitHooks
```

The pre-commit hook will automatically check formatting before each commit. To skip it temporarily:
```bash
git commit --no-verify
```

### Common Tasks

```bash
# Run tests with detailed output
./gradlew test --info

# Build without tests
./gradlew build -x test

# Generate Javadoc
./gradlew javadoc

# Check dependencies
./gradlew dependencies

# Update Gradle wrapper
./gradlew wrapper --gradle-version=8.10.2
```

## Running Examples

### Setup Environment

```bash
# Copy the example environment file
cp .env.example .env

# Edit .env and add your API keys:
# BRAINTRUST_API_KEY=your-actual-api-key
# OPENAI_API_KEY=your-openai-api-key  # Required for TriviaEvaluation example
```

### Canonical Examples

#### Logging and Tracing
```bash
# Basic OpenTelemetry example with manual spans
./run-with-env.sh :examples:runOpenTelemetry

# Simple logging example
./run-with-env.sh :examples:runSimple

# Project management example
./run-with-env.sh :examples:runProject
```

#### Evaluations
```bash
# Canonical evaluation example - runs a real LLM evaluation with OpenAI
# This example evaluates GPT-4o-mini on trivia questions with accuracy scoring
./run-with-env.sh :examples:runTriviaEval

# Go-style experiment example (simple math evaluation)
./run-with-env.sh :examples:runGoStyleExperiment

# Other evaluation examples
./run-with-env.sh :examples:runSimpleExperiment
./run-with-env.sh :examples:runExperimentWithApi
```

### What Each Example Demonstrates

- **`:examples:runOpenTelemetry`** - Manual span creation, nested spans, and OpenAI integration
- **`:examples:runTriviaEval`** - Real LLM evaluation with OpenAI, scoring, and metrics tracking
- **`:examples:runGoStyleExperiment`** - Mimics Go SDK patterns with automatic project/experiment registration
- **`:examples:runSimple`** - Basic tracing without evaluations
- **`:examples:runProject`** - Project and dataset management via API

## Testing

```bash
./gradlew test
```

## Project Structure

### Files to Commit to Git

The following Gradle files should be committed:
- `build.gradle` - Build configuration
- `settings.gradle` - Project settings
- `gradle.properties` - Gradle properties
- `gradlew` - Gradle wrapper script (Unix)
- `gradlew.bat` - Gradle wrapper script (Windows) 
- `gradle/wrapper/gradle-wrapper.jar` - Gradle wrapper JAR
- `gradle/wrapper/gradle-wrapper.properties` - Wrapper configuration

The `.gitignore` file is configured to exclude:
- `.gradle/` - Gradle cache directory
- `build/` - Build outputs
- IDE files (`.idea/`, `*.iml`, etc.)
- Compiled classes (`*.class`)

## License

MIT
