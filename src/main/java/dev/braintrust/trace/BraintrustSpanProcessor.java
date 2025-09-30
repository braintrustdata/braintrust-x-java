package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import dev.braintrust.spec.SdkSpec;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Custom span processor that enriches spans with Braintrust-specific attributes. Supports parent
 * assignment to projects or experiments.
 */
class BraintrustSpanProcessor implements SpanProcessor {

    // Braintrust-specific attributes
    public static final AttributeKey<String> PARENT =
            AttributeKey.stringKey(SdkSpec.Attributes.PARENT);

    // Usage metrics
    public static final AttributeKey<Long> USAGE_PROMPT_TOKENS =
            AttributeKey.longKey("braintrust.usage.prompt_tokens");
    public static final AttributeKey<Long> USAGE_COMPLETION_TOKENS =
            AttributeKey.longKey("braintrust.usage.completion_tokens");
    public static final AttributeKey<Long> USAGE_TOTAL_TOKENS =
            AttributeKey.longKey("braintrust.usage.total_tokens");
    public static final AttributeKey<Double> USAGE_COST =
            AttributeKey.doubleKey("braintrust.usage.cost");

    // Scoring
    public static final AttributeKey<Double> SCORE = AttributeKey.doubleKey("braintrust.score");
    public static final AttributeKey<String> SCORE_NAME =
            AttributeKey.stringKey("braintrust.score.name");

    private final BraintrustConfig config;
    private final SpanProcessor delegate;
    private final ConcurrentMap<String, ParentContext> parentContexts = new ConcurrentHashMap<>();

    public BraintrustSpanProcessor(BraintrustConfig config, SpanProcessor delegate) {
        this.config = config;
        this.delegate = delegate;
    }

    @Override
    public void onStart(@NotNull Context parentContext, ReadWriteSpan span) {
        BraintrustLogger.debug("OnStart: span={}, parent={}", span.getName(), parentContext);

        // Check if span already has a parent attribute
        if (span.getAttribute(PARENT) == null) {
            // Check if parent context has Braintrust attributes first
            var btContext = BraintrustContext.fromContext(parentContext);
            if (btContext == null) {
                // Get parent from the config if otel doesn't have it
                config.getBraintrustParentValue()
                        .ifPresent(
                                parentValue -> {
                                    span.setAttribute(PARENT, parentValue);
                                    BraintrustLogger.debug(
                                            "OnStart: set parent {} for span {}",
                                            parentValue,
                                            span.getName());
                                });
            } else {
                btContext
                        .projectId()
                        .ifPresent(
                                id -> {
                                    span.setAttribute(PARENT, "project_id:" + id);
                                    BraintrustLogger.debug(
                                            "OnStart: set parent project {} from context", id);
                                });
                btContext
                        .experimentId()
                        .ifPresent(
                                id -> {
                                    span.setAttribute(PARENT, "experiment_id:" + id);
                                    BraintrustLogger.debug(
                                            "OnStart: set parent experiment {} from context", id);
                                });
            }
        }

        delegate.onStart(parentContext, span);
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        if (config.debug()) {
            logSpanDetails(span);
        }
        delegate.onEnd(span);
    }

    @Override
    public boolean isEndRequired() {
        return delegate.isEndRequired();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return delegate.forceFlush();
    }

    /** Sets the parent context for a specific trace ID. */
    public void setParentContext(String traceId, ParentContext context) {
        parentContexts.put(traceId, context);
    }

    /** Gets the parent context for a specific trace ID. */
    public Optional<ParentContext> getParentContext(String traceId) {
        return Optional.ofNullable(parentContexts.get(traceId));
    }

    private void logSpanDetails(ReadableSpan span) {
        var spanData = span.toSpanData();
        BraintrustLogger.debug(
                "Span completed: name={}, traceId={}, spanId={}, duration={}ms, attributes={},"
                        + " events={}",
                spanData.getName(),
                spanData.getTraceId(),
                spanData.getSpanId(),
                (spanData.getEndEpochNanos() - spanData.getStartEpochNanos()) / 1_000_000,
                spanData.getAttributes(),
                spanData.getEvents());
    }

    /** Parent context for spans (project or experiment). */
    public record ParentContext(
            @Nullable String projectId, @Nullable String experimentId, ParentType type) {
        public enum ParentType {
            PROJECT,
            EXPERIMENT
        }

        public static ParentContext project(String projectId) {
            return new ParentContext(projectId, null, ParentType.PROJECT);
        }

        public static ParentContext experiment(String experimentId) {
            return new ParentContext(null, experimentId, ParentType.EXPERIMENT);
        }
    }

    /** Builder for creating span processors with various configurations. */
    public static final class Builder {
        private final BraintrustConfig config;
        private SpanExporter exporter;
        private boolean enableConsoleLog;

        public Builder(BraintrustConfig config) {
            this.config = config;
            this.enableConsoleLog = config.enableTraceConsoleLog();
        }

        public Builder withExporter(SpanExporter exporter) {
            this.exporter = exporter;
            return this;
        }

        public Builder enableConsoleLog(boolean enable) {
            this.enableConsoleLog = enable;
            return this;
        }

        public BraintrustSpanProcessor build() {
            SpanProcessor baseProcessor =
                    SpanProcessor.composite(createProcessors().toArray(SpanProcessor[]::new));

            return new BraintrustSpanProcessor(config, baseProcessor);
        }

        private java.util.List<SpanProcessor> createProcessors() {
            var processors = new java.util.ArrayList<SpanProcessor>();

            if (exporter != null) {
                processors.add(BatchSpanProcessor.builder(exporter).build());
            }

            if (enableConsoleLog) {
                processors.add(createConsoleProcessor());
            }

            return processors;
        }

        private SpanProcessor createConsoleProcessor() {
            return new SpanProcessor() {
                @Override
                public void onStart(Context parentContext, ReadWriteSpan span) {}

                @Override
                public boolean isStartRequired() {
                    return false;
                }

                @Override
                public void onEnd(ReadableSpan span) {
                    System.out.println(formatSpan(span.toSpanData()));
                }

                @Override
                public boolean isEndRequired() {
                    return true;
                }

                @Override
                public CompletableResultCode shutdown() {
                    return CompletableResultCode.ofSuccess();
                }

                @Override
                public CompletableResultCode forceFlush() {
                    return CompletableResultCode.ofSuccess();
                }

                private String formatSpan(SpanData span) {
                    return String.format(
                            "[%s] %s (duration: %dms, attributes: %s)",
                            span.getKind(),
                            span.getName(),
                            (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000,
                            span.getAttributes());
                }
            };
        }
    }
}
