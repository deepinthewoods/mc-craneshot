// TransitionMode.java
package ninja.trek.config;

import net.minecraft.client.MinecraftClient;

import java.io.FileReader;
import java.io.FileWriter;

public enum TransitionMode {
    IMMEDIATE("Immediate Switch"),
    INTERPOLATE("Interpolate"),
    QUEUE("Queue");

    private final String displayName;

    TransitionMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

