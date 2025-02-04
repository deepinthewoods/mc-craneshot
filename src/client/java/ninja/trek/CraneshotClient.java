package ninja.trek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;


public class CraneshotClient implements ClientModInitializer {
	public static KeyBinding[] cameraKeyBinds;
	public static KeyBinding selectMovementType;
	public static final CameraController CAMERA_CONTROLLER = new CameraController();

	@Override
	public void onInitializeClient() {
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
				))
		};

		CraneShotEventHandler.register();
	}
}