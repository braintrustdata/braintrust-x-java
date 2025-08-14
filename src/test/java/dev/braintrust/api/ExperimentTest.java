package dev.braintrust.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.braintrust.config.BraintrustConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExperimentTest {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void testDisplayExperimentUrl() throws Exception {
        // Create test data
        var experiment =
                new BraintrustApiClient.Experiment(
                        "exp-123",
                        "proj-456",
                        "test-experiment",
                        Optional.of("Test description"),
                        "2024-01-01T00:00:00Z",
                        "2024-01-01T00:00:00Z");
        var project =
                new BraintrustApiClient.Project(
                        "proj-456",
                        "Test Project",
                        "org-789",
                        "2024-01-01T00:00:00Z",
                        "2024-01-01T00:00:00Z");
        var config =
                BraintrustConfig.builder()
                        .apiKey("test-key")
                        .apiUrl("https://api.braintrust.dev")
                        .appUrl(URI.create("https://www.braintrust.dev"))
                        .orgName("Test Org")
                        .build();

        // Use reflection to call the private method
        var method =
                Experiment.class.getDeclaredMethod(
                        "displayExperimentUrl",
                        BraintrustApiClient.Experiment.class,
                        BraintrustApiClient.Project.class,
                        BraintrustConfig.class);
        method.setAccessible(true);
        method.invoke(null, experiment, project, config);

        // Verify the output
        var output = outputStream.toString();
        assertThat(output).contains("Experiment test-experiment is running at");
        assertThat(output)
                .contains(
                        "https://www.braintrust.dev/app/Test%20Org/p/Test%20Project/experiments/test-experiment");
    }

    @Test
    void testDisplayExperimentUrlWithSpaces() throws Exception {
        // Test with spaces in org and project names
        var experiment =
                new BraintrustApiClient.Experiment(
                        "exp-123",
                        "proj-456",
                        "test experiment",
                        Optional.of("Test description"),
                        "2024-01-01T00:00:00Z",
                        "2024-01-01T00:00:00Z");
        var project =
                new BraintrustApiClient.Project(
                        "proj-456",
                        "Test Project With Spaces",
                        "org-789",
                        "2024-01-01T00:00:00Z",
                        "2024-01-01T00:00:00Z");
        var config =
                BraintrustConfig.builder()
                        .apiKey("test-key")
                        .apiUrl("https://api.braintrust.dev")
                        .appUrl(URI.create("https://www.braintrust.dev"))
                        .orgName("Test Org With Spaces")
                        .build();

        // Use reflection to call the private method
        var method =
                Experiment.class.getDeclaredMethod(
                        "displayExperimentUrl",
                        BraintrustApiClient.Experiment.class,
                        BraintrustApiClient.Project.class,
                        BraintrustConfig.class);
        method.setAccessible(true);
        method.invoke(null, experiment, project, config);

        // Verify the output with proper URL encoding
        var output = outputStream.toString();
        assertThat(output).contains("Experiment test experiment is running at");
        assertThat(output)
                .contains(
                        "https://www.braintrust.dev/app/Test%20Org%20With%20Spaces/p/Test%20Project%20With%20Spaces/experiments/test%20experiment");
    }
}
