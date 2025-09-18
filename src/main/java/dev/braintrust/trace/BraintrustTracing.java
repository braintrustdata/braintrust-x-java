package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Main entry point for Braintrust tracing setup. Provides convenient methods to initialize
 * OpenTelemetry with Braintrust configuration.
 */
public final class BraintrustTracing {
    private static final String INSTRUMENTATION_NAME = "braintrust-java";
    private static final String INSTRUMENTATION_VERSION = "0.0.1";

    private BraintrustTracing() {
        // Utility class
    }

    /**
     * Quick start method that sets up global OpenTelemetry with Braintrust defaults.
     * <br/><br/>
     * If you're looking for more options for configuring Braintrust/OpenTelemetry, consult the `enable` method.
     */
    public static OpenTelemetry quickstart() {
        return quickstart(BraintrustConfig.fromEnvironment(), true);
    }

    /**
     * Quick start method that sets up OpenTelemetry with custom Braintrust and otel settings.
     * <br/><br/>
     * If you're looking for more options for configuring Braintrust and OpenTelemetry, consult the `enable` method.
     */
    public static OpenTelemetry quickstart(@Nonnull BraintrustConfig config, boolean registerGlobal) {
        var builder = new Builder(config);
        var otel = builder.build();
        if (registerGlobal) {
            GlobalOpenTelemetry.set(otel);
            BraintrustLogger.debug("Registered OpenTelemetry globally");
        }
        return otel;
    }

    /**
     * Add braintrust to existing open telemetry builders
     * <br/><br/>
     * This method provides the most options for configuring Braintrust and OpenTelemetry. If you're looking for a more user-friendly setup, consult the `quickstart` methods.
     * <br/><br/>
     * NOTE: if your otel setup does not have any particular builder, pass an instance of the default provider builder. E.g. `SdkMeterProvider.builder()`
     * <br/><br/>
     * NOTE: This method should only be invoked once. Enabling Braintrust multiple times is unsupported and may lead to undesired behavior
     */
    public static void enable(@Nonnull BraintrustConfig config, @Nonnull SdkTracerProviderBuilder tracerProviderBuilder, @Nonnull SdkLoggerProviderBuilder loggerProviderBuilder, @Nonnull SdkMeterProviderBuilder meterProviderBuilder) {
        throw new RuntimeException("TODO");
    }

    /** Gets a tracer with Braintrust instrumentation scope. */
    public static Tracer getTracer() {
        return getTracer(GlobalOpenTelemetry.get());
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
        private boolean registerGlobal = true; // FIXME should be false by default and probably not here

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

        public OpenTelemetry build(SdkTracerProviderBuilder traceBuilder, SdkLoggerProviderBuilder logBuilder, SdkMeterProviderBuilder meterBuilder) {
            throw new RuntimeException("TODO");
        }

        public OpenTelemetry build() {
            BraintrustLogger.info(
                    "Initializing Braintrust OpenTelemetry with service={}", serviceName);

            // Create resource first so BraintrustSpanProcessor can access service.name
            var resourceBuilder =
                    Resource.getDefault().toBuilder()
                            .put(ResourceAttributes.SERVICE_NAME, serviceName)
                            .put(ResourceAttributes.SERVICE_VERSION, INSTRUMENTATION_VERSION);

            resourceAttributes.forEach(resourceBuilder::put);
            var resource = resourceBuilder.build();

            // Create batch processor for efficient export
            var batchProcessor =
                    BatchSpanProcessor.builder(new BraintrustSpanExporter(config))
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

            var logExporter = new BraintrustLogExporter(config);
            var loggerProvider = SdkLoggerProvider.builder()
                    .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                    .build();

            // Create OpenTelemetry instance
            var openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setLoggerProvider(loggerProvider)
                    .build();

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
                                        var result = CompletableResultCode.ofAll(
                                                // run all flushes in parallel. Should block for approx 10 seconds max (which would be rare)
                                                Stream.of(openTelemetry.getSdkLoggerProvider().forceFlush(),
                                                                openTelemetry.getSdkMeterProvider().forceFlush(),
                                                                openTelemetry.getSdkTracerProvider().forceFlush())
                                                        .map(operation -> operation.join(10, TimeUnit.SECONDS))
                                                        .toList()
                                        );
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
