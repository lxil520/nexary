package org.nexary.governance.platform.server;

import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceAuditRecord;
import org.nexary.governance.platform.GovernanceConnectorConfig;
import org.nexary.governance.platform.GovernanceConnectorTestResult;
import org.nexary.governance.platform.GovernanceNotificationRoute;
import org.nexary.governance.platform.GovernanceNotificationTestResult;
import org.nexary.governance.platform.GovernanceReviewPlan;
import org.nexary.governance.platform.GovernanceServiceMapping;
import org.nexary.governance.platform.GovernanceSignal;

import java.util.List;
import java.util.Optional;

/** Storage boundary for platform evidence plus local controlled-governance data. */
public interface GovernancePlatformRepository {

    /** Stores or replaces the latest resource report. */
    void saveResourceReport(GovernancePlatformResourceReport report);

    /** Stores a platform signal. */
    void saveSignal(GovernanceSignal signal);

    /** Stores or replaces a local governance review plan. */
    void saveReviewPlan(GovernanceReviewPlan plan);

    /** Stores or replaces local notification route metadata. */
    void saveNotificationRoute(GovernanceNotificationRoute route);

    /** Stores or replaces a local connector configuration. */
    void saveConnectorConfig(GovernanceConnectorConfig config);

    /** Stores a connector test result. */
    void saveConnectorTestResult(GovernanceConnectorTestResult result);

    /** Stores or replaces a local service mapping. */
    void saveServiceMapping(GovernanceServiceMapping mapping);

    /** Stores a notification test result. */
    void saveNotificationTestResult(GovernanceNotificationTestResult result);

    /** Stores a local audit record. */
    void saveAuditRecord(GovernanceAuditRecord record);

    /** Returns retained resource reports. */
    List<GovernancePlatformResourceReport> resourceReports();

    /** Returns retained platform signals. */
    List<GovernanceSignal> signals();

    /** Returns local governance review plans. */
    List<GovernanceReviewPlan> reviewPlans();

    /** Returns one local governance review plan by key. */
    Optional<GovernanceReviewPlan> reviewPlan(String planKey);

    /** Returns local notification routes. */
    List<GovernanceNotificationRoute> notificationRoutes();

    /** Returns one local notification route by key. */
    Optional<GovernanceNotificationRoute> notificationRoute(String routeKey);

    /** Returns local connector configurations. */
    List<GovernanceConnectorConfig> connectorConfigs();

    /** Returns one local connector configuration by key. */
    Optional<GovernanceConnectorConfig> connectorConfig(String connectorKey);

    /** Returns retained connector test results. */
    List<GovernanceConnectorTestResult> connectorTestResults();

    /** Returns local service mappings. */
    List<GovernanceServiceMapping> serviceMappings();

    /** Returns one local service mapping by key. */
    Optional<GovernanceServiceMapping> serviceMapping(String mappingKey);

    /** Returns retained notification test results. */
    List<GovernanceNotificationTestResult> notificationTestResults();

    /** Returns retained audit records. */
    List<GovernanceAuditRecord> auditRecords();
}
