package org.nexary.samples.governance.sentinel.api;

/** Response returned by the Sentinel governance sample endpoints. */
public final class SentinelSampleResult {
    private final String source;
    private final String status;
    private final int attempts;
    private final String retryStopReason;

    /** Creates a sample response. */
    public SentinelSampleResult(String source, String status) {
        this(source, status, 1, "NONE");
    }

    /** Creates a sample response with retry-stop details. */
    public SentinelSampleResult(String source, String status, int attempts, String retryStopReason) {
        this.source = source;
        this.status = status;
        this.attempts = Math.max(0, attempts);
        this.retryStopReason = retryStopReason == null ? "NONE" : retryStopReason;
    }

    /** Returns the code path that produced this response. */
    public String getSource() {
        return source;
    }

    /** Returns a stable status label for curl checks. */
    public String getStatus() {
        return status;
    }

    /** Returns how many attempts the sample endpoint made. */
    public int getAttempts() {
        return attempts;
    }

    /** Returns the bounded retry stop reason, or {@code NONE}. */
    public String getRetryStopReason() {
        return retryStopReason;
    }
}
