/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.braintrust.instrumentation.anthropic.otel;

import com.anthropic.core.RequestOptions;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

final class InstrumentedMessageService
        extends DelegatingInvocationHandler<MessageService, InstrumentedMessageService> {

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

    private Message create(MessageCreateParams messageCreateParams, RequestOptions requestOptions) {
        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, messageCreateParams)) {
            return delegate.create(messageCreateParams, requestOptions);
        }

        Context context = instrumenter.start(parentContext, messageCreateParams);
        Message message;
        try (Scope ignored = context.makeCurrent()) {
            message = delegate.create(messageCreateParams, requestOptions);
        } catch (Throwable t) {
            instrumenter.end(context, messageCreateParams, null, t);
            throw t;
        }

        instrumenter.end(context, messageCreateParams, message, null);
        return message;
    }
}
