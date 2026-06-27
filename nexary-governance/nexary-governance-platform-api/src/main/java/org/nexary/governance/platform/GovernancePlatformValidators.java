package org.nexary.governance.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class GovernancePlatformValidators {
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final Set<String> FORBIDDEN_ATTRIBUTE_KEYS = Set.of(
            "userid",
            "user_id",
            "tenant",
            "tenantid",
            "tenant_id",
            "orderid",
            "order_id",
            "bizkey",
            "biz_key",
            "payload",
            "urlquery",
            "url_query",
            "query",
            "cachekey",
            "cache_key",
            "messageid",
            "message_id",
            "rawtraceid",
            "raw_trace_id",
            "traceid",
            "trace_id",
            "exception",
            "exceptionmessage",
            "exception_message",
            "stacktrace",
            "stack_trace",
            "token",
            "password");

    private GovernancePlatformValidators() {
    }

    static String token(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException(field + " is too long");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':' || ch == '.' || ch == '/')) {
                throw new IllegalArgumentException(field + " contains unsupported characters");
            }
        }
        return normalized;
    }

    static String label(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > 160) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    static Map<String, String> attributes(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = token(entry.getKey(), "attribute key");
            if (FORBIDDEN_ATTRIBUTE_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT).replace("-", "").replace(".", ""))) {
                throw new IllegalArgumentException("attribute key is not allowed");
            }
            copy.put(key, label(entry.getValue(), "attribute value"));
        }
        return Collections.unmodifiableMap(copy);
    }
}
