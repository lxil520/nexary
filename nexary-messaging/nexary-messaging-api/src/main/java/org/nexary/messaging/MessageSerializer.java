package org.nexary.messaging;

/** Serializes provider-neutral message payloads. */
public interface MessageSerializer {
    /** Serializes a payload to bytes. */
    byte[] serialize(Object payload);

    /** Deserializes bytes to a target type. */
    <T> T deserialize(byte[] payload, Class<T> targetType);
}
