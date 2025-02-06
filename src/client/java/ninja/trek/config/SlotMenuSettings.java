package ninja.trek.config;

import java.util.HashMap;
import java.util.Map;

public class SlotMenuSettings {
    private static final Map<Integer, Boolean> wrapStates = new HashMap<>();

    public static boolean getWrapState(int slotIndex) {
        return wrapStates.getOrDefault(slotIndex, false);
    }

    public static void setWrapState(int slotIndex, boolean state) {
        wrapStates.put(slotIndex, state);
    }
}