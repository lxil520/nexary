package org.nexary.governance.platform;

import java.time.Instant;
import java.util.List;

/** Result of an explicitly requested local connector connectivity test. */
public record GovernanceConnectorTestResult(
        String testKey,
        String connectorKey,
        boolean accepted,
        String status,
        String message,
        Instant testedAt,
        List<GovernanceConnectorCapability> capabilities) {

    /** Creates a connector test result. */
    public GovernanceConnectorTestResult {
        testKey = GovernancePlatformValidators.token(testKey, "testKey");
        connectorKey = GovernancePlatformValidators.token(connectorKey, "connectorKey");
        status = GovernancePlatformValidators.token(status == null ? "UNKNOWN" : status, "status");
        message = GovernancePlatformValidators.label(message == null ? "Connector test finished" : message, "message");
        testedAt = testedAt == null ? Instant.now() : testedAt;
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
