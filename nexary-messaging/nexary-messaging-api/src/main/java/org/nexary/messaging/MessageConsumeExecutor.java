package org.nexary.messaging;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.core.retry.RetrySignal;

/** Runs provider-neutral consume concerns such as interceptors, retries and duplicate protection. */
public class MessageConsumeExecutor {
    private final Optional<MessageDeduplicationStore> deduplicationStore;
    private final Duration deduplicationTtl;
    private final List<MessageInterceptor> interceptors;
    private final MessageRetryPolicy retryPolicy;
    private final MessageDeadLetterPublisher deadLetterPublisher;
    private final ConcurrentMap<String, Integer> attempts = new ConcurrentHashMap<>();
    private final NexaryObservationPublisher observationPublisher;

    public MessageConsumeExecutor(
            Optional<MessageDeduplicationStore> deduplicationStore,
            Duration deduplicationTtl,
            List<MessageInterceptor> interceptors,
            MessageRetryPolicy retryPolicy,
            MessageDeadLetterPublisher deadLetterPublisher) {
        this(deduplicationStore, deduplicationTtl, interceptors, retryPolicy, deadLetterPublisher, NexaryObservationPublisher.noop());
    }

    public MessageConsumeExecutor(
            Optional<MessageDeduplicationStore> deduplicationStore,
            Duration deduplicationTtl,
            List<MessageInterceptor> interceptors,
            MessageRetryPolicy retryPolicy,
            MessageDeadLetterPublisher deadLetterPublisher,
            NexaryObservationPublisher observationPublisher) {
        this.deduplicationStore = deduplicationStore;
        this.deduplicationTtl = deduplicationTtl == null ? Duration.ofHours(1) : deduplicationTtl;
        this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
        this.retryPolicy = retryPolicy == null ? MessageRetryPolicy.defaults() : retryPolicy;
        this.deadLetterPublisher = deadLetterPublisher == null ? MessageDeadLetterPublisher.inMemory() : deadLetterPublisher;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Consumes an envelope through the configured cross-cutting policies. */
    public <T> MessageConsumeResult consume(MessageEnvelope<T> envelope, MessageConsumer<T> consumer) {
        return consume(envelope, "default", consumer);
    }

    /** Consumes an envelope with the provider-neutral consumer group included in terminal failure records. */
    public <T> MessageConsumeResult consume(MessageEnvelope<T> envelope, String consumerGroup, MessageConsumer<T> consumer) {
        MessageEnvelope<T> current = envelope;
        for (MessageInterceptor interceptor : interceptors) {
            current = interceptor.beforeConsume(current);
        }
        MessageEnvelope<T> prepared = current;

        Optional<MessageDeduplicationClaim> claim;
        try {
            claim = deduplicationStore.flatMap(store -> store.claim(prepared.messageId(), deduplicationTtl));
            if (deduplicationStore.isPresent() && claim.isPresent()) {
                MessageObservationSupport.publish(
                        observationPublisher, "dedup.claim", "core", "success");
            }
        } catch (RuntimeException ex) {
            MessageObservationSupport.publish(
                    observationPublisher, "dedup.claim", "core", "failure", Map.of(), ex);
            throw ex;
        }
        if (deduplicationStore.isPresent() && claim.isEmpty()) {
            MessageObservationSupport.publish(
                    observationPublisher, "dedup.claim", "core", "duplicate");
            return MessageConsumeResult.duplicate("message already consumed: " + prepared.messageId());
        }

        Throwable error = null;
        try {
            consumer.handle(prepared);
            claim.ifPresent(MessageDeduplicationClaim::complete);
            attempts.remove(prepared.messageId());
            MessageObservationSupport.publish(
                    observationPublisher, "handler", "core", "success");
            return MessageConsumeResult.success();
        } catch (Throwable ex) {
            error = ex;
            MessageObservationSupport.publish(
                    observationPublisher, "handler", "core", "failure", Map.of(), ex);
            return onFailure(prepared, consumerGroup, claim, ex);
        } finally {
            claim.ifPresent(MessageDeduplicationClaim::close);
            for (MessageInterceptor interceptor : interceptors) {
                interceptor.afterConsume(prepared, error);
            }
        }
    }

    private MessageConsumeResult onFailure(
            MessageEnvelope<?> envelope,
            String consumerGroup,
            Optional<MessageDeduplicationClaim> claim,
            Throwable error) {
        int failedAttempt = attempts.merge(envelope.messageId(), 1, Integer::sum);
        if (retryPolicy.allowsRetryAfter(failedAttempt)) {
            Duration backoff = retryPolicy.backoffFor(failedAttempt);
            MessageObservationSupport.publish(
                    observationPublisher,
                    "retry.schedule",
                    "core",
                    "retry",
                    MessageObservationSupport.retryTags(failedAttempt + 1),
                    error);
            return MessageConsumeResult.retry(
                    error.getMessage(),
                    RetrySignal.retry(failedAttempt + 1, backoff, error.getMessage()));
        }

        MessageDeadLetterRecord record =
                MessageDeadLetterRecord.retryExhausted(envelope, consumerGroup, failedAttempt, error);
        try {
            deadLetterPublisher.publish(record);
            claim.ifPresent(MessageDeduplicationClaim::complete);
            attempts.remove(envelope.messageId());
            MessageObservationSupport.publish(
                    observationPublisher,
                    "deadletter.publish",
                    "core",
                    "success",
                    MessageObservationSupport.terminalTags(record.status().name()),
                    error);
            return MessageConsumeResult.deadLetter(record);
        } catch (RuntimeException dlqError) {
            MessageObservationSupport.publish(
                    observationPublisher,
                    "deadletter.publish",
                    "core",
                    "failure",
                    MessageObservationSupport.terminalTags(record.status().name()),
                    dlqError);
            return MessageConsumeResult.failed(dlqError.getMessage());
        }
    }
}
