package ninja.trek.config;

import net.fabricmc.loader.api.FabricLoader;
import ninja.trek.Craneshot;

public class FollowerMode {
    private static boolean follower = false;
    private static int followerIndex = -1;
    private static FollowerConfig cachedConfig = null;
    private static boolean configLoaded = false;
    private static boolean followerMovementStarted = false;
    private static volatile boolean configChanged = false;
    private static long lastConfigModified = 0;
    private static long lastConfigCheckTime = 0;
    private static final long CONFIG_CHECK_INTERVAL_MS = 2000; // check every 2 seconds

    public static void init() {
        // Check JVM system property first: -Dcraneshot.follower=N
        String sysProp = System.getProperty("craneshot.follower");
        if (sysProp != null) {
            try {
                followerIndex = Integer.parseInt(sysProp.trim());
                follower = true;
                Craneshot.LOGGER.info("Craneshot follower mode enabled via system property, index={}", followerIndex);
                return;
            } catch (NumberFormatException e) {
                Craneshot.LOGGER.warn("Invalid craneshot.follower system property value: {}", sysProp);
            }
        }

        // Check Fabric launch arguments
        // Fabric exposes game arguments, but JVM args like --craneshot-follower=N
        // are best passed as system properties. We also check program args as fallback.
        for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Dcraneshot.follower=")) {
                // Already handled above via System.getProperty
                break;
            }
        }

        // Check game/program arguments for --craneshot-follower=N format
        String[] programArgs = getProgramArguments();
        if (programArgs != null) {
            for (String arg : programArgs) {
                if (arg.startsWith("--craneshot-follower=")) {
                    try {
                        String value = arg.substring("--craneshot-follower=".length()).trim();
                        followerIndex = Integer.parseInt(value);
                        follower = true;
                        Craneshot.LOGGER.info("Craneshot follower mode enabled via program arg, index={}", followerIndex);
                        return;
                    } catch (NumberFormatException e) {
                        Craneshot.LOGGER.warn("Invalid --craneshot-follower argument value: {}", arg);
                    }
                }
            }
        }
    }

    private static String[] getProgramArguments() {
        try {
            // Access the command line to find program arguments
            // sun.java.command contains: main_class arg1 arg2 ...
            String command = System.getProperty("sun.java.command");
            if (command != null) {
                return command.split("\\s+");
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isFollower() {
        return follower;
    }

    public static int getFollowerIndex() {
        return followerIndex;
    }

    public static FollowerConfig getConfig() {
        if (!configLoaded) {
            cachedConfig = FollowerSettingsIO.loadFollowers();
            lastConfigModified = FollowerSettingsIO.getConfigLastModified();
            configLoaded = true;
        }
        return cachedConfig;
    }

    public static void reloadConfig() {
        cachedConfig = FollowerSettingsIO.loadFollowers();
        lastConfigModified = FollowerSettingsIO.getConfigLastModified();
        configLoaded = true;
    }

    /**
     * Called from the network handler when a follower config packet arrives.
     * Stores the config and sets the configChanged flag for the next tick check.
     */
    public static void applyNetworkConfig(FollowerConfig config) {
        cachedConfig = config;
        configLoaded = true;
        configChanged = true;
        Craneshot.LOGGER.info("Follower config received via network");
    }

    /**
     * Checks if the config has changed (via network or file).
     * Network config changes are detected immediately via the configChanged flag.
     * File-based polling is kept as a fallback.
     * @return true if config was reloaded due to change
     */
    public static boolean checkForConfigChange() {
        // Check network-delivered config first (immediate)
        if (configChanged) {
            configChanged = false;
            return true;
        }

        // Fallback: file-based polling
        long now = System.currentTimeMillis();
        if (now - lastConfigCheckTime < CONFIG_CHECK_INTERVAL_MS) {
            return false;
        }
        lastConfigCheckTime = now;

        long currentModified = FollowerSettingsIO.getConfigLastModified();
        if (currentModified != lastConfigModified && currentModified != 0) {
            Craneshot.LOGGER.info("Follower config file changed, reloading");
            reloadConfig();
            return true;
        }
        return false;
    }

    public static boolean isFollowerMovementStarted() {
        return followerMovementStarted;
    }

    public static void setFollowerMovementStarted(boolean started) {
        followerMovementStarted = started;
    }
}
