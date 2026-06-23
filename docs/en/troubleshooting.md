# Troubleshooting

Start here when local integration fails. Each section includes commands you can run directly.

## Check the Version and Dependencies First

Make sure all copied snippets use one version. GitHub tag `v0.5.1` maps to dependency version `0.5.1`.

```bash
./gradlew properties | grep '^version:'
```

Spring Boot 3.3 / Java 17+ services should use the BOM first:

```groovy
def nexaryVersion = "0.5.1"

dependencies {
    implementation platform("com.aweimao:nexary-bom:${nexaryVersion}")
}
```

Boot2 / Boot4 entries currently use direct versions. Do not mix Boot3 starters with Boot2 or Boot4 providers in the same service.

## Sample Port Is Already Used

Check the process listening on the port:

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -nP -iTCP:8082 -sTCP:LISTEN
```

Common sample ports:

| Sample | Port |
| --- | --- |
| `nexary-sample-cache` | `8081` |
| `nexary-sample-messaging` | `8082` |
| `nexary-sample-job` | `8083` |
| `nexary-sample-cache-spi-redis` | `8091` |
| `nexary-sample-messaging-spi-disruptor` | `8092` |
| `nexary-sample-messaging-spi-redis` | `8093` |
| `nexary-sample-messaging-spi-kafka` | `8094` |
| `nexary-sample-messaging-spi-rocketmq` | `8095` |
| `nexary-sample-job-spi-scheduler` | `8096` |
| `nexary-sample-job-spi-xxljob` | `8097` |
| `nexary-sample-messaging-spi-activemq-classic` | `8098` |
| `nexary-sample-job-spi-powerjob` | no fixed HTTP port |

For a temporary port change, pass a Spring Boot argument:

```bash
./gradlew :nexary-samples:nexary-sample-cache:run --args='--server.port=18081'
```

## Local Middleware Is Not Running

Start the local stack first, then run smoke checks:

```bash
./scripts/middleware/up.sh
./scripts/middleware/smoke.sh
```

If smoke checks fail, inspect container status:

```bash
docker compose -f deploy/middleware/docker-compose.yml ps
docker compose -f deploy/middleware/docker-compose.yml logs --tail=80
```

Default ports:

| Middleware | Local Port |
| --- | --- |
| Redis | `16379` |
| Valkey | `16380` |
| Kafka | `19092` |
| RocketMQ NameServer | `19876` |
| ActiveMQ Classic | `61616` |

## Wrong Provider Selected

First check whether you use a starter or API + provider dependency mode.

Starter mode selects the provider through configuration:

```yaml
nexary:
  messaging:
    provider: redis
```

API + provider mode should add exactly one matching runtime provider, for example:

```groovy
dependencies {
    implementation "com.aweimao:nexary-messaging-api:0.5.1"
    runtimeOnly "com.aweimao:nexary-messaging-redis:0.5.1"
}
```

Do not put multiple providers for the same capability on the runtime path unless the sample docs explicitly say that setup is supported.

## Job Cron Does Not Fire

Local scheduler cron lives under `nexary.job.scheduler.schedules`. `job-name` must equal the value returned by `NexaryJob.name()`.

```yaml
nexary:
  job:
    provider: local
    scheduler:
      schedules:
        - job-name: sample-business-job
          cron: "0 */5 * * * *"
```

XXL-JOB and PowerJob timing is owned by the external platform. The Nexary bridge maps external triggers into the `NexaryJob` execution lifecycle.

## Metrics Are Missing

Make sure the Micrometer starter is present:

```groovy
implementation "com.aweimao:nexary-observation-micrometer-spring-boot-starter:0.5.1"
```

Then execute the business path. Nexary reports metrics only from cache, messaging, job, and governance boundary events; it does not create unrelated business metrics.

Default metric names:

| Metric | Meaning |
| --- | --- |
| `nexary.observation.events.total` | event count |
| `nexary.observation.events.duration` | event duration |

Filter cache, messaging, job, and governance events by tags such as `category`, `operation`, `provider`, and `outcome`.

Only whitelist tags are kept. Cache keys, message ids, execution ids, payloads, exception text, and stack traces are not exported as metric tags.

## Local Checks Before Release

Run at least:

```bash
./gradlew check
./gradlew verifyReleaseGate
./gradlew compatibilityAudit
```

Creating a Maven Central bundle requires signing keys on the local machine or in CI:

```bash
./gradlew mavenCentralBundle -PnexaryVersion=0.5.1
```

The final publication must run from a Git tag. Manual workflow runs default to bundle-only checks and do not publish to Maven Central by default.
