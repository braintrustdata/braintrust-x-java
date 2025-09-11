package dev.braintrust.examples;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.braintrust.claude.openai.BraintrustOpenAI;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.instrumentation.openai.OpenAIInterceptor;
import dev.braintrust.trace.BraintrustTracing;

import java.util.List;


/**
 * Basic OTel + OpenAI instrumentation example
 */
public class OpenAIInstrumentationExample {
    public static void main(String[] args) throws Exception {
        var config = BraintrustConfig.builder().build();
        var openTelemetry = BraintrustTracing.quickstart(config);
        var tracer = BraintrustTracing.getTracer(openTelemetry);
        OpenAIClient openAIClient = OpenAIInterceptor.wrapOpenAI(openTelemetry, OpenAIOkHttpClient.fromEnv());
        var systemMessage = "You are a helpful assistant";

        var span = tracer.spanBuilder("open-ai-instrumentation").startSpan();
        try (var ignored = span.makeCurrent()) {
            Thread.sleep(70);
            var question = "What is the capital of France?";
            var request =
                    ChatCompletionCreateParams.builder()
                            .model(ChatModel.GPT_4O_MINI) // Using GPT-4o-mini for cost efficiency
                            .addSystemMessage((String) systemMessage)
                            .addUserMessage("What is the capital of France?")
                            .maxTokens(50L)
                            .temperature(0.0)
                            .build();
            // Use the wrapped client which will handle all tracing
            // var response = openAIClient.chat().completions().create(request, messages);
            var response = openAIClient.chat().completions().create(request);
            System.out.println(response);
            openAIClient.completions();
            Thread.sleep(30);
        } finally {
            span.end();
        }
        System.out.println("example completed!");
    }
}
