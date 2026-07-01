package org.nexary.governance.platform.server;

import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceAuditRecord;
import org.nexary.governance.platform.GovernanceNotificationRoute;
import org.nexary.governance.platform.GovernanceNotificationTestResult;
import org.nexary.governance.platform.GovernanceReviewPlan;
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

    /** Returns retained notification test results. */
    List<GovernanceNotificationTestResult> notificationTestResults();

    /** Returns retained audit records. */
    List<GovernanceAuditRecord> auditRecords();
}
