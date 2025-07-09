package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Custom span processor that enriches spans with Braintrust-specific attributes.
 * Supports parent assignment to projects or experiments.
 */
public class BraintrustSpanProcessor implements SpanProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BraintrustSpanProcessor.class);
    
    // Braintrust-specific attributes
    public static final AttributeKey<String> PARENT_PROJECT_ID = AttributeKey.stringKey("braintrust.parent.project_id");
    public static final AttributeKey<String> PARENT_EXPERIMENT_ID = AttributeKey.stringKey("braintrust.parent.experiment_id");
    public static final AttributeKey<String> PARENT_TYPE = AttributeKey.stringKey("braintrust.parent.type");
    
    // Usage metrics
    public static final AttributeKey<Long> USAGE_PROMPT_TOKENS = AttributeKey.longKey("braintrust.usage.prompt_tokens");
    public static final AttributeKey<Long> USAGE_COMPLETION_TOKENS = AttributeKey.longKey("braintrust.usage.completion_tokens");
    public static final AttributeKey<Long> USAGE_TOTAL_TOKENS = AttributeKey.longKey("braintrust.usage.total_tokens");
    public static final AttributeKey<Double> USAGE_COST = AttributeKey.doubleKey("braintrust.usage.cost");
    
    // Scoring
    public static final AttributeKey<Double> SCORE = AttributeKey.doubleKey("braintrust.score");
    public static final AttributeKey<String> SCORE_NAME = AttributeKey.stringKey("braintrust.score.name");
    
    private final BraintrustConfig config;
    private final SpanProcessor delegate;
    private final ConcurrentMap<String, ParentContext> parentContexts = new ConcurrentHashMap<>();
    
    public BraintrustSpanProcessor(BraintrustConfig config, SpanProcessor delegate) {
        this.config = config;
        this.delegate = delegate;
    }
    
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // Add default project ID if configured
        config.defaultProjectId().ifPresent(projectId -> {
            if (!span.getAttribute(PARENT_PROJECT_ID) != null && 
                !span.getAttribute(PARENT_EXPERIMENT_ID) != null) {
                span.setAttribute(PARENT_PROJECT_ID, projectId);
                span.setAttribute(PARENT_TYPE, "project");
            }
        });
        
        // Check if parent context has Braintrust attributes
        var btContext = BraintrustContext.fromContext(parentContext);
        if (btContext != null) {
            btContext.projectId().ifPresent(id -> span.setAttribute(PARENT_PROJECT_ID, id));
            btContext.experimentId().ifPresent(id -> span.setAttribute(PARENT_EXPERIMENT_ID, id));
            btContext.parentType().ifPresent(type -> span.setAttribute(PARENT_TYPE, type));
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
    
    /**
     * Sets the parent context for a specific trace ID.
     */
    public void setParentContext(String traceId, ParentContext context) {
        parentContexts.put(traceId, context);
    }
    
    /**
     * Gets the parent context for a specific trace ID.
     */
    public Optional<ParentContext> getParentContext(String traceId) {
        return Optional.ofNullable(parentContexts.get(traceId));
    }
    
    private void logSpanDetails(ReadableSpan span) {
        var spanData = span.toSpanData();
        logger.debug("Span completed: name={}, traceId={}, spanId={}, duration={}ms, attributes={}",
            spanData.getName(),
            spanData.getTraceId(),
            spanData.getSpanId(),
            (spanData.getEndEpochNanos() - spanData.getStartEpochNanos()) / 1_000_000,
            spanData.getAttributes()
        );
    }
    
    /**
     * Parent context for spans (project or experiment).
     */
    public record ParentContext(
        @Nullable String projectId,
        @Nullable String experimentId,
        ParentType type
    ) {
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
    
    /**
     * Builder for creating span processors with various configurations.
     */
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
            SpanProcessor baseProcessor = SpanProcessor.composite(
                createProcessors().toArray(SpanProcessor[]::new)
            );
            
            return new BraintrustSpanProcessor(config, baseProcessor);
        }
        
        private java.util.List<SpanProcessor> createProcessors() {
            var processors = new java.util.ArrayList<SpanProcessor>();
            
            if (exporter != null) {
                processors.add(SpanProcessor.simple(exporter));
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
                        span.getAttributes()
                    );
                }
            };
        }
    }
}