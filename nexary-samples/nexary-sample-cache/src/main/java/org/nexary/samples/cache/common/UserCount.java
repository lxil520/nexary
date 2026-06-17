package org.nexary.samples.cache.common;

import java.io.Serializable;

/** Atomic user counter value. */
public record UserCount(String userId, long count, String source) implements Serializable {
}
