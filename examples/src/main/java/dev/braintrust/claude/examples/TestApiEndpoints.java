package dev.braintrust.claude.examples;

import dev.braintrust.claude.config.BraintrustConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TestApiEndpoints {
    public static void main(String[] args) throws Exception {
        var config = BraintrustConfig.fromEnvironment();
        var httpClient = HttpClient.newHttpClient();

        System.out.println("=== Testing Braintrust API Endpoints ===");
        System.out.println("Base URL: " + config.apiUrl());
        System.out.println();

        // Test various endpoints to see which ones exist
        String[] endpoints = {
            "/projects",
            "/v1/projects",
            "/v1/project", // Go SDK uses singular
            "/experiments",
            "/v1/experiments",
            "/v1/experiment", // Go SDK uses singular
            "/v1/dataset",
            "/otel/v1/traces"
        };

        for (String endpoint : endpoints) {
            testEndpoint(httpClient, config, endpoint);
        }
    }

    private static void testEndpoint(
            HttpClient httpClient, BraintrustConfig config, String endpoint) {
        try {
            var request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(config.apiUrl() + endpoint))
                            .header("Authorization", "Bearer " + config.apiKey())
                            .header("Accept", "application/json")
                            .GET()
                            .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.printf(
                    "%-25s -> %d %s%n",
                    endpoint,
                    response.statusCode(),
                    response.statusCode() == 200
                            ? "✓"
                            : response.statusCode() == 403
                                    ? "(403: " + response.body() + ")"
                                    : response.statusCode() == 401
                                            ? "(unauthorized)"
                                            : "(" + extractError(response.body()) + ")");

        } catch (Exception e) {
            System.out.printf("%-25s -> ERROR: %s%n", endpoint, e.getMessage());
        }
    }

    private static String extractError(String body) {
        // Try to extract error message from response
        if (body.contains("\"error\"")) {
            int start = body.indexOf("\"error\"");
            int end = body.indexOf("}", start);
            if (start > 0 && end > start) {
                return body.substring(start, Math.min(end + 1, body.length()));
            }
        }
        return body.length() > 50 ? body.substring(0, 50) + "..." : body;
    }
}
