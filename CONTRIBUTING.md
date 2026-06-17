# Contributing

- Keep public packages under `org.nexary.*`.
- Keep public APIs provider-neutral.
- Keep new user-facing docs bilingual under `docs/zh` and `docs/en`.
- Use `Duration` for time settings and enum types for states.
- Run `./gradlew check` before opening a pull request.
- Run `./gradlew publishToMavenLocal` for release-facing changes.
- If a change affects sample usage, update the corresponding sample README.
- If a change affects release scope or major capability direction, align it with `docs/zh/roadmap.md` and `docs/en/roadmap.md`.
- Do not commit secrets, private endpoints, or internal business terms.
