package org.nexary.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Minimal UTF-8 string serializer for samples and tests. */
public final class DefaultStringMessageSerializer implements MessageSerializer {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(Object payload) {
        if (payload == null) {
            return new byte[0];
        }
        if (payload instanceof byte[]) {
            return (byte[]) payload;
        }
        if (!(payload instanceof CharSequence)) {
            try {
                return objectMapper.writeValueAsBytes(payload);
            } catch (IOException ex) {
                throw new IllegalArgumentException("message payload must be JSON serializable", ex);
            }
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(byte[] payload, Class<T> targetType) {
        if (targetType == byte[].class) {
            return targetType.cast(payload);
        }
        if (targetType == String.class) {
            return targetType.cast(new String(payload, StandardCharsets.UTF_8));
        }
        try {
            return objectMapper.readValue(payload, targetType);
        } catch (IOException ex) {
            throw new IllegalArgumentException("message payload cannot be deserialized as " + targetType.getName(), ex);
        }
    }
}
