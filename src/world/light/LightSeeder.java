package com.atom.life.world.light;

import com.atom.life.world.Chunk;
import com.atom.life.world.ChunkStore;
import com.atom.life.world.World;

public final class LightSeeder {

    private final World world;
    private final ChunkStore store;
    private final LightAccess access;
    private final LightCaches caches;

    private static final int SX = Chunk.SX;
    private static final int SY = Chunk.SY;
    private static final int SZ = Chunk.SZ;

    public LightSeeder(World world, ChunkStore store, LightAccess access, LightCaches caches) {
        this.world = world;
        this.store = store;
        this.access = access;
        this.caches = caches;
    }

    public void seedChunkSources(Chunk c) {
        for (int y = 0; y < SY; y++) {
            for (int z = 0; z < SZ; z++) {
                for (int x = 0; x < SX; x++) {
                    byte id = c.getLocal(x, y, z);
                    int src = caches.emissionToLevel(id);
                    if (src <= 0) continue;

                    // source even if opaque
                    access.setLightLocal(c, x, y, z, (byte) src);
                    access.enqueueAddLocal(c, x, y, z);
                }
            }
        }
    }

    public void seedNeighborAdd(int wx, int wy, int wz) {
        seedOne(wx + 1, wy, wz);
        seedOne(wx - 1, wy, wz);
        seedOne(wx, wy + 1, wz);
        seedOne(wx, wy - 1, wz);
        seedOne(wx, wy, wz + 1);
        seedOne(wx, wy, wz - 1);
    }

    private void seedOne(int wx, int wy, int wz) {
        if (wy < 0 || wy >= SY) return;

        int l = world.getBlockLight(wx, wy, wz) & 0xFF;
        if (l > 0) {
            access.enqueueAddWorld(wx, wy, wz);
        }

        int src = caches.emissionToLevel(world.getBlock(wx, wy, wz));
        if (src > 0) {
            access.enqueueAddWorld(wx, wy, wz);
        }
    }

    public void seedFromNeighborBorders(Chunk c) {
        seedNeighborBorder(c, c.cx + 1, c.cz, +1, 0);
        seedNeighborBorder(c, c.cx - 1, c.cz, -1, 0);
        seedNeighborBorder(c, c.cx, c.cz + 1, 0, +1);
        seedNeighborBorder(c, c.cx, c.cz - 1, 0, -1);
    }

    /**
     * Scan a MAX_LIGHT-thick border strip in neighbor chunk to seed add queue.
     * dirX/dirZ indicates neighbor direction relative to c.
     */
    private void seedNeighborBorder(Chunk c, int ncx, int ncz, int dirX, int dirZ) {
        Chunk n = store.getOrNull(ncx, ncz);
        if (n == null || !n.isReady()) return;

        int x0 = 0, x1 = SX;
        int z0 = 0, z1 = SZ;

        int width = BlockLightSystem.MAX_LIGHT; // 7
        if (dirX == +1) { x0 = 0; x1 = width; }
        else if (dirX == -1) { x0 = SX - width; x1 = SX; }
        else if (dirZ == +1) { z0 = 0; z1 = width; }
        else if (dirZ == -1) { z0 = SZ - width; z1 = SZ; }

        for (int y = 0; y < SY; y++) {
            for (int z = z0; z < z1; z++) {
                for (int x = x0; x < x1; x++) {
                    int l = n.getLightLocal(x, y, z) & 0xFF;
                    if (l <= 0) continue;
                    access.enqueueAddLocal(n, x, y, z);
                }
            }
        }
    }
}
