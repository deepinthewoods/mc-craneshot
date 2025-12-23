Goal
- Keep Craneshot's "hide vanilla crosshair" from suppressing other mods' crosshair overlays (elytra-cursor, superbridging).

Observations
- Craneshot cancels `InGameHud.renderCrosshair(...)` at `HEAD` in `ninja.trek.mixin.client.InGameHudMixin`, which stops all `TAIL` injections (elytra-cursor, superbridging) from running.
- Both elytra-cursor and superbridging draw overlays in `renderCrosshair` at `TAIL`.

Plan
1) Update `ninja.trek.mixin.client.InGameHudMixin` to avoid cancelling the method; instead, prevent only the vanilla draw inside `renderCrosshair`.
2) Add a targeted mixin to skip the vanilla crosshair draw call(s) while letting `renderCrosshair` continue (e.g., `@Redirect` or `@ModifyExpressionValue` on the vanilla crosshair render path), conditioned on `GeneralMenuSettings.isShowVanillaCrosshair() == false`.
3) Verify exact method signatures and injection targets against current Yarn mappings before editing (per `craneshot/AGENTS.md`), then implement.
4) Manual smoke test in dev client: hide vanilla crosshair, confirm elytra-cursor/superbridging overlays still render.

Notes
- Maintain current behavior for Craneshot's own camera crosshair rendering.
- Do not add tests for this repo.
