package com.atom.life.world.light;

import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.world.Chunk;
import com.atom.life.world.ChunkStore;
import com.atom.life.world.MeshSystem;
import com.atom.life.world.World;

import static com.atom.life.GlobalVariables.debug;

public final class BlockLightSystem {

    public static final int MAX_LIGHT = 7;

    private static final int MAX_ADD_POP = 800_000;
    private static final int MAX_REM_POP = 800_000;

    private final World world;

    private final LocalPacker packer;
    private final LightCaches caches;
    private final LightQueues queues;
    private final TouchedChunksTracker touched;
    private final LightAccess access;
    private final LightPropagator propagator;
    private final LightSeeder seeder;

    // batching
    private int batchDepth = 0;
    private boolean batching = false;

    public BlockLightSystem(World world, BlockRegistry registry, ChunkStore store, MeshSystem meshSystem) {
        this.world = world;

        this.packer = new LocalPacker();
        this.caches = new LightCaches(registry, MAX_LIGHT);
        this.queues = new LightQueues();
        this.touched = new TouchedChunksTracker(store, meshSystem);

        this.access = new LightAccess(store, touched, queues, packer);
        this.propagator = new LightPropagator(world, store, access, caches, queues, packer, MAX_ADD_POP, MAX_REM_POP);
        this.seeder = new LightSeeder(world, store, access, caches);
    }

    // Batch API
    /** Begin a batch of many block changes; lighting propagation will be deferred. */
    public void beginBatch() {
        if (batchDepth++ == 0) {
            batching = true;
            queues.beginPass(true);
            touched.beginPass();
        }
    }

    /** End a batch; if this closes the outermost batch, propagate once and flush touched meshes. */
    public void endBatch() {
        if (batchDepth <= 0) return;
        if (--batchDepth == 0) {
            try {
                // single propagation for whole batch
                propagator.processQueues();
                touched.flushTouchedRemesh();
            } finally {
                batching = false;
                // clear for next pass
                queues.beginPass(true);
            }
        }
    }

    // Chunk ready
    public void onChunkReady(Chunk c) {
        if (c == null || !c.isReady()) return;

        queues.beginPass(true);
        touched.beginPass();

        access.clearChunkLight(c);

        seeder.seedChunkSources(c);
        seeder.seedFromNeighborBorders(c);

        propagator.processQueues();

        touched.flushTouchedRemesh();

        if (debug) {
            // System.out.println("[BL] onChunkReady done.");
        }
    }

    // Block changed
    public void onBlockChanged(int wx, int wy, int wz, byte oldId, byte newId) {
        if (wy < 0 || wy >= Chunk.SY) return;

        // non-batch path: keep old behavior
        if (!batching) {
            queues.beginPass(true);
            touched.beginPass();

            onBlockChangedEnqueueOnly(wx, wy, wz, oldId, newId);

            propagator.processQueues();
            touched.flushTouchedRemesh();
            return;
        }

        // batch path: only enqueue, defer propagate + flush
        onBlockChangedEnqueueOnly(wx, wy, wz, oldId, newId);
    }

    /**
     * Enqueue remove/add work for a single changed block.
     * Assumes queues/touched already began (batch or non-batch).
     */
    private void onBlockChangedEnqueueOnly(int wx, int wy, int wz, byte oldId, byte newId) {

        int oldL = world.getBlockLight(wx, wy, wz) & 0xFF;

        // 1) removal (clear this cell light then spread removal)
        if (oldL > 0) {
            access.setLightAtWorld(wx, wy, wz, (byte) 0);
            queues.pushRemove(wx, wy, wz, oldL);
        }

        // 2) new source if any
        int newSrc = caches.emissionToLevel(newId);
        if (newSrc > 0) {
            access.setLightAtWorld(wx, wy, wz, (byte) newSrc);
            access.enqueueAddWorld(wx, wy, wz);
        }

        // 3) reseed neighbors (reopen paths)
        seeder.seedNeighborAdd(wx, wy, wz);
    }
}
