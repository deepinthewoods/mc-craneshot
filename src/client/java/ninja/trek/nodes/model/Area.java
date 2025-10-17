package ninja.trek.nodes.model;

import net.minecraft.util.math.Vec3d;

public class Area {
    public AreaShape shape = AreaShape.CUBE;
    public Vec3d center = Vec3d.ZERO;
    public double minRadius = 8.0;
    public double maxRadius = 16.0;

    public Area() {}
    public Area(AreaShape shape, Vec3d center, double minRadius, double maxRadius) {
        this.shape = shape;
        this.center = center;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
    }
}

