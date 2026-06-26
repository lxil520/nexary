package org.nexary.governance.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Stable low-cardinality reference to one downstream instance seen by governance diagnostics. */
public final class GovernanceInstanceRef {
    private final String resourceKey;
    private final String serviceKey;
    private final String instanceKey;
    private final String zone;

    /** Creates an instance reference with a bounded instance key. */
    public GovernanceInstanceRef(String resourceKey, String serviceKey, String instanceKey, String zone) {
        this.resourceKey = normalize(resourceKey, "unknown-resource");
        this.serviceKey = normalize(serviceKey, "unknown-service");
        this.instanceKey = normalizeInstanceKey(instanceKey);
        this.zone = normalize(zone, "unknown");
    }

    /** Creates an instance reference. */
    public static GovernanceInstanceRef of(String resourceKey, String serviceKey, String instanceKey, String zone) {
        return new GovernanceInstanceRef(resourceKey, serviceKey, instanceKey, zone);
    }

    /** Returns the stable governed resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the bounded service label. */
    public String serviceKey() {
        return serviceKey;
    }

    /** Returns the stable instance alias or a fingerprint when the source looked like host/port. */
    public String instanceKey() {
        return instanceKey;
    }

    /** Returns the bounded zone label. */
    public String zone() {
        return zone;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceInstanceRef)) {
            return false;
        }
        GovernanceInstanceRef that = (GovernanceInstanceRef) other;
        return resourceKey.equals(that.resourceKey)
                && serviceKey.equals(that.serviceKey)
                && instanceKey.equals(that.instanceKey)
                && zone.equals(that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceKey, serviceKey, instanceKey, zone);
    }

    @Override
    public String toString() {
        return "GovernanceInstanceRef{"
                + "resourceKey='" + resourceKey + '\''
                + ", serviceKey='" + serviceKey + '\''
                + ", instanceKey='" + instanceKey + '\''
                + ", zone='" + zone + '\''
                + '}';
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeInstanceKey(String value) {
        String normalized = normalize(value, "unknown-instance");
        if (looksLikeNetworkEndpoint(normalized)) {
            return "instance-" + fingerprint(normalized);
        }
        return normalized;
    }

    private static boolean looksLikeNetworkEndpoint(String value) {
        return value.indexOf(':') >= 0 || value.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*");
    }

    private static String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 6 && i < bytes.length; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
