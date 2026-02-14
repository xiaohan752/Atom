package com.atom.life.data;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

public class PlayerIO {

    private final FileHandle file;
    private final Json json;

    public PlayerIO(FileHandle worldDir) {
        if (!worldDir.exists()) worldDir.mkdirs();

        this.file = worldDir.child("player.json");

        this.json = new Json();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
    }

    public boolean exists() {
        return file.exists();
    }

    public void save(PlayerState state) {
        if (state == null) return;
        String text = json.prettyPrint(state);
        file.writeString(text, false, "UTF-8");
    }

    public PlayerState loadOrNull() {
        if (!file.exists()) return null;
        try {
            String text = file.readString("UTF-8");
            return json.fromJson(PlayerState.class, text);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public FileHandle getFile() {
        return file;
    }
}
