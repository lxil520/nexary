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
    private RuntimeSettings runtime = new RuntimeSettings();
    private Policy defaultPolicy = new Policy();
    private Map<String, ResourcePolicy> resources = new LinkedHashMap<>();

    /** Returns runtime lifecycle settings. */
    public RuntimeSettings getRuntime() {
        return runtime;
    }

    /** Sets runtime lifecycle settings. */
    public void setRuntime(RuntimeSettings runtime) {
        this.runtime = runtime == null ? new RuntimeSettings() : runtime;
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

    /** Local runtime policy settings. */
    public static class Policy {
        private Duration deadline;
        private int maxRequestsPerWindow = Integer.MAX_VALUE;
        private Duration rateLimitWindow = Duration.ofSeconds(1);
        private int maxConcurrency = Integer.MAX_VALUE;
        private boolean degraded;

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
