package ninja.trek.nodes.model;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class CameraNodeDTO {
    public static final int CURRENT_VERSION = 4;

    public int version = CURRENT_VERSION;
    public UUID uuid = UUID.randomUUID();
    public UUID clientRequestId = null; // optional, for client create/ack flow
    public UUID owner = null;
    public String name = "Node";
    public NodeType type = NodeType.CAMERA_CONTROL;
    public Vec3 position = Vec3.ZERO;
    public int colorARGB = 0xFFFF8800;
    public double droneRadius = 6.0;
    public double droneSpeedDegPerSec = 30.0;
    public double droneStartAngleDeg = 0.0;
    public float timelapseYaw = 0f;
    public float timelapsePitch = 0f;
    public float timelapseFovMultiplier = 1.0f;
    public int timelapseIndex = 0;
    public boolean timelapseEnabled = true;
    public boolean autoManaged = false;
    public UUID buildSessionId = null;
    public UUID trackedEntityId = null;
    public String buildMode = "";
    public String buildState = "";
    public Vec3 framingMin = null;
    public Vec3 framingMax = null;
    public float autoRigYaw = 0f;

    public static CameraNodeDTO fromCameraNode(CameraNode node) {
        return fromCameraNode(node, null);
    }

    public static CameraNodeDTO fromCameraNode(CameraNode node, UUID owner) {
        CameraNodeDTO dto = new CameraNodeDTO();
        dto.uuid = node.id;
        dto.owner = owner != null ? owner : node.owner;
        dto.name = node.name;
      	dto.type = node.type;
       	dto.position = node.position;
       	dto.colorARGB = node.colorARGB == null ? 0xFFFF8800 : node.colorARGB;
        dto.droneRadius = node.droneRadius;
        dto.droneSpeedDegPerSec = node.droneSpeedDegPerSec;
        dto.droneStartAngleDeg = node.droneStartAngleDeg;
        dto.timelapseYaw = node.timelapseYaw;
        dto.timelapsePitch = node.timelapsePitch;
        dto.timelapseFovMultiplier = node.timelapseFovMultiplier;
        dto.timelapseIndex = node.timelapseIndex;
        dto.timelapseEnabled = node.timelapseEnabled;
        dto.autoManaged = node.autoManaged;
        dto.buildSessionId = node.buildSessionId;
        dto.trackedEntityId = node.trackedEntityId;
        dto.buildMode = node.buildMode;
        dto.buildState = node.buildState;
        dto.framingMin = node.framingMin;
        dto.framingMax = node.framingMax;
        dto.autoRigYaw = node.autoRigYaw;
        return dto;
    }

    public CameraNode toCameraNode() {
        CameraNode node = new CameraNode();
        node.id = this.uuid;
        node.name = this.name;
        node.type = this.type == null ? NodeType.CAMERA_CONTROL : this.type;
        node.position = this.position;
        node.colorARGB = this.colorARGB;
        node.droneRadius = this.droneRadius;
        node.droneSpeedDegPerSec = this.droneSpeedDegPerSec;
        node.droneStartAngleDeg = this.droneStartAngleDeg;
        node.timelapseYaw = this.timelapseYaw;
        node.timelapsePitch = this.timelapsePitch;
        node.timelapseFovMultiplier = this.timelapseFovMultiplier;
        node.timelapseIndex = this.timelapseIndex;
        node.timelapseEnabled = this.timelapseEnabled;
        node.autoManaged = this.autoManaged;
        node.buildSessionId = this.buildSessionId;
        node.trackedEntityId = this.trackedEntityId;
        node.buildMode = this.buildMode;
        node.buildState = this.buildState;
        node.framingMin = this.framingMin;
        node.framingMax = this.framingMax;
        node.autoRigYaw = this.autoRigYaw;
        node.owner = this.owner;
        return node;
    }

    public CameraNodeDTO copy() {
        CameraNodeDTO dto = new CameraNodeDTO();
        dto.version = CURRENT_VERSION;
        dto.uuid = this.uuid;
        dto.clientRequestId = this.clientRequestId;
        dto.owner = this.owner;
        dto.name = this.name;
        dto.type = this.type;
        dto.position = this.position;
        dto.colorARGB = this.colorARGB;
        dto.droneRadius = this.droneRadius;
        dto.droneSpeedDegPerSec = this.droneSpeedDegPerSec;
        dto.droneStartAngleDeg = this.droneStartAngleDeg;
        dto.timelapseYaw = this.timelapseYaw;
        dto.timelapsePitch = this.timelapsePitch;
        dto.timelapseFovMultiplier = this.timelapseFovMultiplier;
        dto.timelapseIndex = this.timelapseIndex;
        dto.timelapseEnabled = this.timelapseEnabled;
        dto.autoManaged = this.autoManaged;
        dto.buildSessionId = this.buildSessionId;
        dto.trackedEntityId = this.trackedEntityId;
        dto.buildMode = this.buildMode;
        dto.buildState = this.buildState;
        dto.framingMin = this.framingMin;
        dto.framingMax = this.framingMax;
        dto.autoRigYaw = this.autoRigYaw;
        return dto;
    }

    public void write(FriendlyByteBuf buf) {
        // Always advertise the layout actually written below. DTOs loaded from
        // older NBT may retain a legacy version value until their first save.
        buf.writeVarInt(CURRENT_VERSION);
        buf.writeUUID(uuid);
        buf.writeBoolean(clientRequestId != null);
        if (clientRequestId != null) buf.writeUUID(clientRequestId);
        buf.writeBoolean(owner != null);
        if (owner != null) buf.writeUUID(owner);
        buf.writeUtf(name);
        buf.writeEnum(type == null ? NodeType.CAMERA_CONTROL : type);
        writeVec3d(buf, position);
        buf.writeInt(colorARGB);
        buf.writeDouble(droneRadius);
        buf.writeDouble(droneSpeedDegPerSec);
        buf.writeDouble(droneStartAngleDeg);
        buf.writeFloat(timelapseYaw);
        buf.writeFloat(timelapsePitch);
        buf.writeFloat(timelapseFovMultiplier);
        buf.writeVarInt(timelapseIndex);
        buf.writeBoolean(timelapseEnabled);
        buf.writeBoolean(autoManaged);
        writeOptionalUuid(buf, buildSessionId);
        writeOptionalUuid(buf, trackedEntityId);
        buf.writeUtf(buildMode == null ? "" : buildMode);
        buf.writeUtf(buildState == null ? "" : buildState);
        writeOptionalVec3d(buf, framingMin);
        writeOptionalVec3d(buf, framingMax);
        buf.writeFloat(autoRigYaw);
    }

    public static CameraNodeDTO read(FriendlyByteBuf buf) {
        CameraNodeDTO dto = new CameraNodeDTO();
        dto.version = buf.readVarInt();
        dto.uuid = buf.readUUID();
        if (buf.readBoolean()) dto.clientRequestId = buf.readUUID();
        if (buf.readBoolean()) dto.owner = buf.readUUID();
        dto.name = buf.readUtf(FriendlyByteBuf.MAX_STRING_LENGTH);
        dto.type = buf.readEnum(NodeType.class);
        dto.position = readVec3d(buf);
        dto.colorARGB = buf.readInt();
        dto.droneRadius = buf.readDouble();
        dto.droneSpeedDegPerSec = buf.readDouble();
        dto.droneStartAngleDeg = buf.readDouble();
        if (dto.version >= 4) {
            dto.timelapseYaw = buf.readFloat();
            dto.timelapsePitch = buf.readFloat();
            dto.timelapseFovMultiplier = buf.readFloat();
            dto.timelapseIndex = buf.readVarInt();
            dto.timelapseEnabled = buf.readBoolean();
            dto.autoManaged = buf.readBoolean();
            dto.buildSessionId = readOptionalUuid(buf);
            dto.trackedEntityId = readOptionalUuid(buf);
            dto.buildMode = buf.readUtf(FriendlyByteBuf.MAX_STRING_LENGTH);
            dto.buildState = buf.readUtf(FriendlyByteBuf.MAX_STRING_LENGTH);
            dto.framingMin = readOptionalVec3d(buf);
            dto.framingMax = readOptionalVec3d(buf);
            dto.autoRigYaw = buf.readFloat();
        }
        return dto;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", CURRENT_VERSION);
        tag.putString("uuid", uuid.toString());
        if (owner != null) tag.putString("owner", owner.toString());
        tag.putString("name", name);
        tag.putString("type", (type == null ? NodeType.CAMERA_CONTROL : type).name());
        tag.put("pos", vec3dToNbt(position));
        tag.putInt("color", colorARGB);
        tag.putDouble("droneRadius", droneRadius);
        tag.putDouble("droneSpeedDegPerSec", droneSpeedDegPerSec);
        tag.putDouble("droneStartAngleDeg", droneStartAngleDeg);
        tag.putFloat("timelapseYaw", timelapseYaw);
        tag.putFloat("timelapsePitch", timelapsePitch);
        tag.putFloat("timelapseFovMultiplier", timelapseFovMultiplier);
        tag.putInt("timelapseIndex", timelapseIndex);
        tag.putBoolean("timelapseEnabled", timelapseEnabled);
        tag.putBoolean("autoManaged", autoManaged);
        if (buildSessionId != null) tag.putString("buildSessionId", buildSessionId.toString());
        if (trackedEntityId != null) tag.putString("trackedEntityId", trackedEntityId.toString());
        tag.putString("buildMode", buildMode == null ? "" : buildMode);
        tag.putString("buildState", buildState == null ? "" : buildState);
        if (framingMin != null) tag.put("framingMin", vec3dToNbt(framingMin));
        if (framingMax != null) tag.put("framingMax", vec3dToNbt(framingMax));
        tag.putFloat("autoRigYaw", autoRigYaw);
        return tag;
    }

    public static CameraNodeDTO fromNbt(CompoundTag tag) {
        CameraNodeDTO dto = new CameraNodeDTO();
        dto.version = CURRENT_VERSION;
        tag.getString("uuid").ifPresent(uuidStr -> {
            try {
                dto.uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {}
        });
        if (dto.uuid == null) dto.uuid = UUID.randomUUID();
        tag.getString("owner").ifPresent(ownerStr -> {
            try {
                dto.owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException ignored) {}
        });
        dto.name = tag.getString("name").orElse("Node");
        tag.getString("type").ifPresent(typeName -> {
            try {
                dto.type = NodeType.valueOf(typeName);
            } catch (IllegalArgumentException ignored) {}
        });
        tag.getList("pos").ifPresent(list -> {
            if (!list.isEmpty() && list.get(0).getId() == Tag.TAG_DOUBLE) {
                dto.position = vec3dFromNbt(list);
            }
        });
        dto.colorARGB = tag.getInt("color").orElse(0xFFFF8800);
        dto.droneRadius = tag.getDouble("droneRadius").orElse(6.0);
        dto.droneSpeedDegPerSec = tag.getDouble("droneSpeedDegPerSec").orElse(30.0);
        dto.droneStartAngleDeg = tag.getDouble("droneStartAngleDeg").orElse(0.0);
        dto.timelapseYaw = tag.getFloat("timelapseYaw").orElse(0f);
        dto.timelapsePitch = tag.getFloat("timelapsePitch").orElse(0f);
        dto.timelapseFovMultiplier = tag.getFloat("timelapseFovMultiplier").orElse(1f);
        dto.timelapseIndex = tag.getInt("timelapseIndex").orElse(0);
        dto.timelapseEnabled = tag.getBoolean("timelapseEnabled").orElse(true);
        dto.autoManaged = tag.getBoolean("autoManaged").orElse(false);
        dto.buildSessionId = readUuid(tag, "buildSessionId");
        dto.trackedEntityId = readUuid(tag, "trackedEntityId");
        dto.buildMode = tag.getString("buildMode").orElse("");
        dto.buildState = tag.getString("buildState").orElse("");
        tag.getList("framingMin").ifPresent(list -> dto.framingMin = vec3dFromNbt(list));
        tag.getList("framingMax").ifPresent(list -> dto.framingMax = vec3dFromNbt(list));
        dto.autoRigYaw = tag.getFloat("autoRigYaw").orElse(0f);
        return dto;
    }

    public static ResourceKey<net.minecraft.world.level.Level> readDimension(FriendlyByteBuf buf) {
        Identifier id = buf.readIdentifier();
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static void writeDimension(FriendlyByteBuf buf, ResourceKey<net.minecraft.world.level.Level> key) {
        buf.writeIdentifier(key.identifier());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CameraNodeDTO that = (CameraNodeDTO) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    private static void writeVec3d(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec3d(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeOptionalUuid(FriendlyByteBuf buf, UUID value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeUUID(value);
    }

    private static UUID readOptionalUuid(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    private static void writeOptionalVec3d(FriendlyByteBuf buf, Vec3 value) {
        buf.writeBoolean(value != null);
        if (value != null) writeVec3d(buf, value);
    }

    private static Vec3 readOptionalVec3d(FriendlyByteBuf buf) {
        return buf.readBoolean() ? readVec3d(buf) : null;
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        String value = tag.getString(key).orElse("");
        if (value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ListTag vec3dToNbt(Vec3 vec) {
        ListTag list = new ListTag();
        list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.x));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.y));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.z));
        return list;
    }

    private static Vec3 vec3dFromNbt(ListTag list) {
        if (list == null || list.size() < 3) return Vec3.ZERO;
        double x = list.getDouble(0).orElse(0.0);
        double y = list.getDouble(1).orElse(0.0);
        double z = list.getDouble(2).orElse(0.0);
        return new Vec3(x, y, z);
    }
}
