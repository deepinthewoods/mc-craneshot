package ninja.trek.cameramovements;

public class MovementState {
    private final CameraState cameraState;
    private final boolean isComplete;

    public MovementState(CameraState cameraState, boolean isComplete) {
        this.cameraState = cameraState;
        this.isComplete = isComplete;
    }

    public CameraState getCameraState() { return cameraState; }
    public boolean isComplete() { return isComplete; }
}
