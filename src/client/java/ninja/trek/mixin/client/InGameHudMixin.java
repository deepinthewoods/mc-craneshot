package ninja.trek.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import ninja.trek.Craneshot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Shadow @Final private static Identifier CROSSHAIR_TEXTURE;
    @Shadow @Final private static Identifier CROSSHAIR_ATTACK_INDICATOR_FULL_TEXTURE;
    @Shadow @Final private static Identifier CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_TEXTURE;
    @Shadow @Final private static Identifier CROSSHAIR_ATTACK_INDICATOR_PROGRESS_TEXTURE;
    private static boolean craneshot$loggedCrosshairRedirect;

    @Redirect(
            method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
            )
    )
    private void craneshot$maybeDrawCrosshairTexture(DrawContext context, RenderPipeline pipeline, Identifier texture, int x, int y, int width, int height) {
        if (craneshot$shouldDrawCrosshairTexture(texture)) {
            context.drawGuiTexture(pipeline, texture, x, y, width, height);
        }
    }

    @Redirect(
            method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIIIII)V"
            )
    )
    private void craneshot$maybeDrawCrosshairTexture(DrawContext context, RenderPipeline pipeline, Identifier texture, int u, int v, int width, int height, int x, int y, int regionWidth, int regionHeight) {
        if (craneshot$shouldDrawCrosshairTexture(texture)) {
            context.drawGuiTexture(pipeline, texture, u, v, width, height, x, y, regionWidth, regionHeight);
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
        return texture.equals(CROSSHAIR_TEXTURE)
                || texture.equals(CROSSHAIR_ATTACK_INDICATOR_FULL_TEXTURE)
                || texture.equals(CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_TEXTURE)
                || texture.equals(CROSSHAIR_ATTACK_INDICATOR_PROGRESS_TEXTURE);
    }
}
