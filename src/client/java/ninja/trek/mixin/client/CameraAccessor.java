package ninja.trek.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
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
    @Accessor("position")
    void invokesetPos(Vec3 pos);

    @Accessor("position")
    Vec3 getPos();

    @Invoker("setRotation")
    void invokeSetRotation(float yaw, float pitch);
}

