package ninja.trek.nodes.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializable representation of an {@link AreaInstance} that can be sent across the network
 * or persisted on the server without pulling in client-only dependencies.
 */
public class AreaInstanceDTO {
    public static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new Gson();
    private static final Type SETTINGS_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public int version = CURRENT_VERSION;
    public UUID uuid = UUID.randomUUID();
    public UUID clientRequestId = null;
    public UUID owner = null;
    public String name = "Area";

    public AreaShape shape = AreaShape.CUBE;
    public Vec3d center = Vec3d.ZERO;
    public double insideRadius = 8.0;
    public double outsideRadius = 16.0;
    public boolean advanced = false;
    public Vec3d insideRadii = null;
    public Vec3d outsideRadii = null;

    public boolean filterWalking = true;
    public boolean filterElytra = true;
    public boolean filterMinecart = true;
    public boolean filterRidingGhast = true;
    public boolean filterRidingOther = true;
    public boolean filterBoat = true;
    public boolean filterSwimming = true;
    public boolean filterSneaking = true;
    public boolean filterCrawling1Block = true;

    public EasingCurve easing = EasingCurve.LINEAR;
    public final List<AreaMovementConfig> movements = new ArrayList<>();

    public static AreaInstanceDTO fromAreaInstance(AreaInstance area) {
        AreaInstanceDTO dto = new AreaInstanceDTO();
        dto.uuid = area.id != null ? area.id : UUID.randomUUID();
        dto.owner = area.owner;
        dto.name = area.name;
        dto.shape = area.shape;
        dto.center = area.center;
        dto.insideRadius = area.insideRadius;
        dto.outsideRadius = area.outsideRadius;
        dto.advanced = area.advanced;
        dto.insideRadii = area.insideRadii;
        dto.outsideRadii = area.outsideRadii;
        dto.filterWalking = area.filterWalking;
        dto.filterElytra = area.filterElytra;
        dto.filterMinecart = area.filterMinecart;
        dto.filterRidingGhast = area.filterRidingGhast;
        dto.filterRidingOther = area.filterRidingOther;
        dto.filterBoat = area.filterBoat;
        dto.filterSwimming = area.filterSwimming;
        dto.filterSneaking = area.filterSneaking;
        dto.filterCrawling1Block = area.filterCrawling1Block;
        dto.easing = area.easing;
        dto.movements.clear();
        for (AreaMovementConfig cfg : area.movements) {
            dto.movements.add(cfg.copy());
        }
        return dto;
    }

    public AreaInstance toAreaInstance() {
        AreaInstance inst = new AreaInstance();
        inst.id = this.uuid;
        inst.owner = this.owner;
        inst.name = this.name;
        inst.shape = this.shape;
        inst.center = this.center;
        inst.insideRadius = this.insideRadius;
        inst.outsideRadius = this.outsideRadius;
        inst.advanced = this.advanced;
        inst.insideRadii = this.insideRadii;
        inst.outsideRadii = this.outsideRadii;
        inst.filterWalking = this.filterWalking;
        inst.filterElytra = this.filterElytra;
        inst.filterMinecart = this.filterMinecart;
        inst.filterRidingGhast = this.filterRidingGhast;
        inst.filterRidingOther = this.filterRidingOther;
        inst.filterBoat = this.filterBoat;
        inst.filterSwimming = this.filterSwimming;
        inst.filterSneaking = this.filterSneaking;
        inst.filterCrawling1Block = this.filterCrawling1Block;
        inst.easing = this.easing;
        inst.movements.clear();
        for (AreaMovementConfig cfg : this.movements) {
            inst.movements.add(cfg.copy());
        }
        return inst;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(version);
        buf.writeUuid(uuid);
        buf.writeBoolean(clientRequestId != null);
        if (clientRequestId != null) buf.writeUuid(clientRequestId);
        buf.writeBoolean(owner != null);
        if (owner != null) buf.writeUuid(owner);
        buf.writeString(name != null ? name : "");
        buf.writeEnumConstant(shape);
        writeVec3d(buf, center);
        buf.writeDouble(insideRadius);
        buf.writeDouble(outsideRadius);
        buf.writeBoolean(advanced);
        buf.writeBoolean(insideRadii != null);
        if (insideRadii != null) writeVec3d(buf, insideRadii);
        buf.writeBoolean(outsideRadii != null);
        if (outsideRadii != null) writeVec3d(buf, outsideRadii);

        buf.writeBoolean(filterWalking);
        buf.writeBoolean(filterElytra);
        buf.writeBoolean(filterMinecart);
        buf.writeBoolean(filterRidingGhast);
        buf.writeBoolean(filterRidingOther);
        buf.writeBoolean(filterBoat);
        buf.writeBoolean(filterSwimming);
        buf.writeBoolean(filterSneaking);
        buf.writeBoolean(filterCrawling1Block);

        buf.writeEnumConstant(easing);

        buf.writeVarInt(movements.size());
        for (AreaMovementConfig cfg : movements) {
            writeMovement(buf, cfg);
        }
    }

    public static AreaInstanceDTO read(PacketByteBuf buf) {
        AreaInstanceDTO dto = new AreaInstanceDTO();
        dto.version = buf.readVarInt();
        dto.uuid = buf.readUuid();
        if (buf.readBoolean()) dto.clientRequestId = buf.readUuid();
        if (buf.readBoolean()) dto.owner = buf.readUuid();
        dto.name = buf.readString(PacketByteBuf.DEFAULT_MAX_STRING_LENGTH);
        dto.shape = buf.readEnumConstant(AreaShape.class);
        dto.center = readVec3d(buf);
        dto.insideRadius = buf.readDouble();
        dto.outsideRadius = buf.readDouble();
        dto.advanced = buf.readBoolean();
        if (buf.readBoolean()) dto.insideRadii = readVec3d(buf);
        if (buf.readBoolean()) dto.outsideRadii = readVec3d(buf);

        dto.filterWalking = buf.readBoolean();
        dto.filterElytra = buf.readBoolean();
        dto.filterMinecart = buf.readBoolean();
        dto.filterRidingGhast = buf.readBoolean();
        dto.filterRidingOther = buf.readBoolean();
        dto.filterBoat = buf.readBoolean();
        dto.filterSwimming = buf.readBoolean();
        dto.filterSneaking = buf.readBoolean();
        dto.filterCrawling1Block = buf.readBoolean();

        dto.easing = buf.readEnumConstant(EasingCurve.class);

        int count = buf.readVarInt();
        dto.movements.clear();
        for (int i = 0; i < count; i++) {
            dto.movements.add(readMovement(buf));
        }
        return dto;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putInt("version", version);
        tag.putString("uuid", uuid.toString());
        if (clientRequestId != null) tag.putString("clientRequestId", clientRequestId.toString());
        if (owner != null) tag.putString("owner", owner.toString());
        if (name != null) tag.putString("name", name);
        tag.putString("shape", shape.name());
        tag.put("center", vec3dToNbt(center));
        tag.putDouble("insideRadius", insideRadius);
        tag.putDouble("outsideRadius", outsideRadius);
        tag.putBoolean("advanced", advanced);
        if (insideRadii != null) tag.put("insideRadii", vec3dToNbt(insideRadii));
        if (outsideRadii != null) tag.put("outsideRadii", vec3dToNbt(outsideRadii));

        tag.putBoolean("filterWalking", filterWalking);
        tag.putBoolean("filterElytra", filterElytra);
        tag.putBoolean("filterMinecart", filterMinecart);
        tag.putBoolean("filterRidingGhast", filterRidingGhast);
        tag.putBoolean("filterRidingOther", filterRidingOther);
        tag.putBoolean("filterBoat", filterBoat);
        tag.putBoolean("filterSwimming", filterSwimming);
        tag.putBoolean("filterSneaking", filterSneaking);
        tag.putBoolean("filterCrawling1Block", filterCrawling1Block);

        tag.putString("easing", easing.name());

        NbtList movementList = new NbtList();
        for (AreaMovementConfig cfg : movements) {
            movementList.add(movementToNbt(cfg));
        }
        tag.put("movements", movementList);
        return tag;
    }

    public static AreaInstanceDTO fromNbt(NbtCompound tag) {
        AreaInstanceDTO dto = new AreaInstanceDTO();
        dto.version = tag.getInt("version").orElse(0);
        tag.getString("uuid").ifPresent(uuidStr -> {
            try {
                dto.uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {}
        });
        tag.getString("clientRequestId").ifPresent(idStr -> {
            try {
                dto.clientRequestId = UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {}
        });
        tag.getString("owner").ifPresent(ownerStr -> {
            try {
                dto.owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException ignored) {}
        });
        dto.name = tag.getString("name").orElse("Area");
        tag.getString("shape").ifPresent(shapeStr -> {
            try {
                dto.shape = AreaShape.valueOf(shapeStr);
            } catch (IllegalArgumentException ignored) {}
        });
        tag.getList("center").ifPresent(list -> dto.center = vec3dFromNbt(list));
        dto.insideRadius = tag.getDouble("insideRadius").orElse(8.0);
        dto.outsideRadius = tag.getDouble("outsideRadius").orElse(16.0);
        dto.advanced = tag.getBoolean("advanced").orElse(false);
        tag.getList("insideRadii").ifPresent(list -> dto.insideRadii = vec3dFromNbt(list));
        tag.getList("outsideRadii").ifPresent(list -> dto.outsideRadii = vec3dFromNbt(list));

        dto.filterWalking = tag.getBoolean("filterWalking").orElse(true);
        dto.filterElytra = tag.getBoolean("filterElytra").orElse(true);
        dto.filterMinecart = tag.getBoolean("filterMinecart").orElse(true);
        dto.filterRidingGhast = tag.getBoolean("filterRidingGhast").orElse(true);
        dto.filterRidingOther = tag.getBoolean("filterRidingOther").orElse(true);
        dto.filterBoat = tag.getBoolean("filterBoat").orElse(true);
        dto.filterSwimming = tag.getBoolean("filterSwimming").orElse(true);
        dto.filterSneaking = tag.getBoolean("filterSneaking").orElse(true);
        dto.filterCrawling1Block = tag.getBoolean("filterCrawling1Block").orElse(true);

       tag.getString("easing").ifPresent(easingStr -> {
            try {
                dto.easing = EasingCurve.valueOf(easingStr);
            } catch (IllegalArgumentException ignored) {}
        });

        dto.movements.clear();
        tag.getList("movements").ifPresent(list -> {
            for (NbtElement element : list) {
                if (element instanceof NbtCompound compound) {
                    dto.movements.add(movementFromNbt(compound));
                }
            }
        });
        return dto;
    }

    private static void writeVec3d(PacketByteBuf buf, Vec3d vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3d readVec3d(PacketByteBuf buf) {
        return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeMovement(PacketByteBuf buf, AreaMovementConfig cfg) {
        buf.writeUuid(cfg.id != null ? cfg.id : UUID.randomUUID());
        buf.writeString(cfg.movementType != null ? cfg.movementType : "");
        buf.writeBoolean(cfg.name != null);
        if (cfg.name != null) buf.writeString(cfg.name);
        buf.writeBoolean(cfg.enabled);
        buf.writeFloat(cfg.weight);
        buf.writeVarInt(cfg.stateFilters.size());
        for (String key : cfg.stateFilters) {
            buf.writeString(key);
        }
        String json = GSON.toJson(cfg.settings, SETTINGS_TYPE);
        buf.writeString(json != null ? json : "{}");
    }

    private static AreaMovementConfig readMovement(PacketByteBuf buf) {
        AreaMovementConfig cfg = new AreaMovementConfig();
        cfg.id = buf.readUuid();
        cfg.movementType = buf.readString(PacketByteBuf.DEFAULT_MAX_STRING_LENGTH);
        if (buf.readBoolean()) {
            cfg.name = buf.readString(PacketByteBuf.DEFAULT_MAX_STRING_LENGTH);
        } else {
            cfg.name = null;
        }
        cfg.enabled = buf.readBoolean();
        cfg.weight = buf.readFloat();
        cfg.stateFilters.clear();
        int filterCount = buf.readVarInt();
        for (int i = 0; i < filterCount; i++) {
            cfg.stateFilters.add(buf.readString(PacketByteBuf.DEFAULT_MAX_STRING_LENGTH));
        }
        String json = buf.readString(PacketByteBuf.DEFAULT_MAX_STRING_LENGTH);
        cfg.settings.clear();
        if (!json.isEmpty()) {
            Map<String, Object> map = GSON.fromJson(json, SETTINGS_TYPE);
            if (map != null) {
                cfg.settings.putAll(map);
            }
        }
        return cfg;
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

    private static NbtCompound movementToNbt(AreaMovementConfig cfg) {
        NbtCompound tag = new NbtCompound();
        tag.putString("id", cfg.id != null ? cfg.id.toString() : UUID.randomUUID().toString());
        tag.putString("type", cfg.movementType != null ? cfg.movementType : "");
        if (cfg.name != null) tag.putString("name", cfg.name);
        tag.putBoolean("enabled", cfg.enabled);
        tag.putFloat("weight", cfg.weight);
        NbtList filters = new NbtList();
        for (String filter : cfg.stateFilters) {
            filters.add(net.minecraft.nbt.NbtString.of(filter));
        }
        tag.put("filters", filters);
        String json = GSON.toJson(cfg.settings, SETTINGS_TYPE);
        tag.putString("settings", json != null ? json : "{}");
        return tag;
    }

    private static AreaMovementConfig movementFromNbt(NbtCompound tag) {
        AreaMovementConfig cfg = new AreaMovementConfig();
        tag.getString("id").ifPresent(idStr -> {
            try {
                cfg.id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {}
        });
        cfg.movementType = tag.getString("type").orElse("");
        cfg.name = tag.getString("name").orElse(null);
        cfg.enabled = tag.getBoolean("enabled").orElse(true);
        cfg.weight = tag.getFloat("weight").orElse(1.0f);
        cfg.stateFilters.clear();
        tag.getList("filters").ifPresent(list -> {
            for (NbtElement element : list) {
                if (element instanceof net.minecraft.nbt.NbtString str) {
                    str.asString().ifPresent(cfg.stateFilters::add);
                }
            }
        });
        String json = tag.getString("settings").orElse("{}");
        cfg.settings.clear();
        if (json != null && !json.isEmpty()) {
            Map<String, Object> map = GSON.fromJson(json, SETTINGS_TYPE);
            if (map != null) {
                cfg.settings.putAll(map);
            }
        }
        return cfg;
    }
}
