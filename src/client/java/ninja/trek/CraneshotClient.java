package ninja.trek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.config.GeneralSettingsIO;
import ninja.trek.config.MenuOverlayScreen;
import ninja.trek.config.SlotSettingsIO;
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

	@Override
	public void onInitializeClient() {

		toggleMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.craneshot.toggle_menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_Z,
				"category.craneshot.ui"
		));

		selectMovementType = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.craneshot.select_movement",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				"category.craneshot.camera"
		));

        cameraKeyBinds = new KeyBinding[]{
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera1",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_C,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera2",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_V,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera3",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_B,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera4",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera5",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera6",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera7",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera8",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera9",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        "category.craneshot.camera"
                )),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.craneshot.camera10",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        "category.craneshot.camera"
                ))
        };
		CameraMovementRegistry.initialize();
		GeneralSettingsIO.loadSettings();

		List<List<ICameraMovement>> savedSlots = SlotSettingsIO.loadSlots();
		MOVEMENT_MANAGER.setAllSlots(savedSlots);
		CraneShotEventHandler.register();
	}
	public static void checkKeybinds() {
		if (toggleMenuKey.wasPressed()) {
			MENU.toggleMenu();
		}
	}

}
