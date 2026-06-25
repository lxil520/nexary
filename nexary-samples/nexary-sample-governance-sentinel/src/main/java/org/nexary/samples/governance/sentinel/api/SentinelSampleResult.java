package org.nexary.samples.governance.sentinel.api;

/** Response returned by the Sentinel governance sample endpoints. */
public final class SentinelSampleResult {
    private final String source;
    private final String status;

    /** Creates a sample response. */
    public SentinelSampleResult(String source, String status) {
        this.source = source;
        this.status = status;
    }

    /** Returns the code path that produced this response. */
    public String getSource() {
        return source;
    }

    /** Returns a stable status label for curl checks. */
    public String getStatus() {
        return status;
    }
}
