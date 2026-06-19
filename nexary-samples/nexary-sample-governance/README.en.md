# nexary-sample-governance

This sample shows local protection in a Spring Boot service: name a resource, attach traffic tags and a deadline, then run business code through `GovernanceRuntime`.

The sample also adds `nexary-observation-micrometer-spring-boot-starter` and registers a local `SimpleMeterRegistry`. When rate limit, degradation, or bulkhead events happen, governance events are recorded in `nexary.observation.events.total` and `nexary.observation.events.duration`.

## Run

```bash
./gradlew :nexary-samples:nexary-sample-governance:run
```

Available endpoints:

- `GET /governance/profiles/{userId}`: normal profile lookup, with fallback after the local rate limit is hit.
- `GET /governance/degraded/{userId}`: manual degradation that goes directly to fallback.

## Code to Copy

- `GovernanceSampleConfiguration`: local policy setup.
- `GovernanceSampleConfiguration#governanceSampleMeterRegistry`: local Micrometer registry for the sample; real services can use their own registry.
- `GovernanceSampleController`: how a business entry point creates `GovernanceContext` and calls `GovernanceRuntime`.
- `ProfileQueryService`: keeps main logic and fallback logic separate without depending on a third-party governance framework.

Keep resource names stable, such as `profile-api/get-profile`. Do not put user ids, order ids, cache keys, or message ids into resource names or governance tags. Micrometer meters keep only bounded tags such as `resource_kind`, `governance_action`, `traffic_channel`, `traffic_priority`, and `outcome`.
