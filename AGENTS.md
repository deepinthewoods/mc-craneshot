# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Core mod code shared across environments.
- `src/client/java`: Client-only code (camera, mixins, input hooks).
- `src/main/resources`: `fabric.mod.json`, mixin configs, assets under `assets/craneshot/`.
- Build system: Gradle with Fabric Loom; Java 21 (see `build.gradle`, `gradle.properties`).

## Build, Test, and Development Commands
- Build: `./gradlew build` (Unix) or ` .\gradlew.bat build` (Windows) — compiles, runs Loom remap, and packages the jar.
- Run client: `./gradlew runClient` — launches a dev Minecraft client with the mod.
- Clean: `./gradlew clean` — removes build outputs.
- Publish local (optional): `./gradlew publishToMavenLocal` — installs the artifact to local Maven.

## Coding Style & Naming Conventions
- Java 21, 4-space indentation, no tabs; K&R braces.
- Packages: `ninja.trek.*`. Classes `UpperCamelCase`, methods/fields `lowerCamelCase`, constants `UPPER_SNAKE_CASE`.
- Mod id and resource path stay `craneshot` (e.g., `assets/craneshot/...`). Keep mixin and mod json names in sync with package/class names.
- Prefer small, focused classes for camera movements and settings; avoid leaking client code into `main` sources.

## Testing Guidelines
- Current repository has no tests. Add JUnit 5 tests under `src/test/java` mirroring package paths.
- Name tests `*Test` (e.g., `CameraSystemTest`).
- Run tests: `./gradlew test`. Aim to cover camera math, state transitions, and serialization of settings.

## Commit & Pull Request Guidelines
- Commits: short, imperative subject lines (e.g., `fix: smooth ortho transition`). Group related changes.
- PRs: include a clear description, linked issues, and before/after screenshots or short clips for visual/camera changes.
- Checks: ensure `build` and `runClient` work locally; no unrelated refactors; update resource JSON when touching mixins/mod metadata.

## Security & Configuration Tips
- Use the Gradle wrapper (`./gradlew`) and Java 21. Do not commit secrets or local run configs.
- When renaming packages/classes referenced in mixins, update corresponding entries in `*.mixins.json` and `fabric.mod.json`.

## Docs & API References
- Fabric API: https://maven.fabricmc.net/docs/fabric-api-0.132.0+1.21.8/
- Yarn (mappings): https://maven.fabricmc.net/docs/yarn-1.21.8+build.1/
- Fabric Loader: https://maven.fabricmc.net/docs/fabric-loader-0.17.2/
