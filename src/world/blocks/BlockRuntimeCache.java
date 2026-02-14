package com.atom.life.world.blocks;

/**
 * Runtime hot-path caches for 0..255 ids.
 * - Avoids defs[id&255] + branches in tight loops.
 * - Precomputes blocksFullFace bitmask (mesh/culling hotspot).
 */
final class BlockRuntimeCache {

    // Basic flags
    private final boolean[] opaque = new boolean[256];
    private final boolean[] solid  = new boolean[256];
    private final boolean[] cube   = new boolean[256];
    private final boolean[] slope  = new boolean[256];

    // Tiles
    private final int[] tileTop    = new int[256];
    private final int[] tileSide   = new int[256];
    private final int[] tileBottom = new int[256];

    // Face mask bits: X-/X+/Y-/Y+/Z-/Z+
    private final int[] faceMask6 = new int[256];

    void rebuildAll(BlockStore store, BlockDef airDef) {
        BlockDef[] arr = store.rawArray();
        for (int i = 0; i < 256; i++) {
            BlockDef d = arr[i];
            if (d == null) d = airDef;
            update(i, d);
        }
    }

    void update(int idx, BlockDef d) {
        if (d == null) {
            // Defensive (should not happen)
            opaque[idx] = false;
            solid[idx]  = false;
            cube[idx]   = false;
            slope[idx]  = false;
            tileTop[idx] = tileSide[idx] = tileBottom[idx] = 0;
            faceMask6[idx] = 0;
            return;
        }

        opaque[idx] = d.opaque;
        solid[idx]  = d.solid;

        // Shape classification
        BlockDef.Shape s = d.shape;
        cube[idx]  = (s == BlockDef.Shape.CUBE);
        slope[idx] = isSlopeShape(s);

        // Tiles
        tileTop[idx]    = d.tileTop;
        tileSide[idx]   = d.tileSide;
        tileBottom[idx] = d.tileBottom;

        // Full-face mask
        faceMask6[idx] = computeFaceMask6(s);
    }

    boolean isOpaque(int idx) { return opaque[idx & 0xFF]; }
    boolean isSolid(int idx)  { return solid[idx & 0xFF]; }
    boolean isCube(int idx)   { return cube[idx & 0xFF]; }
    boolean isSlope(int idx)  { return slope[idx & 0xFF]; }

    int tileTop(int idx)    { return tileTop[idx & 0xFF]; }
    int tileSide(int idx)   { return tileSide[idx & 0xFF]; }
    int tileBottom(int idx) { return tileBottom[idx & 0xFF]; }

    boolean blocksFullFace(int idx, int axis, boolean positiveFace) {
        int mask = faceMask6[idx & 0xFF];
        int bit = faceBit(axis, positiveFace);
        return (mask & bit) != 0;
    }

    // Face bit helpers
    private static final int BIT_X_NEG = 1 << 0;
    private static final int BIT_X_POS = 1 << 1;
    private static final int BIT_Y_NEG = 1 << 2;
    private static final int BIT_Y_POS = 1 << 3;
    private static final int BIT_Z_NEG = 1 << 4;
    private static final int BIT_Z_POS = 1 << 5;

    private static int faceBit(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? BIT_X_POS : BIT_X_NEG;
            case 1 -> positive ? BIT_Y_POS : BIT_Y_NEG;
            case 2 -> positive ? BIT_Z_POS : BIT_Z_NEG;
            default -> 0;
        };
    }

    private static boolean isSlopeShape(BlockDef.Shape s) {
        return s == BlockDef.Shape.SLOPE_XP
            || s == BlockDef.Shape.SLOPE_XN
            || s == BlockDef.Shape.SLOPE_ZP
            || s == BlockDef.Shape.SLOPE_ZN;
    }

    /**
     * Precompute which axis-aligned faces are fully closed.
     * Mirrors your original rules:
     * - AIR: none
     * - CUBE: all 6
     * - SLOPE: bottom (-Y) always full; plus the "high side" full.
     */
    private static int computeFaceMask6(BlockDef.Shape s) {
        if (s == BlockDef.Shape.AIR) return 0;

        if (s == BlockDef.Shape.CUBE) {
            return BIT_X_NEG | BIT_X_POS | BIT_Y_NEG | BIT_Y_POS | BIT_Z_NEG | BIT_Z_POS;
        }

        // slopes
        if (s == BlockDef.Shape.SLOPE_XP) {
            // bottom (-Y) + high side (-X)
            return BIT_Y_NEG | BIT_X_NEG;
        }
        if (s == BlockDef.Shape.SLOPE_XN) {
            // bottom (-Y) + high side (+X)
            return BIT_Y_NEG | BIT_X_POS;
        }
        if (s == BlockDef.Shape.SLOPE_ZP) {
            // bottom (-Y) + high side (+Z)
            return BIT_Y_NEG | BIT_Z_POS;
        }
        if (s == BlockDef.Shape.SLOPE_ZN) {
            // bottom (-Y) + high side (-Z)
            return BIT_Y_NEG | BIT_Z_NEG;
        }

        // Unknown future shapes: conservative (no full faces)
        return 0;
    }
}
