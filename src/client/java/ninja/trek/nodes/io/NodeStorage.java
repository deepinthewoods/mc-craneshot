package ninja.trek.nodes.io;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class NodeStorage {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3d.class, new Vec3dAdapter())
            .setPrettyPrinting()
            .create();
    private static File getFile() {
        File cfgDir = new File(MinecraftClient.getInstance().runDirectory, "config");
        if (!cfgDir.exists()) cfgDir.mkdirs();
        return new File(cfgDir, "craneshot_nodes.json");
    }

    public static List<CameraNode> load() {
        File f = getFile();
        if (!f.exists()) return new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<CameraNode>>(){}.getType();
            List<CameraNode> list = GSON.fromJson(r, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to load nodes: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void save(List<CameraNode> nodes) {
        File f = getFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(nodes, w);
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to save nodes: {}", e.getMessage());
        }
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

