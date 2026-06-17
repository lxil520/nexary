# Release Checklist

Nexary is not ready for Maven Central until namespace ownership, GitHub repository metadata, and signing infrastructure are configured.

## What Must Happen First

Before cutting a release:

- verify `./gradlew check`
- verify `./gradlew verifyReleaseGate`
- verify `./gradlew publishToMavenLocal`
- run dependency review and secret scanning in GitHub Actions
- confirm `group`, artifact names, license, SCM, and developer metadata
- confirm sources and Javadoc jars are generated
- confirm no internal endpoints, real credentials, or private registry settings exist
- create a SemVer tag such as `v0.2.0`

## Minimum Maven Central Requirements

According to Sonatype Central's public guidance, at minimum the project needs:

- verified ownership of the `org.nexary` namespace
- signed published artifacts
- POM metadata for license, developers, and SCM
- sources and Javadoc artifacts

Official references:

- [Sonatype Central publishing overview](https://central.sonatype.org/publish/publish-portal/)
- [Sonatype Central requirements](https://central.sonatype.org/publish/requirements/)
- [Gradle publishing notes](https://central.sonatype.org/publish/publish-portal-gradle/)

## Nexary Publishing Path

The pragmatic path is:

1. make the GitHub repository public and stabilize `0.2.x`
2. complete namespace verification
3. add GPG signing and credential handling
4. lock down one Gradle release pipeline
5. publish one modern-baseline `0.2.0` first

## Gradle Release Commands

Regular development validation:

```bash
./gradlew check
./gradlew verifyReleaseGate
./gradlew publishToMavenLocal
```

Before publishing to Maven Central, provide real repository metadata and signing keys:

```bash
./gradlew mavenCentralBundle \
  -PnexaryVersion=0.2.0 \
  -PprojectWebsite=https://github.com/<owner>/nexary \
  -PprojectScmUrl=https://github.com/<owner>/nexary.git \
  -PprojectScmConnection=scm:git:https://github.com/<owner>/nexary.git \
  -PprojectScmDeveloperConnection=scm:git:ssh://git@github.com:<owner>/nexary.git \
  -PnexarySigningKey="$NEXARY_SIGNING_KEY" \
  -PnexarySigningPassword="$NEXARY_SIGNING_PASSWORD"
```

This command creates `build/distributions/nexary-<version>-central-bundle.zip` only. It does not automatically publish to Central. Confirm the Sonatype namespace, signatures, public key publication, and repository metadata before uploading or automating publication.

## GitHub Release Configuration

Configure these repository variables:

- `NEXARY_PROJECT_WEBSITE`
- `NEXARY_PROJECT_SCM_URL`
- `NEXARY_PROJECT_SCM_CONNECTION`
- `NEXARY_PROJECT_SCM_DEVELOPER_CONNECTION`

Configure these repository secrets:

- `NEXARY_SIGNING_KEY`
- `NEXARY_SIGNING_PASSWORD`

Normal branch CI runs `check`, `verifyReleaseGate`, and `publishToMavenLocal`. Tags matching `v*.*.*` create a Maven Central Portal bundle as a GitHub Actions artifact.

## Central-Published Modules

Maven Central should publish framework modules, providers, starters, and the BOM only. `nexary-samples` stays as source examples and local validation projects.

## Multi-Version Policy

Nexary should plan multi-version support to reach more users, but unverified combinations must not be documented as supported.

Release policy:

- The current `0.2.x` release claims only the verified Java 17 / Spring Boot 3.3 line.
- Spring Boot 2 / JDK 8 enters an independent compatibility gap audit and verification gate.
- The README dependency matrix is expanded only after those gates pass.
- Multi-version support should prefer independent BOMs, starters, or compatibility branches instead of polluting the mainline API.

Recommended sequence:

- `0.2.x`: Java 17 / Spring Boot 3.3 mainline release plus Boot2/JDK8 gap audit.
- Later `0.2.x` patches or `0.3.x`: add Boot2/JDK8 compatibility lines according to verification results.
- Before `1.0.0`: lock the final compatibility policy and maintenance boundary.
