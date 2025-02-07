package ninja.trek.cameramovements;
import net.minecraft.entity.player.PlayerEntity;
import ninja.trek.Craneshot;
import ninja.trek.config.MovementSetting;
import ninja.trek.config.MovementSettingType;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractMovementSettings {
    private String customName = null;

    @MovementSetting(
            label = "Raycast Type",
            type = MovementSettingType.ENUM,
            description = "Controls how the camera handles collision with blocks"
    )
    private RaycastType raycastType = RaycastType.NEAR;
    public double alpha;

    public enum START_TARGET {PLAYER};
    public enum END_TARGET {BACK, FRONT}

    @MovementSetting(
            label = "Camera Position",
            type = MovementSettingType.ENUM,
            description = "Determines if camera follows in front or behind the player"
    )
    protected END_TARGET endTarget = END_TARGET.BACK;

    protected CameraTarget getEndTarget(PlayerEntity player, double targetDistance) {
        switch (endTarget){
            default:
            case BACK: return CameraTarget.fromDistanceBack(player, targetDistance);
            case FRONT: return CameraTarget.fromDistanceFront(player, targetDistance);
        }


    }

    public RaycastType getRaycastType() {
        return raycastType != null ? raycastType : RaycastType.NONE;
    }

    public void setRaycastType(RaycastType type) {
        this.raycastType = type;
    }

    public String getDisplayName() {
        return customName != null ? customName : getClass().getSimpleName();
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        Stream.concat(
                        Arrays.stream(this.getClass().getDeclaredFields()),
                        Arrays.stream(AbstractMovementSettings.class.getDeclaredFields())
                )
                .filter(field -> field.isAnnotationPresent(MovementSetting.class) || field.getName().equals("customName"))
                .forEach(field -> {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(this);
                        if (value instanceof Enum<?>) {
                            settings.put(field.getName(), ((Enum<?>) value).name());
                        } else {
                            settings.put(field.getName(), value);
                        }
                    } catch (IllegalAccessException e) {
                        Craneshot.LOGGER.error("Error accessing field: " + field.getName(), e);
                    }
                });
        return settings;
    }

    public void updateSetting(String key, Object value) {
        try {
            if (key.equals("customName")) {
                setCustomName((String)value);
                return;
            }

            Field field = findField(key);
            if (field != null && field.isAnnotationPresent(MovementSetting.class)) {
                field.setAccessible(true);

                // Handle different field types
                if (field.getType().isEnum()) {
                    if (value instanceof String) {
                        @SuppressWarnings("unchecked")
                        Enum<?> enumValue = Enum.valueOf(field.getType().asSubclass(Enum.class), (String) value);
                        field.set(this, enumValue);
                        if (key.equals("raycastType")) {
                            setRaycastType((RaycastType)enumValue);
                        }
                    }
                } else if (field.getType() == double.class || field.getType() == Double.class) {
                    double doubleValue;
                    if (value instanceof Number) {
                        doubleValue = ((Number)value).doubleValue();
                    } else if (value instanceof String) {
                        doubleValue = Double.parseDouble((String)value);
                    } else {
                        throw new IllegalArgumentException("Cannot convert " + value + " to double");
                    }
                    field.setDouble(this, doubleValue);
                } else if (field.getType() == float.class || field.getType() == Float.class) {
                    float floatValue;
                    if (value instanceof Number) {
                        floatValue = ((Number)value).floatValue();
                    } else if (value instanceof String) {
                        floatValue = Float.parseFloat((String)value);
                    } else {
                        throw new IllegalArgumentException("Cannot convert " + value + " to float");
                    }
                    field.setFloat(this, floatValue);
                } else if (field.getType() == int.class || field.getType() == Integer.class) {
                    int intValue;
                    if (value instanceof Number) {
                        intValue = ((Number)value).intValue();
                    } else if (value instanceof String) {
                        intValue = Integer.parseInt((String)value);
                    } else {
                        throw new IllegalArgumentException("Cannot convert " + value + " to integer");
                    }
                    field.setInt(this, intValue);
                } else {
                    // Default fallback for other types
                    field.set(this, value);
                }
            }
        } catch (Exception e) {
            Craneshot.LOGGER.error("Error updating setting {} with value {}: {}", key, value, e.getMessage());
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