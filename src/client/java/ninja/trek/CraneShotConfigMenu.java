package ninja.trek;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;


public class CraneShotConfigMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("config.craneshot.title"));


            ConfigCategory general = builder.getOrCreateCategory(
                    Text.translatable("config.craneshot.category.general"));

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            general.addEntry(entryBuilder.startBooleanToggle(
                            Text.translatable("config.craneshot.option.example_toggle"),
                            ninja.trek.CraneShotConfig.get().exampleToggle)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> {
                        ninja.trek.CraneShotConfig.get().exampleToggle = newValue;
                        ninja.trek.CraneShotConfig.save();
                    })
                    .build());

            return builder.build();
        };
    }
}