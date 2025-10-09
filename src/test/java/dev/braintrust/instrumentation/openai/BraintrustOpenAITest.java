package dev.braintrust.instrumentation.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static dev.braintrust.trace.BraintrustTracingTest.getExportedBraintrustSpans;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import dev.braintrust.config.BraintrustConfig;
import dev.braintrust.trace.BraintrustTracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class BraintrustOpenAITest {
    private static final ObjectMapper JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final BraintrustConfig config =
            BraintrustConfig.of(
                    "BRAINTRUST_API_KEY", "foobar",
                    "BRAINTRUST_DEFAULT_PROJECT_NAME", "unit-test-project",
                    "BRAINTRUST_JAVA_EXPORT_SPANS_IN_MEMORY_FOR_UNIT_TEST", "true");

    @BeforeEach
    void beforeEach() {
        GlobalOpenTelemetry.resetForTest();
        getExportedBraintrustSpans().clear();
        wireMock.resetAll();
    }

    @Test
    @SneakyThrows
    void testWrapOpenAi() {
        // Mock the OpenAI API response
        wireMock.stubFor(
                post(urlEqualTo("/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "id": "chatcmpl-test123",
                                  "object": "chat.completion",
                                  "created": 1677652288,
                                  "model": "gpt-4o-mini",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "message": {
                                        "role": "assistant",
                                        "content": "The capital of France is Paris."
                                      },
                                      "finish_reason": "stop"
                                    }
                                  ],
                                  "usage": {
                                    "prompt_tokens": 20,
                                    "completion_tokens": 8,
                                    "total_tokens": 28
                                  }
                                }
                                """)));

        var openTelemetry = (OpenTelemetrySdk) BraintrustTracing.of(config, true);

        // Create OpenAI client pointing to WireMock server
        OpenAIClient openAIClient =
                OpenAIOkHttpClient.builder()
                        .baseUrl("http://localhost:" + wireMock.getPort())
                        .apiKey("test-api-key")
                        .build();

        // Wrap with Braintrust instrumentation
        openAIClient = BraintrustOpenAI.wrapOpenAI(openTelemetry, openAIClient);

        var request =
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O_MINI)
                        .addSystemMessage("You are a helpful assistant")
                        .addUserMessage("What is the capital of France?")
                        .temperature(0.0)
                        .build();

        var response = openAIClient.chat().completions().create(request);

        // Verify the response
        assertNotNull(response);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));
        assertEquals("chatcmpl-test123", response.id());
        assertEquals(
                "The capital of France is Paris.",
                response.choices().get(0).message().content().get());

        // Verify spans were exported
        assertTrue(
                openTelemetry
                        .getSdkTracerProvider()
                        .forceFlush()
                        .join(10, TimeUnit.SECONDS)
                        .isSuccess());
        var spanData =
                getExportedBraintrustSpans().get(config.getBraintrustParentValue().orElseThrow());
        assertNotNull(spanData);
        assertEquals(1, spanData.size());
        var span = spanData.get(0);

        assertEquals("openai", span.getAttributes().get(AttributeKey.stringKey("gen_ai.system")));
        assertEquals(
                "gpt-4o-mini",
                span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
        assertEquals(
                "gpt-4o-mini",
                span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.model")));
        assertEquals(
                "[stop]",
                span.getAttributes()
                        .get(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons"))
                        .toString());
        assertEquals(
                "chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
        assertEquals(
                "chatcmpl-test123",
                span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id")));
        assertEquals(
                "[{\"content\":\"You are a helpful"
                    + " assistant\",\"role\":\"system\",\"valid\":true},{\"content\":\"What is the"
                    + " capital of France?\",\"role\":\"user\",\"valid\":true}]",
                span.getAttributes().get(AttributeKey.stringKey("braintrust.input_json")));
        assertEquals(
                "project_name:unit-test-project",
                span.getAttributes().get(AttributeKey.stringKey("braintrust.parent")));
        assertEquals(
                20L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertEquals(
                8L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
        assertEquals(
                0.0,
                span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.temperature")));

        String outputJson =
                span.getAttributes().get(AttributeKey.stringKey("braintrust.output_json"));
        assertNotNull(outputJson);
        var outputMessages = JSON_MAPPER.readTree(outputJson);
        assertEquals(1, outputMessages.size());
        var messageZero = outputMessages.get(0);
        assertEquals("The capital of France is Paris.", messageZero.get("content").asText());

        assertEquals(
                "chatcmpl-test123",
                span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id")));
    }

    @Test
    @SneakyThrows
    void testWrapOpenAiStreaming() {
        // Mock the OpenAI API streaming response
        String streamingResponse =
                """
                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":"The"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":" capital"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":" of"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":" France"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":" is"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":" Paris"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":"."},"finish_reason":null}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: {"id":"chatcmpl-test123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o-mini","choices":[],"usage":{"prompt_tokens":20,"completion_tokens":8,"total_tokens":28}}

                data: [DONE]

                """;

        wireMock.stubFor(
                post(urlEqualTo("/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(streamingResponse)));

        var openTelemetry = (OpenTelemetrySdk) BraintrustTracing.of(config, true);

        // Create OpenAI client pointing to WireMock server
        OpenAIClient openAIClient =
                OpenAIOkHttpClient.builder()
                        .baseUrl("http://localhost:" + wireMock.getPort())
                        .apiKey("test-api-key")
                        .build();

        // Wrap with Braintrust instrumentation
        openAIClient = BraintrustOpenAI.wrapOpenAI(openTelemetry, openAIClient);

        var request =
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O_MINI)
                        .addSystemMessage("You are a helpful assistant")
                        .addUserMessage("What is the capital of France?")
                        .temperature(0.0)
                        .streamOptions(
                                ChatCompletionStreamOptions.builder().includeUsage(true).build())
                        .build();

        // Consume the stream
        StringBuilder fullResponse = new StringBuilder();
        try (var stream = openAIClient.chat().completions().createStreaming(request)) {
            stream.stream()
                    .forEach(
                            chunk -> {
                                if (!chunk.choices().isEmpty()) {
                                    chunk.choices()
                                            .get(0)
                                            .delta()
                                            .content()
                                            .ifPresent(fullResponse::append);
                                }
                            });
        }

        // Verify the response
        assertEquals("The capital of France is Paris.", fullResponse.toString());
        wireMock.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));

        // Verify spans were exported
        assertTrue(
                openTelemetry
                        .getSdkTracerProvider()
                        .forceFlush()
                        .join(10, TimeUnit.SECONDS)
                        .isSuccess());
        var spanData =
                getExportedBraintrustSpans().get(config.getBraintrustParentValue().orElseThrow());
        assertNotNull(spanData);
        assertEquals(1, spanData.size());
        var span = spanData.get(0);

        // Verify span attributes
        assertEquals("openai", span.getAttributes().get(AttributeKey.stringKey("gen_ai.system")));
        assertEquals(
                "gpt-4o-mini",
                span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
        assertEquals(
                "gpt-4o-mini",
                span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.model")));
        assertEquals(
                "[stop]",
                span.getAttributes()
                        .get(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons"))
                        .toString());
        assertEquals(
                "chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
        assertEquals(
                "chatcmpl-test123",
                span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id")));

        // Verify usage metrics were captured
        assertEquals(
                20L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertEquals(
                8L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));

        // Verify output JSON was captured
        String outputJson =
                span.getAttributes().get(AttributeKey.stringKey("braintrust.output_json"));
        assertNotNull(outputJson);
        var outputMessages = JSON_MAPPER.readTree(outputJson);
        assertEquals(1, outputMessages.size());
        var messageZero = outputMessages.get(0);
        assertEquals("The capital of France is Paris.", messageZero.get("content").asText());
    }
}
