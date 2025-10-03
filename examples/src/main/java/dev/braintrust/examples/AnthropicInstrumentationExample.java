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
            Thread.sleep(70); // just to make span look interesting

            var request =
                    MessageCreateParams.builder()
                            .model(Model.CLAUDE_3_5_HAIKU_20241022)
                            .system("You are the world's greatest philosopher")
                            .addUserMessage("What's the meaning of life? Be very brief.")
                            .maxTokens(50)
                            .temperature(0.0)
                            .build();

            var response = anthropicClient.messages().create(request);
            System.out.println("~~~ GOT RESPONSE: " + response);

            Thread.sleep(30); // not required, just to show span duration
        } finally {
            rootSpan.end();
        }

        var url =
                braintrustConfig.fetchProjectURI()
                        + "/logs?r=%s&s=%s"
                                .formatted(
                                        rootSpan.getSpanContext().getTraceId(),
                                        rootSpan.getSpanContext().getSpanId());

        System.out.println("\n\n  Example complete! View your data in Braintrust: " + url);
    }
}
