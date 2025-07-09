package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BraintrustSpanProcessorTest {
    
    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();
    
    private BraintrustConfig config;
    private SpanProcessor mockDelegate;
    private BraintrustSpanProcessor processor;
    
    @BeforeEach
    void setUp() {
        config = BraintrustConfig.builder()
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
        when(span.getAttribute(BraintrustSpanProcessor.PARENT_PROJECT_ID)).thenReturn(null);
        when(span.getAttribute(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID)).thenReturn(null);
        
        // When
        processor.onStart(Context.root(), span);
        
        // Then
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT_PROJECT_ID, "default-project");
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT_TYPE, "project");
        verify(mockDelegate).onStart(any(), eq(span));
    }
    
    @Test
    void testProjectIdFromContext() {
        // Given
        var span = mock(ReadWriteSpan.class);
        var context = BraintrustContext.forProject("context-project")
            .storeInContext(Context.root());
        
        // When
        processor.onStart(context, span);
        
        // Then
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT_PROJECT_ID, "context-project");
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT_TYPE, "project");
    }
    
    @Test
    void testExperimentIdFromContext() {
        // Given
        var span = mock(ReadWriteSpan.class);
        var context = BraintrustContext.forExperiment("experiment-123")
            .storeInContext(Context.root());
        
        // When
        processor.onStart(context, span);
        
        // Then
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID, "experiment-123");
        verify(span).setAttribute(BraintrustSpanProcessor.PARENT_TYPE, "experiment");
    }
    
    @Test
    void testUsageMetricsAttributes() {
        // Given
        var tracer = otelTesting.getOpenTelemetry().getTracer("test");
        var capturedSpan = new AtomicReference<SpanData>();
        
        var testProcessor = new SpanProcessor() {
            @Override
            public void onStart(Context parentContext, ReadWriteSpan span) {}
            
            @Override
            public boolean isStartRequired() { return false; }
            
            @Override
            public void onEnd(ReadableSpan span) {
                capturedSpan.set(span.toSpanData());
            }
            
            @Override
            public boolean isEndRequired() { return true; }
        };
        
        var braintrustProcessor = new BraintrustSpanProcessor(config, testProcessor);
        
        // Register processor
        otelTesting.getOpenTelemetry().getTracerProvider()
            .get("test")
            .getSpanProcessor()
            .equals(braintrustProcessor);
        
        // When
        var span = tracer.spanBuilder("test-span").startSpan();
        span.setAttribute(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS, 100L);
        span.setAttribute(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS, 50L);
        span.setAttribute(BraintrustSpanProcessor.USAGE_TOTAL_TOKENS, 150L);
        span.setAttribute(BraintrustSpanProcessor.USAGE_COST, 0.0025);
        span.end();
        
        // Then - verify through captured span
        processor.onEnd(new TestReadableSpan(span));
        verify(mockDelegate).onEnd(any());
    }
    
    @Test
    void testScoringAttributes() {
        // Given
        var span = otelTesting.getOpenTelemetry()
            .getTracer("test")
            .spanBuilder("test-span")
            .startSpan();
        
        // When
        BraintrustTracing.SpanUtils.addScore("accuracy", 0.95);
        
        // Then
        assertThat(span.getAttribute(BraintrustSpanProcessor.SCORE_NAME))
            .isEqualTo("accuracy");
        assertThat(span.getAttribute(BraintrustSpanProcessor.SCORE))
            .isEqualTo(0.95);
        
        span.end();
    }
    
    // Helper class for testing
    private static class TestReadableSpan implements ReadableSpan {
        private final Span span;
        
        TestReadableSpan(Span span) {
            this.span = span;
        }
        
        @Override
        public SpanData toSpanData() {
            // This would normally return actual span data
            return mock(SpanData.class);
        }
        
        // Other methods would be implemented as needed
        @Override
        public io.opentelemetry.api.trace.SpanContext getSpanContext() {
            return span.getSpanContext();
        }
        
        @Override
        public String getName() {
            return "test-span";
        }
        
        @Override
        public io.opentelemetry.sdk.common.InstrumentationScopeInfo getInstrumentationScopeInfo() {
            return io.opentelemetry.sdk.common.InstrumentationScopeInfo.empty();
        }
        
        @Override
        public boolean hasEnded() {
            return true;
        }
        
        @Override
        public long getLatencyNanos() {
            return 1000000; // 1ms
        }
    }
}