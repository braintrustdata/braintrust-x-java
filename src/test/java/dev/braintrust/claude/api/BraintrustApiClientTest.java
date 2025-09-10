package dev.braintrust.claude.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.braintrust.claude.config.BraintrustConfig;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BraintrustApiClientTest {

    @Mock private HttpClient mockHttpClient;

    @Mock private HttpResponse<String> mockResponse;

    private BraintrustConfig config;
    private BraintrustApiClient client;

    @BeforeEach
    void setUp() {
        config =
                BraintrustConfig.builder()
                        .apiKey("test-api-key")
                        .apiUrl("https://api.test.dev")
                        .requestTimeout(Duration.ofSeconds(10))
                        .build();

        client = new BraintrustApiClient(config, mockHttpClient);
    }

    @Test
    void testCreateProject() throws Exception {
        // Given
        var responseJson =
                """
            {
                "id": "project-123",
                "name": "Test Project",
                "created_at": "2024-01-01T00:00:00Z",
                "updated_at": "2024-01-01T00:00:00Z"
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        var project = client.createProject("Test Project").get();

        // Then
        assertThat(project.id()).isEqualTo("project-123");
        assertThat(project.name()).isEqualTo("Test Project");

        // Verify request
        verify(mockHttpClient)
                .sendAsync(
                        argThat(
                                request -> {
                                    assertThat(request.uri().toString())
                                            .isEqualTo("https://api.test.dev/v1/project");
                                    assertThat(request.method()).isEqualTo("POST");
                                    assertThat(request.headers().firstValue("Authorization"))
                                            .hasValue("Bearer test-api-key");
                                    assertThat(request.headers().firstValue("Content-Type"))
                                            .hasValue("application/json");
                                    return true;
                                }),
                        any());
    }

    @Test
    void testGetProjectNotFound() {
        // Given
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("Not found");
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When/Then
        assertThatThrownBy(() -> client.getProject("non-existent").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CompletionException.class)
                .getCause()
                .getCause()
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("404");
    }

    @Test
    void testGetProjectError() {
        // Given
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal server error");
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When/Then
        assertThatThrownBy(() -> client.getProject("project-id").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CompletionException.class)
                .getCause()
                .getCause()
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("500");
    }

    @Test
    void testListProjects() throws Exception {
        // Given
        var responseJson =
                """
            {
                "projects": [
                    {
                        "id": "project-1",
                        "name": "Project 1",
                        "created_at": "2024-01-01T00:00:00Z",
                        "updated_at": "2024-01-01T00:00:00Z"
                    },
                    {
                        "id": "project-2",
                        "name": "Project 2",
                        "created_at": "2024-01-02T00:00:00Z",
                        "updated_at": "2024-01-02T00:00:00Z"
                    }
                ]
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        var projects = client.listProjects().get();

        // Then
        assertThat(projects).hasSize(2);
        assertThat(projects.get(0).id()).isEqualTo("project-1");
        assertThat(projects.get(1).id()).isEqualTo("project-2");
    }

    @Test
    void testCreateExperiment() throws Exception {
        // Given
        var request =
                new BraintrustApiClient.CreateExperimentRequest(
                        "project-123",
                        "Test Experiment",
                        Optional.of("Description"),
                        Optional.of("base-exp-123"));

        var responseJson =
                """
            {
                "id": "exp-123",
                "project_id": "project-123",
                "name": "Test Experiment",
                "description": "Description",
                "created_at": "2024-01-01T00:00:00Z",
                "updated_at": "2024-01-01T00:00:00Z"
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        var experiment = client.createExperiment(request).get();

        // Then
        assertThat(experiment.id()).isEqualTo("exp-123");
        assertThat(experiment.projectId()).isEqualTo("project-123");
        assertThat(experiment.name()).isEqualTo("Test Experiment");
        assertThat(experiment.description()).hasValue("Description");
    }

    @Test
    void testInsertDatasetEvents() throws Exception {
        // Given
        var events =
                List.of(
                        new BraintrustApiClient.DatasetEvent("input1", "output1"),
                        new BraintrustApiClient.DatasetEvent(
                                "input2", Optional.of("output2"), Optional.of("metadata")));

        var responseJson =
                """
            {
                "inserted_count": 2
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        var response = client.insertDatasetEvents("dataset-123", events).get();

        // Then
        assertThat(response.insertedCount()).isEqualTo(2);

        // Verify request
        verify(mockHttpClient)
                .sendAsync(
                        argThat(
                                request -> {
                                    assertThat(request.uri().toString())
                                            .isEqualTo(
                                                    "https://api.test.dev/v1/dataset/dataset-123/insert");
                                    assertThat(request.method()).isEqualTo("POST");
                                    return true;
                                }),
                        any());
    }

    @Test
    void testRequestTimeout() {
        // Given
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.failedFuture(new IOException("Request timeout")));

        // When/Then
        assertThatThrownBy(() -> client.getProject("project-id").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CompletionException.class)
                .getCause()
                .getCause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void testInvalidJsonResponse() {
        // Given
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("invalid json");
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When/Then
        assertThatThrownBy(() -> client.getProject("project-id").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CompletionException.class)
                .getCause()
                .getCause()
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Failed to parse response");
    }

    @Test
    void testDebugLogging() throws Exception {
        // Given
        var debugConfig = BraintrustConfig.builder().apiKey("test-api-key").debug(true).build();
        var debugClient = new BraintrustApiClient(debugConfig, mockHttpClient);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{}");
        when(mockHttpClient.<String>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        debugClient.listProjects().get();

        // Then - would see debug logs (tested via logger tests)
        verify(mockHttpClient).sendAsync(any(), any());
    }
}
