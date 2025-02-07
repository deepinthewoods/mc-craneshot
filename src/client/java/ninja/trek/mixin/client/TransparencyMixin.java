package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import ninja.trek.CameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

public class TransparencyMixin {

    @Mixin(PlayerEntityRenderer.class)
    public static class PlayerMixin {
        @ModifyVariable(method = "render", at = @At("HEAD"), ordinal = 0)
        private VertexConsumerProvider modifyVertexConsumer(VertexConsumerProvider vertexConsumers,
                                                            AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                                            MatrixStack matrices, VertexConsumerProvider originalVertexConsumers, int light) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.gameRenderer.getCamera() == null) return originalVertexConsumers;

            double distance = client.gameRenderer.getCamera().getPos().distanceTo(player.getEyePos());
            float alpha;

            if (distance <= CameraController.FIRST_PERSON_THRESHOLD_MIN) {
                alpha = 0.0f;
            } else if (distance >= CameraController.FIRST_PERSON_THRESHOLD_MAX) {
                alpha = 1.0f;
            } else {
                alpha = (float) ((distance - CameraController.FIRST_PERSON_THRESHOLD_MIN) /
                        (CameraController.FIRST_PERSON_THRESHOLD_MAX - CameraController.FIRST_PERSON_THRESHOLD_MIN));
            }

            return new VertexConsumerProvider() {
                @Override
                public VertexConsumer getBuffer(RenderLayer renderLayer) {
                    if (renderLayer.equals(RenderLayer.getEntitySolid(player.getSkinTextures().texture()))) {
                        RenderLayer translucentLayer = RenderLayer.getEntityTranslucent(
                                player.getSkinTextures().texture(), true);
                        return new TranslucentVertexConsumer(originalVertexConsumers.getBuffer(translucentLayer), alpha);
                    }
                    return originalVertexConsumers.getBuffer(renderLayer);
                }
            };
        }
    }

    @Mixin(HeldItemRenderer.class)
    public static class ItemMixin {
        @ModifyVariable(method = "renderArm", at = @At("HEAD"), ordinal = 0)
        private VertexConsumerProvider modifyArmVertexConsumer(VertexConsumerProvider vertexConsumers,
                                                               MatrixStack matrices, VertexConsumerProvider originalVertexConsumers, int light, Arm arm) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.gameRenderer.getCamera() == null || client.player == null) return originalVertexConsumers;

            double distance = client.gameRenderer.getCamera().getPos().distanceTo(client.player.getEyePos());
            float alpha = Math.max(0.0f, Math.min(1.0f,
                    (float) (distance / CameraController.FIRST_PERSON_THRESHOLD_MIN)));

            return new VertexConsumerProvider() {
                @Override
                public VertexConsumer getBuffer(RenderLayer renderLayer) {
                    if (client.player != null &&
                            renderLayer.equals(RenderLayer.getEntitySolid(client.player.getSkinTextures().texture()))) {
                        RenderLayer translucentLayer = RenderLayer.getEntityTranslucent(
                                client.player.getSkinTextures().texture(), true);
                        return new TranslucentVertexConsumer(originalVertexConsumers.getBuffer(translucentLayer), alpha);
                    }
                    return originalVertexConsumers.getBuffer(renderLayer);
                }
            };
        }

        @ModifyVariable(method = "renderItem", at = @At("HEAD"), ordinal = 0)
        private VertexConsumerProvider modifyItemVertexConsumer(VertexConsumerProvider vertexConsumers,
                                                                LivingEntity entity, ItemStack stack, ModelTransformationMode renderMode,
                                                                boolean leftHanded, MatrixStack matrices, VertexConsumerProvider originalVertexConsumers, int light) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.gameRenderer.getCamera() == null || client.player == null) return originalVertexConsumers;

            double distance = client.gameRenderer.getCamera().getPos().distanceTo(client.player.getEyePos());
            float alpha = Math.max(0.0f, Math.min(1.0f,
                    (float) (distance / CameraController.FIRST_PERSON_THRESHOLD_MIN)));

            return new VertexConsumerProvider() {
                @Override
                public VertexConsumer getBuffer(RenderLayer renderLayer) {
                    if (!renderLayer.toString().contains("entity")) {
                        return new TranslucentVertexConsumer(originalVertexConsumers.getBuffer(renderLayer), alpha);
                    }
                    return originalVertexConsumers.getBuffer(renderLayer);
                }
            };
        }

        @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), ordinal = 0)
        private VertexConsumerProvider modifyFirstPersonItemVertexConsumer(VertexConsumerProvider vertexConsumers) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.gameRenderer.getCamera() == null || client.player == null) return vertexConsumers;

            double distance = client.gameRenderer.getCamera().getPos().distanceTo(client.player.getEyePos());
            float alpha = Math.max(0.0f, Math.min(1.0f,
                    (float) (distance / CameraController.FIRST_PERSON_THRESHOLD_MIN)));

            return new VertexConsumerProvider() {
                @Override
                public VertexConsumer getBuffer(RenderLayer renderLayer) {
                    if (!renderLayer.toString().contains("entity")) {
                        return new TranslucentVertexConsumer(vertexConsumers.getBuffer(renderLayer), alpha);
                    }
                    return vertexConsumers.getBuffer(renderLayer);
                }
            };
        }
    }

    private static class TranslucentVertexConsumer implements VertexConsumer {
        private final VertexConsumer parent;
        private final float alpha;

        public TranslucentVertexConsumer(VertexConsumer parent, float alpha) {
            this.parent = parent;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return parent.vertex(x, y, z);
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return parent.color(red, green, blue, (int)(this.alpha * alpha));
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return parent.texture(u, v);
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return parent.overlay(u, v);
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return parent.light(u, v);
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return parent.normal(x, y, z);
        }
    }
}