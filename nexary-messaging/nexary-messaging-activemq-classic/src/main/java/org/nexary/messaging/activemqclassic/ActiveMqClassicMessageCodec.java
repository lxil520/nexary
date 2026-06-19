package org.nexary.messaging.activemqclassic;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageSerializer;

final class ActiveMqClassicMessageCodec {
    static final String MESSAGE_ID_PROPERTY = "nexary_message_id";
    private static final String KEY_PROPERTY = "nexary_message_key";
    private static final String HEADERS_PROPERTY = "nexary_headers";

    private ActiveMqClassicMessageCodec() {
    }

    static Message toMessage(Session session, MessageSerializer serializer, MessageEnvelope<?> envelope)
            throws JMSException {
        BytesMessage message = session.createBytesMessage();
        message.writeBytes(serializer.serialize(envelope.payload()));
        message.setStringProperty(MESSAGE_ID_PROPERTY, envelope.messageId());
        if (!isBlank(envelope.key())) {
            message.setStringProperty(KEY_PROPERTY, envelope.key());
        }
        message.setStringProperty(HEADERS_PROPERTY, encodeHeaders(envelope.headers()));
        return message;
    }

    static <T> MessageEnvelope<T> toEnvelope(
            String topic,
            MessageSerializer serializer,
            Class<T> payloadType,
            Message message) throws JMSException {
        byte[] payload = readPayload(message);
        Map<String, String> headers = decodeHeaders(message.getStringProperty(HEADERS_PROPERTY));
        String messageId = message.getStringProperty(MESSAGE_ID_PROPERTY);
        if (!isBlank(messageId)) {
            headers.putIfAbsent(MessageEnvelope.MESSAGE_ID_HEADER, messageId);
        }
        return new MessageEnvelope<>(
                topic,
                message.getStringProperty(KEY_PROPERTY),
                serializer.deserialize(payload, payloadType),
                headers,
                null,
                null);
    }

    private static byte[] readPayload(Message message) throws JMSException {
        if (message instanceof BytesMessage) {
            BytesMessage bytesMessage = (BytesMessage) message;
            bytesMessage.reset();
            byte[] payload = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(payload);
            return payload;
        }
        if (message instanceof TextMessage) {
            String text = ((TextMessage) message).getText();
            return text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    private static String encodeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        headers.forEach((key, value) -> {
            if (key != null && value != null) {
                builder.append(Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8)))
                        .append(":")
                        .append(Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                        .append("\n");
            }
        });
        return builder.toString();
    }

    private static Map<String, String> decodeHeaders(String encoded) {
        if (isBlank(encoded)) {
            return new LinkedHashMap<>(Collections.emptyMap());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : encoded.split("\\n")) {
            if (isBlank(line)) {
                continue;
            }
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                headers.put(
                        new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8),
                        new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            }
        }
        return headers;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
