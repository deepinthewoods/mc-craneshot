package ninja.trek.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.LinearMovement;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MovementConfigManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("craneshot");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MovementConfigManager INSTANCE;

    // Store both the movement configs and slot configurations
    private final Map<String, JsonObject> savedConfigs = new HashMap<>();
    private final Map<Integer, JsonArray> slotConfigs = new HashMap<>();

    public static MovementConfigManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MovementConfigManager();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    private MovementConfigManager() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to create config directory", e);
        }
    }

    // New method to save slot configuration
    public void saveSlotConfig(int slot, List<ICameraMovement> movements) {
        JsonArray slotConfig = new JsonArray();
        for (ICameraMovement movement : movements) {
            slotConfig.add(movement.getClass().getName());
        }
        slotConfigs.put(slot, slotConfig);
        save();
    }

    // New method to load slot configuration
    public List<ICameraMovement> loadSlotConfig(int slot) {
        List<ICameraMovement> movements = new ArrayList<>();
        JsonArray slotConfig = slotConfigs.get(slot);

        if (slotConfig != null) {
            for (JsonElement element : slotConfig) {
                try {
                    String className = element.getAsString();
                    Class<?> movementClass = Class.forName(className);
                    ICameraMovement movement = (ICameraMovement) movementClass.getDeclaredConstructor().newInstance();
                    movements.add(movement);
                    loadMovementConfig(movement, slot);
                } catch (Exception e) {
                    Craneshot.LOGGER.error("Failed to load movement: " + element.getAsString(), e);
                }
            }
        }

        // Ensure at least one movement exists
        if (movements.isEmpty()) {
            movements.add(new LinearMovement());
        }

        return movements;
    }

    public void saveMovementConfig(ICameraMovement movement, int slot) {
        try {
            JsonObject config = new JsonObject();
            Class<?> clazz = movement.getClass();

            // Save class type for loading
            config.addProperty("_type", clazz.getName());

            // Save all annotated fields
            for (Field field : clazz.getDeclaredFields()) {
                ConfigField annotation = field.getAnnotation(ConfigField.class);
                if (annotation == null || annotation.readonly()) continue;

                field.setAccessible(true);
                JsonElement value = null;

                if (field.getType() == double.class || field.getType() == Double.class) {
                    value = new JsonPrimitive(field.getDouble(movement));
                } else if (field.getType() == float.class || field.getType() == Float.class) {
                    value = new JsonPrimitive(field.getFloat(movement));
                } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    value = new JsonPrimitive(field.getBoolean(movement));
                } else if (field.getType() == int.class || field.getType() == Integer.class) {
                    value = new JsonPrimitive(field.getInt(movement));
                } else if (field.getType() == String.class) {
                    value = new JsonPrimitive((String)field.get(movement));
                }

                if (value != null) {
                    config.add(field.getName(), value);
                }
            }

            savedConfigs.put(getConfigKey(movement, slot), config);
            save();
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to save movement config for slot " + slot, e);
        }
    }

    public void loadMovementConfig(ICameraMovement movement, int slot) {
        try {
            JsonObject config = savedConfigs.get(getConfigKey(movement, slot));
            if (config == null) return;

            Class<?> clazz = movement.getClass();
            for (Field field : clazz.getDeclaredFields()) {
                ConfigField annotation = field.getAnnotation(ConfigField.class);
                if (annotation == null || annotation.readonly()) continue;

                JsonElement element = config.get(field.getName());
                if (element == null) continue;

                field.setAccessible(true);
                if (field.getType() == double.class || field.getType() == Double.class) {
                    field.setDouble(movement, element.getAsDouble());
                } else if (field.getType() == float.class || field.getType() == Float.class) {
                    field.setFloat(movement, element.getAsFloat());
                } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    field.setBoolean(movement, element.getAsBoolean());
                } else if (field.getType() == int.class || field.getType() == Integer.class) {
                    field.setInt(movement, element.getAsInt());
                } else if (field.getType() == String.class) {
                    field.set(movement, element.getAsString());
                }
            }
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to load movement config for slot " + slot, e);
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();

            // Save individual movement configs
            JsonObject configs = new JsonObject();
            savedConfigs.forEach(configs::add);
            root.add("movements", configs);

            // Save slot configurations
            JsonObject slots = new JsonObject();
            slotConfigs.forEach((slot, movements) -> slots.add(String.valueOf(slot), movements));
            root.add("slots", slots);

            Path configFile = CONFIG_DIR.resolve("movement_configs.json");
            Files.writeString(configFile, GSON.toJson(root));
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to save movement configs", e);
        }
    }

    private void load() {
        try {
            Path configFile = CONFIG_DIR.resolve("movement_configs.json");
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                JsonObject root = GSON.fromJson(json, JsonObject.class);

                // Load individual movement configs
                JsonObject configs = root.getAsJsonObject("movements");
                if (configs != null) {
                    configs.entrySet().forEach(entry ->
                            savedConfigs.put(entry.getKey(), entry.getValue().getAsJsonObject()));
                }

                // Load slot configurations
                JsonObject slots = root.getAsJsonObject("slots");
                if (slots != null) {
                    slots.entrySet().forEach(entry ->
                            slotConfigs.put(Integer.parseInt(entry.getKey()),
                                    entry.getValue().getAsJsonArray()));
                }
            }
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to load movement configs", e);
        }
    }

    private String getConfigKey(ICameraMovement movement, int slot) {
        return movement.getClass().getName() + "#" + slot;
    }

    // New method to clear all configurations for a slot
    public void clearSlotConfig(int slot) {
        slotConfigs.remove(slot);
        // Remove all movement configs for this slot
        savedConfigs.entrySet().removeIf(entry -> entry.getKey().endsWith("#" + slot));
        save();
    }
}