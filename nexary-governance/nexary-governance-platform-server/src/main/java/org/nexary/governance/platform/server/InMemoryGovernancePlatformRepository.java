package org.nexary.governance.platform.server;

import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory repository for demos and local platform smoke tests. */
public final class InMemoryGovernancePlatformRepository implements GovernancePlatformRepository {
    private final List<GovernancePlatformResourceReport> reports = new CopyOnWriteArrayList<>();
    private final List<GovernanceSignal> signals = new CopyOnWriteArrayList<>();

    @Override
    public void saveResourceReport(GovernancePlatformResourceReport report) {
        reports.add(Objects.requireNonNull(report, "report"));
    }

    @Override
    public void saveSignal(GovernanceSignal signal) {
        signals.add(Objects.requireNonNull(signal, "signal"));
    }

    @Override
    public List<GovernancePlatformResourceReport> resourceReports() {
        return new ArrayList<>(reports);
    }

    @Override
    public List<GovernanceSignal> signals() {
        return new ArrayList<>(signals);
    }
}
