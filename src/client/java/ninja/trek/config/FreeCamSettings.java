package ninja.trek.config;

public class FreeCamSettings {
    private float moveSpeed = 0.2f;
    private float acceleration = 0.1f;
    private float deceleration = 0.2f;
    private float rotationEasing = 0.1f;
    private float rotationSpeedLimit = 500.0f;

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

    public float getRotationEasing() {
        return rotationEasing;
    }

    public void setRotationEasing(float easing) {
        this.rotationEasing = easing;
    }

    public float getRotationSpeedLimit() {
        return rotationSpeedLimit;
    }

    public void setRotationSpeedLimit(float limit) {
        this.rotationSpeedLimit = limit;
    }
}