package ninja.trek.config;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.nodes.network.payload.FollowerConfigPayload;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.minecraft.client.Minecraft;

public class FollowerSettingsIO {
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/craneshot_followers.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void saveFollowers(FollowerConfig config) {
        // Build JSON string
        JsonObject root = new JsonObject();
        root.addProperty("targetPlayerName", config.getTargetPlayerName());

        JsonArray followersArray = new JsonArray();
        for (FollowerConfig.FollowerEntry entry : config.getFollowers()) {
            JsonObject entryObj = new JsonObject();
            entryObj.addProperty("useZones", entry.isUseZones());
            entryObj.addProperty("directorEnabled", entry.isDirectorEnabled());
            entryObj.addProperty("speechCameraEnabled", entry.isSpeechCameraEnabled());
            entryObj.addProperty("instantFaceEntry", entry.isInstantFaceEntry());
            entryObj.addProperty("instantFaceReturn", entry.isInstantFaceReturn());
            entryObj.addProperty("zoomCameraEnabled", entry.isZoomCameraEnabled());
            entryObj.addProperty("timelapseEnabled", entry.isTimelapseEnabled());
            entryObj.addProperty("timelapseIntervalSeconds", entry.getTimelapseIntervalSeconds());
            entryObj.addProperty("timelapseDistanceChunks", entry.getTimelapseDistanceChunks());
            entryObj.addProperty("timelapseIndex", entry.getTimelapseIndex());
            entryObj.addProperty("speechOnsetMs", entry.getSpeechOnsetMs());
            entryObj.addProperty("speechReleaseMs", entry.getSpeechReleaseMs());
            entryObj.addProperty("trackingSmoothingSeconds", entry.getTrackingSmoothingSeconds());
            entryObj.addProperty("trackingDistance", entry.getTrackingDistance());
            entryObj.addProperty("rigElevationDegrees", entry.getRigElevationDegrees());

            if (entry.getMovement() != null) {
                entryObj.add("movement", SlotSettingsIO.movementToJson(entry.getMovement()));
            } else {
                entryObj.add("movement", JsonNull.INSTANCE);
            }
            if (entry.getSpeakingMovement() != null) {
                entryObj.add("speakingMovement", SlotSettingsIO.movementToJson(entry.getSpeakingMovement()));
            } else {
                entryObj.add("speakingMovement", JsonNull.INSTANCE);
            }
            if (entry.getZoomMovement() != null) {
                entryObj.add("zoomMovement", SlotSettingsIO.movementToJson(entry.getZoomMovement()));
            } else {
                entryObj.add("zoomMovement", JsonNull.INSTANCE);
            }

            followersArray.add(entryObj);
        }
        root.add("followers", followersArray);

        String json = GSON.toJson(root);

        // Write to file (local backup)
        try {
            File parentDir = CONFIG_FILE.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
        } catch (Exception e) {
            Craneshot.LOGGER.warn("Failed to ensure config directory exists for follower config", e);
        }

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(json);
        } catch (IOException e) {
            Craneshot.LOGGER.warn("Failed to save follower configuration", e);
        }

        // Send over network to server (which relays to follower instances)
        try {
            if (Minecraft.getInstance().getConnection() != null) {
                ClientPlayNetworking.send(new FollowerConfigPayload(json));
            }
        } catch (Exception e) {
            Craneshot.LOGGER.debug("Could not send follower config over network (not connected?)", e);
        }
    }

    public static long getConfigLastModified() {
        if (CONFIG_FILE.exists()) {
            return CONFIG_FILE.lastModified();
        }
        return 0;
    }

    public static FollowerConfig parseFollowerConfigJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return parseFollowerConfigFromJson(root);
        } catch (JsonParseException | IllegalStateException e) {
            Craneshot.LOGGER.warn("Failed to parse follower config JSON from network", e);
            return null;
        }
    }

    public static FollowerConfig loadFollowers() {
        if (!CONFIG_FILE.exists()) {
            return new FollowerConfig();
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return parseFollowerConfigFromJson(root);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Craneshot.LOGGER.warn("Failed to read follower configuration, using defaults", e);
            return new FollowerConfig();
        }
    }

    private static FollowerConfig parseFollowerConfigFromJson(JsonObject root) {
        FollowerConfig config = new FollowerConfig();

        if (root.has("targetPlayerName")) {
            config.setTargetPlayerName(root.get("targetPlayerName").getAsString());
        }

        if (root.has("followers") && root.get("followers").isJsonArray()) {
            JsonArray followersArray = root.getAsJsonArray("followers");
            int followerIndex = 0;
            for (JsonElement element : followersArray) {
                JsonObject entryObj = element.getAsJsonObject();

                boolean useZones = true;
                if (entryObj.has("useZones")) {
                    useZones = entryObj.get("useZones").getAsBoolean();
                } else if (entryObj.has("mode")) {
                    // Backward compat: old format with mode enum
                    String modeStr = entryObj.get("mode").getAsString();
                    useZones = "ZONES".equals(modeStr);
                }

                ICameraMovement movement = null;
                if (entryObj.has("movement") && !entryObj.get("movement").isJsonNull()) {
                    movement = SlotSettingsIO.jsonToMovement(entryObj.getAsJsonObject("movement"));
                }

                FollowerConfig.FollowerEntry entry = new FollowerConfig.FollowerEntry(movement, useZones);
                if (entryObj.has("speakingMovement") && !entryObj.get("speakingMovement").isJsonNull()) {
                    entry.setSpeakingMovement(SlotSettingsIO.jsonToMovement(entryObj.getAsJsonObject("speakingMovement")));
                }
                if (entryObj.has("zoomMovement") && !entryObj.get("zoomMovement").isJsonNull()) {
                    entry.setZoomMovement(SlotSettingsIO.jsonToMovement(entryObj.getAsJsonObject("zoomMovement")));
                }
                entry.setDirectorEnabled(entryObj.has("directorEnabled")
                        ? entryObj.get("directorEnabled").getAsBoolean() : followerIndex == 0);
                if (entryObj.has("speechCameraEnabled")) entry.setSpeechCameraEnabled(entryObj.get("speechCameraEnabled").getAsBoolean());
                if (entryObj.has("instantFaceEntry")) entry.setInstantFaceEntry(entryObj.get("instantFaceEntry").getAsBoolean());
                if (entryObj.has("instantFaceReturn")) entry.setInstantFaceReturn(entryObj.get("instantFaceReturn").getAsBoolean());
                if (entryObj.has("zoomCameraEnabled")) entry.setZoomCameraEnabled(entryObj.get("zoomCameraEnabled").getAsBoolean());
                if (entryObj.has("timelapseEnabled")) entry.setTimelapseEnabled(entryObj.get("timelapseEnabled").getAsBoolean());
                if (entryObj.has("timelapseIntervalSeconds")) entry.setTimelapseIntervalSeconds(entryObj.get("timelapseIntervalSeconds").getAsFloat());
                if (entryObj.has("timelapseDistanceChunks")) entry.setTimelapseDistanceChunks(entryObj.get("timelapseDistanceChunks").getAsInt());
                if (entryObj.has("timelapseIndex")) entry.setTimelapseIndex(entryObj.get("timelapseIndex").getAsInt());
                if (entryObj.has("speechOnsetMs")) entry.setSpeechOnsetMs(entryObj.get("speechOnsetMs").getAsInt());
                if (entryObj.has("speechReleaseMs")) entry.setSpeechReleaseMs(entryObj.get("speechReleaseMs").getAsInt());
                if (entryObj.has("trackingSmoothingSeconds")) entry.setTrackingSmoothingSeconds(entryObj.get("trackingSmoothingSeconds").getAsFloat());
                if (entryObj.has("trackingDistance")) entry.setTrackingDistance(entryObj.get("trackingDistance").getAsFloat());
                if (entryObj.has("rigElevationDegrees")) entry.setRigElevationDegrees(entryObj.get("rigElevationDegrees").getAsFloat());
                config.addFollower(entry);
                followerIndex++;
            }
        }

        return config;
    }
}
