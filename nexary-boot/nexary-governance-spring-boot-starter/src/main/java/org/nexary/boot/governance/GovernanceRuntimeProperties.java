package org.nexary.boot.governance;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.RequestPriority;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the local Nexary governance runtime. */
@ConfigurationProperties(prefix = "nexary.governance")
public class GovernanceRuntimeProperties {
    private Provider provider = Provider.LOCAL;
    private RuntimeSettings runtime = new RuntimeSettings();
    private Sentinel sentinel = new Sentinel();
    private Diagnostics diagnostics = new Diagnostics();
    private Cancellation cancellation = new Cancellation();
    private Policy defaultPolicy = new Policy();
    private Map<String, ResourcePolicy> resources = new LinkedHashMap<>();

    /** Returns the runtime provider used for governance execution. */
    public Provider getProvider() {
        return provider;
    }

    /** Sets the runtime provider used for governance execution. */
    public void setProvider(Provider provider) {
        this.provider = provider == null ? Provider.LOCAL : provider;
    }

    /** Returns runtime lifecycle settings. */
    public RuntimeSettings getRuntime() {
        return runtime;
    }

    /** Sets runtime lifecycle settings. */
    public void setRuntime(RuntimeSettings runtime) {
        this.runtime = runtime == null ? new RuntimeSettings() : runtime;
    }

    /** Returns Sentinel provider settings. */
    public Sentinel getSentinel() {
        return sentinel;
    }

    /** Sets Sentinel provider settings. */
    public void setSentinel(Sentinel sentinel) {
        this.sentinel = sentinel == null ? new Sentinel() : sentinel;
    }

    /** Returns read-only diagnostics endpoint settings. */
    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    /** Supported governance runtime providers. */
    public enum Provider {
        /** Nexary's in-process local governance runtime. */
        LOCAL,

        /** Alibaba Sentinel-backed governance runtime. */
        SENTINEL
    }

    /** Sentinel provider settings. */
    public static class Sentinel {
        private boolean enabled = true;
        private Transport transport = new Transport();

        /** Returns whether Sentinel provider auto-configuration may create a runtime. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether Sentinel provider auto-configuration may create a runtime. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns optional Sentinel transport settings. */
        public Transport getTransport() {
            return transport;
        }

        /** Sets optional Sentinel transport settings. */
        public void setTransport(Transport transport) {
            this.transport = transport == null ? new Transport() : transport;
        }
    }

    /** Optional Sentinel transport settings. */
    public static class Transport {
        private boolean enabled;
        private String dashboardServer;

        /** Returns whether Sentinel transport integration is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether Sentinel transport integration is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the optional Sentinel dashboard server address. */
        public String getDashboardServer() {
            return dashboardServer;
        }

        /** Sets the optional Sentinel dashboard server address. */
        public void setDashboardServer(String dashboardServer) {
            this.dashboardServer = dashboardServer;
        }
    }

    /** Sets read-only diagnostics endpoint settings. */
    public void setDiagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics == null ? new Diagnostics() : diagnostics;
    }

    /** Returns cooperative cancellation settings. */
    public Cancellation getCancellation() {
        return cancellation;
    }

    /** Sets cooperative cancellation settings. */
    public void setCancellation(Cancellation cancellation) {
        this.cancellation = cancellation == null ? new Cancellation() : cancellation;
    }

    /** Returns the default policy used when no resource policy matches. */
    public Policy getDefaultPolicy() {
        return defaultPolicy;
    }

    /** Sets the default policy used when no resource policy matches. */
    public void setDefaultPolicy(Policy defaultPolicy) {
        this.defaultPolicy = defaultPolicy == null ? new Policy() : defaultPolicy;
    }

    /** Returns resource policies keyed by a stable configuration id. */
    public Map<String, ResourcePolicy> getResources() {
        return resources;
    }

    /** Sets resource policies keyed by a stable configuration id. */
    public void setResources(Map<String, ResourcePolicy> resources) {
        this.resources = resources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resources);
    }

    /** Runtime lifecycle settings. */
    public static class RuntimeSettings {
        private boolean enabled = true;
        private Policy defaultPolicy;

        /** Returns whether the local governance runtime should be auto-configured. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether the local governance runtime should be auto-configured. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the legacy runtime-scoped default policy, when configured. */
        public Policy getDefaultPolicy() {
            return defaultPolicy;
        }

        /** Sets the legacy runtime-scoped default policy. */
        public void setDefaultPolicy(Policy defaultPolicy) {
            this.defaultPolicy = defaultPolicy;
        }
    }

    /** Read-only diagnostics endpoint settings. */
    public static class Diagnostics {
        private boolean enabled;
        private String pathPrefix = "/nexary/governance";

        /** Returns whether read-only diagnostics HTTP endpoints are enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether read-only diagnostics HTTP endpoints are enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the HTTP path prefix used by diagnostics endpoints. */
        public String getPathPrefix() {
            return pathPrefix;
        }

        /** Sets the HTTP path prefix used by diagnostics endpoints. */
        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix == null || pathPrefix.trim().isEmpty()
                    ? "/nexary/governance"
                    : pathPrefix.trim();
        }
    }

    /** Cooperative cancellation settings. */
    public static class Cancellation {
        private Inbound inbound = new Inbound();
        private Receiver receiver = new Receiver();

        /** Returns inbound header binding settings. */
        public Inbound getInbound() {
            return inbound;
        }

        /** Sets inbound header binding settings. */
        public void setInbound(Inbound inbound) {
            this.inbound = inbound == null ? new Inbound() : inbound;
        }

        /** Returns downstream cancellation receiver settings. */
        public Receiver getReceiver() {
            return receiver;
        }

        /** Sets downstream cancellation receiver settings. */
        public void setReceiver(Receiver receiver) {
            this.receiver = receiver == null ? new Receiver() : receiver;
        }
    }

    /** Inbound request cancellation header binding settings. */
    public static class Inbound {
        private boolean enabled = true;

        /** Returns whether inbound deadline and cancellation headers are bound to the request thread. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether inbound deadline and cancellation headers are bound to the request thread. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Downstream cancellation receiver settings. */
    public static class Receiver {
        private boolean enabled;
        private String pathPrefix = "/nexary/governance";
        private String tokenHeaderName = "Nexary-Cancellation-Token";
        private String token;

        /** Returns whether the cancellation receiver endpoint is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether the cancellation receiver endpoint is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the HTTP path prefix used by cancellation receiver endpoints. */
        public String getPathPrefix() {
            return pathPrefix;
        }

        /** Sets the HTTP path prefix used by cancellation receiver endpoints. */
        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix == null || pathPrefix.trim().isEmpty()
                    ? "/nexary/governance"
                    : pathPrefix.trim();
        }

        /** Returns the optional request header name used for receiver token checks. */
        public String getTokenHeaderName() {
            return tokenHeaderName;
        }

        /** Sets the optional request header name used for receiver token checks. */
        public void setTokenHeaderName(String tokenHeaderName) {
            this.tokenHeaderName = tokenHeaderName == null || tokenHeaderName.trim().isEmpty()
                    ? "Nexary-Cancellation-Token"
                    : tokenHeaderName.trim();
        }

        /** Returns the optional token required by the receiver endpoint. */
        public String getToken() {
            return token;
        }

        /** Sets the optional token required by the receiver endpoint. */
        public void setToken(String token) {
            this.token = token;
        }
    }

    /** Local runtime policy settings. */
    public static class Policy {
        private Duration deadline;
        private int maxRequestsPerWindow = Integer.MAX_VALUE;
        private Duration rateLimitWindow = Duration.ofSeconds(1);
        private int maxConcurrency = Integer.MAX_VALUE;
        private boolean degraded;
        private CircuitBreaker circuitBreaker = new CircuitBreaker();

        /** Returns the optional maximum time allowed for the protected action. */
        public Duration getDeadline() {
            return deadline;
        }

        /** Sets the optional maximum time allowed for the protected action. */
        public void setDeadline(Duration deadline) {
            this.deadline = deadline;
        }

        /** Returns the number of calls allowed during each rate-limit window. */
        public int getMaxRequestsPerWindow() {
            return maxRequestsPerWindow;
        }

        /** Sets the number of calls allowed during each rate-limit window. */
        public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
            this.maxRequestsPerWindow = maxRequestsPerWindow;
        }

        /** Returns the rate-limit accounting window. */
        public Duration getRateLimitWindow() {
            return rateLimitWindow;
        }

        /** Sets the rate-limit accounting window. */
        public void setRateLimitWindow(Duration rateLimitWindow) {
            this.rateLimitWindow = rateLimitWindow;
        }

        /** Returns the maximum number of concurrent calls. */
        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        /** Sets the maximum number of concurrent calls. */
        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        /** Returns whether calls should use fallback without running the main action. */
        public boolean isDegraded() {
            return degraded;
        }

        /** Sets whether calls should use fallback without running the main action. */
        public void setDegraded(boolean degraded) {
            this.degraded = degraded;
        }

        /** Returns circuit-breaker accounting settings for this policy. */
        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        /** Sets circuit-breaker accounting settings for this policy. */
        public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker == null ? new CircuitBreaker() : circuitBreaker;
        }
    }

    /** Circuit-breaker policy settings. */
    public static class CircuitBreaker {
        private boolean enabled;
        private Duration window = Duration.ofSeconds(30);
        private int minimumCalls = 10;
        private double failureRateThreshold = 50.0d;
        private Duration slowCallThreshold = Duration.ofSeconds(1);
        private double slowCallRateThreshold = 50.0d;
        private int halfOpenProbeCalls = 1;
        private Duration openStateDuration = Duration.ofSeconds(30);
        private int slidingWindowSize = 100;
        private int consecutiveFailureThreshold;

        /** Returns whether circuit-breaker accounting is enabled for this policy. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether circuit-breaker accounting is enabled for this policy. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the rolling accounting window. */
        public Duration getWindow() {
            return window;
        }

        /** Sets the rolling accounting window. */
        public void setWindow(Duration window) {
            this.window = window;
        }

        /** Returns the minimum number of calls before breaker decisions are evaluated. */
        public int getMinimumCalls() {
            return minimumCalls;
        }

        /** Sets the minimum number of calls before breaker decisions are evaluated. */
        public void setMinimumCalls(int minimumCalls) {
            this.minimumCalls = minimumCalls;
        }

        /** Returns the failure-rate threshold percentage that opens the circuit. */
        public double getFailureRateThreshold() {
            return failureRateThreshold;
        }

        /** Sets the failure-rate threshold percentage that opens the circuit. */
        public void setFailureRateThreshold(double failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        /** Returns the duration after which a call is counted as slow. */
        public Duration getSlowCallThreshold() {
            return slowCallThreshold;
        }

        /** Sets the duration after which a call is counted as slow. */
        public void setSlowCallThreshold(Duration slowCallThreshold) {
            this.slowCallThreshold = slowCallThreshold;
        }

        /** Returns the slow-call-rate threshold percentage that opens the circuit. */
        public double getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        /** Sets the slow-call-rate threshold percentage that opens the circuit. */
        public void setSlowCallRateThreshold(double slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        /** Returns the number of probe calls allowed in half-open state. */
        public int getHalfOpenProbeCalls() {
            return halfOpenProbeCalls;
        }

        /** Sets the number of probe calls allowed in half-open state. */
        public void setHalfOpenProbeCalls(int halfOpenProbeCalls) {
            this.halfOpenProbeCalls = halfOpenProbeCalls;
        }

        /** Returns how long the circuit stays open before half-open probing. */
        public Duration getOpenStateDuration() {
            return openStateDuration;
        }

        /** Sets how long the circuit stays open before half-open probing. */
        public void setOpenStateDuration(Duration openStateDuration) {
            this.openStateDuration = openStateDuration;
        }

        /** Returns the maximum number of recent calls retained for circuit decisions. */
        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        /** Sets the maximum number of recent calls retained for circuit decisions. */
        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        /** Returns the consecutive failure count that opens the circuit; zero disables this trigger. */
        public int getConsecutiveFailureThreshold() {
            return consecutiveFailureThreshold;
        }

        /** Sets the consecutive failure count that opens the circuit; zero disables this trigger. */
        public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) {
            this.consecutiveFailureThreshold = consecutiveFailureThreshold;
        }
    }

    /** Resource policy settings with optional priority-specific overrides. */
    public static class ResourcePolicy extends Policy {
        private GovernanceResource.ResourceKind kind = GovernanceResource.ResourceKind.CUSTOM;
        private String name;
        private String provider = "nexary";
        private String operation = "default";
        private Map<RequestPriority, Policy> priorities = new EnumMap<>(RequestPriority.class);

        /** Returns the governed resource kind. */
        public GovernanceResource.ResourceKind getKind() {
            return kind;
        }

        /** Sets the governed resource kind. */
        public void setKind(GovernanceResource.ResourceKind kind) {
            this.kind = kind == null ? GovernanceResource.ResourceKind.CUSTOM : kind;
        }

        /** Returns the governed resource name. */
        public String getName() {
            return name;
        }

        /** Sets the governed resource name. */
        public void setName(String name) {
            this.name = name;
        }

        /** Returns the bounded provider label for this resource. */
        public String getProvider() {
            return provider;
        }

        /** Sets the bounded provider label for this resource. */
        public void setProvider(String provider) {
            this.provider = provider;
        }

        /** Returns the stable operation name. */
        public String getOperation() {
            return operation;
        }

        /** Sets the stable operation name. */
        public void setOperation(String operation) {
            this.operation = operation;
        }

        /** Returns priority-specific policy overrides for this resource. */
        public Map<RequestPriority, Policy> getPriorities() {
            return priorities;
        }

        /** Sets priority-specific policy overrides for this resource. */
        public void setPriorities(Map<RequestPriority, Policy> priorities) {
            this.priorities = new EnumMap<>(RequestPriority.class);
            if (priorities != null) {
                this.priorities.putAll(priorities);
            }
        }
    }
}
