package dev.braintrust.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.log.BraintrustLogger;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Modern API client for Braintrust using Java 11+ HTTP client. Provides both synchronous and
 * asynchronous methods.
 */
public class BraintrustApiClient implements AutoCloseable {

    private final BraintrustConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BraintrustApiClient(BraintrustConfig config) {
        this(config, createDefaultHttpClient(config));
    }

    public BraintrustApiClient(BraintrustConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = createObjectMapper();
    }

    /** Creates or gets a project by name. */
    public CompletableFuture<Project> createProject(String name) {
        var request = new CreateProjectRequest(name);
        return postAsync("/v1/project", request, Project.class);
    }

    /** Gets a project by ID. */
    public CompletableFuture<Optional<Project>> getProject(String projectId) {
        return getAsync("/v1/project/" + projectId, Project.class)
                .handle(
                        (project, error) -> {
                            if (error != null && isNotFound(error)) {
                                return Optional.<Project>empty();
                            }
                            if (error != null) {
                                throw new CompletionException(error);
                            }
                            return Optional.of(project);
                        });
    }

    /** Lists all projects. */
    public CompletableFuture<List<Project>> listProjects() {
        return getAsync("/v1/project", ProjectList.class).thenApply(ProjectList::projects);
    }

    /** Creates an experiment. */
    public CompletableFuture<Experiment> createExperiment(CreateExperimentRequest request) {
        return postAsync("/v1/experiment", request, Experiment.class);
    }

    /** Gets an experiment by ID. */
    public CompletableFuture<Optional<Experiment>> getExperiment(String experimentId) {
        return getAsync("/v1/experiment/" + experimentId, Experiment.class)
                .handle(
                        (experiment, error) -> {
                            if (error != null && isNotFound(error)) {
                                return Optional.<Experiment>empty();
                            }
                            if (error != null) {
                                throw new CompletionException(error);
                            }
                            return Optional.of(experiment);
                        });
    }

    /** Lists experiments for a project. */
    public CompletableFuture<List<Experiment>> listExperiments(String projectId) {
        return getAsync("/v1/experiment?project_id=" + projectId, ExperimentList.class)
                .thenApply(ExperimentList::experiments);
    }

    /** Creates a dataset. */
    public CompletableFuture<Dataset> createDataset(CreateDatasetRequest request) {
        return postAsync("/v1/dataset", request, Dataset.class);
    }

    /** Inserts events into a dataset. */
    public CompletableFuture<InsertEventsResponse> insertDatasetEvents(
            String datasetId, List<DatasetEvent> events) {
        var request = new InsertEventsRequest(events);
        return postAsync(
                "/v1/dataset/" + datasetId + "/insert", request, InsertEventsResponse.class);
    }

    /** Lists datasets for a project. */
    public CompletableFuture<List<Dataset>> listDatasets(String projectId) {
        return getAsync("/v1/dataset?project_id=" + projectId, DatasetList.class)
                .thenApply(DatasetList::datasets);
    }

    // Low-level HTTP methods

    private <T> CompletableFuture<T> getAsync(String path, Class<T> responseType) {
        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(config.apiUrl() + path))
                        .header("Authorization", "Bearer " + config.apiKey())
                        .header("Accept", "application/json")
                        .timeout(config.requestTimeout())
                        .GET()
                        .build();

        return sendAsync(request, responseType);
    }

    private <T> CompletableFuture<T> postAsync(String path, Object body, Class<T> responseType) {
        try {
            var jsonBody = objectMapper.writeValueAsString(body);

            var request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(config.apiUrl() + path))
                            .header("Authorization", "Bearer " + config.apiKey())
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .timeout(config.requestTimeout())
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

            return sendAsync(request, responseType);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(
                    new ApiException("Failed to serialize request body", e));
        }
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType) {
        BraintrustLogger.debug("API Request: {} {}", request.method(), request.uri());

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleResponse(response, responseType));
    }

    private <T> T handleResponse(HttpResponse<String> response, Class<T> responseType) {
        BraintrustLogger.debug("API Response: {} - {}", response.statusCode(), response.body());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                return objectMapper.readValue(response.body(), responseType);
            } catch (IOException e) {
                BraintrustLogger.warn("Failed to parse response body", e);
                throw new ApiException("Failed to parse response body", e);
            }
        } else {
            BraintrustLogger.warn(
                    "API request failed with status {}: {}",
                    response.statusCode(),
                    response.body());
            throw new ApiException(
                    String.format(
                            "API request failed with status %d: %s",
                            response.statusCode(), response.body()));
        }
    }

    private boolean isNotFound(Throwable error) {
        if (error instanceof ApiException apiException) {
            return apiException.getMessage().contains("404");
        }
        return false;
    }

    private static HttpClient createDefaultHttpClient(BraintrustConfig config) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module()) // For Optional support
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(
                        JsonInclude.Include.NON_ABSENT) // Skip null and absent Optional
                .configure(
                        com.fasterxml.jackson.databind.DeserializationFeature
                                .FAIL_ON_UNKNOWN_PROPERTIES,
                        false); // Ignore unknown fields from API
    }

    @Override
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
    }

    // Request/Response DTOs

    public record CreateProjectRequest(String name) {}

    public record Project(
            String id, String name, String orgId, String createdAt, String updatedAt) {}

    private record ProjectList(List<Project> projects) {}

    private record ExperimentList(List<Experiment> experiments) {}

    public record CreateExperimentRequest(
            String projectId,
            String name,
            Optional<String> description,
            Optional<String> baseExperimentId) {
        public CreateExperimentRequest(String projectId, String name) {
            this(projectId, name, Optional.empty(), Optional.empty());
        }
    }

    public record Experiment(
            String id,
            String projectId,
            String name,
            Optional<String> description,
            String createdAt,
            String updatedAt) {}

    public record CreateDatasetRequest(
            String projectId, String name, Optional<String> description) {
        public CreateDatasetRequest(String projectId, String name) {
            this(projectId, name, Optional.empty());
        }
    }

    public record Dataset(
            String id,
            String projectId,
            String name,
            Optional<String> description,
            String createdAt,
            String updatedAt) {}

    private record DatasetList(List<Dataset> datasets) {}

    public record DatasetEvent(Object input, Optional<Object> output, Optional<Object> metadata) {
        public DatasetEvent(Object input) {
            this(input, Optional.empty(), Optional.empty());
        }

        public DatasetEvent(Object input, Object output) {
            this(input, Optional.of(output), Optional.empty());
        }
    }

    private record InsertEventsRequest(List<DatasetEvent> events) {}

    public record InsertEventsResponse(int insertedCount) {}
}
