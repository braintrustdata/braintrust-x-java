package dev.braintrust.examples;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.instrumentation.openai.BraintrustOpenAI;
import dev.braintrust.trace.BraintrustTracing;

/** Basic OTel + OpenAI instrumentation example */
public class OpenAIInstrumentationExample {
    public static void main(String[] args) throws Exception {
        if (null == System.getenv("OPENAI_API_KEY")) {
            System.err.println(
                    "\nWARNING envar OPEN_AI_API_KEY not found. This example will likely fail.\n");
        }
        var braintrustConfig = BraintrustConfig.fromEnvironment();
        var openTelemetry = BraintrustTracing.quickstart(braintrustConfig, true);
        var tracer = BraintrustTracing.getTracer(openTelemetry);
        OpenAIClient openAIClient =
                BraintrustOpenAI.wrapOpenAI(openTelemetry, OpenAIOkHttpClient.fromEnv());
        var rootSpan = tracer.spanBuilder("java-braintrust-example").startSpan();
        try (var ignored = rootSpan.makeCurrent()) {
            Thread.sleep(70); // Not required. This is just to make the span look interesting
            var request =
                    ChatCompletionCreateParams.builder()
                            .model(ChatModel.GPT_4O_MINI)
                            .addSystemMessage("You are a helpful assistant")
                            .addUserMessage("What is the capital of France?")
                            .temperature(0.0)
                            .build();
            var response = openAIClient.chat().completions().create(request);
            System.out.println("~~~ GOT RESPONSE: " + response);
            openAIClient.completions();
            Thread.sleep(30); // Not required. This is just to make the span look interesting
        } finally {
            rootSpan.end();
        }
        var url =
                braintrustConfig.fetchProjectURI()
                        + "/logs?r=%s&s=%s"
                                .formatted(
                                        rootSpan.getSpanContext().getSpanId(),
                                        rootSpan.getSpanContext().getSpanId());
        System.out.println("\n\n  Example complete! View your data in Braintrust: " + url);
    }
}
