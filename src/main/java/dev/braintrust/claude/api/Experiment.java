package dev.braintrust.claude.api;

import dev.braintrust.claude.config.BraintrustConfig;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** High-level API for experiment management, matching the Go SDK's DX. */
public final class Experiment {

    private Experiment() {
        // Utility class
    }

    /**
     * Registers a new experiment in Braintrust. This creates a new experiment every time (matching
     * Go's EnsureNew: true behavior).
     *
     * @param name The name of the experiment
     * @param projectId The ID of the project to create the experiment in
     * @return The created experiment
     */
    public static CompletableFuture<BraintrustApiClient.Experiment> registerExperiment(
            String name, String projectId) {
        var config = BraintrustConfig.fromEnvironment();
        var client = new BraintrustApiClient(config);

        var request =
                new BraintrustApiClient.CreateExperimentRequest(
                        projectId, name, Optional.empty(), Optional.empty());

        return client.createExperiment(request);
    }

    /**
     * Registers a new experiment in Braintrust with custom configuration.
     *
     * @param name The name of the experiment
     * @param projectId The ID of the project to create the experiment in
     * @param config Custom Braintrust configuration
     * @return The created experiment
     */
    public static CompletableFuture<BraintrustApiClient.Experiment> registerExperiment(
            String name, String projectId, BraintrustConfig config) {
        var client = new BraintrustApiClient(config);

        var request =
                new BraintrustApiClient.CreateExperimentRequest(
                        projectId, name, Optional.empty(), Optional.empty());

        return client.createExperiment(request);
    }

    /**
     * Gets or creates an experiment (convenience method). This first creates/gets the project, then
     * creates the experiment.
     *
     * @param experimentName The name of the experiment
     * @param projectName The name of the project
     * @return The experiment ID
     */
    public static CompletableFuture<String> getOrCreateExperiment(
            String experimentName, String projectName) {
        var config = BraintrustConfig.fromEnvironment();
        return getOrCreateExperiment(experimentName, projectName, config);
    }

    /**
     * Gets or creates an experiment with custom configuration.
     *
     * @param experimentName The name of the experiment
     * @param projectName The name of the project
     * @param config Custom Braintrust configuration
     * @return The experiment ID
     */
    public static CompletableFuture<String> getOrCreateExperiment(
            String experimentName, String projectName, BraintrustConfig config) {
        var client = new BraintrustApiClient(config);

        // First, get or create the project
        return Project.registerProject(projectName, config)
                .thenCompose(
                        project -> {
                            // Then create the experiment
                            var request =
                                    new BraintrustApiClient.CreateExperimentRequest(
                                            project.id(),
                                            experimentName,
                                            Optional.empty(),
                                            Optional.empty());
                            return client.createExperiment(request);
                        })
                .thenApply(experiment -> experiment.id());
    }

    /** Synchronous version of registerExperiment for simpler usage. */
    public static BraintrustApiClient.Experiment registerExperimentSync(
            String name, String projectId) {
        try {
            return registerExperiment(name, projectId).get();
        } catch (Exception e) {
            if (e instanceof CompletionException && e.getCause() != null) {
                throw new RuntimeException("Failed to register experiment", e.getCause());
            }
            throw new RuntimeException("Failed to register experiment", e);
        }
    }

    /** Synchronous version of getOrCreateExperiment. */
    public static String getOrCreateExperimentSync(String experimentName, String projectName) {
        try {
            return getOrCreateExperiment(experimentName, projectName).get();
        } catch (Exception e) {
            if (e instanceof CompletionException && e.getCause() != null) {
                throw new RuntimeException("Failed to get or create experiment", e.getCause());
            }
            throw new RuntimeException("Failed to get or create experiment", e);
        }
    }
}
