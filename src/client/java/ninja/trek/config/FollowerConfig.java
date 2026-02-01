package ninja.trek.config;

import ninja.trek.cameramovements.ICameraMovement;

import java.util.ArrayList;
import java.util.List;

public class FollowerConfig {

    public static class FollowerEntry {
        private boolean useZones;
        private ICameraMovement movement;

        public FollowerEntry() {
            this.useZones = true;
            this.movement = null;
        }

        public FollowerEntry(ICameraMovement movement, boolean useZones) {
            this.movement = movement;
            this.useZones = useZones;
        }

        public boolean isUseZones() { return useZones; }
        public void setUseZones(boolean useZones) { this.useZones = useZones; }

        public ICameraMovement getMovement() { return movement; }
        public void setMovement(ICameraMovement movement) { this.movement = movement; }
    }

    private String targetPlayerName = "";
    private final List<FollowerEntry> followers = new ArrayList<>();

    public String getTargetPlayerName() { return targetPlayerName; }
    public void setTargetPlayerName(String name) {
        this.targetPlayerName = (name == null) ? "" : name.trim();
    }

    public List<FollowerEntry> getFollowers() { return followers; }

    public FollowerEntry getFollower(int index) {
        if (index >= 0 && index < followers.size()) {
            return followers.get(index);
        }
        return null;
    }

    public void addFollower(FollowerEntry entry) {
        followers.add(entry);
    }

    public void removeFollower(int index) {
        if (index >= 0 && index < followers.size()) {
            followers.remove(index);
        }
    }
}
