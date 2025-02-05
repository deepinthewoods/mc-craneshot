package ninja.trek.cameramovements;

public class MovementState {
    private final CameraTarget target;
    private final boolean isComplete;

    public MovementState(CameraTarget target, boolean isComplete) {
        this.target = target;
        this.isComplete = isComplete;
    }

    public CameraTarget getCameraTarget() {
        return target;
    }

    public boolean isComplete() {
        return isComplete;
    }
}