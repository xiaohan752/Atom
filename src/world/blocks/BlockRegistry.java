package com.atom.life.world.blocks;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.atom.life.GlobalVariables.debug;

/**
 * BlockRegistry (refactored, same public API)
 * - public methods unchanged
 * - package & field visibility unchanged
 * - runtime hot paths use caches (0..255 lookup)
 * - JSON parsing moved to BlockJsonLoader
 * - name->id moved to NameIndex
 * - defs storage moved to BlockStore
 */
public class BlockRegistry {

    int baseColor = 0xFFFFFFFF; // default white opaque
    boolean jitter = false;
    float jitterStrength = 0.10f;
    float lumaJitter = 0.06f;

    private final BlockStore store = new BlockStore();
    private final NameIndex names = new NameIndex();
    private final BlockRuntimeCache cache = new BlockRuntimeCache();
    private final BlockJsonLoader loader = new BlockJsonLoader();

    // Air def is kept as a stable singleton
    private final BlockDef airDef;

    // Warn-once undefined ids (debug only)
    private final boolean[] warnedUndefined = new boolean[256];

    public BlockRegistry() {
        // 1) AIR fallback
        this.airDef = BlockDefaults.makeAirDef(
            (byte) 0,
            baseColor, jitter, jitterStrength, lumaJitter
        );
        registerInternal(airDef);

        // 2) Load JSON
        loader.loadFromJson(
            "blocks.json",
            debug,
            baseColor, jitter, jitterStrength, lumaJitter,
            this::registerInternal
        );

        // 3) ensure defaults
        ensureDefault((byte) 1, "lime_block_jitter", true, true, BlockDef.Shape.CUBE, BlockDef.RenderLayer.OPAQUE, 0, 1, 2);
        ensureDefault((byte) 2, "brown_block_jitter",  true, true, BlockDef.Shape.CUBE, BlockDef.RenderLayer.OPAQUE, 2, 2, 2);
        ensureDefault((byte) 3, "light_gray_block_jitter", true, true, BlockDef.Shape.CUBE, BlockDef.RenderLayer.OPAQUE, 3, 3, 3);

        // 4) Rebuild runtime caches once (fills holes)
        cache.rebuildAll(store, airDef);
    }

    public BlockDef def(byte id) {
        int idx = id & 0xFF;
        BlockDef d = store.get(idx);
        if (d != null) return d;

        return airDef;
    }

    public byte idByName(String name, byte fallback) {
        return names.findId(name, fallback);
    }

    public Set<Byte> idByExpr(String expr, byte fallback) {
        Set<Byte> ids = new HashSet<>();
        if (expr == null) return Set.of(fallback);

        String s = expr.trim().toLowerCase();
        if (s.isEmpty()) return Set.of(fallback);

        if (s.indexOf('*') < 0 && s.indexOf('?') < 0) {
            return Set.of(idByName(s, fallback));
        }

        // glob -> regex
        Pattern p = globToPattern(s);

        for (int i = 0; i < 256; i++) {
            BlockDef d = store.get(i);
            if (d == null) continue;
            if (p.matcher(d.name).matches()) {
                ids.add(d.id);
            }
        }
        return ids;
    }

    private static Pattern globToPattern(String glob) {
        // collapse multiple '*' (*** -> *)
        StringBuilder sb = new StringBuilder(glob.length() + 8);
        sb.append("^");
        boolean prevStar = false;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (prevStar) continue;
                prevStar = true;
                sb.append(".*");
                continue;
            }
            prevStar = false;

            if (c == '?') {
                sb.append(".");
                continue;
            }

            // escape regex meta
            if ("\\.[]{}()+-^$|".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    public String nameOf(byte id) {
        return def(id).name;
    }

    public boolean isOpaque(byte id) {
        return cache.isOpaque(id & 0xFF);
    }

    public boolean isSolid(byte id) {
        return cache.isSolid(id & 0xFF);
    }

    public boolean isSlope(byte id) {
        return cache.isSlope(id & 0xFF);
    }

    public boolean isCube(byte id) {
        return cache.isCube(id & 0xFF);
    }

    public boolean blocksFullFace(byte id, int axis, boolean positiveFace) {
        return cache.blocksFullFace(id & 0xFF, axis, positiveFace);
    }

    public int tileTop(byte id)    { return cache.tileTop(id & 0xFF); }
    public int tileSide(byte id)   { return cache.tileSide(id & 0xFF); }
    public int tileBottom(byte id) { return cache.tileBottom(id & 0xFF); }

    public BlockDef.RenderLayer renderLayer(byte id) {
        // Keep behavior: undefined -> airDef -> NONE
        return def(id).renderLayer;
    }

    private void ensureDefault(byte id, String name, boolean opaque, boolean solid,
                               BlockDef.Shape shape, BlockDef.RenderLayer layer,
                               int top, int side, int bottom) {
        int idx = id & 0xFF;
        if (store.get(idx) != null) return;

        BlockDef d = new BlockDef(
            id, name,
            opaque, solid,
            shape, layer,
            top, side, bottom,
            0f,
            baseColor, jitter, jitterStrength, lumaJitter,
            false, 0f, 0f, 0f, 0f
        );

        registerInternal(d);
    }

    /**
     * Centralized "putDef".
     */
    private void registerInternal(BlockDef def) {
        int idx = def.id & 0xFF;

        store.set(idx, def);

        // Same-name mapping warning handled in NameIndex
        names.put(def.name, def.id, debug);

        // Update cache for this id
        cache.update(idx, def);
    }
}
