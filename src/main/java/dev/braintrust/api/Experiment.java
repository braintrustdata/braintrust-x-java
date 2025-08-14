package dev.braintrust.api;

import dev.braintrust.config.BraintrustConfig;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        return registerExperiment(name, projectId, config);
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

        return client.createExperiment(request)
                .thenCompose(
                        experiment -> {
                            // Get project info to build URL
                            return client.getProject(projectId)
                                    .thenApply(
                                            projectOpt -> {
                                                projectOpt.ifPresent(
                                                        project ->
                                                                displayExperimentUrl(
                                                                        experiment,
                                                                        project,
                                                                        config));
                                                return experiment;
                                            });
                        });
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
                            return client.createExperiment(request)
                                    .thenApply(
                                            experiment -> {
                                                displayExperimentUrl(experiment, project, config);
                                                return experiment;
                                            });
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

    /**
     * Displays the experiment URL to stdout automatically when an experiment is created.
     *
     * @param experiment The experiment that was created
     * @param project The project containing the experiment
     * @param config The Braintrust configuration
     */
    private static void displayExperimentUrl(
            BraintrustApiClient.Experiment experiment,
            BraintrustApiClient.Project project,
            BraintrustConfig config) {
        try {
            var appUrl = config.appUrl().toString().replaceAll("/$", "");
            var orgNameEncoded =
                    URLEncoder.encode(config.orgName().orElse("unknown"), StandardCharsets.UTF_8)
                            .replace("+", "%20");
            var projectNameEncoded =
                    URLEncoder.encode(project.name(), StandardCharsets.UTF_8).replace("+", "%20");
            var experimentNameEncoded =
                    URLEncoder.encode(experiment.name(), StandardCharsets.UTF_8)
                            .replace("+", "%20");
            var experimentUrl =
                    String.format(
                            "%s/app/%s/p/%s/experiments/%s",
                            appUrl, orgNameEncoded, projectNameEncoded, experimentNameEncoded);

            System.out.println(
                    "Experiment " + experiment.name() + " is running at " + experimentUrl);
        } catch (Exception e) {
            // Don't fail experiment creation if URL display fails
            System.err.println("Warning: Could not display experiment URL: " + e.getMessage());
        }
    }
}
