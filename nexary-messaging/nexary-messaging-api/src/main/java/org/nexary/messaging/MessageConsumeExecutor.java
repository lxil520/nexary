package org.nexary.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Callable;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernanceRejection;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.GovernanceResource.ResourceKind;
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
    private final GovernanceExecution governanceExecution;
    private final String governanceProvider;

    public MessageConsumeExecutor(
            Optional<MessageDeduplicationStore> deduplicationStore,
            Duration deduplicationTtl,
            List<MessageInterceptor> interceptors,
            MessageRetryPolicy retryPolicy,
            MessageDeadLetterPublisher deadLetterPublisher) {
        this(deduplicationStore, deduplicationTtl, interceptors, retryPolicy, deadLetterPublisher,
                NexaryObservationPublisher.noop());
    }

    public MessageConsumeExecutor(
            Optional<MessageDeduplicationStore> deduplicationStore,
            Duration deduplicationTtl,
            List<MessageInterceptor> interceptors,
            MessageRetryPolicy retryPolicy,
            MessageDeadLetterPublisher deadLetterPublisher,
            NexaryObservationPublisher observationPublisher) {
        this(deduplicationStore, deduplicationTtl, interceptors, retryPolicy, deadLetterPublisher,
                observationPublisher, GovernanceExecution.direct(), "core");
    }

    public MessageConsumeExecutor(
            Optional<MessageDeduplicationStore> deduplicationStore,
            Duration deduplicationTtl,
            List<MessageInterceptor> interceptors,
            MessageRetryPolicy retryPolicy,
            MessageDeadLetterPublisher deadLetterPublisher,
            NexaryObservationPublisher observationPublisher,
            GovernanceExecution governanceExecution,
            String governanceProvider) {
        this.deduplicationStore = deduplicationStore;
        this.deduplicationTtl = deduplicationTtl == null ? Duration.ofHours(1) : deduplicationTtl;
        this.interceptors = interceptors == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(interceptors));
        this.retryPolicy = retryPolicy == null ? MessageRetryPolicy.defaults() : retryPolicy;
        this.deadLetterPublisher = deadLetterPublisher == null ? MessageDeadLetterPublisher.inMemory() : deadLetterPublisher;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
        this.governanceExecution = governanceExecution == null ? GovernanceExecution.direct() : governanceExecution;
        this.governanceProvider = hasText(governanceProvider) ? governanceProvider.trim() : "core";
    }

    /** Consumes an envelope through the configured cross-cutting policies. */
    public <T> MessageConsumeResult consume(MessageEnvelope<T> envelope, MessageConsumer<T> consumer) {
        return consume(envelope, "default", consumer);
    }

    /** Consumes an envelope with the provider-neutral consumer group included in terminal failure records. */
    public <T> MessageConsumeResult consume(MessageEnvelope<T> envelope, String consumerGroup, MessageConsumer<T> consumer) {
        Optional<MessageConsumeResult> expired = MessageGovernanceSupport.rejectExpiredConsume(
                envelope, "core", observationPublisher);
        if (expired.isPresent()) {
            return expired.get();
        }
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
                    observationPublisher, "dedup.claim", "core", "failure", Collections.emptyMap(), ex);
            throw ex;
        }
        if (deduplicationStore.isPresent() && !claim.isPresent()) {
            MessageObservationSupport.publish(
                    observationPublisher, "dedup.claim", "core", "duplicate");
            return MessageConsumeResult.duplicate("message already consumed: " + prepared.messageId());
        }

        Throwable error = null;
        try {
            executeGoverned(prepared, "consume", () -> {
                consumer.handle(prepared);
                return null;
            });
            claim.ifPresent(MessageDeduplicationClaim::complete);
            attempts.remove(prepared.messageId());
            MessageObservationSupport.publish(
                    observationPublisher, "handler", "core", "success");
            return MessageConsumeResult.success();
        } catch (Throwable ex) {
            error = ex;
            MessageObservationSupport.publish(
                    observationPublisher, "handler", "core", "failure", Collections.emptyMap(), ex);
            if (ex instanceof GovernanceRejection) {
                attempts.remove(prepared.messageId());
                MessageConsumeResult result = MessageConsumeResult.failed(ex.getMessage());
                MessageGovernanceSupport.publishRetryStoppedIfStop(
                        prepared, "core", "consume", result.retrySignal(), observationPublisher);
                return result;
            }
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
            MessageConsumeResult result = MessageConsumeResult.deadLetter(record);
            MessageGovernanceSupport.publishRetryStoppedIfStop(
                    envelope, "core", "consume", result.retrySignal(), observationPublisher);
            MessageGovernanceSupport.publishDegraded(envelope, "core", "consume", observationPublisher);
            return result;
        } catch (RuntimeException dlqError) {
            MessageObservationSupport.publish(
                    observationPublisher,
                    "deadletter.publish",
                    "core",
                    "failure",
                    MessageObservationSupport.terminalTags(record.status().name()),
                    dlqError);
            MessageConsumeResult result = MessageConsumeResult.failed(dlqError.getMessage());
            MessageGovernanceSupport.publishRetryStoppedIfStop(
                    envelope, "core", "consume", result.retrySignal(), observationPublisher);
            return result;
        }
    }

    private Object executeGoverned(MessageEnvelope<?> envelope, String operation, Callable<?> action) throws Exception {
        return governanceExecution.execute(governanceContext(envelope, operation), action);
    }

    private GovernanceContext governanceContext(MessageEnvelope<?> envelope, String operation) {
        GovernanceContext.Builder builder = GovernanceContext.builder()
                .resource(new GovernanceResource(
                        ResourceKind.MESSAGING,
                        "message-" + operation,
                        governanceProvider,
                        operation));
        if (envelope != null) {
            builder.trafficTag(envelope.trafficTag());
        }
        effectiveDeadline(envelope).ifPresent(builder::deadline);
        return builder.build();
    }

    private Optional<java.time.Instant> effectiveDeadline(MessageEnvelope<?> envelope) {
        java.time.Instant envelopeDeadline = envelope == null ? null : envelope.deadline();
        Optional<java.time.Instant> currentDeadline = GovernanceContext.current().flatMap(GovernanceContext::deadline);
        if (envelopeDeadline == null) {
            return currentDeadline;
        }
        if (!currentDeadline.isPresent()) {
            return Optional.of(envelopeDeadline);
        }
        java.time.Instant inherited = currentDeadline.get();
        return Optional.of(envelopeDeadline.isBefore(inherited) ? envelopeDeadline : inherited);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
