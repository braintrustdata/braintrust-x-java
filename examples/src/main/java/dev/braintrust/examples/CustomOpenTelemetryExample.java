package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

/**
 * Example showing how to add Braintrust to an existing open telemetry setup.
 */
public class CustomOpenTelemetryExample {
    public static void main(String[] args) throws Exception {
        // Not required to run this example, but this assumes you have a local collector running.
        // See repo root README for instructions to set up a local collector that prints to stdout
        var localCollector = "http://localhost:4318";
        var tracerBuilder = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder()
                                .setEndpoint(localCollector + "/v1/traces")
                                .build()).build());
        var loggerBuilder = SdkLoggerProvider.builder()
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(OtlpHttpLogRecordExporter.builder()
                                .setEndpoint(localCollector + "/v1/logs")
                                .build()).build());
        var meterBuilder = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(OtlpHttpMetricExporter.builder()
                                .setEndpoint(localCollector + "/v1/metrics")
                                .build()).build());

        // NOTE: there are many ways to set up otel builders, etc.
        // The important line is here: call enable with your otel builders and braintrust will export open telemetry data in addition to your existing setup
        BraintrustTracing.enable(BraintrustConfig.fromEnvironment(), tracerBuilder, loggerBuilder, meterBuilder);

        var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerBuilder.build())
                .setLoggerProvider(loggerBuilder.build())
                .setMeterProvider(meterBuilder.build())
                .build();
        var braintrustTracer = BraintrustTracing.getTracer(openTelemetry);

        // Now otel data will be exported to the local collector AND to braintrust
        var span = braintrustTracer.spanBuilder("hello-java").startSpan();
        try (var ignored = span.makeCurrent()) {
            System.out.println("Performing simple operation...");
            span.setAttribute("some boolean attribute", true);
            Thread.sleep(100); // Not required. This is just to make the span look interesting
        } finally {
            span.end();
        }
        System.out.println("custom example completed!");
    }
}
