package org.nexary.messaging;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import org.nexary.core.context.DeadlineContext;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernanceObservationEvents;
import org.nexary.core.governance.GovernanceRejection;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.GovernanceResource.ResourceKind;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.core.retry.RetrySignal;
import org.nexary.core.retry.RetrySignal.RetryDecision;

/** Internal helpers for messaging governance events that stay provider-neutral. */
public final class MessageGovernanceSupport {
    private MessageGovernanceSupport() {
    }

    /**
     * Executes provider publish work through governance and returns a failed publish result
     * when governance rejects before the provider should be called.
     */
    @SuppressWarnings("unchecked")
    public static CompletionStage<MessagePublishResult> executeGovernedPublish(
            MessageEnvelope<?> envelope,
            String provider,
            NexaryObservationPublisher observationPublisher,
            GovernanceExecution governanceExecution,
            Callable<CompletionStage<MessagePublishResult>> action) {
        Optional<MessagePublishResult> expired = rejectExpiredPublish(envelope, provider, observationPublisher);
        if (expired.isPresent()) {
            return CompletableFuture.completedFuture(expired.get());
        }
        GovernanceExecution safeGovernanceExecution =
                governanceExecution == null ? GovernanceExecution.direct() : governanceExecution;
        try {
            Object result = safeGovernanceExecution.execute(
                    governanceContext(envelope, provider, "publish"),
                    () -> {
                        CompletionStage<MessagePublishResult> stage = action.call();
                        if (stage == null) {
                            return CompletableFuture.completedFuture(MessagePublishResult.failed(
                                    "message publish returned no result",
                                    RetrySignal.stop("publish_no_result")));
                        }
                        return stage;
                    });
            return (CompletionStage<MessagePublishResult>) result;
        } catch (Exception ex) {
            if (ex instanceof GovernanceRejection) {
                return CompletableFuture.completedFuture(rejectedPublishResult(
                        envelope, provider, observationPublisher, (GovernanceRejection) ex, ex));
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new IllegalStateException("message publish governance execution failed", ex);
        }
    }

    /** Returns a failed publish result when the envelope or current deadline has expired. */
    public static Optional<MessagePublishResult> rejectExpiredPublish(
            MessageEnvelope<?> envelope,
            String provider,
            NexaryObservationPublisher observationPublisher) {
        if (!expired(envelope)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        RetrySignal retrySignal = RetrySignal.stop("deadline_exceeded");
        publishDeadlineExceeded(envelope, provider, "publish", observationPublisher, now);
        publishRetryStopped(envelope, provider, "publish", retrySignal, observationPublisher, now);
        MessageObservationSupport.publish(
                observationPublisher,
                "publish",
                provider,
                "failure",
                boundaryTags("deadline", "publish"),
                new TimeoutException("deadline exceeded"));
        return Optional.of(MessagePublishResult.failed("message publish deadline exceeded", retrySignal));
    }

    /** Returns a failed consume result when the envelope or current deadline has expired. */
    public static Optional<MessageConsumeResult> rejectExpiredConsume(
            MessageEnvelope<?> envelope,
            String provider,
            NexaryObservationPublisher observationPublisher) {
        if (!expired(envelope)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        RetrySignal retrySignal = RetrySignal.stop("deadline_exceeded");
        publishDeadlineExceeded(envelope, provider, "consume", observationPublisher, now);
        publishRetryStopped(envelope, provider, "consume", retrySignal, observationPublisher, now);
        MessageObservationSupport.publish(
                observationPublisher,
                "handler",
                provider,
                "failure",
                boundaryTags("deadline", "consume"),
                new TimeoutException("deadline exceeded"));
        return Optional.of(MessageConsumeResult.failed("message consume deadline exceeded"));
    }

    private static MessagePublishResult rejectedPublishResult(
            MessageEnvelope<?> envelope,
            String provider,
            NexaryObservationPublisher observationPublisher,
            GovernanceRejection rejection,
            Exception error) {
        String reason = normalize(rejection.governanceRejectionReason());
        RetrySignal retrySignal = RetrySignal.stop(reason);
        MessageObservationSupport.publish(
                observationPublisher,
                "publish",
                provider,
                "failure",
                boundaryTags(reason, "publish"),
                error);
        publishRetryStoppedIfStop(envelope, provider, "publish", retrySignal, observationPublisher);
        String detail = error.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = "message publish governance rejected: " + reason;
        }
        return MessagePublishResult.failed(detail, retrySignal);
    }

    /** Publishes a governance retry-stopped event when a returned retry signal is terminal. */
    public static void publishRetryStoppedIfStop(
            MessageEnvelope<?> envelope,
            String provider,
            String operation,
            RetrySignal retrySignal,
            NexaryObservationPublisher observationPublisher) {
        if (retrySignal != null && retrySignal.decision() == RetryDecision.STOP) {
            publishRetryStopped(envelope, provider, operation, retrySignal, observationPublisher, Instant.now());
        }
    }

    /** Publishes a governance degraded event for a terminal fallback such as dead-letter handling. */
    public static void publishDegraded(
            MessageEnvelope<?> envelope,
            String provider,
            String operation,
            NexaryObservationPublisher observationPublisher) {
        Instant now = Instant.now();
        safePublisher(observationPublisher).publish(GovernanceObservationEvents.degraded(
                resource(envelope, provider, operation),
                trafficTag(envelope),
                now,
                now));
    }

    /** Returns envelope headers with the effective deadline encoded for provider transport. */
    public static Map<String, String> governedHeaders(MessageEnvelope<?> envelope) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (envelope != null && envelope.headers() != null) {
            headers.putAll(envelope.headers());
        }
        Instant deadline = effectiveDeadline(envelope);
        if (deadline != null) {
            headers.put(MessageEnvelope.DEADLINE_HEADER, Long.toString(deadline.toEpochMilli()));
        }
        return headers;
    }

    /** Decodes a provider-neutral deadline from transported headers. */
    public static Instant deadlineFromHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.get(MessageEnvelope.DEADLINE_HEADER);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value.trim()));
        } catch (DateTimeException | NumberFormatException ex) {
            return null;
        }
    }

    private static void publishDeadlineExceeded(
            MessageEnvelope<?> envelope,
            String provider,
            String operation,
            NexaryObservationPublisher observationPublisher,
            Instant now) {
        safePublisher(observationPublisher).publish(GovernanceObservationEvents.deadlineExceeded(
                resource(envelope, provider, operation),
                trafficTag(envelope),
                now,
                now));
    }

    private static void publishRetryStopped(
            MessageEnvelope<?> envelope,
            String provider,
            String operation,
            RetrySignal retrySignal,
            NexaryObservationPublisher observationPublisher,
            Instant now) {
        safePublisher(observationPublisher).publish(GovernanceObservationEvents.retryStopped(
                resource(envelope, provider, operation),
                trafficTag(envelope),
                retrySignal,
                now,
                now));
    }

    private static boolean expired(MessageEnvelope<?> envelope) {
        Instant deadline = effectiveDeadline(envelope);
        return deadline != null && !Instant.now().isBefore(deadline);
    }

    private static Instant effectiveDeadline(MessageEnvelope<?> envelope) {
        Instant envelopeDeadline = envelope == null ? null : envelope.deadline();
        Optional<Instant> currentDeadline = DeadlineContext.current();
        if (envelopeDeadline == null) {
            return currentDeadline.orElse(null);
        }
        if (!currentDeadline.isPresent()) {
            return envelopeDeadline;
        }
        Instant threadDeadline = currentDeadline.get();
        return envelopeDeadline.isBefore(threadDeadline) ? envelopeDeadline : threadDeadline;
    }

    private static GovernanceResource resource(MessageEnvelope<?> envelope, String provider, String operation) {
        String safeOperation = normalize(operation);
        return new GovernanceResource(
                ResourceKind.MESSAGING,
                "message-" + safeOperation,
                normalize(provider),
                safeOperation);
    }

    private static GovernanceContext governanceContext(
            MessageEnvelope<?> envelope,
            String provider,
            String operation) {
        GovernanceContext.Builder builder = GovernanceContext.builder()
                .resource(resource(envelope, provider, operation));
        if (envelope != null) {
            builder.trafficTag(envelope.trafficTag());
        }
        effectiveDeadlineForContext(envelope).ifPresent(builder::deadline);
        return builder.build();
    }

    private static TrafficTag trafficTag(MessageEnvelope<?> envelope) {
        return envelope == null ? TrafficTag.defaults() : envelope.trafficTag();
    }

    private static Optional<Instant> effectiveDeadlineForContext(MessageEnvelope<?> envelope) {
        Instant envelopeDeadline = envelope == null ? null : envelope.deadline();
        Optional<Instant> currentDeadline = GovernanceContext.current().flatMap(GovernanceContext::deadline);
        if (envelopeDeadline == null) {
            return currentDeadline;
        }
        if (!currentDeadline.isPresent()) {
            return Optional.of(envelopeDeadline);
        }
        Instant inherited = currentDeadline.get();
        return Optional.of(envelopeDeadline.isBefore(inherited) ? envelopeDeadline : inherited);
    }

    private static NexaryObservationPublisher safePublisher(NexaryObservationPublisher observationPublisher) {
        return observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    private static String normalize(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
    }

    private static Map<String, String> boundaryTags(String boundary, String operation) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("boundary", boundary);
        tags.put("operation", operation);
        return tags;
    }
}
