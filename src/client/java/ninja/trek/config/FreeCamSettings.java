package ninja.trek.config;

public class FreeCamSettings {
    public enum MovementMode {
        CAMERA,    // Movement relative to camera direction
        AXIS_ALIGNED  // Movement along world axes
    }

    private static float moveSpeed = 0.2f;
    private static float acceleration = 0.1f;
    private static float deceleration = 0.2f;
    private static MovementMode movementMode = MovementMode.CAMERA;

    // Getters and setters
    public static float getMoveSpeed() {
        return moveSpeed;
    }

    public static void setMoveSpeed(float speed) {
        moveSpeed = speed;
    }

    public static float getAcceleration() {
        return acceleration;
    }

    public static void setAcceleration(float acc) {
        acceleration = acc;
    }

    public static float getDeceleration() {
        return deceleration;
    }

    public static void setDeceleration(float dec) {
        deceleration = dec;
    }

    public static MovementMode getMovementMode() {
        return movementMode;
    }

    public static void setMovementMode(MovementMode mode) {
        movementMode = mode;
    }
}