package org.nexary.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultStringMessageSerializerTest {
    @Test
    void roundTripsStringPayload() {
        DefaultStringMessageSerializer serializer = new DefaultStringMessageSerializer();

        byte[] bytes = serializer.serialize("hello");

        assertThat(serializer.deserialize(bytes, String.class)).isEqualTo("hello");
    }
}
