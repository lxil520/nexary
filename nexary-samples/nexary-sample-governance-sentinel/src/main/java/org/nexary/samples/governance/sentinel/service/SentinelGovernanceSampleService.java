package org.nexary.samples.governance.sentinel.service;

import java.time.Duration;
import org.nexary.samples.governance.sentinel.api.SentinelSampleResult;
import org.springframework.stereotype.Service;

/** Business work used by the Sentinel governance sample. */
@Service
public class SentinelGovernanceSampleService {
    /** Returns a successful sample result. */
    public SentinelSampleResult ok() {
        return new SentinelSampleResult("business", "ok");
    }

    /** Returns a fallback sample result. */
    public SentinelSampleResult fallback() {
        return new SentinelSampleResult("fallback", "ok");
    }

    /** Holds the request thread long enough to trigger concurrency or slow-call policies. */
    public SentinelSampleResult hold(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
        return ok();
    }

    /** Throws a stable exception for the exception-circuit sample. */
    public SentinelSampleResult fail() {
        throw new IllegalStateException("sample failure");
    }
}
