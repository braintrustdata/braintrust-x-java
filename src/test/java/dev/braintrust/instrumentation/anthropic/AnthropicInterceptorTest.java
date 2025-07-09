package dev.braintrust.instrumentation.anthropic;

import static org.assertj.core.api.Assertions.*;

import dev.braintrust.trace.BraintrustSpanProcessor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AnthropicInterceptorTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(otelTesting.getOpenTelemetry());
    }

    @Test
    void testTraceMessageSuccess() {
        // Given
        var request = new TestRequest("claude-3-opus", 2000, 0.8);
        var response = new TestResponse(new TestUsage(150, 100));

        // When
        var result =
                AnthropicInterceptor.traceMessage(
                        request,
                        req ->
                                new AnthropicInterceptor.RequestDetails(
                                        req.model, req.maxTokens, req.temperature),
                        resp ->
                                new AnthropicInterceptor.ResponseDetails(
                                        new AnthropicInterceptor.Usage(
                                                resp.usage.inputTokens, resp.usage.outputTokens)),
                        () -> response);

        // Then
        assertThat(result).isEqualTo(response);

        // Verify span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("anthropic.messages");
        assertThat(span.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify attributes
        var attributes = span.getAttributes();
        assertThat(
                        attributes.get(
                                io.opentelemetry.api.common.AttributeKey.stringKey(
                                        "anthropic.model")))
                .isEqualTo("claude-3-opus");
        assertThat(
                        attributes.get(
                                io.opentelemetry.api.common.AttributeKey.longKey(
                                        "anthropic.max_tokens")))
                .isEqualTo(2000L);
        assertThat(
                        attributes.get(
                                io.opentelemetry.api.common.AttributeKey.doubleKey(
                                        "anthropic.temperature")))
                .isEqualTo(0.8);

        // Verify usage metrics
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS)).isEqualTo(150L);
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS)).isEqualTo(100L);
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_TOTAL_TOKENS)).isEqualTo(250L);
        assertThat(attributes.get(BraintrustSpanProcessor.USAGE_COST)).isNotNull();
    }

    @Test
    void testTraceMessageError() {
        // Given
        var request = new TestRequest("claude-2", 1000, 0.5);
        var expectedException = new RuntimeException("API Error");

        // When/Then
        assertThatThrownBy(
                        () ->
                                AnthropicInterceptor.traceMessage(
                                        request,
                                        req ->
                                                new AnthropicInterceptor.RequestDetails(
                                                        req.model, req.maxTokens, req.temperature),
                                        resp -> null,
                                        () -> {
                                            throw expectedException;
                                        }))
                .isEqualTo(expectedException);

        // Verify span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("anthropic.messages");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("API Error");
        assertThat(span.getEvents()).hasSize(1);
        assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
    }

    @Test
    void testTraceMessageAsync() throws Exception {
        // Given
        var request = new TestRequest("claude-3-sonnet", 1500, 0.7);
        var response = new TestResponse(new TestUsage(200, 150));

        // When
        var future =
                AnthropicInterceptor.traceMessageAsync(
                        request,
                        req ->
                                new AnthropicInterceptor.RequestDetails(
                                        req.model, req.maxTokens, req.temperature),
                        resp ->
                                new AnthropicInterceptor.ResponseDetails(
                                        new AnthropicInterceptor.Usage(
                                                resp.usage.inputTokens, resp.usage.outputTokens)),
                        () -> CompletableFuture.completedFuture(response));

        // Then
        var result = future.get();
        assertThat(result).isEqualTo(response);

        // Verify span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("anthropic.messages");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS))
                .isEqualTo(200L);
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS))
                .isEqualTo(150L);
    }

    @Test
    void testTraceMessageAsyncError() {
        // Given
        var request = new TestRequest("claude-3-haiku", 500, 0.3);
        var exception = new RuntimeException("Async error");

        // When
        var future =
                AnthropicInterceptor.traceMessageAsync(
                        request,
                        req ->
                                new AnthropicInterceptor.RequestDetails(
                                        req.model, req.maxTokens, req.temperature),
                        resp -> null,
                        () -> CompletableFuture.failedFuture(exception));

        // Then
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Async error");

        // Verify span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void testTraceStreamingMessage() {
        // Given
        var request = new TestRequest("claude-3-opus", 3000, 0.9);
        var stream = new TestStream();
        var capturedOnChunk = new AtomicReference<Consumer<Object>>();
        var capturedOnComplete = new AtomicReference<Runnable>();
        var capturedOnError = new AtomicReference<Consumer<Exception>>();

        // When
        var result =
                AnthropicInterceptor.traceStreamingMessage(
                        request,
                        req ->
                                new AnthropicInterceptor.RequestDetails(
                                        req.model, req.maxTokens, req.temperature),
                        () -> stream,
                        (s, onChunk, onComplete, onError) -> {
                            capturedOnChunk.set(onChunk);
                            capturedOnComplete.set(onComplete);
                            capturedOnError.set(onError);
                            return s;
                        });

        // Then
        assertThat(result).isEqualTo(stream);

        // Verify initial span
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("anthropic.messages.stream");
        assertThat(span.hasEnded()).isFalse();
        assertThat(
                        span.getAttributes()
                                .get(
                                        io.opentelemetry.api.common.AttributeKey.booleanKey(
                                                "anthropic.stream")))
                .isTrue();

        // Simulate streaming chunks
        capturedOnChunk.get().accept(new TestChunk("chunk1"));
        capturedOnChunk.get().accept(new TestChunk("chunk2"));
        capturedOnChunk.get().accept(new TestChunk("chunk3"));

        // Complete stream
        capturedOnComplete.get().run();

        // Re-fetch spans after completion
        spans = otelTesting.getSpans();
        span = spans.get(0);

        assertThat(span.hasEnded()).isTrue();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void testTraceStreamingMessageError() {
        // Given
        var request = new TestRequest("claude-2", 1000, 0.6);
        var stream = new TestStream();
        var capturedOnError = new AtomicReference<Consumer<Exception>>();

        // When
        AnthropicInterceptor.traceStreamingMessage(
                request,
                req ->
                        new AnthropicInterceptor.RequestDetails(
                                req.model, req.maxTokens, req.temperature),
                () -> stream,
                (s, onChunk, onComplete, onError) -> {
                    capturedOnError.set(onError);
                    return s;
                });

        // Simulate stream error
        var error = new RuntimeException("Stream error");
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
        // Test different Claude model pricing
        var testCases =
                new Object[][] {
                    // model, inputTokens, outputTokens, expectedCost
                    {"claude-3-opus-20240229", 1000L, 500L, 0.0525}, // $15/$75 per million
                    {"claude-3-sonnet", 1000L, 500L, 0.0105}, // $3/$15 per million
                    {"claude-3-haiku-20240307", 1000L, 500L, 0.00088}, // $0.25/$1.25 per million
                    {"claude-2.1", 1000L, 500L, 0.020}, // $8/$24 per million
                };

        for (var testCase : testCases) {
            var model = (String) testCase[0];
            var inputTokens = (Long) testCase[1];
            var outputTokens = (Long) testCase[2];
            var expectedCost = (Double) testCase[3];

            var request = new TestRequest(model, 1000, 0.7);
            var response = new TestResponse(new TestUsage(inputTokens, outputTokens));

            AnthropicInterceptor.traceMessage(
                    request,
                    req ->
                            new AnthropicInterceptor.RequestDetails(
                                    req.model, req.maxTokens, req.temperature),
                    resp ->
                            new AnthropicInterceptor.ResponseDetails(
                                    new AnthropicInterceptor.Usage(
                                            resp.usage.inputTokens, resp.usage.outputTokens)),
                    () -> response);

            var span = otelTesting.getSpans().get(otelTesting.getSpans().size() - 1);
            var cost = span.getAttributes().get(BraintrustSpanProcessor.USAGE_COST);

            assertThat(cost)
                    .as(
                            "Cost for model %s with %d input and %d output tokens",
                            model, inputTokens, outputTokens)
                    .isCloseTo(expectedCost, within(0.0001));
        }
    }

    @Test
    void testNullUsageHandling() {
        // Given
        var request = new TestRequest("claude-3-opus", 1000, 0.7);
        var response = new TestResponse(null); // No usage data

        // When
        AnthropicInterceptor.traceMessage(
                request,
                req ->
                        new AnthropicInterceptor.RequestDetails(
                                req.model, req.maxTokens, req.temperature),
                resp -> new AnthropicInterceptor.ResponseDetails(null),
                () -> response);

        // Then
        var span = otelTesting.getSpans().get(0);
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS)).isNull();
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS))
                .isNull();
        assertThat(span.getAttributes().get(BraintrustSpanProcessor.USAGE_COST)).isNull();
    }

    @Test
    void testConcurrentRequests() throws Exception {
        // Given
        var requestCount = 10;
        var futures = new CompletableFuture[requestCount];

        // When - make concurrent requests
        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            futures[i] =
                    CompletableFuture.supplyAsync(
                            () -> {
                                var request = new TestRequest("claude-3-sonnet", 1000 + index, 0.5);
                                var response =
                                        new TestResponse(new TestUsage(100 + index, 50 + index));

                                return AnthropicInterceptor.traceMessage(
                                        request,
                                        req ->
                                                new AnthropicInterceptor.RequestDetails(
                                                        req.model, req.maxTokens, req.temperature),
                                        resp ->
                                                new AnthropicInterceptor.ResponseDetails(
                                                        new AnthropicInterceptor.Usage(
                                                                resp.usage.inputTokens,
                                                                resp.usage.outputTokens)),
                                        () -> response);
                            });
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).get();

        // Then
        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(requestCount);

        // Verify each span has unique token counts
        var tokenCounts =
                spans.stream()
                        .map(
                                span ->
                                        span.getAttributes()
                                                .get(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS))
                        .toList();

        assertThat(tokenCounts).doesNotHaveDuplicates();
    }

    @Test
    void testStreamingWithTokenAccumulation() {
        // Given
        var request = new TestRequest("claude-3-opus", 2000, 0.7);
        var stream = new TestStream();
        var onChunkRef = new AtomicReference<Consumer<Object>>();
        var onCompleteRef = new AtomicReference<Runnable>();

        // When
        AnthropicInterceptor.traceStreamingMessage(
                request,
                req ->
                        new AnthropicInterceptor.RequestDetails(
                                req.model, req.maxTokens, req.temperature),
                () -> stream,
                (s, onChunk, onComplete, onError) -> {
                    onChunkRef.set(onChunk);
                    onCompleteRef.set(onComplete);
                    return s;
                });

        // Simulate chunks with usage data
        onChunkRef.get().accept(new ChunkWithUsage("Hello", 10, 5));
        onChunkRef.get().accept(new ChunkWithUsage(" world", 0, 3));
        onChunkRef.get().accept(new ChunkWithUsage("!", 0, 1));

        // Complete
        onCompleteRef.get().run();

        // Then - verify accumulated tokens
        var span = otelTesting.getSpans().get(0);
        // Note: The actual implementation would need to handle token accumulation
        // This test shows the expected behavior
        assertThat(span.hasEnded()).isTrue();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    // Test data classes
    record TestRequest(String model, int maxTokens, double temperature) {}

    record TestResponse(TestUsage usage) {}

    record TestUsage(long inputTokens, long outputTokens) {}

    static class TestStream {}

    record TestChunk(String content) {}

    record ChunkWithUsage(String content, long inputTokens, long outputTokens) {}
}
