package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BraintrustTracingTest {
    private final BraintrustConfig config = BraintrustConfig.of("BRAINTRUST_API_KEY", "foobar");

    @BeforeEach
    void stuff() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void globalBTTracing() {
        var tracing = BraintrustTracing.of(config, true);
        // doSimpleOtelTrace with custom tracer
        // force otel flush
        // assert:
        // - BT span exporter sees spans
    }

    @Test
    void customBTTracing() {
        // hook up a custom otel with in-memory exporters
        // doSimpleOtelTrace with custom tracer
        // force otel flush
        // assert:
        // - in memory exporters receive
    }

    private void doSimpleOtelTrace(Tracer tracer) {
        // use tracer to create a simple trace with a root span and a child span
    }
}
