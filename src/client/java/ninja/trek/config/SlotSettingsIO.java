package ninja.trek.config;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.movements.LinearMovement;

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
        } catch (IOException e) {
            // logging removed
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
            // logging removed
            // Return default movement if loading fails
            return new LinearMovement();
        }
    }

    public static List<List<ICameraMovement>> loadSlots() {
        List<List<ICameraMovement>> slots = new ArrayList<>();

        if (!CONFIG_FILE.exists()) {
            // Return default configuration: first 3 slots with a default movement, rest empty up to 10
            for (int i = 0; i < 10; i++) {
                List<ICameraMovement> slot = new ArrayList<>();
                if (i < 3) {
                    slot.add(new LinearMovement());
                }
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

        } catch (IOException e) {
            // logging removed
            // Return default configuration on error: first 3 initialized, up to 10 total
            for (int i = 0; i < 10; i++) {
                List<ICameraMovement> slot = new ArrayList<>();
                if (i < 3) {
                    slot.add(new LinearMovement());
                }
                slots.add(slot);
            }
        }

        return slots;
    }

    public static void copyMovementToClipboard(ICameraMovement movement) {
        try {
            JsonObject movementJson = movementToJson(movement);
            String jsonStr = GSON.toJson(movementJson);

            // Use Minecraft's clipboard handling
            MinecraftClient.getInstance().keyboard.setClipboard(jsonStr);
        } catch (Exception e) {
            // logging removed
        }
    }

    public static ICameraMovement createMovementFromClipboard() {
        try {
            // Use Minecraft's clipboard handling
            String clipboardText = MinecraftClient.getInstance().keyboard.getClipboard();
            JsonObject movementObj = JsonParser.parseString(clipboardText).getAsJsonObject();
            return jsonToMovement(movementObj);
        } catch (Exception e) {
            // logging removed
            return new LinearMovement(); // Return default movement if parsing fails
        }
    }

    private static class CameraMovementSerializer implements JsonSerializer<ICameraMovement> {
        @Override
        public JsonElement serialize(ICameraMovement movement, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            return movementToJson(movement);
        }
    }
}
