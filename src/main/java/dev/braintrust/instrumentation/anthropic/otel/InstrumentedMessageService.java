package dev.braintrust.instrumentation.anthropic.otel;

import com.anthropic.core.RequestOptions;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;

final class InstrumentedMessageService
        extends DelegatingInvocationHandler<MessageService, InstrumentedMessageService> {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final Instrumenter<MessageCreateParams, Message> instrumenter;
    private final Logger eventLogger;
    private final boolean captureMessageContent;

    InstrumentedMessageService(
            MessageService delegate,
            Instrumenter<MessageCreateParams, Message> instrumenter,
            Logger eventLogger,
            boolean captureMessageContent) {
        super(delegate);
        this.instrumenter = instrumenter;
        this.eventLogger = eventLogger;
        this.captureMessageContent = captureMessageContent;
    }

    @Override
    protected Class<MessageService> getProxyType() {
        return MessageService.class;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();

        if (methodName.equals("create")) {
            if (parameterTypes.length >= 1 && parameterTypes[0] == MessageCreateParams.class) {
                if (parameterTypes.length == 1) {
                    return create((MessageCreateParams) args[0], RequestOptions.none());
                } else if (parameterTypes.length == 2
                        && parameterTypes[1] == RequestOptions.class) {
                    return create((MessageCreateParams) args[0], (RequestOptions) args[1]);
                }
            }
        }

        return super.invoke(proxy, method, args);
    }

    @SneakyThrows
    private Message create(MessageCreateParams inputMessage, RequestOptions requestOptions) {
        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, inputMessage)) {
            return delegate.create(inputMessage, requestOptions);
        }

        Context context = instrumenter.start(parentContext, inputMessage);
        Message outputMessage;
        try (Scope ignored = context.makeCurrent()) {
            List<MessageParam> inputMessages = new ArrayList<>(inputMessage.messages());
            // Put system in the input message so the backend will pick it up in the LLM display
            if (inputMessage.system().isPresent()) {
                inputMessages.add(
                        0,
                        MessageParam.builder()
                                .role(MessageParam.Role.of("system"))
                                .content(inputMessage.system().get().asString())
                                .build());
            }
            Span.current()
                    .setAttribute(
                            "braintrust.input_json", JSON_MAPPER.writeValueAsString(inputMessages));
            outputMessage = delegate.create(inputMessage, requestOptions);
            Span.current()
                    .setAttribute(
                            "braintrust.output_json",
                            JSON_MAPPER.writeValueAsString(new Message[] {outputMessage}));
        } catch (Throwable t) {
            instrumenter.end(context, inputMessage, null, t);
            throw t;
        }

        instrumenter.end(context, inputMessage, outputMessage, null);
        return outputMessage;
    }

    private static String contentToString(MessageCreateParams.System content) {
        if (content.isString()) {
            return content.asString();
        } else if (content.isTextBlockParams()) {
            return content.asTextBlockParams().stream()
                    .map(TextBlockParam::text)
                    .collect(Collectors.joining());
        }
        return "";
    }
}
