package dev.braintrust.examples;

import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustContext;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.time.Duration;

/**
 * Example demonstrating how Braintrust project names work with OpenTelemetry.
 * 
 * In Braintrust, traces are organized by project. You can set the project in several ways:
 * 1. Via span attributes (braintrust.parent.project_id)
 * 2. Via BraintrustContext
 * 3. Via environment variable BRAINTRUST_PROJECT_ID
 * 
 * If no project is specified, Braintrust will create a default project based on the service name.
 */
public class ProjectExample {
    
    public static void main(String[] args) {
        try {
            var apiKey = System.getenv("BRAINTRUST_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("Set BRAINTRUST_API_KEY to see traces in Braintrust dashboard");
                System.exit(1);
            }
            
            var config = BraintrustConfig.builder()
                .apiKey(apiKey)
                .build();
            
            // The service name is used as a default if no project is specified
            var openTelemetry = BraintrustTracing.quickstart(config, builder -> builder
                .serviceName("my-ai-application")  // This becomes the default project name
                .resourceAttribute("environment", "production")
                .exportInterval(Duration.ofSeconds(2))
            );
            
            var tracer = BraintrustTracing.getTracer(openTelemetry);
            
            System.out.println("=== Braintrust Project Name Examples ===\n");
            
            // Example 1: Default project (uses service name)
            System.out.println("1. Default project (service name: my-ai-application)");
            var span1 = tracer.spanBuilder("example.default_project")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
            
            try (Scope scope1 = span1.makeCurrent()) {
                span1.setAttribute("info", "This will go to project 'my-ai-application'");
                span1.setStatus(StatusCode.OK);
            } finally {
                span1.end();
            }
            
            // Example 2: Explicit project via span attribute
            System.out.println("2. Explicit project via span attribute");
            var span2 = tracer.spanBuilder("example.explicit_project")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
            
            try (Scope scope2 = span2.makeCurrent()) {
                // Set project ID explicitly
                BraintrustTracing.SpanUtils.setParentProject("customer-support-bot");
                span2.setAttribute("info", "This will go to project 'customer-support-bot'");
                span2.setStatus(StatusCode.OK);
            } finally {
                span2.end();
            }
            
            // Example 3: Using BraintrustContext
            System.out.println("3. Using BraintrustContext for multiple spans");
            var context = BraintrustContext.builder()
                .projectId("recommendation-engine")
                .storeInCurrent();
            
            try (Scope contextScope = context.makeCurrent()) {
                // All spans created within this scope will belong to the recommendation-engine project
                
                var span3a = tracer.spanBuilder("example.context_span_1")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                try (Scope scope3a = span3a.makeCurrent()) {
                    span3a.setAttribute("info", "Part of recommendation-engine project");
                    span3a.setStatus(StatusCode.OK);
                } finally {
                    span3a.end();
                }
                
                var span3b = tracer.spanBuilder("example.context_span_2")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                try (Scope scope3b = span3b.makeCurrent()) {
                    span3b.setAttribute("info", "Also part of recommendation-engine project");
                    span3b.setStatus(StatusCode.OK);
                } finally {
                    span3b.end();
                }
            }
            
            // Example 4: Experiment tracking
            System.out.println("4. Experiment tracking");
            var experimentContext = BraintrustContext.builder()
                .experimentId("exp-12345")  // Experiments are a special type of project
                .storeInCurrent();
            
            try (Scope expScope = experimentContext.makeCurrent()) {
                var span4 = tracer.spanBuilder("example.experiment_run")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
                
                try (Scope scope4 = span4.makeCurrent()) {
                    span4.setAttribute("info", "This is part of experiment exp-12345");
                    BraintrustTracing.SpanUtils.addScore("accuracy", 0.95);
                    span4.setStatus(StatusCode.OK);
                } finally {
                    span4.end();
                }
            }
            
            // Wait for export
            System.out.println("\nWaiting for spans to export...");
            Thread.sleep(3000);
            
            System.out.println("\nExample completed!");
            System.out.println("\nIn Braintrust dashboard, you should see traces in these projects:");
            System.out.println("- my-ai-application (default from service name)");
            System.out.println("- customer-support-bot (explicit project)");
            System.out.println("- recommendation-engine (via context)");
            System.out.println("- Experiment exp-12345 (if it exists)");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}