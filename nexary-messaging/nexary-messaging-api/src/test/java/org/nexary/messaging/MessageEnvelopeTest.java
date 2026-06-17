package org.nexary.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MessageEnvelopeTest {
    @Test
    void createsSimpleEnvelopeWithDefaults() {
        MessageEnvelope<String> envelope = MessageEnvelope.of("events", "payload");

        assertThat(envelope.topic()).isEqualTo("events");
        assertThat(envelope.payload()).isEqualTo("payload");
        assertThat(envelope.headers()).isEmpty();
        assertThat(envelope.trafficTag()).isNotNull();
    }

    @Test
    void rejectsBlankTopic() {
        assertThatThrownBy(() -> MessageEnvelope.of(" ", "payload")).isInstanceOf(IllegalArgumentException.class);
    }
}
