package ninja.trek.config;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.AbstractMovementSettings;

import java.lang.reflect.Field;

public class SettingWidget {

    public static ButtonWidget createEnumButton ( int x, int y, int width, int height,
                                                  String fieldName, AbstractMovementSettings settings, MovementSetting annotation){
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
                            Craneshot.LOGGER.info("Updated enum setting " + fieldName + " to " + nextValue.name());
                            // Ensure raycastType is properly updated in settings
                            if (fieldName.equals("raycastType")) {
                                settings.updateSetting("raycastType", nextValue.name());
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