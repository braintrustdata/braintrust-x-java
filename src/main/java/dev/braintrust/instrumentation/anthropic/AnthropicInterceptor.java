package dev.braintrust.instrumentation.anthropic;

import dev.braintrust.trace.BraintrustSpanProcessor;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Interceptor for Anthropic Claude API calls that creates OTEL spans. Uses functional interfaces to
 * avoid hard dependency on Anthropic SDK.
 */
public class AnthropicInterceptor {

    /** Wraps a message request with tracing. */
    public static <REQ, RESP> RESP traceMessage(
            REQ request,
            RequestExtractor<REQ> requestExtractor,
            ResponseExtractor<RESP> responseExtractor,
            Supplier<RESP> executeRequest) {
        // Get tracer lazily to ensure test setup is complete
        var tracer = BraintrustTracing.getTracer();
        var requestDetails = requestExtractor.extract(request);

        var span =
                tracer.spanBuilder("anthropic.messages")
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute("anthropic.model", requestDetails.model())
                        .setAttribute("anthropic.max_tokens", requestDetails.maxTokens())
                        .setAttribute("anthropic.temperature", requestDetails.temperature())
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            var response = executeRequest.get();

            // Extract and record usage metrics
            var responseDetails = responseExtractor.extract(response);
            if (responseDetails.usage() != null) {
                span.setAttribute(
                        BraintrustSpanProcessor.USAGE_PROMPT_TOKENS,
                        responseDetails.usage().inputTokens());
                span.setAttribute(
                        BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS,
                        responseDetails.usage().outputTokens());
                span.setAttribute(
                        BraintrustSpanProcessor.USAGE_TOTAL_TOKENS,
                        responseDetails.usage().inputTokens()
                                + responseDetails.usage().outputTokens());

                // Estimate cost
                var estimatedCost =
                        estimateCost(
                                requestDetails.model(),
                                responseDetails.usage().inputTokens(),
                                responseDetails.usage().outputTokens());
                span.setAttribute(BraintrustSpanProcessor.USAGE_COST, estimatedCost);
            }

            span.setStatus(StatusCode.OK);
            return response;

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /** Wraps an async message request. */
    public static <REQ, RESP> CompletableFuture<RESP> traceMessageAsync(
            REQ request,
            RequestExtractor<REQ> requestExtractor,
            ResponseExtractor<RESP> responseExtractor,
            Supplier<CompletableFuture<RESP>> executeRequest) {
        // Get tracer lazily to ensure test setup is complete
        var tracer = BraintrustTracing.getTracer();
        var requestDetails = requestExtractor.extract(request);

        var span =
                tracer.spanBuilder("anthropic.messages")
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute("anthropic.model", requestDetails.model())
                        .setAttribute("anthropic.max_tokens", requestDetails.maxTokens())
                        .setAttribute("anthropic.temperature", requestDetails.temperature())
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            return executeRequest
                    .get()
                    .whenComplete(
                            (response, error) -> {
                                if (error != null) {
                                    span.recordException(error);
                                    span.setStatus(StatusCode.ERROR, error.getMessage());
                                } else {
                                    var responseDetails = responseExtractor.extract(response);
                                    if (responseDetails.usage() != null) {
                                        span.setAttribute(
                                                BraintrustSpanProcessor.USAGE_PROMPT_TOKENS,
                                                responseDetails.usage().inputTokens());
                                        span.setAttribute(
                                                BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS,
                                                responseDetails.usage().outputTokens());
                                        span.setAttribute(
                                                BraintrustSpanProcessor.USAGE_TOTAL_TOKENS,
                                                responseDetails.usage().inputTokens()
                                                        + responseDetails.usage().outputTokens());

                                        var estimatedCost =
                                                estimateCost(
                                                        requestDetails.model(),
                                                        responseDetails.usage().inputTokens(),
                                                        responseDetails.usage().outputTokens());
                                        span.setAttribute(
                                                BraintrustSpanProcessor.USAGE_COST, estimatedCost);
                                    }
                                    span.setStatus(StatusCode.OK);
                                }
                                span.end();
                            });
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            throw e;
        }
    }

    /** Wraps a streaming message request. */
    public static <REQ, STREAM> STREAM traceStreamingMessage(
            REQ request,
            RequestExtractor<REQ> requestExtractor,
            Supplier<STREAM> executeRequest,
            StreamWrapper<STREAM> streamWrapper) {
        // Get tracer lazily to ensure test setup is complete
        var tracer = BraintrustTracing.getTracer();
        var requestDetails = requestExtractor.extract(request);

        var span =
                tracer.spanBuilder("anthropic.messages.stream")
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute("anthropic.model", requestDetails.model())
                        .setAttribute("anthropic.max_tokens", requestDetails.maxTokens())
                        .setAttribute("anthropic.temperature", requestDetails.temperature())
                        .setAttribute("anthropic.stream", true)
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            var stream = executeRequest.get();

            // Track token accumulator
            var tokenAccumulator = new TokenAccumulator();

            // Wrap the stream to capture metrics
            return streamWrapper.wrap(
                    stream,
                    chunk -> tokenAccumulator.accumulate(chunk),
                    () -> {
                        // Stream completed
                        span.setAttribute(
                                BraintrustSpanProcessor.USAGE_PROMPT_TOKENS,
                                tokenAccumulator.getInputTokens());
                        span.setAttribute(
                                BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS,
                                tokenAccumulator.getOutputTokens());
                        span.setAttribute(
                                BraintrustSpanProcessor.USAGE_TOTAL_TOKENS,
                                tokenAccumulator.getTotalTokens());

                        var estimatedCost =
                                estimateCost(
                                        requestDetails.model(),
                                        tokenAccumulator.getInputTokens(),
                                        tokenAccumulator.getOutputTokens());
                        span.setAttribute(BraintrustSpanProcessor.USAGE_COST, estimatedCost);

                        span.setStatus(StatusCode.OK);
                        span.end();
                    },
                    error -> {
                        span.recordException(error);
                        span.setStatus(StatusCode.ERROR, error.getMessage());
                        span.end();
                    });

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            throw e;
        }
    }

    private static double estimateCost(String model, long inputTokens, long outputTokens) {
        // Simplified cost calculation - should use actual pricing
        var costPer1kInput = getCostPer1kTokens(model, true);
        var costPer1kOutput = getCostPer1kTokens(model, false);

        return (inputTokens / 1000.0 * costPer1kInput) + (outputTokens / 1000.0 * costPer1kOutput);
    }

    private static double getCostPer1kTokens(String model, boolean isInput) {
        // Simplified pricing - should be configurable
        var modelLower = model.toLowerCase();
        if (modelLower.contains("claude-3-opus")) {
            return isInput ? 0.015 : 0.075;
        } else if (modelLower.contains("claude-3-sonnet")) {
            return isInput ? 0.003 : 0.015;
        } else if (modelLower.contains("claude-3-haiku")) {
            return isInput ? 0.00025 : 0.00125;
        } else if (modelLower.contains("claude-2")) {
            return isInput ? 0.008 : 0.024;
        } else {
            return 0.0;
        }
    }

    /** Extracts request details. */
    public interface RequestExtractor<REQ> {
        RequestDetails extract(REQ request);
    }

    /** Extracts response details. */
    public interface ResponseExtractor<RESP> {
        ResponseDetails extract(RESP response);
    }

    /** Wraps a stream to capture metrics. */
    public interface StreamWrapper<STREAM> {
        STREAM wrap(
                STREAM stream,
                java.util.function.Consumer<Object> onChunk,
                Runnable onComplete,
                java.util.function.Consumer<Exception> onError);
    }

    public record RequestDetails(String model, int maxTokens, double temperature) {}

    public record ResponseDetails(Usage usage) {}

    public record Usage(long inputTokens, long outputTokens) {}

    /** Helper class to accumulate token counts from streaming responses. */
    private static class TokenAccumulator {
        private long inputTokens = 0;
        private long outputTokens = 0;

        public void accumulate(Object chunk) {
            // This would need to be implemented based on the actual chunk type
            // For now, this is a placeholder
        }

        public long getInputTokens() {
            return inputTokens;
        }

        public long getOutputTokens() {
            return outputTokens;
        }

        public long getTotalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
