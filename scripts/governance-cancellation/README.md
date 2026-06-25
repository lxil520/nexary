# Governance Cancellation Smoke

This script checks the v0.11 request cancellation path against a running `nexary-sample-governance` application.

Start the downstream sample on a known port:

```bash
./gradlew :nexary-samples:nexary-sample-governance:run --args='--server.port=28091'
```

Then run the smoke check:

```bash
NEXARY_GOVERNANCE_CANCELLATION_BASE_URL=http://localhost:28091 \
./scripts/governance-cancellation/smoke.sh
```

Expected checks:

- a propagated cancellation signal stops the sample before doing useful work
- `cancelledCount` is visible in local diagnostics
- `CLIENT_DISCONNECTED` is visible in recent events
- the cancellation id is not exposed by diagnostics
- the check remains local to the current JVM and does not claim Sentinel behavior
