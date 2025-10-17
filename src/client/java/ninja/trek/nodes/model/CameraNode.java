package ninja.trek.nodes.model;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CameraNode {
    public UUID id = UUID.randomUUID();
    public String name = "Node";
    public NodeType type = NodeType.CAMERA_CONTROL;
    public Vec3d position = Vec3d.ZERO;
    public Integer colorARGB = 0xFFFF8800; // default orange
    public Vec3d lookAt = null; // optional
    public final List<Area> areas = new ArrayList<>();

    // DroneShot params (used when type == DRONE_SHOT)
    public double droneRadius = 6.0; // blocks
    public double droneSpeedDegPerSec = 30.0; // degrees per second
    public double droneStartAngleDeg = 0.0;
}

