package org.nexary.samples.job.business;

import org.nexary.job.JobContext;
import org.springframework.stereotype.Service;

/** Ordinary business service used by the job handler. */
@Service
public class SampleBusinessService {
    /** Runs the business use case for the current shard. */
    public BusinessReceipt run(JobContext context) {
        // In a real service this is where RPC, MQ, cache, or database collaborators are called.
        return new BusinessReceipt("processed shard " + context.shardIndex() + "/" + context.shardTotal());
    }

    /** Business receipt returned by the use case. */
    public record BusinessReceipt(String message) {
    }
}
