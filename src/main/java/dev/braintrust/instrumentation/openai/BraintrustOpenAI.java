package dev.braintrust.instrumentation.openai;

import com.openai.client.OpenAIClient;
import dev.braintrust.instrumentation.openai.otel.OpenAITelemetry;
import io.opentelemetry.api.OpenTelemetry;

public class BraintrustOpenAI {
    /** Instrument openai client with braintrust traces */
    public static OpenAIClient wrapOpenAI(OpenTelemetry openTelemetry, OpenAIClient openAIClient) {
        if ("true".equalsIgnoreCase(System.getenv("BRAINTRUST_X_OTEL_LOGS"))) {
            return io.opentelemetry.instrumentation.openai.v1_1.OpenAITelemetry.builder(
                            openTelemetry)
                    .setCaptureMessageContent(true)
                    .build()
                    .wrap(openAIClient);
        } else {
            return OpenAITelemetry.builder(openTelemetry)
                    .setCaptureMessageContent(true)
                    .build()
                    .wrap(openAIClient);
        }
    }
}
