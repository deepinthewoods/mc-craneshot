package ninja.trek.config;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.movements.EasingMovement;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SlotSettingsIO {
    private static final File CONFIG_FILE = new File(MinecraftClient.getInstance().runDirectory, "config/craneshot_slots.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ICameraMovement.class, new CameraMovementSerializer())
            .create();

    public static JsonObject movementToJson(ICameraMovement movement) {
        JsonObject movementObj = new JsonObject();
        movementObj.addProperty("type", movement.getClass().getName());

        if (movement instanceof AbstractMovementSettings settings) {
            JsonObject settingsObj = new JsonObject();
            for (Map.Entry<String, Object> entry : settings.getSettings().entrySet()) {
                if (entry.getValue() != null) {
                    settingsObj.addProperty(entry.getKey(), entry.getValue().toString());
                }
            }
            movementObj.add("settings", settingsObj);
        }

        return movementObj;
    }

    public static void saveSlots(List<List<ICameraMovement>> slots) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            JsonArray slotsArray = new JsonArray();

            for (List<ICameraMovement> slot : slots) {
                JsonArray slotArray = new JsonArray();
                for (ICameraMovement movement : slot) {
                    slotArray.add(movementToJson(movement));
                }
                slotsArray.add(slotArray);
            }

            GSON.toJson(slotsArray, writer);
            Craneshot.LOGGER.info("Saved camera movement slots configuration");
        } catch (IOException e) {
            Craneshot.LOGGER.error("Failed to save camera movement slots", e);
        }
    }

    public static ICameraMovement jsonToMovement(JsonObject movementObj) {
        String type = movementObj.get("type").getAsString();

        try {
            Class<?> movementClass = Class.forName(type);
            Constructor<?> constructor = movementClass.getDeclaredConstructor();
            ICameraMovement movement = (ICameraMovement) constructor.newInstance();

            if (movement instanceof AbstractMovementSettings settings &&
                    movementObj.has("settings")) {
                JsonObject settingsObj = movementObj.getAsJsonObject("settings");
                for (Map.Entry<String, JsonElement> entry : settingsObj.entrySet()) {
                    String value = entry.getValue().getAsString();
                    settings.updateSetting(entry.getKey(), value);
                }
            }

            return movement;
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to load movement: " + type, e);
            // Return default movement if loading fails
            return new EasingMovement();
        }
    }

    public static List<List<ICameraMovement>> loadSlots() {
        List<List<ICameraMovement>> slots = new ArrayList<>();

        if (!CONFIG_FILE.exists()) {
            // Return default configuration with one EasingMovement per slot
            for (int i = 0; i < 3; i++) {
                List<ICameraMovement> slot = new ArrayList<>();
                slot.add(new EasingMovement());
                slots.add(slot);
            }
            return slots;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonArray slotsArray = JsonParser.parseReader(reader).getAsJsonArray();

            for (JsonElement slotElement : slotsArray) {
                List<ICameraMovement> slot = new ArrayList<>();
                JsonArray slotArray = slotElement.getAsJsonArray();

                for (JsonElement movementElement : slotArray) {
                    JsonObject movementObj = movementElement.getAsJsonObject();
                    slot.add(jsonToMovement(movementObj));
                }

                slots.add(slot);
            }

            Craneshot.LOGGER.info("Loaded camera movement slots configuration");
        } catch (IOException e) {
            Craneshot.LOGGER.error("Failed to load camera movement slots", e);
            // Return default configuration on error
            for (int i = 0; i < 3; i++) {
                List<ICameraMovement> slot = new ArrayList<>();
                slot.add(new EasingMovement());
                slots.add(slot);
            }
        }

        return slots;
    }

    private static class CameraMovementSerializer implements JsonSerializer<ICameraMovement> {
        @Override
        public JsonElement serialize(ICameraMovement movement, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            return movementToJson(movement);
        }
    }
}