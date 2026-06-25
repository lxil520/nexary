# Release Checklist

This page is the public release runbook. Examples use `0.11.1`; replace the version everywhere when cutting another patch release.

## Pre-Release Checks

Before a release, complete at least these checks:

- `scripts/release/preflight.sh 0.11.1`
- `./gradlew check`
- `./gradlew verifyReleaseGate`
- `./gradlew publishToMavenLocal`
- run the full command from "Build a Central Bundle Locally" with `./gradlew mavenCentralBundle -PnexaryVersion=0.11.1`, then confirm `build/distributions/nexary-0.11.1-central-bundle.zip` exists
- build, dependency review, and secret scan pass in GitHub Actions
- POM `group`, artifact names, license, developer, and SCM metadata are correct
- sources jars, Javadoc jars, `.pom`, `.module`, and signature files are present in the Central bundle
- the repository contains no real credentials, private endpoints, or private registry settings
- `nexary-samples` remains source examples and local validation projects, not Central-published modules

## Maven Central Prerequisites

Before publishing to Maven Central, confirm these external prerequisites:

- The `com.aweimao` namespace is verified in Sonatype Central Portal.
- The PGP public key is published to a keyserver reachable by Sonatype Central validation, and the private key is used only through GitHub secrets or temporary local environment variables.
- A Central Portal token has been created; GitHub secrets use the token username and password, not a personal login password.
- GitHub repository, SCM URL, and developer connection metadata point to `lxil520/nexary`.

Required GitHub variables:

- `NEXARY_PROJECT_WEBSITE=https://github.com/lxil520/nexary`
- `NEXARY_PROJECT_SCM_URL=https://github.com/lxil520/nexary.git`
- `NEXARY_PROJECT_SCM_CONNECTION=scm:git:https://github.com/lxil520/nexary.git`
- `NEXARY_PROJECT_SCM_DEVELOPER_CONNECTION=scm:git:ssh://git@github.com:lxil520/nexary.git`

Required GitHub secrets:

- `NEXARY_SIGNING_KEY`
- `NEXARY_SIGNING_PASSWORD`
- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`

## Build a Central Bundle Locally

This local command creates the upload bundle only. It does not publish to Central:

```bash
./gradlew mavenCentralBundle \
  -PnexaryVersion=0.11.1 \
  -PprojectWebsite=https://github.com/lxil520/nexary \
  -PprojectScmUrl=https://github.com/lxil520/nexary.git \
  -PprojectScmConnection=scm:git:https://github.com/lxil520/nexary.git \
  -PprojectScmDeveloperConnection=scm:git:ssh://git@github.com:lxil520/nexary.git \
  -PnexarySigningKey="$NEXARY_SIGNING_KEY" \
  -PnexarySigningPassword="$NEXARY_SIGNING_PASSWORD"
```

After the bundle is generated, inspect it:

```bash
unzip -l build/distributions/nexary-0.11.1-central-bundle.zip | grep 'nexary-bom/0.11.1'
unzip -l build/distributions/nexary-0.11.1-central-bundle.zip | grep '.asc'
unzip -l build/distributions/nexary-0.11.1-central-bundle.zip | grep '.sha1'
```

## GitHub Actions Release

Use a tag for the real release:

```bash
git tag v0.11.1
git push origin v0.11.1
```

`release.yml` builds the Central Portal bundle from the tagged commit and uploads it as a GitHub Actions artifact. When Central secrets are configured, a tag push also uploads and publishes the Central deployment.

After the release run is created, use the script for a compact summary instead of repeatedly refreshing browser logs:

```bash
scripts/release/watch-github-run.sh <run-id>
```

If the Central token is missing, the publish step fails instead of marking a tag run successful without publishing to Central. For a bundle-only check, run `workflow_dispatch`, enter `0.11.1` or `v0.11.1`, and keep `publish_to_central=false`. Manual Central publication must run from an existing tag ref, and the entered version must match the selected tag; do not publish a Central deployment manually from the `main` branch.

## Check Maven Central After Publication

After Central Portal shows published, check Maven Central sync:

```bash
curl -I https://repo.maven.apache.org/maven2/com/aweimao/nexary-bom/0.11.1/nexary-bom-0.11.1.pom
curl -I https://repo.maven.apache.org/maven2/com/aweimao/nexary-framework/nexary-core/0.11.1/nexary-core-0.11.1.jar
```

Or run:

```bash
scripts/release/check-central.sh 0.11.1
```

Update the GitHub Release notes and README version guidance only after Maven Central has synced. Do not tell users to copy a Maven Central version before it is visible there.

## Failure Handling

- Local validation failed: fix the issue and rerun local commands before creating a tag.
- Bundle generation failed: check Gradle, JDK 21, signing secrets, SCM metadata, and the Central module list.
- Central validation failed before publication: drop or delete the deployment in Central Portal, fix the issue, and rebuild the bundle; if the tag points to the wrong commit, delete the local and remote tag before recreating it on the correct commit.
- Central already published but a problem was found: published Maven Central versions cannot be rolled back or reused; release a new patch version and document the affected version in GitHub Releases.
- GitHub Actions is stuck while polling Central status: check the deployment in Central Portal before rerunning the workflow. Do not push the same tag again without confirming Central state.

## Current Support Claims

`0.5.x` claims only combinations that have passed gates. Spring Boot 2.7 / JDK 8+, Spring Boot 3.3 / Java 17+, and Spring Boot 4.1 / Java 21 support statements come from the README and capability docs; providers that have not been verified are not documented as supported.
