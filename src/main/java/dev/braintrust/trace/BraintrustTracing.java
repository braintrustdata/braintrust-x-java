package dev.braintrust.trace;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
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
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

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

    /** Gets a tracer with Braintrust instrumentation scope. */
    public static Tracer getTracer() {
        return getTracer(GlobalOpenTelemetry.get());
    }

    /** Gets a tracer from a specific OpenTelemetry instance. */
    public static Tracer getTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    /**
     * Quick start method that sets up global OpenTelemetry with Braintrust defaults. <br>
     * <br>
     * If you're looking for more options for configuring Braintrust/OpenTelemetry, consult the
     * `enable` method.
     */
    public static OpenTelemetry quickstart() {
        return quickstart(BraintrustConfig.fromEnvironment(), true);
    }

    /**
     * Quick start method that sets up OpenTelemetry with custom Braintrust and otel settings. <br>
     * <br>
     * If you're looking for more options for configuring Braintrust and OpenTelemetry, consult the
     * `enable` method.
     */
    public static OpenTelemetry quickstart(
            @Nonnull BraintrustConfig config, boolean registerGlobal) {
        var tracerBuilder = SdkTracerProvider.builder();
        var loggerBuilder = SdkLoggerProvider.builder();
        var meterBuilder = SdkMeterProvider.builder();
        enable(config, tracerBuilder, loggerBuilder, meterBuilder);
        var openTelemetry =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerBuilder.build())
                        .setLoggerProvider(loggerBuilder.build())
                        .setMeterProvider(meterBuilder.build())
                        .build();
        if (registerGlobal) {
            GlobalOpenTelemetry.set(openTelemetry);
            BraintrustLogger.debug("Registered OpenTelemetry globally");
        }
        return openTelemetry;
    }

    /**
     * Add braintrust to existing open telemetry builders <br>
     * <br>
     * This method provides the most options for configuring Braintrust and OpenTelemetry. If you're
     * looking for a more user-friendly setup, consult the `quickstart` methods. <br>
     * <br>
     * NOTE: if your otel setup does not have any particular builder, pass an instance of the
     * default provider builder. E.g. `SdkMeterProvider.builder()` <br>
     * <br>
     * NOTE: This method should only be invoked once. Enabling Braintrust multiple times is
     * unsupported and may lead to undesired behavior
     */
    public static void enable(
            @Nonnull BraintrustConfig config,
            @Nonnull SdkTracerProviderBuilder tracerProviderBuilder,
            @Nonnull SdkLoggerProviderBuilder loggerProviderBuilder,
            @Nonnull SdkMeterProviderBuilder meterProviderBuilder) {
        // NOTE: these options are hardcoded for now. We'll move them into braintrust config
        // as-needed
        final Map<String, String> resourceAttributes = new HashMap<>();
        final String serviceName = "braintrust-app";
        final Duration exportInterval = Duration.ofSeconds(5);
        final int maxQueueSize = 2048;
        final int maxExportBatchSize = 512;
        BraintrustLogger.info("Initializing Braintrust OpenTelemetry with service={}", serviceName);

        // Create resource first so BraintrustSpanProcessor can access service.name
        var resourceBuilder =
                Resource.getDefault().toBuilder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, INSTRUMENTATION_VERSION);
        resourceAttributes.forEach(resourceBuilder::put);
        var resource = resourceBuilder.build();

        // spans
        var spanProcessor =
                new BraintrustSpanProcessor(
                        config,
                        BatchSpanProcessor.builder(new BraintrustSpanExporter(config))
                                .setScheduleDelay(exportInterval.toMillis(), TimeUnit.MILLISECONDS)
                                .setMaxQueueSize(maxQueueSize)
                                .setMaxExportBatchSize(maxExportBatchSize)
                                .build());
        tracerProviderBuilder.addResource(resource).addSpanProcessor(spanProcessor);
        // logs
        var logProcessor =
                BatchLogRecordProcessor.builder(new BraintrustLogExporter(config))
                        .setScheduleDelay(exportInterval.toMillis(), TimeUnit.MILLISECONDS)
                        .setMaxQueueSize(maxQueueSize)
                        .setMaxExportBatchSize(maxExportBatchSize)
                        .build();
        loggerProviderBuilder.addLogRecordProcessor(logProcessor);

        // NOTE: we don't actually do anything with otel metrics right now,
        // but it's included in the method signature so we can do so in the future without
        // introducing a breaking change

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    BraintrustLogger.debug(
                                            "Shutting down. Force-Flushing all otel data.");
                                    var result =
                                            CompletableResultCode.ofAll(
                                                    // run all flushes in parallel. Should (rarely)
                                                    // block for approx 10 seconds max
                                                    Stream.of(
                                                                    spanProcessor.shutdown(),
                                                                    logProcessor.shutdown())
                                                            .map(
                                                                    operation ->
                                                                            operation.join(
                                                                                    10,
                                                                                    TimeUnit
                                                                                            .SECONDS))
                                                            .toList());
                                    BraintrustLogger.debug(
                                            "otel shutdown complete. Flush done: %s, Flush successful: %s"
                                                    .formatted(
                                                            result.isDone(), result.isSuccess()));
                                }));
    }
}
