package dev.braintrust.trace;

import dev.braintrust.claude.config.BraintrustConfig;
import dev.braintrust.claude.log.BraintrustLogger;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Custom span exporter for Braintrust that adds the x-bt-parent header dynamically based on span
 * attributes.
 */
public class BraintrustSpanExporter implements SpanExporter {

    private final BraintrustConfig config;
    private final String tracesEndpoint;
    private final Map<String, OtlpHttpSpanExporter> exporterCache = new ConcurrentHashMap<>();

    public BraintrustSpanExporter(BraintrustConfig config) {
        this.config = config;
        this.tracesEndpoint = config.apiUrl() + "/otel/v1/traces";
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (spans.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }

        // Group spans by their parent (project or experiment)
        var spansByParent = spans.stream().collect(Collectors.groupingBy(this::getParentFromSpan));

        // Export each group with the appropriate x-bt-parent header
        var results =
                spansByParent.entrySet().stream()
                        .map(entry -> exportWithParent(entry.getKey(), entry.getValue()))
                        .toList();

        // Combine all results
        var combined = CompletableResultCode.ofAll(results);

        return combined;
    }

    private String getParentFromSpan(SpanData span) {
        // Check for the braintrust.parent attribute
        var parent = span.getAttributes().get(BraintrustSpanProcessor.PARENT);
        if (parent != null) {
            return parent;
        }

        // Check legacy attributes for backward compatibility
        var experimentId = span.getAttributes().get(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID);
        if (experimentId != null) {
            return "experiment_id:" + experimentId;
        }

        var projectId = span.getAttributes().get(BraintrustSpanProcessor.PARENT_PROJECT_ID);
        if (projectId != null) {
            return "project_id:" + projectId;
        }

        // Use default project ID if configured
        return config.defaultProjectId().map(id -> "project_id:" + id).orElse("");
    }

    private CompletableResultCode exportWithParent(String parent, List<SpanData> spans) {
        try {
            // Get or create exporter for this parent
            var exporter =
                    exporterCache.computeIfAbsent(
                            parent,
                            p -> {
                                var exporterBuilder =
                                        OtlpHttpSpanExporter.builder()
                                                .setEndpoint(tracesEndpoint)
                                                .addHeader(
                                                        "Authorization",
                                                        "Bearer " + config.apiKey())
                                                .setTimeout(config.requestTimeout());

                                // Add x-bt-parent header if we have a parent
                                if (!p.isEmpty()) {
                                    exporterBuilder.addHeader("x-bt-parent", p);
                                    BraintrustLogger.debug(
                                            "Created exporter with x-bt-parent: {}", p);
                                }

                                return exporterBuilder.build();
                            });

            BraintrustLogger.debug("Exporting {} spans with x-bt-parent: {}", spans.size(), parent);

            // Export the spans
            return exporter.export(spans);
        } catch (Exception e) {
            BraintrustLogger.error("Failed to export spans", e);
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        // Flush all cached exporters
        var results = exporterCache.values().stream().map(OtlpHttpSpanExporter::flush).toList();
        return CompletableResultCode.ofAll(results);
    }

    @Override
    public CompletableResultCode shutdown() {
        // Shutdown all cached exporters
        var results = exporterCache.values().stream().map(OtlpHttpSpanExporter::shutdown).toList();
        exporterCache.clear();
        return CompletableResultCode.ofAll(results);
    }
}
