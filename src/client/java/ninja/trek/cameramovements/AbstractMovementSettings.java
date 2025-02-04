package ninja.trek.cameramovements;

import ninja.trek.config.MovementConfigManager;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMovementSettings {

    public AbstractMovementSettings() {
        loadSettings();
    }

    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(MovementSetting.class)) {
                field.setAccessible(true);
                try {
                    settings.put(field.getName(), field.get(this));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return settings;
    }

    public void updateSetting(String key, Object value) {
        try {
            Field field = this.getClass().getDeclaredField(key);
            if (field.isAnnotationPresent(MovementSetting.class)) {
                field.setAccessible(true);
                field.set(this, value);
                saveSettings();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
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
