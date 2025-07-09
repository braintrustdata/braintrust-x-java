package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Quick start example showing basic Braintrust tracing setup.
 * Demonstrates idiomatic Java usage with try-with-resources and functional style.
 */
public class QuickStartExample {
    
    public static void main(String[] args) throws Exception {
        // Initialize Braintrust with environment configuration
        var openTelemetry = BraintrustTracing.quickstart();
        
        // Or with custom configuration using builder pattern
        var customConfig = BraintrustConfig.create(builder -> builder
            .apiKey("your-api-key")
            .defaultProjectId("my-project")
            .enableTraceConsoleLog(true)
            .debug(true)
        );
        
        var tracer = BraintrustTracing.getTracer(openTelemetry);
        
        // Example 1: Simple span creation
        try (var ignored = tracer.spanBuilder("simple-operation")
                .startSpan()
                .makeCurrent()) {
            
            System.out.println("Performing simple operation...");
            Thread.sleep(100);
            
            // Add custom attributes
            Span.current().setAttribute("custom.attribute", "value");
            Span.current().setAttribute("custom.count", 42);
        }
        
        // Example 2: Nested spans with functional style
        processItems(List.of("item1", "item2", "item3"), tracer);
        
        // Example 3: Error handling
        try (var ignored = tracer.spanBuilder("error-example")
                .startSpan()
                .makeCurrent()) {
            
            simulateError();
            
        } catch (Exception e) {
            Span.current().recordException(e);
            System.err.println("Caught expected error: " + e.getMessage());
        }
        
        // Allow time for spans to export
        Thread.sleep(2000);
        System.out.println("Quick start example completed!");
    }
    
    private static void processItems(List<String> items, io.opentelemetry.api.trace.Tracer tracer) {
        var span = tracer.spanBuilder("process-items")
            .setAttribute("item.count", items.size())
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Use Java streams for functional processing
            var results = items.parallelStream()
                .map(item -> processItem(item, tracer))
                .toList();
            
            // Add summary metrics
            var successCount = results.stream().filter(r -> r).count();
            span.setAttribute("process.success_count", successCount);
            span.setAttribute("process.failure_count", items.size() - successCount);
            
        } finally {
            span.end();
        }
    }
    
    private static boolean processItem(String item, io.opentelemetry.api.trace.Tracer tracer) {
        var span = tracer.spanBuilder("process-item")
            .setAttribute("item.name", item)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Simulate processing with random delay
            var random = new Random();
            var duration = Duration.ofMillis(50 + random.nextInt(150));
            Thread.sleep(duration.toMillis());
            
            // Simulate random success/failure
            var success = random.nextDouble() > 0.2;
            span.setAttribute("item.success", success);
            
            if (!success) {
                span.addEvent("Processing failed");
                span.setAttribute("failure.reason", "Random failure for demo");
                span.setAttribute("failure.retry_possible", true);
            }
            
            return success;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            span.recordException(e);
            return false;
        } finally {
            span.end();
        }
    }
    
    private static void simulateError() {
        throw new RuntimeException("Simulated error for demonstration");
    }
}