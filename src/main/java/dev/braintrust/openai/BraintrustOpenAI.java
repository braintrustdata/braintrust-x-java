package dev.braintrust.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.List;

/**
 * Wrapper for OpenAI client that adds Braintrust-specific OpenTelemetry tracing.
 *
 * <p>This follows the same semantic conventions as the Go SDK's wrap_openai functionality.
 */
public class BraintrustOpenAI {

    private final OpenAIClient client;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    // Attribute keys matching Go SDK conventions
    private static final AttributeKey<String> ATTR_INPUT =
            AttributeKey.stringKey("braintrust.input");
    private static final AttributeKey<String> ATTR_OUTPUT =
            AttributeKey.stringKey("braintrust.output");
    private static final AttributeKey<String> ATTR_METADATA =
            AttributeKey.stringKey("braintrust.metadata");
    private static final AttributeKey<String> ATTR_METRICS =
            AttributeKey.stringKey("braintrust.metrics");

    // Individual metric attributes (for compatibility with Java SDK patterns)
    private static final AttributeKey<Long> ATTR_PROMPT_TOKENS =
            AttributeKey.longKey("braintrust.usage.prompt_tokens");
    private static final AttributeKey<Long> ATTR_COMPLETION_TOKENS =
            AttributeKey.longKey("braintrust.usage.completion_tokens");
    private static final AttributeKey<Long> ATTR_TOTAL_TOKENS =
            AttributeKey.longKey("braintrust.usage.total_tokens");

    public BraintrustOpenAI(OpenAIClient client, Tracer tracer) {
        this.client = client;
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper();
    }

    public class Chat {
        public Completions completions() {
            return new Completions();
        }
    }

    public class Completions {
        /**
         * Enhanced create method that adds OpenTelemetry tracing with Braintrust semantic
         * conventions.
         *
         * @param params The chat completion parameters
         * @param messages List of message contents in order (system, user, etc.) - needed because
         *     the SDK doesn't expose message details
         * @return The chat completion response
         */
        public ChatCompletion create(
                ChatCompletionCreateParams params, List<MessageInfo> messages) {
            // Create span with proper naming convention
            var span =
                    tracer.spanBuilder("openai.chat.completions.create")
                            .setSpanKind(SpanKind.CLIENT)
                            .startSpan();

            try (Scope scope = span.makeCurrent()) {
                // Prepare input structure - matches Go SDK format exactly
                var inputArray = objectMapper.createArrayNode();
                for (var message : messages) {
                    var messageNode = objectMapper.createObjectNode();
                    messageNode.put("role", message.role);
                    messageNode.put("content", message.content);
                    inputArray.add(messageNode);
                }

                // Set braintrust.input as JSON string (matching Go SDK)
                span.setAttribute(ATTR_INPUT, inputArray.toString());

                // Prepare metadata
                var metadataNode = objectMapper.createObjectNode();
                metadataNode.put("provider", "openai");
                metadataNode.put("endpoint", "/v1/chat/completions");
                metadataNode.put("model", params.model().toString());
                params.temperature().ifPresent(temp -> metadataNode.put("temperature", temp));
                params.topP().ifPresent(topP -> metadataNode.put("top_p", topP));
                params.maxTokens().ifPresent(max -> metadataNode.put("max_tokens", max));
                params.frequencyPenalty()
                        .ifPresent(penalty -> metadataNode.put("frequency_penalty", penalty));
                params.presencePenalty()
                        .ifPresent(penalty -> metadataNode.put("presence_penalty", penalty));
                params.n().ifPresent(n -> metadataNode.put("n", n));
                metadataNode.put("stream", false); // Always false for non-streaming

                // Make the actual API call
                var response = client.chat().completions().create(params);

                // Add response metadata
                metadataNode.put("id", response.id());
                metadataNode.put("object", "chat.completion");
                metadataNode.put("created", response.created());
                response.systemFingerprint()
                        .ifPresent(fp -> metadataNode.put("system_fingerprint", fp));

                // Set braintrust.metadata as JSON string
                span.setAttribute(ATTR_METADATA, metadataNode.toString());

                // Prepare output structure - matches Go SDK format exactly
                var outputArray = objectMapper.createArrayNode();
                for (var choice : response.choices()) {
                    var choiceNode = objectMapper.createObjectNode();
                    choiceNode.put("index", choice.index());

                    var messageNode = objectMapper.createObjectNode();
                    messageNode.put("role", "assistant");
                    choice.message()
                            .content()
                            .ifPresent(content -> messageNode.put("content", content));
                    messageNode.putNull("tool_calls"); // Explicitly null for non-tool responses

                    choiceNode.set("message", messageNode);
                    choiceNode.putNull("logprobs"); // Explicitly null when not requested
                    if (choice.finishReason() != null) {
                        choiceNode.put(
                                "finish_reason", choice.finishReason().toString().toLowerCase());
                    }
                    outputArray.add(choiceNode);
                }

                // Set braintrust.output as JSON string
                span.setAttribute(ATTR_OUTPUT, outputArray.toString());

                // Set metrics - both as JSON and individual attributes
                if (response.usage().isPresent()) {
                    var usage = response.usage().get();

                    // As JSON (matching Go SDK)
                    var metricsNode = objectMapper.createObjectNode();
                    metricsNode.put("prompt_tokens", usage.promptTokens());
                    metricsNode.put("completion_tokens", usage.completionTokens());
                    metricsNode.put("tokens", usage.totalTokens());
                    span.setAttribute(ATTR_METRICS, metricsNode.toString());

                    // As individual attributes (for Java SDK compatibility)
                    span.setAttribute(ATTR_PROMPT_TOKENS, usage.promptTokens());
                    span.setAttribute(ATTR_COMPLETION_TOKENS, usage.completionTokens());
                    span.setAttribute(ATTR_TOTAL_TOKENS, usage.totalTokens());
                }

                span.setStatus(StatusCode.OK);
                return response;

            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage());
                throw new RuntimeException("Failed to call OpenAI API", e);
            } finally {
                span.end();
            }
        }

        /**
         * Simpler create method that doesn't require message info (but won't have input details).
         */
        public ChatCompletion create(ChatCompletionCreateParams params) {
            return create(params, List.of());
        }
    }

    public Chat chat() {
        return new Chat();
    }

    /** Helper class to pass message information since the SDK doesn't expose it. */
    public static class MessageInfo {
        public final String role;
        public final String content;

        public MessageInfo(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public static MessageInfo system(String content) {
            return new MessageInfo("system", content);
        }

        public static MessageInfo user(String content) {
            return new MessageInfo("user", content);
        }

        public static MessageInfo assistant(String content) {
            return new MessageInfo("assistant", content);
        }
    }

    /**
     * Creates a wrapped OpenAI client with Braintrust tracing.
     *
     * @param client The OpenAI client to wrap
     * @param tracer The OpenTelemetry tracer to use
     * @return A wrapped client that adds tracing
     */
    public static BraintrustOpenAI wrap(OpenAIClient client, Tracer tracer) {
        return new BraintrustOpenAI(client, tracer);
    }
}
