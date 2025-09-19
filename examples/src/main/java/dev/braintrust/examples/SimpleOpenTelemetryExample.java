package dev.braintrust.examples;

import dev.braintrust.trace.BraintrustTracing;

public class SimpleOpenTelemetryExample {
    public static void main(String[] args) throws Exception {
        var openTelemetry = BraintrustTracing.quickstart();
        var tracer = BraintrustTracing.getTracer(openTelemetry);

        var span = tracer.spanBuilder("hello-java").startSpan();
        try (var ignored = span.makeCurrent()) {
            System.out.println("Performing simple operation...");
            span.setAttribute("some boolean attribute", true);
            Thread.sleep(100); // Not required. This is just to make the span look interesting
        } finally {
            span.end();
        }
        System.out.println("simple example completed!");
    }
}
