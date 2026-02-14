package com.atom.life.data;

import com.atom.life.GlobalVariables;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

/**
 * Runtime config loaded from config.json (local override).
 * - If local config.json missing: copy from internal assets/config.json; if missing too, write defaults.
 * - If fields missing: defaults stay.
 */
public final class GameIO {

    public static final String LOCAL_FILE = "config.json";
    public static final String INTERNAL_FILE = "config.json";

    public String worldName = "World";

    public float reach = 5f;

    public float maxProportion = 0.8f;

    /** sync interval seconds */
    public float weatherAutoSyncSeconds = 900f;

    public GameIO() {}

    public static GameIO loadOrCreate() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);

        FileHandle local = Gdx.files.local(LOCAL_FILE);

        // 1) if local missing -> try copy from internal
        if (!local.exists()) {
            FileHandle internal = Gdx.files.internal(INTERNAL_FILE);
            if (internal.exists()) {
                try {
                    local.writeString(internal.readString("UTF-8"), false, "UTF-8");
                } catch (Throwable ignored) {
                    // fallback to defaults write
                }
            }
        }

        // 2) if still missing -> write defaults
        if (!local.exists()) {
            GameIO def = new GameIO();
            saveLocal(def);
            return def;
        }

        // 3) read local
        try {
            String txt = local.readString("UTF-8");
            GameIO loaded = json.fromJson(GameIO.class, txt);
            if (loaded == null) {
                GameIO def = new GameIO();
                saveLocal(def);
                return def;
            }

            if (loaded.worldName == null || loaded.worldName.trim().isEmpty()) loaded.worldName = "World";
            if (loaded.reach <= 0f) loaded.reach = 5f;
            if (loaded.maxProportion <= 0f) loaded.maxProportion = 0.8f;
            if (loaded.weatherAutoSyncSeconds <= 0f) loaded.weatherAutoSyncSeconds = 900f;

            return loaded;
        } catch (Throwable ex) {
            ex.printStackTrace();
            GameIO def = new GameIO();
            saveLocal(def);
            return def;
        }
    }

    public static void saveLocal(GameIO cfg) {
        if (cfg == null) return;

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        FileHandle local = Gdx.files.local(LOCAL_FILE);
        String pretty = json.prettyPrint(cfg);
        System.out.println(pretty);
        local.writeString(pretty, false, "UTF-8");
    }

    public void applyToGlobals() {
        GlobalVariables.worldName = this.worldName;
        GlobalVariables.reach = this.reach;
        GlobalVariables.maxProportion = this.maxProportion;
        GlobalVariables.weatherAutoSyncSeconds = this.weatherAutoSyncSeconds;
    }
}
