# Core Concepts

## Why Nexary Exists

Nexary is not trying to replace Redis, Kafka, RocketMQ, or Spring. It provides a small middleware layer that:

- keeps business code away from provider-native types
- makes common cache, messaging, and job patterns consistent
- introduces shared resilience semantics early

## Cache

- `CacheKey`: namespace + key pair
- `CacheClient`: get, put, put-if-absent, batch, expire, tryLock
- `cacheAside(...)`: standard read-through helper
- `CacheCounterKey`: counter-specific key, keeping counters separate from ordinary object cache entries
- `CacheCounterClient`: increment, decrement, current, and clear; implementations must use provider atomic counter capabilities and must not enter JVM-local L1
- `LockHandle`: owner-token lock handle; the Redis provider can return a fencing token when acquisition succeeds
- `fencingToken()`: monotonic token that callers carry to the protected resource; validation happens at that protected resource and does not replace business-side version checks

## Messaging

- `MessageEnvelope`: topic, key, payload, headers, deadline, traffic tag
- `MessagePublisher`: Nexary-level publish contract
- `MessagePublishResult`: normalized publish result
- `MessageInterceptor`: hook point before and after publish/consume flows

## Job

- `NexaryJob`: executable unit
- `JobSchedule`: cron-based schedule definition
- `NexaryJobScheduler`: schedule/cancel contract
- `JobExecutionListener`: post-execution callback

## Governance

- `DeadlineContext`: prevents requests from running after their useful time window
- `TrafficTag`: carries tenant, priority, and business labels
- `RetrySignal`: tells upstream code whether to continue retries
- `FaultSignal`: turns provider-specific failures into shared categories
- `NexaryObservationEvent`: prepares later metrics and tracing integration
