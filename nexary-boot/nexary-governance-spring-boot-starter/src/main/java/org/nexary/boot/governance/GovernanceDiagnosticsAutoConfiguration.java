package org.nexary.boot.governance;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nexary.governance.runtime.GovernanceDiagnostics;
import org.nexary.governance.runtime.GovernanceFaultTrace;
import org.nexary.governance.runtime.GovernanceFaultTraceSummary;
import org.nexary.governance.runtime.GovernanceInstanceHealth;
import org.nexary.governance.runtime.GovernancePolicySnapshot;
import org.nexary.governance.runtime.GovernanceResourceDescriptor;
import org.nexary.governance.runtime.GovernanceRuntimeEvent;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.GovernanceRuntimeSummary;
import org.nexary.governance.runtime.GovernanceTraceStep;
import org.nexary.governance.runtime.InstanceHealthSnapshot;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auto-configuration for read-only local governance diagnostics endpoints. */
@AutoConfiguration(
        after = GovernanceRuntimeAutoConfiguration.class,
        afterName = "org.nexary.boot.governance.sentinel.GovernanceSentinelAutoConfiguration")
@EnableConfigurationProperties(GovernanceRuntimeProperties.class)
@ConditionalOnClass(RestController.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "nexary.governance.diagnostics", name = "enabled", havingValue = "true")
public class GovernanceDiagnosticsAutoConfiguration {
    /** Read-only HTTP endpoint for local governance diagnostics. */
    @RestController
    @ConditionalOnBean(GovernanceDiagnostics.class)
    @RequestMapping("${nexary.governance.diagnostics.path-prefix:/nexary/governance}")
    public static final class GovernanceDiagnosticsEndpoint {
        private final GovernanceDiagnostics diagnostics;

        public GovernanceDiagnosticsEndpoint(GovernanceDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        /** Returns known governance resources. */
        @GetMapping("/resources")
        public List<Map<String, Object>> resources() {
            List<Map<String, Object>> resources = new ArrayList<>();
            for (GovernanceResourceDescriptor descriptor : diagnostics.resources()) {
                resources.add(resource(descriptor));
            }
            return resources;
        }

        /** Returns one governance resource by resource key. */
        @GetMapping("/resources/{resourceKey}")
        public ResponseEntity<Map<String, Object>> resource(@PathVariable String resourceKey) {
            return diagnostics.resources().stream()
                    .filter(resource -> resource.resourceKey().equals(resourceKey))
                    .findFirst()
                    .map(GovernanceDiagnosticsEndpoint::resource)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        /** Returns recent low-cardinality governance events. */
        @GetMapping("/events")
        public List<Map<String, Object>> events() {
            List<Map<String, Object>> events = new ArrayList<>();
            for (GovernanceRuntimeEvent runtimeEvent : diagnostics.recentEvents()) {
                events.add(event(runtimeEvent));
            }
            return events;
        }

        /** Returns a low-cardinality runtime summary. */
        @GetMapping("/summary")
        public Map<String, Object> summary() {
            return summary(diagnostics.summary());
        }

        /** Returns retained local fault traces. */
        @GetMapping("/traces")
        public List<Map<String, Object>> traces() {
            List<Map<String, Object>> traces = new ArrayList<>();
            for (GovernanceFaultTrace trace : diagnostics.traces()) {
                traces.add(trace(trace));
            }
            return traces;
        }

        /** Returns one retained local fault trace by trace key. */
        @GetMapping("/traces/{traceKey}")
        public ResponseEntity<Map<String, Object>> trace(@PathVariable String traceKey) {
            return diagnostics.trace(traceKey)
                    .map(GovernanceDiagnosticsEndpoint::trace)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        /** Returns aggregate local fault trace counters. */
        @GetMapping("/faults/summary")
        public Map<String, Object> faultSummary() {
            return faultSummary(diagnostics.faultTraceSummary());
        }

        private static Map<String, Object> resource(GovernanceResourceDescriptor descriptor) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("resourceKey", descriptor.resourceKey());
            body.put("kind", descriptor.kind().name());
            body.put("name", descriptor.name());
            body.put("provider", descriptor.provider());
            body.put("operation", descriptor.operation());
            body.put("trafficClass", descriptor.trafficClass());
            body.put("priority", descriptor.priority());
            body.put("engine", descriptor.engine().name());
            body.put("policySnapshot", policy(descriptor.policySnapshot()));
            body.put("runtimeSnapshot", snapshot(descriptor.runtimeSnapshot()));
            body.put("instanceHealthSnapshots", instanceSnapshots(descriptor.instanceHealthSnapshots()));
            body.put("lastTraceOutcome", descriptor.lastTraceOutcome().name());
            body.put("lastTraceStopReason", descriptor.lastTraceStopReason().name());
            return body;
        }

        private static Map<String, Object> policy(GovernancePolicySnapshot snapshot) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("maxRequestsPerWindow", snapshot.maxRequestsPerWindow());
            body.put("rateLimitWindow", duration(snapshot.rateLimitWindow()));
            body.put("maxConcurrency", snapshot.maxConcurrency());
            body.put("degraded", snapshot.degraded());
            body.put("minimumRequests", snapshot.minimumRequests());
            body.put("failureRateThreshold", threshold(snapshot.failureRateThreshold()));
            body.put("slowCallThreshold", threshold(snapshot.slowCallThreshold()));
            body.put("slowCallDuration", optionalDuration(snapshot.slowCallDuration()));
            body.put("openStateDuration", duration(snapshot.openStateDuration()));
            body.put("halfOpenMaxCalls", snapshot.halfOpenMaxCalls());
            body.put("slidingWindowSize", snapshot.slidingWindowSize());
            body.put("slidingWindowDuration", duration(snapshot.slidingWindowDuration()));
            body.put("consecutiveFailureThreshold", snapshot.consecutiveFailureThreshold());
            return body;
        }

        private static Map<String, Object> snapshot(GovernanceRuntimeSnapshot snapshot) {
            if (snapshot == null) {
                return null;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("resourceKey", snapshot.resourceKey());
            body.put("trafficClass", snapshot.trafficClass());
            body.put("priority", snapshot.priority());
            body.put("circuitState", snapshot.circuitState().name());
            body.put("windowCalls", snapshot.windowCalls());
            body.put("windowFailures", snapshot.windowFailures());
            body.put("windowSlowCalls", snapshot.windowSlowCalls());
            body.put("consecutiveFailures", snapshot.consecutiveFailures());
            body.put("totalRejections", snapshot.totalRejections());
            body.put("lastRejectionReason", snapshot.lastRejectionReason().name());
            body.put("lastIsolationReason", snapshot.lastIsolationReason().name());
            body.put("lastCancellationReason", snapshot.lastCancellationReason().name());
            body.put("lastRetryStopReason", snapshot.lastRetryStopReason().name());
            body.put("openUntil", optionalInstant(snapshot.openUntil()));
            body.put("activeConcurrency", snapshot.activeConcurrency());
            body.put("maxConcurrency", snapshot.maxConcurrency());
            body.put("maxRequestsPerWindow", snapshot.maxRequestsPerWindow());
            body.put("rateLimitWindow", duration(snapshot.rateLimitWindow()));
            body.put("degraded", snapshot.degraded());
            body.put("minimumRequests", snapshot.minimumRequests());
            body.put("failureRateThreshold", threshold(snapshot.failureRateThreshold()));
            body.put("slowCallThreshold", threshold(snapshot.slowCallThreshold()));
            body.put("slowCallDuration", optionalDuration(snapshot.slowCallDuration()));
            body.put("openStateDuration", duration(snapshot.openStateDuration()));
            body.put("halfOpenMaxCalls", snapshot.halfOpenMaxCalls());
            body.put("slidingWindowSize", snapshot.slidingWindowSize());
            body.put("slidingWindowDuration", duration(snapshot.slidingWindowDuration()));
            body.put("consecutiveFailureThreshold", snapshot.consecutiveFailureThreshold());
            body.put("lastStateTransitionAt", optionalInstant(snapshot.lastStateTransitionAt()));
            body.put("lastOutcome", snapshot.lastOutcome().name());
            body.put("lastOutcomeAt", optionalInstant(snapshot.lastOutcomeAt()));
            body.put("engine", snapshot.engine().name());
            body.put("lastBlockReason", snapshot.lastBlockReason().name());
            return body;
        }

        private static Map<String, Object> event(GovernanceRuntimeEvent event) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("resourceKey", event.resourceKey());
            body.put("trafficClass", event.trafficClass().name());
            body.put("priority", event.priority().name());
            body.put("action", event.action().name());
            body.put("outcome", event.outcome().name());
            body.put("rejectionReason", event.rejectionReason().name());
            body.put("isolationReason", event.isolationReason().name());
            body.put("cancellationReason", event.cancellationReason().name());
            body.put("engine", event.engine().name());
            body.put("blockReason", event.blockReason().name());
            body.put("retryStopReason", event.retryStopReason().name());
            body.put("instanceHealthState", event.instanceHealthState().name());
            body.put("quarantineReason", event.quarantineReason().name());
            body.put("recoveryAdvice", event.recoveryAdvice().name());
            body.put("traceStage", event.traceStage().name());
            body.put("tracePrimaryStopReason", event.tracePrimaryStopReason().name());
            body.put("circuitState", event.circuitState().name());
            body.put("timestamp", instant(event.timestamp()));
            body.put("durationBucket", event.durationBucket().name());
            return body;
        }

        private static Map<String, Object> summary(GovernanceRuntimeSummary summary) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("resourceCount", summary.resourceCount());
            body.put("snapshotCount", summary.snapshotCount());
            body.put("eventCount", summary.eventCount());
            body.put("successCount", summary.successCount());
            body.put("failureCount", summary.failureCount());
            body.put("rejectedCount", summary.rejectedCount());
            body.put("cancelledCount", summary.cancelledCount());
            body.put("retryStoppedCount", summary.retryStoppedCount());
            body.put("blockedCount", summary.blockedCount());
            body.put("isolatedCount", summary.isolatedCount());
            body.put("instanceSuspectCount", summary.instanceSuspectCount());
            body.put("quarantineCandidateCount", summary.quarantineCandidateCount());
            body.put("recoveryProbeCount", summary.recoveryProbeCount());
            body.put("faultTraceCount", summary.faultTraceCount());
            body.put("stoppedTraceCount", summary.stoppedTraceCount());
            body.put("trafficClassCounts", summary.trafficClassCounts());
            body.put("priorityCounts", summary.priorityCounts());
            body.put("fallbackCount", summary.fallbackCount());
            body.put("openCircuitCount", summary.openCircuitCount());
            body.put("halfOpenCircuitCount", summary.halfOpenCircuitCount());
            body.put("degradedResourceCount", summary.degradedResourceCount());
            body.put("sentinelResourceCount", summary.sentinelResourceCount());
            body.put("lastEventAt", optionalInstant(summary.lastEventAt()));
            return body;
        }

        private static Map<String, Object> trace(GovernanceFaultTrace trace) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("traceKey", trace.traceKey());
            body.put("rootResourceKey", trace.rootResourceKey());
            body.put("startedAt", instant(trace.startedAt()));
            body.put("lastEventAt", instant(trace.lastEventAt()));
            body.put("terminalOutcome", trace.terminalOutcome().name());
            body.put("primaryStopReason", trace.primaryStopReason().name());
            body.put("suggestedResourceKey", trace.suggestedResourceKey());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (GovernanceTraceStep step : trace.steps()) {
                steps.add(traceStep(step));
            }
            body.put("steps", steps);
            return body;
        }

        private static Map<String, Object> traceStep(GovernanceTraceStep step) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("stage", step.stage().name());
            body.put("resourceKey", step.resourceKey());
            body.put("action", step.action().name());
            body.put("outcome", step.outcome().name());
            body.put("durationBucket", step.durationBucket().name());
            body.put("timestamp", instant(step.timestamp()));
            body.put("rejectionReason", step.rejectionReason().name());
            body.put("blockReason", step.blockReason().name());
            body.put("cancellationReason", step.cancellationReason().name());
            body.put("retryStopReason", step.retryStopReason().name());
            body.put("isolationReason", step.isolationReason().name());
            body.put("instanceHealthState", step.instanceHealthState().name());
            body.put("quarantineReason", step.quarantineReason().name());
            return body;
        }

        private static Map<String, Object> faultSummary(GovernanceFaultTraceSummary summary) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("traceCount", summary.traceCount());
            body.put("stoppedCount", summary.stoppedCount());
            body.put("blockedCount", summary.blockedCount());
            body.put("cancelledCount", summary.cancelledCount());
            body.put("retryStoppedCount", summary.retryStoppedCount());
            body.put("instanceRelatedCount", summary.instanceRelatedCount());
            body.put("topStopReasons", summary.topStopReasons());
            return body;
        }

        private static List<Map<String, Object>> instanceSnapshots(List<InstanceHealthSnapshot> snapshots) {
            List<Map<String, Object>> body = new ArrayList<>();
            if (snapshots == null) {
                return body;
            }
            for (InstanceHealthSnapshot snapshot : snapshots) {
                body.add(instanceSnapshot(snapshot));
            }
            return body;
        }

        private static Map<String, Object> instanceSnapshot(InstanceHealthSnapshot snapshot) {
            Map<String, Object> body = new LinkedHashMap<>();
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("resourceKey", snapshot.instanceRef().resourceKey());
            ref.put("serviceKey", snapshot.instanceRef().serviceKey());
            ref.put("instanceKey", snapshot.instanceRef().instanceKey());
            ref.put("zone", snapshot.instanceRef().zone());
            body.put("instanceRef", ref);
            body.put("state", snapshot.state().name());
            body.put("quarantineReason", snapshot.quarantineReason().name());
            body.put("recoveryAdvice", snapshot.recoveryAdvice().name());
            body.put("windowCalls", snapshot.windowCalls());
            body.put("failureCount", snapshot.failureCount());
            body.put("slowCallCount", snapshot.slowCallCount());
            body.put("timeoutCount", snapshot.timeoutCount());
            body.put("resetCount", snapshot.resetCount());
            body.put("serverErrorCount", snapshot.serverErrorCount());
            body.put("failureRatio", snapshot.failureRatio());
            body.put("slowRatio", snapshot.slowRatio());
            body.put("timeoutRatio", snapshot.timeoutRatio());
            body.put("skewFactor", snapshot.skewFactor());
            body.put("lastSignalAt", instant(snapshot.lastSignalAt()));
            body.put("lastChangedAt", instant(snapshot.lastChangedAt()));
            return body;
        }

        private static String duration(Duration duration) {
            return duration == null ? null : duration.toString();
        }

        private static String optionalDuration(Optional<Duration> duration) {
            return duration.map(Duration::toString).orElse(null);
        }

        private static String optionalInstant(Optional<Instant> instant) {
            return instant.map(Instant::toString).orElse(null);
        }

        private static String instant(Instant instant) {
            return instant == null ? null : instant.toString();
        }

        private static Double threshold(double value) {
            return Double.isInfinite(value) ? null : value;
        }
    }

    /** Read-only HTTP endpoint for local instance health diagnostics. */
    @RestController
    @ConditionalOnBean(GovernanceInstanceHealth.class)
    @ConditionalOnProperty(prefix = "nexary.governance.instance-health", name = "enabled", havingValue = "true")
    @RequestMapping("${nexary.governance.diagnostics.path-prefix:/nexary/governance}")
    public static final class GovernanceInstanceHealthEndpoint {
        private final GovernanceInstanceHealth instanceHealth;

        public GovernanceInstanceHealthEndpoint(GovernanceInstanceHealth instanceHealth) {
            this.instanceHealth = instanceHealth;
        }

        /** Returns all known local instance health snapshots. */
        @GetMapping("/instance-health")
        public List<Map<String, Object>> instanceHealth() {
            return GovernanceDiagnosticsEndpoint.instanceSnapshots(instanceHealth.snapshots());
        }

        /** Returns local instance health snapshots for one resource. */
        @GetMapping("/instance-health/{resourceKey}")
        public List<Map<String, Object>> instanceHealth(@PathVariable String resourceKey) {
            return GovernanceDiagnosticsEndpoint.instanceSnapshots(instanceHealth.snapshots(resourceKey));
        }
    }
}
