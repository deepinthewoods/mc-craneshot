package ninja.trek.config;

public class FreeCamSettings {
    private float moveSpeed = 0.2f;
    private float acceleration = 0.1f;
    private float deceleration = 0.2f;
    private MovementMode movementMode = MovementMode.CAMERA;

    public enum MovementMode {
        CAMERA,    // Movement relative to camera direction
        AXIS_ALIGNED  // Movement along world axes
    }

    public float getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(float speed) {
        moveSpeed = speed;
    }

    public float getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(float acc) {
        acceleration = acc;
    }

    public float getDeceleration() {
        return deceleration;
    }

    public void setDeceleration(float dec) {
        deceleration = dec;
    }

    public MovementMode getMovementMode() {
        return movementMode;
    }

    public void setMovementMode(MovementMode mode) {
        movementMode = mode;
    }
}