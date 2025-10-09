package dev.braintrust.instrumentation.anthropic.otel;

import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

final class TracingStreamedResponse implements StreamResponse<RawMessageStreamEvent> {

    private final StreamResponse<RawMessageStreamEvent> delegate;
    private final StreamListener listener;

    TracingStreamedResponse(
            StreamResponse<RawMessageStreamEvent> delegate, StreamListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public Stream<RawMessageStreamEvent> stream() {
        return StreamSupport.stream(new TracingSpliterator(delegate.stream().spliterator()), false);
    }

    @Override
    public void close() {
        listener.endSpan(null);
        delegate.close();
    }

    private class TracingSpliterator implements Spliterator<RawMessageStreamEvent> {

        private final Spliterator<RawMessageStreamEvent> delegateSpliterator;

        private TracingSpliterator(Spliterator<RawMessageStreamEvent> delegateSpliterator) {
            this.delegateSpliterator = delegateSpliterator;
        }

        @Override
        public boolean tryAdvance(Consumer<? super RawMessageStreamEvent> action) {
            boolean eventReceived =
                    delegateSpliterator.tryAdvance(
                            event -> {
                                listener.onEvent(event);
                                action.accept(event);
                            });
            if (!eventReceived) {
                listener.endSpan(null);
            }
            return eventReceived;
        }

        @Override
        @Nullable
        public Spliterator<RawMessageStreamEvent> trySplit() {
            // do not support parallelism to reliably catch the last event
            return null;
        }

        @Override
        public long estimateSize() {
            return delegateSpliterator.estimateSize();
        }

        @Override
        public long getExactSizeIfKnown() {
            return delegateSpliterator.getExactSizeIfKnown();
        }

        @Override
        public int characteristics() {
            return delegateSpliterator.characteristics();
        }

        @Override
        public Comparator<? super RawMessageStreamEvent> getComparator() {
            return delegateSpliterator.getComparator();
        }
    }
}
