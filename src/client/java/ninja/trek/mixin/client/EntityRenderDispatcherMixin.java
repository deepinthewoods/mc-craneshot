package ninja.trek.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import ninja.trek.Craneshot;
import ninja.trek.camera.CameraSystem;
import ninja.trek.config.FollowerMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class EntityRenderDispatcherMixin {

    @Unique
    private static boolean craneshot$loggedFollowerNameTagSuppression;

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At("HEAD"), cancellable = true
    )
    private void craneshot$hidePlayerNameTagsInFollowerMode(
            LivingEntity entity,
            double distanceToCameraSq,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!FollowerMode.isFollower() || !(entity instanceof Player)) return;

        if (!craneshot$loggedFollowerNameTagSuppression) {
            craneshot$loggedFollowerNameTagSuppression = true;
            Craneshot.LOGGER.debug("Follower mode: suppressing player name tags");
        }

        cir.setReturnValue(false);
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
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

        // Suppress for a couple frames after deactivation to prevent one-frame flash
        if (cameraSystem.shouldSuppressPlayerRender()) {
            ci.cancel();
            return;
        }

        if (cameraSystem.isCameraActive() && !cameraSystem.shouldRenderPlayerModel()) {
            ci.cancel();
        }
    }
}
