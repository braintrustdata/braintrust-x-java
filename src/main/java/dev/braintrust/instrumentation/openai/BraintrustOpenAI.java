package dev.braintrust.instrumentation.openai;

import com.openai.client.OpenAIClient;
import dev.braintrust.instrumentation.openai.otel.OpenAITelemetry;
import io.opentelemetry.api.OpenTelemetry;

public class BraintrustOpenAI {
    public static OpenAIClient wrapOpenAI(OpenTelemetry openTelemetry, OpenAIClient openAIClient) {
        var oaiTel = OpenAITelemetry.builder(openTelemetry)
                .setCaptureMessageContent(true)
                .build();
        return  oaiTel.wrap(openAIClient);
    }
}
