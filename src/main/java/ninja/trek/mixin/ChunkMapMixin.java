package ninja.trek.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import ninja.trek.Craneshot;
import ninja.trek.follower.FollowerChunkLoadingRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Inject(
            method = "skipPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craneshot$skipFollowerPlayer(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (!FollowerChunkLoadingRegistry.shouldSuppressChunkLoading(player)) return;

        if (FollowerChunkLoadingRegistry.markSuppressionLogged(player.getUUID())) {
            Craneshot.LOGGER.debug(
                    "Follower {} is now excluded from server chunk loading",
                    player.getName().getString());
        }
        cir.setReturnValue(true);
    }
}
