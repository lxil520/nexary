package org.nexary.samples.cache.common;

import java.io.Serializable;

/** Cached sample profile. */
public record Profile(String id, String name, String source) implements Serializable {
}
