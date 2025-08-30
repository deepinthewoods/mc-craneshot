Agent Workflow and Validation Rules

Scope: These rules guide contributors and AI assistants working in this repo. Follow them before making code changes that touch Minecraft/Fabric internals (especially mixins), and when handling build issues.

Primary Sources (keep in sync with gradle.properties)
- Fabric API docs: https://maven.fabricmc.net/docs/fabric-api-0.132.0+1.21.8/
- Yarn (mappings): https://maven.fabricmc.net/docs/yarn-1.21.8+build.1/
- Fabric Loader docs: https://maven.fabricmc.net/docs/fabric-loader-0.17.2/

Before Writing or Modifying Mixins
- Verify method signatures: Look up the exact mapped signature (name, params, return) in the Yarn docs for the current `minecraft_version`/`yarn_mappings`. Do not guess parameter lists.
- Confirm target owner and side: Ensure the target class is client-side for client mixins and that the package in the mixin JSON matches the Java package.
- Check injection points: Confirm the method exists at runtime with the expected descriptor. Prefer `method = "name(L...;...)V"` descriptors when the method is overloaded.
- Validate mixin config wiring:
  - Client mixins config file lives at `src/client/resources/craneshot.client.mixins.json`.
  - `fabric.mod.json` must include the client mixin config under the `mixins` section with `environment: "client"`.
  - Package in the JSON must match the Java package: `ninja.trek.mixin.client`.
- Add lightweight runtime proof: Temporarily log once in the injected method head (guarded to avoid spam) to confirm it fires. Remove or demote to debug once validated.

When Build Errors Occur
- Search the linked docs first (Fabric API, Yarn, Loader). Cross-check changed API signatures, removed methods, or renamed classes for the current versions in `gradle.properties`.
- Read Loom output carefully; resolve descriptor/signature mismatches before proceeding. Do not silence or bypass mixin errors.
- If deprecation or signature changes are suspected, open the decompiled sources from the Loom cache or IDE navigation to verify real signatures.

Common Pitfalls Checklist
- Wrong mixin JSON package or file not referenced in `fabric.mod.json`.
- Injection method signature does not exactly match runtime signature.
- Overloaded targets without explicit descriptor in `@Inject(method = ...)`.
- Target method moved/renamed across MC versions; update to the correct Yarn name.
- Client-only classes used in `src/main/java` (keep client code in `src/client/java`).

Repository Conventions (summary)
- Java 21; 4 spaces; K&R braces.
- Mod id/resources: `craneshot`.
- Packages under `ninja.trek.*`.
- Don’t add or run tests for this repo.

