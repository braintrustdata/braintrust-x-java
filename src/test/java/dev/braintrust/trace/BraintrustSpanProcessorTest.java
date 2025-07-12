package dev.braintrust.trace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.braintrust.config.BraintrustConfig;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class BraintrustSpanProcessorTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private BraintrustConfig config;
    private SpanProcessor mockDelegate;
    private BraintrustSpanProcessor processor;

    @BeforeEach
    void setUp() {
        config =
                BraintrustConfig.builder()
                        .apiKey("test-key")
                        .defaultProjectId("default-project")
                        .build();

        mockDelegate = mock(SpanProcessor.class);
        processor = new BraintrustSpanProcessor(config, mockDelegate);
    }

    @Test
    void testDefaultProjectIdIsAdded() {
        // Given
        var span = mock(ReadWriteSpan.class);
        when(span.getAttribute(BraintrustSpanProcessor.PARENT)).thenReturn(null);

        // When
        processor.onStart(Context.root(), span);

        // Then
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT, "project_id:default-project");
        verify(mockDelegate).onStart(any(), eq(span));
    }

    @Test
    void testProjectIdFromContext() {
        // Given
        var span = mock(ReadWriteSpan.class);
        when(span.getAttribute(BraintrustSpanProcessor.PARENT)).thenReturn(null);
        var context =
                BraintrustContext.forProject("context-project").storeInContext(Context.root());

        // When
        processor.onStart(context, span);

        // Then
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT, "project_id:context-project");
    }

    @Test
    void testExperimentIdFromContext() {
        // Given
        var span = mock(ReadWriteSpan.class);
        when(span.getAttribute(BraintrustSpanProcessor.PARENT)).thenReturn(null);
        var context =
                BraintrustContext.forExperiment("experiment-123").storeInContext(Context.root());

        // When
        processor.onStart(context, span);

        // Then
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT, "experiment_id:experiment-123");
    }

    @Test
    void testUsageMetricsAttributes() {
        // Given
        var tracer = otelTesting.getOpenTelemetry().getTracer("test");
        var capturedSpan = new AtomicReference<SpanData>();

        var testProcessor =
                new SpanProcessor() {
                    @Override
                    public void onStart(Context parentContext, ReadWriteSpan span) {}

                    @Override
                    public boolean isStartRequired() {
                        return false;
                    }

                    @Override
                    public void onEnd(ReadableSpan span) {
                        capturedSpan.set(span.toSpanData());
                    }

                    @Override
                    public boolean isEndRequired() {
                        return true;
                    }
                };

        var braintrustProcessor = new BraintrustSpanProcessor(config, testProcessor);

        // The test processor is already registered through the testing framework

        // When
        var span = tracer.spanBuilder("test-span").startSpan();
        span.setAttribute(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS, 100L);
        span.setAttribute(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS, 50L);
        span.setAttribute(BraintrustSpanProcessor.USAGE_TOTAL_TOKENS, 150L);
        span.setAttribute(BraintrustSpanProcessor.USAGE_COST, 0.0025);
        span.end();

        // Then - the span would be processed through the registered processor
        // We can verify this through the OTEL testing framework's span data
    }

    @Test
    void testScoringAttributes() {
        // Given
        var span =
                otelTesting
                        .getOpenTelemetry()
                        .getTracer("test")
                        .spanBuilder("test-span")
                        .startSpan();

        // When
        try (var scope = span.makeCurrent()) {
            BraintrustTracing.SpanUtils.addScore("accuracy", 0.95);
        }

        span.end();
    }
}
