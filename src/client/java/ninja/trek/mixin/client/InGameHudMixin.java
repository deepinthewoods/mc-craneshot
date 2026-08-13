package ninja.trek.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import ninja.trek.Craneshot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public class InGameHudMixin {
    @Shadow @Final private static Identifier CROSSHAIR_SPRITE;
    @Shadow @Final private static Identifier CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE;
    @Shadow @Final private static Identifier CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE;
    @Shadow @Final private static Identifier CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE;
    private static boolean craneshot$loggedCrosshairRedirect;

    @Redirect(
            method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
            )
    )
    private void craneshot$maybeDrawCrosshairTexture(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier texture, int x, int y, int width, int height) {
        if (craneshot$shouldDrawCrosshairTexture(texture)) {
            context.blitSprite(pipeline, texture, x, y, width, height);
        }
    }

    @Redirect(
            method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"
            )
    )
    private void craneshot$maybeDrawCrosshairTexture(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier texture, int u, int v, int width, int height, int x, int y, int regionWidth, int regionHeight) {
        if (craneshot$shouldDrawCrosshairTexture(texture)) {
            context.blitSprite(pipeline, texture, u, v, width, height, x, y, regionWidth, regionHeight);
        }
    }

    private static boolean craneshot$shouldDrawCrosshairTexture(Identifier texture) {
        if (!craneshot$loggedCrosshairRedirect) {
            craneshot$loggedCrosshairRedirect = true;
            Craneshot.LOGGER.debug("Craneshot crosshair redirect active; showVanillaCrosshair={}",
                    ninja.trek.config.GeneralMenuSettings.isShowVanillaCrosshair());
        }
        if (ninja.trek.config.GeneralMenuSettings.isShowVanillaCrosshair()) {
            return true;
        }
        return !craneshot$isVanillaCrosshairTexture(texture);
    }

    private static boolean craneshot$isVanillaCrosshairTexture(Identifier texture) {
        return texture.equals(CROSSHAIR_SPRITE)
                || texture.equals(CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE)
                || texture.equals(CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE)
                || texture.equals(CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE);
    }
}
