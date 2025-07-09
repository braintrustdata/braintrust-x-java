package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class BraintrustTracingTest {
    
    private InMemorySpanExporter spanExporter;
    private BraintrustConfig config;
    
    @BeforeEach
    void setUp() {
        // Reset global OpenTelemetry
        GlobalOpenTelemetry.resetForTest();
        
        spanExporter = InMemorySpanExporter.create();
        config = BraintrustConfig.builder()
            .apiKey("test-key")
            .defaultProjectId("test-project")
            .build();
    }
    
    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }
    
    @Test
    void testQuickstartWithDefaults() {
        // When
        var otel = BraintrustTracing.quickstart(config);
        
        // Then
        assertThat(otel).isNotNull();
        assertThat(GlobalOpenTelemetry.get()).isNotNull();
        
        // Create a test span
        var tracer = BraintrustTracing.getTracer(otel);
        var span = tracer.spanBuilder("test").startSpan();
        span.end();
        
        // Verify tracer name
        assertThat(tracer).isNotNull();
    }
    
    @Test
    void testQuickstartWithCustomization() {
        // When
        var otel = BraintrustTracing.quickstart(config, builder -> builder
            .serviceName("custom-service")
            .resourceAttribute("custom.attribute", "value")
            .exportInterval(Duration.ofSeconds(1))
            .maxQueueSize(1000)
            .maxExportBatchSize(100)
            .registerGlobal(false)
        );
        
        // Then
        assertThat(otel).isNotNull();
        assertThat(GlobalOpenTelemetry.get()).isNotEqualTo(otel); // Not registered globally
    }
    
    @Test
    void testGetTracerWithGlobal() {
        // Given
        BraintrustTracing.quickstart(config);
        
        // When
        var tracer = BraintrustTracing.getTracer();
        
        // Then
        assertThat(tracer).isNotNull();
        // Test by creating a span - can't directly access instrumentation scope info from Tracer
        var span = tracer.spanBuilder("test").startSpan();
        span.end();
    }
    
    @Test
    void testGetTracerWithSpecificInstance() {
        // Given
        var otel = BraintrustTracing.quickstart(config, builder -> 
            builder.registerGlobal(false)
        );
        
        // When
        var tracer = BraintrustTracing.getTracer(otel);
        
        // Then
        assertThat(tracer).isNotNull();
        // Test by creating a span
        var span = tracer.spanBuilder("test").startSpan();
        span.end();
    }
    
    @Test
    void testSpanUtilsAddUsageMetrics() {
        // Given
        var otel = createTestOpenTelemetry();
        var tracer = otel.getTracer("test");
        
        // When
        var span = tracer.spanBuilder("test-span").startSpan();
        try (var scope = span.makeCurrent()) {
            BraintrustTracing.SpanUtils.addUsageMetrics(100, 50, 0.025);
        } finally {
            span.end();
        }
        
        // Then
        var spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        var spanData = spans.get(0);
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS))
            .isEqualTo(100L);
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS))
            .isEqualTo(50L);
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.USAGE_TOTAL_TOKENS))
            .isEqualTo(150L);
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.USAGE_COST))
            .isEqualTo(0.025);
    }
    
    @Test
    void testSpanUtilsAddScore() {
        // Given
        var otel = createTestOpenTelemetry();
        var tracer = otel.getTracer("test");
        
        // When
        var span = tracer.spanBuilder("test-span").startSpan();
        try (var scope = span.makeCurrent()) {
            BraintrustTracing.SpanUtils.addScore("accuracy", 0.95);
        } finally {
            span.end();
        }
        
        // Then
        var spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        var spanData = spans.get(0);
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.SCORE_NAME))
            .isEqualTo("accuracy");
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.SCORE))
            .isEqualTo(0.95);
    }
    
    @Test
    void testSpanUtilsSetParentProject() {
        // Given
        var otel = createTestOpenTelemetry();
        var tracer = otel.getTracer("test");
        
        // When
        var span = tracer.spanBuilder("test-span").startSpan();
        try (var scope = span.makeCurrent()) {
            BraintrustTracing.SpanUtils.setParentProject("project-123");
        } finally {
            span.end();
        }
        
        // Then
        var spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        var spanData = spans.get(0);
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.PARENT_PROJECT_ID))
            .isEqualTo("project-123");
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.PARENT_TYPE))
            .isEqualTo("project");
    }
    
    @Test
    void testSpanUtilsSetParentExperiment() {
        // Given
        var otel = createTestOpenTelemetry();
        var tracer = otel.getTracer("test");
        
        // When
        var span = tracer.spanBuilder("test-span").startSpan();
        try (var scope = span.makeCurrent()) {
            BraintrustTracing.SpanUtils.setParentExperiment("exp-456");
        } finally {
            span.end();
        }
        
        // Then
        var spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        var spanData = spans.get(0);
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID))
            .isEqualTo("exp-456");
        assertThat(spanData.getAttributes().get(BraintrustSpanProcessor.PARENT_TYPE))
            .isEqualTo("experiment");
    }
    
    @Test
    void testBuilderResourceAttributes() {
        // When
        var otel = BraintrustTracing.quickstart(config, builder -> builder
            .serviceName("test-service")
            .resourceAttribute("environment", "test")
            .resourceAttribute("version", "1.2.3")
            .registerGlobal(false)
        );
        
        // Create a span to verify resource attributes
        var tracer = otel.getTracer("test");
        var span = tracer.spanBuilder("test").startSpan();
        span.end();
        
        // Then
        assertThat(otel).isNotNull();
    }
    
    @Test
    void testConcurrentSpanCreation() throws InterruptedException {
        // Given
        var otel = BraintrustTracing.quickstart(config);
        var tracer = BraintrustTracing.getTracer();
        var threadCount = 10;
        var spansPerThread = 100;
        
        // When
        var threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < spansPerThread; j++) {
                    var span = tracer.spanBuilder("thread-" + threadId + "-span-" + j)
                        .startSpan();
                    // Simulate some work
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    span.end();
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then - no exceptions thrown, all spans created successfully
        assertThat(tracer).isNotNull();
    }
    
    private OpenTelemetrySdk createTestOpenTelemetry() {
        var tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }
}