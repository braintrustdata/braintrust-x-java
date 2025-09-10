package dev.braintrust.claude.examples;

import dev.braintrust.claude.config.BraintrustConfig;
import dev.braintrust.claude.log.BraintrustLogger;
import dev.braintrust.claude.trace.BraintrustSpanProcessor;
import dev.braintrust.claude.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple example demonstrating Braintrust with OpenTelemetry. This example doesn't require external
 * API keys and shows basic usage.
 */
public class SimpleExample {

    public static void main(String[] args) {
        try {
            // Initialize Braintrust with OpenTelemetry
            // This will use BRAINTRUST_API_KEY if set, or run in demo mode
            var configBuilder = BraintrustConfig.builder();

            // Use API key if available, otherwise use demo mode
            var apiKey = System.getenv("BRAINTRUST_API_KEY");
            if (apiKey != null && !apiKey.isEmpty()) {
                configBuilder.apiKey(apiKey);
            } else {
                System.out.println("Running in demo mode (no BRAINTRUST_API_KEY set)");
                configBuilder.apiKey("demo-key");
                configBuilder.apiUrl("http://localhost:8080"); // Won't actually connect
            }

            var config = configBuilder.build();

            var openTelemetry =
                    BraintrustTracing.quickstart(
                            config,
                            builder ->
                                    builder.serviceName("simple-example")
                                            .resourceAttribute("environment", "development")
                                            .resourceAttribute("version", "1.0.0")
                                            .exportInterval(Duration.ofSeconds(2)));

            var tracer = BraintrustTracing.getTracer(openTelemetry);

            // Example 1: Simple operation with tracing
            System.out.println("\n=== Example 1: Simple Operation ===");
            performSimpleOperation(tracer);

            // Example 2: Nested operations
            System.out.println("\n=== Example 2: Nested Operations ===");
            performNestedOperations(tracer);

            // Example 3: Simulated LLM call
            System.out.println("\n=== Example 3: Simulated LLM Call ===");
            performSimulatedLLMCall(tracer);

            // Example 4: Logger demonstration
            System.out.println("\n=== Example 4: Logger Demo ===");
            demonstrateLogger();

            // Give time for spans to export
            System.out.println("\nWaiting for spans to export...");
            Thread.sleep(3000);

            System.out.println("\nExample completed successfully!");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println(
                        "\n"
                                + "Note: To see traces in Braintrust, set BRAINTRUST_API_KEY"
                                + " environment variable");
            }

        } catch (Exception e) {
            System.err.println("Error running example: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void performSimpleOperation(io.opentelemetry.api.trace.Tracer tracer) {
        var span =
                tracer.spanBuilder("simple.operation")
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("operation.type", "calculation")
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Simulate some work
            var result = calculateSomething();

            // Add attributes
            span.setAttribute("result", result);
            span.setAttribute("success", true);

            // Add a custom score
            BraintrustTracing.SpanUtils.addScore("accuracy", 0.95);

            System.out.println("Calculation result: " + result);
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
        } finally {
            span.end();
        }
    }

    private static void performNestedOperations(io.opentelemetry.api.trace.Tracer tracer) {
        var rootSpan = tracer.spanBuilder("nested.root").setSpanKind(SpanKind.INTERNAL).startSpan();

        try (Scope rootScope = rootSpan.makeCurrent()) {
            // Child operation 1
            var child1Span =
                    tracer.spanBuilder("nested.child1").setSpanKind(SpanKind.INTERNAL).startSpan();

            try (Scope child1Scope = child1Span.makeCurrent()) {
                Thread.sleep(50);
                child1Span.setAttribute("child1.result", "success");
                child1Span.setStatus(StatusCode.OK);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                child1Span.recordException(e);
                child1Span.setStatus(StatusCode.ERROR);
            } finally {
                child1Span.end();
            }

            // Child operation 2
            var child2Span =
                    tracer.spanBuilder("nested.child2").setSpanKind(SpanKind.INTERNAL).startSpan();

            try (Scope child2Scope = child2Span.makeCurrent()) {
                Thread.sleep(75);
                child2Span.setAttribute("child2.result", "success");
                BraintrustTracing.SpanUtils.addScore("performance", 0.88);
                child2Span.setStatus(StatusCode.OK);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                child2Span.recordException(e);
                child2Span.setStatus(StatusCode.ERROR);
            } finally {
                child2Span.end();
            }

            System.out.println("Nested operations completed");
            rootSpan.setStatus(StatusCode.OK);

        } finally {
            rootSpan.end();
        }
    }

    private static void performSimulatedLLMCall(io.opentelemetry.api.trace.Tracer tracer) {
        var span =
                tracer.spanBuilder("llm.chat.completion")
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute("llm.model", "gpt-4o")
                        .setAttribute("llm.temperature", 0.7)
                        .setAttribute("llm.max_tokens", 100)
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Simulate LLM processing time
            Thread.sleep(200);

            // Simulate token usage
            var promptTokens = 45L;
            var completionTokens = 78L;
            var totalTokens = promptTokens + completionTokens;

            span.setAttribute(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS, promptTokens);
            span.setAttribute(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS, completionTokens);
            span.setAttribute(BraintrustSpanProcessor.USAGE_TOTAL_TOKENS, totalTokens);

            // Simulate cost calculation
            var cost = (promptTokens * 0.0005 + completionTokens * 0.0015) / 1000;
            span.setAttribute(BraintrustSpanProcessor.USAGE_COST, cost);

            // Add quality scores
            BraintrustTracing.SpanUtils.addScore("relevance", 0.92);
            BraintrustTracing.SpanUtils.addScore("coherence", 0.89);

            System.out.println("Simulated LLM response generated");
            System.out.println(
                    "Tokens used: "
                            + totalTokens
                            + " (cost: $"
                            + String.format("%.4f", cost)
                            + ")");

            span.setStatus(StatusCode.OK);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
        } finally {
            span.end();
        }
    }

    private static void demonstrateLogger() {
        // Enable debug logging
        BraintrustLogger.setDebugEnabled(true);

        // Various log levels
        BraintrustLogger.debug("Debug message: processing_id={}", UUID.randomUUID());
        BraintrustLogger.info("Info message: status={}", "running");
        BraintrustLogger.warn("Warning message: retry_count={}", 3);

        // Log with multiple parameters
        BraintrustLogger.info(
                "Processing complete: items={} duration_ms={} success_rate={}", 100, 1234, 0.95);

        // Error logging
        try {
            throw new IllegalArgumentException("Invalid parameter");
        } catch (Exception e) {
            BraintrustLogger.error("Error occurred during processing", e);
        }

        System.out.println("Logger demonstration completed");
    }

    private static double calculateSomething() {
        // Simulate some calculation
        return ThreadLocalRandom.current().nextDouble() * 100;
    }
}
