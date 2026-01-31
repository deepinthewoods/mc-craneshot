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
    public static final int CURRENT_VERSION = 3;

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
        node.owner = this.owner;
        return node;
    }

    public CameraNodeDTO copy() {
        CameraNodeDTO dto = new CameraNodeDTO();
        dto.version = this.version;
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
        return dto;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(version);
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
        return dto;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", version);
        tag.putString("uuid", uuid.toString());
        if (owner != null) tag.putString("owner", owner.toString());
        tag.putString("name", name);
        tag.putString("type", (type == null ? NodeType.CAMERA_CONTROL : type).name());
        tag.put("pos", vec3dToNbt(position));
        tag.putInt("color", colorARGB);
        tag.putDouble("droneRadius", droneRadius);
        tag.putDouble("droneSpeedDegPerSec", droneSpeedDegPerSec);
        tag.putDouble("droneStartAngleDeg", droneStartAngleDeg);
        return tag;
    }

    public static CameraNodeDTO fromNbt(CompoundTag tag) {
        CameraNodeDTO dto = new CameraNodeDTO();
        dto.version = tag.getInt("version").orElse(0);
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
