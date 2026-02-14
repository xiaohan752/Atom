package com.atom.life.world.blocks;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.function.Consumer;

/**
 * Loads blocks.json and emits BlockDef objects.
 * Parsing logic moved out of BlockRegistry.
 */
final class BlockJsonLoader {

    void loadFromJson(String internalPath,
                      boolean debug,
                      int defaultBaseColorRGBA,
                      boolean defaultJitter,
                      float defaultJitterStrength,
                      float defaultLumaJitter,
                      Consumer<BlockDef> sink) {

        FileHandle fh = Gdx.files.internal(internalPath);
        if (!fh.exists()) {
            if (debug) {
                System.out.println("[BlockRegistry] blocks.json not found at internal: " + internalPath + " (using defaults)");
            }
            return;
        }

        JsonValue root;
        try {
            root = new JsonReader().parse(fh);
        } catch (Throwable ex) {
            ex.printStackTrace();
            return;
        }

        JsonValue arr = root.get("blocks");
        if (arr == null || !arr.isArray()) {
            if (debug) {
                System.out.println("[BlockRegistry] blocks.json missing 'blocks' array (using defaults)");
            }
            return;
        }

        for (JsonValue b = arr.child; b != null; b = b.next) {
            try {
                int idInt = b.getInt("id", -1);
                if (idInt < 0 || idInt > 255) continue;
                byte id = (byte) idInt;

                String name = b.getString("name", "id_" + idInt);

                boolean opaque = b.getBoolean("opaque", idInt != 0);
                boolean solid  = b.getBoolean("solid",  idInt != 0);

                float emission = (float) b.getDouble("emission", 0.0);

                BlockDef.Shape shape = BlockDef.Shape.fromString(b.getString("shape", "cube"));
                BlockDef.RenderLayer layer = BlockDef.RenderLayer.fromString(b.getString("renderLayer", "opaque"));

                JsonValue tiles = b.get("tiles");
                int top, side, bottom;
                if (tiles != null) {
                    top = tiles.getInt("top", 0);
                    side = tiles.getInt("side", top);
                    bottom = tiles.getInt("bottom", side);
                } else {
                    top = b.getInt("tileTop", 0);
                    side = b.getInt("tileSide", top);
                    bottom = b.getInt("tileBottom", side);
                }

                int baseColorRGBA = defaultBaseColorRGBA;
                boolean jitter = defaultJitter;
                float jitterStrength = defaultJitterStrength;
                float lumaJitter = defaultLumaJitter;

                // root-level color/tint override
                String rootColor = b.getString("color", null);
                if (rootColor == null) rootColor = b.getString("tint", null);
                if (rootColor != null) baseColorRGBA = BlockDefaults.parseHexRGBA(rootColor, baseColorRGBA);

                // texture object overrides
                JsonValue tex = b.get("texture");
                if (tex != null) {
                    String colorStr = tex.getString("color", null);
                    if (colorStr == null) colorStr = tex.getString("tint", null);
                    if (colorStr != null) baseColorRGBA = BlockDefaults.parseHexRGBA(colorStr, baseColorRGBA);

                    jitter = tex.getBoolean("jitter", jitter);
                    jitterStrength = tex.getFloat("jitterStrength", jitterStrength);
                    lumaJitter = tex.getFloat("lumaJitter", lumaJitter);
                }

                boolean isFluid = false;
                float fluidDrag = 0f;
                float fluidBuoyancy = 0f;
                float fluidGravityScale = 1f;
                float fluidMoveScale = 1f;

                JsonValue fluid = b.get("fluid");
                if (fluid != null) {
                    String type = fluid.getString("type", "");
                    if ("water".equalsIgnoreCase(type)) {
                        isFluid = true;

                        // water defaults to non-solid
                        solid = false;

                        fluidDrag = fluid.getFloat("drag", 6.0f);
                        fluidBuoyancy = fluid.getFloat("buoyancy", 0.0f);
                        fluidGravityScale = fluid.getFloat("gravityScale", 0.25f);
                        fluidMoveScale = fluid.getFloat("moveScale", 0.60f);

                        // optionally force alpha render layer:
                        // layer = BlockDef.RenderLayer.ALPHA;
                    }
                }

                BlockDef def = new BlockDef(
                    id, name,
                    opaque, solid,
                    shape, layer,
                    top, side, bottom,
                    emission,
                    baseColorRGBA, jitter, jitterStrength, lumaJitter,
                    isFluid, fluidDrag, fluidBuoyancy, fluidGravityScale, fluidMoveScale
                );

                sink.accept(def);

            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
    }
}
