package ninja.trek.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import ninja.trek.CraneshotClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// CameraAccessor.java
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("pos")
    void invokesetPos(Vec3d pos);

    @Accessor("pos")
    Vec3d getPos();

    @Invoker("setRotation")
    void invokeSetRotation(float yaw, float pitch);
}

