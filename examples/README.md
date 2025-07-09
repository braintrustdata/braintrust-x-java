# Braintrust Java SDK Examples

This example demonstrates how to use Braintrust with OpenTelemetry to instrument OpenAI API calls in Java. It showcases various features including streaming/non-streaming completions, custom scoring, error handling, and complex multi-step operations.

## Prerequisites

1. **Java 17 or higher** - The example uses modern Java features like records and var
2. **Gradle** - For building and running the example
3. **API Keys** - You'll need both OpenAI and Braintrust API keys

## Setup

### 1. Get Your API Keys

#### OpenAI API Key
- Sign up or log in at [OpenAI Platform](https://platform.openai.com/)
- Navigate to [API Keys](https://platform.openai.com/api-keys)
- Create a new secret key and save it securely

#### Braintrust API Key
- Sign up or log in at [Braintrust](https://www.braintrust.dev/)
- Go to your project settings
- Create a new API key and save it securely

### 2. Set Environment Variables

Export your API keys as environment variables:

```bash
export OPENAI_API_KEY="sk-..."
export BRAINTRUST_API_KEY="sk-..."
```

For Windows:
```cmd
set OPENAI_API_KEY=sk-...
set BRAINTRUST_API_KEY=sk-...
```

### 3. Build the Project

From the root directory of the braintrust-x-java project:

```bash
./gradlew build
```

## Available Examples

### 1. OpenTelemetry Example (`OpenTelemetryExample.java`)
Full example using OpenAI's GPT-4o model with real API calls.

```bash
./gradlew :examples:runOpenTelemetry
```

### 2. Simple Example (`SimpleExample.java`)
Demonstrates basic functionality without requiring API keys.

```bash
./gradlew :examples:runSimple
```

### 3. Project Example (`ProjectExample.java`)
Shows how Braintrust organizes traces into projects.

```bash
./gradlew :examples:runProject
```

## What the Example Demonstrates

### 1. Basic Setup
The example shows how to initialize Braintrust with OpenTelemetry:

```java
var config = BraintrustConfig.builder()
    .apiKey(BRAINTRUST_API_KEY)
    .build();

var openTelemetry = BraintrustTracing.quickstart(config, builder -> builder
    .serviceName("opentelemetry-example")
    .resourceAttribute("environment", "development")
    .resourceAttribute("team", "engineering")
    .exportInterval(Duration.ofSeconds(2))
);
```

### 2. Non-Streaming Completion
Demonstrates a simple chat completion with instrumentation:

```java
var response = OpenAIInterceptor.traceCompletion(
    "chat.completion",
    request,
    requestExtractor,
    responseExtractor,
    () -> service.createChatCompletion(request)
);
```

Features shown:
- Creating spans for API calls
- Automatic token usage tracking
- Cost estimation
- Custom metadata and scoring

### 3. Streaming Completion
Shows how to handle streaming responses:

```java
var stream = OpenAIInterceptor.traceStreamingCompletion(
    "chat.completion",
    request,
    requestExtractor,
    () -> service.streamChatCompletion(request),
    streamWrapper
);
```

Features shown:
- Wrapping streaming responses
- Collecting chunks for analysis
- Token usage estimation for streams

### 4. Complex Multi-Step Operations
Demonstrates a complex workflow with nested spans:

```java
// Root span for the entire operation
var rootSpan = tracer.spanBuilder("example.complex_operation")
    .setSpanKind(SpanKind.INTERNAL)
    .startSpan();

// Child spans for each step
var outlineSpan = tracer.spanBuilder("generate_outline")
    .setSpanKind(SpanKind.INTERNAL)
    .startSpan();
```

Features shown:
- Nested span hierarchies
- Step-by-step operation tracking
- Custom scoring at each step
- Aggregated metrics

### 5. Error Handling
Shows proper error handling and recovery:

```java
try {
    // Intentionally invalid request
    var response = OpenAIInterceptor.traceCompletion(...);
} catch (Exception e) {
    span.recordException(e);
    span.setAttribute("error.handled", true);
    // Recovery logic...
}
```

Features shown:
- Exception recording in spans
- Error attributes
- Recovery strategies
- Proper span status setting

## Understanding the Output

When you run the example, you'll see:

1. **Console Output** - Real-time feedback showing:
   - Generated haiku
   - Streaming text as it arrives
   - Story generation progress
   - Error handling demonstration

2. **Braintrust Dashboard** - After running, check your Braintrust dashboard to see:
   - Trace visualization
   - Token usage metrics
   - Cost tracking
   - Custom scores
   - Error details
   - Span hierarchy

## Key Features Demonstrated

### Custom Scoring
```java
BraintrustTracing.SpanUtils.addScore("haiku_quality", evaluateHaikuQuality(completion));
```

### Metadata Addition
```java
currentSpan.setAttribute("example.haiku", completion);
currentSpan.setAttribute("example.quality_score", score);
```

### Token Usage Tracking
Automatically captured from OpenAI responses:
- Prompt tokens
- Completion tokens
- Total tokens
- Estimated cost

### Error Recovery
```java
span.addEvent("Attempting recovery with valid parameters");
// Recovery logic...
span.setAttribute("recovery.success", true);
```

## Customization

You can customize the example by:

1. **Changing Models** - Try different OpenAI models:
   ```java
   .model("gpt-4") // or "gpt-3.5-turbo-16k"
   ```

2. **Adjusting Parameters**:
   ```java
   .temperature(0.9)  // More creative
   .maxTokens(500)    // Longer responses
   ```

3. **Adding Custom Attributes**:
   ```java
   span.setAttribute("custom.metric", value);
   span.setAttribute("user.id", userId);
   ```

4. **Implementing Custom Scoring**:
   ```java
   BraintrustTracing.SpanUtils.addScore("custom_score", calculateScore(response));
   ```

## Troubleshooting

### Connection Issues
If you see connection errors:
- Check your internet connection
- Verify API keys are correctly set
- Ensure Braintrust API endpoint is accessible

### Token Limit Errors
If you hit token limits:
- Reduce `maxTokens` in requests
- Use shorter prompts
- Consider using a model with higher limits

### Export Delays
Spans are exported in batches. If you don't see traces immediately:
- Wait a few seconds for batch export
- Check the export interval setting
- Ensure the application runs long enough for export

## Next Steps

1. **Explore Other Examples** - Check out the evaluation and dataset examples
2. **Integrate Into Your App** - Use this pattern in your own applications
3. **Custom Instrumentation** - Create spans for your own operations
4. **Advanced Features** - Explore Braintrust's evaluation and experimentation features

## Project Organization in Braintrust

In Braintrust, all traces are organized into projects. The project determines where your traces appear in the Braintrust dashboard.

### How Project Names are Determined

1. **Default: Service Name** - If no project is specified, Braintrust uses the service name from OpenTelemetry:
   ```java
   BraintrustTracing.quickstart(config, builder -> builder
       .serviceName("my-ai-app")  // This becomes the project name
   )
   ```

2. **Explicit Project ID** - Set a project for specific spans:
   ```java
   BraintrustTracing.SpanUtils.setParentProject("customer-support");
   ```

3. **Via Context** - Set a project for multiple spans:
   ```java
   var context = BraintrustContext.builder()
       .projectId("recommendation-engine")
       .build()
       .storeInCurrent();
   ```

4. **Environment Variable** - Set `BRAINTRUST_PROJECT_ID` to override the default

### Best Practices

- Use meaningful project names that reflect your application's purpose
- Group related traces in the same project for easier analysis
- Use different projects for different environments (dev, staging, prod)
- Consider using experiments for A/B testing and model comparisons

## Model Updates

The examples use OpenAI's GPT-4o model, which offers:
- Better performance than GPT-3.5-turbo
- More accurate responses
- Support for vision and other multimodal features
- Pricing: $5/1M input tokens, $15/1M output tokens

To use a different model, update the `ChatModel` parameter:
```java
.model(ChatModel.GPT_4O)        // GPT-4o (recommended)
.model(ChatModel.GPT_4_TURBO)   // GPT-4 Turbo
.model(ChatModel.GPT_3_5_TURBO) // GPT-3.5 Turbo
```

## Additional Resources

- [Braintrust Documentation](https://docs.braintrust.dev/)
- [OpenTelemetry Java Guide](https://opentelemetry.io/docs/instrumentation/java/)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)