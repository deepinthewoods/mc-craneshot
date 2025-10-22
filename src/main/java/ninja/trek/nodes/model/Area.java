package ninja.trek.nodes.model;

import net.minecraft.util.math.Vec3d;

public class Area {
    public AreaShape shape = AreaShape.CUBE;
    public Vec3d center = Vec3d.ZERO;
    public double insideRadius = 8.0; // inner boundary (100% influence)
    public double outsideRadius = 16.0; // outer boundary (0% influence)

    // Phase 2: per-axis radii for advanced mode (ellipsoid for SPHERE, AABB for CUBE)
    public boolean advanced = false;
    public Vec3d insideRadii = null; // if null, use insideRadius for all axes
    public Vec3d outsideRadii = null; // if null, use outsideRadius for all axes

    // Phase 2: per-area movement filters (all true by default)
    public boolean filterWalking = true;
    public boolean filterElytra = true;
    public boolean filterMinecart = true;
    public boolean filterRidingGhast = true;
    public boolean filterRidingOther = true;
    public boolean filterBoat = true;
    public boolean filterSwimming = true;
    public boolean filterSneaking = true;
    public boolean filterCrawling1Block = true;

    // Phase 2: per-area easing curve for influence blend
    public EasingCurve easing = EasingCurve.LINEAR;

    public Area() {}
    public Area(AreaShape shape, Vec3d center, double insideRadius, double outsideRadius) {
        this.shape = shape;
        this.center = center;
        this.insideRadius = insideRadius;
        this.outsideRadius = outsideRadius;
    }
}
