package ninja.trek.config;

import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.movements.LinearMovement;

import java.util.ArrayList;
import java.util.List;

public class FollowerConfig {

    public static class FollowerEntry {
        private boolean useZones;
        private ICameraMovement movement;
        private ICameraMovement speakingMovement;
        private boolean directorEnabled = true;
        private boolean speechCameraEnabled = true;
        private boolean timelapseEnabled = true;
        private float timelapseIntervalSeconds = 60f;
        private int timelapseDistanceChunks = 5;
        private int timelapseIndex = 0;
        private int speechOnsetMs = 100;
        private int speechReleaseMs = 500;
        private float trackingSmoothingSeconds = 10f;
        private float trackingDistance = 20f;
        private float rigElevationDegrees = 30f;

        public FollowerEntry() {
            this.useZones = true;
            this.movement = createNormalMovement();
            this.speakingMovement = createSpeakingMovement();
        }

        public FollowerEntry(ICameraMovement movement, boolean useZones) {
            this.movement = movement;
            this.useZones = useZones;
            this.speakingMovement = createSpeakingMovement();
        }

        public boolean isUseZones() { return useZones; }
        public void setUseZones(boolean useZones) { this.useZones = useZones; }

        public ICameraMovement getMovement() { return movement; }
        public void setMovement(ICameraMovement movement) { this.movement = movement; }
        public ICameraMovement getSpeakingMovement() { return speakingMovement; }
        public void setSpeakingMovement(ICameraMovement movement) { this.speakingMovement = movement; }
        public boolean isDirectorEnabled() { return directorEnabled; }
        public void setDirectorEnabled(boolean value) { directorEnabled = value; }
        public boolean isSpeechCameraEnabled() { return speechCameraEnabled; }
        public void setSpeechCameraEnabled(boolean value) { speechCameraEnabled = value; }
        public boolean isTimelapseEnabled() { return timelapseEnabled; }
        public void setTimelapseEnabled(boolean value) { timelapseEnabled = value; }
        public float getTimelapseIntervalSeconds() { return timelapseIntervalSeconds; }
        public void setTimelapseIntervalSeconds(float value) { timelapseIntervalSeconds = Math.max(1f, value); }
        public int getTimelapseDistanceChunks() { return timelapseDistanceChunks; }
        public void setTimelapseDistanceChunks(int value) { timelapseDistanceChunks = Math.max(1, value); }
        public int getTimelapseIndex() { return timelapseIndex; }
        public void setTimelapseIndex(int value) { timelapseIndex = Math.max(0, value); }
        public int getSpeechOnsetMs() { return speechOnsetMs; }
        public void setSpeechOnsetMs(int value) { speechOnsetMs = Math.max(0, value); }
        public int getSpeechReleaseMs() { return speechReleaseMs; }
        public void setSpeechReleaseMs(int value) { speechReleaseMs = Math.max(0, value); }
        public float getTrackingSmoothingSeconds() { return trackingSmoothingSeconds; }
        public void setTrackingSmoothingSeconds(float value) { trackingSmoothingSeconds = Math.max(0.1f, value); }
        public float getTrackingDistance() { return trackingDistance; }
        public void setTrackingDistance(float value) { trackingDistance = Math.max(1f, value); }
        public float getRigElevationDegrees() { return rigElevationDegrees; }
        public void setRigElevationDegrees(float value) { rigElevationDegrees = Math.max(5f, Math.min(80f, value)); }

        private static ICameraMovement createNormalMovement() {
            return new LinearMovement();
        }

        private static ICameraMovement createSpeakingMovement() {
            LinearMovement movement = new LinearMovement();
            movement.updateSetting("endTarget", "HEAD_FRONT");
            movement.updateSetting("targetDistance", 4.0);
            movement.updateSetting("fovMultiplier", 0.8f);
            return movement;
        }
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
