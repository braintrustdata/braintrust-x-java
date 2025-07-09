package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.time.Duration;

/**
 * Test the corrected endpoint path.
 */
public class TestEndpoint {
    
    public static void main(String[] args) {
        System.out.println("Testing Braintrust OTLP endpoint...\n");
        
        // Use a dummy API key for testing
        var config = BraintrustConfig.builder()
            .apiKey("test-key-12345")
            .build();
        
        System.out.println("Configuration:");
        System.out.println("- API URL: " + config.apiUrl());
        System.out.println("- OTLP Endpoint: " + config.apiUrl() + "/otel/v1/traces");
        
        try {
            var openTelemetry = BraintrustTracing.quickstart(config, builder -> builder
                .serviceName("endpoint-test")
                .exportInterval(Duration.ofSeconds(1))
            );
            
            var tracer = BraintrustTracing.getTracer(openTelemetry);
            
            var span = tracer.spanBuilder("test.endpoint")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
            
            try (Scope scope = span.makeCurrent()) {
                span.setAttribute("test", true);
                span.setStatus(StatusCode.OK);
            } finally {
                span.end();
            }
            
            System.out.println("\nSpan created and sent to endpoint");
            Thread.sleep(2000);
            
            System.out.println("\nExpected result:");
            System.out.println("- With invalid key: HTTP 401 Unauthorized");
            System.out.println("- With valid key: HTTP 200 OK");
            System.out.println("\nPrevious error (HTTP 403) suggests wrong endpoint path");
            System.out.println("Corrected from /otlp/v1/traces to /otel/v1/traces");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}