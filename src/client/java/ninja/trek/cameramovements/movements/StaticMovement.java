package ninja.trek.cameramovements.movements;

import ninja.trek.cameramovements.*;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.AreaMovementConfig;
import ninja.trek.nodes.model.CameraNode;

import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

@CameraMovementType(
        name = "Static",
        description = "Locks the camera to specified nodes for area-driven control"
)
public class StaticMovement extends AbstractMovementSettings implements ICameraMovement {
    public static final String MOVEMENT_ID = "craneshot:static";

    private UUID positionNodeId = null;
    private UUID lookNodeId = null;

    @Override
    public void start(Minecraft client, Camera camera) {
        // No runtime state to initialize; static targets are resolved on demand
    }

    @Override
    public MovementState calculateState(Minecraft client, Camera camera, float tickDelta, float deltaSeconds) {
        // Fallback behaviour when invoked through the generic movement system: remain at current camera state
        CameraTarget target = CameraTarget.fromCamera(camera);
        return new MovementState(target, true);
    }

    @Override
    public void queueReset(Minecraft client, Camera camera) {
        // No reset path required
    }

    @Override
    public void adjustDistance(boolean increase, Minecraft client) {
        // Static movement has no distance parameter in the generic context
    }

    @Override
    public String getName() {
        return "Static";
    }

    @Override
    public float getWeight() {
        return 1.0f;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public RaycastType getRaycastType() {
        return RaycastType.NONE;
    }

    public UUID getPositionNodeId() {
        return positionNodeId;
    }

    public void setPositionNodeId(UUID positionNodeId) {
        this.positionNodeId = positionNodeId;
    }

    public UUID getLookNodeId() {
        return lookNodeId;
    }

    public void setLookNodeId(UUID lookNodeId) {
        this.lookNodeId = lookNodeId;
    }

    public static CameraTarget resolveTarget(NodeManager manager, AreaMovementConfig config, CameraTarget base) {
        if (manager == null || config == null) return null;

        UUID posId = parseUuid(config.settings.get("positionNodeId"));
        if (posId == null) return null;
        CameraNode positionNode = manager.getNode(posId);
        if (positionNode == null || positionNode.position == null) return null;

        Vec3 position = positionNode.position;
        float yaw = base != null ? base.getYaw() : 0f;
        float pitch = base != null ? base.getPitch() : 0f;
        float fov = base != null ? base.getFovMultiplier() : 1.0f;

        UUID lookId = parseUuid(config.settings.get("lookNodeId"));
        if (lookId != null) {
            CameraNode lookNode = manager.getNode(lookId);
            if (lookNode != null && lookNode.position != null) {
                Vec3 dir = lookNode.position.subtract(position);
                if (dir.lengthSqr() > 1e-6) {
                    dir = dir.normalize();
                    yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                    pitch = (float) (-Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0, 1.0))));
                }
            }
        }

        Object fovSetting = config.settings.get("fovMultiplier");
        if (fovSetting instanceof Number number) {
            fov = Math.max(0.1f, number.floatValue());
        }

        float ortho = base != null ? base.getOrthoFactor() : 0f;
        return new CameraTarget(position, yaw, pitch, fov, ortho);
    }

    private static UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
