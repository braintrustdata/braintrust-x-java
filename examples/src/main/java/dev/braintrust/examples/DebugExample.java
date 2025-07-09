package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.time.Duration;

/**
 * Debug example to troubleshoot HTTP 403 errors.
 */
public class DebugExample {
    
    public static void main(String[] args) {
        // Enable debug logging
        BraintrustLogger.setDebugEnabled(true);
        
        var apiKey = System.getenv("BRAINTRUST_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("BRAINTRUST_API_KEY not set");
            System.exit(1);
        }
        
        System.out.println("=== Debug Information ===");
        System.out.println("API Key: " + apiKey.substring(0, 10) + "..." + apiKey.substring(apiKey.length() - 4));
        System.out.println("API Key Length: " + apiKey.length());
        
        try {
            var config = BraintrustConfig.builder()
                .apiKey(apiKey)
                .debug(true)
                .build();
            
            System.out.println("API URL: " + config.apiUrl());
            System.out.println("OTLP Endpoint: " + config.apiUrl() + "/otlp/v1/traces");
            System.out.println("Debug Mode: " + config.debug());
            
            var openTelemetry = BraintrustTracing.quickstart(config, builder -> builder
                .serviceName("debug-test")
                .exportInterval(Duration.ofSeconds(1))
            );
            
            var tracer = BraintrustTracing.getTracer(openTelemetry);
            
            // Create a minimal span
            System.out.println("\nCreating test span...");
            var span = tracer.spanBuilder("debug.test")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
            
            try (Scope scope = span.makeCurrent()) {
                span.setAttribute("test", true);
                span.setStatus(StatusCode.OK);
            } finally {
                span.end();
            }
            
            System.out.println("Span created, waiting for export...");
            Thread.sleep(3000);
            
            System.out.println("\nIf you see HTTP 403 errors above, possible causes:");
            System.out.println("1. API key format issue (should start with 'sk-'?)");
            System.out.println("2. API key permissions");
            System.out.println("3. Project access permissions");
            System.out.println("4. HTTP vs HTTPS issue");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}