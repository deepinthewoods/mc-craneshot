package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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

                // Save wrap states and toggle states
                JsonObject wrapStatesObj = new JsonObject();
                JsonObject toggleStatesObj = new JsonObject();
                for (int i = 0; i < 3; i++) {
                    wrapStatesObj.addProperty("slot" + i, SlotMenuSettings.getWrapState(i));
                    toggleStatesObj.addProperty("slot" + i, SlotMenuSettings.getToggleState(i));
                }
                settingsObj.add("wrapStates", wrapStatesObj);
                settingsObj.add("toggleStates", toggleStatesObj);

                // Save FreeCamSettings
                JsonObject freeCamObj = new JsonObject();
                FreeCamSettings freeCam = GeneralMenuSettings.getFreeCamSettings();
                freeCamObj.addProperty("moveSpeed", freeCam.getMoveSpeed());
                freeCamObj.addProperty("acceleration", freeCam.getAcceleration());
                freeCamObj.addProperty("deceleration", freeCam.getDeceleration());
                freeCamObj.addProperty("movementMode", freeCam.getMovementMode().name());
                settingsObj.add("freeCam", freeCamObj);
                
                // Save FreeCamReturnMovement settings
                JsonObject freeCamReturnObj = new JsonObject();
                ninja.trek.cameramovements.movements.FreeCamReturnMovement freeCamReturn = GeneralMenuSettings.getFreeCamReturnMovement();
                // Save settings using reflection to access private fields
                java.util.Map<String, Object> returnSettings = ((ninja.trek.cameramovements.AbstractMovementSettings)freeCamReturn).getSettings();
                returnSettings.forEach((key, value) -> {
                    if (value instanceof Float || value instanceof Double) {
                        freeCamReturnObj.addProperty(key, ((Number)value).doubleValue());
                    } else if (value instanceof String) {
                        freeCamReturnObj.addProperty(key, (String)value);
                    } else if (value instanceof Boolean) {
                        freeCamReturnObj.addProperty(key, (Boolean)value);
                    } else if (value instanceof Enum) {
                        freeCamReturnObj.addProperty(key, ((Enum<?>)value).name());
                    }
                });
                settingsObj.add("freeCamReturn", freeCamReturnObj);

                // Save autoAdvance
                settingsObj.addProperty("autoAdvance", GeneralMenuSettings.isAutoAdvance());
                
                // Save expanded settings state
                JsonArray expandedSettingsArray = new JsonArray();
                for (String key : MenuOverlayScreen.getExpandedSettings()) {
                    expandedSettingsArray.add(key);
                }
                settingsObj.add("expandedSettings", expandedSettingsArray);

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

            // Load wrap states and toggle states
            if (settingsObj.has("wrapStates")) {
                JsonObject wrapStatesObj = settingsObj.getAsJsonObject("wrapStates");
                for (int i = 0; i < 3; i++) {
                    String key = "slot" + i;
                    if (wrapStatesObj.has(key)) {
                        boolean wrapState = wrapStatesObj.get(key).getAsBoolean();
                        SlotMenuSettings.setWrapState(i, wrapState);
                    }
                }
            }

            if (settingsObj.has("toggleStates")) {
                JsonObject toggleStatesObj = settingsObj.getAsJsonObject("toggleStates");
                for (int i = 0; i < 3; i++) {
                    String key = "slot" + i;
                    if (toggleStatesObj.has(key)) {
                        boolean toggleState = toggleStatesObj.get(key).getAsBoolean();
                        SlotMenuSettings.setToggleState(i, toggleState);
                    }
                }
            }

            // Load FreeCamSettings
            if (settingsObj.has("freeCam")) {
                JsonObject freeCamObj = settingsObj.getAsJsonObject("freeCam");
                FreeCamSettings freeCam = GeneralMenuSettings.getFreeCamSettings();

                if (freeCamObj.has("moveSpeed")) {
                    freeCam.setMoveSpeed(freeCamObj.get("moveSpeed").getAsFloat());
                }
                if (freeCamObj.has("acceleration")) {
                    freeCam.setAcceleration(freeCamObj.get("acceleration").getAsFloat());
                }
                if (freeCamObj.has("deceleration")) {
                    freeCam.setDeceleration(freeCamObj.get("deceleration").getAsFloat());
                }
                if (freeCamObj.has("movementMode")) {
                    freeCam.setMovementMode(
                            FreeCamSettings.MovementMode.valueOf(
                                    freeCamObj.get("movementMode").getAsString()
                            )
                    );
                }
            }
            
            // Load FreeCamReturnMovement settings
            if (settingsObj.has("freeCamReturn")) {
                JsonObject returnObj = settingsObj.getAsJsonObject("freeCamReturn");
                ninja.trek.cameramovements.movements.FreeCamReturnMovement freeCamReturn = GeneralMenuSettings.getFreeCamReturnMovement();
                
                // Use the movement's updateSetting method to load each setting
                for (String key : returnObj.keySet()) {
                    try {
                        if (returnObj.get(key).isJsonPrimitive()) {
                            if (returnObj.get(key).getAsJsonPrimitive().isString()) {
                                ((ninja.trek.cameramovements.AbstractMovementSettings)freeCamReturn).updateSetting(
                                    key, returnObj.get(key).getAsString());
                            } else if (returnObj.get(key).getAsJsonPrimitive().isNumber()) {
                                ((ninja.trek.cameramovements.AbstractMovementSettings)freeCamReturn).updateSetting(
                                    key, returnObj.get(key).getAsDouble());
                            } else if (returnObj.get(key).getAsJsonPrimitive().isBoolean()) {
                                ((ninja.trek.cameramovements.AbstractMovementSettings)freeCamReturn).updateSetting(
                                    key, returnObj.get(key).getAsBoolean());
                            }
                        }
                    } catch (Exception e) {
                        Craneshot.LOGGER.error("Error loading FreeCamReturn setting: " + key, e);
                    }
                }
            }

            // Load autoAdvance
            if (settingsObj.has("autoAdvance")) {
                GeneralMenuSettings.setAutoAdvance(settingsObj.get("autoAdvance").getAsBoolean());
            }
            
            // Load expanded settings
            if (settingsObj.has("expandedSettings") && settingsObj.get("expandedSettings").isJsonArray()) {
                JsonArray expandedSettingsArray = settingsObj.getAsJsonArray("expandedSettings");
                MenuOverlayScreen.clearExpandedSettings();
                for (int i = 0; i < expandedSettingsArray.size(); i++) {
                    if (expandedSettingsArray.get(i).isJsonPrimitive()) {
                        MenuOverlayScreen.addExpandedSetting(expandedSettingsArray.get(i).getAsString());
                    }
                }
            }

            Craneshot.LOGGER.info("Loaded general settings configuration");
        } catch (IOException e) {
            Craneshot.LOGGER.error("Failed to load general settings", e);
        }
    }
}