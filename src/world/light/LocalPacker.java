package com.atom.life.world.light;

import com.atom.life.world.Chunk;

public final class LocalPacker {

    private static final int SX = Chunk.SX;
    private static final int SZ = Chunk.SZ;

    private static final boolean SX_POW2 = (SX & (SX - 1)) == 0;
    private static final boolean SZ_POW2 = (SZ & (SZ - 1)) == 0;

    private static final int SX_MASK = SX - 1;
    private static final int SZ_MASK = SZ - 1;

    private static final int SX_SHIFT = SX_POW2 ? Integer.numberOfTrailingZeros(SX) : -1;
    private static final int SZ_SHIFT = SZ_POW2 ? Integer.numberOfTrailingZeros(SZ) : -1;

    // local bits layout only if pow2:
    // local = (y << (SX_SHIFT + SZ_SHIFT)) | (lz << SX_SHIFT) | lx
    private static final int L_Z_SHIFT = SX_POW2 ? SX_SHIFT : -1;
    private static final int L_Y_SHIFT = (SX_POW2 && SZ_POW2) ? (SX_SHIFT + SZ_SHIFT) : -1;

    public int packLocal(int lx, int ly, int lz) {
        if (SX_POW2 && SZ_POW2) {
            return (ly << L_Y_SHIFT) | (lz << L_Z_SHIFT) | (lx);
        }
        // 通用：永远正确
        return (ly * SZ + lz) * SX + lx;
    }

    public int unpackLocalX(int local) {
        if (SX_POW2 && SZ_POW2) return local & SX_MASK;
        return local % SX;
    }

    public int unpackLocalZ(int local) {
        if (SX_POW2 && SZ_POW2) return (local >>> L_Z_SHIFT) & SZ_MASK;
        int t = local / SX;
        return t % SZ;
    }

    public int unpackLocalY(int local) {
        if (SX_POW2 && SZ_POW2) return (local >>> L_Y_SHIFT);
        return local / (SX * SZ);
    }
}
