package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Custom log exporter for Braintrust that adds the x-bt-parent header dynamically based on log
 * attributes.
 */
public class BraintrustLogExporter implements LogRecordExporter {

    private final BraintrustConfig config;
    private final String logsEndpoint;
    private final Map<String, OtlpHttpLogRecordExporter> exporterCache = new ConcurrentHashMap<>();

    public BraintrustLogExporter(BraintrustConfig config) {
        this.config = config;
        this.logsEndpoint = config.apiUrl() + config.logsPath();
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
        if (logs.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }

        // Group logs by their parent (project or experiment)
        var logsByParent = logs.stream().collect(Collectors.groupingBy(this::getParentFromLog));

        // Export each group with the appropriate x-bt-parent header
        var results =
                logsByParent.entrySet().stream()
                        .map(entry -> exportWithParent(entry.getKey(), entry.getValue()))
                        .toList();

        // Combine all results
        var combined = CompletableResultCode.ofAll(results);

        return combined;
    }

    private String getParentFromLog(LogRecordData log) {
        // Check for the braintrust.parent attribute
        var parent = log.getAttributes().get(BraintrustSpanProcessor.PARENT);
        if (parent != null) {
            return parent;
        }

        // Check legacy attributes for backward compatibility
        var experimentId = log.getAttributes().get(BraintrustSpanProcessor.PARENT_EXPERIMENT_ID);
        if (experimentId != null) {
            return "experiment_id:" + experimentId;
        }

        var projectId = log.getAttributes().get(BraintrustSpanProcessor.PARENT_PROJECT_ID);
        if (projectId != null) {
            return "project_id:" + projectId;
        }

        // Use default project ID if configured
        return config.defaultProjectId().map(id -> "project_id:" + id).orElse("");
    }

    private CompletableResultCode exportWithParent(String parent, List<LogRecordData> logs) {
        try {
            // Get or create exporter for this parent
            var exporter =
                    // FIXME: This will grow unbounded
                    exporterCache.computeIfAbsent(
                            parent,
                            p -> {
                                var exporterBuilder =
                                        OtlpHttpLogRecordExporter.builder()
                                                .setEndpoint(logsEndpoint)
                                                .addHeader(
                                                        "Authorization",
                                                        "Bearer " + config.apiKey())
                                                .setTimeout(config.requestTimeout());

                                // Add x-bt-parent header if we have a parent
                                if (!p.isEmpty()) {
                                    exporterBuilder.addHeader("x-bt-parent", p);
                                    BraintrustLogger.debug(
                                            "Created log exporter with x-bt-parent: {}", p);
                                }

                                return exporterBuilder.build();
                            });

            BraintrustLogger.debug("Exporting {} logs with x-bt-parent: {}", logs.size(), parent);
            // Export the logs
            return exporter.export(logs);
        } catch (Exception e) {
            BraintrustLogger.error("Failed to export logs", e);
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        // Flush all cached exporters
        var results = exporterCache.values().stream().map(OtlpHttpLogRecordExporter::flush).toList();
        return CompletableResultCode.ofAll(results);
    }

    @Override
    public CompletableResultCode shutdown() {
        // Shutdown all cached exporters
        var results = exporterCache.values().stream().map(OtlpHttpLogRecordExporter::shutdown).toList();
        exporterCache.clear();
        return CompletableResultCode.ofAll(results);
    }
}
