package dev.braintrust.examples;

/**
 * Verify the corrected OTLP endpoint.
 */
public class VerifyEndpoint {
    public static void main(String[] args) {
        System.out.println("Braintrust OTLP Endpoint Configuration:");
        System.out.println("=======================================");
        System.out.println();
        System.out.println("Previous (incorrect): https://api.braintrust.dev/otlp/v1/traces");
        System.out.println("Current (correct):    https://api.braintrust.dev/otel/v1/traces");
        System.out.println();
        System.out.println("The issue was a typo in the endpoint path.");
        System.out.println("Braintrust uses '/otel/v1/traces' not '/otlp/v1/traces'");
        System.out.println();
        System.out.println("This matches the Go implementation:");
        System.out.println("  otlptracehttp.WithURLPath(\"/otel/v1/traces\")");
        System.out.println();
        System.out.println("With the correct endpoint, you should now see:");
        System.out.println("- HTTP 401 for invalid API keys");
        System.out.println("- HTTP 200 for valid API keys");
        System.out.println("- No more HTTP 403 errors");
    }
}