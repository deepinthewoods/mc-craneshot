package ninja.trek.nodes.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

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
    public Vec3 center = Vec3.ZERO;
    public double insideRadius = 8.0;
    public double outsideRadius = 16.0;
    public boolean advanced = false;
    public Vec3 insideRadii = null;
    public Vec3 outsideRadii = null;
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
        dto.insideRadius = area.insideRadius;
        dto.outsideRadius = area.outsideRadius;
        dto.advanced = area.advanced;
        dto.insideRadii = area.insideRadii;
        dto.outsideRadii = area.outsideRadii;
        dto.easing = area.easing;
        dto.filterMask = packFilters(area);
        return dto;
    }

    public Area toArea() {
        Area area = new Area();
        area.shape = this.shape;
        area.center = this.center;
        area.insideRadius = this.insideRadius;
        area.outsideRadius = this.outsideRadius;
        area.advanced = this.advanced;
        area.insideRadii = this.insideRadii;
        area.outsideRadii = this.outsideRadii;
        area.easing = this.easing == null ? EasingCurve.LINEAR : this.easing;
        unpackFilters(area, this.filterMask);
        return area;
    }

    public AreaDTO copy() {
        AreaDTO dto = new AreaDTO();
        dto.shape = this.shape;
        dto.center = this.center;
        dto.insideRadius = this.insideRadius;
        dto.outsideRadius = this.outsideRadius;
        dto.advanced = this.advanced;
        dto.insideRadii = this.insideRadii;
        dto.outsideRadii = this.outsideRadii;
        dto.filterMask = this.filterMask;
        dto.easing = this.easing;
        return dto;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(shape);
        writeVec3d(buf, center);
        buf.writeDouble(insideRadius);
        buf.writeDouble(outsideRadius);
        buf.writeBoolean(advanced);
        writeNullableVec3d(buf, insideRadii);
        writeNullableVec3d(buf, outsideRadii);
        buf.writeVarInt(filterMask);
        buf.writeEnum(easing == null ? EasingCurve.LINEAR : easing);
    }

    public static AreaDTO read(FriendlyByteBuf buf) {
        AreaDTO dto = new AreaDTO();
        dto.shape = buf.readEnum(AreaShape.class);
        dto.center = readVec3d(buf);
        dto.insideRadius = buf.readDouble();
        dto.outsideRadius = buf.readDouble();
        dto.advanced = buf.readBoolean();
        dto.insideRadii = readNullableVec3d(buf);
        dto.outsideRadii = readNullableVec3d(buf);
        dto.filterMask = buf.readVarInt();
        dto.easing = buf.readEnum(EasingCurve.class);
        return dto;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("shape", shape.name());
        tag.put("center", vec3dToNbt(center));
        tag.putDouble("insideRadius", insideRadius);
        tag.putDouble("outsideRadius", outsideRadius);
        tag.putBoolean("advanced", advanced);
        if (insideRadii != null) tag.put("insideRadii", vec3dToNbt(insideRadii));
        if (outsideRadii != null) tag.put("outsideRadii", vec3dToNbt(outsideRadii));
        tag.putInt("filters", filterMask);
        tag.putString("easing", (easing == null ? EasingCurve.LINEAR : easing).name());
        return tag;
    }

    public static AreaDTO fromNbt(CompoundTag tag) {
        AreaDTO dto = new AreaDTO();
        tag.getString("shape").ifPresent(shapeName -> {
            try {
                dto.shape = AreaShape.valueOf(shapeName);
            } catch (IllegalArgumentException ignored) {}
        });
        tag.getList("center").ifPresent(list -> {
            if (!list.isEmpty() && list.get(0).getId() == Tag.TAG_DOUBLE) {
                dto.center = vec3dFromNbt(list);
            }
        });
        dto.insideRadius = tag.getDouble("insideRadius").orElse(8.0);
        dto.outsideRadius = tag.getDouble("outsideRadius").orElse(16.0);
        dto.advanced = tag.getBoolean("advanced").orElse(false);
        tag.getList("insideRadii").ifPresent(list -> {
            if (!list.isEmpty() && list.get(0).getId() == Tag.TAG_DOUBLE) {
                dto.insideRadii = vec3dFromNbt(list);
            }
        });
        tag.getList("outsideRadii").ifPresent(list -> {
            if (!list.isEmpty() && list.get(0).getId() == Tag.TAG_DOUBLE) {
                dto.outsideRadii = vec3dFromNbt(list);
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

    private static void writeVec3d(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec3d(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeNullableVec3d(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeBoolean(vec != null);
        if (vec != null) writeVec3d(buf, vec);
    }

    private static Vec3 readNullableVec3d(FriendlyByteBuf buf) {
        return buf.readBoolean() ? readVec3d(buf) : null;
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
