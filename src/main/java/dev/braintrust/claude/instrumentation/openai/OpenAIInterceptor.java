package dev.braintrust.claude.instrumentation.openai;

import dev.braintrust.claude.log.BraintrustLogger;
import dev.braintrust.claude.trace.BraintrustSpanProcessor;
import dev.braintrust.claude.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.function.Supplier;

/**
 * Interceptor for OpenAI API calls that creates OTEL spans. Uses functional interfaces to avoid
 * hard dependency on OpenAI SDK.
 */
public class OpenAIInterceptor {

    /**
     * Wraps a completion request with tracing.
     *
     * @param operation The operation name (e.g., "chat.completion", "completion")
     * @param request The request object
     * @param requestExtractor Function to extract request details
     * @param responseExtractor Function to extract response details
     * @param executeRequest The actual request execution
     * @return The response
     */
    public static <REQ, RESP> RESP traceCompletion(
            String operation,
            REQ request,
            RequestExtractor<REQ> requestExtractor,
            ResponseExtractor<RESP> responseExtractor,
            Supplier<RESP> executeRequest) {
        // Get tracer lazily to ensure test setup is complete
        var tracer = BraintrustTracing.getTracer();
        var requestDetails = requestExtractor.extract(request);

        BraintrustLogger.debug(
                "OpenAI {} request: model={}, maxTokens={}, temperature={}",
                operation,
                requestDetails.model(),
                requestDetails.maxTokens(),
                requestDetails.temperature());

        var span =
                tracer.spanBuilder("openai." + operation)
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute("openai.model", requestDetails.model())
                        .setAttribute("openai.max_tokens", requestDetails.maxTokens())
                        .setAttribute("openai.temperature", requestDetails.temperature())
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            var response = executeRequest.get();

            // Extract and record usage metrics
            var responseDetails = responseExtractor.extract(response);
            if (responseDetails.usage() != null) {
                BraintrustLogger.debug(
                        "OpenAI {} response: promptTokens={}, completionTokens={}, totalTokens={}",
                        operation,
                        responseDetails.usage().promptTokens(),
                        responseDetails.usage().completionTokens(),
                        responseDetails.usage().totalTokens());

                span.setAttribute(
                        BraintrustSpanProcessor.USAGE_PROMPT_TOKENS,
                        responseDetails.usage().promptTokens());
                span.setAttribute(
                        BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS,
                        responseDetails.usage().completionTokens());
                span.setAttribute(
                        BraintrustSpanProcessor.USAGE_TOTAL_TOKENS,
                        responseDetails.usage().totalTokens());

                // Estimate cost (simplified - should use proper pricing)
                var estimatedCost =
                        estimateCost(
                                requestDetails.model(),
                                responseDetails.usage().promptTokens(),
                                responseDetails.usage().completionTokens());
                span.setAttribute(BraintrustSpanProcessor.USAGE_COST, estimatedCost);
            }

            span.setStatus(StatusCode.OK);
            return response;

        } catch (Exception e) {
            BraintrustLogger.warn("OpenAI {} request failed: {}", operation, e.getMessage(), e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /** Wraps a streaming completion request. */
    public static <REQ, STREAM, RESP> STREAM traceStreamingCompletion(
            String operation,
            REQ request,
            RequestExtractor<REQ> requestExtractor,
            Supplier<STREAM> executeRequest,
            StreamWrapper<STREAM, RESP> streamWrapper) {
        // Get tracer lazily to ensure test setup is complete
        var tracer = BraintrustTracing.getTracer();
        var requestDetails = requestExtractor.extract(request);

        var span =
                tracer.spanBuilder("openai." + operation + ".stream")
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute("openai.model", requestDetails.model())
                        .setAttribute("openai.max_tokens", requestDetails.maxTokens())
                        .setAttribute("openai.temperature", requestDetails.temperature())
                        .setAttribute("openai.stream", true)
                        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            var stream = executeRequest.get();

            // Wrap the stream to capture metrics when it completes
            return streamWrapper.wrap(
                    stream,
                    usage -> {
                        if (usage != null) {
                            span.setAttribute(
                                    BraintrustSpanProcessor.USAGE_PROMPT_TOKENS,
                                    usage.promptTokens());
                            span.setAttribute(
                                    BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS,
                                    usage.completionTokens());
                            span.setAttribute(
                                    BraintrustSpanProcessor.USAGE_TOTAL_TOKENS,
                                    usage.totalTokens());

                            var estimatedCost =
                                    estimateCost(
                                            requestDetails.model(),
                                            usage.promptTokens(),
                                            usage.completionTokens());
                            span.setAttribute(BraintrustSpanProcessor.USAGE_COST, estimatedCost);
                        }
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

    private static double estimateCost(String model, long promptTokens, long completionTokens) {
        // Simplified cost calculation - should use actual pricing
        var costPer1kPrompt = getCostPer1kTokens(model, true);
        var costPer1kCompletion = getCostPer1kTokens(model, false);

        return (promptTokens / 1000.0 * costPer1kPrompt)
                + (completionTokens / 1000.0 * costPer1kCompletion);
    }

    private static double getCostPer1kTokens(String model, boolean isPrompt) {
        // Simplified pricing - should be configurable
        var modelLower = model.toLowerCase();
        if (modelLower.contains("gpt-4")) {
            return isPrompt ? 0.03 : 0.06;
        } else if (modelLower.contains("gpt-3.5")) {
            return isPrompt ? 0.0015 : 0.002;
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
    public interface StreamWrapper<STREAM, RESP> {
        STREAM wrap(
                STREAM stream,
                java.util.function.Consumer<Usage> onComplete,
                java.util.function.Consumer<Exception> onError);
    }

    public record RequestDetails(String model, int maxTokens, double temperature) {}

    public record ResponseDetails(Usage usage) {}

    public record Usage(long promptTokens, long completionTokens, long totalTokens) {}
}
