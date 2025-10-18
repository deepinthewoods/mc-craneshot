package ninja.trek;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Craneshot implements ModInitializer {
    public static final String MOD_ID = "craneshot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Initialization logic
        LOGGER.debug("{} initialized", MOD_ID);
    }
}
