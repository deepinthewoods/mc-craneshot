package ninja.trek.nodes.model;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public class CameraNode {
    public UUID id = UUID.randomUUID();
    public String name = "Node";
    public NodeType type = NodeType.CAMERA_CONTROL;
    public Vec3 position = Vec3.ZERO;
    public Integer colorARGB = 0xFFFF8800; // default orange
    public UUID owner = null;

    // DroneShot params (used when type == DRONE_SHOT)
    public double droneRadius = 6.0; // blocks
    public double droneSpeedDegPerSec = 30.0; // degrees per second
    public double droneStartAngleDeg = 0.0;

    // Timelapse params (used when type == TIMELAPSE)
    public float timelapseYaw = 0f;
    public float timelapsePitch = 0f;
    public float timelapseFovMultiplier = 1.0f;
    public int timelapseIndex = 0;
    public boolean timelapseEnabled = true;

    // Optional Gold Golem-managed framing metadata.
    public boolean autoManaged = false;
    public UUID buildSessionId = null;
    public UUID trackedEntityId = null;
    public String buildMode = "";
    public String buildState = "";
    public Vec3 framingMin = null;
    public Vec3 framingMax = null;
    public float autoRigYaw = 0f;
}
