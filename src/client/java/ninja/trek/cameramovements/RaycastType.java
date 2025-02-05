package ninja.trek.cameramovements;

/**
 * Enum defining different raycast behaviors for camera collision handling
 */
public enum RaycastType {
    /**
     * No raycast collision checking - camera can clip through blocks
     */
    NONE,

    /**
     * Raycasts from player to desired camera position.
     * If collision detected, moves camera closer to player.
     * Behaves like default Minecraft third person camera.
     */
    NEAR,

    /**
     * Raycasts from desired camera position towards player.
     * If camera would be inside block, moves it to first non-solid position.
     * Provides more stable distant shots by preferring to keep camera far out.
     */
    FAR
}