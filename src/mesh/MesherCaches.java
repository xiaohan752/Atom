package com.atom.life.mesh;

import com.atom.life.world.blocks.BlockDef;
import com.atom.life.world.blocks.BlockRegistry;

/**
 * Cached per-block properties (0..255).
 * Only depends on registry and can be rebuilt if registry changes.
 */
final class MesherCaches {

    // render layer (small int for branch-friendly checks)
    static final byte LAYER_NONE   = 0;
    static final byte LAYER_OPAQUE = 1;
    static final byte LAYER_ALPHA  = 2;

    final BlockRegistry registry;

    // registry predicates
    final boolean[] isCube = new boolean[256];
    final boolean[] isSlope = new boolean[256];
    final boolean[] isOpaque = new boolean[256];

    // layer and tiles
    final byte[] layer = new byte[256];
    final int[] tileTop = new int[256];
    final int[] tileSide = new int[256];
    final int[] tileBottom = new int[256];

    // emission packed to 0..255
    final int[] emission8 = new int[256];

    // derived fast predicates
    final boolean[] isOpaqueCube = new boolean[256];
    final boolean[] isAlphaBlock = new boolean[256];
    final boolean[] isOccluderOpaqueLayer = new boolean[256];

    MesherCaches(BlockRegistry registry) {
        this.registry = registry;
        rebuild();
    }

    void rebuild() {
        for (int i = 0; i < 256; i++) {
            byte id = (byte) i;

            BlockDef d = registry.def(id);
            if (d == null) {
                layer[i] = LAYER_NONE;
                tileTop[i] = tileSide[i] = tileBottom[i] = 0;
                emission8[i] = 0;
            } else {
                if (d.renderLayer == BlockDef.RenderLayer.OPAQUE) layer[i] = LAYER_OPAQUE;
                else if (d.renderLayer == BlockDef.RenderLayer.ALPHA) layer[i] = LAYER_ALPHA;
                else layer[i] = LAYER_NONE;

                tileTop[i] = d.tileTop;
                tileSide[i] = d.tileSide;
                tileBottom[i] = d.tileBottom;

                float e = d.emission;
                if (e <= 0f) emission8[i] = 0;
                else if (e >= 1f) emission8[i] = 255;
                else emission8[i] = Math.round(e * 255f);
            }

            isCube[i] = registry.isCube(id);
            isSlope[i] = registry.isSlope(id);
            isOpaque[i] = registry.isOpaque(id);

            isAlphaBlock[i] = (layer[i] == LAYER_ALPHA);
            isOpaqueCube[i] = isCube[i] && isOpaque[i] && (layer[i] == LAYER_OPAQUE);
            isOccluderOpaqueLayer[i] = (layer[i] == LAYER_OPAQUE) && isOpaque[i];
        }
    }

    int tileForFaceFast(int idIndex, int axisD, boolean positiveNormal) {
        if (axisD == 1) return positiveNormal ? tileTop[idIndex] : tileBottom[idIndex];
        return tileSide[idIndex];
    }

    float emission01(int idIndex) {
        return emission8[idIndex] / 255f;
    }
}
