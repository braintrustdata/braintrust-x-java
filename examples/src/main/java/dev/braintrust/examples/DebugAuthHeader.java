package dev.braintrust.examples;

import dev.braintrust.api.BraintrustApiClient;
import dev.braintrust.config.BraintrustConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DebugAuthHeader {
    public static void main(String[] args) throws Exception {
        // Get the config
        var config = BraintrustConfig.fromEnvironment();

        System.out.println("=== Debug Authorization Header ===");
        System.out.println("API URL: " + config.apiUrl());
        System.out.println("API Key: " + maskApiKey(config.apiKey()));
        System.out.println("API Key length: " + config.apiKey().length());
        System.out.println(
                "API Key starts with: "
                        + config.apiKey().substring(0, Math.min(10, config.apiKey().length())));

        // Check for any special characters
        System.out.println("\nChecking for special characters in API key:");
        for (int i = 0; i < config.apiKey().length(); i++) {
            char c = config.apiKey().charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') {
                System.out.println(
                        "  Found special char at position "
                                + i
                                + ": '"
                                + c
                                + "' (ASCII: "
                                + (int) c
                                + ")");
            }
        }

        // Try a raw HTTP request to see exactly what we're sending
        System.out.println("\n=== Testing Raw HTTP Request ===");
        var httpClient = HttpClient.newHttpClient();
        var authHeader = "Bearer " + config.apiKey();
        System.out.println("Full Authorization header: '" + authHeader + "'");
        System.out.println("Header length: " + authHeader.length());

        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(config.apiUrl() + "/projects"))
                        .header("Authorization", authHeader)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

        System.out.println("\nRequest headers:");
        request.headers()
                .map()
                .forEach(
                        (key, values) -> {
                            System.out.println("  " + key + ": " + values);
                        });

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("\nResponse status: " + response.statusCode());
            System.out.println("Response body: " + response.body());
        } catch (Exception e) {
            System.err.println("Request failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Also try using the API client
        System.out.println("\n=== Testing via API Client ===");
        try {
            var apiClient = new BraintrustApiClient(config);
            var projects = apiClient.listProjects().get();
            System.out.println("Success! Found " + projects.size() + " projects");
        } catch (Exception e) {
            System.err.println("API client failed: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
        }
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
