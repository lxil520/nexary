package org.nexary.core.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.governance.GovernanceRejection;

class RetryStopClassifierTest {
    @Test
    void mapsStopSignalToBoundedReason() {
        assertThat(RetrySignal.stop(RetryStopReason.DEADLINE_EXPIRED).stopReason())
                .isEqualTo(RetryStopReason.DEADLINE_EXPIRED);
        assertThat(RetryStopReason.from("concurrency_limited")).isEqualTo(RetryStopReason.BULKHEAD_FULL);
        assertThat(RetryStopReason.from("raw downstream details")).isEqualTo(RetryStopReason.UNKNOWN);
    }

    @Test
    void classifiesGovernanceCancellationAndTimeoutFailures() {
        assertThat(RetryStopClassifier.classify(new CircuitOpenFailure())).isEqualTo(RetryStopReason.CIRCUIT_OPEN);
        assertThat(RetryStopClassifier.fromCancellationReason(CancellationReason.CLIENT_DISCONNECTED))
                .isEqualTo(RetryStopReason.CLIENT_DISCONNECTED);
        assertThat(RetryStopClassifier.classify(new TimeoutException("slow private details")))
                .isEqualTo(RetryStopReason.TIMEOUT);
    }

    private static final class CircuitOpenFailure extends RuntimeException implements GovernanceRejection {
        @Override
        public String governanceRejectionReason() {
            return "circuit_open";
        }
    }
}
