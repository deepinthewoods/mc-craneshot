package ninja.trek.cameramovements;
import net.minecraft.client.MinecraftClient;
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
    @MovementSetting(label = "Pitch offset", min = -180, max = 180)
    protected float pitchOffset = 0.0f;

    @MovementSetting(
            label = "Raycast Type",
            type = MovementSettingType.ENUM,
            description = "Controls how the camera handles collision with blocks"
    )
    private RaycastType raycastType = RaycastType.NEAR;
    public double alpha;

    public END_TARGET getEndTarget() {
        return endTarget;
    }

    public enum START_TARGET {PLAYER};
    public enum END_TARGET {HEAD_BACK, HEAD_FRONT, VELOCITY_BACK, VELOCITY_FRONT, FIXED_BACK, FIXED_FRONT}
    public enum POST_MOVE_MOUSE {
        NONE,       // Default behavior
        ROTATE_CAMERA // Allow free mouse control after movement
           // Allow WASD movement after movement
    }

    @MovementSetting(
            label = "Post-Movement Mouse",
            type = MovementSettingType.ENUM,
            description = "Controls camera behavior after movement completes"
    )
    protected POST_MOVE_MOUSE postMoveMouse = POST_MOVE_MOUSE.NONE;
    public POST_MOVE_MOUSE getPostMoveMouse() {
        return postMoveMouse;
    }

    public enum POST_MOVE_KEYS {
        NONE,       // Default behavior
        MOVE_CAMERA_FLAT, // Y-axis locked camera-relative movement
        MOVE_CAMERA_FREE, // Full camera-relative movement including pitch
        MOVE8       // 8-directional player movement relative to camera
    }

    @MovementSetting(
            label = "Post-Movement Keys",
            type = MovementSettingType.ENUM,
            description = "Controls camera behavior after movement completes"
    )
    protected POST_MOVE_KEYS postMoveKeys = POST_MOVE_KEYS.NONE;
    public POST_MOVE_KEYS getPostMoveKeys(){return postMoveKeys;}

    @MovementSetting(
            label = "Camera Position",
            type = MovementSettingType.ENUM,
            description = "Determines if camera follows in front or behind the player"
    )
    protected END_TARGET endTarget = END_TARGET.HEAD_BACK;

    public enum SCROLL_WHEEL {NONE, DISTANCE, FOV};

    @MovementSetting(
            label = "Scroll",
            type = MovementSettingType.ENUM,
            description = "What the scroll wheel does while movement is active"
    )
    public SCROLL_WHEEL mouseWheel = SCROLL_WHEEL.NONE;

    @MovementSetting(label = "FOV Easing", min = 0.01, max = 1.0)
    protected double fovEasing = 0.1;

    @MovementSetting(label = "FOV Speed Limit", min = 0.1, max = 100.0)
    protected double fovSpeedLimit = 10.0;


    protected double minFov = 1.0;


    protected double maxFov = 180.0;

    @MovementSetting(label = "FOV Multiplier", min = 0.1, max = 3.0)
    protected float fovMultiplier = 1.0f;

    public void adjustFov(boolean increase, MinecraftClient client){};






    protected boolean headLockedToCamera = true;
    public boolean isHeadLockedToCamera() {
        return headLockedToCamera;
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