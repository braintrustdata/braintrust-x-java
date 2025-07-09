# Braintrust Java SDK

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

```java
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;

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

The evaluation framework uses Java generics and functional interfaces for type-safe, flexible testing:

```java
// Define your test cases using records
record TestCase(String input, String expected) {}

// Run evaluation with multiple scorers
var results = Evaluation.<TestCase, String>builder()
    .name("My Evaluation")
    .data(testCases)
    .task(testCase -> myModel.process(testCase.input))
    .scorer(Scorer.StringScorers.exactMatch(tc -> tc.expected))
    .scorer(Scorer.StringScorers.levenshtein(tc -> tc.expected))
    .parallel(true)
    .run();

// Analyze results using streams
results.successful()
    .filter(r -> r.averageScore() < 0.8)
    .forEach(r -> System.out.println("Low score: " + r.input()));
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
```java
// Wrap OpenAI calls with tracing
OpenAIInterceptor.traceCompletion(
    "chat.completion",
    request,
    req -> new RequestDetails(req.getModel(), req.getMaxTokens(), req.getTemperature()),
    resp -> new ResponseDetails(new Usage(
        resp.getUsage().getPromptTokens(),
        resp.getUsage().getCompletionTokens(),
        resp.getUsage().getTotalTokens()
    )),
    () -> openAiService.createCompletion(request)
);
```

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

```bash
# Quick start example
./gradlew :examples:run

# Evaluation example
./gradlew :examples:runEvaluation

# Dataset example
./gradlew :examples:runDataset
```

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
