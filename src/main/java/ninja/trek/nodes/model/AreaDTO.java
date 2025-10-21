package ninja.trek.nodes.model;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

public class AreaDTO {
    private static final int FILTER_WALKING = 1;
    private static final int FILTER_ELYTRA = 1 << 1;
    private static final int FILTER_MINECART = 1 << 2;
    private static final int FILTER_RIDING_GHAST = 1 << 3;
    private static final int FILTER_RIDING_OTHER = 1 << 4;
    private static final int FILTER_BOAT = 1 << 5;
    private static final int FILTER_SWIMMING = 1 << 6;
    private static final int FILTER_SNEAKING = 1 << 7;
    private static final int FILTER_CRAWLING = 1 << 8;

    public AreaShape shape = AreaShape.CUBE;
    public Vec3d center = Vec3d.ZERO;
    public double minRadius = 8.0;
    public double maxRadius = 16.0;
    public boolean advanced = false;
    public Vec3d minRadii = null;
    public Vec3d maxRadii = null;
    public int filterMask = FILTER_WALKING
            | FILTER_ELYTRA
            | FILTER_MINECART
            | FILTER_RIDING_GHAST
            | FILTER_RIDING_OTHER
            | FILTER_BOAT
            | FILTER_SWIMMING
            | FILTER_SNEAKING
            | FILTER_CRAWLING;
    public EasingCurve easing = EasingCurve.LINEAR;

    public static AreaDTO fromArea(Area area) {
        AreaDTO dto = new AreaDTO();
        dto.shape = area.shape;
        dto.center = area.center;
        dto.minRadius = area.minRadius;
        dto.maxRadius = area.maxRadius;
        dto.advanced = area.advanced;
        dto.minRadii = area.minRadii;
        dto.maxRadii = area.maxRadii;
        dto.easing = area.easing;
        dto.filterMask = packFilters(area);
        return dto;
    }

    public Area toArea() {
        Area area = new Area();
        area.shape = this.shape;
        area.center = this.center;
        area.minRadius = this.minRadius;
        area.maxRadius = this.maxRadius;
        area.advanced = this.advanced;
        area.minRadii = this.minRadii;
        area.maxRadii = this.maxRadii;
        area.easing = this.easing == null ? EasingCurve.LINEAR : this.easing;
        unpackFilters(area, this.filterMask);
        return area;
    }

    public AreaDTO copy() {
        AreaDTO dto = new AreaDTO();
        dto.shape = this.shape;
        dto.center = this.center;
        dto.minRadius = this.minRadius;
        dto.maxRadius = this.maxRadius;
        dto.advanced = this.advanced;
        dto.minRadii = this.minRadii;
        dto.maxRadii = this.maxRadii;
        dto.filterMask = this.filterMask;
        dto.easing = this.easing;
        return dto;
    }

    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(shape);
        writeVec3d(buf, center);
        buf.writeDouble(minRadius);
        buf.writeDouble(maxRadius);
        buf.writeBoolean(advanced);
        writeNullableVec3d(buf, minRadii);
        writeNullableVec3d(buf, maxRadii);
        buf.writeVarInt(filterMask);
        buf.writeEnumConstant(easing == null ? EasingCurve.LINEAR : easing);
    }

    public static AreaDTO read(PacketByteBuf buf) {
        AreaDTO dto = new AreaDTO();
        dto.shape = buf.readEnumConstant(AreaShape.class);
        dto.center = readVec3d(buf);
        dto.minRadius = buf.readDouble();
        dto.maxRadius = buf.readDouble();
        dto.advanced = buf.readBoolean();
        dto.minRadii = readNullableVec3d(buf);
        dto.maxRadii = readNullableVec3d(buf);
        dto.filterMask = buf.readVarInt();
        dto.easing = buf.readEnumConstant(EasingCurve.class);
        return dto;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("shape", shape.name());
        tag.put("center", vec3dToNbt(center));
        tag.putDouble("minRadius", minRadius);
        tag.putDouble("maxRadius", maxRadius);
        tag.putBoolean("advanced", advanced);
        if (minRadii != null) tag.put("minRadii", vec3dToNbt(minRadii));
        if (maxRadii != null) tag.put("maxRadii", vec3dToNbt(maxRadii));
        tag.putInt("filters", filterMask);
        tag.putString("easing", (easing == null ? EasingCurve.LINEAR : easing).name());
        return tag;
    }

    public static AreaDTO fromNbt(NbtCompound tag) {
        AreaDTO dto = new AreaDTO();
        tag.getString("shape").ifPresent(shapeName -> {
            try {
                dto.shape = AreaShape.valueOf(shapeName);
            } catch (IllegalArgumentException ignored) {}
        });
        tag.getList("center").ifPresent(list -> {
            if (!list.isEmpty() && list.get(0).getType() == NbtElement.DOUBLE_TYPE) {
                dto.center = vec3dFromNbt(list);
            }
        });
        dto.minRadius = tag.getDouble("minRadius").orElse(8.0);
        dto.maxRadius = tag.getDouble("maxRadius").orElse(16.0);
        dto.advanced = tag.getBoolean("advanced").orElse(false);
        tag.getList("minRadii").ifPresent(list -> {
            if (!list.isEmpty() && list.get(0).getType() == NbtElement.DOUBLE_TYPE) {
                dto.minRadii = vec3dFromNbt(list);
            }
        });
        tag.getList("maxRadii").ifPresent(list -> {
            if (!list.isEmpty() && list.get(0).getType() == NbtElement.DOUBLE_TYPE) {
                dto.maxRadii = vec3dFromNbt(list);
            }
        });
        dto.filterMask = tag.getInt("filters").orElse(dto.filterMask);
        tag.getString("easing").ifPresent(easingName -> {
            try {
                dto.easing = EasingCurve.valueOf(easingName);
            } catch (IllegalArgumentException ignored) {}
        });
        return dto;
    }

    private static int packFilters(Area area) {
        int mask = 0;
        if (area.filterWalking) mask |= FILTER_WALKING;
        if (area.filterElytra) mask |= FILTER_ELYTRA;
        if (area.filterMinecart) mask |= FILTER_MINECART;
        if (area.filterRidingGhast) mask |= FILTER_RIDING_GHAST;
        if (area.filterRidingOther) mask |= FILTER_RIDING_OTHER;
        if (area.filterBoat) mask |= FILTER_BOAT;
        if (area.filterSwimming) mask |= FILTER_SWIMMING;
        if (area.filterSneaking) mask |= FILTER_SNEAKING;
        if (area.filterCrawling1Block) mask |= FILTER_CRAWLING;
        return mask;
    }

    private static void unpackFilters(Area area, int mask) {
        area.filterWalking = (mask & FILTER_WALKING) != 0;
        area.filterElytra = (mask & FILTER_ELYTRA) != 0;
        area.filterMinecart = (mask & FILTER_MINECART) != 0;
        area.filterRidingGhast = (mask & FILTER_RIDING_GHAST) != 0;
        area.filterRidingOther = (mask & FILTER_RIDING_OTHER) != 0;
        area.filterBoat = (mask & FILTER_BOAT) != 0;
        area.filterSwimming = (mask & FILTER_SWIMMING) != 0;
        area.filterSneaking = (mask & FILTER_SNEAKING) != 0;
        area.filterCrawling1Block = (mask & FILTER_CRAWLING) != 0;
    }

    private static void writeVec3d(PacketByteBuf buf, Vec3d vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3d readVec3d(PacketByteBuf buf) {
        return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeNullableVec3d(PacketByteBuf buf, Vec3d vec) {
        buf.writeBoolean(vec != null);
        if (vec != null) writeVec3d(buf, vec);
    }

    private static Vec3d readNullableVec3d(PacketByteBuf buf) {
        return buf.readBoolean() ? readVec3d(buf) : null;
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
