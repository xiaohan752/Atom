package com.atom.life.input;

import com.atom.life.world.Chunk;

/**
 * Fast math helpers (avoid Math.floor(double) on hot paths).
 */
final class FastMath {

    private FastMath() {}

    /**
     * Fast floor for float -> int.
     * Works for negative values as well.
     */
    static int fastFloor(float v) {
        int i = (int) v;
        return (v < i) ? (i - 1) : i;
    }

    static int clampY(int y) {
        if (y < 0) return 0;
        if (y >= Chunk.SY) return Chunk.SY - 1;
        return y;
    }
}
