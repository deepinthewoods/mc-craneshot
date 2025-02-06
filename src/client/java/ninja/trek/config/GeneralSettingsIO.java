package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import ninja.trek.Craneshot;

import java.io.*;

public class GeneralSettingsIO {
    private static final File CONFIG_FILE = new File(MinecraftClient.getInstance().runDirectory, "config/craneshot_general.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void saveSettings() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                JsonObject settingsObj = new JsonObject();

                // Save TransitionMode
                settingsObj.addProperty("transitionMode", GeneralMenuSettings.getCurrentTransitionMode().name());

                // Save wrap states for each slot
                JsonObject wrapStatesObj = new JsonObject();
                for (int i = 0; i < 3; i++) {  // Assuming 3 slots
                    wrapStatesObj.addProperty("slot" + i, SlotMenuSettings.getWrapState(i));
                }
                settingsObj.add("wrapStates", wrapStatesObj);

                GSON.toJson(settingsObj, writer);
                Craneshot.LOGGER.info("Saved general settings configuration");
            }
        } catch (IOException e) {
            Craneshot.LOGGER.error("Failed to save general settings", e);
        }
    }

    public static void loadSettings() {
        if (!CONFIG_FILE.exists()) {
            Craneshot.LOGGER.info("No general settings file found, using defaults");
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject settingsObj = GSON.fromJson(reader, JsonObject.class);

            // Load TransitionMode
            if (settingsObj.has("transitionMode")) {
                String modeName = settingsObj.get("transitionMode").getAsString();
                try {
                    TransitionMode mode = TransitionMode.valueOf(modeName);
                    GeneralMenuSettings.setCurrentTransitionMode(mode);
                } catch (IllegalArgumentException e) {
                    Craneshot.LOGGER.warn("Invalid transition mode in config: " + modeName);
                }
            }

            // Load wrap states
            if (settingsObj.has("wrapStates")) {
                JsonObject wrapStatesObj = settingsObj.getAsJsonObject("wrapStates");
                for (int i = 0; i < 3; i++) {  // Assuming 3 slots
                    String key = "slot" + i;
                    if (wrapStatesObj.has(key)) {
                        boolean wrapState = wrapStatesObj.get(key).getAsBoolean();
                        SlotMenuSettings.setWrapState(i, wrapState);
                    }
                }
            }

            Craneshot.LOGGER.info("Loaded general settings configuration");
        } catch (IOException e) {
            Craneshot.LOGGER.error("Failed to load general settings", e);
        }
    }
}