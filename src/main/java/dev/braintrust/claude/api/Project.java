package dev.braintrust.claude.api;

import dev.braintrust.claude.config.BraintrustConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** High-level API for project management, matching the Go SDK's DX. */
public final class Project {

    private Project() {
        // Utility class
    }

    /**
     * Registers a project in Braintrust. If a project with the same name already exists, returns
     * the existing project.
     *
     * @param name The name of the project
     * @return The project (created or existing)
     */
    public static CompletableFuture<BraintrustApiClient.Project> registerProject(String name) {
        var config = BraintrustConfig.fromEnvironment();
        return registerProject(name, config);
    }

    /**
     * Registers a project with custom configuration.
     *
     * @param name The name of the project
     * @param config Custom Braintrust configuration
     * @return The project (created or existing)
     */
    public static CompletableFuture<BraintrustApiClient.Project> registerProject(
            String name, BraintrustConfig config) {
        var client = new BraintrustApiClient(config);

        // The API handles "get or create" semantics
        return client.createProject(name);
    }

    /** Synchronous version of registerProject for simpler usage. */
    public static BraintrustApiClient.Project registerProjectSync(String name) {
        try {
            return registerProject(name).get();
        } catch (Exception e) {
            if (e instanceof CompletionException && e.getCause() != null) {
                throw new RuntimeException("Failed to register project", e.getCause());
            }
            throw new RuntimeException("Failed to register project", e);
        }
    }
}
