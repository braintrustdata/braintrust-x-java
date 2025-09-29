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
import java.util.concurrent.ExecutionException;

public interface BraintrustApiClient {
    /** Creates or gets a project by name. */
    Project getOrCreateProject(String projectName);

    /** Gets a project by ID. */
    Optional<Project> getProject(String projectId);

    /** Creates an experiment. */
    Experiment getOrCreateExperiment(CreateExperimentRequest request);

    /** Get project and org info for the default project ID */
    Optional<OrganizationAndProjectInfo> getProjectAndOrgInfo();

    /** Get project and org info for the given project ID */
    Optional<OrganizationAndProjectInfo> getProjectAndOrgInfo(String projectId);

    static BraintrustApiClient of(BraintrustConfig config) {
        return new HttpImpl(config);
    }

    class HttpImpl implements BraintrustApiClient {
        private final BraintrustConfig config;
        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        HttpImpl(BraintrustConfig config) {
            this(config, createDefaultHttpClient(config));
        }

        private HttpImpl(BraintrustConfig config, HttpClient httpClient) {
            this.config = config;
            this.httpClient = httpClient;
            this.objectMapper = createObjectMapper();
        }

        @Override
        public Project getOrCreateProject(String projectName) {
            try {
                var request = new CreateProjectRequest(projectName);
                return postAsync("/v1/project", request, Project.class).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<Project> getProject(String projectId) {
            try {
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
                                })
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Experiment getOrCreateExperiment(CreateExperimentRequest request) {
            try {
                return postAsync("/v1/experiment", request, Experiment.class).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ApiException(e);
            }
        }

        private LoginResponse login() {
            try {
                return postAsync(
                                "/api/apikey/login",
                                new LoginRequest(config.apiKey()),
                                LoginResponse.class)
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<OrganizationAndProjectInfo> getProjectAndOrgInfo() {
            var projectId = config.defaultProjectId().orElse(null);
            if (null == projectId) {
                projectId = getOrCreateProject(config.defaultProjectName().orElseThrow()).id();
            }
            return getProjectAndOrgInfo(projectId);
        }

        @Override
        public Optional<OrganizationAndProjectInfo> getProjectAndOrgInfo(String projectId) {
            var project = getProject(projectId).orElse(null);
            if (null == project) {
                return Optional.empty();
            }
            OrganizationInfo orgInfo = null;
            for (var org : login().orgInfo()) {
                if (project.orgId().equalsIgnoreCase(org.id())) {
                    orgInfo = org;
                    break;
                }
            }
            if (null == orgInfo) {
                throw new ApiException(
                        "Should not happen. Unable to find project's org: " + project.orgId());
            }
            return Optional.of(new OrganizationAndProjectInfo(orgInfo, project));
        }

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

        private <T> CompletableFuture<T> postAsync(
                String path, Object body, Class<T> responseType) {
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
            if (error instanceof ApiException) {
                return ((ApiException) error).getMessage().contains("404");
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
    }

    /** Implementation for test doubling */
    class InMemoryImpl implements BraintrustApiClient {
        private final List<OrganizationAndProjectInfo> organizationAndProjectInfos;

        public InMemoryImpl(OrganizationAndProjectInfo... organizationAndProjectInfos) {
            this.organizationAndProjectInfos = List.of(organizationAndProjectInfos);
        }

        @Override
        public Project getOrCreateProject(String projectName) {
            // Find existing project by name
            for (var orgAndProject : organizationAndProjectInfos) {
                if (orgAndProject.project().name().equals(projectName)) {
                    return orgAndProject.project();
                }
            }
            throw new RuntimeException(
                    "Project '"
                            + projectName
                            + "' not found in test data. Please add it to the InMemoryImpl"
                            + " constructor.");
        }

        @Override
        public Optional<Project> getProject(String projectId) {
            return organizationAndProjectInfos.stream()
                    .map(OrganizationAndProjectInfo::project)
                    .filter(project -> project.id().equals(projectId))
                    .findFirst();
        }

        @Override
        public Experiment getOrCreateExperiment(CreateExperimentRequest request) {
            throw new RuntimeException("not supported");
        }

        @Override
        public Optional<OrganizationAndProjectInfo> getProjectAndOrgInfo() {
            return organizationAndProjectInfos.isEmpty()
                    ? Optional.empty()
                    : Optional.of(organizationAndProjectInfos.get(0));
        }

        @Override
        public Optional<OrganizationAndProjectInfo> getProjectAndOrgInfo(String projectId) {
            return organizationAndProjectInfos.stream()
                    .filter(orgAndProject -> orgAndProject.project().id().equals(projectId))
                    .findFirst();
        }
    }

    // Request/Response DTOs

    record CreateProjectRequest(String name) {}

    record Project(String id, String name, String orgId, String createdAt, String updatedAt) {}

    record ProjectList(List<Project> projects) {}

    record ExperimentList(List<Experiment> experiments) {}

    record CreateExperimentRequest(
            String projectId,
            String name,
            Optional<String> description,
            Optional<String> baseExperimentId) {

        public CreateExperimentRequest(String projectId, String name) {
            this(projectId, name, Optional.empty(), Optional.empty());
        }
    }

    record Experiment(
            String id,
            String projectId,
            String name,
            Optional<String> description,
            String createdAt,
            String updatedAt) {}

    record CreateDatasetRequest(String projectId, String name, Optional<String> description) {
        public CreateDatasetRequest(String projectId, String name) {
            this(projectId, name, Optional.empty());
        }
    }

    record Dataset(
            String id,
            String projectId,
            String name,
            Optional<String> description,
            String createdAt,
            String updatedAt) {}

    record DatasetList(List<Dataset> datasets) {}

    record DatasetEvent(Object input, Optional<Object> output, Optional<Object> metadata) {
        public DatasetEvent(Object input) {
            this(input, Optional.empty(), Optional.empty());
        }

        public DatasetEvent(Object input, Object output) {
            this(input, Optional.of(output), Optional.empty());
        }
    }

    record InsertEventsRequest(List<DatasetEvent> events) {}

    record InsertEventsResponse(int insertedCount) {}

    // User and Organization models for login functionality
    record OrganizationInfo(String id, String name) {}

    record LoginRequest(String token) {}

    record LoginResponse(List<OrganizationInfo> orgInfo) {}

    record OrganizationAndProjectInfo(OrganizationInfo orgInfo, Project project) {}
}
