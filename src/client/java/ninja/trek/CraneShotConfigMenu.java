package ninja.trek;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;
import ninja.trek.cameramovements.*;
import ninja.trek.config.ConfigField;
import ninja.trek.config.MovementConfigManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

public class CraneShotConfigMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("Camera Settings"));

            // Add global settings tab
            ConfigCategory globalCategory = builder.getOrCreateCategory(
                    Text.literal("Global Settings"));
            addGlobalSettings(globalCategory, builder.entryBuilder());

            // Add tabs for each camera slot
            CameraController controller = CraneshotClient.CAMERA_CONTROLLER;
            for (int i = 0; i < controller.getMovementCount(); i++) {
                final int slotIndex = i;
                ConfigCategory slotCategory = builder.getOrCreateCategory(
                        Text.literal("Camera " + (i + 1)));

                // Add movement management section
                addMovementManagement(slotCategory, builder.entryBuilder(), controller, slotIndex);

                // Add settings for current movement
                ICameraMovement movement = controller.getMovementAt(i);
                if (movement != null) {
                    addMovementSettings(slotCategory, builder.entryBuilder(), movement, slotIndex);
                }
            }

            return builder.build();
        };
    }

    private void addMovementManagement(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                                       CameraController controller, int slotIndex) {
        // Add dropdown to show current movements
        List<ICameraMovement> movements = controller.getAvailableMovementsForSlot(slotIndex);
        if (!movements.isEmpty()) {
            category.addEntry(entryBuilder.startDropdownMenu(
                            Text.literal("Current Movements"),
                            movements.get(controller.getCurrentTypeForSlot(slotIndex)).getName(),
                            movement -> Text.literal(movement))
                    .setDefaultValue(movements.get(0).getName())
                    .setSelections(movements.stream()
                            .map(ICameraMovement::getName)
                            .collect(java.util.stream.Collectors.toSet()))
                    .setSaveConsumer(newSelection -> {
                        // Find movement by name and set it as current
                        int index = movements.indexOf(movements.stream()
                                .filter(m -> m.getName().equals(newSelection))
                                .findFirst()
                                .orElse(movements.get(0)));
                        controller.cycleMovementType(index > controller.getCurrentTypeForSlot(slotIndex));
                    })
                    .build());
        }

        // Add button to add new movement types
        category.addEntry(entryBuilder.startSelector(
                        Text.literal("Add Movement Type"),
                        new String[]{"Linear", "Circular", "Bezier"},
                        "Linear")
                .setDefaultValue("Linear")
                .setSaveConsumer(type -> {
                    ICameraMovement newMovement = switch (type) {
                        case "Linear" -> new LinearMovement();
                        // Add cases for other movement types as they're implemented
                        default -> new LinearMovement();
                    };
                    controller.addMovementToSlot(slotIndex, newMovement);
                })
                .build());

        // Add button to remove current movement (if more than one exists)
        if (movements.size() > 1) {
            category.addEntry(entryBuilder.startBooleanToggle(
                            Text.literal("Remove Current Movement"),
                            false)
                    .setDefaultValue(false)
                    .setSaveConsumer(remove -> {
                        if (remove) {
                            controller.removeMovementFromSlot(slotIndex,
                                    controller.getCurrentTypeForSlot(slotIndex));
                        }
                    })
                    .build());
        }
    }

    private void addGlobalSettings(ConfigCategory category, ConfigEntryBuilder entryBuilder) {
        CraneShotConfig config = CraneShotConfig.get();
        category.addEntry(entryBuilder.startBooleanToggle(
                        Text.literal("exampleToggle"),
                        config.exampleToggle)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> {
                    config.exampleToggle = newValue;
                    CraneShotConfig.save();
                })
                .build());
    }

    private void addMovementSettings(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                                     ICameraMovement movement, int slotIndex) {
        category.addEntry(entryBuilder.startTextDescription(
                        Text.literal("Settings for " + movement.getName() + " Movement"))
                .build());

        for (Field field : movement.getClass().getDeclaredFields()) {
            ConfigField annotation = field.getAnnotation(ConfigField.class);
            if (annotation == null) continue;
            field.setAccessible(true);
            try {
                if (field.getType() == double.class || field.getType() == Double.class) {
                    addDoubleField(category, entryBuilder, movement, field, annotation, slotIndex);
                } else if (field.getType() == float.class || field.getType() == Float.class) {
                    addFloatField(category, entryBuilder, movement, field, annotation, slotIndex);
                } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    addBooleanField(category, entryBuilder, movement, field, annotation, slotIndex);
                }
            } catch (Exception e) {
                Craneshot.LOGGER.error("Failed to add config field: " + field.getName(), e);
            }
        }
    }

    private void addDoubleField(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                                ICameraMovement movement, Field field, ConfigField annotation,
                                int slotIndex) throws IllegalAccessException {
        double currentValue = field.getDouble(movement);
        var entry = entryBuilder.startDoubleField(
                Text.literal(annotation.name().isEmpty() ? field.getName() : annotation.name()),
                currentValue);
        if (!annotation.description().isEmpty()) {
            entry.setTooltip(Text.literal(annotation.description()));
        }
        entry.setDefaultValue(currentValue)
                .setMin(annotation.min())
                .setMax(annotation.max())
                .setSaveConsumer(newValue -> {
                    try {
                        field.setDouble(movement, newValue);
                        MovementConfigManager.getInstance().saveMovementConfig(movement, slotIndex);
                    } catch (IllegalAccessException e) {
                        Craneshot.LOGGER.error("Failed to save config value", e);
                    }
                });
        category.addEntry(entry.build());
    }

    private void addFloatField(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                               ICameraMovement movement, Field field, ConfigField annotation,
                               int slotIndex) throws IllegalAccessException {
        float currentValue = field.getFloat(movement);
        var entry = entryBuilder.startFloatField(
                Text.literal(annotation.name().isEmpty() ? field.getName() : annotation.name()),
                currentValue);
        if (!annotation.description().isEmpty()) {
            entry.setTooltip(Text.literal(annotation.description()));
        }
        entry.setDefaultValue(currentValue)
                .setMin((float)annotation.min())
                .setMax((float)annotation.max())
                .setSaveConsumer(newValue -> {
                    try {
                        field.setFloat(movement, newValue);
                        MovementConfigManager.getInstance().saveMovementConfig(movement, slotIndex);
                    } catch (IllegalAccessException e) {
                        Craneshot.LOGGER.error("Failed to save config value", e);
                    }
                });
        category.addEntry(entry.build());
    }

    private void addBooleanField(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                                 ICameraMovement movement, Field field, ConfigField annotation,
                                 int slotIndex) throws IllegalAccessException {
        boolean currentValue = field.getBoolean(movement);
        var entry = entryBuilder.startBooleanToggle(
                Text.literal(annotation.name().isEmpty() ? field.getName() : annotation.name()),
                currentValue);
        if (!annotation.description().isEmpty()) {
            entry.setTooltip(Text.literal(annotation.description()));
        }
        entry.setDefaultValue(currentValue)
                .setSaveConsumer(newValue -> {
                    try {
                        field.setBoolean(movement, newValue);
                        MovementConfigManager.getInstance().saveMovementConfig(movement, slotIndex);
                    } catch (IllegalAccessException e) {
                        Craneshot.LOGGER.error("Failed to save config value", e);
                    }
                });
        category.addEntry(entry.build());
    }
}