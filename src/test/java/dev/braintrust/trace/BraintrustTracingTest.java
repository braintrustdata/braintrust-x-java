package dev.braintrust.trace;

import static org.junit.jupiter.api.Assertions.*;

import dev.braintrust.config.BraintrustConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BraintrustTracingTest {
    public static Map<String, List<SpanData>> getExportedBraintrustSpans() {
        return BraintrustSpanExporter.SPANS_EXPORTED;
    }

    private final BraintrustConfig config =
            BraintrustConfig.of(
                    "BRAINTRUST_API_KEY", "foobar",
                    "BRAINTRUST_TEST_JAVA_EXPORT_SPANS_IN_MEMORY", "true");

    @BeforeEach
    void beforeEach() {
        GlobalOpenTelemetry.resetForTest();
        getExportedBraintrustSpans().clear();
    }

    @Test
    void globalBTTracing() {
        var sdk = (OpenTelemetrySdk) BraintrustTracing.of(config, true);
        doSimpleOtelTrace(BraintrustTracing.getTracer());
        assertTrue(sdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS).isSuccess());
        assertEquals(1, getExportedBraintrustSpans().size());
        var spanData =
                getExportedBraintrustSpans().get(config.getBraintrustParentValue().orElseThrow());
        assertNotNull(spanData);
        assertEquals(1, spanData.size());
        assertEquals(
                true, spanData.get(0).getAttributes().get(AttributeKey.booleanKey("unit-test")));
    }

    @Test
    void customBTTracing() {
        var tracerBuilder = SdkTracerProvider.builder();
        var loggerBuilder = SdkLoggerProvider.builder();
        var meterBuilder = SdkMeterProvider.builder();
        BraintrustTracing.enable(config, tracerBuilder, loggerBuilder, meterBuilder);
        var sdk =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerBuilder.build())
                        .setLoggerProvider(loggerBuilder.build())
                        .setMeterProvider(meterBuilder.build())
                        .build();
        GlobalOpenTelemetry.set(sdk);
        doSimpleOtelTrace(sdk.getTracer("some-instrumentation"));
        assertTrue(sdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS).isSuccess());
        assertEquals(1, getExportedBraintrustSpans().size());
        var spanData =
                getExportedBraintrustSpans().get(config.getBraintrustParentValue().orElseThrow());
        assertNotNull(spanData);
        assertEquals(1, spanData.size());
        assertEquals(
                true, spanData.get(0).getAttributes().get(AttributeKey.booleanKey("unit-test")));
    }

    private void doSimpleOtelTrace(Tracer tracer) {
        // use tracer to create a simple trace with a root span and a child span
        var span = tracer.spanBuilder("unit-test-root").startSpan();
        try (var ignored = span.makeCurrent()) {
            span.setAttribute("unit-test", true);
        } finally {
            span.end();
        }
    }
}
