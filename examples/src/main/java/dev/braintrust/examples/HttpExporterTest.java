package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;

/** Quick test to verify HTTP exporter configuration. */
public class HttpExporterTest {
    public static void main(String[] args) {
        System.out.println("Testing Braintrust HTTP exporter configuration...\n");

        // Test with dummy credentials
        var config =
                BraintrustConfig.builder()
                        .apiKey("test-key")
                        .apiUrl("https://api.braintrust.dev") // Real endpoint
                        .build();

        System.out.println("Configuration:");
        System.out.println("- API URL: " + config.apiUrl());
        System.out.println("- OTLP Endpoint: " + config.apiUrl() + "/otlp/v1/traces");
        System.out.println("- Timeout: " + config.requestTimeout());

        try {
            var openTelemetry =
                    BraintrustTracing.quickstart(
                            config, builder -> builder.serviceName("http-exporter-test"));

            var tracer = BraintrustTracing.getTracer(openTelemetry);

            // Create a simple span
            var span = tracer.spanBuilder("test.span").startSpan();
            span.setAttribute("test", true);
            span.end();

            System.out.println("\nSpan created and exported via HTTP");
            System.out.println("Expected result: HTTP 401/403 error (invalid API key)");

            // Wait for export
            Thread.sleep(2000);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
