package ninja.trek.config;

import com.google.gson.*;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.ICameraMovement;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.minecraft.client.Minecraft;

public class FollowerSettingsIO {
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/craneshot_followers.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void saveFollowers(FollowerConfig config) {
        try {
            File parentDir = CONFIG_FILE.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
        } catch (Exception e) {
            Craneshot.LOGGER.warn("Failed to ensure config directory exists for follower config", e);
        }

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            JsonObject root = new JsonObject();
            root.addProperty("targetPlayerName", config.getTargetPlayerName());

            JsonArray followersArray = new JsonArray();
            for (FollowerConfig.FollowerEntry entry : config.getFollowers()) {
                JsonObject entryObj = new JsonObject();
                entryObj.addProperty("mode", entry.getMode().name());

                if (entry.getMode() == FollowerConfig.FollowerMode.MOVEMENT && entry.getMovement() != null) {
                    entryObj.add("movement", SlotSettingsIO.movementToJson(entry.getMovement()));
                } else {
                    entryObj.add("movement", JsonNull.INSTANCE);
                }

                followersArray.add(entryObj);
            }
            root.add("followers", followersArray);

            GSON.toJson(root, writer);
        } catch (IOException e) {
            Craneshot.LOGGER.warn("Failed to save follower configuration", e);
        }
    }

    public static long getConfigLastModified() {
        if (CONFIG_FILE.exists()) {
            return CONFIG_FILE.lastModified();
        }
        return 0;
    }

    public static FollowerConfig loadFollowers() {
        FollowerConfig config = new FollowerConfig();

        if (!CONFIG_FILE.exists()) {
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("targetPlayerName")) {
                config.setTargetPlayerName(root.get("targetPlayerName").getAsString());
            }

            if (root.has("followers") && root.get("followers").isJsonArray()) {
                JsonArray followersArray = root.getAsJsonArray("followers");
                for (JsonElement element : followersArray) {
                    JsonObject entryObj = element.getAsJsonObject();

                    FollowerConfig.FollowerMode mode = FollowerConfig.FollowerMode.MOVEMENT;
                    if (entryObj.has("mode")) {
                        try {
                            mode = FollowerConfig.FollowerMode.valueOf(entryObj.get("mode").getAsString());
                        } catch (IllegalArgumentException ignored) {}
                    }

                    ICameraMovement movement = null;
                    if (entryObj.has("movement") && !entryObj.get("movement").isJsonNull()) {
                        movement = SlotSettingsIO.jsonToMovement(entryObj.getAsJsonObject("movement"));
                    }

                    config.addFollower(new FollowerConfig.FollowerEntry(mode, movement));
                }
            }

            return config;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Craneshot.LOGGER.warn("Failed to read follower configuration, using defaults", e);
            return new FollowerConfig();
        }
    }
}
