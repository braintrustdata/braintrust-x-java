package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.time.Duration;

/** Minimal test to debug HTTP 400 errors. */
public class MinimalTest {

    public static void main(String[] args) {
        // Enable debug logging
        BraintrustLogger.setDebugEnabled(true);

        var apiKey = System.getenv("BRAINTRUST_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set BRAINTRUST_API_KEY");
            System.exit(1);
        }

        try {
            // Minimal configuration
            var config = BraintrustConfig.builder().apiKey(apiKey).debug(true).build();

            System.out.println("Creating minimal test span...");
            System.out.println("Endpoint: " + config.apiUrl() + "/otel/v1/traces");

            var openTelemetry =
                    BraintrustTracing.quickstart(
                            config,
                            builder ->
                                    builder.serviceName("minimal-test")
                                            .exportInterval(Duration.ofSeconds(1))
                                            .maxExportBatchSize(1) // Send one span at a time
                            );

            var tracer = BraintrustTracing.getTracer(openTelemetry);

            // Create the simplest possible span
            var span = tracer.spanBuilder("test").startSpan();

            try (Scope scope = span.makeCurrent()) {
                // Minimal attributes
                span.setAttribute("test", true);
                span.setStatus(StatusCode.OK);
                Thread.sleep(100); // Give it some duration
            } finally {
                span.end();
            }

            System.out.println("Span created, waiting for export...");
            Thread.sleep(3000);

            System.out.println("\nIf you see HTTP 400 errors, possible causes:");
            System.out.println("1. OTLP protocol version mismatch");
            System.out.println("2. Missing required attributes");
            System.out.println("3. Incorrect protobuf encoding");
            System.out.println("4. Compression issues");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
