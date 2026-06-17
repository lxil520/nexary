package org.nexary.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.retry.RetrySignal;

class MessageApiValueObjectsTest {
    @Test
    void envelopePreservesRecordStyleAccessorsAndValueSemantics() {
        Instant deadline = Instant.parse("2026-06-17T08:00:00Z");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(MessageEnvelope.MESSAGE_ID_HEADER, "message-42");
        headers.put("tenant", "demo");

        MessageEnvelope<String> first =
                new MessageEnvelope<>("events", "key-42", "payload", headers, deadline, TrafficTag.defaults());
        MessageEnvelope<String> second =
                new MessageEnvelope<>("events", "key-42", "payload", headers, deadline, TrafficTag.defaults());

        headers.put("later", "ignored");

        assertThat(first.topic()).isEqualTo("events");
        assertThat(first.key()).isEqualTo("key-42");
        assertThat(first.payload()).isEqualTo("payload");
        assertThat(first.deadline()).isEqualTo(deadline);
        assertThat(first.trafficTag()).isEqualTo(TrafficTag.defaults());
        assertThat(first.messageId()).isEqualTo("message-42");
        assertThat(first.headers()).containsExactly(
                Map.entry(MessageEnvelope.MESSAGE_ID_HEADER, "message-42"),
                Map.entry("tenant", "demo"));
        assertThatThrownBy(() -> first.headers().put("mutate", "nope"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("MessageEnvelope", "topic=events", "message-42");
    }

    @Test
    void envelopeRejectsBlankTopicAndFallsBackToKeyOrPayloadMessageId() {
        assertThatThrownBy(() -> MessageEnvelope.of(" ", "payload")).isInstanceOf(IllegalArgumentException.class);

        assertThat(MessageEnvelope.of("events", "payload").messageId())
                .isEqualTo("events:" + Integer.toHexString("payload".hashCode()));
        assertThat(new MessageEnvelope<>("events", "business-key", "payload", null, null, null).messageId())
                .isEqualTo("events:business-key");
    }

    @Test
    void retryPolicyNormalizesAndKeepsValueSemantics() {
        MessageRetryPolicy first =
                new MessageRetryPolicy(3, Duration.ofMillis(100), MessageBackoffStrategy.EXPONENTIAL, 2.0d, Duration.ofMillis(300));
        MessageRetryPolicy second =
                new MessageRetryPolicy(3, Duration.ofMillis(100), MessageBackoffStrategy.EXPONENTIAL, 2.0d, Duration.ofMillis(300));
        MessageRetryPolicy normalized =
                new MessageRetryPolicy(1, Duration.ofMillis(-1), null, 0.5d, Duration.ofMillis(-1));

        assertThat(first.maxAttempts()).isEqualTo(3);
        assertThat(first.initialDelay()).isEqualTo(Duration.ofMillis(100));
        assertThat(first.backoffStrategy()).isEqualTo(MessageBackoffStrategy.EXPONENTIAL);
        assertThat(first.multiplier()).isEqualTo(2.0d);
        assertThat(first.maxBackoff()).isEqualTo(Duration.ofMillis(300));
        assertThat(first.backoffFor(3)).isEqualTo(Duration.ofMillis(300));
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("MessageRetryPolicy", "maxAttempts=3");
        assertThat(normalized.initialDelay()).isEqualTo(Duration.ZERO);
        assertThat(normalized.backoffStrategy()).isEqualTo(MessageBackoffStrategy.FIXED);
        assertThat(normalized.multiplier()).isEqualTo(1.0d);
    }

    @Test
    void publishAndConsumeResultsPreserveRecordStyleAccessorsAndValueSemantics() {
        RetrySignal retrySignal = RetrySignal.retry(2, Duration.ofMillis(25), "retry");
        MessagePublishResult publishFirst =
                new MessagePublishResult(MessagePublishResult.PublishStatus.FAILED, null, retrySignal, "failed");
        MessagePublishResult publishSecond =
                new MessagePublishResult(MessagePublishResult.PublishStatus.FAILED, null, retrySignal, "failed");

        MessageConsumeResult consumeFirst =
                new MessageConsumeResult(MessageConsumeResult.ConsumeStatus.RETRY, retrySignal, "retry", null);
        MessageConsumeResult consumeSecond =
                new MessageConsumeResult(MessageConsumeResult.ConsumeStatus.RETRY, retrySignal, "retry", null);

        assertThat(publishFirst.status()).isEqualTo(MessagePublishResult.PublishStatus.FAILED);
        assertThat(publishFirst.retrySignal()).isEqualTo(retrySignal);
        assertThat(publishFirst.detail()).isEqualTo("failed");
        assertThat(publishFirst).isEqualTo(publishSecond).hasSameHashCodeAs(publishSecond);
        assertThat(publishFirst.toString()).contains("MessagePublishResult", "status=FAILED");

        assertThat(consumeFirst.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.RETRY);
        assertThat(consumeFirst.retrySignal()).isEqualTo(retrySignal);
        assertThat(consumeFirst.detail()).isEqualTo("retry");
        assertThat(consumeFirst.deadLetterRecord()).isNull();
        assertThat(consumeFirst).isEqualTo(consumeSecond).hasSameHashCodeAs(consumeSecond);
        assertThat(consumeFirst.toString()).contains("MessageConsumeResult", "status=RETRY");
    }

    @Test
    void deadLetterRecordPreservesImmutableSnapshotAndValueSemantics() {
        Instant occurredAt = Instant.parse("2026-06-17T08:10:00Z");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(MessageEnvelope.MESSAGE_ID_HEADER, "message-42");
        headers.put("tenant", "demo");

        MessageDeadLetterRecord first = new MessageDeadLetterRecord(
                "message-42",
                "events",
                "key-42",
                "consumer-group",
                "payload",
                headers,
                3,
                null,
                "java.lang.IllegalStateException",
                "boom",
                occurredAt);
        MessageDeadLetterRecord second = new MessageDeadLetterRecord(
                "message-42",
                "events",
                "key-42",
                "consumer-group",
                "payload",
                headers,
                3,
                MessageFailureStatus.RETRY_EXHAUSTED,
                "java.lang.IllegalStateException",
                "boom",
                occurredAt);

        headers.put("later", "ignored");

        assertThat(first.messageId()).isEqualTo("message-42");
        assertThat(first.topic()).isEqualTo("events");
        assertThat(first.key()).isEqualTo("key-42");
        assertThat(first.consumerGroup()).isEqualTo("consumer-group");
        assertThat(first.payload()).isEqualTo("payload");
        assertThat(first.headers()).containsExactly(
                Map.entry(MessageEnvelope.MESSAGE_ID_HEADER, "message-42"),
                Map.entry("tenant", "demo"));
        assertThatThrownBy(() -> first.headers().put("mutate", "nope"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(first.attempts()).isEqualTo(3);
        assertThat(first.status()).isEqualTo(MessageFailureStatus.RETRY_EXHAUSTED);
        assertThat(first.errorType()).isEqualTo("java.lang.IllegalStateException");
        assertThat(first.errorMessage()).isEqualTo("boom");
        assertThat(first.occurredAt()).isEqualTo(occurredAt);
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("MessageDeadLetterRecord", "message-42", "RETRY_EXHAUSTED");
    }
}
