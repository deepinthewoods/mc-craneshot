package ninja.trek.nodes.model;

import java.util.*;

/**
 * Lightweight persisted configuration for an area-managed camera movement.
 * Stores only declarative data so it can be deserialized on both client and server.
 */
public class AreaMovementConfig {
    /** Stable identifier so runtime state can be tracked across reloads. */
    public UUID id = UUID.randomUUID();
    /**
     * The fully qualified class name (or other agreed identifier) for the camera movement
     * implementation that should be instantiated on the client.
     */
    public String movementType = "";
    /** Optional human friendly label exposed in UI. */
    public String name = null;
    /** Whether this config should currently be considered when evaluating movements. */
    public boolean enabled = true;
    /** Weight applied on top of the area's influence (0..1 recommended). */
    public float weight = 1.0f;
    /**
     * Optional state filters. The interpretation is determined by the client runtime
     * (e.g. movement state keys, player condition tags, etc).
     */
    public final List<String> stateFilters = new ArrayList<>();
    /**
     * Arbitrary serialized settings for the movement. Values should be primitives, strings,
     * UUIDs, or nested maps/lists that Gson can handle.
     */
    public final Map<String, Object> settings = new HashMap<>();

    public AreaMovementConfig() {}

    public AreaMovementConfig copy() {
        AreaMovementConfig cfg = new AreaMovementConfig();
        cfg.id = this.id;
        cfg.movementType = this.movementType;
        cfg.name = this.name;
        cfg.enabled = this.enabled;
        cfg.weight = this.weight;
        cfg.stateFilters.clear();
        cfg.stateFilters.addAll(this.stateFilters);
        cfg.settings.clear();
        cfg.settings.putAll(this.settings);
        return cfg;
    }
}
