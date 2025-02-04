package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class WrapSettings {
    private static final File CONFIG_FILE = new File(MinecraftClient.getInstance().runDirectory, "config/craneshot_wrap.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<Integer, Boolean> wrapStates = new HashMap<>();

    public static void loadSettings() {
        if (!CONFIG_FILE.exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            wrapStates = GSON.fromJson(reader, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveSettings() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(wrapStates, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean getWrapState(int slotIndex) {
        return wrapStates.getOrDefault(slotIndex, false);
    }

    public static void setWrapState(int slotIndex, boolean state) {
        wrapStates.put(slotIndex, state);
        saveSettings();
    }
}