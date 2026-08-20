package ninja.trek.follower;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import ninja.trek.Craneshot;
import ninja.trek.integration.MouthAnimRelayPayload;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Resolves Mouth Anim's optional ObjectShare API when that mod is installed,
 * or receives its server relay directly on lightweight follower clients.
 */
public final class MouthAnimSpeakingBridge {
    private static final String MOUTH_ANIM_MOD_ID = "mouth-anim";
    private static final String SPEAKING_PROVIDER_KEY = "mouth-anim:speaking-provider-v1";
    private static final ConcurrentHashMap<UUID, Byte> RELAYED_STATES = new ConcurrentHashMap<>();
    private static final Predicate<UUID> FALLBACK_PROVIDER = MouthAnimSpeakingBridge::isRelayedSpeaking;

    private static boolean fallbackEnabled;
    private static boolean providerReadyLogged;
    private static boolean providerMissingLogged;

    private MouthAnimSpeakingBridge() {
    }

    public static void initialize() {
        fallbackEnabled = !FabricLoader.getInstance().isModLoaded(MOUTH_ANIM_MOD_ID);
        if (fallbackEnabled) {
            ClientPlayNetworking.registerGlobalReceiver(MouthAnimRelayPayload.TYPE, (payload, context) -> {
                if (payload.stateId() == 0) {
                    RELAYED_STATES.remove(payload.playerUuid());
                } else if (payload.stateId() >= 1 && payload.stateId() <= 6) {
                    RELAYED_STATES.put(payload.playerUuid(), payload.stateId());
                }
            });
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RELAYED_STATES.clear());
            Craneshot.LOGGER.info("Mouth Anim follower bridge enabled (standalone relay receiver)");
        } else {
            Craneshot.LOGGER.info("Mouth Anim detected; follower bridge will use its shared speaking provider");
        }
    }

    public static Predicate<UUID> getProvider() {
        Predicate<UUID> provider = fallbackEnabled ? FALLBACK_PROVIDER : getSharedProvider();
        if (provider != null) {
            if (!providerReadyLogged) {
                providerReadyLogged = true;
                Craneshot.LOGGER.info("Mouth Anim speaking provider is ready");
            }
            return provider;
        }

        if (!providerMissingLogged) {
            providerMissingLogged = true;
            Craneshot.LOGGER.warn("Mouth Anim is installed but did not publish speaking provider '{}'; speech camera switching is unavailable",
                    SPEAKING_PROVIDER_KEY);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Predicate<UUID> getSharedProvider() {
        Object provider = FabricLoader.getInstance().getObjectShare().get(SPEAKING_PROVIDER_KEY);
        return provider instanceof Predicate<?> ? (Predicate<UUID>) provider : null;
    }

    private static boolean isRelayedSpeaking(UUID playerUuid) {
        return playerUuid != null && RELAYED_STATES.containsKey(playerUuid);
    }
}
