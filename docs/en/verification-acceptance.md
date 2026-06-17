# Validation Checklist

Before a release or after an important capability change, record at least:

- affected area: cache, messaging, job, boot, docs, or samples.
- baseline commands that were run, such as `./gradlew check`.
- whether local middleware was started and `./scripts/middleware/smoke.sh` passed.
- whether real middleware integration tests were run.
- whether `publishToMavenLocal` passed.
- affected sample and documentation paths.
- known residual risks and explicitly unsupported claims.

## Release-Blocking Cases

- Public APIs expose provider-native types.
- Copyable user samples require reading or modifying provider wiring before the basic usage is clear.
- Chinese and English docs disagree.
- Middleware scripts cannot run while the change depends on real middleware behavior.
- Failing tests are unexplained, have no risk classification, or still affect the main path.
- Documentation claims unverified capabilities such as strong consistency, exactly-once delivery, distributed transactions, or platform-managed lifecycle.
