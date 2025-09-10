package dev.braintrust.claude.examples;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.braintrust.claude.config.BraintrustConfig;
import dev.braintrust.claude.log.BraintrustLogger;
import dev.braintrust.claude.trace.BraintrustSpanProcessor;
import dev.braintrust.claude.trace.BraintrustTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.time.Duration;

/**
 * Comprehensive example demonstrating Braintrust with OpenTelemetry and OpenAI.
 *
 * <p>This example shows: - Setting up Braintrust with OpenTelemetry - Making real OpenAI API calls
 * using the official SDK - Wrapping calls with Braintrust instrumentation - Logging results with
 * custom metadata and scores
 */
public class OpenTelemetryExample {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String BRAINTRUST_API_KEY = System.getenv("BRAINTRUST_API_KEY");

    public static void main(String[] args) {
        // Validate environment variables
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty()) {
            System.err.println("Error: OPENAI_API_KEY environment variable is not set");
            System.exit(1);
        }

        if (BRAINTRUST_API_KEY == null || BRAINTRUST_API_KEY.isEmpty()) {
            System.err.println("Error: BRAINTRUST_API_KEY environment variable is not set");
            System.exit(1);
        }

        try {

            // Initialize Braintrust with OpenTelemetry
            // You can set the project ID here or via BRAINTRUST_PROJECT_ID env var
            String projectId = System.getenv("BRAINTRUST_PROJECT_ID");
            if (projectId == null || projectId.isEmpty()) {
                projectId = "opentelemetry-example"; // Default project ID
            }

            var config =
                    BraintrustConfig.builder()
                            .apiKey(BRAINTRUST_API_KEY)
                            .defaultProjectId(projectId) // Set default project
                            .build();

            var openTelemetry =
                    BraintrustTracing.quickstart(
                            config,
                            builder ->
                                    builder.serviceName("opentelemetry-example")
                                            .resourceAttribute("environment", "development")
                                            .resourceAttribute("team", "engineering")
                                            .exportInterval(Duration.ofSeconds(2)));

            var tracer = BraintrustTracing.getTracer(openTelemetry);

            // Create OpenAI client
            var openAiClient = OpenAIOkHttpClient.fromEnv(); // Uses OPENAI_API_KEY env var

            // Example 1: Simple non-streaming completion
            System.out.println("\n=== Example 1: Non-Streaming Completion ===");
            performNonStreamingCompletion(openAiClient, tracer);

            // Example 2: Complex multi-step operation with custom scoring
            System.out.println("\n=== Example 2: Multi-Step Operation with Scoring ===");
            performComplexOperation(openAiClient, tracer);

            // Example 3: Error handling demonstration
            System.out.println("\n=== Example 3: Error Handling ===");
            demonstrateErrorHandling(openAiClient, tracer);

            // Initialize logger and test it
            System.out.println("\n=== Example 4: Braintrust Logger ===");
            demonstrateLogger();

            // Give time for spans to export
            System.out.println("\nWaiting for spans to export...");
            Thread.sleep(5000);

            System.out.println("\nAll examples completed successfully!");
            System.out.println("Check your Braintrust dashboard to see the traces.");

        } catch (Exception e) {
            System.err.println("Error running example: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void performNonStreamingCompletion(
            OpenAIClient client, io.opentelemetry.api.trace.Tracer tracer) {
        // Create a parent span for the operation
        var parentSpan =
                tracer.spanBuilder("example.non_streaming_completion")
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("example.type", "non_streaming")
                        .startSpan();

        try (Scope scope = parentSpan.makeCurrent()) {
            // Create chat completion request
            var request =
                    ChatCompletionCreateParams.builder()
                            .model(ChatModel.GPT_4O)
                            .addSystemMessage("You are a helpful assistant that writes poetry.")
                            .addUserMessage("Write a haiku about OpenTelemetry and observability.")
                            .maxTokens(100L)
                            .temperature(0.7)
                            .build();

            // Wrap the OpenAI call with tracing
            var apiSpan =
                    tracer.spanBuilder("openai.chat.completion")
                            .setSpanKind(SpanKind.CLIENT)
                            .setAttribute("openai.model", request.model().toString())
                            .setAttribute("openai.max_tokens", request.maxTokens().orElse(0L))
                            .setAttribute("openai.temperature", request.temperature().orElse(1.0))
                            .startSpan();

            ChatCompletion response;
            try (Scope apiScope = apiSpan.makeCurrent()) {
                response = client.chat().completions().create(request);

                // Add usage metrics
                if (response.usage().isPresent()) {
                    var usage = response.usage().get();
                    apiSpan.setAttribute(
                            BraintrustSpanProcessor.USAGE_PROMPT_TOKENS, usage.promptTokens());
                    apiSpan.setAttribute(
                            BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS,
                            usage.completionTokens());
                    apiSpan.setAttribute(
                            BraintrustSpanProcessor.USAGE_TOTAL_TOKENS, usage.totalTokens());

                    var estimatedCost =
                            estimateCost("gpt-4o", usage.promptTokens(), usage.completionTokens());
                    apiSpan.setAttribute(BraintrustSpanProcessor.USAGE_COST, estimatedCost);
                }

                apiSpan.setStatus(StatusCode.OK);
            } catch (Exception e) {
                apiSpan.recordException(e);
                apiSpan.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                apiSpan.end();
            }

            // Log the response
            var completion = response.choices().get(0).message().content().orElse("");
            System.out.println("Haiku: " + completion);

            // Add custom metadata and scoring
            var currentSpan = Span.current();
            currentSpan.setAttribute("example.haiku", completion);
            currentSpan.setAttribute("example.quality_score", evaluateHaikuQuality(completion));

            // Add Braintrust-specific scoring
            BraintrustTracing.SpanUtils.addScore("haiku_quality", evaluateHaikuQuality(completion));

            parentSpan.setStatus(StatusCode.OK);

        } catch (Exception e) {
            parentSpan.recordException(e);
            parentSpan.setStatus(StatusCode.ERROR, e.getMessage());
            System.err.println("Error in non-streaming completion: " + e.getMessage());
        } finally {
            parentSpan.end();
        }
    }

    private static void performComplexOperation(
            OpenAIClient client, io.opentelemetry.api.trace.Tracer tracer) {
        var rootSpan =
                tracer.spanBuilder("example.complex_operation")
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("example.type", "complex")
                        .startSpan();

        try (Scope rootScope = rootSpan.makeCurrent()) {
            // Step 1: Generate a story outline
            var outlineSpan =
                    tracer.spanBuilder("generate_outline")
                            .setSpanKind(SpanKind.INTERNAL)
                            .startSpan();

            String outline;
            try (Scope outlineScope = outlineSpan.makeCurrent()) {
                outline = generateStoryOutline(client);
                outlineSpan.setAttribute("outline.length", outline.length());
                BraintrustTracing.SpanUtils.addScore("outline_quality", 0.85);
                outlineSpan.setStatus(StatusCode.OK);
            } finally {
                outlineSpan.end();
            }

            // Step 2: Expand the outline into a story
            var storySpan =
                    tracer.spanBuilder("expand_story").setSpanKind(SpanKind.INTERNAL).startSpan();

            String story;
            try (Scope storyScope = storySpan.makeCurrent()) {
                story = expandOutlineToStory(client, outline);
                storySpan.setAttribute("story.length", story.length());
                storySpan.setAttribute("story.word_count", story.split("\\s+").length);
                BraintrustTracing.SpanUtils.addScore("story_coherence", 0.92);
                storySpan.setStatus(StatusCode.OK);
            } finally {
                storySpan.end();
            }

            // Step 3: Generate a title
            var titleSpan =
                    tracer.spanBuilder("generate_title").setSpanKind(SpanKind.INTERNAL).startSpan();

            String title;
            try (Scope titleScope = titleSpan.makeCurrent()) {
                title = generateTitle(client, story);
                titleSpan.setAttribute("title", title);
                BraintrustTracing.SpanUtils.addScore("title_relevance", 0.88);
                titleSpan.setStatus(StatusCode.OK);
            } finally {
                titleSpan.end();
            }

            // Add overall operation metadata
            rootSpan.setAttribute("operation.success", true);
            rootSpan.setAttribute("operation.total_api_calls", 3);
            BraintrustTracing.SpanUtils.addScore("overall_quality", 0.88);

            System.out.println("\nGenerated Story:");
            System.out.println("Title: " + title);
            System.out.println(
                    "\nStory Preview: "
                            + story.substring(0, Math.min(200, story.length()))
                            + "...");

            rootSpan.setStatus(StatusCode.OK);

        } catch (Exception e) {
            rootSpan.recordException(e);
            rootSpan.setStatus(StatusCode.ERROR, e.getMessage());
            System.err.println("Error in complex operation: " + e.getMessage());
        } finally {
            rootSpan.end();
        }
    }

    private static void demonstrateErrorHandling(
            OpenAIClient client, io.opentelemetry.api.trace.Tracer tracer) {
        var span =
                tracer.spanBuilder("example.error_handling")
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("example.type", "error_demo")
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Intentionally cause an error with invalid parameters
            var request =
                    ChatCompletionCreateParams.builder()
                            .model(ChatModel.of("invalid-model-name"))
                            .addUserMessage("This should fail")
                            .maxTokens(-1L) // Invalid max tokens
                            .build();

            try {
                var apiSpan =
                        tracer.spanBuilder("openai.chat.completion.error")
                                .setSpanKind(SpanKind.CLIENT)
                                .startSpan();

                try (Scope apiScope = apiSpan.makeCurrent()) {
                    client.chat().completions().create(request);
                    apiSpan.setStatus(StatusCode.OK);
                } finally {
                    apiSpan.end();
                }

                System.out.println("Unexpected success!");

            } catch (Exception e) {
                System.out.println("Expected error occurred: " + e.getMessage());
                span.recordException(e);
                span.setAttribute("error.handled", true);
                span.setAttribute("error.type", e.getClass().getSimpleName());
            }

            // Demonstrate recovery
            span.addEvent("Attempting recovery with valid parameters");

            var validRequest =
                    ChatCompletionCreateParams.builder()
                            .model(ChatModel.GPT_4O)
                            .addUserMessage("Say 'recovered!'")
                            .maxTokens(10L)
                            .build();

            var recovery = client.chat().completions().create(validRequest);

            System.out.println(
                    "Recovery successful: "
                            + recovery.choices().get(0).message().content().orElse(""));

            span.setAttribute("recovery.success", true);
            span.setStatus(StatusCode.OK, "Recovered from error");

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            System.err.println("Unrecoverable error: " + e.getMessage());
        } finally {
            span.end();
        }
    }

    private static void demonstrateLogger() {
        // Enable debug logging
        BraintrustLogger.setDebugEnabled(true);

        // Log various events
        BraintrustLogger.info(
                "Application started with version={} environment={}", "1.0.0", "development");

        BraintrustLogger.info(
                "API performance metrics: score={} endpoint={} latency_ms={}",
                0.95,
                "/api/chat",
                250);

        BraintrustLogger.info(
                "Model quality score={} model={} task={}", 0.88, "gpt-4o", "summarization");

        BraintrustLogger.debug("Debug information: request_id={}", "12345");

        // Simulate an error
        try {
            throw new RuntimeException("Simulated error for logging");
        } catch (Exception e) {
            BraintrustLogger.error(
                    "Simulated error occurred: error_type={} handled={}", e, "simulation", true);
        }

        System.out.println("Logger demonstration completed");
    }

    // Helper methods

    private static String generateStoryOutline(OpenAIClient client) {
        var request =
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addSystemMessage("You are a creative writing assistant.")
                        .addUserMessage(
                                "Create a brief outline for a short story about AI and humanity."
                                        + " Include 3 main plot points.")
                        .maxTokens(150L)
                        .temperature(0.8)
                        .build();

        var response = client.chat().completions().create(request);
        return response.choices().get(0).message().content().orElse("");
    }

    private static String expandOutlineToStory(OpenAIClient client, String outline) {
        var request =
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addSystemMessage("You are a creative writing assistant.")
                        .addUserMessage("Expand this outline into a 200-word story:\n\n" + outline)
                        .maxTokens(300L)
                        .temperature(0.7)
                        .build();

        var response = client.chat().completions().create(request);
        return response.choices().get(0).message().content().orElse("");
    }

    private static String generateTitle(OpenAIClient client, String story) {
        var request =
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addSystemMessage("You are a creative writing assistant.")
                        .addUserMessage("Generate a compelling title for this story:\n\n" + story)
                        .maxTokens(20L)
                        .temperature(0.9)
                        .build();

        var response = client.chat().completions().create(request);
        return response.choices().get(0).message().content().orElse("").trim();
    }

    private static double evaluateHaikuQuality(String haiku) {
        // Simple heuristic: check if it has approximately 3 lines
        var lines = haiku.split("\n");
        if (lines.length >= 3) {
            return 0.9;
        } else if (lines.length == 2) {
            return 0.7;
        } else {
            return 0.5;
        }
    }

    private static double estimateCost(String model, long promptTokens, long completionTokens) {
        // Simplified pricing based on OpenAI's pricing (as of 2024)
        var modelLower = model.toLowerCase();
        double promptCostPer1k, completionCostPer1k;

        if (modelLower.contains("gpt-4o")) {
            promptCostPer1k = 0.005; // $5 per 1M input tokens
            completionCostPer1k = 0.015; // $15 per 1M output tokens
        } else if (modelLower.contains("gpt-4")) {
            promptCostPer1k = 0.03;
            completionCostPer1k = 0.06;
        } else if (modelLower.contains("gpt-3.5-turbo")) {
            promptCostPer1k = 0.0005;
            completionCostPer1k = 0.0015;
        } else {
            promptCostPer1k = 0.002;
            completionCostPer1k = 0.002;
        }

        return (promptTokens / 1000.0 * promptCostPer1k)
                + (completionTokens / 1000.0 * completionCostPer1k);
    }
}
