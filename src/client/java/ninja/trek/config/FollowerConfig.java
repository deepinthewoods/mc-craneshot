package ninja.trek.config;

import ninja.trek.cameramovements.ICameraMovement;

import java.util.ArrayList;
import java.util.List;

public class FollowerConfig {

    public enum FollowerMode {
        MOVEMENT,
        ZONES
    }

    public static class FollowerEntry {
        private FollowerMode mode;
        private ICameraMovement movement; // nullable, only used when mode=MOVEMENT

        public FollowerEntry() {
            this.mode = FollowerMode.MOVEMENT;
            this.movement = null;
        }

        public FollowerEntry(FollowerMode mode, ICameraMovement movement) {
            this.mode = mode;
            this.movement = movement;
        }

        public FollowerMode getMode() { return mode; }
        public void setMode(FollowerMode mode) { this.mode = mode; }

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
