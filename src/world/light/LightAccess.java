package com.atom.life.world.light;

import com.atom.life.world.Chunk;
import com.atom.life.world.ChunkKey;
import com.atom.life.world.ChunkStore;

import java.util.Arrays;

public final class LightAccess {

    private final ChunkStore store;
    private final TouchedChunksTracker touched;
    private final LightQueues queues;
    private final LocalPacker packer;

    // chunk size shortcuts
    private static final int SX = Chunk.SX;
    private static final int SY = Chunk.SY;
    private static final int SZ = Chunk.SZ;

    // pow2 fast path
    private static final boolean SX_POW2 = (SX & (SX - 1)) == 0;
    private static final boolean SZ_POW2 = (SZ & (SZ - 1)) == 0;
    private static final int SX_MASK = SX - 1;
    private static final int SZ_MASK = SZ - 1;
    private static final int SX_SHIFT = SX_POW2 ? Integer.numberOfTrailingZeros(SX) : -1;
    private static final int SZ_SHIFT = SZ_POW2 ? Integer.numberOfTrailingZeros(SZ) : -1;

    public LightAccess(ChunkStore store, TouchedChunksTracker touched, LightQueues queues, LocalPacker packer) {
        this.store = store;
        this.touched = touched;
        this.queues = queues;
        this.packer = packer;
    }

    public void clearChunkLight(Chunk c) {
        Arrays.fill(c.blockLight, (byte) 0);
        touched.markTouched(c);
    }

    public void setLightAtWorld(int wx, int wy, int wz, byte level) {
        if (wy < 0 || wy >= SY) return;

        int cx, cz, lx, lz;

        if (SX_POW2) {
            cx = wx >> SX_SHIFT;
            lx = wx & SX_MASK;
        } else {
            cx = Math.floorDiv(wx, SX);
            lx = wx - cx * SX;
        }

        if (SZ_POW2) {
            cz = wz >> SZ_SHIFT;
            lz = wz & SZ_MASK;
        } else {
            cz = Math.floorDiv(wz, SZ);
            lz = wz - cz * SZ;
        }

        Chunk c = store.getOrNull(cx, cz);
        if (c == null || !c.isReady()) return;

        if (c.setLightLocal(lx, wy, lz, level)) {
            touched.markTouched(c);
        }
    }

    public void enqueueAddWorld(int wx, int wy, int wz) {
        if (wy < 0 || wy >= SY) return;

        int cx, cz, lx, lz;

        if (SX_POW2) {
            cx = wx >> SX_SHIFT;
            lx = wx & SX_MASK;
        } else {
            cx = Math.floorDiv(wx, SX);
            lx = wx - cx * SX;
        }

        if (SZ_POW2) {
            cz = wz >> SZ_SHIFT;
            lz = wz & SZ_MASK;
        } else {
            cz = Math.floorDiv(wz, SZ);
            lz = wz - cz * SZ;
        }

        Chunk c = store.getOrNull(cx, cz);
        if (c == null || !c.isReady()) return;

        enqueueAddLocal(c, lx, wy, lz);
    }

    public void enqueueAddLocal(Chunk c, int lx, int ly, int lz) {
        long key = ChunkKey.pack(c.cx, c.cz);
        int local = packer.packLocal(lx, ly, lz);
        queues.pushAdd(key, local);
    }

    public boolean setLightLocal(Chunk c, int lx, int ly, int lz, byte level) {
        if (c.setLightLocal(lx, ly, lz, level)) {
            touched.markTouched(c);
            return true;
        }
        return false;
    }
}
