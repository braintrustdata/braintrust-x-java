package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;

public class SimpleOpenTelemetryExample {
    public static void main(String[] args) throws Exception {
        var braintrustConfig = BraintrustConfig.fromEnvironment();
        var openTelemetry = BraintrustTracing.quickstart(braintrustConfig, true);
        var tracer = BraintrustTracing.getTracer(openTelemetry);

        var span = tracer.spanBuilder("hello-java").startSpan();
        try (var ignored = span.makeCurrent()) {
            System.out.println("Performing simple operation...");
            span.setAttribute("some boolean attribute", true);
            Thread.sleep(100); // Not required. This is just to make the span look interesting
        } finally {
            span.end();
        }
        var url = braintrustConfig.fetchProjectURI() + "/logs?r=%s&s=%s".formatted(span.getSpanContext().getSpanId(), span.getSpanContext().getSpanId());
        System.out.println("\n\n  Example complete! View your data in Braintrust: " + url);
    }
}
