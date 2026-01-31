package ninja.trek.config;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import ninja.trek.cameramovements.AbstractMovementSettings;

class SettingSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final String fieldName;
    private final AbstractMovementSettings settings;
    private final Component label;

    public SettingSlider(int x, int y, int width, int height, Component label,
                         double min, double max, double value, String fieldName, AbstractMovementSettings settings) {
        super(x, y, width, height, label, (value - min) / (max - min));
        this.min = min;
        this.max = max;
        this.fieldName = fieldName;
        this.settings = settings;
        this.label = label;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(String.format("%.2f", getValue())));
    }

    @Override
    protected void applyValue() {
        double value = min + (max - min) * this.value;
        settings.updateSetting(fieldName, value);
    }

    public Component getLabel() {
        return label;
    }

    private double getValue() {
        return min + (max - min) * this.value;
    }
}