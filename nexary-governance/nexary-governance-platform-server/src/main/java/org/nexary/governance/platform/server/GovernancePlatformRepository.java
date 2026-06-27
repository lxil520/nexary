package org.nexary.governance.platform.server;

import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceSignal;

import java.util.List;

/** Storage boundary for read-only governance platform assets and signals. */
public interface GovernancePlatformRepository {

    /** Stores or replaces the latest resource report. */
    void saveResourceReport(GovernancePlatformResourceReport report);

    /** Stores a platform signal. */
    void saveSignal(GovernanceSignal signal);

    /** Returns retained resource reports. */
    List<GovernancePlatformResourceReport> resourceReports();

    /** Returns retained platform signals. */
    List<GovernanceSignal> signals();
}
