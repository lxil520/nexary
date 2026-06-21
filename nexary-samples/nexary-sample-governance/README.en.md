# nexary-sample-governance

This sample shows local protection in a Spring Boot service: policies live in `application.yml`, while the business entry point names the resource, attaches traffic tags and a deadline, then runs business code through `GovernanceRuntime`.

The sample also adds `nexary-observation-micrometer-spring-boot-starter` and registers a local `SimpleMeterRegistry`. When rate limit, degradation, or bulkhead events happen, governance events are recorded in `nexary.observation.events.total` and `nexary.observation.events.duration`.

## Run

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

Available endpoints:

- `GET /governance/profiles/{userId}`: uses the `profile-api/get-profile` policy. More than `2/min` goes to fallback.
- `GET /governance/degraded/{userId}`: uses the `inventory-service/reserve` policy. `degraded=true` goes directly to fallback.

## Configuration to Copy

```yaml
nexary:
  governance:
    resources:
      profile-api:
        kind: http
        name: profile-api
        operation: get-profile
        deadline: 300ms
        max-requests-per-window: 2
        rate-limit-window: 1m
        max-concurrency: 1
```

## Code to Copy

- `GovernanceSampleConfiguration`: stable resource names, not policies.
- `GovernanceSampleController`: how a business entry point creates `GovernanceContext` and calls `GovernanceRuntime`.
- `ProfileQueryService`: keeps main logic and fallback logic separate without depending on a third-party governance framework.

Keep resource names stable, such as `profile-api/get-profile`. Do not put user ids, order ids, cache keys, or message ids into resource names or governance tags. Micrometer meters keep only bounded tags such as `resource_kind`, `governance_action`, `traffic_channel`, `traffic_priority`, and `outcome`.
