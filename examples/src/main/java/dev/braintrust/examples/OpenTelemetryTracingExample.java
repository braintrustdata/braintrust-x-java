package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;

/**
 * Basic OTel tracing example
 */
public class OpenTelemetryTracingExample {
    public static void main(String[] args) throws Exception {
        var config = BraintrustConfig.builder().build();
        var openTelemetry = BraintrustTracing.quickstart(config);
        var tracer = BraintrustTracing.getTracer(openTelemetry);

        var span = tracer.spanBuilder("hello-java").startSpan();
        try (var ignored = span.makeCurrent()) {
            System.out.println("Performing simple operation...");
            span.setAttribute("is-java-span", true);
            Thread.sleep(100);
        } finally {
            span.end();
        }
        System.out.println("example completed!");
    }
}
