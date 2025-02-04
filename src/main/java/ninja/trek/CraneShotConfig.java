package ninja.trek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public class CraneShotConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("craneshot.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CraneShotConfig INSTANCE;

    public boolean exampleToggle = true;

    public static CraneShotConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(Files.readString(CONFIG_PATH), CraneShotConfig.class);
            } else {
                INSTANCE = new CraneShotConfig();
                save();
            }
        } catch (Exception e) {
            INSTANCE = new CraneShotConfig();
        }
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            // Log error
        }
    }
}