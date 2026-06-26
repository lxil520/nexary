package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.Objects;

/** Low-cardinality local signal used to detect abnormal downstream instances. */
public final class InstanceHealthSignal {
    private final GovernanceInstanceRef instanceRef;
    private final InstanceHealthSignalType signalType;
    private final GovernanceCallOutcome outcome;
    private final InstanceStatusCodeClass statusCodeClass;
    private final GovernanceDurationBucket durationBucket;
    private final Instant timestamp;

    /** Creates an instance health signal. */
    public InstanceHealthSignal(
            GovernanceInstanceRef instanceRef,
            InstanceHealthSignalType signalType,
            GovernanceCallOutcome outcome,
            InstanceStatusCodeClass statusCodeClass,
            GovernanceDurationBucket durationBucket,
            Instant timestamp) {
        this.instanceRef = instanceRef == null
                ? GovernanceInstanceRef.of("unknown-resource", "unknown-service", "unknown-instance", "unknown")
                : instanceRef;
        this.signalType = signalType == null ? InstanceHealthSignalType.STATUS_CODE_SKEW : signalType;
        this.outcome = outcome == null ? GovernanceCallOutcome.NONE : outcome;
        this.statusCodeClass = statusCodeClass == null ? InstanceStatusCodeClass.NONE : statusCodeClass;
        this.durationBucket = durationBucket == null ? GovernanceDurationBucket.NOT_RUN : durationBucket;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    /** Creates a success signal for an instance. */
    public static InstanceHealthSignal success(GovernanceInstanceRef instanceRef) {
        return new InstanceHealthSignal(
                instanceRef,
                InstanceHealthSignalType.STATUS_CODE_SKEW,
                GovernanceCallOutcome.SUCCESS,
                InstanceStatusCodeClass.HTTP_2XX,
                GovernanceDurationBucket.LT_10_MS,
                Instant.now());
    }

    /** Returns the bounded instance reference. */
    public GovernanceInstanceRef instanceRef() {
        return instanceRef;
    }

    /** Returns the fixed signal type. */
    public InstanceHealthSignalType signalType() {
        return signalType;
    }

    /** Returns the bounded call outcome. */
    public GovernanceCallOutcome outcome() {
        return outcome;
    }

    /** Returns the fixed status-code class. */
    public InstanceStatusCodeClass statusCodeClass() {
        return statusCodeClass;
    }

    /** Returns the coarse duration bucket. */
    public GovernanceDurationBucket durationBucket() {
        return durationBucket;
    }

    /** Returns when the signal was recorded. */
    public Instant timestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InstanceHealthSignal)) {
            return false;
        }
        InstanceHealthSignal that = (InstanceHealthSignal) other;
        return instanceRef.equals(that.instanceRef)
                && signalType == that.signalType
                && outcome == that.outcome
                && statusCodeClass == that.statusCodeClass
                && durationBucket == that.durationBucket
                && timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceRef, signalType, outcome, statusCodeClass, durationBucket, timestamp);
    }
}
