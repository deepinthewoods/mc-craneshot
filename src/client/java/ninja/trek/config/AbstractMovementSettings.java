package ninja.trek.config;

import ninja.trek.Craneshot;
import ninja.trek.cameramovements.RaycastType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractMovementSettings {
    @MovementSetting(
            label = "Raycast Type",
            type = MovementSettingType.ENUM,
            description = "Controls how the camera handles collision with blocks"
    )
    private RaycastType raycastType = RaycastType.NEAR;

    public RaycastType getRaycastType() {
//        Craneshot.LOGGER.debug("Getting raycast type: {}", raycastType);
        return raycastType != null ? raycastType : RaycastType.NONE;
    }

    public void setRaycastType(RaycastType type) {
        this.raycastType = type;
//        Craneshot.LOGGER.debug("Setting raycast type to: {} : {}", type);
    }

    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        Stream.concat(
                        Arrays.stream(this.getClass().getDeclaredFields()),
                        Arrays.stream(AbstractMovementSettings.class.getDeclaredFields())
                )
                .filter(field -> field.isAnnotationPresent(MovementSetting.class))
                .forEach(field -> {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(this);
                        if (value instanceof Enum<?>) {
                            settings.put(field.getName(), ((Enum<?>) value).name());
//                            Craneshot.LOGGER.debug("Serializing {}: {}", field.getName(), value);
                        } else {
                            settings.put(field.getName(), value);
                        }
                    } catch (IllegalAccessException e) {
//                        Craneshot.LOGGER.error("Error accessing field: " + field.getName(), e);
                    }
                });
        return settings;
    }

    public void updateSetting(String key, Object value) {
        try {
            Field field = findField(key);
            if (field != null && field.isAnnotationPresent(MovementSetting.class)) {
                field.setAccessible(true);
                if (field.getType().isEnum()) {
                    if (value instanceof String) {
                        @SuppressWarnings("unchecked")
                        Enum<?> enumValue = Enum.valueOf(field.getType().asSubclass(Enum.class), (String) value);
                        field.set(this, enumValue);
//                        Craneshot.LOGGER.debug("Updated enum {} to {}", key, enumValue);

                        if (key.equals("raycastType")) {
                            setRaycastType((RaycastType)enumValue);
                            Craneshot.LOGGER.info("Updated RAYCAST {} to {}", key, enumValue);
                        }
                    }
                } else {
                    field.set(this, value);
//                    Craneshot.LOGGER.debug("Updated setting {} to {}", key, value);
                }
                MovementConfigManager.updateMovementSettings(this.getClass().getSimpleName(), getSettings());
            }
        } catch (Exception e) {
//            Craneshot.LOGGER.error("Error updating setting " + key, e);
        }
    }

    private Field findField(String key) {
        try {
            return this.getClass().getDeclaredField(key);
        } catch (NoSuchFieldException e) {
            try {
                return AbstractMovementSettings.class.getDeclaredField(key);
            } catch (NoSuchFieldException ex) {
                Craneshot.LOGGER.error("Field not found: {}", key);
                return null;
            }
        }
    }
}