package ninja.trek.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import ninja.trek.Craneshot;
import ninja.trek.camera.CameraSystem;
import ninja.trek.util.CameraUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Selects Voxy's existing no-nearby-Sodium-terrain rendering path when
 * Craneshot's detached camera has moved beyond the player's loaded chunks.
 * That path uses a close near plane and does not mask Voxy's LOD terrain with
 * Sodium's visible-section bounds.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.VoxyClient", remap = false)
public abstract class VoxyClientMixin {
    @Unique
    private static boolean craneshot$loggedActivation;

    @Inject(
            method = "disableSodiumChunkRender()Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void craneshot$useDetachedCameraRendering(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.mainCamera();

        if (cir.getReturnValue()
                || !CameraSystem.getInstance().isCameraActive()
                || CameraUtils.isPoseWithinRenderDistance(mc, camera.position())) {
            return;
        }

        cir.setReturnValue(true);
        if (!craneshot$loggedActivation) {
            craneshot$loggedActivation = true;
            Craneshot.LOGGER.debug("Voxy detached-camera rendering enabled outside the loaded player render area");
        }
    }
}
