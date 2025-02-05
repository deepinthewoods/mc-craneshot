package ninja.trek.config;

import ninja.trek.cameramovements.RaycastType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMovementSettings {
    @MovementSetting(
            label = "Raycast Type",
            type = MovementSettingType.ENUM,
            description = "Controls how the camera handles collision with blocks"
    )
    protected RaycastType raycastType = RaycastType.NEAR;

    public AbstractMovementSettings() {
        loadSettings();
    }

    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(MovementSetting.class)) {
                field.setAccessible(true);
                try {
                    Object value = field.get(this);
                    // Handle enum serialization
                    if (value instanceof Enum<?>) {
                        settings.put(field.getName(), ((Enum<?>) value).name());
                    } else {
                        settings.put(field.getName(), value);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        // Also check superclass (AbstractMovementSettings) fields
        for (Field field : this.getClass().getSuperclass().getDeclaredFields()) {
            if (field.isAnnotationPresent(MovementSetting.class)) {
                field.setAccessible(true);
                try {
                    Object value = field.get(this);
                    if (value instanceof Enum<?>) {
                        settings.put(field.getName(), ((Enum<?>) value).name());
                    } else {
                        settings.put(field.getName(), value);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return settings;
    }

    public void updateSetting(String key, Object value) {
        try {
            // First try to find the field in the current class
            Field field = null;
            try {
                field = this.getClass().getDeclaredField(key);
            } catch (NoSuchFieldException e) {
                // If not found, try the superclass
                field = this.getClass().getSuperclass().getDeclaredField(key);
            }

            if (field != null && field.isAnnotationPresent(MovementSetting.class)) {
                field.setAccessible(true);
                if (field.getType().isEnum() && value instanceof String) {
                    // Handle enum deserialization
                    @SuppressWarnings("unchecked")
                    Object enumValue = Enum.valueOf(field.getType().asSubclass(Enum.class), (String) value);
                    field.set(this, enumValue);
                } else {
                    field.set(this, value);
                }
                saveSettings();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public RaycastType getRaycastType() {
        return raycastType;
    }

    private void saveSettings() {
        MovementConfigManager.updateMovementSettings(this.getClass().getSimpleName(), getSettings());
    }

    private void loadSettings() {
        Map<String, Object> savedSettings = MovementConfigManager.getSettingsForMovement(this.getClass().getSimpleName());
        for (Map.Entry<String, Object> entry : savedSettings.entrySet()) {
            updateSetting(entry.getKey(), entry.getValue());
        }
    }
}