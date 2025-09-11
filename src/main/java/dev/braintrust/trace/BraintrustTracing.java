package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Main entry point for Braintrust tracing setup. Provides convenient methods to initialize
 * OpenTelemetry with Braintrust configuration.
 */
public final class BraintrustTracing {
    private static final String INSTRUMENTATION_NAME = "braintrust-java";
    private static final String INSTRUMENTATION_VERSION = "0.1.0";

    private BraintrustTracing() {
        // Utility class
    }

    /**
     * Quick start method that sets up OpenTelemetry with Braintrust defaults. This is the simplest
     * way to get started.
     */
    public static OpenTelemetry quickstart() {
        return quickstart(BraintrustConfig.fromEnvironment());
    }

    /** Quick start with custom configuration. */
    public static OpenTelemetry quickstart(BraintrustConfig config) {
        return quickstart(config, builder -> {});
    }

    // TODO -- remove this constructor?
    /** Quick start with custom configuration and additional setup. */
    public static OpenTelemetry quickstart(BraintrustConfig config, Consumer<Builder> customizer) {
        var builder = new Builder(config);
        customizer.accept(builder);
        return builder.build();
    }

    /** Gets a tracer with Braintrust instrumentation scope. */
    public static Tracer getTracer() {
        return GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    /** Gets a tracer from a specific OpenTelemetry instance. */
    public static Tracer getTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    /** Builder for customizing OpenTelemetry setup. */
    public static final class Builder {
        private final BraintrustConfig config;
        private final Map<String, String> resourceAttributes = new HashMap<>();
        private String serviceName = "braintrust-app";
        private Duration exportInterval = Duration.ofSeconds(5);
        private int maxQueueSize = 2048;
        private int maxExportBatchSize = 512;
        private boolean registerGlobal = true;

        public Builder(BraintrustConfig config) {
            this.config = config;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder resourceAttribute(String key, String value) {
            this.resourceAttributes.put(key, value);
            return this;
        }

        public Builder exportInterval(Duration interval) {
            this.exportInterval = interval;
            return this;
        }

        public Builder maxQueueSize(int size) {
            this.maxQueueSize = size;
            return this;
        }

        public Builder maxExportBatchSize(int size) {
            this.maxExportBatchSize = size;
            return this;
        }

        public Builder registerGlobal(boolean register) {
            this.registerGlobal = register;
            return this;
        }

        public OpenTelemetry build() {
            BraintrustLogger.info(
                    "Initializing Braintrust OpenTelemetry with service={}", serviceName);

            // Create OTLP HTTP exporter
            // The Java OTLP HTTP exporter uses the exact endpoint we provide
            var exporterEndpoint = config.apiUrl() + "/otel/v1/traces";

            // Create the custom Braintrust exporter that handles x-bt-parent header
            SpanExporter exporter = new BraintrustSpanExporter(config);

            // Create resource first so BraintrustSpanProcessor can access service.name
            var resourceBuilder =
                    Resource.getDefault().toBuilder()
                            .put(ResourceAttributes.SERVICE_NAME, serviceName)
                            .put(ResourceAttributes.SERVICE_VERSION, INSTRUMENTATION_VERSION);

            resourceAttributes.forEach(resourceBuilder::put);
            var resource = resourceBuilder.build();

            // Create batch processor for efficient export
            var batchProcessor =
                    BatchSpanProcessor.builder(exporter)
                            .setScheduleDelay(exportInterval.toMillis(), TimeUnit.MILLISECONDS)
                            .setMaxQueueSize(maxQueueSize)
                            .setMaxExportBatchSize(maxExportBatchSize)
                            .build();

            // Create Braintrust span processor that wraps the batch processor
            var spanProcessor = new BraintrustSpanProcessor(config, batchProcessor);

            // Create tracer provider
            var tracerProvider =
                    SdkTracerProvider.builder()
                            .setResource(resource)
                            .addSpanProcessor(spanProcessor)
                            .build();

            // Create OpenTelemetry instance
            var openTelemetry =
                    OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

            // Register globally if requested
            if (registerGlobal) {
                GlobalOpenTelemetry.set(openTelemetry);
                BraintrustLogger.debug("Registered OpenTelemetry globally");
            }

            // Register shutdown hook
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        BraintrustLogger.debug("Shutting down tracer provider. Force-Flushing all otel data.");
                                        var result = CompletableResultCode.ofAll(List.of(
                                                openTelemetry.getSdkLoggerProvider().forceFlush().join(10, TimeUnit.SECONDS),
                                                openTelemetry.getSdkMeterProvider().forceFlush().join(10, TimeUnit.SECONDS),
                                                openTelemetry.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS)
                                        ));
                                        BraintrustLogger.debug("tracer shutdown complete. Flush successful? " + result.isSuccess());
                                        tracerProvider.shutdown();
                                    }));

            return openTelemetry;
        }
    }

    /** Utility methods for working with spans. */
    public static final class SpanUtils {
        private SpanUtils() {}

        /** Adds usage metrics to the current span. */
        public static void addUsageMetrics(long promptTokens, long completionTokens, double cost) {
            var span = io.opentelemetry.api.trace.Span.current();
            span.setAttribute(BraintrustSpanProcessor.USAGE_PROMPT_TOKENS, promptTokens);
            span.setAttribute(BraintrustSpanProcessor.USAGE_COMPLETION_TOKENS, completionTokens);
            span.setAttribute(
                    BraintrustSpanProcessor.USAGE_TOTAL_TOKENS, promptTokens + completionTokens);
            span.setAttribute(BraintrustSpanProcessor.USAGE_COST, cost);
        }

        /** Adds a score to the current span. */
        public static void addScore(String name, double score) {
            var span = io.opentelemetry.api.trace.Span.current();
            span.setAttribute(BraintrustSpanProcessor.SCORE_NAME, name);
            span.setAttribute(BraintrustSpanProcessor.SCORE, score);
        }

        /** Sets the parent project for the current span. */
        public static void setParentProject(String projectId) {
            var span = io.opentelemetry.api.trace.Span.current();
            span.setAttribute(BraintrustSpanProcessor.PARENT, "project_id:" + projectId);
        }

        /** Sets the parent experiment for the current span. */
        public static void setParentExperiment(String experimentId) {
            var span = io.opentelemetry.api.trace.Span.current();
            span.setAttribute(BraintrustSpanProcessor.PARENT, "experiment_id:" + experimentId);
        }
    }
}
