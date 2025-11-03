package ninja.trek.nodes.model;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Top-level area entry persisted alongside nodes.
 * Extends {@link Area} so legacy logic that expects the geometric data can be reused,
 * while adding identifiers and movement bindings.
 */
public class AreaInstance extends Area {
    public UUID id = UUID.randomUUID();
    public String name = "Area";
    public UUID owner = null;
    public final List<AreaMovementConfig> movements = new ArrayList<>();

    public AreaInstance() { }

    public AreaInstance(Area other) {
        copyFrom(other);
    }

    public void copyFrom(Area other) {
        this.shape = other.shape;
        this.center = other.center;
        this.insideRadius = other.insideRadius;
        this.outsideRadius = other.outsideRadius;
        this.advanced = other.advanced;
        this.insideRadii = other.insideRadii;
        this.outsideRadii = other.outsideRadii;
        this.filterWalking = other.filterWalking;
        this.filterElytra = other.filterElytra;
        this.filterMinecart = other.filterMinecart;
        this.filterRidingGhast = other.filterRidingGhast;
        this.filterRidingOther = other.filterRidingOther;
        this.filterBoat = other.filterBoat;
        this.filterSwimming = other.filterSwimming;
        this.filterSneaking = other.filterSneaking;
        this.filterCrawling1Block = other.filterCrawling1Block;
        this.easing = other.easing;
    }

    public AreaInstance copy() {
        AreaInstance inst = new AreaInstance();
        inst.id = this.id;
        inst.name = this.name;
        inst.owner = this.owner;
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
        for (AreaMovementConfig config : this.movements) {
            inst.movements.add(config.copy());
        }
        return inst;
    }

    public Vec3d getCenter() {
        return center;
    }
}
