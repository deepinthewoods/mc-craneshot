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
     * Uses near raycasting, but lets the active movement interpolate toward
     * the collision-adjusted position using its normal position settings.
     */
    SOFT_NEAR,

    /**
     * Raycasts from desired camera position towards player.
     * If camera would be inside block, moves it to first non-solid position.
     * Provides more stable distant shots by preferring to keep camera far out.
     */
    FAR,

    /**
     * Uses far raycasting, but lets the active movement interpolate toward
     * the collision-adjusted position using its normal position settings.
     */
    SOFT_FAR;

    public boolean isSoft() {
        return this == SOFT_NEAR || this == SOFT_FAR;
    }
}
