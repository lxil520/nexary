package org.nexary.governance.platform.server;

import org.nexary.governance.platform.GovernanceAuditRecord;
import org.nexary.governance.platform.GovernanceConnectorConfig;
import org.nexary.governance.platform.GovernanceConnectorTestResult;
import org.nexary.governance.platform.GovernanceNotificationRoute;
import org.nexary.governance.platform.GovernanceNotificationTestResult;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceReviewPlan;
import org.nexary.governance.platform.GovernanceServiceMapping;
import org.nexary.governance.platform.GovernanceSignal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory repository for demos and local platform smoke tests. */
public final class InMemoryGovernancePlatformRepository implements GovernancePlatformRepository {
    private final List<GovernancePlatformResourceReport> reports = new CopyOnWriteArrayList<>();
    private final List<GovernanceSignal> signals = new CopyOnWriteArrayList<>();
    private final Map<String, GovernanceReviewPlan> reviewPlans = new LinkedHashMap<>();
    private final Map<String, GovernanceNotificationRoute> notificationRoutes = new LinkedHashMap<>();
    private final Map<String, GovernanceConnectorConfig> connectorConfigs = new LinkedHashMap<>();
    private final List<GovernanceConnectorTestResult> connectorTests = new CopyOnWriteArrayList<>();
    private final Map<String, GovernanceServiceMapping> serviceMappings = new LinkedHashMap<>();
    private final List<GovernanceNotificationTestResult> notificationTests = new CopyOnWriteArrayList<>();
    private final List<GovernanceAuditRecord> auditRecords = new CopyOnWriteArrayList<>();

    @Override
    public void saveResourceReport(GovernancePlatformResourceReport report) {
        reports.add(Objects.requireNonNull(report, "report"));
    }

    @Override
    public void saveSignal(GovernanceSignal signal) {
        signals.add(Objects.requireNonNull(signal, "signal"));
    }

    @Override
    public synchronized void saveReviewPlan(GovernanceReviewPlan plan) {
        Objects.requireNonNull(plan, "plan");
        reviewPlans.put(plan.planKey(), plan);
    }

    @Override
    public synchronized void saveNotificationRoute(GovernanceNotificationRoute route) {
        Objects.requireNonNull(route, "route");
        notificationRoutes.put(route.routeKey(), route);
    }

    @Override
    public synchronized void saveConnectorConfig(GovernanceConnectorConfig config) {
        Objects.requireNonNull(config, "config");
        connectorConfigs.put(config.connectorKey(), config);
    }

    @Override
    public void saveConnectorTestResult(GovernanceConnectorTestResult result) {
        connectorTests.add(Objects.requireNonNull(result, "result"));
    }

    @Override
    public synchronized void saveServiceMapping(GovernanceServiceMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        serviceMappings.put(mapping.mappingKey(), mapping);
    }

    @Override
    public void saveNotificationTestResult(GovernanceNotificationTestResult result) {
        notificationTests.add(Objects.requireNonNull(result, "result"));
    }

    @Override
    public void saveAuditRecord(GovernanceAuditRecord record) {
        auditRecords.add(Objects.requireNonNull(record, "record"));
    }

    @Override
    public List<GovernancePlatformResourceReport> resourceReports() {
        return new ArrayList<>(reports);
    }

    @Override
    public List<GovernanceSignal> signals() {
        return new ArrayList<>(signals);
    }

    @Override
    public synchronized List<GovernanceReviewPlan> reviewPlans() {
        return new ArrayList<>(reviewPlans.values());
    }

    @Override
    public synchronized Optional<GovernanceReviewPlan> reviewPlan(String planKey) {
        return Optional.ofNullable(reviewPlans.get(planKey));
    }

    @Override
    public synchronized List<GovernanceNotificationRoute> notificationRoutes() {
        return new ArrayList<>(notificationRoutes.values());
    }

    @Override
    public synchronized Optional<GovernanceNotificationRoute> notificationRoute(String routeKey) {
        return Optional.ofNullable(notificationRoutes.get(routeKey));
    }

    @Override
    public synchronized List<GovernanceConnectorConfig> connectorConfigs() {
        return new ArrayList<>(connectorConfigs.values());
    }

    @Override
    public synchronized Optional<GovernanceConnectorConfig> connectorConfig(String connectorKey) {
        return Optional.ofNullable(connectorConfigs.get(connectorKey));
    }

    @Override
    public List<GovernanceConnectorTestResult> connectorTestResults() {
        return new ArrayList<>(connectorTests);
    }

    @Override
    public synchronized List<GovernanceServiceMapping> serviceMappings() {
        return new ArrayList<>(serviceMappings.values());
    }

    @Override
    public synchronized Optional<GovernanceServiceMapping> serviceMapping(String mappingKey) {
        return Optional.ofNullable(serviceMappings.get(mappingKey));
    }

    @Override
    public List<GovernanceNotificationTestResult> notificationTestResults() {
        return new ArrayList<>(notificationTests);
    }

    @Override
    public List<GovernanceAuditRecord> auditRecords() {
        return new ArrayList<>(auditRecords);
    }
}
