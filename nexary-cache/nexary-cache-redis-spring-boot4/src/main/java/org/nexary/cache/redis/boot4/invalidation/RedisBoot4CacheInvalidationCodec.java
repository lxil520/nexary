package org.nexary.cache.redis.boot4.invalidation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.nexary.cache.CacheKey;
import org.nexary.cache.invalidation.CacheInvalidationEvent;
import org.nexary.cache.invalidation.CacheInvalidationOperation;

final class RedisBoot4CacheInvalidationCodec {
    private static final String VERSION = "v1";
    private static final String DELIMITER = "|";

    private RedisBoot4CacheInvalidationCodec() {
    }

    static String encode(CacheInvalidationEvent event) {
        return String.join(
                DELIMITER,
                VERSION,
                event.operation().name(),
                encodeText(event.originId()),
                Long.toString(event.createdAt().toEpochMilli()),
                encodeText(event.key().namespace()),
                encodeText(event.key().key()));
    }

    static CacheInvalidationEvent decode(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 6 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported cache invalidation payload");
        }
        CacheInvalidationOperation operation = CacheInvalidationOperation.valueOf(parts[1]);
        String originId = decodeText(parts[2]);
        Instant createdAt = Instant.ofEpochMilli(Long.parseLong(parts[3]));
        CacheKey key = CacheKey.of(decodeText(parts[4]), decodeText(parts[5]));
        return new CacheInvalidationEvent(key, operation, originId, createdAt);
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
