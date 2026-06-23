package org.nexary.samples.governance.common;

import org.nexary.governance.runtime.GovernanceCircuitState;
import org.nexary.governance.runtime.GovernanceRejectionReason;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;

/** Response returned by the circuit-breaker sample endpoint. */
public final class CircuitProfileResult {
    private final String userId;
    private final String displayName;
    private final String source;
    private final String outcome;
    private final GovernanceCircuitState circuitState;
    private final int windowCalls;
    private final int windowFailures;
    private final int windowSlowCalls;
    private final int consecutiveFailures;
    private final long totalRejections;
    private final GovernanceRejectionReason lastRejectionReason;

    public CircuitProfileResult(ProfileResult profile, String outcome, GovernanceRuntimeSnapshot snapshot) {
        this.userId = profile.userId();
        this.displayName = profile.displayName();
        this.source = profile.source();
        this.outcome = outcome;
        this.circuitState = snapshot.circuitState();
        this.windowCalls = snapshot.windowCalls();
        this.windowFailures = snapshot.windowFailures();
        this.windowSlowCalls = snapshot.windowSlowCalls();
        this.consecutiveFailures = snapshot.consecutiveFailures();
        this.totalRejections = snapshot.totalRejections();
        this.lastRejectionReason = snapshot.lastRejectionReason();
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSource() {
        return source;
    }

    public String getOutcome() {
        return outcome;
    }

    public GovernanceCircuitState getCircuitState() {
        return circuitState;
    }

    public int getWindowCalls() {
        return windowCalls;
    }

    public int getWindowFailures() {
        return windowFailures;
    }

    public int getWindowSlowCalls() {
        return windowSlowCalls;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public long getTotalRejections() {
        return totalRejections;
    }

    public GovernanceRejectionReason getLastRejectionReason() {
        return lastRejectionReason;
    }
}
