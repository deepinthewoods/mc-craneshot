package ninja.trek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.config.FollowerMode;
import ninja.trek.config.GeneralSettingsIO;
import ninja.trek.config.MenuOverlayScreen;
import ninja.trek.config.SlotSettingsIO;
import ninja.trek.render.CrosshairHudRenderer;
import ninja.trek.render.NodeAreaHudRenderer;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;


public class CraneshotClient implements ClientModInitializer {
	public static KeyMapping[] cameraKeyBinds;
	public static KeyMapping selectMovementType;
	public static final CameraController CAMERA_CONTROLLER = new CameraController();
	public static KeyMapping toggleMenuKey;
	public static KeyMapping followMovementKey;
	public static KeyMapping zoomKey;
	public static KeyMapping toggleZonesKey;
	private static boolean isMenuOpen = false;
	public static MenuOverlayScreen MENU = new MenuOverlayScreen();
	public static final CameraMovementManager MOVEMENT_MANAGER = new CameraMovementManager();

    private static final KeyMapping.Category KB_CAT_UI = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("craneshot", "ui"));
    private static final KeyMapping.Category KB_CAT_CAMERA = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("craneshot", "camera"));

    @Override
    public void onInitializeClient() {

        toggleMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craneshot.toggle_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KB_CAT_UI
        ));

        selectMovementType = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craneshot.select_movement",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KB_CAT_CAMERA
        ));

        followMovementKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craneshot.follow_movement",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KB_CAT_CAMERA
        ));

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craneshot.zoom",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KB_CAT_CAMERA
        ));

        toggleZonesKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craneshot.toggle_zones",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KB_CAT_CAMERA
        ));

        cameraKeyBinds = new KeyMapping[CameraMovementManager.SLOT_COUNT];
        int[] defaultKeyCodes = new int[]{
                GLFW.GLFW_KEY_C,
                GLFW.GLFW_KEY_V,
                GLFW.GLFW_KEY_B,
                GLFW.GLFW_KEY_UNKNOWN,
                GLFW.GLFW_KEY_UNKNOWN,
                GLFW.GLFW_KEY_UNKNOWN
        };

        for (int i = 0; i < cameraKeyBinds.length; i++) {
            int keyCode = i < defaultKeyCodes.length ? defaultKeyCodes[i] : GLFW.GLFW_KEY_UNKNOWN;
            cameraKeyBinds[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.craneshot.camera" + (i + 1),
                    InputConstants.Type.KEYSYM,
                    keyCode,
                    KB_CAT_CAMERA
            ));
        }
        CameraMovementRegistry.initialize();
        // Detect follower mode from launch args (--craneshot-follower=N or -Dcraneshot.follower=N)
        FollowerMode.init();

        // Load camera nodes from client config
        ninja.trek.nodes.NodeManager.get().load();
        GeneralSettingsIO.loadSettings();

		List<List<ICameraMovement>> savedSlots = SlotSettingsIO.loadSlots();
		MOVEMENT_MANAGER.setAllSlots(savedSlots);
		CraneShotEventHandler.register();
        // Draw second crosshair at camera look point
        CrosshairHudRenderer.register();
        // Draw active node area influences
        NodeAreaHudRenderer.register();

        // When in follower mode, auto-switch to spectator on server join
        if (FollowerMode.isFollower()) {
            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                // Send the command on the next tick so the connection is fully ready
                client.execute(() -> {
                    if (client.player != null && client.player.connection != null) {
                        client.player.connection.sendCommand("gamemode spectator");
                        Craneshot.LOGGER.info("Follower mode: requested spectator gamemode");
                    }
                });
            });
        }

        // Register client networking
        ninja.trek.nodes.network.ClientNodeNetworking.register();
	}
	public static void checkKeybinds() {
		if (toggleMenuKey.consumeClick()) {
			MENU.toggleMenu();
		}
		if (toggleZonesKey != null && toggleZonesKey.consumeClick()) {
			boolean newState = !ninja.trek.config.GeneralMenuSettings.isZonesEnabled();
			ninja.trek.config.GeneralMenuSettings.setZonesEnabled(newState);
			ninja.trek.config.GeneralSettingsIO.saveSettings();
			MovementToastRenderer.showTextToast(newState ? "Zones: ON" : "Zones: OFF");
		}
	}

}
