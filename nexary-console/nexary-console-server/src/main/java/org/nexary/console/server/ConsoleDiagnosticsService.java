package org.nexary.console.server;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.nexary.console.api.ConsoleEventItem;
import org.nexary.console.api.ConsoleEventsResponse;
import org.nexary.console.api.ConsoleInstanceHealthSnapshot;
import org.nexary.console.api.ConsolePolicySnapshot;
import org.nexary.console.api.ConsoleResourceItem;
import org.nexary.console.api.ConsoleResourcesResponse;
import org.nexary.console.api.ConsoleRuntimeSnapshot;
import org.nexary.console.api.ConsoleSummaryResponse;
import org.nexary.governance.runtime.GovernanceDiagnostics;
import org.nexary.governance.runtime.GovernancePolicySnapshot;
import org.nexary.governance.runtime.GovernanceResourceDescriptor;
import org.nexary.governance.runtime.GovernanceRuntimeEvent;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.GovernanceRuntimeSummary;
import org.nexary.governance.runtime.InstanceHealthSnapshot;

/** Maps local governance diagnostics into the read-only console API model. */
public final class ConsoleDiagnosticsService {
    private final GovernanceDiagnostics diagnostics;

    /** Creates a service backed by the local governance diagnostics source. */
    public ConsoleDiagnosticsService(GovernanceDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /** Returns the local console summary. */
    public ConsoleSummaryResponse summary() {
        GovernanceRuntimeSummary summary = diagnostics == null ? null : diagnostics.summary();
        if (summary == null) {
	            return new ConsoleSummaryResponse(
	                    0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
	                    Collections.<String, Long>emptyMap(), Collections.<String, Long>emptyMap(), 0L, 0L, 0L, null);
        }
        return new ConsoleSummaryResponse(
                summary.resourceCount(),
                summary.snapshotCount(),
                summary.eventCount(),
                summary.successCount(),
                summary.failureCount(),
                summary.rejectedCount(),
                summary.cancelledCount(),
                summary.fallbackCount(),
                summary.retryStoppedCount(),
                summary.blockedCount(),
	                summary.isolatedCount(),
	                summary.sentinelResourceCount(),
	                summary.instanceSuspectCount(),
	                summary.quarantineCandidateCount(),
	                summary.recoveryProbeCount(),
	                summary.trafficClassCounts(),
                summary.priorityCounts(),
                summary.openCircuitCount(),
                summary.halfOpenCircuitCount(),
                summary.degradedResourceCount(),
                optionalInstant(summary.lastEventAt()));
    }

    /** Returns all local console resources. */
    public ConsoleResourcesResponse resources() {
        List<ConsoleResourceItem> items = new ArrayList<>();
        for (GovernanceResourceDescriptor descriptor : resourcesOrEmpty()) {
            items.add(resource(descriptor));
        }
        return new ConsoleResourcesResponse(items);
    }

    /** Returns one local console resource by stable resource id. */
    public Optional<ConsoleResourceItem> resource(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (GovernanceResourceDescriptor descriptor : resourcesOrEmpty()) {
            if (id.equals(descriptor.resourceKey())) {
                return Optional.of(resource(descriptor));
            }
        }
        return Optional.empty();
    }

    /** Returns recent low-cardinality local console events. */
    public ConsoleEventsResponse events() {
        List<ConsoleEventItem> items = new ArrayList<>();
        for (GovernanceRuntimeEvent event : eventsOrEmpty()) {
            items.add(event(event));
        }
        return new ConsoleEventsResponse(items);
    }

    private List<GovernanceResourceDescriptor> resourcesOrEmpty() {
        List<GovernanceResourceDescriptor> resources = diagnostics == null ? null : diagnostics.resources();
        return resources == null ? Collections.emptyList() : resources;
    }

    private List<GovernanceRuntimeEvent> eventsOrEmpty() {
        List<GovernanceRuntimeEvent> events = diagnostics == null ? null : diagnostics.recentEvents();
        return events == null ? Collections.emptyList() : events;
    }

    private static ConsoleResourceItem resource(GovernanceResourceDescriptor descriptor) {
        return new ConsoleResourceItem(
                descriptor.resourceKey(),
                descriptor.engine().name(),
                descriptor.kind().name(),
                descriptor.name(),
                descriptor.provider(),
                descriptor.operation(),
	                descriptor.trafficClass(),
	                descriptor.priority(),
	                policy(descriptor.policySnapshot()),
	                runtime(descriptor.runtimeSnapshot()),
	                instanceSnapshots(descriptor.instanceHealthSnapshots()));
    }

    private static ConsolePolicySnapshot policy(GovernancePolicySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ConsolePolicySnapshot(
                optionalDuration(snapshot.deadline()),
                snapshot.maxRequestsPerWindow(),
                duration(snapshot.rateLimitWindow()),
                snapshot.maxConcurrency(),
                snapshot.degraded(),
                snapshot.minimumRequests(),
                threshold(snapshot.failureRateThreshold()),
                threshold(snapshot.slowCallThreshold()),
                optionalDuration(snapshot.slowCallDuration()),
                duration(snapshot.openStateDuration()),
                snapshot.halfOpenMaxCalls(),
                snapshot.slidingWindowSize(),
                duration(snapshot.slidingWindowDuration()),
                snapshot.consecutiveFailureThreshold());
    }

    private static ConsoleRuntimeSnapshot runtime(GovernanceRuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ConsoleRuntimeSnapshot(
                snapshot.resourceKey(),
                snapshot.engine().name(),
                snapshot.trafficClass(),
                snapshot.priority(),
                snapshot.circuitState().name(),
                snapshot.windowCalls(),
                snapshot.windowFailures(),
                snapshot.windowSlowCalls(),
                snapshot.consecutiveFailures(),
                snapshot.totalRejections(),
                snapshot.lastRejectionReason().name(),
                snapshot.lastBlockReason().name(),
                snapshot.lastIsolationReason().name(),
                snapshot.lastCancellationReason().name(),
                snapshot.lastRetryStopReason().name(),
                optionalInstant(snapshot.openUntil()),
                snapshot.activeConcurrency(),
                snapshot.maxConcurrency(),
                snapshot.maxRequestsPerWindow(),
                duration(snapshot.rateLimitWindow()),
                snapshot.degraded(),
                snapshot.minimumRequests(),
                threshold(snapshot.failureRateThreshold()),
                threshold(snapshot.slowCallThreshold()),
                optionalDuration(snapshot.slowCallDuration()),
                duration(snapshot.openStateDuration()),
                snapshot.halfOpenMaxCalls(),
                snapshot.slidingWindowSize(),
                duration(snapshot.slidingWindowDuration()),
                snapshot.consecutiveFailureThreshold(),
                optionalInstant(snapshot.lastStateTransitionAt()),
                snapshot.lastOutcome().name(),
                optionalInstant(snapshot.lastOutcomeAt()));
    }

    private static ConsoleEventItem event(GovernanceRuntimeEvent event) {
        return new ConsoleEventItem(
                event.resourceKey(),
                event.engine().name(),
                event.trafficClass().name(),
                event.priority().name(),
                event.action().name(),
                event.outcome().name(),
                event.rejectionReason().name(),
                event.isolationReason().name(),
                event.blockReason().name(),
	                event.cancellationReason().name(),
	                event.retryStopReason().name(),
	                event.instanceHealthState().name(),
	                event.quarantineReason().name(),
	                event.recoveryAdvice().name(),
	                event.circuitState().name(),
	                instant(event.timestamp()),
	                event.durationBucket().name());
	}

	private static List<ConsoleInstanceHealthSnapshot> instanceSnapshots(List<InstanceHealthSnapshot> snapshots) {
	    if (snapshots == null || snapshots.isEmpty()) {
	        return Collections.emptyList();
	    }
	    List<ConsoleInstanceHealthSnapshot> items = new ArrayList<>();
	    for (InstanceHealthSnapshot snapshot : snapshots) {
	        items.add(new ConsoleInstanceHealthSnapshot(
	                snapshot.instanceRef().resourceKey(),
	                snapshot.instanceRef().serviceKey(),
	                snapshot.instanceRef().instanceKey(),
	                snapshot.instanceRef().zone(),
	                snapshot.state().name(),
	                snapshot.quarantineReason().name(),
	                snapshot.recoveryAdvice().name(),
	                snapshot.windowCalls(),
	                snapshot.failureCount(),
	                snapshot.slowCallCount(),
	                snapshot.timeoutCount(),
	                snapshot.resetCount(),
	                snapshot.serverErrorCount(),
	                snapshot.failureRatio(),
	                snapshot.slowRatio(),
	                snapshot.timeoutRatio(),
	                snapshot.skewFactor(),
	                instant(snapshot.lastSignalAt()),
	                instant(snapshot.lastChangedAt())));
	    }
	    return items;
	}

    private static String duration(Duration duration) {
        return duration == null ? null : duration.toString();
    }

    private static String optionalDuration(Optional<Duration> duration) {
        return duration == null ? null : duration.map(Duration::toString).orElse(null);
    }

    private static String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String optionalInstant(Optional<Instant> instant) {
        return instant == null ? null : instant.map(Instant::toString).orElse(null);
    }

    private static Double threshold(double value) {
        return Double.isInfinite(value) ? null : value;
    }
}
