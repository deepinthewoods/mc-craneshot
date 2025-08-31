package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import ninja.trek.camera.CameraSystem;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    /**
     * Cancel rendering the local player entity when body rendering is disabled
     * or when distance threshold logic says not to show the body.
     */
    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void craneshot$gateLocalPlayerBody(E entity, double x, double y, double z, float yaw,
                                                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                                 int light, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        // Only gate the local player's body rendering
        if (entity.getId() != client.player.getId()) return;

        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive() && !cameraSystem.shouldRenderPlayerModel()) {
            ci.cancel();
        }
    }
}

@Mixin(EntityRenderDispatcher.class)
class EntityRenderDispatcherStateMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private <S extends EntityRenderState> void craneshot$gateLocalPlayerBodyState(S state, double x, double y, double z,
                                                                                  MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                                                  int light, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        if (playerState.id != client.player.getId()) return;

        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive() && !cameraSystem.shouldRenderPlayerModel()) {
            ci.cancel();
        }
    }
}
