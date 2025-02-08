package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.AbstractMovementSettings;

import java.lang.reflect.Field;

public class SettingWidget {

    // Add this to the SettingWidget class in SettingWidget.java

    private static ButtonWidget createWarningButton(int x, int y) {
        return ButtonWidget.builder(
                        Text.literal("!").formatted(Formatting.GOLD),
                        button -> {}  // Empty click handler since we're just showing tooltip
                )
                .dimensions(x, y, 20, 20)
                .tooltip(Tooltip.of(Text.literal("Warning: This configuration may cause view instability")))
                .build();
    }

    public static ButtonWidget[] createEnumButtonWithWarning(
            int x, int y, int width, int height,
            String fieldName, AbstractMovementSettings settings,
            MovementSetting annotation
    ) {
        ButtonWidget enumButton = createEnumButton(x, y, width, height, fieldName, settings, annotation);

        // Check if we need to show warning
        boolean showWarning = false;
        try {
            if (fieldName.equals("postMoveMouse")) {
                Field mouseField = settings.getClass().getDeclaredField("postMoveMouse");
                Field keysField = settings.getClass().getDeclaredField("postMoveKeys");
                mouseField.setAccessible(true);
                keysField.setAccessible(true);

                AbstractMovementSettings.POST_MOVE_MOUSE mouseMode =
                        (AbstractMovementSettings.POST_MOVE_MOUSE) mouseField.get(settings);
                AbstractMovementSettings.POST_MOVE_KEYS keysMode =
                        (AbstractMovementSettings.POST_MOVE_KEYS) keysField.get(settings);

                if (mouseMode == AbstractMovementSettings.POST_MOVE_MOUSE.NONE &&
                        (keysMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                keysMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE)) {
                    showWarning = true;
                }
            }
        } catch (Exception e) {
            Craneshot.LOGGER.error("Error checking warning conditions", e);
        }

        if (showWarning) {
            ButtonWidget warningButton = createWarningButton(x + width + 5, y);
            return new ButtonWidget[]{enumButton, warningButton};
        } else {
            return new ButtonWidget[]{enumButton};
        }
    }

    public static ButtonWidget createEnumButton(int x, int y, int width, int height,
                                                String fieldName, AbstractMovementSettings settings,
                                                MovementSetting annotation) {
        try {
            Field field = null;
            try {
                field = settings.getClass().getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                try {
                    field = AbstractMovementSettings.class.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ex) {
                    throw new IllegalStateException("Field not found: " + fieldName);
                }
            }

            if (!field.getType().isEnum()) {
                throw new IllegalStateException("Field is not an enum type: " + fieldName);
            }

            field.setAccessible(true);
            Object initialValue = field.get(settings);
            if (!(initialValue instanceof Enum<?>)) {
                throw new IllegalStateException("Field value is not an enum: " + fieldName);
            }

            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) field.getType().asSubclass(Enum.class);
            Enum<?>[] enumConstants = enumClass.getEnumConstants();
            final Field finalField = field;

            // Create button with current value
            ButtonWidget button = ButtonWidget.builder(
                    Text.literal(formatButtonText(annotation.label(), initialValue.toString())),
                    btn -> {
                        try {
                            Enum<?> currentValue = (Enum<?>) finalField.get(settings);
                            int currentIndex = currentValue.ordinal();
                            int nextIndex = (currentIndex + 1) % enumConstants.length;
                            Enum<?> nextValue = enumConstants[nextIndex];

                            // Set the new value and update the settings
                            finalField.set(settings, nextValue);
                            settings.updateSetting(fieldName, nextValue.name());

                            // Update button text
                            btn.setMessage(Text.literal(formatButtonText(annotation.label(), nextValue.toString())));

                            // If this is either the postMoveMouse or postMoveKeys field, force a menu refresh to update warnings
                            if (fieldName.equals("postMoveMouse") || fieldName.equals("postMoveKeys")) {
                                if (MinecraftClient.getInstance().currentScreen instanceof MenuOverlayScreen menuScreen) {
                                    menuScreen.reinitialize();
                                }
                            }
                        } catch (IllegalAccessException e) {
                            Craneshot.LOGGER.error("Failed to update enum setting", e);
                        }
                    }
            ).dimensions(x, y, width, height).build();

            return button;
        } catch (Exception e) {
            Craneshot.LOGGER.error("Error creating enum button", e);
            return null;
        }
    }




    private static String formatButtonText(String label, String value) {
        // Convert SNAKE_CASE to Title Case and format nicely
        String formattedValue = value.toLowerCase()
                .replace('_', ' ')
                .trim();
        formattedValue = Character.toUpperCase(formattedValue.charAt(0)) +
                formattedValue.substring(1);
        return label + ": " + formattedValue;
    }

    public static SliderWidget createSlider(int x, int y, int width, int height, Text label,
                                            double min, double max, double value, String fieldName, AbstractMovementSettings settings) {
        return new SettingSlider(x, y, width, height, label, min, max, value, fieldName, settings);
    }
}