package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import ninja.trek.Craneshot;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MovementConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(MinecraftClient.getInstance().runDirectory, "config/craneshot_movements.json");
    private static Map<String, Map<String, Object>> movementSettings = new HashMap<>();

    public static void loadSettings() {
        if (!CONFIG_FILE.exists()) return;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
            movementSettings = GSON.fromJson(reader, type);
//            Craneshot.LOGGER.debug("Loaded movement settings: {}", movementSettings);
        } catch (Exception e) {
//            Craneshot.LOGGER.error("Error loading movement settings", e);
        }
    }

    public static void saveSettings() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            //GSON.toJson(movementSettings, writer);
//            Craneshot.LOGGER.debug("Saved movement settings: {}", movementSettings);
        } catch (Exception e) {
//            Craneshot.LOGGER.error("Error saving movement settings", e);
        }
    }

    public static Map<String, Object> getSettingsForMovement(String movementName) {
        Map<String, Object> settings = movementSettings.getOrDefault(movementName, new HashMap<>());
//        Craneshot.LOGGER.debug("Retrieved settings for {}: {}", movementName, settings);
        return settings;
    }

    public static void updateMovementSettings(String movementName, Map<String, Object> settings) {
//        Craneshot.LOGGER.debug("Updating settings for {}: {}", movementName, settings);
        movementSettings.put(movementName, settings);
        saveSettings();
    }
}