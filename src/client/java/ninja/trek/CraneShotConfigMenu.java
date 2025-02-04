package ninja.trek;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.config.ConfigField;
import ninja.trek.config.MovementConfigManager;

import java.lang.reflect.Field;
import java.util.Arrays;

public class CraneShotConfigMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("config.craneshot.title"));

            // Add global settings tab
            ConfigCategory globalCategory = builder.getOrCreateCategory(
                    Text.translatable("config.craneshot.category.global"));

            addGlobalSettings(globalCategory, builder.entryBuilder());

            // Add tabs for each camera slot
            CameraController controller = CraneshotClient.CAMERA_CONTROLLER;
            for (int i = 0; i < controller.getMovementCount(); i++) {
                ICameraMovement movement = controller.getMovementAt(i);
                if (movement != null) {
                    ConfigCategory slotCategory = builder.getOrCreateCategory(
                            Text.translatable("config.craneshot.category.slot", i + 1));

                    addMovementSettings(slotCategory, builder.entryBuilder(), movement, i);
                }
            }

            return builder.build();
        };
    }

    private void addGlobalSettings(ConfigCategory category, ConfigEntryBuilder entryBuilder) {
        // Add any global settings from CraneShotConfig
        CraneShotConfig config = CraneShotConfig.get();

        category.addEntry(entryBuilder.startBooleanToggle(
                        Text.translatable("config.craneshot.option.example_toggle"),
                        config.exampleToggle)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> {
                    config.exampleToggle = newValue;
                    CraneShotConfig.save();
                })
                .build());
    }

    private void addMovementSettings(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                                     ICameraMovement movement, int slot) {
        // Use reflection to find all fields with @ConfigField annotation
        for (Field field : movement.getClass().getDeclaredFields()) {
            ConfigField annotation = field.getAnnotation(ConfigField.class);
            if (annotation == null) continue;

            field.setAccessible(true);
            String translationKey = "config.craneshot.movement." + field.getName().toLowerCase();

            try {
                if (field.getType() == double.class || field.getType() == Double.class) {
                    addDoubleField(category, entryBuilder, movement, field, annotation, translationKey);
                } else if (field.getType() == float.class || field.getType() == Float.class) {
                    addFloatField(category, entryBuilder, movement, field, annotation, translationKey);
                } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    addBooleanField(category, entryBuilder, movement, field, annotation, translationKey);
                }
                // Add other types as needed
            } catch (Exception e) {
                Craneshot.LOGGER.error("Failed to add config field: " + field.getName(), e);
            }
        }
    }

    private void addDoubleField(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                                ICameraMovement movement, Field field, ConfigField annotation,
                                String translationKey) throws IllegalAccessException {
        double currentValue = field.getDouble(movement);

        var entry = annotation.sliderControl()
                ? entryBuilder.startDoubleSlider(
                Text.translatable(translationKey),
                currentValue,
                annotation.min(),
                annotation.max())
                : entryBuilder.startDoubleField(
                Text.translatable(translationKey),
                currentValue);

        if (!annotation.description().isEmpty()) {
            entry.setTooltip(Text.translatable(translationKey + ".tooltip"));
        }

        entry.setDefaultValue(currentValue)
                .setSaveConsumer(newValue -> {
                    try {
                        field.setDouble(movement, newValue);
                        MovementConfigManager.getInstance().saveMovementConfig(movement, slot);
                    } catch (IllegalAccessException e) {
                        Craneshot.LOGGER.error("Failed to save config value", e);
                    }
                });

        category.addEntry(entry.build());
    }

    private void addFloatField(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                               ICameraMovement movement, Field field, ConfigField annotation,
                               String translationKey) throws IllegalAccessException {
        float currentValue = field.getFloat(movement);

        var entry = annotation.sliderControl()
                ? entryBuilder.startFloatSlider(
                Text.translatable(translationKey),
                currentValue,
                (float)annotation.min(),
                (float)annotation.max())
                : entryBuilder.startFloatField(
                Text.translatable(translationKey),
                currentValue);

        if (!annotation.description().isEmpty()) {
            entry.setTooltip(Text.translatable(translationKey + ".tooltip"));
        }

        entry.setDefaultValue(currentValue)
                .setSaveConsumer(newValue -> {
                    try {
                        field.setFloat(movement, newValue);
                        MovementConfigManager.getInstance().saveMovementConfig(movement, slot);
                    } catch (IllegalAccessException e) {
                        Craneshot.LOGGER.error("Failed to save config value", e);
                    }
                });

        category.addEntry(entry.build());
    }

    private void addBooleanField(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                                 ICameraMovement movement, Field field, ConfigField annotation,
                                 String translationKey) throws IllegalAccessException {
        boolean currentValue = field.getBoolean(movement);

        var entry = entryBuilder.startBooleanToggle(
                Text.translatable(translationKey),
                currentValue);

        if (!annotation.description().isEmpty()) {
            entry.setTooltip(Text.translatable(translationKey + ".tooltip"));
        }

        entry.setDefaultValue(currentValue)
                .setSaveConsumer(newValue -> {
                    try {
                        field.setBoolean(movement, newValue);
                        MovementConfigManager.getInstance().saveMovementConfig(movement, slot);
                    } catch (IllegalAccessException e) {
                        Craneshot.LOGGER.error("Failed to save config value", e);
                    }
                });

        category.addEntry(entry.build());
    }
}