package ninja.trek.nodes.model;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;
import java.util.UUID;

public class CameraNodeDTO {
    public static final int CURRENT_VERSION = 3;

    public int version = CURRENT_VERSION;
    public UUID uuid = UUID.randomUUID();
    public UUID clientRequestId = null; // optional, for client create/ack flow
    public UUID owner = null;
    public String name = "Node";
    public NodeType type = NodeType.CAMERA_CONTROL;
    public Vec3d position = Vec3d.ZERO;
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

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(version);
        buf.writeUuid(uuid);
        buf.writeBoolean(clientRequestId != null);
        if (clientRequestId != null) buf.writeUuid(clientRequestId);
        buf.writeBoolean(owner != null);
        if (owner != null) buf.writeUuid(owner);
        buf.writeString(name);
        buf.writeEnumConstant(type == null ? NodeType.CAMERA_CONTROL : type);
        writeVec3d(buf, position);
        buf.writeInt(colorARGB);
        buf.writeDouble(droneRadius);
        buf.writeDouble(droneSpeedDegPerSec);
        buf.writeDouble(droneStartAngleDeg);
    }

    public static CameraNodeDTO read(PacketByteBuf buf) {
        CameraNodeDTO dto = new CameraNodeDTO();
        dto.version = buf.readVarInt();
        dto.uuid = buf.readUuid();
        if (buf.readBoolean()) dto.clientRequestId = buf.readUuid();
        if (buf.readBoolean()) dto.owner = buf.readUuid();
        dto.name = buf.readString(PacketByteBuf.DEFAULT_MAX_STRING_LENGTH);
        dto.type = buf.readEnumConstant(NodeType.class);
        dto.position = readVec3d(buf);
        dto.colorARGB = buf.readInt();
        dto.droneRadius = buf.readDouble();
        dto.droneSpeedDegPerSec = buf.readDouble();
        dto.droneStartAngleDeg = buf.readDouble();
        return dto;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
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

    public static CameraNodeDTO fromNbt(NbtCompound tag) {
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
            if (!list.isEmpty() && list.get(0).getType() == NbtElement.DOUBLE_TYPE) {
                dto.position = vec3dFromNbt(list);
            }
        });
        dto.colorARGB = tag.getInt("color").orElse(0xFFFF8800);
        dto.droneRadius = tag.getDouble("droneRadius").orElse(6.0);
        dto.droneSpeedDegPerSec = tag.getDouble("droneSpeedDegPerSec").orElse(30.0);
        dto.droneStartAngleDeg = tag.getDouble("droneStartAngleDeg").orElse(0.0);
        return dto;
    }

    public static RegistryKey<net.minecraft.world.World> readDimension(PacketByteBuf buf) {
        Identifier id = buf.readIdentifier();
        return RegistryKey.of(RegistryKeys.WORLD, id);
    }

    public static void writeDimension(PacketByteBuf buf, RegistryKey<net.minecraft.world.World> key) {
        buf.writeIdentifier(key.getValue());
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

    private static void writeVec3d(PacketByteBuf buf, Vec3d vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3d readVec3d(PacketByteBuf buf) {
        return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static NbtList vec3dToNbt(Vec3d vec) {
        NbtList list = new NbtList();
        list.add(net.minecraft.nbt.NbtDouble.of(vec.x));
        list.add(net.minecraft.nbt.NbtDouble.of(vec.y));
        list.add(net.minecraft.nbt.NbtDouble.of(vec.z));
        return list;
    }

    private static Vec3d vec3dFromNbt(NbtList list) {
        if (list == null || list.size() < 3) return Vec3d.ZERO;
        double x = list.getDouble(0).orElse(0.0);
        double y = list.getDouble(1).orElse(0.0);
        double z = list.getDouble(2).orElse(0.0);
        return new Vec3d(x, y, z);
    }
}
