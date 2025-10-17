package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import ninja.trek.camera.CameraSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class EntityRenderDispatcherMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true
    )
    private <S extends LivingEntityRenderState> void craneshot$gateLocalPlayerBodyState(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue commands,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
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
