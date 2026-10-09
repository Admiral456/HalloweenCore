# Vampire King — ModelEngine animation synchronization

The MythicMobs spawn skill attaches the vampire_king blueprint. HalloweenCore then invokes the ModelEngine animation handler reflectively:

- attack is requested immediately before each live special attack telegraph and delayed impact.
- fly is requested once on entering phase 4 and loops while the final phase continues; the one-shot attack animation is requested on top of it for each special ability.

The reflection bridge expects the ModelEngine R4 API shape ModelEngineAPI.getModeledEntity(Entity) -> ModeledEntity.getModel(String) -> ActiveModel.getAnimationHandler() -> AnimationHandler.playAnimation(String, double, double, double, boolean). If the optional API is unavailable, HalloweenCore logs at most one warning and continues its gameplay telegraphs and delayed damage. No direct ModelEngine dependency is added to the plugin build.

## Staging verification
1. Import the blueprint and reload ModelEngine.
2. Spawn the MythicMobs definition in staging and verify idle/walk states.
3. Use the Halloween boss-effects preview, then test the live encounter in staging to confirm the attack animation is visible before impact.
4. Enter phase 4 and confirm the wings use the looping fly animation while attacks still telegraph and damage correctly.
5. Keep bosses.vampire.model.ready: false until the client-side test passes.
