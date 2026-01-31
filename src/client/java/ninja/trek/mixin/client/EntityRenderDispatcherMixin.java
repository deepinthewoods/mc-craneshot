package ninja.trek.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import ninja.trek.camera.CameraSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class EntityRenderDispatcherMixin {

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true
    )
    private <S extends LivingEntityRenderState> void craneshot$gateLocalPlayerBodyState(
            S state,
            PoseStack matrices,
            SubmitNodeCollector commands,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!(state instanceof AvatarRenderState playerState)) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        if (playerState.id != client.player.getId()) return;

        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive() && !cameraSystem.shouldRenderPlayerModel()) {
            ci.cancel();
        }
    }
}
