package com.atom.life.data;

import com.atom.life.GlobalVariables;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import static com.atom.life.GlobalVariables.*;

/**
 * world.json authoritative config (seed/mode/version/renderDistance).
 * Rules:
 * - If world.json doesn't exist: create it using GlobalVariables.seed/worldMode/renderDistance/gameVersion.
 * - If it exists: GlobalVariables.seed/worldMode/renderDistance/gameVersion are ignored (locked by save).
 * - worldMode/renderDistance are locked by world.json (no switching inside same save).
 */
public final class WorldIO {

    public static final String FILE_NAME = "world.json";
    public static final int SCHEMA_VERSION = 2; // ✅ bump schema

    /** schema version for future migration */
    public int schema = SCHEMA_VERSION;

    /** world seed */
    public long seed;

    /** "normal" / "flat" / "single" */
    public String worldMode;

    /** render distance in chunks (locked by save) */
    public int renderDistance;

    /** world save version (usually equals gameVersion at creation time) */
    public String version;

    public WorldIO() {}

    public WorldIO(long seed, String worldMode, int renderDistance, String version) {
        this.schema = SCHEMA_VERSION;
        this.seed = seed;
        this.worldMode = normalizeMode(worldMode);
        this.renderDistance = normalizeRenderDistance(renderDistance);
        this.version = (version == null ? "" : version);
    }

    public static WorldIO loadOrCreate(FileHandle worldDir) {
        if (!worldDir.exists()) worldDir.mkdirs();
        FileHandle f = worldDir.child(FILE_NAME);

        Json json = new Json();
        json.setIgnoreUnknownFields(true);

        if (!f.exists()) {
            GlobalVariables.seed = new java.util.Random().nextLong();
            WorldIO created = new WorldIO(
                GlobalVariables.seed,
                GlobalVariables.worldMode,
                GlobalVariables.renderDistance,
                gameVersion
            );
            save(worldDir, created);
            return created;
        }

        try {
            String text = f.readString("UTF-8");
            WorldIO loaded = json.fromJson(WorldIO.class, text);

            if (loaded == null) {
                // corrupted -> recreate using GlobalVariables (only for first fallback)
                WorldIO recreated = new WorldIO(
                    GlobalVariables.seed,
                    GlobalVariables.worldMode,
                    GlobalVariables.renderDistance,
                    gameVersion
                );
                save(worldDir, recreated);
                return recreated;
            }

            // sanitize / defaults / migration
            int oldSchema = loaded.schema <= 0 ? 1 : loaded.schema;

            loaded.schema = SCHEMA_VERSION;
            loaded.worldMode = normalizeMode(loaded.worldMode);
            if (loaded.version == null) loaded.version = "";

            // schema v1 -> v2 migration: renderDistance didn't exist
            if (oldSchema < 2) {
                loaded.renderDistance = normalizeRenderDistance(GlobalVariables.renderDistance);

                save(worldDir, loaded);
            } else {
                loaded.renderDistance = normalizeRenderDistance(loaded.renderDistance);
            }

            GlobalVariables.seed = loaded.seed;
            GlobalVariables.worldMode = loaded.worldMode;
            GlobalVariables.renderDistance = loaded.renderDistance;

            return loaded;
        } catch (Throwable ex) {
            ex.printStackTrace();

            // On read error: recreate (still keep folder)
            WorldIO recreated = new WorldIO(
                GlobalVariables.seed,
                GlobalVariables.worldMode,
                GlobalVariables.renderDistance,
                gameVersion
            );
            save(worldDir, recreated);
            return recreated;
        }
    }

    public static void save(FileHandle worldDir, WorldIO info) {
        if (!worldDir.exists()) worldDir.mkdirs();
        FileHandle f = worldDir.child(FILE_NAME);

        // sanitize before save
        if (info == null) return;
        info.schema = SCHEMA_VERSION;
        info.worldMode = normalizeMode(info.worldMode);
        info.renderDistance = normalizeRenderDistance(info.renderDistance);
        if (info.version == null) info.version = "";

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        String pretty = json.prettyPrint(info);
        f.writeString(pretty, false, "UTF-8");
    }

    public static String normalizeMode(String s) {
        if (s == null) return "normal";
        String m = s.trim().toLowerCase();
        if (m.equals("flat") || m.equals("normal") || m.equals("single")) return m;
        return "normal";
    }

    public static int normalizeRenderDistance(int r) {
        if (r < 1) return 1;
        if (r > 64) return 64;
        return r;
    }
}
