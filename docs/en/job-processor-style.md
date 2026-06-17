# Processor-Style Job Integration

Processor-style integration targets production job executor processes. It is different from the focused adoption sample:

- the focused sample explains local scheduling and bridge triggers
- processor-style runs as a non-web process closer to a real job executor

## Target Shape

A processor-style job process should:

- start with Spring Boot and `WebApplicationType.NONE`
- register job handlers as Spring `@Component` beans
- implement Nexary's `NexaryJob`
- receive shard and runtime context through `JobContext`

Business handlers are the stable layer. Processor startup, external-platform trigger adapters, executor registration, and receipt delivery should not be written into business handlers.

## Current Reference Skeleton

The reference skeleton lives in:

```text
nexary-samples/nexary-sample-job/src/main/java/org/nexary/samples/job/processor/
```

Key classes:

- `JobProcessorSampleApplication`: non-web startup entry point
- `ProcessorBusinessJob`: component-scanned job handler

## Relationship to the Focused Sample

`nexary-sample-job` now has two clear paths:

- starter selector focused sample: switches between local scheduling and the XXL-JOB bridge-shaped provider through `nexary.job.provider`
- `processor`: non-web production-style job process skeleton

Both share the `NexaryJob` abstraction, but they serve different usage modes. Do not treat the starter selector sample as the processor production shape, and do not treat the processor skeleton as complete external platform integration evidence.

The focused sample's `business/SampleBusinessJob` shows the cleaner adoption shape: the business handler implements only `NexaryJob`, and switching between local scheduling and the XXL-JOB bridge does not change business code.

## How to Run

```bash
./gradlew :nexary-samples:nexary-sample-job:runProcessor
```

The task starts a non-web Spring Boot process with the `processor` profile. Job execution enters the business job through the active Nexary provider or an external platform bridge.

## Relationship to the XXL-JOB Bridge

The current processor skeleton shows the standalone process shape and business job handler style.

It does not claim:

- real XXL-JOB executor registration
- Admin scheduling
- full platform callback lifecycle

Those require separate Docker-backed integration evidence.

## Public API Boundary

Processor-style integration exposes only Nexary's own job API. Handler types, parameter types, and naming from third-party or private implementations do not enter the Nexary public API.

The design maps to:

- job handler: implement `NexaryJob`
- runtime parameters: use `JobContext`

## Non-Goals

- do not add a second public job handler API
- do not make RPC, MQ, or cache collaborators part of the job API
- do not implement the PowerJob bridge in the current version
- do not present the processor skeleton as complete external platform integration evidence
