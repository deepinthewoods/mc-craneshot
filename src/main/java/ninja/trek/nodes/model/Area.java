package ninja.trek.nodes.model;

import net.minecraft.util.math.Vec3d;

public class Area {
    public AreaShape shape = AreaShape.CUBE;
    public Vec3d center = Vec3d.ZERO;
    public double minRadius = 8.0; // legacy scalar radii
    public double maxRadius = 16.0; // legacy scalar radii

    // Phase 2: per-axis radii for advanced mode (ellipsoid for SPHERE, AABB for CUBE)
    public boolean advanced = false;
    public Vec3d minRadii = null; // if null, use minRadius for all axes
    public Vec3d maxRadii = null; // if null, use maxRadius for all axes

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
    public Area(AreaShape shape, Vec3d center, double minRadius, double maxRadius) {
        this.shape = shape;
        this.center = center;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
    }
}
