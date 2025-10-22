package ninja.trek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import ninja.trek.cameramovements.ICameraMovement;
import net.minecraft.util.Identifier;
import ninja.trek.config.GeneralSettingsIO;
import ninja.trek.config.MenuOverlayScreen;
import ninja.trek.config.SlotSettingsIO;
import ninja.trek.render.CrosshairHudRenderer;
import ninja.trek.render.NodeAreaHudRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.List;


public class CraneshotClient implements ClientModInitializer {
	public static KeyBinding[] cameraKeyBinds;
	public static KeyBinding selectMovementType;
	public static final CameraController CAMERA_CONTROLLER = new CameraController();
	public static KeyBinding toggleMenuKey;
	private static boolean isMenuOpen = false;
	public static MenuOverlayScreen MENU = new MenuOverlayScreen();
	public static final CameraMovementManager MOVEMENT_MANAGER = new CameraMovementManager();

    private static final KeyBinding.Category KB_CAT_UI = KeyBinding.Category.create(Identifier.of("craneshot", "ui"));
    private static final KeyBinding.Category KB_CAT_CAMERA = KeyBinding.Category.create(Identifier.of("craneshot", "camera"));

    @Override
    public void onInitializeClient() {

        toggleMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.craneshot.toggle_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KB_CAT_UI
        ));

        selectMovementType = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.craneshot.select_movement",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KB_CAT_CAMERA
        ));

        cameraKeyBinds = new KeyBinding[]{
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera1",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_C,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera2",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_V,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera3",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_B,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera4",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera5",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera6",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera7",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera8",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera9",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        KB_CAT_CAMERA
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera10",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        KB_CAT_CAMERA
                ))
        };
        CameraMovementRegistry.initialize();
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

        // Register client networking
        ninja.trek.nodes.network.ClientNodeNetworking.register();
	}
	public static void checkKeybinds() {
		if (toggleMenuKey.wasPressed()) {
			MENU.toggleMenu();
		}
	}

}
