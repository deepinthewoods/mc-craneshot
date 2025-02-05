package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.*;

public class TransitionModeManager {
    private static final File CONFIG_FILE = new File(MinecraftClient.getInstance().runDirectory, "config/craneshot_transition.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TransitionMode currentMode = TransitionMode.IMMEDIATE;

    public static void loadSettings() {
        if (!CONFIG_FILE.exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            String modeName = GSON.fromJson(reader, String.class);
            currentMode = TransitionMode.valueOf(modeName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveSettings() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(currentMode.name(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static TransitionMode getCurrentMode() {
        return currentMode;
    }

    public static void setCurrentMode(TransitionMode mode) {
        currentMode = mode;
        saveSettings();
    }
}