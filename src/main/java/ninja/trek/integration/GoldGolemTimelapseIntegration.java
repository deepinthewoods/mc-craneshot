package ninja.trek.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;
import ninja.trek.nodes.model.NodeType;
import ninja.trek.nodes.network.ServerNodeNetworking;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-side receiver for versioned Gold Golem build lifecycle events. */
public final class GoldGolemTimelapseIntegration {
    public static final String EVENT_SINK_KEY = "craneshot:gold-golem-build-event-v1";
    private static MinecraftServer server;

    private GoldGolemTimelapseIntegration() {
    }

    public static void register() {
        FabricLoader.getInstance().getObjectShare().put(EVENT_SINK_KEY,
                (Consumer<String>) GoldGolemTimelapseIntegration::accept);
        ServerLifecycleEvents.SERVER_STARTED.register(value -> server = value);
        ServerLifecycleEvents.SERVER_STOPPED.register(value -> server = null);
    }

    private static void accept(String json) {
        MinecraftServer currentServer = server;
        if (currentServer == null || json == null || json.isBlank()) return;
        currentServer.execute(() -> apply(currentServer, json));
    }

    private static void apply(MinecraftServer currentServer, String json) {
        try {
            JsonObject event = JsonParser.parseString(json).getAsJsonObject();
            if (event.get("version").getAsInt() != 1) return;

            UUID sessionId = UUID.fromString(event.get("sessionId").getAsString());
            UUID golemId = UUID.fromString(event.get("golemId").getAsString());
            UUID ownerId = event.has("ownerId") ? UUID.fromString(event.get("ownerId").getAsString()) : null;
            String state = event.get("state").getAsString().toLowerCase(Locale.ROOT);
            String mode = event.get("mode").getAsString().toLowerCase(Locale.ROOT);
            ResourceKey<Level> dimension = parseDimension(event.get("dimension").getAsString());
            if (dimension == null) return;
            ServerLevel world = currentServer.getLevel(dimension);
            if (world == null) return;

            UUID nodeId = UUID.nameUUIDFromBytes(
                    ("craneshot:gold-golem:" + sessionId).getBytes(StandardCharsets.UTF_8));
            CameraNodeDTO existing = ninja.trek.nodes.server.ServerNodeManager.get().getNode(world, nodeId);
            CameraNodeDTO node = existing == null ? new CameraNodeDTO() : existing.copy();

            node.uuid = nodeId;
            node.owner = ownerId != null ? ownerId : node.owner;
            node.name = existing == null
                    ? "Golem " + title(mode) + " " + sessionId.toString().substring(0, 8)
                    : existing.name;
            node.type = NodeType.TIMELAPSE;
            node.autoManaged = true;
            node.buildSessionId = sessionId;
            node.trackedEntityId = golemId;
            node.buildMode = mode;
            node.buildState = state;
            node.timelapseIndex = 0;
            node.timelapseEnabled = state.equals("start") || state.equals("resume");

            Vec3 origin = readVec(event.getAsJsonArray("origin"));
            if (origin != null && !mode.equals("tower") && !mode.equals("pyramid")) {
                // Tracking rigs move their authoritative node with the golem so
                // chunk subscriptions and range checks follow long builds.
                node.position = origin;
            } else if (existing == null && origin != null) {
                node.position = origin;
            }
            if (event.has("bounds")) {
                JsonObject bounds = event.getAsJsonObject("bounds");
                node.framingMin = readVec(bounds.getAsJsonArray("min"));
                node.framingMax = readVec(bounds.getAsJsonArray("max"));
                if (node.framingMin != null && node.framingMax != null) {
                    node.position = node.framingMin.add(node.framingMax).scale(0.5);
                }
            }
            if (existing == null) {
                float golemYaw = event.has("yaw") ? event.get("yaw").getAsFloat() : 0f;
                node.autoRigYaw = golemYaw + 45f;
            }

            ServerNodeNetworking.upsertManagedNode(world, node);
        } catch (Exception e) {
            Craneshot.LOGGER.warn("Ignoring malformed Gold Golem timelapse event", e);
        }
    }

    private static ResourceKey<Level> parseDimension(String value) {
        Identifier id = Identifier.tryParse(value);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    private static Vec3 readVec(JsonArray array) {
        if (array == null || array.size() < 3) return null;
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static String title(String value) {
        return value.isEmpty() ? "Build" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
