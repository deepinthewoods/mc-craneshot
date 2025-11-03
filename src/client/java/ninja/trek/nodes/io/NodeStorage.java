package ninja.trek.nodes.io;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import ninja.trek.nodes.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NodeStorage {
    public static class Payload {
        public final List<CameraNode> nodes = new ArrayList<>();
        public final List<AreaInstance> areas = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3d.class, new Vec3dAdapter())
            .setPrettyPrinting()
            .create();
    private static File getFile() {
        // Prefer per-world save under saves/<world>/craneshot_nodes.json when possible (singleplayer)
        try {
            var mc = MinecraftClient.getInstance();
            if (mc != null && mc.getServer() != null) {
                java.nio.file.Path root = mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT);
                if (root != null) {
                    File worldFile = root.toFile();
                    return new File(worldFile, "craneshot_nodes.json");
                }
            }
        } catch (Throwable ignored) {}
        // Fallback to global config
        File cfgDir = new File(MinecraftClient.getInstance().runDirectory, "config");
        if (!cfgDir.exists()) cfgDir.mkdirs();
        return new File(cfgDir, "craneshot_nodes.json");
    }

    public static Payload load() {
        Payload payload = new Payload();
        File f = getFile();
        if (!f.exists()) return payload;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (root == null) return payload;
            if (root.isJsonArray()) {
                // Legacy format: array of nodes only
                List<CameraNode> list = GSON.fromJson(root, new TypeToken<List<CameraNode>>(){}.getType());
                if (list != null) {
                    payload.nodes.addAll(list);
                }
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("nodes")) {
                    List<CameraNode> list = GSON.fromJson(obj.get("nodes"), new TypeToken<List<CameraNode>>(){}.getType());
                    if (list != null) payload.nodes.addAll(list);
                }
                if (obj.has("areas")) {
                    List<AreaInstance> list = GSON.fromJson(obj.get("areas"), new TypeToken<List<AreaInstance>>(){}.getType());
                    if (list != null) payload.areas.addAll(list);
                }
            }
        } catch (Exception e) {
            // logging removed
        }
        return payload;
    }

    public static void save(List<CameraNode> nodes, List<AreaInstance> areas) {
        File f = getFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("nodes", GSON.toJsonTree(nodes, new TypeToken<List<CameraNode>>(){}.getType()));
            root.add("areas", GSON.toJsonTree(areas, new TypeToken<List<AreaInstance>>(){}.getType()));
            GSON.toJson(root, w);
        } catch (Exception e) {
            // logging removed
        }
    }

    public static File getExportFile() {
        File cfgDir = new File(MinecraftClient.getInstance().runDirectory, "config");
        if (!cfgDir.exists()) cfgDir.mkdirs();
        return new File(cfgDir, "craneshot_nodes_export.json");
    }

    public static boolean exportData(List<CameraNode> nodes, List<AreaInstance> areas) {
        File f = getExportFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("nodes", GSON.toJsonTree(nodes, new TypeToken<List<CameraNode>>(){}.getType()));
            root.add("areas", GSON.toJsonTree(areas, new TypeToken<List<AreaInstance>>(){}.getType()));
            GSON.toJson(root, w);
            return true;
        } catch (Exception e) { return false; }
    }

    public static Payload importData() {
        Payload payload = new Payload();
        File f = getExportFile();
        if (!f.exists()) return payload;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (root == null) return payload;
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("nodes")) {
                    List<CameraNode> list = GSON.fromJson(obj.get("nodes"), new TypeToken<List<CameraNode>>(){}.getType());
                    if (list != null) payload.nodes.addAll(list);
                }
                if (obj.has("areas")) {
                    List<AreaInstance> list = GSON.fromJson(obj.get("areas"), new TypeToken<List<AreaInstance>>(){}.getType());
                    if (list != null) payload.areas.addAll(list);
                }
            } else if (root.isJsonArray()) {
                List<CameraNode> list = GSON.fromJson(root, new TypeToken<List<CameraNode>>(){}.getType());
                if (list != null) payload.nodes.addAll(list);
            }
        } catch (Exception e) {
            return payload;
        }
        return payload;
    }

    static class Vec3dAdapter implements JsonSerializer<Vec3d>, JsonDeserializer<Vec3d> {
        @Override public JsonElement serialize(Vec3d src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray a = new JsonArray();
            a.add(src.x); a.add(src.y); a.add(src.z);
            return a;
        }
        @Override public Vec3d deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonArray a = json.getAsJsonArray();
            return new Vec3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        }
    }
}
