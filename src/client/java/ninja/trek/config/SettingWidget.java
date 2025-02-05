package ninja.trek.config;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import java.lang.reflect.Field;

public class SettingWidget {
    public static ButtonWidget createEnumButton(int x, int y, int width, int height,
                                                String fieldName, AbstractMovementSettings settings, MovementSetting annotation) {
        try {
            Field field = null;
            // Try to find field in concrete class first
            try {
                field = settings.getClass().getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // If not found, try superclass
                field = settings.getClass().getSuperclass().getDeclaredField(fieldName);
            }

            if (!field.getType().isEnum()) {
                throw new IllegalStateException("Field is not an enum type: " + fieldName);
            }

            // Make the field accessible for future use
            field.setAccessible(true);

            // Get initial value for button text
            Object initialValue = field.get(settings);
            if (!(initialValue instanceof Enum<?>)) {
                throw new IllegalStateException("Field value is not an enum: " + fieldName);
            }

            // Store enum class and values for button callback
            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) field.getType().asSubclass(Enum.class);
            Enum<?>[] enumConstants = enumClass.getEnumConstants();

            // Create final reference to field for use in lambda
            final Field finalField = field;

            return ButtonWidget.builder(
                    Text.literal(formatButtonText(annotation.label(), initialValue.toString())),
                    button -> {
                        try {
                            // Get current value fresh each click
                            Enum<?> currentValue = (Enum<?>) finalField.get(settings);
                            int currentIndex = currentValue.ordinal();
                            int nextIndex = (currentIndex + 1) % enumConstants.length;

                            // Set the new value
                            Enum<?> nextValue = enumConstants[nextIndex];
                            finalField.set(settings, nextValue);
                            settings.updateSetting(fieldName, nextValue.name());

                            // Update button text
                            button.setMessage(Text.literal(formatButtonText(annotation.label(), nextValue.toString())));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
            ).dimensions(x, y, width, height).build();
        } catch (Exception e) {
            e.printStackTrace();
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