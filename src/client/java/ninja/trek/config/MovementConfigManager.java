package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import ninja.trek.cameramovements.ICameraMovement;

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveSettings() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(movementSettings, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Object> getSettingsForMovement(String movementName) {
        return movementSettings.getOrDefault(movementName, new HashMap<>());
    }

    public static void updateMovementSettings(String movementName, Map<String, Object> settings) {
        movementSettings.put(movementName, settings);
        saveSettings();
    }
}
