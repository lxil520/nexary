package org.nexary.governance.platform;

/** Fixed dependency edge kinds used by the platform topology. */
public enum GovernanceDependencyKind {
    HTTP,
    CACHE,
    MESSAGING,
    JOB,
    DATABASE,
    OBJECT_STORAGE,
    SIGNALING,
    RESOURCE
}
