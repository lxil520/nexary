package org.nexary.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobResultTest {
    @Test
    void createsSuccessResult() {
        assertThat(JobResult.success().status()).isEqualTo(JobResult.JobStatus.SUCCESS);
    }
}
