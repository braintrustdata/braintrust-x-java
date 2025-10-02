package dev.braintrust.eval;

import static org.junit.jupiter.api.Assertions.*;

import dev.braintrust.api.BraintrustApiClient;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;
import dev.braintrust.trace.BraintrustTracingTest;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EvalTest {

    @BeforeEach
    void beforeEach() {
        GlobalOpenTelemetry.resetForTest();
        BraintrustTracingTest.getExportedBraintrustSpans().clear();
    }

    @Test
    public void evalOtelTraceWithProperAttributes() {
        var projectId = "1234";
        var projectName = "proj-name";
        var experimentName = "unit-test-eval";
        var config =
                BraintrustConfig.of(
                        "BRAINTRUST_API_KEY", "foobar",
                        "BRAINTRUST_JAVA_EXPORT_SPANS_IN_MEMORY_FOR_UNIT_TEST", "true",
                        "BRAINTRUST_API_URL", "https://api.braintrust.dev",
                        "BRAINTRUST_APP_URL", "https://braintrust.dev",
                        "BRAINTRUST_DEFAULT_PROJECT_NAME", projectName);
        var apiClient = createApiClient(projectId, projectName);
        var sdk = (OpenTelemetrySdk) BraintrustTracing.of(config, true);

        var eval =
                Eval.<String, String>builder()
                        .name(experimentName)
                        .tracer(BraintrustTracing.getTracer(sdk))
                        .config(config)
                        .apiClient(apiClient)
                        .cases(
                                EvalCase.of("strawberry", "fruit"),
                                EvalCase.of("asparagus", "vegetable"))
                        .task((Function<String, String>) food -> "fruit")
                        .scorers(
                                Scorer.of(
                                        "fruit_scorer",
                                        result -> "fruit".equals(result) ? 1.0 : 0.0),
                                Scorer.of(
                                        "vegetable_scorer",
                                        result -> "vegetable".equals(result) ? 1.0 : 0.0))
                        .build();
        var result = eval.run();
        assertEquals(
                "https://braintrust.dev/app/Test%20Org/p/proj-name/experiments/unit-test-eval",
                result.getExperimentUrl());
        assertTrue(sdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS).isSuccess());
        assertEquals(1, BraintrustTracingTest.getExportedBraintrustSpans().size());
        var experiment =
                apiClient.getOrCreateExperiment(
                        new BraintrustApiClient.CreateExperimentRequest(projectId, experimentName));
        var evalSpans =
                BraintrustTracingTest.getExportedBraintrustSpans()
                        .get("experiment_id:" + experiment.id());
        assertNotNull(evalSpans);
        assertNotEquals(0, evalSpans.size());
        evalSpans.forEach(
                span -> {
                    var parent =
                            span.getAttributes()
                                    .get(AttributeKey.stringKey(BraintrustTracing.PARENT_KEY));
                    assertEquals(
                            "experiment_id:" + experiment.id(),
                            parent,
                            "all eval spans must set the parent to the experiment id");
                });
    }

    private BraintrustApiClient createApiClient(String projectId, String projectName) {
        var orgInfo =
                new dev.braintrust.api.BraintrustApiClient.OrganizationInfo("org_123", "Test Org");
        var project =
                new dev.braintrust.api.BraintrustApiClient.Project(
                        projectId,
                        projectName,
                        "org_123",
                        "2023-01-01T00:00:00Z",
                        "2023-01-01T00:00:00Z");
        var orgAndProjectInfo =
                new dev.braintrust.api.BraintrustApiClient.OrganizationAndProjectInfo(
                        orgInfo, project);
        return new dev.braintrust.api.BraintrustApiClient.InMemoryImpl(orgAndProjectInfo);
    }
}
