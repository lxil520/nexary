package org.nexary.boot.governance;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nexary.governance.runtime.GovernanceDiagnostics;
import org.nexary.governance.runtime.GovernancePolicySnapshot;
import org.nexary.governance.runtime.GovernanceResourceDescriptor;
import org.nexary.governance.runtime.GovernanceRuntimeEvent;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.GovernanceRuntimeSummary;
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
@AutoConfiguration(after = GovernanceRuntimeAutoConfiguration.class)
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

        private static Map<String, Object> resource(GovernanceResourceDescriptor descriptor) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("resourceKey", descriptor.resourceKey());
            body.put("kind", descriptor.kind().name());
            body.put("name", descriptor.name());
            body.put("provider", descriptor.provider());
            body.put("operation", descriptor.operation());
            body.put("priority", descriptor.priority());
            body.put("policySnapshot", policy(descriptor.policySnapshot()));
            body.put("runtimeSnapshot", snapshot(descriptor.runtimeSnapshot()));
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
            body.put("priority", snapshot.priority());
            body.put("circuitState", snapshot.circuitState().name());
            body.put("windowCalls", snapshot.windowCalls());
            body.put("windowFailures", snapshot.windowFailures());
            body.put("windowSlowCalls", snapshot.windowSlowCalls());
            body.put("consecutiveFailures", snapshot.consecutiveFailures());
            body.put("totalRejections", snapshot.totalRejections());
            body.put("lastRejectionReason", snapshot.lastRejectionReason().name());
            body.put("lastCancellationReason", snapshot.lastCancellationReason().name());
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
            return body;
        }

        private static Map<String, Object> event(GovernanceRuntimeEvent event) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("resourceKey", event.resourceKey());
            body.put("action", event.action().name());
            body.put("outcome", event.outcome().name());
            body.put("rejectionReason", event.rejectionReason().name());
            body.put("cancellationReason", event.cancellationReason().name());
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
            body.put("fallbackCount", summary.fallbackCount());
            body.put("openCircuitCount", summary.openCircuitCount());
            body.put("halfOpenCircuitCount", summary.halfOpenCircuitCount());
            body.put("degradedResourceCount", summary.degradedResourceCount());
            body.put("lastEventAt", optionalInstant(summary.lastEventAt()));
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
}
