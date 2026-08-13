package ninja.trek.config;

import ninja.trek.Craneshot;
import ninja.trek.cameramovements.AbstractMovementSettings;

import java.lang.reflect.Field;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class SettingWidget {

    // Add this to the SettingWidget class in SettingWidget.java

    private static Button createWarningButton(int x, int y) {
        return Button.builder(
                        Component.literal("!").withStyle(ChatFormatting.GOLD),
                        button -> {}  // Empty click handler since we're just showing tooltip
                )
                .bounds(x, y, 20, 20)
                .tooltip(Tooltip.create(Component.literal("Warning: This configuration may cause view instability")))
                .build();
    }

    public static Button[] createEnumButtonWithWarning(
            int x, int y, int width, int height,
            String fieldName, AbstractMovementSettings settings,
            MovementSetting annotation
    ) {
        Button enumButton = createEnumButton(x, y, width, height, fieldName, settings, annotation);

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
            // logging removed
        }

        if (showWarning) {
            Button warningButton = createWarningButton(x + width + 5, y);
            return new Button[]{enumButton, warningButton};
        } else {
            return new Button[]{enumButton};
        }
    }

    public static Button createEnumButton(int x, int y, int width, int height,
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
            Button button = Button.builder(
                    Component.literal(formatButtonText(annotation.label(), initialValue.toString())),
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
                            btn.setMessage(Component.literal(formatButtonText(annotation.label(), nextValue.toString())));

                            // If this is either the postMoveMouse or postMoveKeys field, force a menu refresh to update warnings
                            if (fieldName.equals("postMoveMouse") || fieldName.equals("postMoveKeys")) {
                                if (Minecraft.getInstance().gui.screen() instanceof MenuOverlayScreen menuScreen) {
                                    menuScreen.reinitialize();
                                }
                            }
                        } catch (IllegalAccessException e) {
                            // logging removed
                        }
                    }
            ).bounds(x, y, width, height).build();

            return button;
        } catch (Exception e) {
            // logging removed
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

    public static AbstractSliderButton createSlider(int x, int y, int width, int height, Component label,
                                            double min, double max, double value, String fieldName, AbstractMovementSettings settings) {
        return new SettingSlider(x, y, width, height, label, min, max, value, fieldName, settings);
    }
}
