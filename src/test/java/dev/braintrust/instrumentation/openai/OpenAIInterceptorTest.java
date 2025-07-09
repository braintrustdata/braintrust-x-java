package dev.braintrust.instrumentation.openai;

import dev.braintrust.trace.BraintrustSpanProcessor;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class OpenAIInterceptorTest {
    
    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();
    
    @BeforeEach
    void setUp() {
        // Reset and set the test OpenTelemetry instance globally
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
        io.opentelemetry.api.GlobalOpenTelemetry.set(otelTesting.getOpenTelemetry());
    }
    
    @AfterEach
    void tearDown() {
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
    }
    
    @Test
    void testTraceCompletionSuccess() {
        // Given
        var request = new TestRequest("gpt-4", 1000, 0.7);
        var response = new TestResponse(new TestUsage(100, 50, 150));
        
        // When
        var result = OpenAIInterceptor.traceCompletion(
            "chat.completion",
            request,
            req -> new OpenAIInterceptor.RequestDetails(req.model, req.maxTokens, req.temperature),
            resp -> new OpenAIInterceptor.ResponseDetails(
                new OpenAIInterceptor.Usage(
                    resp.usage.promptTokens,
                    resp.usage.completionTokens,
                    resp.usage.totalTokens
                )
            ),
            () -> response
        );
        
        // Then
        assertThat(result).isEqualTo(response);
        
        // Verify span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        
        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("openai.chat.completion");
        assertThat(span.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        
        // Verify attributes
        var attributes = span.getAttributes();
        assertThat(attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("openai.model")))
            .isEqualTo("gpt-4");
        assertThat(attributes.get(io.opentelemetry.api.common.AttributeKey.longKey("openai.max_tokens")))
            .isEqualTo(1000L);
        assertThat(attributes.get(io.opentelemetry.api.common.AttributeKey.doubleKey("openai.temperature")))
            .isEqualTo(0.7);
        
        // Verify usage metrics
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS)).isEqualTo(100L);
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS)).isEqualTo(50L);
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_TOTAL_TOKENS)).isEqualTo(150L);
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_COST)).isNotNull();
    }
    
    @Test
    void testTraceCompletionError() {
        // Given
        var request = new TestRequest("gpt-3.5-turbo", 500, 0.5);
        var expectedException = new RuntimeException("API Error");
        
        // When/Then
        assertThatThrownBy(() ->
            OpenAIInterceptor.traceCompletion(
                "completion",
                request,
                req -> new OpenAIInterceptor.RequestDetails(req.model, req.maxTokens, req.temperature),
                resp -> null,
                () -> { throw expectedException; }
            )
        ).isEqualTo(expectedException);
        
        // Verify span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        
        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("openai.completion");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("API Error");
        assertThat(span.getEvents()).hasSize(1);
        assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
    }
    
    @Test
    void testTraceStreamingCompletion() {
        // Given
        var request = new TestRequest("gpt-4", 2000, 0.9);
        var stream = new TestStream();
        var capturedOnComplete = new AtomicReference<Consumer<OpenAIInterceptor.Usage>>();
        var capturedOnError = new AtomicReference<Consumer<Exception>>();
        
        // When
        var result = OpenAIInterceptor.traceStreamingCompletion(
            "chat.completion",
            request,
            req -> new OpenAIInterceptor.RequestDetails(req.model, req.maxTokens, req.temperature),
            () -> stream,
            (s, onComplete, onError) -> {
                capturedOnComplete.set(onComplete);
                capturedOnError.set(onError);
                return s;
            }
        );
        
        // Then
        assertThat(result).isEqualTo(stream);
        
        // Force flush to ensure spans are available
        otelTesting.getOpenTelemetry().getSdkTracerProvider().forceFlush();
        
        // Verify initial span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        
        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("openai.chat.completion.stream");
        assertThat(span.hasEnded()).isFalse();
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.booleanKey("openai.stream")))
            .isTrue();
        
        // Simulate stream completion
        capturedOnComplete.get().accept(new OpenAIInterceptor.Usage(200, 100, 300));
        
        // Re-fetch spans after completion
        spans = otelTesting.getSpans();
        span = spans.get(0);
        
        assertThat(span.hasEnded()).isTrue();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS)).isEqualTo(200L);
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS)).isEqualTo(100L);
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_TOTAL_TOKENS)).isEqualTo(300L);
    }
    
    @Test
    void testTraceStreamingCompletionError() {
        // Given
        var request = new TestRequest("gpt-3.5-turbo", 1000, 0.8);
        var stream = new TestStream();
        var capturedOnError = new AtomicReference<Consumer<Exception>>();
        
        // When
        OpenAIInterceptor.traceStreamingCompletion(
            "completion",
            request,
            req -> new OpenAIInterceptor.RequestDetails(req.model, req.maxTokens, req.temperature),
            () -> stream,
            (s, onComplete, onError) -> {
                capturedOnError.set(onError);
                return s;
            }
        );
        
        // Simulate stream error
        var error = new IOException("Stream error");
        capturedOnError.get().accept(error);
        
        // Verify span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        
        var span = spans.get(0);
        assertThat(span.hasEnded()).isTrue();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("Stream error");
    }
    
    @Test
    void testCostCalculation() {
        // Test GPT-4 pricing
        var gpt4Request = new TestRequest("gpt-4", 1000, 0.7);
        var gpt4Response = new TestResponse(new TestUsage(1000, 500, 1500));
        
        OpenAIInterceptor.traceCompletion(
            "chat.completion",
            gpt4Request,
            req -> new OpenAIInterceptor.RequestDetails(req.model, req.maxTokens, req.temperature),
            resp -> new OpenAIInterceptor.ResponseDetails(
                new OpenAIInterceptor.Usage(
                    resp.usage.promptTokens,
                    resp.usage.completionTokens,
                    resp.usage.totalTokens
                )
            ),
            () -> gpt4Response
        );
        
        var span = otelTesting.getSpans().get(0);
        var cost = span.getAttributes().get(BraintrustSpanProcessor.USAGE_COST);
        // 1000 prompt tokens * $0.03/1k + 500 completion tokens * $0.06/1k = $0.03 + $0.03 = $0.06
        assertThat(cost).isEqualTo(0.06);
    }
    
    @Test
    void testNullUsageHandling() {
        // Given
        var request = new TestRequest("gpt-4", 1000, 0.7);
        var response = new TestResponse(null); // No usage data
        
        // When
        OpenAIInterceptor.traceCompletion(
            "chat.completion",
            request,
            req -> new OpenAIInterceptor.RequestDetails(req.model, req.maxTokens, req.temperature),
            resp -> new OpenAIInterceptor.ResponseDetails(null),
            () -> response
        );
        
        // Then
        var span = otelTesting.getSpans().get(0);
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS)).isNull();
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS)).isNull();
    }
    
    // Test data classes
    record TestRequest(String model, int maxTokens, double temperature) {}
    record TestResponse(TestUsage usage) {}
    record TestUsage(long promptTokens, long completionTokens, long totalTokens) {}
    static class TestStream {}
    
    static class IOException extends Exception {
        IOException(String message) { super(message); }
    }
}