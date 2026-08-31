package ninja.trek.follower;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FollowerChunkLoadingRegistry {
    private static final Map<UUID, Integer> FOLLOWERS = new ConcurrentHashMap<>();
    private static final Set<UUID> LOGGED_SUPPRESSIONS = ConcurrentHashMap.newKeySet();

    private FollowerChunkLoadingRegistry() {}

    public static void register(ServerPlayer player, int followerIndex) {
        FOLLOWERS.put(player.getUUID(), followerIndex);
        LOGGED_SUPPRESSIONS.remove(player.getUUID());
    }

    public static void unregister(UUID playerId) {
        FOLLOWERS.remove(playerId);
        LOGGED_SUPPRESSIONS.remove(playerId);
    }

    public static boolean shouldSuppressChunkLoading(ServerPlayer player) {
        return player.isSpectator() && FOLLOWERS.containsKey(player.getUUID());
    }

    public static boolean markSuppressionLogged(UUID playerId) {
        return LOGGED_SUPPRESSIONS.add(playerId);
    }

    public static void clear() {
        FOLLOWERS.clear();
        LOGGED_SUPPRESSIONS.clear();
    }
}
