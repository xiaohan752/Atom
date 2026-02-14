package com.atom.life.world.light;

import com.atom.life.world.Chunk;
import com.atom.life.world.ChunkStore;
import com.atom.life.world.World;

import static com.atom.life.GlobalVariables.debug;

public final class LightPropagator {

    private final World world;
    private final ChunkStore store;
    private final LightAccess access;
    private final LightCaches caches;
    private final LightQueues queues;
    private final LocalPacker packer;

    private final int maxAddPop;
    private final int maxRemPop;

    private static final int SX = Chunk.SX;
    private static final int SY = Chunk.SY;
    private static final int SZ = Chunk.SZ;

    public LightPropagator(World world,
                           ChunkStore store,
                           LightAccess access,
                           LightCaches caches,
                           LightQueues queues,
                           LocalPacker packer,
                           int maxAddPop,
                           int maxRemPop) {
        this.world = world;
        this.store = store;
        this.access = access;
        this.caches = caches;
        this.queues = queues;
        this.packer = packer;
        this.maxAddPop = maxAddPop;
        this.maxRemPop = maxRemPop;
    }

    public void processQueues() {
        processRemoveOnly();
        processAddOnly();
    }

    public void processRemoveOnly() {
        int pops = 0;
        LightQueues.RemEntry e = new LightQueues.RemEntry();

        while (queues.popRemove(e)) {
            if (++pops > maxRemPop) {
                if (debug) {
                    System.out.println("[BL][WARN] remove pop limit hit, remaining remQ=" + queues.remSize());
                }
                return;
            }

            int x = e.x, y = e.y, z = e.z, level = e.level;

            if (level <= 1) continue;

            removeCheckNeighbor(x + 1, y, z, level);
            removeCheckNeighbor(x - 1, y, z, level);
            removeCheckNeighbor(x, y + 1, z, level);
            removeCheckNeighbor(x, y - 1, z, level);
            removeCheckNeighbor(x, y, z + 1, level);
            removeCheckNeighbor(x, y, z - 1, level);
        }
    }

    public void processAddOnly() {
        int pops = 0;
        LightQueues.AddEntry e = new LightQueues.AddEntry();

        while (queues.popAdd(e)) {
            if (++pops > maxAddPop) {
                if (debug) {
                    System.out.println("[BL][WARN] add pop limit hit, remaining addQ=" + queues.addSize());
                }
                return;
            }

            long key = e.chunkKey;
            int local = e.local;

            Chunk c = store.getByKey(key);
            if (c == null || !c.isReady()) continue;

            int lx = packer.unpackLocalX(local);
            int ly = packer.unpackLocalY(local);
            int lz = packer.unpackLocalZ(local);

            if (ly < 0 || ly >= SY) continue;

            int level = c.getLightLocal(lx, ly, lz) & 0xFF;
            if (level <= 1) continue;

            int next = level - 1;

            // x+1
            if (lx + 1 < SX) {
                addTryPropagateLocal(c, lx + 1, ly, lz, next);
            } else {
                Chunk n = store.getOrNull(c.cx + 1, c.cz);
                if (n != null && n.isReady()) addTryPropagateLocal(n, 0, ly, lz, next);
            }

            // x-1
            if (lx - 1 >= 0) {
                addTryPropagateLocal(c, lx - 1, ly, lz, next);
            } else {
                Chunk n = store.getOrNull(c.cx - 1, c.cz);
                if (n != null && n.isReady()) addTryPropagateLocal(n, SX - 1, ly, lz, next);
            }

            // z+1
            if (lz + 1 < SZ) {
                addTryPropagateLocal(c, lx, ly, lz + 1, next);
            } else {
                Chunk n = store.getOrNull(c.cx, c.cz + 1);
                if (n != null && n.isReady()) addTryPropagateLocal(n, lx, ly, 0, next);
            }

            // z-1
            if (lz - 1 >= 0) {
                addTryPropagateLocal(c, lx, ly, lz - 1, next);
            } else {
                Chunk n = store.getOrNull(c.cx, c.cz - 1);
                if (n != null && n.isReady()) addTryPropagateLocal(n, lx, ly, SZ - 1, next);
            }

            // y+1 / y-1
            if (ly + 1 < SY) addTryPropagateLocal(c, lx, ly + 1, lz, next);
            if (ly - 1 >= 0) addTryPropagateLocal(c, lx, ly - 1, lz, next);
        }
    }

    private void removeCheckNeighbor(int nx, int ny, int nz, int oldLevel) {
        if (ny < 0 || ny >= SY) return;

        int nl = world.getBlockLight(nx, ny, nz) & 0xFF;
        if (nl == 0) return;

        if (nl < oldLevel) {
            access.setLightAtWorld(nx, ny, nz, (byte) 0);
            queues.pushRemove(nx, ny, nz, nl);
        } else {
            access.enqueueAddWorld(nx, ny, nz);
        }
    }

    private void addTryPropagateLocal(Chunk c, int lx, int ly, int lz, int candidate) {
        if (candidate <= 0) return;

        if (!canLightEnterCellLocal(c, lx, ly, lz)) return;

        int cur = c.getLightLocal(lx, ly, lz) & 0xFF;
        if (candidate > cur) {
            access.setLightLocal(c, lx, ly, lz, (byte) candidate);
            access.enqueueAddLocal(c, lx, ly, lz);
        }
    }

    private boolean canLightEnterCellLocal(Chunk c, int lx, int ly, int lz) {
        byte id = c.getLocal(lx, ly, lz);

        if (id == 0) return true;

        if (caches.isSource(id)) return true;

        return !caches.isOpaque(id);
    }
}
