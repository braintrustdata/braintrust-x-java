package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustSpanProcessor;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.time.Duration;
import java.util.Collection;

/**
 * Debug with console output to see what spans are being sent.
 */
public class DebugWithConsole {
    
    public static void main(String[] args) {
        var apiKey = System.getenv("BRAINTRUST_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "test-key"; // Use dummy key to see span structure
        }
        
        try {
            var config = BraintrustConfig.builder()
                .apiKey(apiKey)
                .build();
            
            // Create a console exporter to see what's being sent
            var consoleExporter = new SpanExporter() {
                @Override
                public CompletableResultCode export(Collection<SpanData> spans) {
                    spans.forEach(span -> {
                        System.out.println("\n=== SPAN EXPORT ===");
                        System.out.println("Name: " + span.getName());
                        System.out.println("TraceId: " + span.getTraceId());
                        System.out.println("SpanId: " + span.getSpanId());
                        System.out.println("Attributes: " + span.getAttributes());
                        System.out.println("==================\n");
                    });
                    return CompletableResultCode.ofSuccess();
                }
                
                @Override
                public CompletableResultCode flush() {
                    return CompletableResultCode.ofSuccess();
                }
                
                @Override
                public CompletableResultCode shutdown() {
                    return CompletableResultCode.ofSuccess();
                }
            };
            
            // Create resource
            var resource = Resource.getDefault().toBuilder()
                .put("service.name", "debug-console")
                .build();
            
            // Create tracer provider with console output
            var tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(new BraintrustSpanProcessor(config, 
                    SimpleSpanProcessor.create(consoleExporter)))
                .build();
            
            var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
            
            var tracer = BraintrustTracing.getTracer(openTelemetry);
            
            System.out.println("Creating test span with Braintrust attributes...\n");
            
            // Test 1: Basic span
            var span1 = tracer.spanBuilder("test.basic")
                .startSpan();
            
            try (Scope scope = span1.makeCurrent()) {
                span1.setAttribute("test", true);
                span1.setStatus(StatusCode.OK);
            } finally {
                span1.end();
            }
            
            Thread.sleep(100);
            
            // Test 2: Span with explicit project
            var span2 = tracer.spanBuilder("test.with_project")
                .startSpan();
            
            try (Scope scope = span2.makeCurrent()) {
                BraintrustTracing.SpanUtils.setParentProject("my-test-project");
                span2.setAttribute("has_project", true);
                span2.setStatus(StatusCode.OK);
            } finally {
                span2.end();
            }
            
            Thread.sleep(100);
            
            // Test 3: Span with usage metrics
            var span3 = tracer.spanBuilder("test.with_usage")
                .startSpan();
            
            try (Scope scope = span3.makeCurrent()) {
                BraintrustTracing.SpanUtils.setParentProject("my-test-project");
                BraintrustTracing.SpanUtils.addUsageMetrics(100, 50, 0.001);
                span3.setAttribute("model", "gpt-4");
                span3.setStatus(StatusCode.OK);
            } finally {
                span3.end();
            }
            
            System.out.println("\nCheck the console output above to see:");
            System.out.println("1. What attributes are being added");
            System.out.println("2. If project IDs are being set correctly");
            System.out.println("3. The full span structure being sent");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}