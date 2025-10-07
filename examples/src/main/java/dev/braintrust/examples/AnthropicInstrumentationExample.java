package dev.braintrust.examples;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.instrumentation.anthropic.BraintrustAnthropic;
import dev.braintrust.trace.BraintrustTracing;

/** Basic OTel + Anthropic instrumentation example */
public class AnthropicInstrumentationExample {
    public static void main(String[] args) throws Exception {
        if (null == System.getenv("ANTHROPIC_API_KEY")) {
            System.err.println(
                    "\n"
                            + "WARNING envar ANTHROPIC_API_KEY not found. This example will likely"
                            + " fail.\n");
        }

        var braintrustConfig = BraintrustConfig.fromEnvironment();
        var openTelemetry = BraintrustTracing.of(braintrustConfig, true);
        var tracer = BraintrustTracing.getTracer(openTelemetry);

        // Wrap Anthropic client with Braintrust instrumentation
        AnthropicClient anthropicClient =
                BraintrustAnthropic.wrap(openTelemetry, AnthropicOkHttpClient.fromEnv());

        var rootSpan = tracer.spanBuilder("anthropic-java-instrumentation-example").startSpan();
        try (var ignored = rootSpan.makeCurrent()) {
            messagesApiExample(anthropicClient);
            // streaming instrumentation coming soon
            // messagesStreamingExample(anthropicClient);
        } finally {
            rootSpan.end();
        }

        var url =
                braintrustConfig.fetchProjectURI()
                        + "/logs?r=%s&s=%s"
                                .formatted(
                                        rootSpan.getSpanContext().getTraceId(),
                                        rootSpan.getSpanContext().getSpanId());

        System.out.println(
                "\n\n  Example complete! View your data in Braintrust: %s\n".formatted(url));
    }

    private static void messagesApiExample(AnthropicClient anthropicClient) {
        var request =
                MessageCreateParams.builder()
                        .model(Model.CLAUDE_3_5_HAIKU_20241022)
                        .system("Use as few words as possible in your answers")
                        .addUserMessage("Who was the first president of the United States?")
                        .maxTokens(50)
                        .temperature(0.0)
                        .build();
        var response = anthropicClient.messages().create(request);
        System.out.println("\n~~~ MESSAGES RESPONSE: %s\n".formatted(response));
    }

    private static void messagesStreamingExample(AnthropicClient anthropicClient) {
        var request =
                MessageCreateParams.builder()
                        .model(Model.CLAUDE_3_5_HAIKU_20241022)
                        .system("Use as few words as possible in your answers")
                        .addUserMessage("Who was the first president of the United States?")
                        .maxTokens(50)
                        .temperature(0.0)
                        .build();

        System.out.println("\n~~~ STREAMING RESPONSE:");
        try (var stream = anthropicClient.messages().createStreaming(request)) {
            stream.stream().forEach(System.out::print);
        }
        System.out.println("\n");
    }
}
