package dev.braintrust.trace;

import dev.braintrust.log.BraintrustLogger;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;

// TODO -- is this just for unit testing? Make package-private?
/** Debug wrapper for SpanExporter that logs all spans being exported. */
public class DebugSpanExporter implements SpanExporter {
    private final SpanExporter delegate;
    private final String configuredEndpoint;

    public DebugSpanExporter(SpanExporter delegate) {
        this(delegate, null);
    }

    public DebugSpanExporter(SpanExporter delegate, String configuredEndpoint) {
        this.delegate = delegate;
        this.configuredEndpoint = configuredEndpoint;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        BraintrustLogger.info("braintrust: === EXPORTING {} SPANS ===", spans.size());
        BraintrustLogger.info("braintrust: Exporter type: {}", delegate.getClass().getName());
        if (configuredEndpoint != null) {
            BraintrustLogger.info("braintrust: Configured endpoint: {}", configuredEndpoint);
        }

        // Check if we actually have spans
        if (spans.isEmpty()) {
            BraintrustLogger.warn("braintrust: WARNING: No spans to export!");
            return CompletableResultCode.ofSuccess();
        }

        // Try to get more info about the exporter
        BraintrustLogger.info("Exporter details: {}", delegate.toString());

        for (SpanData span : spans) {
            BraintrustLogger.info(
                    "Span: name={}, traceId={}, spanId={}",
                    span.getName(),
                    span.getTraceId(),
                    span.getSpanId());
            BraintrustLogger.info("  Attributes: {}", span.getAttributes());
            BraintrustLogger.info("  Resource: {}", span.getResource().getAttributes());

            // Check for braintrust.parent attribute
            var parentAttr = span.getAttributes().get(BraintrustSpanProcessor.PARENT);
            if (parentAttr != null) {
                BraintrustLogger.info("  Braintrust Parent: {}", parentAttr);
            } else {
                BraintrustLogger.warn("  WARNING: No braintrust.parent attribute!");
            }
        }

        BraintrustLogger.info("=== END SPAN EXPORT ===");

        // Delegate to actual exporter
        var result = delegate.export(spans);

        result.whenComplete(
                () -> {
                    if (result.isSuccess()) {
                        BraintrustLogger.info("Export successful");
                    } else {
                        BraintrustLogger.error("Export failed!");
                    }
                });

        return result;
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }
}
