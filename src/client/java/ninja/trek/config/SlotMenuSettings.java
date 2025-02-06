package ninja.trek.config;

import java.util.HashMap;
import java.util.Map;

public class SlotMenuSettings {
    private static final Map<Integer, Boolean> wrapStates = new HashMap<>();
    private static final Map<Integer, Boolean> toggleStates = new HashMap<>();

    public static boolean getWrapState(int slotIndex) {
        return wrapStates.getOrDefault(slotIndex, false);
    }

    public static void setWrapState(int slotIndex, boolean state) {
        wrapStates.put(slotIndex, state);
    }

    public static boolean getToggleState(int slotIndex) {
        return toggleStates.getOrDefault(slotIndex, false);
    }

    public static void setToggleState(int slotIndex, boolean state) {
        toggleStates.put(slotIndex, state);
    }
}